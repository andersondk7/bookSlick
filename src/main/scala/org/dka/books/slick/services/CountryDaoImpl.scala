package org.dka.books.slick.services

import org.dka.books.domain.model.fields.{CountryAbbreviation, CountryName, CreateDate, ID, UpdateDate, Version}
import org.dka.books.domain.model.item.Country
import org.dka.books.domain.services.CountryDao
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

import java.sql.Timestamp
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

class CountryDaoImpl(override val db: Database) extends CrudDaoImpl[Country] with CountryDao {

  import CountryDaoImpl._

  //
  // crud IO operations
  //
  override protected val singleCreateIO: Country => DBIO[Int] = country => tableQuery += country

  override protected val multipleCreateIO: Seq[Country] => DBIO[Option[Int]] = countries => tableQuery ++= countries

  override protected val getIO: (ID, ExecutionContext) => DBIO[Option[Country]] = (id, ec) =>
    tableQuery.filter(_.id === id.value.toString).result.map(_.headOption)(ec)

  override protected val deletedIO: ID => DBIO[Int] = id => tableQuery.filter(_.id === id.value.toString).delete

  override protected val updateAction: (Country, ExecutionContext) => DBIO[Country] = (item, ec) => {
    val updated = item.update
    tableQuery
      .filter(_.id === item.id.value.toString)
      .map(ct =>
        (
          ct.id,
          ct.version,
          ct.countryName,
          ct.countryAbbreviation,
          ct.updateDate
        ))
      .update(
        (
          updated.id.value.toString,
          updated.version.value,
          updated.countryName.value,
          updated.countryAbbreviation.value,
          updated.lastUpdate.map(_.asTimeStamp)
        )
      )
      .map(_ => updated)(ec) // convert number of rows updated to the updated item (i.e. updated version etc.)

  }

  //
  // additional IO operations
  // needed to support CountryDao
  //
}

object CountryDaoImpl {

  val tableQuery = TableQuery[CountryTable]

  class CountryTable(tag: Tag)
    extends Table[Country](
      tag,
      None, // schema is set at connection time rather than a compile time, see DBConfig notes
      "countries") {

    val id = column[String]("id", O.PrimaryKey) // This is the primary key column

    val version = column[Int]("version")

    val countryName = column[String]("country_name")

    val countryAbbreviation = column[String]("country_abbreviation")

    val createDate = column[Timestamp]("create_date")

    val updateDate = column[Option[Timestamp]]("update_date")

    // Every table needs a * projection with the same type as the table's type parameter
    override def * = (id, version, countryName, countryAbbreviation, createDate, updateDate) <> (fromDB, toDB)

  }

  //
  // conversions between db and model
  // the model is guaranteed valid,
  // the db is assumed valid because the data only come from the model
  //

  private type CountryTuple = (
    String,           // id
    Int,              // version
    String,           // country_name
    String,           // country_abbreviation
    Timestamp,        // create date
    Option[Timestamp] // update date
  )

  def fromDB(tuple: CountryTuple): Country = {
    val (id, version, countryName, countryAbbreviation, createDate, updateDate) = tuple
    Country(
      ID.build(UUID.fromString(id)),
      Version.build(version),
      countryName = CountryName.build(countryName),
      countryAbbreviation = CountryAbbreviation.build(countryAbbreviation),
      createDate = CreateDate.build(createDate),
      lastUpdate = updateDate.map(UpdateDate.build)
    )
  }

  def toDB(country: Country): Option[CountryTuple] = Some(
    country.id.value.toString,
    country.version.value,
    country.countryName.value,
    country.countryAbbreviation.value,
    country.createDate.asTimestamp,
    country.lastUpdate.map(_.asTimeStamp)
  )

}
