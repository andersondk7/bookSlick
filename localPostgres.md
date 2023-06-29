# Notes regarding postgres setup
This library handles the database driver and connection to the database internally.
The configuration is handled in the application.conf file and needs the following defined:
- host
- port 
- database
- schema
- user
- password
- numThreads

Currently host, port and database are hard coded and schema, user and password are defined as environment variables.

When launching postgres from a docker container, the command would look like:
docker run --name localPostgresDb -p 5432:5432 -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=***** postgres

When running psql, and selecting a specific schema:

`psql -h localhost -p 5432 book_biz -U userWithAccess`

and then from the command prompt: 

`set schema 'schemaName';`


