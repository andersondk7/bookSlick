package org.dka.books.slick.services

import com.typesafe.scalalogging.Logger
import org.dka.books.slick.TearDownException
import org.dka.books.domain.services.InvalidVersionException
import org.dka.books.domain.model.fields.{CountryAbbreviation, CountryName, CreateDate, ID, UpdateDate, Version}
import org.dka.books.domain.model.item.Country
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Success, Try}

class CountryDaoImplSpec extends AnyFunSpec with DBTestRunner with Matchers {

  import CountryDaoImplSpec._

  // for a test, this is fine ...
  implicit private val ec: ExecutionContext = ExecutionContext.global

  private val logger = Logger(getClass.getName)

  val delay: FiniteDuration = 10.seconds

  describe("conversion to/from db") {
    it("should convert from domain to db") {
      CountryDaoImpl.toDB(iceland) match {
        case None => fail(s"could not convert $iceland")
        case Some((id, version, name, abbreviation, createDate, updateDate)) =>
          id shouldBe iceland.id.value.toString
          version shouldBe iceland.version.value
          name shouldBe iceland.countryName.value
          abbreviation shouldBe iceland.countryAbbreviation.value
          createDate shouldBe iceland.createDate.asTimestamp
          updateDate shouldBe iceland.lastUpdate.map(_.asTimeStamp)

      }
    }
    it("should convert from db to domain") {
      val db = (
        iceland.id.value.toString,
        iceland.version.value,
        iceland.countryName.value,
        iceland.countryAbbreviation.value,
        iceland.createDate.asTimestamp,
        iceland.lastUpdate.map(_.asTimeStamp)
      )
      val converted = CountryDaoImpl.fromDB(db)
      converted shouldBe iceland
    }
  }

  describe("populating") {
    it("should add a country") {
      val result = withDB(
        setup = noSetup,
        test = factory =>
          Try {
            Await.result(factory.countryDao.create(iceland), delay) match {
              case Left(e) => fail(e)
              case Right(country) =>
                country.id shouldBe iceland.id
                country.version shouldBe iceland.version
                country.countryName shouldBe iceland.countryName
                country.countryAbbreviation shouldBe iceland.countryAbbreviation
            }
          },
        tearDown = factory => deleteCountry(iceland.id)(factory, ec)
      )
      result.setupResult.failure shouldBe None
      result.tearDownResult.failure shouldBe None
      result.testResult.evaluate
    }
    it("should find a specific country") {
      val result = withDB(
        setup = factory => loadCountry(iceland)(factory, ec),
        test = factory =>
          Try {
            Await.result(factory.countryDao.read(iceland.id), delay) match {
              case Left(e)    => fail(e)
              case Right(opt) => opt.fold(fail(s"did not find $iceland"))(country => country shouldBe iceland)
            }
          },
        tearDown = factory => deleteCountry(iceland.id)(factory, ec)
      )
      result.setupResult.failure shouldBe None
      result.tearDownResult.failure shouldBe None
      result.testResult.evaluate
    }
  }

