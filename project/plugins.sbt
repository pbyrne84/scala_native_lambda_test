addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")
addSbtPlugin("org.scalameta" % "sbt-native-image" % "0.2.2")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.15.0")

libraryDependencies ++= List(
  "io.circe" %% "circe-core" % "0.13.0",
  "io.circe" %% "circe-parser" % "0.13.0",
  "io.circe" %% "circe-generic" % "0.13.0"
)
