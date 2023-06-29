package org.dka.books.slick

import org.scalatest.Assertion
import cats.data.State
import org.scalatest.Assertions.{fail, succeed}

import scala.util.{Failure, Success, Try}

sealed trait RunnerStepResult[R] {

  def result: Option[Try[R]]

  def wasRun: Boolean = result.isDefined

  def failure: Option[Throwable] = result.flatMap(_.failed.toOption)

}

//
// setup
//
final case class SetupResult(override val result: Option[Try[Unit]]) extends RunnerStepResult[Unit] {}

object SetupResult {

  def apply(tried: Try[Unit]): SetupResult = new SetupResult(Some(tried))

}

final case class SetupException(message: String, cause: Option[Throwable] = None)
  extends Throwable(message, cause.orNull) {}

//
// test
//
final case class TestResult(result: Option[Try[Assertion]]) extends RunnerStepResult[Assertion] {

  def evaluate: Assertion = result match {
    case None => succeed
    case Some(t) =>
      t match {
        case Failure(ex) => fail(ex)
        case Success(a)  => a
      }
  }

}

object TestResult {

  def apply(tested: Try[Assertion]): TestResult = {
    val yes: Option[Try[Assertion]] = Some(tested)
    new TestResult(yes)
  }

}

//
// tearDown
//
final case class TearDownResult(override val result: Option[Try[Unit]]) extends RunnerStepResult[Unit] {}

object TearDownResult {

  def apply(tried: Try[Unit]): TearDownResult = new TearDownResult(Some(tried))

}

final case class TearDownException(message: String, cause: Option[Throwable] = None)
  extends Throwable(message, cause.orNull) {}

final case class TestRunnerResult(
  setupResult: SetupResult,
  testResult: TestResult,
  tearDownResult: TearDownResult) {

  val setupGood: Boolean = setupResult.result.fold(false)(_.isSuccess)

  val tearDownGood: Boolean = tearDownResult.result.fold(false)(_.isSuccess)

  val shouldCheck: Boolean = setupGood && tearDownGood

  def +(setup: SetupResult): TestRunnerResult =
    this.copy(setupResult = setup)

  def +(test: TestResult): TestRunnerResult =
    this.copy(testResult = test)

  def +(tearDown: TearDownResult): TestRunnerResult =
    this.copy(tearDownResult = tearDown)

}

object TestRunnerResult {

  val initial: TestRunnerResult = TestRunnerResult(
    SetupResult(None),
    TestResult(None),
    TearDownResult(None)
  )

}

trait TestRunner[F] {

  /**
   * Runs test in a controlled manner:
   *
   * @param setup
   *   function that sets up the test, must be wrapped in a Try
   * @param test
   *   the actual test function, must be wrapped in a Try -- only runs if setup returns Success
   * @param tearDown
   *   function to clean up -- '''always runs''', is not wrapped in a Try
   * @return
   *   if tearDown succeeds, assertion from test, assertion from tearDown otherwise
   */
  def runWithFixture(
    fixture: F,
    setup: F => Try[Unit],
    test: F => Try[Assertion],
    tearDown: F => Try[Unit]
  ): TestRunnerResult = {

    // the 'state' is the TestRunnerResult
    // the 'value' is the fixture, which in this scenario does not change

    val runSetup: State[TestRunnerResult, F] = State(s =>
      (
        s + SetupResult(setup(fixture)),
        fixture
      ))

    val runTest: State[TestRunnerResult, F] = State { s =>
      val testResults =
        if (s.setupResult.failure.isEmpty)
          TestResult(test(fixture))
        else
          TestResult(None)

      (
        s + testResults,
        fixture
      )
    }

    val runTearDown: State[TestRunnerResult, F] = State(s =>
      (
        s + TearDownResult(tearDown(fixture)),
        fixture
      ))

    val steps = for {
      _      <- runSetup
      _      <- runTest
      result <- runTearDown
    } yield result
    steps.runS(TestRunnerResult.initial).value
  }

}
