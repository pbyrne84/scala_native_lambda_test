name := "scala-native-lambda-test-cleanser"

organization := "com.github.pbyrne84"

lazy val scala212 = "2.12.17"
lazy val scala213 = "2.13.10"
lazy val supportedScalaVersions = List(scala212, scala213)

scalaVersion := scala213

crossScalaVersions := supportedScalaVersions

//native-image-agent
val circeVersion = "0.14.3"
libraryDependencies ++= Vector(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "org.scalatest" %% "scalatest" % "3.2.14" % Test
)
