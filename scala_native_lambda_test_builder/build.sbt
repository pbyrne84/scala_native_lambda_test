name := "scala_native_lambda_test_builder"

scalaVersion := "2.13.10"
//https://medium.com/@mateuszstankiewicz/aws-lambda-with-scala-and-graalvm-eb1cc46b7740
//https://blogs.oracle.com/developers/building-cross-platform-native-images-with-graalvm
//https://www.bks2.com/2019/05/17/scala-lambda-functions-with-graalvm/

val testcontainersScalaVersion = "0.40.11"

//assembly / assemblyMergeStrategy := MergeStrategy.last

ThisBuild / assemblyMergeStrategy := {
  case "application.conf" => MergeStrategy.concat
  case PathList("module-info.class") => MergeStrategy.discard
  case PathList("META-INF", "versions", xs @ _, "module-info.class") => MergeStrategy.discard
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}

val circeVersion = "0.14.3"

//native-image-agent
libraryDependencies ++= Vector(
  /*  "software.amazon.awssdk" % "auth" % "2.18.25",
  "software.amazon.awssdk" % "lambda" % "2.18.25",*/

  "io.microlam" % "slf4j-simple-lambda" % "2.0.3_1",
  "org.slf4j" % "slf4j-api" % "2.0.5",
  "org.slf4j" % "log4j-over-slf4j" % "2.0.5",
  "com.amazonaws" % "aws-lambda-java-core" % "1.2.2",
  "org.apache.logging.log4j" % "log4j-api" % "2.19.0",
  // "com.amazonaws" % "aws-java-sdk-lambda" % "1.12.353",
//  "com.amazonaws" % "aws-java-sdk-cloudwatch" % "1.12.353",
  "com.amazonaws" % "aws-java-sdk-core" % "1.12.350" % Test,
  "io.circe" %% "circe-parser" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "org.scalatest" %% "scalatest" % "3.2.14" % Test,
  "com.dimafeng" %% "testcontainers-scala-scalatest" % testcontainersScalaVersion % "test",
  "com.dimafeng" %% "testcontainers-scala-localstack" % testcontainersScalaVersion % "test"
)

assembly / test := {}

logBuffered := false

logLevel := Level.Info

//change name in generateConfig.sh if changed
lazy val jarName = "graalvm-scala-lambda.jar"

lazy val myNativeImageProject = (project in file("."))
  .enablePlugins(NativeImagePlugin)
  .settings(
    Compile / mainClass := Some("lambda.Main"),
    assembly / assemblyJarName := jarName
  )

// sudo apt install zlib1g-dev gcc
// sdk install java 19.3.5.r11-grl
// gu install native-image

/*
native-image  --no-server \
  --no-fallback \
  --allow-incomplete-classpath \
  --report-unsupported-elements-at-runtime \
  --static \
  --initialize-at-build-time=scala.runtime.Statics \
  -H:ConfigurationFileDirectories=lambdas/src/main/resources/META-INF/native-image \
  -H:+ReportExceptionStackTraces \
  -H:+TraceClassInitialization \
  -jar target/scala-2.13/graalvm-scala-lambda.jar
 */

/*
java  -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image -jar target/scala-2.13/graalvm-scala-lambda.jar
 */

//size is 13,808,984 before generating the confs via tests
//size is 15,390,184 after

fork := true

Test / javaOptions += "-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image"

nativeImageOptions ++= List(
  "--no-server",
  "--verbose",
  "--no-fallback",
  // "--allow-incomplete-classpath",
  //  "--report-unsupported-elements-at-runtime",
  // "--static",
  "-H:ConfigurationFileDirectories=src/main/resources/META-INF/native-image"
)

Test / testOptions ++= List(
  Tests.Cleanup(() =>
    (for {
      _ <- CleanReflectionConfig.filterAndRewrite(
        includeRegexes = List.empty,
        bundleRegexes = List(".*ScalaTestBundle.*"),
        nameRegexes = List("org.scalatest.*", "(.{0,2})sbt.*", "com.dimafeng.*")
      )
    } yield true).left
      .map(error => throw error)
  )
)
