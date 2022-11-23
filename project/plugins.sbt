addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.6")
addSbtPlugin("org.scalameta" % "sbt-native-image" % "0.3.2")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.0.0")

libraryDependencies ++= List(
  "io.circe" %% "circe-core" % "0.14.3",
  "io.circe" %% "circe-parser" % "0.14.3",
  "io.circe" %% "circe-generic" % "0.14.3"
)