  describe("updating") {
    it("should update once") {
      val updatedAbbreviation = "ILD"
      val result = withDB(
        setup = factory => loadCountry(iceland)(factory, ec),
        test = factory =>
          Try {
            val updatedCountry = iceland.copy(countryAbbreviation = CountryAbbreviation.build(updatedAbbreviation))
            Await.result(factory.countryDao.update(updatedCountry)(ec), delay) match {
              case Left(e) => fail(e)
              case Right(updated) =>
                updated.version shouldBe updatedCountry.version.next
                updated.createDate shouldBe updatedCountry.createDate
                updated.countryName shouldBe updatedCountry.countryName
                updated.countryAbbreviation shouldBe updatedCountry.countryAbbreviation
                updated.lastUpdate should not be updatedCountry.lastUpdate
            }
          },
        tearDown = factory => deleteCountry(iceland.id)(factory, ec)
      )
      result.setupResult.failure shouldBe None
      result.tearDownResult.failure shouldBe None
      result.testResult.evaluate
    }
    it("should fail update with old version (sequentially)") {
      /*
      scenario:
      - bill wants to update only the country name
      - susan wants to update only the country abbreviation
      - neither bill nor susan is aware of the other's edits
      result:
      - the first one to make an update (countryName) succeeds
      - the second one to make an update (countryAbbreviation) fails
       */
      val updatedCountryName         = "FrostyLand"
      val updatedCountryAbbreviation = "FTLD"
      val result = withDB(
        setup = factory => loadCountry(iceland)(factory, ec),
        test = factory =>
          Try {
            val firstChange  = iceland.copy(countryName = CountryName.build(updatedCountryName))
            val secondChange = iceland.copy(countryAbbreviation = CountryAbbreviation.build(updatedCountryAbbreviation))
            Await.result(factory.countryDao.update(firstChange)(ec), delay) match {
              case Left(e) => fail(s"firstChange failed with", e)
              case Right(updated) =>
                updated.version shouldBe firstChange.version.next
                updated.createDate shouldBe firstChange.createDate
                updated.countryName shouldBe firstChange.countryName
                updated.countryAbbreviation shouldBe firstChange.countryAbbreviation
                updated.lastUpdate should not be firstChange.lastUpdate
            }
            Await.result(factory.countryDao.update(secondChange)(ec), delay) match {
              case Left(e) =>
                logger.info(s"failed with $e")
                e shouldBe a[InvalidVersionException]
              case Right(updated) =>
                logger.debug(s"after second change: $updated")
                fail(s"second change ($secondChange) with old version succeeded")
            }
          },
        tearDown = factory => deleteCountry(iceland.id)(factory, ec)
      )
      result.setupResult.failure shouldBe None
      result.tearDownResult.failure shouldBe None
      result.testResult.evaluate
    }
    it("should fail update with old version (async)") {
      /*
      scenario:
      - bill wants to update only the country name
      - susan wants to update only the country abbreviation
      - neither bill nor susan is aware of the other's edits
      result:
      - the first one to make an update (countryName) succeeds
      - the second one to make an update (countryAbbreviation) fails
       */
      val updatedCountryName         = "FrostyLand"
      val updatedCountryAbbreviation = "FTLD"
      val result = withDB(
        setup = factory => loadCountry(iceland)(factory, ec),
        test = factory =>
          Try {
            val firstChange  = iceland.copy(countryName = CountryName.build(updatedCountryName))
            val secondChange = iceland.copy(countryAbbreviation = CountryAbbreviation.build(updatedCountryAbbreviation))
            // launch async
            val attempt1 = factory.countryDao.update(firstChange)(ec)
            val attempt2 = factory.countryDao.update(secondChange)(ec)

            val finished = Await.result(Future.sequence(Seq(attempt1, attempt2)), delay)
            val countries = finished.collect { case Right(country) =>
              country
            }
            val errors = finished.collect { case Left(e) =>
              e
            }
            countries.size shouldBe 1
            errors.size shouldBe 1
          },
        tearDown = factory => deleteCountry(iceland.id)(factory, ec)
      )
      result.setupResult.failure shouldBe None
      result.tearDownResult.failure shouldBe None
      result.testResult.evaluate
    }
  }

  private def loadCountry(country: Country)(implicit factory: DaoFactory, ec: ExecutionContext): Try[Unit] = Try {
    Await.result(factory.countryDao.create(country), delay) match {
      case Left(e)  => fail(e)
      case Right(_) => ()
    }
  }

  private def deleteCountry(id: ID)(implicit factory: DaoFactory, ec: ExecutionContext): Try[Unit] = Try {
    Await.result(factory.countryDao.delete(id), delay) match {
      case Left(e) => TearDownException(s"could not delete country $id", Some(e))
      case Right(idOpt) =>
        idOpt match {
          case None => TearDownException(s"did not find country $id to delete")
          case Some(deleted) =>
            logger.info(s"deleted country: $id")
            if (deleted == id) Success()
            else TearDownException(s"deleted wrong country, actual: $deleted, expected: $id")
        }
    }
  }

}

object CountryDaoImplSpec {

  val iceland: Country = Country(
    ID.build,
    Version.defaultVersion,
    CountryName.build("Iceland"),
    CountryAbbreviation.build("ICL"),
    CreateDate.now,
    UpdateDate.now
  )

}
