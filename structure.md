# Code structure

## Common
The _common_ module represents the database agnostic model and the data access patterns that use the model.
### Model
The model is split into 4 parts:
- _items_
- _fields_
- _validation_
- _query_

#### Items
The _item_ package in the model holds top level objects such as a book, an author, a publisher etc.  
Each of these top level objects are comprised of _field_ objects, such as an ID, firstName, lastName, price etc.  


#### Fields
The _field_ package in the model holds the fields that comprise the different _item_ objects.  A _field_ can be represented as in json with a _fieldName_ and _value_.  
The _fieldName_ is the json name and the _value_ is the underlying type such as a string, integer, UUID, LocalDate,  etc.  

In addition, each _field_ has validation parameters such as maxLength, minLength for a string.  These validations should also be enforced in the corresponding database layer.
This means that you can't have a FirstName with more characters than the business model has specified or that the underlying datastore can hold.

Each field is typed.  For example a FirstName is a different type as a LastName even though the underlying data type is a string.  This allows for compile-time checking when creating objects.  For example you can't inadvertently switch firstName and lastName when creating an Author.



#### Validation
_Validation_ reads and writes _Item_ objects with their corresponding _fields_ to and from json.  As part of reading from json, validation checks:
1. that the json is valid
2. all required fields in an item are present
3. each field is valid based on the business definitions (firstName does not exceed maxLength for example)

_Validation_ is split out based on the underlying type of the _fieldName_.  For example the same validation code is used for firstName and LastName and a different validation is used for UUID's etc.

#### Query
The _query_ package holds the data objects returned from dao queries.  These objects are also comprised of the same _fields_ as would be found in an _item_,  but since they are only written to json and never read, there is no validation for query objects.


### dao
The _dao_ module represents data access for the _items_.  Basic _create, read, update, delete, operations (aka _CRUD) are modeled in the _CrudDao_ and queries specific to a specific item are found in the Dao for each item.

## db
The _db_ project is used for setting up the database.  It consists of 2 parts:
1. database creation
2. database seeding

### Database Structure
_Database creation_ is handled by ordered sql scripts to create, alter, drop etc.  they must have the format Vxxxx__someName.sql where 'xxxx' is the order in which the script should be run.  

Database creation/migration is handled by the flyway library.  This library adds an extra table in the database called 'flyway_schema_history'.  This is used to manage which scripts have been run against a particular database instance.

The means that once a Vxxxx__someName.sql file has been committed, it should never be changed.  If changes are needed, an additional Vxxxx__someOtherName.sql should be written to make changes.

The sbt target is 'flywayMigrate' 

### Database Seed Data
If desired, seed data for any given environment (local, dev, qa, etc.) can be added using sql scripts to insert data.

By convention, these scripts have are named 'xxxx_someName' where 'xxxx' is the order in which the scripts should be run on a new database.
There is no specialized management of these scripts to make sure they are run in the correct order or that they are not run more than once.  This is out of scope for this project.

## slick

The _slick_ project implements the dao traits defined in _common_ using the defined _item_ and _field_ classes

It has 2 parts
- config
- dao

The _config_ package manages connecting to a postgres instance as defined in a set of environment variables (see [Environemnt](README.md)). 
Details can be found in the documentation for [DBConfig](slick/src/main/scala/org/dka/rdbms/slick/config/DBConfig.scala)

The _dao_ package implements each of the traits in the _common/dao_ package.  There are also additional DAO style objects as needed.

This package as a [DaoFactory](slick/src/main/scala/org/dka/rdbms/slick/dao/DaoFactory.scala) that contains all the individual DAO implementations (AuthorDaoImpl, BookDaoImpl, etc.)

Each _DaoImpl_ has the following structure:
- class that implements the corresponding dao trait from the common/dao package
    - the needed IO operations to support crud 
    - implementations of the additional dao methods
    - additional IO operations used by the implementations of the specific dao methods
- a companion object that:
  - defines the class representing the database table (extends Table[Item])
  - holds the instance of the corresponding TableQuery
  - defines the tuple used to read and write to the underlying database table
  - methods to read from and write to the underlying table
    - _fromDB_
    - _toDB_
  - it is assumed that the data in the db is valid (since it can only be inserted/modified by validated Item data) so no validation is performed on db reads and writes.
  