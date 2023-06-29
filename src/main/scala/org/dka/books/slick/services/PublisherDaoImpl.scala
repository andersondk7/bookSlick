package org.dka.books.slick.services

import org.dka.books.domain.services.{LocationDao, PublisherDao}
import org.dka.books.domain.model.fields.{CreateDate, ID, LocationID, PublisherName, UpdateDate, Version, WebSite}
import org.dka.books.domain.model.item.Publisher
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

import java.sql.Timestamp
import java.util.UUID
import scala.concurrent.ExecutionContext

class PublisherDaoImpl(override val db: Database) extends CrudDaoImpl[Publisher] with PublisherDao {

  import PublisherDaoImpl._

  //
  // crud IO operations
  //
  override protected val singleCreateIO: Publisher => DBIO[Int] = publisher => tableQuery += publisher

  override protected val multipleCreateIO: Seq[Publisher] => DBIO[Option[Int]] = publishers => tableQuery ++= publishers


  override protected val getIO: (ID, ExecutionContext) => DBIO[Option[Publisher]] = (id, ec) =>
    tableQuery.filter(_.id === id.value.toString).result.map(_.headOption)(ec)

  override protected val deletedIO: ID => DBIO[Int] = id => tableQuery.filter(_.id === id.value.toString).delete

  override protected val updateAction: (Publisher, ExecutionContext) => DBIO[Publisher] = (item, ec) => {
    val updated = item.update
    tableQuery
      .filter(_.id === item.id.value.toString)
      .map(pt =>
        (
          pt.id,
          pt.version,
          pt.publisherName,
          pt.locationId,
          pt.website,
          pt.updateDate
        ))
      .update(
        (
          updated.id.value.toString,
          updated.version.value,
          updated.publisherName.value,
          updated.locationId.map(_.value.toString),
          updated.webSite.map(_.value),
          updated.lastUpdate.map(_.asTimeStamp)
        )
      )
      .map(_ => updated)(ec) // convert number of rows updated to the updated item (i.e. updated version etc.)
  }

  //
  // additional IO operations
  // needed to support PublisherDao
  //

}

object PublisherDaoImpl {

  val tableQuery = TableQuery[PublisherTable]

  class PublisherTable(tag: Tag)
    extends Table[Publisher](
      tag,
      None, // schema is set at connection time rather than a compile time, see DBConfig notes
      "publishers") {

    val id = column[String]("id", O.PrimaryKey) // This is the primary key column

    val version = column[Int]("version")

    val publisherName = column[String]("publisher_name")

    val locationId = column[Option[String]]("location_id")

    val website = column[Option[String]]("website")

    val createDate = column[Timestamp]("create_date")

    val updateDate = column[Option[Timestamp]]("update_date")

    override def * = (id, version, publisherName, locationId, website, createDate, updateDate) <> (fromDB, toDB)

  }

  //
  // conversions between db and model
  // the model is guaranteed valid,
  // the db is assumed valid because the data only come from the model
  //
  private type PublisherTuple = (
    String,           // id
    Int,              // version
    String,           // publisherName
    Option[String],   // locationId
    Option[String],   // website
    Timestamp,        // createDate
    Option[Timestamp] // updateDate
  )

  def fromDB(tuple: PublisherTuple): Publisher = {
    val (id, version, publisherName, locationId, webSite, createDate, updateDate) = tuple
    Publisher(
      ID.build(UUID.fromString(id)),
      Version.build(version),
      PublisherName.build(publisherName),
      LocationID.fromOpt(locationId),
      WebSite.build(webSite),
      CreateDate.build(createDate),
      updateDate.map(UpdateDate.build)
    )
  }

  def toDB(publisher: Publisher): Option[PublisherTuple] = Some(
    publisher.id.value.toString,
    publisher.version.value,
    publisher.publisherName.value,
    publisher.locationId.map(_.value.toString),
    publisher.webSite.map(_.value),
    publisher.createDate.asTimestamp,
    publisher.lastUpdate.map(_.asTimeStamp)
  )

}
