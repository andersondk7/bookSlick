package org.dka.books.slick.services

import com.typesafe.scalalogging.Logger
import org.dka.books.slick.TearDownException
import org.dka.books.domain.services.InvalidVersionException
import org.dka.books.domain.model.fields.{CreateDate, ID, Price, Title, UpdateDate, Version}
import org.dka.books.domain.model.item.Book
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Success, Try}

class BookDaoImplSpec extends AnyFunSpec with DBTestRunner with Matchers {

  import BookDaoImplSpec._

  // for a test, this is fine ...
  implicit private val ec: ExecutionContext = ExecutionContext.global

  private val logger = Logger(getClass.getName)

  val delay: FiniteDuration = 10.seconds

  describe("conversion to/from db") {
    it("should convert from domain to db") {
      BookDaoImpl.toDB(pridePrejudice) match {
        case None => fail(s"could not convert $pridePrejudice")
        case Some((id, version, title, price, publisherId, publishedDate, createDate, updateDate)) =>
          id shouldBe pridePrejudice.id.value.toString
          version shouldBe pridePrejudice.version.value
          title shouldBe pridePrejudice.title.value
          price shouldBe pridePrejudice.price.value
          publisherId shouldBe pridePrejudice.publisherID.map(_.value.toString)
          publishedDate shouldBe pridePrejudice.publishDate.map(_.value)
          createDate shouldBe pridePrejudice.createDate.asTimestamp
          updateDate shouldBe pridePrejudice.lastUpdate.map(_.asTimeStamp)
      }
    }
    it("should convert from db to domain") {
      val db = (
        pridePrejudice.id.value.toString,
        pridePrejudice.version.value,
        pridePrejudice.title.value,
        pridePrejudice.price.value,
        None,
        None,
        pridePrejudice.createDate.asTimestamp,
        pridePrejudice.lastUpdate.map(_.asTimeStamp)
      )
      val converted = BookDaoImpl.fromDB(db)
      converted shouldBe pridePrejudice
    }
  }

  describe("populating") {
    it("should add an Book") {
      val result = withDB(
        setup = noSetup,
        test = factory =>
          Try {
            Await.result(factory.bookDao.create(pridePrejudice), delay) match {
              case Left(e) => fail(e)
              case Right(book) =>
                logger.debug(s"attempting to insert book.id: ${pridePrejudice.id}")
                book.id shouldBe pridePrejudice.id
            }
          },
        tearDown = factory => deleteBook(pridePrejudice.id)(factory, ec)
      )
      result.setupResult.failure shouldBe None
      result.tearDownResult.failure shouldBe None
      result.testResult.evaluate
    }
    it("should find a specific book") {
      val result = withDB(
        setup = factory => loadBook(pridePrejudice)(factory, ec),
        test = factory =>
          Try {
            Await.result(factory.bookDao.read(pridePrejudice.id), delay) match {
              case Left(e)    => fail(e)
              case Right(opt) => opt.fold(fail(s"did not find $pridePrejudice"))(book => book shouldBe pridePrejudice)
            }
          },
        tearDown = factory => deleteBook(pridePrejudice.id)(factory, ec)
      )
      result.setupResult.failure shouldBe None
      result.tearDownResult.failure shouldBe None
      result.testResult.evaluate
    }
  }

  describe("queries") {
    it("should find authors for a given book") {
      val bookId    = "e1de1c95-19e5-4df6-aa49-7c1f7b1d1868"
      val titleName = "Grimms Fairy Tales"
      val result = withDB(
        noSetup,
        test = factory =>
          Try {
            val response = Await.result(factory.bookDao.getBookAuthorSummary(ID.build(bookId)), delay)
            response match {
              case Left(e) =>
                fail(e)
              case Right(summaries) =>
                logger.debug(s"summaries: \n${summaries.mkString("\n")}")

                summaries.length shouldBe 2 // jacob and wilhelm
                val wilhelm = summaries.head
                val jacob   = summaries.tail.head
                wilhelm.titleName.value shouldBe titleName
                wilhelm.authorOrder shouldBe 2
                jacob.titleName.value shouldBe titleName
                jacob.authorOrder shouldBe 1
            }
          }.recoverWith { case t: Throwable =>
            logger.debug(s"caught $t")
            // t.printStackTrace()
            fail(t)
          },
        noSetup
      )
      logger.debug(s"setup:  ${result.setupResult}")
      result.setupGood shouldBe true
      result.tearDownGood shouldBe true
      result.testResult.evaluate
    }
    it("should find authors for a given book via sql") {
      val bookId    = "e1de1c95-19e5-4df6-aa49-7c1f7b1d1868"
      val titleName = "Grimms Fairy Tales"
      val result = withDB(
        noSetup,
        test = factory =>
          Try {
            val response = Await.result(factory.bookDao.getAuthorsForBookSql(ID.build(bookId)), delay)
            response match {
              case Left(e) =>
                fail(e)
              case Right(summaries) =>
                logger.debug(s"summaries: \n${summaries.mkString("\n")}")

                summaries.length shouldBe 2 // jacob and wilhelm
                val wilhelm = summaries.head
                val jacob   = summaries.tail.head
                wilhelm.titleName.value shouldBe titleName
                wilhelm.authorOrder shouldBe 2
                jacob.titleName.value shouldBe titleName
                jacob.authorOrder shouldBe 1
            }
          }.recoverWith { case t: Throwable =>
            logger.debug(s"caught $t")
//            t.printStackTrace()
            fail(t)
          },
        noSetup
      )
      logger.debug(s"setup:  ${result.setupResult}")
      result.setupGood shouldBe true
      result.tearDownGood shouldBe true
      result.testResult.evaluate
    }
  }

