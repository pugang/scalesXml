package scales.xml

@deprecated(message="No further tests will be added here, newer tests use Functions._, this is only here to make sure no bugs are in the OldFunctions - since 0.3")
class OldBaseFunctionalityTest extends junit.framework.TestCase {

  import junit.framework.Assert._
  import java.io._
  import scales.utils._
  import ScalesUtils._
  import ScalesXml._

  import strategies.TextNodeJoiner

  import BaseTestConstants._

  val xmlFile = resource(this, "/data/BaseXmlTest.xml")

  val testXml = loadXml(xmlFile)
  val path = top(testXml)

  def testElemConstruction : Unit = {
    val q = pre("local")
    val mu = Elem(q, pre, jh, defo, jh, pre)
    val eal = Elem(q, emptyAttributes, List(pre, jh, defo, jh, defo))
    val el = Elem(q, List(pre, jh, defo, jh, defo))
    val mua = Elem(q, emptyAttributes, pre, jh, defo, jh, pre)
    
    import scalaz._
    import Scalaz._
    
    mu.namespaces assert_=== eal.namespaces
    eal.namespaces assert_=== el.namespaces
    el.namespaces assert_=== mua.namespaces
    mua.namespaces assert_=== mu.namespaces
  }

  /* uncomment in times of need
  def testPrint = {
	  foldPrint(XmlOutput(System.out))(testXml)
  }*/

  val prefixed = path.\*("NoNamespace").\*(Elements.localName("prefixed"))

  def testLocalNamePredicate = {
    assertTrue(prefixed.size == 1)
    assertEquals(prefixedPQN, Elements.Functions.pqName(prefixed.head))
  }
/* */
  def doAttrTest(res: Iterable[AttributePath]) {
    assertTrue("Did not find attr", res.size == 1)
    val attrib = res.head.attribute
    assertEquals("ns1:{urn:prefix}attr", attrib.name.pqName)
    assertEquals("namespaced", attrib.value)
  }
    
  def testNSAttributesDirect = {
    doAttrTest(prefixed \@ ("urn:prefix"::"attr"))
  }
   
  def testLocalNamePredicateAttributesDirect = {
    doAttrTest(prefixed \@ Attributes.localName("attr"))
  }

  def testNSAttributes = {
    doAttrTest(prefixed.\.*@("urn:prefix" :: "attr"))
  }

  def testPrefixedExactAttributes = {
    doAttrTest(prefixed.\.*@(Namespace("urn:prefix").prefixed("ns1", "attr")))
  }

  def testLocalOnlyFails = {
    assertTrue("Should not find an attribute, as this should only match for no namespace matches", prefixed.*@("attr").size == 0)
  }

  def testLocalNamePredicateAttributes = {
    doAttrTest(prefixed.\.*@(Attributes.localName("attr")))
  }

  def doExistsTest( f : (XPath[_]) => XPath[_]) = {
    val res = f(path \* ("NoNamespace"l))
    assertTrue("Did not find the attr in an element", res.size == 1)
    assertEquals("prefixed", Elements.Functions.localName(res.head))
  }

  def testExistsAttributes = {
    import Attributes.*@
    doExistsTest{ _ \* ( *@("urn:prefix" :: "attr") ) }
  }

  def testExistsAttributesMethod = {
    import Attributes.Functions.*@
    doExistsTest( _ .\*{ *@("urn:prefix" :: "attr")(_) } )
  }
 
  import TestUtils._

  def positionAllKids : XmlPaths = path.\*(2).\*

  def testPositionAllKids = {
    val expected = List(dontRedeclareNoNS, dontRedeclareNoNS,
      shouldRedeclareDefaultNS, prefixedPQN)
    
    assertCompare(expected, positionAllKids)(Elements.Functions.pqName(_))
  }

  val dontRedeclaresX = path.\\.*(Elements.localName("DontRedeclare"))
  def dontRedeclares : XmlPaths = dontRedeclaresX

  def testDescendentLocalNamePredicate = {
    val expected = List("{urn:default}DontRedeclare",
      "{}DontRedeclare", "{}DontRedeclare");

    assertCompare(expected, dontRedeclares)(Elements.Functions.pqName(_))
  }

