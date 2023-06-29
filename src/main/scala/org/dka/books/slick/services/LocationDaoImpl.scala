package org.dka.books.slick.services

import org.dka.books.domain.services.LocationDao
import org.dka.books.domain.model.fields.{CountryID, CreateDate, ID, LocationAbbreviation, LocationName, UpdateDate, Version}
import org.dka.books.domain.model.item.Location

import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery


import java.sql.Timestamp
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

class LocationDaoImpl(override val db: Database) extends CrudDaoImpl[Location] with LocationDao {

  import LocationDaoImpl._

  //
  // crud IO operations
  //
  override protected val singleCreateIO: Location => DBIO[Int] = location => tableQuery += location

  override protected val multipleCreateIO: Seq[Location] => DBIO[Option[Int]] = locations => tableQuery ++= locations

  override protected val getIO: (ID, ExecutionContext) => DBIO[Option[Location]] = (id, ec) =>
    tableQuery.filter(_.id === id.value.toString).result.map(_.headOption)(ec)

  override protected val deletedIO: ID => DBIO[Int] = id => tableQuery.filter(_.id === id.value.toString).delete

  override protected val updateAction: (Location, ExecutionContext) => DBIO[Location] = (item, ec) => {
    val updated = item.update
    tableQuery
      .filter(_.id === item.id.value.toString)
      .map(lt =>
        (
          lt.id,
          lt.version,
          lt.locationName,
          lt.locationAbbreviation,
          lt.countryID,
          lt.updateDate
        ))
      .update(
        (
          updated.id.value.toString,
          updated.version.value,
          updated.locationName.value,
          updated.locationAbbreviation.value,
          updated.countryID.value.toString,
          updated.lastUpdate.map(_.asTimeStamp)
        ))
      .map(_ => updated)(ec) // convert number of rows updated to the updated item (i.e. updated version etc.)
  }

  //
  // additional IO operations
  // needed to support LocationDao
  //
}

object LocationDaoImpl {

  val tableQuery = TableQuery[LocationTable]

  class LocationTable(tag: Tag)
    extends Table[Location](
      tag,
      None, // schema is set at connection time rather than a compile time, see DBConfig notes
      "locations") {

    val id = column[String]("id", O.PrimaryKey) // This is the primary key column

    val version = column[Int]("version")

    val locationName = column[String]("location_name")

    val locationAbbreviation = column[String]("location_abbreviation")

    val countryID = column[String]("country_id")

    val createDate = column[Timestamp]("create_date")

    val updateDate = column[Option[Timestamp]]("update_date")

    // Every table needs a * projection with the same type as the table's type parameter
    override def * =
      (id, version, locationName, locationAbbreviation, countryID, createDate, updateDate) <> (fromDB, toDB)

  }

  //
  // conversions between db and model
  // the model is guaranteed valid,
  // the db is assumed valid because the data only come from the model
  //

  private type LocationTuple = (
    String,           // id
    Int,              // version
    String,           // location_name
    String,           // location_abbreviation
    String,           // country_id
    Timestamp,        // create date
    Option[Timestamp] // update date
  )

  def fromDB(tuple: LocationTuple): Location = {
    val (id, version, locationName, locationAbbreviation, countryID, createDate, updateDate) = tuple
    Location(
      ID.build(UUID.fromString(id)),
      Version.build(version),
      locationName = LocationName.build(locationName),
      locationAbbreviation = LocationAbbreviation.build(locationAbbreviation),
      countryID = CountryID.build(UUID.fromString(countryID)),
      createDate = CreateDate.build(createDate),
      lastUpdate = updateDate.map(UpdateDate.build)
    )
  }

  def toDB(location: Location): Option[LocationTuple] = Some(
    location.id.value.toString,
    location.version.value,
    location.locationName.value,
    location.locationAbbreviation.value,
    location.countryID.value.toString,
    location.createDate.asTimestamp,
    location.lastUpdate.map(_.asTimeStamp)
  )

}
