addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.6")
addSbtPlugin("org.scalameta" % "sbt-native-image" % "0.3.2")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.0.0")

libraryDependencies ++= List(
  //need to publish local scala_native_lambda_test_cleanser
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "log4j" % "log4j" % "1.2.17",
  "com.github.pbyrne84" %% "scala-native-lambda-test-cleanser" % "0.1.0-SNAPSHOT"
)
