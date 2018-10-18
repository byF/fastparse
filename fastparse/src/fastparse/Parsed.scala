package fastparse

import fastparse.internal.Util

/**
  * The outcome of a [[ParsingRun]] run, either a success (with value and index) or
  * failure (with associated debugging metadata to help figure out what went
  * wrong).
  *
  * Doesn't contain any information not already present in [[ParsingRun]], but
  * packages it up nicely in an immutable case class that's easy for external
  * code to make use of.
  */
sealed abstract class Parsed[+T](val isSuccess: Boolean){
  def fold[V](onFailure: (String, Int, Parsed.Extra) => V, onSuccess: (T, Int) => V): V
  def get: Parsed.Success[T]
}

object Parsed{
  def fromParsingRun[T](p: ParsingRun[T]): Parsed[T] = {
    if (p.isSuccess) Parsed.Success(p.successValue.asInstanceOf[T], p.index)
    else Parsed.Failure(
      Option(p.lastFailureMsg).fold("")(_()),
      p.index,
      Parsed.Extra(p.input, p.startIndex, p.index, p.originalParser, p.failureStack)
    )
  }

  /**
    * The outcome of a successful parse
    *
    * @param value The value returned by the parse
    * @param index The index at which the parse completed at
    */
  final case class Success[+T](value: T, index: Int) extends Parsed[T](true){
    def get = this
    def fold[V](onFailure: (String, Int, Extra) => V, onSuccess: (T, Int) => V) = onSuccess(value, index)
    override def toString() = s"Parsed.Success($value, $index)"
  }

  /**
    * The outcome of a failed parse
    *
    * @param failureLabel A hint as to why the parse failed. Defaults to "",
    *                     unless you set `verboseFailures = true` or call
    *                     `.trace()` on an existing failure
    * @param index The index at which the parse failed
    * @param extra Metadata about the parse; useful for re-running the parse
    *              to trace out a more detailed error report
    */
  final case class Failure(failureLabel: String,
                           index: Int,
                           extra: Extra) extends Parsed[Nothing](false){
    def get = throw new Exception("Parse Error, " + msg)
    def fold[V](onFailure: (String, Int, Extra) => V, onSuccess: (Nothing, Int) => V) = onFailure(failureLabel, index, extra)
    override def toString() = s"Parsed.Failure($msg)"

    /**
      * Displays the failure message excluding the parse stack
      */
    def msg = {
      failureLabel match{
        case "" =>
          "Position " + extra.input.prettyIndex(index) +
          ", found " + Failure.formatTrailing(extra.input, index)
        case s => Failure.formatMsg(extra.input, List(s -> index), index)
      }
    }

    /**
      * Displays the failure message including the parse stack, if possible
      */
    def longMsg = {
      if (extra.stack.nonEmpty) {
        Failure.formatMsg(extra.input, extra.stack ++ List(failureLabel -> index), index)
      } else throw new Exception(
        "`.longMsg` requires the parser to be run with `verboseFailures = true`, " +
        "or to be called via `.trace().longMsg` or `.trace().longAggregateMsg`"
      )
    }

    /**
      * Re-runs the failed parse with `verboseFailures` turned on and failure
      * aggregation enabled. This allows Fastparse to provide much more
      * detailed error messages, at a cost of taking ~2x as long than the
      * original parse
      */
    def trace() = extra.trace
  }

  object Failure{
    def formatMsg(input: ParserInput, stack: List[(String, Int)], index: Int) = {
      "Expected " + Failure.formatStack(input, stack) +
      ", found " + Failure.formatTrailing(input, index)
    }
    def formatStack(input: ParserInput, stack: List[(String, Int)]) = {
      stack.map{case (s, i) => s"$s:${input.prettyIndex(i)}"}.mkString(" / ")
    }
    def formatTrailing(input: ParserInput, index: Int) = {
      Util.literalize(input.slice(index, index + 10))
    }
  }

  case class Extra(input: ParserInput,
                   startIndex: Int,
                   index: Int,
                   originalParser: ParsingRun[_] => ParsingRun[_],
                   stack: List[(String, Int)]) {
    @deprecated("Use .trace instead")
    def traced = trace

    /**
      * Re-runs the failed parse with aggregation turned on. This is the
      * slowest of Fastparse's error reporting mode, taking ~2x as long
      * as the original parse, but provides the greatest detail in the error
      * message
      */
    def trace() = {
      input.checkTraceable()
      TracedFailure.fromParsingRun(
        parseInputRaw[Any](
          input,
          originalParser,
          startIndex = startIndex,
          traceIndex = index,
          enableLogging = false,
          verboseFailures = true
        )
      )
    }
  }

  object TracedFailure{

    def fromParsingRun[T](p: ParsingRun[T]) = {
      assert(!p.isSuccess)
      TracedFailure(
        p.failureAggregate.reverse.map(_()).distinct,
        Parsed.fromParsingRun(p).asInstanceOf[Failure]
      )
    }
  }

  /**
    * A decorated [[Failure]] with extra metadata; provides a much more
    * detailed, through verbose, of the possible inputs that may have been
    * expected at the index at which the parse failed.
    *
    * @param failureAggregate contains not just the parsers which were present
    *                         when the parse finally failed, but also any other
    *                         parsers which may have earlier been tried at the
    *                         same index.
    * @param failure The raw failure object
    */
  case class TracedFailure(failureAggregate: Seq[String],
                           failure: Failure){
    def failureLabel = failure.failureLabel
    def index = failure.index
    def input = failure.extra.input
    def stack = failure.extra.stack
    def combinedAggregateString = failureAggregate match{
      case Seq(x) => x
      case items => items.mkString("(", " | ", ")")
    }

    @deprecated("Use .msg instead")
    def trace = longAggregateMsg
    /**
      * Displays the failure message excluding the parse stack
      */
    def msg = failure.msg
    /**
      * Displays the failure message including the parse stack, if possible
      */
    def longMsg = failure.longMsg
    /**
      * Displays the aggregate failure message, excluding the parse stack
      */
    def aggregateMsg = Failure.formatMsg(input, List(combinedAggregateString -> index), index)

    /**
      * Displays the aggregate failure message, including the parse stack
      */
    def longAggregateMsg = Failure.formatMsg(input, stack ++ Seq(combinedAggregateString -> index), index)
  }
}
