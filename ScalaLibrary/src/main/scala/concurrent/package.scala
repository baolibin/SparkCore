/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2013, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

package concurrent

/**
  * 此包对象包含用于并发和并行编程的原语。
  *
  * This package object contains primitives for concurrent and parallel programming.
  *
  * == Guide ==
  *
  * A more detailed guide to Futures and Promises, including discussion and examples
  * can be found at
  * [[http://docs.scala-lang.org/overviews/core/futures.html]].
  *
  * == Common Imports ==
  *
  * When working with Futures, you will often find that importing the whole concurrent
  * package is convenient, furthermore you are likely to need an implicit ExecutionContext
  * in scope for many operations involving Futures and Promises:
  *
  * {{{
  * import scala.concurrent._
  * import ExecutionContext.Implicits.global
  * }}}
  *
  * == Specifying Durations ==
  *
  * Operations often require a duration to be specified. A duration DSL is available
  * to make defining these easier:
  *
  * {{{
  * import scala.concurrent.duration._
  * val d: Duration = 10.seconds
  * }}}
  *
  * == Using Futures For Non-blocking Computation ==
  *
  * Basic use of futures is easy with the factory method on Future, which executes a
  * provided function asynchronously, handing you back a future result of that function
  * without blocking the current thread. In order to create the Future you will need
  * either an implicit or explicit ExecutionContext to be provided:
  *
  * {{{
  * import scala.concurrent._
  * import ExecutionContext.Implicits.global  // implicit execution context
  *
  * val firstZebra: Future[Int] = Future {
  *   val source = scala.io.Source.fromFile("/etc/dictionaries-common/words")
  *   source.toSeq.indexOfSlice("zebra")
  * }
  * }}}
  *
  * == Avoid Blocking ==
  *
  * Although blocking is possible in order to await results (with a mandatory timeout duration):
  *
  * {{{
  * import scala.concurrent.duration._
  * Await.result(firstZebra, 10.seconds)
  * }}}
  *
  * and although this is sometimes necessary to do, in particular for testing purposes, blocking
  * in general is discouraged when working with Futures and concurrency in order to avoid
  * potential deadlocks and improve performance. Instead, use callbacks or combinators to
  * remain in the future domain:
  *
  * {{{
  * val animalRange: Future[Int] = for {
  *   aardvark <- firstAardvark
  *   zebra <- firstZebra
  * } yield zebra - aardvark
  *
  * animalRange.onSuccess {
  *   case x if x > 500000 => println("It's a long way from Aardvark to Zebra")
  * }
  * }}}
  */
/**
  * Internal usage only, implementation detail.
  */
private[concurrent] object AwaitPermission extends CanAwait

/**
  * This marker trait is used by [[Await]] to ensure that [[Awaitable.ready]] and [[Awaitable.result]]
  * are not directly called by user code. An implicit instance of this trait is only available when
  * user code is currently calling the methods on [[Await]].
  */
@implicitNotFound("Don't call `Awaitable` methods directly, use the `Await` object.")
sealed trait CanAwait

/**
  * Internal usage only, implementation detail.
  */
private[concurrent] object AwaitPermission extends CanAwait

/**
  * `Await` is what is used to ensure proper handling of blocking for `Awaitable` instances.
  *
  * While occasionally useful, e.g. for testing, it is recommended that you avoid Await
  * when possible in favor of callbacks and combinators like onComplete and use in
  * for comprehensions. Await will block the thread on which it runs, and could cause
  * performance and deadlock issues.
  */
object Await {
    /**
      * Await the "completed" state of an `Awaitable`.
      *
      * Although this method is blocking, the internal use of [[scala.concurrent.blocking blocking]] ensures that
      * the underlying [[ExecutionContext]] is prepared to properly manage the blocking.
      *
      * @param  awaitable
      * the `Awaitable` to be awaited
      * @param  atMost
      * maximum wait time, which may be negative (no waiting is done),
      * [[scala.concurrent.duration.Duration.Inf Duration.Inf]] for unbounded waiting, or a finite positive
      * duration
      * @return the `awaitable`
      * @throws InterruptedException     if the current thread is interrupted while waiting
      * @throws TimeoutException         if after waiting for the specified time this `Awaitable` is still not ready
      * @throws IllegalArgumentException if `atMost` is [[scala.concurrent.duration.Duration.Undefined Duration.Undefined]]
      */
    @throws(classOf[TimeoutException])
    @throws(classOf[InterruptedException])
    def ready[T](awaitable: Awaitable[T], atMost: Duration): awaitable.type =
        blocking(awaitable.ready(atMost)(AwaitPermission))

    /**
      * Await and return the result (of type `T`) of an `Awaitable`.
      *
      * Although this method is blocking, the internal use of [[scala.concurrent.blocking blocking]] ensures that
      * the underlying [[ExecutionContext]] to properly detect blocking and ensure that there are no deadlocks.
      *
      * @param  awaitable
      * the `Awaitable` to be awaited
      * @param  atMost
      * maximum wait time, which may be negative (no waiting is done),
      * [[scala.concurrent.duration.Duration.Inf Duration.Inf]] for unbounded waiting, or a finite positive
      * duration
      * @return the result value if `awaitable` is completed within the specific maximum wait time
      * @throws InterruptedException     if the current thread is interrupted while waiting
      * @throws TimeoutException         if after waiting for the specified time `awaitable` is still not ready
      * @throws IllegalArgumentException if `atMost` is [[scala.concurrent.duration.Duration.Undefined Duration.Undefined]]
      */
    @throws(classOf[Exception])
    def result[T](awaitable: Awaitable[T], atMost: Duration): T =
        blocking(awaitable.result(atMost)(AwaitPermission))
}
