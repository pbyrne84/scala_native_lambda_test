name := "scala_native_lambda_test"

scalaVersion := "2.13.10"
//https://medium.com/@mateuszstankiewicz/aws-lambda-with-scala-and-graalvm-eb1cc46b7740
//https://blogs.oracle.com/developers/building-cross-platform-native-images-with-graalvm
//https://www.bks2.com/2019/05/17/scala-lambda-functions-with-graalvm/

//native-image-agent
libraryDependencies ++= Vector(
  "com.amazonaws" % "aws-lambda-java-core" % "1.2.1",
  "org.slf4j" % "slf4j-log4j12" % "1.7.30",
  "org.slf4j" % "jcl-over-slf4j" % "1.7.30",
  "org.apache.logging.log4j" % "log4j-api" % "2.13.0",
  "org.apache.logging.log4j" % "log4j-core" % "2.13.0",
  "org.scalatest" %% "scalatest" % "3.0.8" % Test
)

test in assembly := {}

//change name in generateConfig.sh if changed
lazy val jarName = "graalvm-scala-lambda.jar"

lazy val myNativeImageProject = (project in file("."))
  .enablePlugins(NativeImagePlugin)
  .settings(
    Compile / mainClass := Some("lambda.Main"),
    assemblyJarName in assembly := jarName
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

javaOptions in Test += "-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image"

nativeImageOptions ++= List(
  "--no-server",
  "--verbose",
  "--no-fallback",
  // "--allow-incomplete-classpath",
  //  "--report-unsupported-elements-at-runtime",
  //"--static",
  "-H:ConfigurationFileDirectories=src/main/resources/META-INF/native-image"
)

testOptions in Test += Tests.Cleanup(
  () =>
    (for {
      _ <- CleanNativeImageConf.cleanResourceBundles(List("org/scalatest/.*"))
      _ <- CleanNativeImageConf.cleanReflectConfig(List("org\\.scalatest.*"))
    } yield true).left
      .map(error => throw error)
)
