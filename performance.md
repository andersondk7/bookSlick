
# Setup
A test that joins 3 tables
>select b.title, a.last_name, a.first_name, r.author_order from
authors_books as r
join books as b on b.id = r.book_id
join authors as a on a.id = r.author_id
where r.book_id = bookId.value.toString

In load test there were 2000 books, each with 4 authors, yielding 8000 results.

A test was run where:
1. the query was run for just one book
2. the query was run concurrently for each book in the authors_books table. (each run in the same future, combined in the end)
3. the query was run sequentially for each book in the authors_books table. (run in a separate future, one after another)
4. the query was run for just one book again

The test was run on a laptop configured:
- 8 cores
- 32 GB ram
- postgres hosted in a docker image
Because the test and the postgresql instance were on the same machine, the network overhead was minimal

Tests were run from within Intellij  

### Config
For these tests, the database was configured:
- queueSize = 10000
- maxConnections = 8
- numThreads = 8

I believe the large queue size was needed for the concurrent test (combining of 2000 futures)


## Slick
There are 2 ways to execute queries in slick:
1. via scala map/flatmap etc.
2. via raw sql

### results
all times in milliseconds
#### as scala
- slick: first single query, time: 65
- slick: concurrent for 2000 queries, time: 4628, avg time: 2
- slick: sequential for 2000 queries, time: 25015, avg time: 12
- slick: last single query, time: 10
#### as sql
- sql: first single query, time: 35
- sql: concurrent for 2000 queries, time: 3131, avg time: 1
- sql: sequential for 2000 queries, time: 21933, avg time: 10
- sql: last single query, time: 11

#### slick summary
- the first run takes longer than subsequent runs
  - slick is ~6 times slower
  - sql is ~3 times slower
- queries based on sql are roughly 20% faster
- but this is a limited test