  describe("updating") {
    it("should update once") {
      val updatedTitle = "PP"
      val result = withDB(
        setup = factory => loadBook(pridePrejudice)(factory, ec),
        test = factory =>
          Try {
            val updatedBook = pridePrejudice.copy(title = Title.build(updatedTitle))
            Await.result(factory.bookDao.update(updatedBook)(ec), delay) match {
              case Left(e) => fail(e)
              case Right(updated) =>
                updated.version shouldBe pridePrejudice.version.next
                updated.title shouldBe Title.build(updatedTitle)
                updated.price shouldBe pridePrejudice.price
                updated.createDate shouldBe pridePrejudice.createDate
                updated.lastUpdate should not be pridePrejudice.lastUpdate
            }
          },
        tearDown = factory => deleteBook(pridePrejudice.id)(factory, ec)
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
      val updatedTitle = "prideBeforeFall"
      val updatedPrice = BigDecimal(123.45)
      val result = withDB(
        setup = factory => loadBook(pridePrejudice)(factory, ec),
        test = factory =>
          Try {
            val firstChange  = pridePrejudice.copy(title = Title.build(updatedTitle))
            val secondChange = pridePrejudice.copy(price = Price.build(updatedPrice))

            logger.debug(s"firstChange: $firstChange")
            Await.result(factory.bookDao.update(firstChange)(ec), delay) match {
              case Left(e) => fail(s"firstChange failed with", e)
              case Right(updated) =>
                updated.version shouldBe firstChange.version.next
                updated.title shouldBe firstChange.title
                updated.price shouldBe firstChange.price
                updated.createDate shouldBe firstChange.createDate
                updated.lastUpdate should not be firstChange.lastUpdate
            }
            Await.result(factory.bookDao.update(secondChange)(ec), delay) match {
              case Left(e)  => e shouldBe a[InvalidVersionException]
              case Right(_) => fail(s"second change ($secondChange) with old version succeeded")
            }
          },
        tearDown = factory => deleteBook(pridePrejudice.id)(factory, ec)
      )
      result.setupResult.failure shouldBe None
      result.tearDownResult.failure shouldBe None
      result.testResult.evaluate
    }
    it("should fail update with old version (async)") {
      /*
      scenario:
      - bill wants to update only the title of a book
      - susan wants to update only the price of a book
      - neither bill nor susan is aware of the other's edits
      result:
      - since this is async, it is indeterminate which will succeed and which will fail
      -  but there will be one of each
       */
      val updatedTitle = "prideBeforeFall"
      val updatedPrice = BigDecimal(123.45)
      val result = withDB(
        setup = factory => loadBook(pridePrejudice)(factory, ec),
        test = factory =>
          Try {
            val firstChange  = pridePrejudice.copy(title = Title.build(updatedTitle))
            val secondChange = pridePrejudice.copy(price = Price.build(updatedPrice))
            // launch async
            val attempt1 = factory.bookDao.update(firstChange)(ec)
            val attempt2 = factory.bookDao.update(secondChange)(ec)

            val finished = Await.result(Future.sequence(Seq(attempt1, attempt2)), delay)
            val books = finished.collect { case Right(book) =>
              logger.info(s"successful $book")
              book
            }
            val errors = finished.collect { case Left(e) =>
              logger.info(s"error: ${e.getMessage}")
              e
            }
            books.size shouldBe 1
            errors.size shouldBe 1
          },
        tearDown = factory => deleteBook(pridePrejudice.id)(factory, ec)
      )
      result.setupResult.failure shouldBe None
      result.tearDownResult.failure shouldBe None
      result.testResult.evaluate
    }
  }

  private def loadBook(book: Book)(implicit factory: DaoFactory, ec: ExecutionContext): Try[Unit] = Try {
    Await.result(factory.bookDao.create(book), delay) match {
      case Left(e)  => fail(e)
      case Right(_) => ()
    }
  }

  private def deleteBook(id: ID)(implicit factory: DaoFactory, ec: ExecutionContext): Try[Unit] = Try {
    logger.info(s"deleteBook: $id")
    logger.info(s"factory: $factory")
    Await.result(factory.bookDao.delete(id), delay) match {
      case Left(e) => TearDownException(s"could not delete book $id", Some(e))
      case Right(idOpt) =>
        idOpt match {
          case None => TearDownException(s"did not find book $id to delete")
          case Some(deleted) =>
            logger.info(s"deleted book: $id")
            if (deleted == id) Success()
            else TearDownException(s"deleted wrong book, actual: $deleted, expected: $id")
        }
    }
  }

}

object BookDaoImplSpec {

  val pridePrejudice: Book = Book(
    ID.build,
    Version.defaultVersion,
    Title.build("Pride and Prejudice"),
    Price.build(BigDecimal(12.34)),
    None, // publisherID
    None, // publishDate
    CreateDate.now,
    UpdateDate.now
  )

}
