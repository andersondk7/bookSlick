package org.dka.books.slick.services


import org.dka.books.domain.services.BookDao
import org.dka.books.domain.services.DaoException.DaoErrorsOr
import org.dka.books.domain.model.fields.{CreateDate, ID, Price, PublishDate, PublisherID, Title, UpdateDate, Version}
import org.dka.books.domain.model.item.{AuthorBookRelationship, Book}
import org.dka.books.domain.model.query.BookAuthorSummary

import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

import java.sql.Timestamp
import java.time.LocalDate
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

class BookDaoImpl(override val db: Database) extends CrudDaoImpl[Book] with BookDao {

  import BookDaoImpl._

  //
  // crud IO operations
  //
  override protected val singleCreateIO: Book => DBIO[Int] = title => tableQuery += title

  override protected val multipleCreateIO: Seq[Book] => DBIO[Option[Int]] = titles => tableQuery ++= titles

  override protected val getIO: (ID, ExecutionContext) => DBIO[Option[Book]] = (id, ec) =>
    tableQuery.filter(_.id === id.value.toString).result.map(_.headOption)(ec)

  override protected val deletedIO: ID => DBIO[Int] = id => tableQuery.filter(_.id === id.value.toString).delete

  override protected val updateAction: (Book, ExecutionContext) => DBIO[Book] = (item, ec) => {
    val updated = item.update
    tableQuery
      .filter(_.id === item.id.value.toString)
      .map(bt =>
        (
          bt.id,
          bt.version,
          bt.title,
          bt.price,
          bt.publisherId,
          bt.publishDate,
          bt.updateDate
        ))
      .update(
        (
          updated.id.value.toString,
          updated.version.value,
          updated.title.value,
          updated.price.value,
          updated.publisherID.map(_.value.toString),
          updated.publishDate.map(_.value),
          updated.lastUpdate.map(_.asTimeStamp)
        )
      )
      .map(_ => updated)(ec) // convert number of rows updated to the updated item (i.e. updated version etc.)
  }

  //
  // additional IO operations
  // needed to support BookDao
  //

  private val getAllIdsIO: ExecutionContext => DBIO[Seq[ID]] = ec =>
    tableQuery.result.map(seq => seq.map(at => at.id))(ec)

  private val bookAuthorSummaryIO: (ID, ExecutionContext) => DBIO[Seq[BookAuthorSummary]] = (bookId, ec) => {
    // the first join:  join author_books table and books table on bookId  -> (authorBookTable, bookTable)
    // second join:  join the first with authors table on authorBookTable.authorID == authorTable.id

    val innerJoin = for {
      ((authorBookTable, bookTable), authorTable) <-
        AuthorsBooksDao.tableQuery join
          BookDaoImpl.tableQuery on (_.bookId === _.id) join
          AuthorDaoImpl.tableQuery on (_._1.authorId === _.id)
    } yield ((authorBookTable, bookTable), authorTable)
    innerJoin
      // authorBookTable.bookId == bookTable.bookId
      .filter(_._1._1.bookId === bookId.value.toString)
      .result
      .map(seq =>
        seq.map { result =>
          val relationship: AuthorBookRelationship = result._1._1
          val book                                 = result._1._2 // from bookTable
          val author                               = result._2    // from authorTable
          BookAuthorSummary(relationship, book, author)
        })(ec)
  }

  //
  // implementation of BookDao methods
  //

  override def getAllIds(implicit ec: ExecutionContext): Future[DaoErrorsOr[Seq[ID]]] =
    db.run(getAllIdsIO(ec))
      .map(r => Right(r))

  override def getBookAuthorSummary(
    bookId: ID
  )(implicit ec: ExecutionContext
  ): Future[DaoErrorsOr[Seq[BookAuthorSummary]]] =
    db.run(bookAuthorSummaryIO(bookId, ec)).map(r => Right(r))

  def getAuthorsForBookSql(bookId: ID)(implicit ec: ExecutionContext): Future[DaoErrorsOr[List[BookAuthorSummary]]] = {
    val query =
      sql"select b.title, a.last_name, a.first_name, r.author_order from authors_books as r join books as b on b .id = r.book_id join authors as a on a .id = r.author_id where r .book_id = ${bookId.value.toString}"
        .as[(String, String, Option[String], Int)]
    db.run(query)
      .map(r => r.map(t => BookAuthorSummary.fromDB _ tupled t).toList)
      .map(f => Right(f))
  }

}

object BookDaoImpl {

  val tableQuery = TableQuery[BooksTable]

  class BooksTable(tag: Tag)
    extends Table[Book](
      tag,
      None, // schema is set at connection time rather than a compile time, see DBConfig notes
      "books") {

    val id = column[String]("id", O.PrimaryKey) // This is the primary key column

    val version = column[Int]("version")

    val title = column[String]("title")

    val price = column[BigDecimal]("price")

    val publisherId = column[Option[String]]("publisher_id")

    val publishDate = column[Option[LocalDate]]("publish_date")

    val createDate = column[Timestamp]("create_date")

    val updateDate = column[Option[Timestamp]]("update_date")

    // Every table needs a * projection with the same type as the table's type parameter
    override def * = (id, version, title, price, publisherId, publishDate, createDate, updateDate) <> (fromDB, toDB)

  }

  //
  // conversions between db and model
  // the model is guaranteed valid,
  // the db is assumed valid because the data only come from the model
  //

  private type BookTuple = (
    String,            // id
    Int,               // version
    String,            // title
    BigDecimal,        // price
    Option[String],    // publisherId
    Option[LocalDate], // published date
    Timestamp,         // create date
    Option[Timestamp]  // lastUpdate
  )

  def fromDB(tuple: BookTuple): Book = {
    val (id, version, title, price, publisherId, publishDate, createDate, updateDate) = tuple
    Book(
      ID.build(UUID.fromString(id)),
      Version.build(version),
      title = Title.build(title),
      price = Price.build(price),
      publisherID = publisherId.map(s => PublisherID.build(UUID.fromString(s))),
      publishDate = publishDate.map(d => PublishDate.build(d)),
      createDate = CreateDate.build(createDate),
      lastUpdate = updateDate.map(UpdateDate.build)
    )
  }

  def toDB(book: Book): Option[BookTuple] = Some(
    book.id.value.toString,
    book.version.value,
    book.title.value,
    book.price.value,
    book.publisherID.map(_.value.toString),
    book.publishDate.map(_.value),
    book.createDate.asTimestamp,
    book.lastUpdate.map(_.asTimeStamp)
  )

}
