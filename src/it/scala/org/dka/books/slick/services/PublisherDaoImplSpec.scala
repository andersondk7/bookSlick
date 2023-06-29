package org.dka.books.slick.services

import com.typesafe.scalalogging.Logger
import org.dka.books.domain.services.InvalidVersionException
import org.dka.books.domain.model.fields.{ID, PublisherName, UpdateDate, Version, WebSite}
import org.dka.books.domain.model.item
import org.dka.books.domain.model.item.Publisher
import PublisherDaoImplSpec._
import org.dka.books.slick.TearDownException
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Success, Try}

class PublisherDaoImplSpec extends AnyFunSpec with DBTestRunner with Matchers {

  // for a test, this is fine ...
  implicit private val ec: ExecutionContext = ExecutionContext.global

  private val logger = Logger(getClass.getName)

  val delay: FiniteDuration = 10.seconds

  describe("conversion to/from db") {
    it("should convert from domain to db") {
      PublisherDaoImpl.toDB(rh) match {
        case None => fail(s"could not convert $rh")
        case Some((id, version, publisherName, locationId, webSite, createDate, updateDate)) =>
          id shouldBe rh.id.value.toString
          version shouldBe rh.version.value
          publisherName shouldBe rh.publisherName.value
          locationId shouldBe rh.locationId.map(_.value.toString)
          webSite shouldBe rh.webSite.map(_.value)
          createDate shouldBe rh.createDate.asTimestamp
          updateDate shouldBe rh.lastUpdate.map(_.asTimeStamp)
      }
    }
    it("should convert from db to domain") {
      val db = (
        rh.id.value.toString,
        rh.version.value,
        rh.publisherName.value,
        rh.locationId.map(_.value.toString),
        rh.webSite.map(_.value),
        rh.createDate.asTimestamp,
        rh.lastUpdate.map(_.asTimeStamp)
      )
      val converted = PublisherDaoImpl.fromDB(db)
      converted shouldBe rh
    }
  }

  describe("populating") {
    it("should add a publisher") {
      val result = withDB(
        setup = noSetup,
        test = factory =>
          Try {
            Await.result(factory.publisherDao.create(rh), delay) match {
              case Left(e) => fail(e)
              case Right(publisher) =>
                logger.debug(s"attempting to insert author.id: ${rh.id}")
                publisher.id shouldBe rh.id
            }
          },
        tearDown = factory => deletePublisher(rh.id)(factory, ec)
      )
      result.setupResult.failure shouldBe None
      result.tearDownResult.failure shouldBe None
      result.testResult.evaluate
    }
    it("should add multiple publishers") {
      val result = withDB(
        setup = noSetup,
        test = factory =>
          Try {
            val added = Future
              .sequence(multiplePublishers.map(id => factory.publisherDao.create(id)))
              .map(_.partitionMap(identity))
            val (errors, _) = Await.result(added, delay)
            if (errors.nonEmpty) throw errors.head
            // todo, need to be able to combine errors...
            else succeed
          },
        tearDown = factory => {
          val deleted = Future
            .sequence(multiplePublishers.map(publisher => factory.publisherDao.delete(publisher.id)))
            .map(_.partitionMap(identity))
          val (errors, _) = Await.result(deleted, delay)
          if (errors.nonEmpty) throw errors.head
          // todo, need to be able to combine errors...
          else Success()
        }
      )
      result.setupResult.failure shouldBe None
      result.tearDownResult.failure match {
        case Some(t) =>
          logger.warn(s"caught $t")
          t.printStackTrace()
        case None => logger.debug(s"no failures here")
      }
      result.tearDownResult.failure shouldBe None
      result.testResult.evaluate
    }

    it("should find a specific publisher") {
      val result = withDB(
        setup = factory => loadPublisher(ad)(factory, ec),
        test = factory =>
          Try {
            Await.result(factory.publisherDao.read(ad.id), delay) match {
              case Left(e)    => fail(e)
              case Right(opt) => opt.fold(fail(s"did not find $ad"))(publisher => publisher shouldBe ad)
            }
          },
        tearDown = factory => deletePublisher(ad.id)(factory, ec)
      )
      result.setupResult.failure shouldBe None
      result.tearDownResult.failure shouldBe None
      result.testResult.evaluate
    }
  }

