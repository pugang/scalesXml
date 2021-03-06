package scales.xml

import org.xml.sax.{InputSource, XMLReader}
import javax.xml.parsers.SAXParser
import scales.utils._
import java.io._

trait XmlParserImplicits {
  implicit def streamToSource( source : InputStream ) = new org.xml.sax.InputSource(source)
  implicit def readerToSource( source : Reader ) = new org.xml.sax.InputSource(source)
  /**
   * will call openStream.
   */ 
  implicit def urlToSource( url : java.net.URL ) = new org.xml.sax.InputSource(url.openStream)
}

/**
 * Whilst Saxon, Xerces and nu.validator support certain combinations, this is an abstraction over those needed by the readXml function
 */ 
trait SaxSupport {
  def setLexicalHandler[T <: OptimisationToken]( reader : XMLReader, handler : Handler[T] ) : Unit
  def getXmlVersion( reader : XMLReader ) : AnyRef
}

trait DefaultSaxSupport extends SaxSupport {

  def setLexicalHandler[T <: OptimisationToken]( reader : XMLReader, handler : Handler[T] ) : Unit = {
    reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)    
  }

  def getXmlVersion( reader : XMLReader ) : AnyRef =
    reader.getProperty("http://xml.org/sax/properties/document-xml-version")
  
}

trait XmlParser {

  import ScalesXml.xmlCBF

  import org.xml.sax.Locator
  import org.xml.sax.ext.Locator2

  /**
   * Use a custom parserPool to control the sax factory features 
   */ 
  def loadXml[Token <: OptimisationToken](source: InputSource, strategy : PathOptimisationStrategy[Token] = defaultPathOptimisation, parsers : Loaner[SAXParser] with SaxSupport= DefaultSAXParserFactoryPool.parsers)(implicit xmlVer : XmlVersion): Doc =
    parsers.loan {
      parser =>
	readXml(source, strategy, parser.getXMLReader(), parsers)
    }

  def readXml[Token <: OptimisationToken](source: InputSource, strategy : PathOptimisationStrategy[Token], reader : XMLReader, saxSupport : SaxSupport )(implicit xmlVer : XmlVersion): Doc = {
    var handler = new Handler[Token](strategy)
    saxSupport.setLexicalHandler[Token](reader, handler)
    
    reader.setContentHandler(handler)
    reader.parse(source)

    val docVersion = {
      val f = saxSupport.getXmlVersion(reader)

      if (f eq null) Xml10
      else {
	val v = f.toString
	if (v == "1.1") 
	  Xml11
	else
	  Xml10	  
      }
    }

    Doc(handler.getBuf.tree, handler.getProlog.copy( 
      decl = handler.getProlog.decl.copy(version = docVersion)),
	handler.getEnd)
  }   
  
  /**
   * Use a custom parserPool to control the sax factory features 
   */ 
  def loadXmlReader[Token <: OptimisationToken](source: InputSource, strategy : PathOptimisationStrategy[Token] = defaultPathOptimisation, parsers : Loaner[XMLReader] with SaxSupport = DefaultXMLReaderFactoryPool)(implicit xmlVer : XmlVersion): Doc =
    parsers.loan {
      parser =>
	readXml(source, strategy, parser, parsers)
    }

  /** Elems can have any QName, attribs only prefixed or default */
  def eqn[Token <: OptimisationToken](uri: String,
    localName: String,
    qName: String, strategy : MemoryOptimisationStrategy[Token], token : Token): QName =
    if (uri.length() == 0) {
      // can only be in qName
      strategy.noNamespaceQName(qName, token)
    } else {
      // might have a prefix
      if (qName == localName) {
        // no prefix
	strategy.unprefixedQName(localName, uri, token)
      } else {
        // break up the prefix
        val bits = qName.split(':')
	
	strategy.prefixedQName(localName, uri, bits(0), token)
      }
    }

