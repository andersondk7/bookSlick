package org.dka.books.slick.services

import org.dka.books.domain.services.AuthorDao
import org.dka.books.domain.model.fields.{CreateDate, FirstName, ID, LastName, LocationID, UpdateDate, Version}
import org.dka.books.domain.model.item.Author

import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

import java.sql.Timestamp
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

class AuthorDaoImpl(override val db: Database) extends CrudDaoImpl[Author] with AuthorDao {

  import AuthorDaoImpl._

  //
  // crud IO operations
  //
  override protected val singleCreateIO: Author => DBIO[Int] = author => tableQuery += author

  override protected val multipleCreateIO: Seq[Author] => DBIO[Option[Int]] = authors => tableQuery ++= authors

  override protected val getIO: (ID, ExecutionContext) => DBIO[Option[Author]] = (id, ec) =>
    tableQuery.filter(x => x.id === id.value.toString).result.map(x => x.headOption)(ec)

  override protected val deletedIO: ID => DBIO[Int] = id => tableQuery.filter(_.id === id.value.toString).delete

  override protected val updateAction: (Author, ExecutionContext) => DBIO[Author] = (item, ec) => {
    val updated = item.update
    tableQuery
      .filter(_.id === item.id.value.toString)
      .map(at =>
        (
          at.id,
          at.version,
          at.lastName,
          at.firstName,
          at.locationId,
          at.updateDate
        ))
      .update(
        (
          updated.id.value.toString,
          updated.version.value,
          updated.lastName.value,
          updated.firstName.map(_.value),
          updated.locationId.map(_.value.toString),
          updated.lastUpdate.map(_.asTimeStamp)
        )
      )
      .map(_ => updated)(ec) // convert number of rows updated to the updated item (i.e. updated version etc.)
  }

  //
  // additional IO operations
  // needed to support AuthorDao
  //

  //
  // implementation of AuthorDao methods
  //

}

object AuthorDaoImpl {

  val tableQuery = TableQuery[AuthorTable]

  class AuthorTable(tag: Tag)
    extends Table[Author](
      tag,
      None, // schema is set at connection time rather than a compile time, see DBConfig notes
      "authors") {

    val id = column[String]("id", O.PrimaryKey) // This is the primary key column

    val version = column[Int]("version")

    val lastName = column[String]("last_name")

    val firstName = column[Option[String]]("first_name")

    val locationId = column[Option[String]]("location_id")

    val createDate = column[Timestamp]("create_date")

    val updateDate = column[Option[Timestamp]]("update_date")

    // Every table needs a * projection with the same type as the table's type parameter
    override def * = (id, version, lastName, firstName, locationId, createDate, updateDate) <> (fromDB, toDB)

  }

  //
  // conversions between db and model
  // the model is guaranteed valid,
  // the db is assumed valid because the data only come from the model
  //

  private type AuthorTuple = (
    String,           // id
    Int,              // version
    String,           // last name
    Option[String],   // first name
    Option[String],   // location id
    Timestamp,        // createDate
    Option[Timestamp] // lastUpdate
  )

  def fromDB(tuple: AuthorTuple): Author = {
    val (id, version, lastName, firstName, locationId, createDate, updateDate) = tuple
    val result = Author(
      ID.build(UUID.fromString(id)),
      Version.build(version),
      lastName = LastName.build(lastName),
      firstName = firstName.map(FirstName.build),
      locationId = locationId.map(s => LocationID.build(UUID.fromString(s))),
      createDate = CreateDate.build(createDate),
      updateDate.map(UpdateDate.build)
    )
    result
  }

  def toDB(author: Author): Option[AuthorTuple] =
    Some(
      author.id.value.toString,
      author.version.value,
      author.lastName.value,
      author.firstName.map(_.value),
      author.locationId.map(_.value.toString),
      author.createDate.asTimestamp,
      author.lastUpdate.map(_.asTimeStamp)
    )

}