  def unions : XmlPaths = dontRedeclaresX | dontRedeclaresX | (dontRedeclaresX.\^)

  def testUnions = {
    val expected = List("{urn:default}Default", 
      "{urn:default}DontRedeclare",
      "{}NoNamespace",
      "{}DontRedeclare", "{}DontRedeclare");

    assertCompare(expected, unions)(
      Elements.Functions.pqName(_))
  }

  def parentsDuplicates : XmlPaths = dontRedeclaresX.\^

  // without the duplicate filter it would have three results
  def testParentsDuplicates = {
    val expected = List("{urn:default}Default",
      "{}NoNamespace");

    assertCompare(expected, parentsDuplicates )(Elements.Functions.pqName(_))
  }
    
  val allAttribsX = path.\\.*@

  def allAttribs : Iterable[AttributePath] = allAttribsX

  //  
  def testAllAttributes = {
    val expected = List("justHere:{urn:justHere}attr=only", "{}type=interesting", 
  			"ns1:{urn:prefix}attr=namespaced");
    import Attributes.Functions._
    assertCompare( expected, allAttribs ){ implicit attr => pqName + "=" + value }
  }

  def testChainedAttributes = {
    val expected = List("justHere:{urn:justHere}attr=only")
    import Attributes.Functions._
    assertCompare( expected, allAttribsX.*@(jh("attr")).*@(_.value == "only") ){
      implicit attr => pqName + "=" + value }
  }

  def testElementsWithAttributes = {
    val expected = List("{}NoNamespace")
    
    // the attrib list to boolean should get picked up
    assertCompare( expected, path.\\*("NoNamespace").*{
      implicit p => p.\@("type").*@(_.value == "interesting")}) { 
	Elements.Functions.pqName(_) }
  }

  def allElementsWithAttributes : XmlPaths = allAttribsX \^
  
  def testAllElementsWithAttributes = {
    val expected = List("{}NoNamespace", 
  			"ns1:{urn:prefix}prefixed");
    
    assertCompare( expected, allElementsWithAttributes ){ Elements.Functions.pqName(_) }
  }
  
  def testElementText = {
    val expected = List("prefixed text")
    assertCompare(expected, path.\\*("urn:prefix" :: "prefixed")) { Elements.Functions.text(_) }
  }

  def elementsPredicate : XmlPaths = path.\\*(_ === "prefixed text")

  def testElementPredicate = {
    val expected = List("ns1:{urn:prefix}prefixed")
    assertCompare(expected, elementsPredicate) { Elements.Functions.pqName(_) }
  }

  def normalizePredicate : XmlPaths = path.\\.*(Elements.Functions.normalizeSpace(_) == "start mix mode prefixed text end mix mode")

  def testNormalizePredicate = {
    val expected = List("{}NoNamespace")
    assertCompare(expected, normalizePredicate) { Elements.Functions.pqName(_) }
  }

  def testCData = {
    // we have two nodes before so 3 whitespaces.  Of course we actually should be getting path as well I think...
    val expected = List("should not have to be & < escaped @ all \"\"&")
    assertCompare(expected, path.\\.cdata) { TextFunctions.value(_).trim }
  }

  def testComments = {
    val expected = List(" some comments are better than others ",
      " this wouldn't be one of them. ")
    assertCompare(expected, path.\+.comment) { TextFunctions.value(_) }
  }