  /** attribs only prefixed or default */
  def aqn[Token <: OptimisationToken](uri: String,
    localName: String,
    qName: String, strategy : MemoryOptimisationStrategy[Token], token : Token): AttributeQName =
    if (uri.length() == 0) {
      // can only be in qName
      strategy.noNamespaceQName(qName, token) // Right
    } else {
      // might have a prefix
      if (qName == localName) {
        // no prefix
        scales.utils.error("Should not have an attribute with a unprefixed namspace")
      } else {
        // break up the prefix
        val bits = qName.split(':')
        strategy.prefixedQName(localName, uri,bits(0), token) // Left
      }
    }

}


  import org.xml.sax.Locator
  import org.xml.sax.ext.Locator2

  class Handler[Token <: OptimisationToken](strategy : PathOptimisationStrategy[Token])(implicit val defaultVersion : XmlVersion) extends org.xml.sax.ext.DefaultHandler2 {

    import ScalesXml.xmlCBF

    import scales.utils.{noPath, Path, top, ScalesUtils }
    import org.xml.sax._
    import scala.collection.immutable.Stack
    import ScalesUtils._
//    import ScalesXml.toQName // Note we aren't validating the names here anyway so we don't need to use the correct xml version, future version may double check perhaps?

    private[this] implicit val weAreInAParser : FromParser = IsFromParser

    private[this] val token : Token = strategy.createToken

    // only trees have kids, and we only need to keep the parent
    private[this] val buf = new TreeProxies()
    def getBuf = buf

    private[this] var isCData = false

    // declarations on this element
    private[this] var nsDeclarations = emptyNamespaces

    private[this] var prolog = Prolog()
    private[this] var end = EndMisc()

    def getProlog = prolog
    def getEnd = end

    private[this] var locator : Locator = _
    
    // used for judging PI or Comments
    private[this] var inprolog = true

    def checkit(what: String) {
    }

    override def startDTD(name : String, publicId : String, systemId : String) {
      prolog = prolog.copy(dtd = Some(DTD(name, publicId, systemId)))
    }

    override def processingInstruction(target : String, data : String) {
      val pi = PI(target, data)
      addMisc(Right(pi))
    } 

    override def startPrefixMapping(prefix: String, uri: String) {
      nsDeclarations += (prefix -> uri)
    }

    override def setDocumentLocator( loc : Locator ) {
      locator = loc
    }

    override def startElement(uri: String,
      localName: String,
      qName: String,
      attributes: org.xml.sax.Attributes) {

      if (inprolog) {
	if (locator.isInstanceOf[Locator2]) {
	  val loc2 = locator.asInstanceOf[Locator2]
	  prolog = prolog.copy( decl = prolog.decl.copy(encoding = (
	    if (loc2.getEncoding ne null) {
	      java.nio.charset.Charset.forName(loc2.getEncoding)
	    } else scales.utils.defaultCharset
	  )))	  
	}
      }

      inprolog = false

      var i = 0
      val length = attributes.getLength
      var attribs = emptyAttributes

      while (i < length) {
        val qname = aqn(attributes.getURI(i), attributes.getLocalName(i), attributes.getQName(i), strategy, token)
        attribs = attribs unsafePlus 
	    strategy.attribute(qname, 
			attributes.getValue(i), 
			token)

        i += 1
      }
      
      // use the current nsMap
      val elem = strategy.elem(
	  eqn(uri, localName, qName, strategy, token)
	     , attribs, nsDeclarations, token)

      // reset the map
      nsDeclarations = emptyNamespaces

      // give it a fake mutable default to work with
      strategy.beginSubTree(buf, elem, token)
    }

    override def endElement(uri: String,
      localName: String,
      qName: String) {

      // pop it and we are now with the correct parent
      // let the strategy decide what actually happens
      strategy.elementEnd(buf, token)
    }

    override def comment(ch: Array[Char], offset: Int, length: Int): Unit = {
      val text = new String(ch, offset, length)

      addMisc( Left(Comment(text)) )      
    }

    def addMisc( miscItem : Misc ) {
      if (inprolog) 
	prolog = prolog.copy(misc = prolog.misc :+ miscItem)
      else {
	// need to tell if its finished the root elem or not
	if (buf.depth == -1)
	  end = end.copy( misc = end.misc :+ miscItem)
	else
	  // add it like a normal child
	  buf.addChild( miscItem.fold[XmlItem](x=>x, y=>y)) 
      }
    }

    override def characters(ch: Array[Char], offset: Int, length: Int): Unit = {
      val text = new String(ch, offset, length)

      val child: XmlItem = if (isCData) CData(text) else Text(text)

      // append as child
      buf.addChild(child)
    }

    override def startCDATA() { isCData = true; }
    override def endCDATA() { isCData = false; }

  }


