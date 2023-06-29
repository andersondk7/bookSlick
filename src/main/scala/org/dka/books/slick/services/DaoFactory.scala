package org.dka.books.slick.services

import com.typesafe.scalalogging.Logger

import org.dka.books.domain.services.ConfigurationException
import org.dka.books.domain.config.DBConfig
import org.dka.books.domain.config.DBConfig.ConfigErrorsOr

import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api._

import scala.util.{Failure, Try} // must be kept even though intellij thinks it is unused

/**
 * Factory to create all the dao implementations for the project
 * @param database
 *   database that the dao's will use
 */
class DaoFactory(val database: Database) {

  val countryDao: CountryDaoImpl = new CountryDaoImpl(database)

  val locationDao: LocationDaoImpl = new LocationDaoImpl(database)

  val authorDao: AuthorDaoImpl = new AuthorDaoImpl(database)

  val publisherDao: PublisherDaoImpl = new PublisherDaoImpl(database)

  val bookDao: BookDaoImpl = new BookDaoImpl(database)

  val authorsTitlesDao: AuthorsBooksDao = new AuthorsBooksDao(database)

}

/**
 * builds a DaoFactory there will be only one DaoFactory created (via a lazy val)
 *
 * reads the config to create a database and uses that database to construct the DaoFactory
 *
 * This builder is expected to be called by the client of the library (which could be tests or an application)
 */
object DaoFactory {

  private val logger = Logger(getClass.getName)

  lazy val configure: ConfigErrorsOr[DaoFactory] = {
    logger.info(s"loading configure")
    DBConfig.load
      .map { config =>
        // executor construction lifted from Database.forConfig()
        val executor = AsyncExecutor(
          config.connectionPool,
          config.numThreads,
          config.numThreads,
          config.queueSize,
          config.maxConnections,
          config.registerMBeans
        )
      val db      = Database.forURL(executor = executor, url = config.url)
      val factory = new DaoFactory(db)
      logger.info(s"got factory")
      factory
      }
  }

  def shutdown(database: Database): Try[Unit] = Try {
    database.close()
  }.recoverWith { case t: Throwable =>
    Failure(ConfigurationException(List(t.getMessage), Some(t)))
  }

}