  describe("updating") {
    it("should update once") {
      val updatedPublisherName = "Riley"
      val result = withDB(
        setup = factory => loadPublisher(hb)(factory, ec),
        test = factory =>
          Try {
            val updatedPublisher = hb.copy(publisherName = PublisherName.build(updatedPublisherName))
            Await.result(factory.publisherDao.update(updatedPublisher)(ec), delay) match {
              case Left(e) => fail(e)
              case Right(updated) =>
                updated.version shouldBe updatedPublisher.version.next
                updated.publisherName shouldBe updatedPublisher.publisherName
                updated.locationId shouldBe updatedPublisher.locationId
                updated.webSite shouldBe updatedPublisher.webSite
                updated.lastUpdate should not be updatedPublisher.lastUpdate
            }
          },
        tearDown = factory => deletePublisher(hb.id)(factory, ec)
      )
      result.setupResult.failure shouldBe None
      result.tearDownResult.failure shouldBe None
      result.testResult.evaluate
    }
    it("should fail update with old version (sequentially)") {
      /*
      scenario:
      - bill wants to update only the publisher name
      - susan wants to update only the web site
      - neither bill nor susan is aware of the other's edits
      result:
      - the first one to make an update (publisher name) succeeds
      - the second one to make an update (web site) fails
       */
      val updatedName    = "Riley"
      val updatedWebSite = Some("http://riley.com")
      val result = withDB(
        setup = factory => loadPublisher(hb)(factory, ec),
        test = factory =>
          Try {
            val firstChange  = hb.copy(publisherName = PublisherName.build(updatedName))
            val secondChange = hb.copy(webSite = WebSite.build(updatedWebSite))

            Await.result(factory.publisherDao.update(firstChange)(ec), delay) match {
              case Left(e) => fail(s"firstChange failed with", e)
              case Right(updated) =>
                updated.version shouldBe firstChange.version.next
                updated.publisherName shouldBe firstChange.publisherName
                updated.locationId shouldBe firstChange.locationId
                updated.webSite shouldBe firstChange.webSite
                updated.lastUpdate should not be firstChange.lastUpdate
            }
            logger.debug(s"secondChange: $secondChange")
            Await.result(factory.publisherDao.update(secondChange)(ec), delay) match {
              case Left(e) => e shouldBe a[InvalidVersionException]
              case Right(updated) =>
                logger.debug(s"updated:  $updated")
                fail(s"second change ($secondChange) with old version succeeded")
            }
          },
        tearDown = factory => deletePublisher(hb.id)(factory, ec)
      )
      result.setupResult.failure shouldBe None
      result.tearDownResult.failure shouldBe None
      result.testResult.evaluate
    }
    it("should fail update with old version (async)") {
      /*
      scenario:
      - bill wants to update only the publisher name
      - susan wants to update only the web site
      - neither bill nor susan is aware of the other's edits
      result:
      - the first one to make an update (publisher name) succeeds
      - the second one to make an update (web site) fails
      result:
      - since this is async, it is indeterminate which will succeed and which will fail
      -  but there will be one of each
       */
      val updatedName    = "Riley"
      val updatedWebSite = Some("http://riley.com")
      val result = withDB(
        setup = factory => loadPublisher(hb)(factory, ec),
        test = factory =>
          Try {
            val firstChange  = hb.copy(publisherName = PublisherName.build(updatedName))
            val secondChange = hb.copy(webSite = WebSite.build(updatedWebSite))
            // launch async
            val attempt1 = factory.publisherDao.update(firstChange)(ec)
            val attempt2 = factory.publisherDao.update(secondChange)(ec)

            val finished = Await.result(Future.sequence(Seq(attempt1, attempt2)), delay)
            val publishers = finished.collect { case Right(publisher) =>
              publisher
            }
            val errors = finished.collect { case Left(e) =>
              logger.info(s"error: ${e.getMessage}")
              e
            }
            publishers.size shouldBe 1
            errors.size shouldBe 1
          },
        tearDown = factory => deletePublisher(hb.id)(factory, ec)
      )
      result.setupResult.failure shouldBe None
      result.tearDownResult.failure shouldBe None
      result.testResult.evaluate
    }
  }

  private def loadPublisher(publisher: Publisher)(implicit factory: DaoFactory, ec: ExecutionContext): Try[Unit] = Try {
    Await.result(factory.publisherDao.create(publisher), delay) match {
      case Left(e)  => fail(e)
      case Right(_) => ()
    }
  }

  private def deletePublisher(id: ID)(implicit factory: DaoFactory, ec: ExecutionContext): Try[Unit] = Try {
    logger.info(s"deletePublisher: $id")
    logger.info(s"factory: $factory")
    Await.result(factory.publisherDao.delete(id), delay) match {
      case Left(e) => TearDownException(s"could not delete publisher $id", Some(e))
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

object PublisherDaoImplSpec {

  val rh: Publisher = item.Publisher(
    ID.build,
    Version.defaultVersion,
    PublisherName.build("RandomHouse"),
    None,
    Some(WebSite.build("www.random.com"))
  )

  val hb: Publisher = item.Publisher(
    ID.build,
    Version.defaultVersion,
    PublisherName.build("Hachette Book Group"),
    None,
    None
  )

  val hc: Publisher = item.Publisher(
    ID.build,
    Version.defaultVersion,
    PublisherName.build("Harper Collins"),
    None,
    Some(WebSite.build("www.harperCollins.com"))
  )

  val ad: Publisher = item.Publisher(
    ID.build,
    Version.defaultVersion,
    PublisherName.build("Addison-Wesley"),
    None,
    None
  )

  val multiplePublishers: Seq[Publisher] = Seq(hb, hc, ad)

  val publisherIds: Seq[ID] = PublisherDaoImplSpec.multiplePublishers.map(_.id)

}
