import Dependencies._

lazy val scala213 = "2.13.11"

ThisBuild / organization := "org.dka.book.slick"
ThisBuild / version := "0.5.1"
ThisBuild / scalaVersion := scala213

lazy val slick = (project in file("."))
  .configs(IntegrationTest)
  .settings(
    name := "bookSlick",
    libraryDependencies ++= Dependencies.slickDependencies,
    Defaults.itSettings
  )
