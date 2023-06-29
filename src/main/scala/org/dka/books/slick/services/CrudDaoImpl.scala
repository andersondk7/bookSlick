package org.dka.books.slick.services

import com.typesafe.scalalogging.Logger

import scala.concurrent.{ExecutionContext, Future}
import org.dka.books.domain.services._
import org.dka.books.domain.services.DaoException.DaoErrorsOr
import org.dka.books.domain.model.fields.ID
import org.dka.books.domain.model.item.Updatable
import org.postgresql.util.PSQLException
import slick.dbio.DBIO
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.TransactionIsolation

/**
 * Implementation of a CrudDao using an instance of the database
 * @tparam D
 *   domain object stored
 */
trait CrudDaoImpl[D <: Updatable[D]] extends CrudDao[D] {

  def db: Database

  private val logger = Logger(getClass.getName)

  //
  // crud IO operations
  //
  protected def singleCreateIO: D => DBIO[Int]

  protected def multipleCreateIO: Seq[D] => DBIO[Option[Int]]

  protected def getIO: (ID, ExecutionContext) => DBIO[Option[D]]

  protected def deletedIO: ID => DBIO[Int]

  protected val updateAction: (D, ExecutionContext) => DBIO[D]

  override def create(item: D)(implicit ec: ExecutionContext): Future[DaoErrorsOr[D]] =
    db.run(singleCreateIO(item))
      .map { c: Int =>
        if (c == 1) Right(item)
        else Left(InsertException(s"${item.getClass.getName}: could not insert $item"))
      }

  override def create(items: Seq[D])(implicit ec: ExecutionContext): Future[DaoErrorsOr[Int]] =
    db.run(multipleCreateIO(items)).map {
      case Some(count) => Right(count)
      case None =>
        throw InsertException(s"${items.getClass.getName} could not insert ${items.size}")
    }

  override def read(id: ID)(implicit ec: ExecutionContext): Future[DaoErrorsOr[Option[D]]] =
    // note this only gets the first, assume that since id is the primary key, there will only be one!
    db.run(getIO(id, ec)).map(r => Right(r)) // don't know how to capture when it fails...

  override def delete(id: ID)(implicit ec: ExecutionContext): Future[DaoErrorsOr[Option[ID]]] =
    db.run(deletedIO(id)).map {
      case 0 => Right(None)
      case _ =>
        Right(Some(id)) // again assumes that since id is the primary key, there will only be one
    }

  override def update(item: D)(implicit ec: ExecutionContext): Future[DaoErrorsOr[D]] = {
    val getItemAction = getIO(item.id, ec).map {
      case None => Left(ItemNotFoundException(item.id))
      case Some(i) if i.version == item.version =>
        logger.info(s"update same version: ${i.version.value}, ${item.version.value}")
        logger.info(s"existing: $i, update: $item")
        Right(i)
      case Some(i) =>
        logger.info(s"update different version: ${i.version.value}, ${item.version.value}")
        Left(InvalidVersionException(item.version))
    }

    val combo = (for {
      target <- getItemAction
      _ = logger.info(s"target: $target")
      update <- updateAction(item, ec)
    } yield (target, update))
      // these type of transaction boundary does not block until the row is released, it blows up
      // only these levels work:
      // TransactionIsolation.RepeatableRead
      // TransactionIsolation.Serializable
      .transactionally.withTransactionIsolation(TransactionIsolation.RepeatableRead)

    db.run(combo)
      .map {
        case (Left(e), _) => Left(e)
        case (_, item)    => Right(item)
      }
      .recover { case e: PSQLException =>
        // plsql exception gives no useful information
          logger.warn(s"******************* caught exception: \n ${e.getSQLState}, message ${e.getMessage}, ${e.getCause} \n ****************")
        Left(InvalidVersionException(item.version))
      }
  }

}
