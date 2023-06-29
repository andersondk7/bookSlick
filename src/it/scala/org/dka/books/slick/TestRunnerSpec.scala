package org.dka.books.slick

import com.typesafe.scalalogging.Logger
import org.scalatest.Assertion
//import org.scalatest.Assertions.succeed
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Failure, Success, Try}

// example of a spec test with fixture of type String

class TestRunnerSpec extends AnyFunSpec with TestRunner[String] with Matchers {

  private val logger = Logger(getClass.getName)

  //
  // setup stuff
  //
  private val setupException = new Exception("expected in setup")

  private val setupFails: String => Try[Unit] = s => {
    logger.info(s"setup failed in $s")
    Failure(setupException)
  }

  private val setupSucceeds: String => Try[Unit] = s => {
    logger.info(s"setup succeeded in $s")
    Success()
  }

  //
  // test stuff
  //
  private val testFailure: Try[Assertion] = Try(1 + 1 shouldBe 3)

  private val testRunException = new IllegalStateException("test was run when should not have been")

  private val testNotRun: String => Try[Assertion] = s => {
    logger.error(s"test not run in  $s")
    Failure(testRunException)
  }

  private val testFails: String => Try[Assertion] = s => {
    logger.info(s"test run and failed in $s")
    testFailure
  }

  private val testSucceeds: String => Try[Assertion] = s => {
    logger.info(s"test run and succeeded in $s")
    Success(succeed)
  }

  //
  // tearDown stuff
  //
  private val tearDownException = new Exception("expected in tearDown")

  private val tearDownFails: String => Try[Unit] = s =>
    Try {
      logger.info(s"tearDown fails in $s")
      throw tearDownException
    }

  private val tearDownSucceeds: String => Try[Unit] = s => {
    logger.info(s"tearDown succeeds in $s")
    Success()
  }

  describe("setup failures") {
    val test1 = "setup fails, test not run, tearDown succeeds"
    it(test1) {
      val runResult = runWithFixture(
        fixture = test1,
        setup = setupFails,
        test = testNotRun,
        tearDown = tearDownSucceeds
      )
      runResult.setupResult.wasRun shouldBe true
      runResult.setupResult.failure shouldBe Some(setupException)

      runResult.testResult.wasRun shouldBe false
      runResult.testResult.result shouldBe empty

      runResult.tearDownResult.wasRun shouldBe true
      runResult.tearDownResult.failure shouldBe None
    }

    val test2 = "setup fails, test not run, tearDown fails"
    it(test2) {
      val runResult = runWithFixture(
        fixture = test2,
        setup = setupFails,
        test = testNotRun,
        tearDown = tearDownFails
      )
      runResult.setupResult.wasRun shouldBe true
      runResult.setupResult.failure shouldBe Some(setupException)

      runResult.testResult.wasRun shouldBe false
      runResult.testResult.result shouldBe empty

      runResult.tearDownResult.wasRun shouldBe true
      runResult.tearDownResult.failure shouldBe Some(tearDownException)
    }

    val test3 = "setup passes, test fails, tearDown fails"
    it(test3) {
      val runResult = runWithFixture(
        fixture = test3,
        setup = setupSucceeds,
        test = testFails,
        tearDown = tearDownFails
      )
      runResult.setupResult.wasRun shouldBe true
      runResult.setupResult.failure shouldBe None

      runResult.testResult.wasRun shouldBe true
      runResult.testResult.result shouldBe Some(testFailure)

      runResult.tearDownResult.wasRun shouldBe true
      runResult.tearDownResult.failure shouldBe Some(tearDownException)
    }

    val test4 = "setup passes, test fails, tearDown succeeds"
    it(test4) {
      val runResult = runWithFixture(
        fixture = test4,
        setup = setupSucceeds,
        test = testFails,
        tearDown = tearDownSucceeds
      )
      runResult.setupResult.wasRun shouldBe true
      runResult.setupResult.failure shouldBe None

      runResult.testResult.wasRun shouldBe true
      runResult.testResult.result shouldBe Some(testFailure)

      runResult.tearDownResult.wasRun shouldBe true
      runResult.tearDownResult.failure shouldBe None
    }

    val test5 = "setup passes, test succeeds, tearDown fails"
    it(test5) {
      val runResult = runWithFixture(
        fixture = test5,
        setup = setupSucceeds,
        test = testSucceeds,
        tearDown = tearDownFails
      )
      runResult.setupResult.wasRun shouldBe true
      runResult.setupResult.failure shouldBe None

      runResult.testResult.wasRun shouldBe true
      runResult.testResult.result shouldBe Some(Success(succeed))

      runResult.tearDownResult.wasRun shouldBe true
      runResult.tearDownResult.failure shouldBe Some(tearDownException)
    }

    val test6 = "setup passes, test succeeds, tearDown succeeds"
    it(test6) {
      val runResult = runWithFixture(
        fixture = test6,
        setup = setupSucceeds,
        test = testSucceeds,
        tearDown = tearDownSucceeds
      )
      runResult.setupResult.wasRun shouldBe true
      runResult.setupResult.failure shouldBe None

      runResult.testResult.wasRun shouldBe true
      runResult.testResult.result shouldBe Some(Success(succeed))

      runResult.tearDownResult.wasRun shouldBe true
      runResult.tearDownResult.failure shouldBe None
    }
  }

}