// XmlBuilder, XmlChildren
final class TreeProxy( private[this] var _elem : Elem, private[this] val _builder : XmlBuilder){
  @inline def elem = _elem

  @inline def setElem( elem : Elem ) {
    _elem = elem
  }

  @inline def builder = _builder
}

import scala.collection.mutable.ArrayBuffer

/**
 * Mutable list that keeps the item creation to a minimum, no extra garbage here until the parse is done...
 *
 * NOTE this is effectively an internal structure, but is provided for user land performance tweaks
 */ 
class TreeProxies( ){
  import ScalesXml.xmlCBF

  // special case root tree
  var rootTree : XmlTree = _
  
  private[this] var _depth : Int = -1

  private[this] var _proxies : Array[TreeProxy] = Array.ofDim[TreeProxy](50)

  // current max size in the proxies (_proxies.length could be far larger)
  private[this] var _size = 0

  private[this] var _current : TreeProxy = _

/*
 * interface for TreeOptimisations below, don't penalise normal parsing
 */ 
  def current = _current
  def current_=( tp : TreeProxy ) {
    _current = tp 
  }
  def depth = _depth
  def depth_= ( newDepth : Int ) { _depth = newDepth }
  def proxy( depth : Int ) = _proxies( depth )

  /**
   * Strips a path from the current position to the top of tree.  The
   * existing cached trees are then effectively re-created without the current position (one depth less)
   */ 
  def proxyPath() : XmlPath = {
    var d = _depth
    val l = _current

    val te = l.elem
    val tcc = l.builder.result
     
    var elems = Array.ofDim[Elem](if (d < 0) 0 else d)
    while( d > 0 ) {
      d -= 1

      elems(d) = _proxies(d).elem
    }

    reuse

    var p = noXmlPath

    // add them back
    d = 0
    while( d < elems.length ){
      beginSub(elems(d), XmlBuilder())
      // add child to path
      p = addAndFocus(p, elems(d))
      d += 1
    }
    p = addAndFocus(p, te, tcc)
    
    p
  }

  /**
   * Pushes up the tree discarding the last, if its the top it "resets" the tree
   * Note unlike Paths, there is only the notion of last child on the parent depth.
   */
  def proxyRemoveAndUp() : TreeProxies = {
    
    val nd = _depth - 1

    if (_depth > 0) {
      _current = _proxies( nd )
      // scrap the last
      //val r = _current.builder.result
      _current.builder.clear()
    } else {
      // end of doc
      rootTree = null.asInstanceOf[XmlTree]
      _current = null.asInstanceOf[TreeProxy]
    }
    if (nd > -2)
      _depth = nd
    else
      _depth = -1
    
    this
  }

  /**
   * Keeps the same _proxies array but resets the rest.  The old proxies is
   * no longer usable.
   * WARN - as per class docs this is effectively an internal structure caveat empor
   */ 
  def reuse() : TreeProxies = {
    // size is not redone so we keep builders around
    _depth = -1
    rootTree = null.asInstanceOf[XmlTree]
    _current = null.asInstanceOf[TreeProxy]
    this
  }

  def addChild( i : XmlItem ) {
    _current.builder.+=(i)
  }

  def elementEnd() {
    val l = _current

    val newTree = Tree(l.elem, l.builder.result)
    
    if (_depth > 0) {
      _depth -= 1
      _current = _proxies( _depth )
      _current.builder.+=(newTree)
    } else {
      // end of doc
      rootTree = newTree
      _depth -= 1
    }
  }

  def beginSub( elem : Elem, builder : => XmlBuilder) {
    _depth += 1
    
    if (_depth == _proxies.length) {
      // double the size
      val ar = Array.ofDim[TreeProxy]( _proxies.length * 2 )
      Array.copy(_proxies, 0, ar, 0, _proxies.length)
      _proxies = ar
    }

    if (_depth == _size) {
      _current = new TreeProxy(elem, builder)
      _proxies(_depth) = _current
      _size +=1
    } else {
      _current = _proxies(_depth)
      _current.setElem(elem)
      _current.builder.clear() // don't create a new one
    }
  }

  /**
   * Only call when its the end of the parse
   */ 
  def tree = 
    rootTree

}
