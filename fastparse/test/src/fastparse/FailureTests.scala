package test.fastparse
import fastparse._
import NoWhitespace._
import utest._

object FailureTests extends TestSuite{
  def tests = Tests{
    def checkSimple(parser: P[_] => P[_]) = {
      val f @ Parsed.Failure(failureString, index, extra) = parse("d", parser(_))
      val trace = f.trace(true)

      assert(
        trace.terminalAggregateString == """("a" | "b" | "c")""",
        trace.groupAggregateString == """(parseB | "c")"""
      )
    }

    def checkOffset(input: String,
                    expected: String,
                    label: String,
                    terminals: String = null,
                    parser: P[_] => P[_]) = {
      val f @ Parsed.Failure(failureString, index, extra) = parse(input, parser(_))
      val trace = f.trace(true)

      val terminals1 = Option(terminals).getOrElse(expected)
      assert(
        trace.failure.label == label,
        trace.groupAggregateString == expected,
        trace.terminalAggregateString == terminals1
      )
    }

    'simple - {
      'either - checkSimple{
        def parseB[_: P] = P( "a" | "b" )
        def parseA[_: P] = P( (parseB | "") ~ "c" )
        parseA(_)
      }
      'option - checkSimple{
        def parseB[_: P] = P( "a" | "b" )
        def parseA[_: P] = P( parseB.? ~ "c" )
        parseA(_)
      }
      'rep - checkSimple{
        def parseB[_: P] = P( "a" | "b" )
        def parseA[_: P] = P( parseB.rep ~ "c" )
        parseA(_)
      }
      'repApply - checkSimple{
        def parseB[_: P] = P( "a" | "b" )
        def parseA[_: P] = P( parseB.rep() ~ "c" )
        parseA(_)
      }
      'repX - checkSimple{
        def parseB[_: P] = P( "a" | "b" )
        def parseA[_: P] = P( parseB.repX ~ "c" )
        parseA(_)
      }
      'repXApply - checkSimple{
        def parseB[_: P] = P( "a" | "b" )
        def parseA[_: P] = P( parseB.repX() ~ "c" )
        parseA(_)
      }
    }

    'deep - {
      'option - checkSimple{
        def parseC[_: P] = P( "a" | "b" )
        def parseB[_: P] = P( parseC.rep(1) )
        def parseA[_: P] = P( parseB.? ~ "c" )
        parseA(_)
      }
      'either - checkSimple{
        def parseC[_: P] = P( "a" | "b" )
        def parseB[_: P] = P( parseC.rep(1) )
        def parseA[_: P] = P( (parseB | "") ~ "c" )
        parseA(_)
      }
      'rep - checkSimple{
        def parseC[_: P] = P( "a" | "b" )
        def parseB[_: P] = P( parseC.rep(1) )
        def parseA[_: P] = P( parseB.rep ~ "c" )
        parseA(_)
      }
      'repApply - checkSimple{
        def parseC[_: P] = P( "a" | "b" )
        def parseB[_: P] = P( parseC.rep(1) )
        def parseA[_: P] = P( parseB.rep() ~ "c" )
        parseA(_)
      }
      'repX - checkSimple{
        def parseC[_: P] = P( "a" | "b" )
        def parseB[_: P] = P( parseC.repX(1) )
        def parseA[_: P] = P( parseB.repX ~ "c" )
        parseA(_)
      }
      'repXApply - checkSimple{
        def parseC[_: P] = P( "a" | "b" )
        def parseB[_: P] = P( parseC.repX(1) )
        def parseA[_: P] = P( parseB.repX() ~ "c" )
        parseA(_)
      }
    }

    'sep - {
      def parseB[_: P] = P( "a" | "b" )
      def parseA[_: P] = P( parseB.rep(sep = ",") ~ "c" )
      val f1 @ Parsed.Failure(_, _, _) = parse("ad", parseA(_))

      val trace = f1.trace()

      assert(trace.groupAggregateString == """("," | "c")""")

      val f2 @ Parsed.Failure(_, _, _) = parse("a,d", parseA(_))
      val trace2 = f2.trace()

      // Make sure if the parse fails after the separator and has to backtrack,
      // we list both the separator and the post-separator parse that failed
      // since showing the separator alone (which passed) is misleading
      assert(trace2.groupAggregateString == """("," ~ parseB | "c")""")
      f2.index
    }

    'sepCut - {
      def parseB[_: P] = P( "a" | "b" | "c" )
      def parseA[_: P] = P( parseB.rep(sep = ","./) ~ "d" )
      val f1 @ Parsed.Failure(_, _, _) = parse("ax", parseA(_))
      val trace = f1.trace()

      trace.groupAggregateString ==> """("," | "d")"""
      f1.index ==> 1

      val f2 @ Parsed.Failure(_, _, _) = parse("a,x", parseA(_))
      val trace2 = f2.trace()

      trace2.groupAggregateString ==> """("a" | "b" | "c")"""
      f2.index ==> 2
    }

    'aggregateInNamedParser - checkOffset(
      input = "ad",
      expected = """("b" | "c")""",
      label = "\"c\"",
      parser = {
        def parseB[_: P] = P("a" ~ "b".?)
        def parseA[_: P] = P(parseB ~ "c")
        parseA(_)
      }
    )

    'manualSep - checkOffset(
      input = "a,d",
      expected = "\"c\"",
      label = "\"c\"",
      parser = {
        def parseA[_: P] = P( "a" ~ (("." | ","./) ~ "c").rep ~ "x" )
        parseA(_)
      }
    )

    'sequentialEithers - checkOffset(
      input = "z",
      expected = """("a" | "b" | "c" | "d" | "e" | "f" | "g" | "h" | "i" | "j" | "k" | "l" | "m" | "n" | "o" | "p" | "q" | "r" | "x")""",
      label = "\"x\"",
      parser = {
        def parseD[_: P] = P( (("m" | "n") | "o").rep )
        def parseC[_: P] = P( (("g" | "h") | "i").? )
        def parseB[_: P] = P( ("a" | "b") | "c" | "" )
        def parseA[_: P] = P(
          parseB ~ ("d" | ("e" | "f") | "") ~
            parseC ~ ("j" | ("k" | "l")).? ~
            parseD ~ ("p" | ("q" | "r")).rep ~
            "x"
        )
        parseA(_)
      }
    )
    'passingNamedParsersAggregateIsShallow - checkOffset(
      input = "ad",
      expected = """(parseB ~ fail | "c")""",
      label = "\"c\"",
      terminals = "\"c\"",
      parser = {
        def parseB[_: P] = P( "a" ~ "b".? )
        def parseA[_: P] = P( (parseB ~ Fail).? ~ "c" )
        parseA(_)
      }
    )

    'passingNamedParsersEitherAggregateIsShallow - checkOffset(
      input = "_",
      expected = """(parseB | parseZ)""",
      label = "(parseB | parseZ)",
      terminals = """("c" | "d" | "x" | "y")""",
      parser = {
        def parseD[_: P] = P("d")
        def parseC[_: P] = P("c")
        def parseX[_: P] = P("x")
        def parseY[_: P] = P("y")
        def parseB[_: P] = P(parseC | parseD)
        def parseZ[_: P] = P(parseX | parseY)
        def parseA[_: P] = P(parseB | parseZ)
        parseA(_)
      }
    )

    
    'offset - {

      // Consider cases where the failure happens down some branch of an either,
      // rep, or option, where the branch takes place before traceIndex but the
      // actual failure lines up nicely.
      'opt - checkOffset(
        input = "ax",
        expected = """("b" | "d")""",
        label = "\"d\"",
        parser = { implicit c => ("a" ~ "b").? ~ "a" ~ "d" }
      )
      'optRep - checkOffset(
        input = "ax",
        expected = """("b".rep(1) | "d")""",
        label = "\"d\"",
        terminals = """("b" | "d")""",
        parser = { implicit c => ("a" ~ "b".rep(1)).? ~ "a" ~ "d" }
      )
      'opt3 - checkOffset(
        input = "ax",
        expected = """("b" | "c" | "d")""",
        label = "\"d\"",
        parser = { implicit c => ("a" ~ "b".? ~ "c").? ~ "a" ~ "d"
        }
      )
      'rep - checkOffset(
        input = "ax",
        expected = """("b" | "d")""",
        label = "\"d\"",
        parser = { implicit c => ("a" ~ "b").rep ~ "a" ~ "d" }
      )
      'repX - checkOffset(
        input = "ax",
        expected = """("b" | "d")""",
        label = "\"d\"",
        parser = { implicit c => ("a" ~ "b").repX ~ "a" ~ "d" }
      )
      'repSep - checkOffset(
        input = "ax",
        expected = """("b" | "d")""",
        label = "\"d\"",
        parser = { implicit c => ("a" ~ "b").rep(sep = Pass) ~ "a" ~ "d" }
      )
      'repXSep - checkOffset(
        input = "ax",
        expected = """("b" | "d")""",
        label = "\"d\"",
        parser = { implicit c => ("a" ~ "b").repX(sep = Pass) ~ "a" ~ "d" }
      )
      'rep1 - checkOffset(
        input = "ax",
        expected = """("b" | "d")""",
        label = "\"d\"",
        parser = { implicit c => ("a" ~ "b").rep(1).? ~ "a" ~ "d" }
      )
      'repX1 - checkOffset(
        input = "ax",
        expected = """("b" | "d")""",
        label = "\"d\"",
        parser = { implicit c => ("a" ~ "b").repX(1).? ~ "a" ~ "d" }
      )
      'rep1Sep - checkOffset(
        input = "ax",
        expected = """("b" | "d")""",
        label = "\"d\"",
        parser = { implicit c =>
          ("a" ~ "b").rep(1, sep = Pass).? ~ "a" ~ "d"
        }
      )
      'repX1Sep - checkOffset(
        input = "ax",
        expected = """("b" | "d")""",
        label = "\"d\"",
        parser = { implicit c => ("a" ~ "b").repX(1, sep = Pass).? ~ "a" ~ "d" }
      )
      'either1 - checkOffset(
        input = "ax",
        expected = """("b" | "d")""",
        label = "\"d\"",
        parser = { implicit c => "a" ~ "b" | "a" ~/ "d" }
      )
      'either2 - checkOffset(
        input = "ax",
        expected = """("b" | "c" | "d")""",
        label = "\"d\"",
        parser = {implicit c => ("a" ~ "b" | "a" ~ "c" | "") ~ "a" ~ "d" }
      )

      'opEither - checkOffset(
        input = "ax",
        expected = """("b" | "c" | "d")""",
        label = "\"d\"",
        parser = { implicit c => ("a" ~ "b" | "a" ~ "c").? ~ "a" ~ "d" }
      )
      'optEitherRep1 - checkOffset(
        input = "ax",
        expected = """("b" | "c" | "d")""",
        label = "\"d\"",
        parser = { implicit c => (("a" ~ "b").rep(1, sep = Pass) | ("a" ~ "c")).? ~ "a" ~ "d" }
      )

      'optOpt - checkOffset(
        input = "ax",
        expected = """("b" | "d")""",
        label = "\"d\"",
        parser = { implicit c => ("a" ~ "b").?.? ~ "a" ~ "d" }
      )
      'optRepX - checkOffset(
        input = "ax",
        expected = """("b" | "d")""",
        label = "\"d\"",
        parser = { implicit c => ("a" ~ "b").repX.? ~ "a" ~ "d" }
      )

    }
    'downstream - {
      // In the case where one branch fails further in than `traceIndex`, we
      // collect the partial aggregation from that branch in the
      // `failureGroupAggregate` but ignore that branch's downstream failure in
      // `failureTerminalsAggregate`
      'either1 - checkOffset(
        input = "abx",
        expected = """("b" ~ "c" | "d")""",
        label = "\"d\"", "\"d\"",
        parser = { implicit c =>
//          (("a" ~ "b") ~ "c") | "a" ~/ "d"
          ("a" ~ "b").log("AB").logAfter(P.current.failureGroupAggregate) ~ "c".log("C").logAfter(P.current.failureGroupAggregate) |
            "a" ~/ "d"
        }
      )

      'either2 - checkOffset(
        input = "abx",
        expected = """("b" ~ "c" | "d")""",
        label = "\"d\"", "\"d\"",
        parser = { implicit c => "a" ~ ("b" ~ "c") | "a" ~/ "d" }
      )
      'either3 - checkOffset(
        input = "abx",
        expected = """("b" ~ "c" | "d")""",
        label = "\"d\"",
        terminals = "\"d\"",
        parser = { implicit c => ("a" ~ ("b" ~ "c") | "") ~ "a" ~/ "d" }
      )
      'rep - checkOffset(
        input = "abx",
        expected = """("b" ~ "c" | "d")""",
        label = "\"d\"",
        terminals = "\"d\"",
        parser = { implicit c => ("a" ~ ("b" ~ "c")).rep ~ "a" ~/ "d" }
      )
      'repX - checkOffset(
        input = "abx",
        expected = """("b" ~ "c" | "d")""",
        label = "\"d\"",
        terminals = "\"d\"",
        parser = { implicit c => ("a" ~ ("b" ~ "c")).repX ~ "a" ~/ "d" }
      )
      'repSep - checkOffset(
        input = "abx",
        expected = """("b" ~ "c" | "d")""",
        label = "\"d\"",
        terminals = "\"d\"",
        parser = { implicit c => ("a" ~ ("b" ~ "c")).rep(sep = Pass) ~ "a" ~/ "d" }
      )
      'option - checkOffset(
        input = "abx",
        expected = """("b" ~ "c" | "d")""",
        label = "\"d\"",
        terminals = "\"d\"",
        parser = { implicit c => ("a" ~ ("b" ~ "c")).? ~ "a" ~/ "d" }
      )
    }
  }
}