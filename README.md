# learning Slick (the functional orm)

## Purpose
This project is a proof of concept work to encapsulate database access using slick.

The output of this project is a library that can be consumed by various other projects with minimal exposure of the underlying database technology.

The library exposes an api consisting of data types, actions and possible exceptions.  It also provides an implementation of this api using slick.   Eventually the api could be refactored into a separate library and a slick implementation.  This would allow for different implementations in the future.

## Goals
 - investigate how slick works
 - investigate possible code organization patterns
 - investigate test approaches
 - learn more about integration with Postgres

## Database
This project uses a database hosted on a postgres server.  

Instructions on how to set up a local docker instance of postgres are found [here](localPostgres.md)

The database is called book_biz and represents a fictitious publishing company.  

### Environment
The following environment variables are required:
- *BZ_USER* -- the username for access to the book_biz database
- *BZ_PASSWORD* -- the password for access to the book_biz database
- *BZ_SCHEMA* -- the schema in the book_biz database

this schema is typically:
- *local* for individual testing
- *dev* for the shared development environment
- *qa* for separate qa testing
- *prod* for production deployments

## Usage
The consumer of the library must:
1. configure the library with an application.conf.  
   2. see [example](slick/src/test/resources/application.conf)
3. on startup create an instance of DaoFactory by calling DaoFactoryBuilder.configure.  If the result is a Left[ConfigurationException] then there you can't continue.  If it is a Right[DaoFactory] then use this to access the different dao classes to interact with the database
4. on shutdown call DaoFactoryBuilder.shutdown with the DaoFactory.database to clean up 

## Release Steps
This project is based on the git-flow pattern described by [Atlassian](https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow)

Specific steps are detailed [here](release.md)

## Code structure
The code structure is detailed [here](structure.md)

## Performance tests
Limited Performance testing is described [here](performance.md)