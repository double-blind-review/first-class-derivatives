package fcd
package markdown

trait MarkdownParsers { self: RichParsers with MarkdownHelperfunctions =>

  val eosChar = 0.toChar

  // ###########################################################################
  // ############################ emptyLine  ###################################
  // ###########################################################################
  case class EmptyLine() extends Block {
  }

  lazy val emptyLine: Parser[EmptyLine] =
    biasedAlt((manyN(4, ' ') ~> many(space) ~ newline), many(space) ~> newline) ^^ {
      case a => new EmptyLine()
    }

  // ###########################################################################
  // ########################### ATX Heading  ##################################
  // ###########################################################################
  case class Heading(o: Int, a: String) extends Block  {
    //println("Anzahl der #: " + o + "\nInhalt der Ueberschrift: " + a);
    print("<h" + o + ">");
    a.foreach(print);
    print("</h" + o + ">\n");
  }

  lazy val atxHeading : Parser[Heading] =
    openingSeq ~ atxHeadingContent <~ closingSeq  ^^ {
      case(o: String , a: String) => new Heading(o.length, a)
      // case Heading(o, a) if o == a => { o + a}
      // case Heading(o, a) => { o + a}
      /* new Heading(o.length, inlineParser.parse(a).flatten)*/
    }

  lazy val openingSeq: Parser[String] =
    repeat(' ', 0, 3) ~> repeat('#', 1, 6) <~ some(space)

  lazy val closingSeq =
    (some(space) ~ many('#') ~ many(space) ~> newline) | (many(space) ~
    many('#') ~ some(space) ~> newline) | newline

  lazy val atxHeadingContent: Parser[String] =
    not(some(space) ~ many(any)) &> (many(no('#')) &> inlineParser) <&
    not(many(any) ~ some(space))

  // ###########################################################################
  // ############################ Code Block  #############ää###################
  // ###########################################################################
  case class CodeBlock(a: String) extends Block {
    // println("Inhalt des Code Blocks: " + a);
    print("<pre><code>");
    a.foreach(print);
    print("</code></pre>\n");
  }
  // Umschreiben in FCD!
  // Siehe Paper

  lazy val codeBlockContent: Parser[String] =
    some(no('\n'))

  lazy val codeBlockLine: Parser[String] =
    manyN(4, ' ') ~> codeBlockContent ~ newline

  lazy val indentedCodeBlock: Parser[CodeBlock] =
    codeBlockLine ~ many(biasedAlt(emptyLineCodeBlock, codeBlockLine)) ^^ {
      case (a: String, b: List[String]) => new CodeBlock(a::b mkString)
    }

  lazy val emptyLineCodeBlock: Parser[String] =
    biasedAlt((manyN(4, ' ') ~> many(space) ~ newline), many(space) ~> newline)

  // ###########################################################################
  // ####################### Fenced Code Block  ################################
  // ###########################################################################
  lazy val eos: Parser[Char] = eosChar
  // End of document charakter einführen
  lazy val fencedCodeBlock: Parser[CodeBlock] =
    openingFence >> {
      case (indentation: List[Char], openingFence: List[Char]) =>
        val closing = getClosingFence(openingFence.head, openingFence.length)
        many(getCodeBlockLine(indentation.length) <& not(closing)) <~ closing
    } ^^ {
      case (a: List[String]) => new CodeBlock(a mkString)
    }

  lazy val openingFence: Parser[(List[Char], List[Char])] =
    repeat(' ', 0, 3) ~ (min('~', 3) | min('`', 3)) <~ many(space) ~ newline

  def getCodeBlockLine[T](i: Int): Parser[String] = {
    biasedAlt(manyN(i, ' ') ~> many(no('\n')), withoutPrefix(some(space), many(no('\n')))) ~ newline
  }

  def getClosingFence[T](p: Parser[T],i: Int): Parser[List[T]] = {
    (repeat(' ', 0, 3) ~> min (p, i) <~ many(space) ~ newline) | eos ^^^ List()
  }

  // returns a Parser wich accept prefix + content but the prefix is stripped
  def withoutPrefix[T](prefix: Parser[Any], content: Parser[T]): Parser[T] = {
    biasedAlt(prefix ~> (not(prefix ~ many(any)) &> content), content)
  }

