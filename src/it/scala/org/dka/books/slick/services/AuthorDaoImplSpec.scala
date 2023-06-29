package org.dka.books.slick.services

import com.typesafe.scalalogging.Logger
import org.dka.books.domain.model.fields.{FirstName, ID, LastName, UpdateDate, Version}
import org.dka.books.domain.model.item.Author
import AuthorDaoImplSpec._
import org.dka.books.domain.services.InvalidVersionException
import org.dka.books.slick.TearDownException
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.sql.Timestamp
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Success, Try}

class AuthorDaoImplSpec extends AnyFunSpec with DBTestRunner with Matchers {

  // for a test, this is fine ...
  implicit private val ec: ExecutionContext = ExecutionContext.global

  private val logger = Logger(getClass.getName)

  val delay: FiniteDuration = 10.seconds

  describe("conversion to/from db") {
    it("should convert from domain to db") {
      AuthorDaoImpl.toDB(mt) match {
        case None => fail(s"could not convert $mt")
        case Some((id, version, last, first, locationId, createDate, lastUpdate)) =>
          id shouldBe mt.id.value.toString
          version shouldBe mt.version.value
          last shouldBe mt.lastName.value
          first shouldBe mt.firstName.map(_.value)
          locationId shouldBe mt.locationId.map(_.value.toString)
          lastUpdate.map(_.toLocalDateTime) shouldBe mt.lastUpdate
          createDate.toLocalDateTime shouldBe mt.createDate.value
      }
    }
    it("should convert from db to domain") {
      val db = (
        mt.id.value.toString,
        mt.version.value,
        mt.lastName.value,
        mt.firstName.map(_.value),
        mt.locationId.map(_.value.toString),
        Timestamp.valueOf(mt.createDate.value),
        mt.lastUpdate.map(t => Timestamp.valueOf(t.value))
      )
      val converted = AuthorDaoImpl.fromDB(db)
      converted shouldBe mt
    }
  }

  describe("populating") {
    it("should add an author") {
      val result = withDB(
        setup = noSetup,
        test = factory =>
          Try {
            Await.result(factory.authorDao.create(ja), delay) match {
              case Left(e) => fail(e)
              case Right(author) =>
                logger.debug(s"attempting to insert author.id: ${ja.id}")
                author.id shouldBe ja.id
            }
          },
        tearDown = factory => deleteAuthor(ja.id)(factory, ec)
      )
      result.setupResult.failure shouldBe None
      result.tearDownResult.failure shouldBe None
      result.testResult.evaluate
    }
    it("should add multiple authors") {
      val result = withDB(
        setup = noSetup,
        test = factory =>
          Try {
            val added = Future
              .sequence(multipleAuthors.map(id => factory.authorDao.create(id)))
              .map(_.partitionMap(identity))
            val (errors, _) = Await.result(added, delay)
            if (errors.nonEmpty) throw errors.head
            // todo, need to be able to combine errors...
            else succeed
          },
        tearDown = factory => {
          val deleted = Future
            .sequence(multipleAuthors.map(author => factory.authorDao.delete(author.id)))
            .map(_.partitionMap(identity))
          val (errors, _) = Await.result(deleted, delay)
          if (errors.nonEmpty) throw errors.head
          // todo, need to be able to combine errors...
          else Success()
        }
      )
      result.setupResult.failure shouldBe None
      result.tearDownResult.failure match {
        case Some(t) => t.printStackTrace()
        case None    => logger.debug(s"no failures here")
      }
      result.tearDownResult.failure shouldBe None
      result.testResult.evaluate
    }
    it("should find a specific author") {
      val result = withDB(
        setup = factory => loadAuthor(eh)(factory, ec),
        test = factory =>
          Try {
            Await.result(factory.authorDao.read(eh.id), delay) match {
              case Left(e)    => fail(e)
              case Right(opt) => opt.fold(fail(s"did not find $eh"))(author => author shouldBe eh)
            }
          },
        tearDown = factory => deleteAuthor(eh.id)(factory, ec)
      )
      result.setupResult.failure shouldBe None
      result.tearDownResult.failure shouldBe None
      result.testResult.evaluate
    }
  }