  /**
   * //a:ShouldRedeclare/../text()[5]/preceding-sibling::text()[1]
   *
  def testPreviousSimple = {
    val expected = List("start mix mode")
    assertCompare(expected, 
      path.\\.*("urn:default"::"ShouldRedeclare").\^.\+.text.pos(4).\.preceding_sibling_::.text |> { x =>
	x.process( x.path.nodes.drop(3).take(1)) 
      }) { TextFunctions.value(_).trim }
    assertCompare(expected, 
      path.\\.*("urn:default"::"ShouldRedeclare").\^.\+.text.pos(4).\.preceding_sibling_::.text.pos(3)
      ) { TextFunctions.value(_).trim }
    /*assertCompare(expected, 
      path.\\.*("urn:default"::"ShouldRedeclare").\^.\.text.pos(4).\.preceding_sibling_::.text.\.previous
      ) { TextFunctions.value(_).trim }*/
  } */

  def parentTextNodesRepeats(path : XmlPath) : XmlPaths = path.\\.*("urn:default"::"ShouldRedeclare").\^.\+.text.pos(4) |> { x => assertEquals("was not size 2", 2, x.path.nodes.size);x}

  /**
   * //a:ShouldRedeclare/../text()[4]     
   */ 
  def testParentTextNodesRepeats = { // /preceding-sibling::text()[1]
    val testXml = loadXml(resource(this, "/data/BaseXmlTestRepeats.xml"))
    val path = top(testXml)
    
    val expected = List("start mix mode","start mix mode")
    assertCompare(expected, 
      parentTextNodesRepeats(path)
      ) { TextFunctions.value(_).trim }
  }

  def parentTextNodesMainRepeats : XmlPaths = path.\\.*("urn:default"::"ShouldRedeclare").\^.\+.text.pos(4)  |> { x => assertEquals("was not size 1", 1, x.path.nodes.size);x} 

  def testParentTextNodesMainRepeats = {
    val expected = one("start mix mode")
    assertCompare(expected, 
      parentTextNodesMainRepeats
      ) { TextFunctions.value(_).trim }
  }

  def previousThenSibling : XmlPaths = path.\*("NoNamespace"l).\*.\.preceding_sibling_::.*.\.following_sibling_::.*

  def testPreviousThenSibling = {
    val expected = List("{}DontRedeclare",
			"{urn:default}ShouldRedeclare",
			"ns1:{urn:prefix}prefixed")
    assertCompare(expected, previousThenSibling )
    { Elements.Functions.pqName(_) }
  }

  val utf8 = {
    val reader = new java.io.InputStreamReader(resource(this, "/data/BaseFunctionalityTestEntityTest.utf8").openStream, "UTF-8")
    val buffered = new java.io.BufferedReader(reader)
    val rutf8 = buffered.readLine()
    buffered.close()
    rutf8
  } 

  def testLazyViewed : Unit = {
    var res : List[XmlPath] = Nil
    import Elements.localName

    val paths = viewed(path).\\.*(localName("DontRedeclare")).
      filter{
	path => 
	  res = path :: res
	  true
      }
 
    assertEquals(0, res.size)

    val bits = lazyRaw(paths)
    bits.head
    assertEquals(1, res.size)

    bits.head // lazy but based on stream, implementation details ftw?
    assertEquals(1, res.size)
  }

  def testEager : Unit = {
    var res : List[XmlPath] = Nil
    import Elements.localName

    val paths = eager(path).\\.*(localName("DontRedeclare")).
      filter{
	path => 
	  res = path :: res
	  true
      }
    
    assertEquals(3, res.size)
  }

  /** Can't do in normal xpath, as it needs to filter out the cdatas */
  def testTextOnly : Unit = {
    val tnj = new TextNodeJoiner[QNameToken] with QNameTokenF {} 
    // or indeed mixed in.
    //new MutableVectorLikeStrategy with ElemQNameOptimisationT with TextNodeJoiner {}
    // join up the text nodes
    val testXml2 = loadXml(xmlFile, strategy = tnj)
    val path = top(testXml2)

    // we have two nodes before so 3 whitespaces.  Of course we actually should be getting path as well I think...
    val expected = List("", "", "", "", "", "start mix mode", "prefixed text", "end mix mode",
      "\"\n\t" + utf8,
      "mix it up some more", "", "")
    assertCompare(expected, path.\\.textOnly) { TextFunctions.value(_).trim }
  }

  def textP( path : XmlPath ) : XmlPaths = path.\\.text

  def testText = {
    // we have two nodes before so 3 whitespaces.  Of course we actually should be getting path as well I think...

    val tnj = new TextNodeJoiner[QNameToken] with QNameTokenF {} 
    val testXml2 = loadXml(xmlFile, strategy = tnj)
    val path = top(testXml2)

    val expected = List("", "", "", "", "", "start mix mode", "prefixed text", "end mix mode",
      "\"\n\t" + utf8,
      "should not have to be & < escaped @ all \"\"&",
      "mix it up some more",
      "", "")
    // we, like Jaxen work correctly, Saxon can't see the n-3 and 4
    assertCompare( expected, textP(path)){ TextFunctions.value(_).trim }
  }

  def followingSiblings : XmlPaths = path.\\.*.\.following_sibling_::.*(2)

  // //*/following-sibling::*[2]
  def testFollowingSiblings = {
    val expected = List( "{urn:default}ShouldRedeclare",
			"ns1:{urn:prefix}prefixed");
    assertCompare(expected,
      followingSiblings
      ) { Elements.Functions.pqName(_) }
  }

  def precedingSiblings : XmlPaths  = path.\\.*.\.preceding_sibling_::.*(2)

  // //*/preceding-sibling::*[2]
  def testPrecedingSiblings = {
    val expected = List( "{}DontRedeclare",
			"{}DontRedeclare");
    assertCompare(expected,
      precedingSiblings
      ) { Elements.Functions.pqName(_) }
  }

  def descendantSingleRoot : XmlPaths = path.\.descendant_::.*(Elements.localName("DontRedeclare")).pos(1)

  // greedily swallows up, doesn't flatmap
  def testDescendantSingleRoot = {
    val expected = one("{urn:default}DontRedeclare")
    assertCompare(expected,
      descendantSingleRoot
      ) { Elements.Functions.pqName(_) }
  }

  def descendantMultipleRoots : XmlPaths = path.\\.descendant_::.*(Elements.localName("DontRedeclare")).*(1)

  // two paths are opened up by \\ so it greedily works on two paths
  def testDescendantMultipleRoots = {
    val expected = List("{urn:default}DontRedeclare",
			"{}DontRedeclare")
    assertCompare(expected,
      descendantMultipleRoots
      ) { Elements.Functions.pqName(_) }
  }

  def descendantMultipleRootsGt1 : XmlPaths = path.\\.descendant_::.*(Elements.localName("DontRedeclare")).pos_>(1)
 
  def testDescendantMultipleRootsGt1 = {
    val expected = List("{}DontRedeclare",
			"{}DontRedeclare")
    assertCompare(expected,
      descendantMultipleRootsGt1
      ) { Elements.Functions.pqName(_) }
  }
  
  def descendantMultipleRootsLt2 : XmlPaths = path.\\.descendant_::.*(Elements.localName("DontRedeclare")).*.pos_<(2)

  def testDescendantMultipleRootsLt2 = {
    val expected = List("{urn:default}DontRedeclare",
			"{}DontRedeclare")
    assertCompare(expected,
      descendantMultipleRootsLt2
      ) { Elements.Functions.pqName(_) }
  }

  def descendantText : XmlPaths = {
    import TextFunctions.{value => tvalue}
    path.\\.descendant_::.text.filter{ implicit p => tvalue.trim.length > 2}.pos_==(3)
  }

  // /descendant::text()[string-length(normalize-space(.)) > 2][3] "end mix mode"
  /** descendant doesn't just work on elems */ 
  def testDescendantText = {
    import TextFunctions.{value => tvalue}
    val expected = one("end mix mode")

    assertCompare(expected, 
      descendantText
      ) { tvalue(_).trim }
  } 

  val nestedXmlFile = resource(this, "/data/Nested.xml")
  val nestedXml = loadXml(nestedXmlFile)
  val nested = top(nestedXml)

  def descendantTextNested : XmlPaths = {
    import TextFunctions.{value => tvalue}
    nested.\.descendant_::.text.filter{ implicit p => tvalue.trim.length > 2}      
  }

  // this one doesn't test alot directly, its more of a sanity mechanism
  def testDescendantTextNested = {
    import TextFunctions.{value => tvalue}
    val expected = List("start mix mode","end mix mode")

    assertCompare(expected, 
      descendantTextNested
      ) { tvalue(_).trim }
  } 

  def descendantSingleRootNested : XmlPaths = nested.\.descendant_::.*(Elements.localName("DontRedeclare")).pos(1)

  // again sanity
  def testDescendantSingleRootNested = {
    val expected = one("{urn:default}DontRedeclare")
    assertCompare(expected,
      descendantSingleRootNested
      ) { Elements.Functions.pqName(_) }
  }

  def descendantMultipleRootsNested : XmlPaths = 
    nested.\\.descendant_::.*(Elements.localName("DontRedeclare")).*(1)

  // two paths are opened up by \\ so it greedily works on two paths
  def testDescendantMultipleRootsNested = {
    val expected = List("{urn:default}DontRedeclare",
			"{}DontRedeclare",
			"{}DontRedeclare",
			"{}DontRedeclare",
			"{}DontRedeclare",
			"{}DontRedeclare")
    assertCompare(expected,
      descendantMultipleRootsNested
      ) { Elements.Functions.pqName(_) }
  }
 
  def descendantMultipleRootsGt1Nested : XmlPaths = 
    nested.\\.descendant_::.*(Elements.localName("DontRedeclare")).pos_>(1)

  def testDescendantMultipleRootsGt1Nested = {
    val expected = List("{}DontRedeclare",
			"{}DontRedeclare",
			"{}DontRedeclare",
			"{}DontRedeclare",
			"{}DontRedeclare",
			"{}DontRedeclare",
			"{}DontRedeclare",
			"{}DontRedeclare",
			"{}DontRedeclare",
			"{}DontRedeclare")
    assertCompare(expected,
      descendantMultipleRootsGt1Nested
      ) { Elements.Functions.pqName(_) }
  }

  def descendantMultipleRootsLt2Nested : XmlPaths =
    nested.\\.descendant_::.*(Elements.localName("DontRedeclare")).*.pos_<(2)
  
  def testDescendantMultipleRootsLt2Nested = {
    val expected = List("{urn:default}DontRedeclare",
			"{}DontRedeclare",
			"{}DontRedeclare",
			"{}DontRedeclare",
			"{}DontRedeclare",
			"{}DontRedeclare")
    assertCompare(expected,
      descendantMultipleRootsLt2Nested
      ) { Elements.Functions.pqName(_) }
  }

  def lastEq : XmlPaths = path.\\*.last_==(4)

  // //*[last() = 4] 
  def testLastEq = {
    val expected = List("{}DontRedeclare",
			"{}DontRedeclare",
			"{urn:default}ShouldRedeclare",
			"ns1:{urn:prefix}prefixed")
    assertCompare(expected,
      lastEq
      ) { Elements.Functions.pqName(_) }
  }

  def lastLt : XmlPaths = path.\\*("DontRedeclare"l).last_<(3)
  
  // //DontRedeclare[last() < 3]
  def testLastLt = {
    val expected = List("{}DontRedeclare",
			"{}DontRedeclare")
    assertCompare(expected,
      lastLt
      ) { Elements.Functions.pqName(_) }
  }

  def lastGt : XmlPaths = path.\\*.last_>(1)

  // //*[last() > 1]
  def testLastGt = {
    val expected = List("{urn:default}DontRedeclare",
			"{}NoNamespace",
			"{}DontRedeclare",
			"{}DontRedeclare",
			"{urn:default}ShouldRedeclare",
			"ns1:{urn:prefix}prefixed")
    assertCompare(expected,
      lastGt
      ) { Elements.Functions.pqName(_) }
  }

  def posIsLast : XmlPaths = path.\*.\\*.pos_eq_last

  // /*//*[position() = last()]
  def testPosIsLast = {
    val expected = List("{}NoNamespace",
			"ns1:{urn:prefix}prefixed")
    assertCompare(expected,
      posIsLast
      ) { Elements.Functions.pqName(_) }
  }

  def posIsLastFromRoot : XmlPaths = path.\\*.pos_eq_last
  
  // //*[position() = last()]
  def testPosIsLastFromRoot = {
    val expected = List("{urn:default}Default",
			"{}NoNamespace",
			"ns1:{urn:prefix}prefixed")
    assertCompare(expected,
      posIsLastFromRoot
      ) { Elements.Functions.pqName(_) }
  }

  def textPosIsLast : XmlPaths = path.\\.text.pos_eq_last

  // /*//text()[position() = last()]
  def testTextPosIsLast = {
    val expected = List("prefixed text",
			"end mix mode",
			"") //last bit at the end of the doc after the commment

    assertCompare(expected,
      textPosIsLast
      ) { TextFunctions.value(_).trim }
  }
}