  // ###########################################################################
  // ######################## Thematic Breaks ##################################
  // ###########################################################################
  case class ThematicBreak() extends Block {
    print("<hr");
  }
  lazy val thematicBreak: Parser [ThematicBreak] =
    (thematicBreakLine('*')|
    thematicBreakLine('-')|
    thematicBreakLine('_')) ^^ { case a =>  ThematicBreak()}

  def stripSpaces[T](p: Parser[T]): Parser[T] =
    ( no(' ')         >> {c => stripSpaces(p << c) }
    | charParser(' ') >> {c => stripSpaces(p)}
    | done(p)
    )

  def thematicBreakLine(c: Char): Parser[Any] = {
        repeat(' ', 0, 3) ~> (stripSpaces(min(c, 3)) <& not(space ~ always))  <~ newline
  }
  // ###########################################################################
  // ######################## setext Heading ###################################
  // ###########################################################################
  lazy val setextHeading =
    paragraph ~ setextUnderline ^^ {case (a,b) => new Heading(b,a.toString)}

  lazy val setextUnderline =
    repeat(' ', 0, 3) ~> min('-', 1) <~ many(space) ~ newline ^^ {case a => 2} |
    repeat(' ', 0, 3) ~> min('=', 1) <~ many(space) ~ newline ^^ {case a => 1}

  // ###########################################################################
  // ########################### Paragraph #####################################
  // ###########################################################################

  case class Paragraph (content: String) extends Block{
    print("<p>");
    print(content);
    print("</p>");
    override def toString: String =
      content
  }

  lazy val paragraph: Parser[Paragraph] =
    (repeat(' ', 0, 3) ~> paragraphContent ~
    many(many(space) ~> paragraphContent)) ^^ {
      case (a: String,b: List[String]) => new Paragraph(a + (b mkString))
    }

  lazy val paragraphContent: Parser[String] =
    no(' ') ~  many(no('\n')) ~ newline
  // ###########################################################################
  // ########################## Block Quote ####################################
  // ###########################################################################
  lazy val blockQuote: Parser[BlockQuote]= {
    def blockQuoteCombinator[T](p: Parser[T]): Parser[T] =
      done(p) | biasedAlt ('>' ~ space ~> readLine(p),
                           '>' ~> readLine(p))

    def readLine[T](p: Parser[T]): Parser[T] =
      ( no('\n')          >> {c => readLine(p << c)}
      | charParser('\n')  >> {c => blockQuoteCombinator(p << c)}
      )

    blockQuoteCombinator(blockParser) ^^ {
      case (a,b) => new BlockQuote(a)
    }
    // line &> delegate(p)
  }
  case class BlockQuote (content: String) extends Block{
    print("<BlockQuote>");
  }


  // Hier könnte ihr Markdown Inline Parser stehen
  lazy val inlineParser =
    many(any)
  // TODO: Blockparser schreiben
  lazy val blockParser =
    many(any) ~ newline

  lazy val line: Parser[String] =
    many(no('\n')) ~ newline

  def readLine (open: Parser[Block]): Parser[Block] =
    done(open) |
    line >> { l =>
      (breaking(open) <~ md) <<< l  <|
      ((open <<< l)   <~ md)        <|
      md <<< l
    }

  def breaking (p: Parser[Block]): Parser[Block] =
    p >> {
      case a : Heading => fail
      case a : CodeBlock => fail
      case a : ThematicBreak => fail
      case a : Paragraph => fencedCodeBlock <| thematicBreak <| blockQuote <| atxHeading <| setextHeading <|  emptyLine
      case a : BlockQuote => fail
      case a : Block => fail
      case a => fail

    }

  case class Markdown () extends Block {
    var childBlocks: List[Block] = List()

    def addChild(block: Block) ={
      childBlocks = childBlocks ++ List(block);
    }
  }

  val md: Parser[Markdown] ={
    val document = new Markdown();

    many( readLine(fencedCodeBlock)   <|
          readLine(indentedCodeBlock) <|
          readLine(thematicBreak)     <|
          readLine(blockQuote)        <|
          readLine(atxHeading)        <|
          readLine(setextHeading)     <|
          readLine(emptyLine))        ^^ {
            case a: List[Block] => {
              a.foreach(document.addChild);
              document
            }
          }
  }


  class Block(){

  }
}

object MarkdownParsers extends MarkdownParsers with RichParsers with DerivativeParsers with MarkdownHelperfunctions