  describe("updating") {
    it("should update once") {
      val updatedLastName = "Hardy"
      val result = withDB(
        setup = factory => loadAuthor(nd)(factory, ec),
        test = factory =>
          Try {
            val updatedAuthor = nd.copy(lastName = LastName.build(updatedLastName))
            logger.info(s"initial: $nd")
            logger.info(s"before: $updatedAuthor")
            Await.result(factory.authorDao.update(updatedAuthor)(ec), delay) match {
              case Left(e) => fail(e)
              case Right(updated) =>
                logger.info(s"after:  $updated")
                updated.version shouldBe nd.version.next
                updated.lastName shouldBe LastName.build(updatedLastName)
                updated.firstName shouldBe nd.firstName
                updated.createDate shouldBe nd.createDate
                updated.lastUpdate should not be nd.lastUpdate
            }
          },
        tearDown = factory => deleteAuthor(nd.id)(factory, ec)
      )
      result.setupResult.failure shouldBe None
      result.tearDownResult.failure shouldBe None
      result.testResult.evaluate
    }
    it("should fail update with old version (sequentially)") {
      /*
      scenario:
      - bill wants to update only the first name of an author
      - susan wants to update only the last name of an author
      - neither bill nor susan is aware of the other's edits
      result:
      - the first one to make an update (first Name) succeeds
      - the second one to make an update (last Name) fails
       */
      val updatedFirstName = "Nanette"
      val updatedLastName  = "Drewmore"
      val result = withDB(
        setup = factory => loadAuthor(nd)(factory, ec),
        test = factory =>
          Try {
            val firstChange  = nd.copy(firstName = FirstName.build(Some(updatedFirstName)), lastUpdate = UpdateDate.now)
            val secondChange = nd.copy(lastName = LastName.build(updatedLastName), lastUpdate = UpdateDate.now)

            logger.debug(s"firstChange: $firstChange")
            Await.result(factory.authorDao.update(firstChange)(ec), delay) match {
              case Left(e)        => fail(s"firstChange failed with", e)
              case Right(updated) =>
                // everything but updateDate, since the exact time of the update is somewhat indeterminate
                updated.id shouldBe firstChange.id
                updated.version shouldBe firstChange.version.next
                updated.lastName shouldBe firstChange.lastName
                updated.firstName shouldBe firstChange.firstName
                updated.locationId shouldBe firstChange.locationId
                updated.createDate shouldBe firstChange.createDate
                updated.lastUpdate should not be firstChange.lastUpdate // verify only that it changed
            }
            logger.debug(s"secondChange: $secondChange")
            Await.result(factory.authorDao.update(secondChange)(ec), delay) match {
              case Left(e) =>
                e shouldBe a[InvalidVersionException]
              case Right(updated) =>
                logger.debug(s"updated:  $updated")
                fail(s"second change ($secondChange) with old version succeeded")
            }
          },
        tearDown = factory => deleteAuthor(nd.id)(factory, ec)
      )
      result.setupResult.failure shouldBe None
      result.tearDownResult.failure shouldBe None
      result.testResult.evaluate
    }
    it("should fail update with old version (async)") {
      /*
      scenario:
      - bill wants to update only the first name of an author
      - susan wants to update only the last name of an author
      - neither bill nor susan is aware of the other's edits
      result:
      - since this is async, it is indeterminate which will succeed and which will fail
      -  but there will be one of each
       */
      val updatedFirstName = "Nanette"
      val updatedLastName  = "Drewmore"
      val result = withDB(
        setup = factory => loadAuthor(nd)(factory, ec),
        test = factory =>
          Try {
            val firstChange  = nd.copy(firstName = FirstName.build(Some(updatedFirstName)))
            val secondChange = nd.copy(lastName = LastName.build(updatedLastName))
            // launch async
            val attempt1 = factory.authorDao.update(firstChange)(ec)
            val attempt2 = factory.authorDao.update(secondChange)(ec)

            val finished = Await.result(Future.sequence(Seq(attempt1, attempt2)), delay)
            val authors = finished.collect { case Right(author) =>
              logger.info(s"successful $author")
              author
            }
            val errors = finished.collect { case Left(e) =>
              logger.info(s"error: ${e.getMessage}")
              e
            }
            authors.size shouldBe 1
            errors.size shouldBe 1
          },
        tearDown = factory => deleteAuthor(nd.id)(factory, ec)
      )
      result.setupResult.failure shouldBe None
      result.tearDownResult.failure shouldBe None
      result.testResult.evaluate
    }
  }

  private def loadAuthor(author: Author)(implicit factory: DaoFactory, ec: ExecutionContext): Try[Unit] = Try {
    Await.result(factory.authorDao.create(author), delay) match {
      case Left(e)  => fail(e)
      case Right(_) => ()
    }
  }

  private def deleteAuthor(id: ID)(implicit factory: DaoFactory, ec: ExecutionContext): Try[Unit] = Try {
    logger.info(s"deleteAuthor: $id")
    logger.info(s"factory: $factory")
    Await.result(factory.authorDao.delete(id), delay) match {
      case Left(e) => TearDownException(s"could not delete author $id", Some(e))
      case Right(idOpt) =>
        idOpt match {
          case None => TearDownException(s"did not find author $id to delete")
          case Some(deleted) =>
            logger.info(s"deleted author: $id")
            if (deleted == id) Success()
            else TearDownException(s"deleted wrong author, actual: $deleted, expected: $id")
        }
    }
  }

}

object AuthorDaoImplSpec {

  val jm: Author = Author(
    ID.build,
    Version.defaultVersion,
    LastName.build("Milton"),
    Some(FirstName.build("John")),
    None // locationId
  )

  val ja: Author = Author(
    ID.build,
    Version.defaultVersion,
    LastName.build("Austen"),
    Some(FirstName.build("Jane")),
    None
  )

  val nd: Author = Author(
    ID.build,
    Version.defaultVersion,
    LastName.build("Drew"),
    Some(FirstName.build("Nancy")),
    None
  )

  val mt: Author = Author(
    ID.build,
    Version.defaultVersion,
    LastName.build("Twain"),
    Some(FirstName.build("Mark")),
    None
  )

  val eh: Author = Author(
    ID.build,
    Version.defaultVersion,
    LastName.build("Hemmingway"),
    None,
    None
  )

  val multipleAuthors: Seq[Author] = Seq(ja, jm, nd, mt)

  val authorIds: Seq[ID] = AuthorDaoImplSpec.multipleAuthors.map(_.id)

}
