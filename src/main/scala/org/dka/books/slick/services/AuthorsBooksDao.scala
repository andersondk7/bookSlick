package org.dka.books.slick.services


import org.dka.books.domain.model.fields.ID
import org.dka.books.domain.model.item.AuthorBookRelationship
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

import java.util.UUID
import scala.language.implicitConversions

class AuthorsBooksDao(val db: Database) {}

object AuthorsBooksDao {

  val tableQuery = TableQuery[AuthorsBooksTable]

  class AuthorsBooksTable(tag: Tag)
    extends Table[AuthorBookRelationship](
      tag,
      None, // schema is set at connection time rather than a compile time, see DBConfig notes
      "authors_books") {

    val authorId = column[String]("author_id")

    val bookId = column[String]("book_id")

    val authorOrder = column[Int]("author_order")

    // Every table needs a * projection with the same type as the table's type parameter
    override def * = (authorId, bookId, authorOrder) <> (fromDB, toDB)

  }

  //
  // conversions between db and model
  // the model is guaranteed valid,
  // the db is assumed valid because the data only come from the model
  //

  private type AuthorTitleTuple = (
    String, // authorID
    String, // bookId
    Int     // authorOrder
  )

  def fromDB(tuple: AuthorTitleTuple): AuthorBookRelationship = {
    val (authorId, bookId, authorOrder) = tuple
    AuthorBookRelationship(
      authorId = ID.build(UUID.fromString(authorId)),
      bookId = ID.build(UUID.fromString(bookId)),
      authorOrder = authorOrder
    )
  }

  def toDB(relationship: AuthorBookRelationship): Option[AuthorTitleTuple] = Some(
    relationship.authorId.value.toString,
    relationship.bookId.value.toString,
    relationship.authorOrder
  )

}