# Scala native lambdas

## Purpose of the experiment
Startup times with jvm based languages can make scala non-ideal for lambdas that have a high call rate that are expected to finish quickly such
as sqs message consumption. There is some argument for anything of real complication using dockerized sqs stream processing as it can 
fit nicely into the current dockerized workflow and easily fit into the same tracing and logging strategies. A pure function is observable
so can be ideal for serverless but when things start involving a lot of side effect the design can up being like a Rube Goldberg machine,
all the issues of microservices but multiplied.

I would personally not use native images for things like http servers as they can rely on runtime instrumentation and that will break.

The size of the lambdas memory does affect cold starts, default was billing time of about 1.5 seconds, 512MB about .5 seconds then halved again for
1024MB at 261 ms. The lambdas also finished faster with warm starts with warm memory. For 512MB Billed Duration: 53 ms and 1024 MB 31 ms.

<https://arnoldgalovics.com/java-cold-start-aws-lambda-graalvm/> has examples showing how memory affects start up time.

[testRunLog.txt](scala_native_lambda_test_builder/testRunLog.txt) has my own findings. Once warm 53ms.


## Technologies
1. Docker image based upon amazonlinux:2 (CentOS based) with dev tools as amazonlinux is a very light image, we want things to run in a lambda.
2. Graal vm (sdk install java 22.3.r11-grl (sdkman install)), version is important as the config file structures can change breaking these instructions.
3. Scala 2.13
4. Native image <https://www.graalvm.org/reference-manual/native-image/> (gu install native-image)

## Organisation of projects

### [scala_native_lambda_test_cleanser](scala_native_lambda_test_cleanser)

This is a utility than can be published locally with **sbt +publishLocal**. This allows us to make filters to remove needless junk when we run the tests
of the application using. This is split out to allow it to be tested to some degree. More fun than monkey pawing edge cases
within sbt or any build environment.

```scala
Test / javaOptions += "-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image"
```
This will catch any reflection calls our library dependencies use. It does mean we need to exercise the code well in our tests. This will also create a lot of junk
in the config files which we well will need to filter out using this cleanser. The good news is we have entered the world of the native image breaking on build
versus breaking on runtime as it will tell you what you need to filter out such as sbt stuff.

### Including this local published resource for running the tests
You can add it to the library dependencies in the plugins.sbt file e.g.

```scala
libraryDependencies ++= List(
  "com.github.pbyrne84" %% "scala-native-lambda-test-cleanser" % "0.1.0-SNAPSHOT"
)
```

[scala_native_lambda_test_builder/project/plugins.sbt](scala_native_lambda_test_builder/project/plugins.sbt)

### [scala_native_lambda_test_builder](scala_native_lambda_test_builder)

This project is an example of
1. Building an aws docker image that has the required scala and jvm etc. [Dockerfile](scala_native_lambda_test_builder/Dockerfile).
   It also has other development tools we require this to work
2. A Pulumi based infrastructure as code example. This creates a sqs queue that will fire off our lambda with the message. This can
   be found at [scala_native_lambda_test_builder/pulumi/buildEnvironment.ts](scala_native_lambda_test_builder/pulumi/buildEnvironment.ts).
   Renamed from index.ts as there are enough index.ts files in the world. I can go on a big side track on how index.ts breaks all ability to 
   keyboard navigate without contextual overload all the time. We can zip up our build and this will upload it all.
3. A code example of something that reads said message using circe. The decoder uses generics for the message body, this allows us to easily
   define body structures such as simple text of encoded json which allows us to have type safe object structures. 
   [SqsDecodingSpec.scala](scala_native_lambda_test_builder%2Fsrc%2Ftest%2Fscala%2Flambda%2FSqsDecodingSpec.scala) has examples of this.
   It does use some hackery to pass the message in from the bootstrap file as the lambda shell environment is quite limited and I got annoyed 
   trying to make the message a single line. Really we should try and read the message within the lambda code as exampled in
   [https://github.com/redskap/aws-lambda-java-runtime](https://github.com/redskap/aws-lambda-java-runtime). A later task.
    
#### Graalvm, jdk and reflection
When building a native image only the code from explicit execution calls gets auto bound in. Calls that are done via reflection are ignored,
java based dependencies tend to use reflection a lot more so not all things would be compiled in. This leads to NoSuchMethodException being thrown
and other similar errors exampled here <https://github.com/oracle/graal/issues/1261>. There are a set of configuration files that can be
added to the resources that can manage these sorts of problems. This is covered later as we can make things more child-proof and hassle-free.

### agentlib:native-image-agent
The **agentlib:native-image-agent** instrumentation scans the execution paths making note of things like reflection calls generating all the configuration.
Note how I said you need like good test coverage to make life simple? We can run this while running tests to build the config.
The generated files can be found in 
[scala_native_lambda_test_builder/src/main/resources/META-INF/native-image](scala_native_lambda_test_builder/src/main/resources/META-INF/native-image).

#### Running the instrumentation when running tests via SBT

```scala
fork := true // This start a new java process as we cannot add instrumentation post start in the current

javaOptions in Test += "-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image"
```

#### Cleaning up the configs post running tests
The issue with running the instrumentation while running tests in sbt is we pick up stragglers that break the conversion. 
To solve this problem I created a test shutdown hook that filters things out via circe.


[CleanReflectionConfig.scala](scala_native_lambda_test_builder%2Fproject%2FCleanReflectionConfig.scala) is used to apply
filters across 

```scala
  private val nativeImageConfigDir = "src/main/resources/META-INF/native-image/"
  private val resourceConfigFileLocation = nativeImageConfigDir + "resource-config.json"
  private val reflectionConfigFileLocation = nativeImageConfigDir + "reflect-config.json"
  private val serializationConfigFileLocation = nativeImageConfigDir + "serialization-config.json"
```

And is used by adding the following to the build.sbt file.
```scala
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
```

There are warnings when compiling about things in the reflection-config not existing from the test scope etc., these can be 
ignored if wished.

### What the lambda does
It simply parses with circe the incoming message. The implementation is generic based, though the current usage one is 
simply the string message body one.

[https://github.com/pbyrne84/scala_native_lambda_test/blob/main/scala_native_lambda_test_builder/src/main/scala/lambda/SqsDecoding.scala](https://github.com/pbyrne84/scala_native_lambda_test/blob/main/scala_native_lambda_test_builder/src/main/scala/lambda/SqsDecoding.scala)

The tests showing how to decode an encoded object can be found here

[https://github.com/pbyrne84/scala_native_lambda_test/blob/main/scala_native_lambda_test_builder/src/test/scala/lambda/SqsDecodingSpec.scala](https://github.com/pbyrne84/scala_native_lambda_test/blob/main/scala_native_lambda_test_builder/src/test/scala/lambda/SqsDecodingSpec.scala)

```scala
"when the message body is encoded json of an object that can be a case class" in {
  val exampleBody = ExampleBody(name = "Unknown")
  // playing the add the backslashes game is always fun
  val encodedBody = "\"{\\n  \\\"name\\\" : \\\"Unknown\\\"\\n}\""

  val jsonWithEncodedBody = createMessageJson(encodedBody)
  val expectedConversion = createSqsDecoding(exampleBody)

  implicit val sqsMessageStrongDecoder: Decoder[SqsDecoding[ExampleBody]] =
    ExampleBody.sqsEncodedMessageBodyStringDecoder

  io.circe.parser.decode[SqsDecoding[ExampleBody]](jsonWithEncodedBody.spaces2) shouldBe Right(expectedConversion)
}
```


### Building the images

The image creation requires a fat jar as we pass the path of that to the **native-image** location.

1. We run assembly and then log in to the docker image to build our actual executable. The executable has to be built 
   natively. The folder mount is below the build project, this allows us to access both projects.
   ```bash
   sbt test #generate all the config files
   sbt assembly
   docker build -t amazon_scala_vm .
   docker run --name amazon_lambda_compile --mount src="$(pwd)/../",target=/root/project_mount,type=bind -t -d amazon_scala_vm
   docker exec -it amazon_lambda_compile bash
   ```

2. Then within the image we publish locally the cleanser.
   ```bash
   cd /root/project_mount/scala_native_lambda_test_cleanser
   sbt +publishLocal
   cd ../scala_native_lambda_test_builder
   ./linux_build.sh
   ```
  
   Or single line if life is favourable.
   ```bash
   docker exec -it  amazon_lambda_compile  /bin/bash -c "cd /root/project_mount/scala_native_lambda_test_builder;./linux_build.sh"
   ```
     

This should exit after building and attempting to run the executable.

3. Verify you have a ``graalvm-scala-lambda`` executable in the [scala_native_lambda_test_builder](scala_native_lambda_test_builder) 
   project folder.


#### Building the zip file for the lambda

We need to package our executable up with a bootstrap file into a zip to upload. Amazon uses a **bootstrap** file to run
any native executables.

Example modified from https://docs.amazonaws.cn/en_us/lambda/latest/dg/runtimes-walkthrough.html

We should later be able to do all the lambda stuff internally as mentioned in the 
[https://github.com/redskap/aws-lambda-java-runtime](https://github.com/redskap/aws-lambda-java-runtime) project.

```bash
#!/bin/sh
set -euo pipefail
while true
do
  HEADERS="$(mktemp)"
  EVENT_DATA=$(curl -sS -LD "$HEADERS" -X GET "http://${AWS_LAMBDA_RUNTIME_API}/2018-06-01/runtime/invocation/next")
  REQUEST_ID=$(grep -Fi Lambda-Runtime-Aws-Request-Id "$HEADERS" | tr -d '[:space:]' | cut -d: -f2)
  ## Everything piped out will go the the logs, capturing the output ruins this so be careful.
  ./graalvm-scala-lambda -Xmx128m $EVENT_DATA
  ## Send something like happy/not happy etc. This is what is shown in the response.
  curl -sS -X POST "http://${AWS_LAMBDA_RUNTIME_API}/2018-06-01/runtime/invocation/$REQUEST_ID/response"  -d "SUCCESS"
done
```

We can do the packaging by running the appropriate shell script in 
[scala_native_lambda_test_builder/deployable](scala_native_lambda_test_builder/deployable) folder. This copies the executable
into the directory and adds it to a zip with the bootstrap file.


#### Uploading it all using Pulumi

In [scala_native_lambda_test_builder/pulumi](scala_native_lambda_test_builder/pulumi) there is the Pulumi config **buildEnvironment.ts** that does the 
following :-
1. Creates a sqs queue called "test-queue"
2. Creates an assumeRolePolicy for the lambda called **lambda-role**.
3. We attach a policy to **lambda-role** to allow AWSLambdaBasicExecutionRole for execution
4. We attach a policy to **lambda-role** to allow AWSLambdaSQSQueueExecutionRole for execution from sqs messages
5. We create a lambda using the role with the policies from above using the zip that should now be held in the **deployable**
   directory mentioned in the above steps.
6. Create a sqs mapping that triggers the lambda.
7. To run use **pulumi up**.

More instructions can be found at [https://www.pulumi.com/docs/get-started/](https://www.pulumi.com/docs/get-started/).


## Lambda logs

### Cold start for a 512MB docker image - Billed Duration: 489 ms
```
2022-12-09T12:00:52.956+00:00	xxxxxxxxxx
2022-12-09T12:00:52.958+00:00	xxxxxxxxxb
2022-12-09T12:00:52.980+00:00	START RequestId: d3e0779a-5302-5e81-ab07-2b9776a557ad Version: $LATEST
2022-12-09T12:00:52.983+00:00	xxxxxxxxxc
2022-12-09T12:00:52.986+00:00	xxxxxxxxxd
2022-12-09T12:00:53.377+00:00	[main] DEBUG Main$ - mooo
2022-12-09T12:00:53.398+00:00	[main] INFO Main$ - I haz cheezeburgers {"Records":[{"messageId":"7caa7d79-bc4e-4455-a7ad-e511fc00353e","receiptHandle":"AQEBDEpTDg8hVyxLc6aK1sOrq6Hb/I+fbhZVCjxk3b1pp6J7GTBleD7wFRB//5qul45XWgHvonm6Q4E3k4Of3xlAgHXjdu/f3tMpHyYRo+a4bFU4HvNI5Ka6x8wZG/FbYykXoWI+kcYJ0gmw7qkcokYBIRjBHOgSzhR5w390vCJfbVgSDFQIBhyduQupePyvxc6VLqvtJYuJbjgOiobQtAn0vBmBWopwxUzSP5PZ6r7MVWA3ToGKGPwR/PoQeO3sQL0dGoHseVt0s2igWzUUXQw1NiSlDq5VvgUXQgxL3itEBi+IDLXthuo+0NxeqoMeod8V3CycnJfDm17d4Iio/HsYWjdBVuffe5vxqzA01ekndoiiTeOAU8Pduc844SOA1e1mX3UOtcVEJsszZS43AWSghg==","body":"{\n, \"Records\", :, [\n, {\n, \"messageId\", :, \"a78cdb8b-d8cb-4c28-be77-fc89608f022c\",\n, \"receiptHandle\", :, \"AQEBI91B0b3bpIffKQMxxG4CAISSeiN2Fd4HA0OT57vJNPeI1H/vtx9Pf/gas6P7maYdACB3hfrh5XCzyDgaT2paTkfthZh2KsUeiRCb7iF9RINZ5DjsCSmSt34Yjr/N2cQcZRaVWeQ6ZOBhxfMcEUTNOgsvEfi7RlSQYE8NvsygEm6NL/rbqcDV2N8azu1sCxKn7+TzVf4sFbM+fXk4vLifMgN4gakCf132MD2JQ67shMD6nxq86IHJbEKMMIICnxF8Yi1O7NBLqXQvTevvDnxqrdYAcuyfVhNtjc/p8Qk4Fpx5ATsKD2hqhW0DeYclx3r2pkeBF88zS2VakAR4SBInuOCZlLsiS9t1POzSwOC2FRVxgKDkfDJuIkQr71Ta3gx+Ui+B2phz0IXqwD6zuv4T8Q==\",\n, \"body\", :, \"I,, am,, the,, one,, and,, only\",\n, \"attributes\", :, {\n, \"ApproximateReceiveCount\", :, \"1\",\n, \"SentTimestamp\", :, \"1670426083016\",\n, \"SenderId\", :, \"538645939706\",\n, \"ApproximateFirstReceiveTimestamp\", :, \"1670426083021\"\n, },\n, \"messageAttributes\", :, {\n, \n, },\n, \"md5OfBody\", :, \"a390ff989d692670fa09d8d64b134179\",\n, \"eventSource\", :, \"aws:sqs\",\n, \"eventSourceARN\", :, \"arn:aws:sqs:eu-west-2:538645939706:test-queue\",\n, \"awsRegion\", :, \"eu-west-2\"\n, }\n, ]\n}","attributes":{"ApproximateReceiveCount":"1","SentTimestamp":"1670587252805","SenderId":"538645939706","ApproximateFirstReceiveTimestamp":"1670587252813"},"messageAttributes":{},"md5OfBody":"3ec707dc9de9badd0863fa3ac9c4766f","eventSource":"aws:sqs","eventSourceARN":"arn:aws:sqs:eu-west-2:538645939706:test-queue","awsRegion":"eu-west-2"}]}
2022-12-09T12:00:53.398+00:00	[main] ERROR Main$ - test error java.lang.RuntimeException: a at lambda.Main$.main(Main.scala:39) at lambda.Main.main(Main.scala) Caused by: java.lang.RuntimeException: b ... 2 more
2022-12-09T12:00:53.398+00:00	[main] INFO Main$ - processing message {"Records":[{"messageId":"7caa7d79-bc4e-4455-a7ad-e511fc00353e","receiptHandle":"AQEBDEpTDg8hVyxLc6aK1sOrq6Hb/I+fbhZVCjxk3b1pp6J7GTBleD7wFRB//5qul45XWgHvonm6Q4E3k4Of3xlAgHXjdu/f3tMpHyYRo+a4bFU4HvNI5Ka6x8wZG/FbYykXoWI+kcYJ0gmw7qkcokYBIRjBHOgSzhR5w390vCJfbVgSDFQIBhyduQupePyvxc6VLqvtJYuJbjgOiobQtAn0vBmBWopwxUzSP5PZ6r7MVWA3ToGKGPwR/PoQeO3sQL0dGoHseVt0s2igWzUUXQw1NiSlDq5VvgUXQgxL3itEBi+IDLXthuo+0NxeqoMeod8V3CycnJfDm17d4Iio/HsYWjdBVuffe5vxqzA01ekndoiiTeOAU8Pduc844SOA1e1mX3UOtcVEJsszZS43AWSghg==","body":"{\n, \"Records\", :, [\n, {\n, \"messageId\", :, \"a78cdb8b-d8cb-4c28-be77-fc89608f022c\",\n, \"receiptHandle\", :, \"AQEBI91B0b3bpIffKQMxxG4CAISSeiN2Fd4HA0OT57vJNPeI1H/vtx9Pf/gas6P7maYdACB3hfrh5XCzyDgaT2paTkfthZh2KsUeiRCb7iF9RINZ5DjsCSmSt34Yjr/N2cQcZRaVWeQ6ZOBhxfMcEUTNOgsvEfi7RlSQYE8NvsygEm6NL/rbqcDV2N8azu1sCxKn7+TzVf4sFbM+fXk4vLifMgN4gakCf132MD2JQ67shMD6nxq86IHJbEKMMIICnxF8Yi1O7NBLqXQvTevvDnxqrdYAcuyfVhNtjc/p8Qk4Fpx5ATsKD2hqhW0DeYclx3r2pkeBF88zS2VakAR4SBInuOCZlLsiS9t1POzSwOC2FRVxgKDkfDJuIkQr71Ta3gx+Ui+B2phz0IXqwD6zuv4T8Q==\",\n, \"body\", :, \"I,, am,, the,, one,, and,, only\",\n, \"attributes\", :, {\n, \"ApproximateReceiveCount\", :, \"1\",\n, \"SentTimestamp\", :, \"1670426083016\",\n, \"SenderId\", :, \"538645939706\",\n, \"ApproximateFirstReceiveTimestamp\", :, \"1670426083021\"\n, },\n, \"messageAttributes\", :, {\n, \n, },\n, \"md5OfBody\", :, \"a390ff989d692670fa09d8d64b134179\",\n, \"eventSource\", :, \"aws:sqs\",\n, \"eventSourceARN\", :, \"arn:aws:sqs:eu-west-2:538645939706:test-queue\",\n, \"awsRegion\", :, \"eu-west-2\"\n, }\n, ]\n}","attributes":{"ApproximateReceiveCount":"1","SentTimestamp":"1670587252805","SenderId":"538645939706","ApproximateFirstReceiveTimestamp":"1670587252813"},"messageAttributes":{},"md5OfBody":"3ec707dc9de9badd0863fa3ac9c4766f","eventSource":"aws:sqs","eventSourceARN":"arn:aws:sqs:eu-west-2:538645939706:test-queue","awsRegion":"eu-west-2"}]}
2022-12-09T12:00:53.398+00:00	{"Records":[{"messageId":"7caa7d79-bc4e-4455-a7ad-e511fc00353e","receiptHandle":"AQEBDEpTDg8hVyxLc6aK1sOrq6Hb/I+fbhZVCjxk3b1pp6J7GTBleD7wFRB//5qul45XWgHvonm6Q4E3k4Of3xlAgHXjdu/f3tMpHyYRo+a4bFU4HvNI5Ka6x8wZG/FbYykXoWI+kcYJ0gmw7qkcokYBIRjBHOgSzhR5w390vCJfbVgSDFQIBhyduQupePyvxc6VLqvtJYuJbjgOiobQtAn0vBmBWopwxUzSP5PZ6r7MVWA3ToGKGPwR/PoQeO3sQL0dGoHseVt0s2igWzUUXQw1NiSlDq5VvgUXQgxL3itEBi+IDLXthuo+0NxeqoMeod8V3CycnJfDm17d4Iio/HsYWjdBVuffe5vxqzA01ekndoiiTeOAU8Pduc844SOA1e1mX3UOtcVEJsszZS43AWSghg==","body":"{\n, \"Records\", :, [\n, {\n, \"messageId\", :, \"a78cdb8b-d8cb-4c28-be77-fc89608f022c\",\n, \"receiptHandle\", :, \"AQEBI91B0b3bpIffKQMxxG4CAISSeiN2Fd4HA0OT57vJNPeI1H/vtx9Pf/gas6P7maYdACB3hfrh5XCzyDgaT2paTkfthZh2KsUeiRCb7iF9RINZ5DjsCSmSt34Yjr/N2cQcZRaVWeQ6ZOBhxfMcEUTNOgsvEfi7RlSQYE8NvsygEm6NL/rbqcDV2N8azu1sCxKn7+TzVf4sFbM+fXk4vLifMgN4gakCf132MD2JQ67shMD6nxq86IHJbEKMMIICnxF8Yi1O7NBLqXQvTevvDnxqrdYAcuyfVhNtjc/p8Qk4Fpx5ATsKD2hqhW0DeYclx3r2pkeBF88zS2VakAR4SBInuOCZlLsiS9t1POzSwOC2FRVxgKDkfDJuIkQr71Ta3gx+Ui+B2phz0IXqwD6zuv4T8Q==\",\n, \"body\", :, \"I,, am,, the,, one,, and,, only\",\n, \"attributes\", :, {\n, \"ApproximateReceiveCount\", :, \"1\",\n, \"SentTimestamp\", :, \"1670426083016\",\n, \"SenderId\", :, \"538645939706\",\n, \"ApproximateFirstReceiveTimestamp\", :, \"1670426083021\"\n, },\n, \"messageAttributes\", :, {\n, \n, },\n, \"md5OfBody\", :, \"a390ff989d692670fa09d8d64b134179\",\n, \"eventSource\", :, \"aws:sqs\",\n, \"eventSourceARN\", :, \"arn:aws:sqs:eu-west-2:538645939706:test-queue\",\n, \"awsRegion\", :, \"eu-west-2\"\n, }\n, ]\n}","attributes":{"ApproximateReceiveCount":"1","SentTimestamp":"1670587252805","SenderId":"538645939706","ApproximateFirstReceiveTimestamp":"1670587252813"},"messageAttributes":{},"md5OfBody":"3ec707dc9de9badd0863fa3ac9c4766f","eventSource":"aws:sqs","eventSourceARN":"arn:aws:sqs:eu-west-2:538645939706:test-queue","awsRegion":"eu-west-2"}]}
2022-12-09T12:00:53.399+00:00	[main] INFO SqsOperation$ - decoded List(SqsDecoding(7caa7d79-bc4e-4455-a7ad-e511fc00353e,AQEBDEpTDg8hVyxLc6aK1sOrq6Hb/I+fbhZVCjxk3b1pp6J7GTBleD7wFRB//5qul45XWgHvonm6Q4E3k4Of3xlAgHXjdu/f3tMpHyYRo+a4bFU4HvNI5Ka6x8wZG/FbYykXoWI+kcYJ0gmw7qkcokYBIRjBHOgSzhR5w390vCJfbVgSDFQIBhyduQupePyvxc6VLqvtJYuJbjgOiobQtAn0vBmBWopwxUzSP5PZ6r7MVWA3ToGKGPwR/PoQeO3sQL0dGoHseVt0s2igWzUUXQw1NiSlDq5VvgUXQgxL3itEBi+IDLXthuo+0NxeqoMeod8V3CycnJfDm17d4Iio/HsYWjdBVuffe5vxqzA01ekndoiiTeOAU8Pduc844SOA1e1mX3UOtcVEJsszZS43AWSghg==,{ , "Records", :, [ , { , "messageId", :, "a78cdb8b-d8cb-4c28-be77-fc89608f022c", , "receiptHandle", :, "AQEBI91B0b3bpIffKQMxxG4CAISSeiN2Fd4HA0OT57vJNPeI1H/vtx9Pf/gas6P7maYdACB3hfrh5XCzyDgaT2paTkfthZh2KsUeiRCb7iF9RINZ5DjsCSmSt34Yjr/N2cQcZRaVWeQ6ZOBhxfMcEUTNOgsvEfi7RlSQYE8NvsygEm6NL/rbqcDV2N8azu1sCxKn7+TzVf4sFbM+fXk4vLifMgN4gakCf132MD2JQ67shMD6nxq86IHJbEKMMIICnxF8Yi1O7NBLqXQvTevvDnxqrdYAcuyfVhNtjc/p8Qk4Fpx5ATsKD2hqhW0DeYclx3r2pkeBF88zS2VakAR4SBInuOCZlLsiS9t1POzSwOC2FRVxgKDkfDJuIkQr71Ta3gx+Ui+B2phz0IXqwD6zuv4T8Q==", , "body", :, "I,, am,, the,, one,, and,, only", , "attributes", :, { , "ApproximateReceiveCount", :, "1", , "SentTimestamp", :, "1670426083016", , "SenderId", :, "538645939706", , "ApproximateFirstReceiveTimestamp", :, "1670426083021" , }, , "messageAttributes", :, { , , }, , "md5OfBody", :, "a390ff989d692670fa09d8d64b134179", , "eventSource", :, "aws:sqs", , "eventSourceARN", :, "arn:aws:sqs:eu-west-2:538645939706:test-queue", , "awsRegion", :, "eu-west-2" , } , ] },Map(ApproximateReceiveCount -> 1, SentTimestamp -> 1670587252805, SenderId -> 538645939706, ApproximateFirstReceiveTimestamp -> 1670587252813),Map(),3ec707dc9de9badd0863fa3ac9c4766f,aws:sqs,arn:aws:sqs:eu-west-2:538645939706:test-queue,eu-west-2))
2022-12-09T12:00:53.399+00:00	[main] INFO Main$ - processed message {"Records":[{"messageId":"7caa7d79-bc4e-4455-a7ad-e511fc00353e","receiptHandle":"AQEBDEpTDg8hVyxLc6aK1sOrq6Hb/I+fbhZVCjxk3b1pp6J7GTBleD7wFRB//5qul45XWgHvonm6Q4E3k4Of3xlAgHXjdu/f3tMpHyYRo+a4bFU4HvNI5Ka6x8wZG/FbYykXoWI+kcYJ0gmw7qkcokYBIRjBHOgSzhR5w390vCJfbVgSDFQIBhyduQupePyvxc6VLqvtJYuJbjgOiobQtAn0vBmBWopwxUzSP5PZ6r7MVWA3ToGKGPwR/PoQeO3sQL0dGoHseVt0s2igWzUUXQw1NiSlDq5VvgUXQgxL3itEBi+IDLXthuo+0NxeqoMeod8V3CycnJfDm17d4Iio/HsYWjdBVuffe5vxqzA01ekndoiiTeOAU8Pduc844SOA1e1mX3UOtcVEJsszZS43AWSghg==","body":"{\n, \"Records\", :, [\n, {\n, \"messageId\", :, \"a78cdb8b-d8cb-4c28-be77-fc89608f022c\",\n, \"receiptHandle\", :, \"AQEBI91B0b3bpIffKQMxxG4CAISSeiN2Fd4HA0OT57vJNPeI1H/vtx9Pf/gas6P7maYdACB3hfrh5XCzyDgaT2paTkfthZh2KsUeiRCb7iF9RINZ5DjsCSmSt34Yjr/N2cQcZRaVWeQ6ZOBhxfMcEUTNOgsvEfi7RlSQYE8NvsygEm6NL/rbqcDV2N8azu1sCxKn7+TzVf4sFbM+fXk4vLifMgN4gakCf132MD2JQ67shMD6nxq86IHJbEKMMIICnxF8Yi1O7NBLqXQvTevvDnxqrdYAcuyfVhNtjc/p8Qk4Fpx5ATsKD2hqhW0DeYclx3r2pkeBF88zS2VakAR4SBInuOCZlLsiS9t1POzSwOC2FRVxgKDkfDJuIkQr71Ta3gx+Ui+B2phz0IXqwD6zuv4T8Q==\",\n, \"body\", :, \"I,, am,, the,, one,, and,, only\",\n, \"attributes\", :, {\n, \"ApproximateReceiveCount\", :, \"1\",\n, \"SentTimestamp\", :, \"1670426083016\",\n, \"SenderId\", :, \"538645939706\",\n, \"ApproximateFirstReceiveTimestamp\", :, \"1670426083021\"\n, },\n, \"messageAttributes\", :, {\n, \n, },\n, \"md5OfBody\", :, \"a390ff989d692670fa09d8d64b134179\",\n, \"eventSource\", :, \"aws:sqs\",\n, \"eventSourceARN\", :, \"arn:aws:sqs:eu-west-2:538645939706:test-queue\",\n, \"awsRegion\", :, \"eu-west-2\"\n, }\n, ]\n}","attributes":{"ApproximateReceiveCount":"1","SentTimestamp":"1670587252805","SenderId":"538645939706","ApproximateFirstReceiveTimestamp":"1670587252813"},"messageAttributes":{},"md5OfBody":"3ec707dc9de9badd0863fa3ac9c4766f","eventSource":"aws:sqs","eventSourceARN":"arn:aws:sqs:eu-west-2:538645939706:test-queue","awsRegion":"eu-west-2"}]}
2022-12-09T12:00:53.417+00:00	{"status":"OK"}
2022-12-09T12:00:53.418+00:00	xxxxxxxxxx
2022-12-09T12:00:53.419+00:00	xxxxxxxxxb
2022-12-09T12:00:53.437+00:00	END RequestId: d3e0779a-5302-5e81-ab07-2b9776a557ad
2022-12-09T12:00:53.437+00:00	REPORT RequestId: d3e0779a-5302-5e81-ab07-2b9776a557ad Duration: 457.54 ms Billed Duration: 489 ms Memory Size: 512 MB Max Memory Used: 45 MB Init Duration: 30.55 ms
```

### Warm start at 512MB - Billed Duration: 53 ms
```
2022-12-09T12:02:34.810+00:00	START RequestId: 6968b141-7b46-5c64-a8fd-ed4c5d54d369 Version: $LATEST
2022-12-09T12:02:34.811+00:00	xxxxxxxxxc
2022-12-09T12:02:34.814+00:00	xxxxxxxxxd
2022-12-09T12:02:34.818+00:00	[main] DEBUG Main$ - mooo
2022-12-09T12:02:34.818+00:00	[main] INFO Main$ - I haz cheezeburgers {"Records":[{"messageId":"ee7462a0-2b6c-40d5-9ad6-1b977fe98c0a","receiptHandle":"AQEBz3dRWT8md9T01R/cPcWqvYUkSZ7E3NhRWe97+7I0jHcgnFORSgminJo4K/VoAIbw+pZDwA+wUc7so3Kn/5Zykip+XDyazYrxe+byUlNFRyQdrJJk7SZ8mfLxgrlhYQ+eiWZPG5InnuoeZ9+ob8p1QhqQLdkntrXNrJb8wTD9YBJjWMR2g4jaKHcPj0SbgXbQrJ1BnnQImyQE9f7rxD467ckIDjye49w+A7vjgIax/xFjw3TpwIQ7+bDBb82sFi2ysPtXF6NngZBueqTWAtght7LuD1i9em2cma7GUG7luujyZYa+SXdyfg6KGlwrCaTiYFS9uso8Y3xQX6fPz9dMO1HEkmdAKRAdt6Ox4hR1YOd/lJxIn6LjsJqG9WY/4wCCZDCYe4tKu1zTJ3u13nKxSg==","body":"{\n, \"Records\", :, [\n, {\n, \"messageId\", :, \"a78cdb8b-d8cb-4c28-be77-fc89608f022c\",\n, \"receiptHandle\", :, \"AQEBI91B0b3bpIffKQMxxG4CAISSeiN2Fd4HA0OT57vJNPeI1H/vtx9Pf/gas6P7maYdACB3hfrh5XCzyDgaT2paTkfthZh2KsUeiRCb7iF9RINZ5DjsCSmSt34Yjr/N2cQcZRaVWeQ6ZOBhxfMcEUTNOgsvEfi7RlSQYE8NvsygEm6NL/rbqcDV2N8azu1sCxKn7+TzVf4sFbM+fXk4vLifMgN4gakCf132MD2JQ67shMD6nxq86IHJbEKMMIICnxF8Yi1O7NBLqXQvTevvDnxqrdYAcuyfVhNtjc/p8Qk4Fpx5ATsKD2hqhW0DeYclx3r2pkeBF88zS2VakAR4SBInuOCZlLsiS9t1POzSwOC2FRVxgKDkfDJuIkQr71Ta3gx+Ui+B2phz0IXqwD6zuv4T8Q==\",\n, \"body\", :, \"I,, am,, the,, one,, and,, only\",\n, \"attributes\", :, {\n, \"ApproximateReceiveCount\", :, \"1\",\n, \"SentTimestamp\", :, \"1670426083016\",\n, \"SenderId\", :, \"538645939706\",\n, \"ApproximateFirstReceiveTimestamp\", :, \"1670426083021\"\n, },\n, \"messageAttributes\", :, {\n, \n, },\n, \"md5OfBody\", :, \"a390ff989d692670fa09d8d64b134179\",\n, \"eventSource\", :, \"aws:sqs\",\n, \"eventSourceARN\", :, \"arn:aws:sqs:eu-west-2:538645939706:test-queue\",\n, \"awsRegion\", :, \"eu-west-2\"\n, }\n, ]\n}","attributes":{"ApproximateReceiveCount":"1","SentTimestamp":"1670587354782","SenderId":"538645939706","ApproximateFirstReceiveTimestamp":"1670587354789"},"messageAttributes":{},"md5OfBody":"3ec707dc9de9badd0863fa3ac9c4766f","eventSource":"aws:sqs","eventSourceARN":"arn:aws:sqs:eu-west-2:538645939706:test-queue","awsRegion":"eu-west-2"}]}
2022-12-09T12:02:34.818+00:00	[main] ERROR Main$ - test error java.lang.RuntimeException: a at lambda.Main$.main(Main.scala:39) at lambda.Main.main(Main.scala) Caused by: java.lang.RuntimeException: b ... 2 more
2022-12-09T12:02:34.818+00:00	[main] INFO Main$ - processing message {"Records":[{"messageId":"ee7462a0-2b6c-40d5-9ad6-1b977fe98c0a","receiptHandle":"AQEBz3dRWT8md9T01R/cPcWqvYUkSZ7E3NhRWe97+7I0jHcgnFORSgminJo4K/VoAIbw+pZDwA+wUc7so3Kn/5Zykip+XDyazYrxe+byUlNFRyQdrJJk7SZ8mfLxgrlhYQ+eiWZPG5InnuoeZ9+ob8p1QhqQLdkntrXNrJb8wTD9YBJjWMR2g4jaKHcPj0SbgXbQrJ1BnnQImyQE9f7rxD467ckIDjye49w+A7vjgIax/xFjw3TpwIQ7+bDBb82sFi2ysPtXF6NngZBueqTWAtght7LuD1i9em2cma7GUG7luujyZYa+SXdyfg6KGlwrCaTiYFS9uso8Y3xQX6fPz9dMO1HEkmdAKRAdt6Ox4hR1YOd/lJxIn6LjsJqG9WY/4wCCZDCYe4tKu1zTJ3u13nKxSg==","body":"{\n, \"Records\", :, [\n, {\n, \"messageId\", :, \"a78cdb8b-d8cb-4c28-be77-fc89608f022c\",\n, \"receiptHandle\", :, \"AQEBI91B0b3bpIffKQMxxG4CAISSeiN2Fd4HA0OT57vJNPeI1H/vtx9Pf/gas6P7maYdACB3hfrh5XCzyDgaT2paTkfthZh2KsUeiRCb7iF9RINZ5DjsCSmSt34Yjr/N2cQcZRaVWeQ6ZOBhxfMcEUTNOgsvEfi7RlSQYE8NvsygEm6NL/rbqcDV2N8azu1sCxKn7+TzVf4sFbM+fXk4vLifMgN4gakCf132MD2JQ67shMD6nxq86IHJbEKMMIICnxF8Yi1O7NBLqXQvTevvDnxqrdYAcuyfVhNtjc/p8Qk4Fpx5ATsKD2hqhW0DeYclx3r2pkeBF88zS2VakAR4SBInuOCZlLsiS9t1POzSwOC2FRVxgKDkfDJuIkQr71Ta3gx+Ui+B2phz0IXqwD6zuv4T8Q==\",\n, \"body\", :, \"I,, am,, the,, one,, and,, only\",\n, \"attributes\", :, {\n, \"ApproximateReceiveCount\", :, \"1\",\n, \"SentTimestamp\", :, \"1670426083016\",\n, \"SenderId\", :, \"538645939706\",\n, \"ApproximateFirstReceiveTimestamp\", :, \"1670426083021\"\n, },\n, \"messageAttributes\", :, {\n, \n, },\n, \"md5OfBody\", :, \"a390ff989d692670fa09d8d64b134179\",\n, \"eventSource\", :, \"aws:sqs\",\n, \"eventSourceARN\", :, \"arn:aws:sqs:eu-west-2:538645939706:test-queue\",\n, \"awsRegion\", :, \"eu-west-2\"\n, }\n, ]\n}","attributes":{"ApproximateReceiveCount":"1","SentTimestamp":"1670587354782","SenderId":"538645939706","ApproximateFirstReceiveTimestamp":"1670587354789"},"messageAttributes":{},"md5OfBody":"3ec707dc9de9badd0863fa3ac9c4766f","eventSource":"aws:sqs","eventSourceARN":"arn:aws:sqs:eu-west-2:538645939706:test-queue","awsRegion":"eu-west-2"}]}
2022-12-09T12:02:34.819+00:00	{"Records":[{"messageId":"ee7462a0-2b6c-40d5-9ad6-1b977fe98c0a","receiptHandle":"AQEBz3dRWT8md9T01R/cPcWqvYUkSZ7E3NhRWe97+7I0jHcgnFORSgminJo4K/VoAIbw+pZDwA+wUc7so3Kn/5Zykip+XDyazYrxe+byUlNFRyQdrJJk7SZ8mfLxgrlhYQ+eiWZPG5InnuoeZ9+ob8p1QhqQLdkntrXNrJb8wTD9YBJjWMR2g4jaKHcPj0SbgXbQrJ1BnnQImyQE9f7rxD467ckIDjye49w+A7vjgIax/xFjw3TpwIQ7+bDBb82sFi2ysPtXF6NngZBueqTWAtght7LuD1i9em2cma7GUG7luujyZYa+SXdyfg6KGlwrCaTiYFS9uso8Y3xQX6fPz9dMO1HEkmdAKRAdt6Ox4hR1YOd/lJxIn6LjsJqG9WY/4wCCZDCYe4tKu1zTJ3u13nKxSg==","body":"{\n, \"Records\", :, [\n, {\n, \"messageId\", :, \"a78cdb8b-d8cb-4c28-be77-fc89608f022c\",\n, \"receiptHandle\", :, \"AQEBI91B0b3bpIffKQMxxG4CAISSeiN2Fd4HA0OT57vJNPeI1H/vtx9Pf/gas6P7maYdACB3hfrh5XCzyDgaT2paTkfthZh2KsUeiRCb7iF9RINZ5DjsCSmSt34Yjr/N2cQcZRaVWeQ6ZOBhxfMcEUTNOgsvEfi7RlSQYE8NvsygEm6NL/rbqcDV2N8azu1sCxKn7+TzVf4sFbM+fXk4vLifMgN4gakCf132MD2JQ67shMD6nxq86IHJbEKMMIICnxF8Yi1O7NBLqXQvTevvDnxqrdYAcuyfVhNtjc/p8Qk4Fpx5ATsKD2hqhW0DeYclx3r2pkeBF88zS2VakAR4SBInuOCZlLsiS9t1POzSwOC2FRVxgKDkfDJuIkQr71Ta3gx+Ui+B2phz0IXqwD6zuv4T8Q==\",\n, \"body\", :, \"I,, am,, the,, one,, and,, only\",\n, \"attributes\", :, {\n, \"ApproximateReceiveCount\", :, \"1\",\n, \"SentTimestamp\", :, \"1670426083016\",\n, \"SenderId\", :, \"538645939706\",\n, \"ApproximateFirstReceiveTimestamp\", :, \"1670426083021\"\n, },\n, \"messageAttributes\", :, {\n, \n, },\n, \"md5OfBody\", :, \"a390ff989d692670fa09d8d64b134179\",\n, \"eventSource\", :, \"aws:sqs\",\n, \"eventSourceARN\", :, \"arn:aws:sqs:eu-west-2:538645939706:test-queue\",\n, \"awsRegion\", :, \"eu-west-2\"\n, }\n, ]\n}","attributes":{"ApproximateReceiveCount":"1","SentTimestamp":"1670587354782","SenderId":"538645939706","ApproximateFirstReceiveTimestamp":"1670587354789"},"messageAttributes":{},"md5OfBody":"3ec707dc9de9badd0863fa3ac9c4766f","eventSource":"aws:sqs","eventSourceARN":"arn:aws:sqs:eu-west-2:538645939706:test-queue","awsRegion":"eu-west-2"}]}
2022-12-09T12:02:34.819+00:00	[main] INFO SqsOperation$ - decoded List(SqsDecoding(ee7462a0-2b6c-40d5-9ad6-1b977fe98c0a,AQEBz3dRWT8md9T01R/cPcWqvYUkSZ7E3NhRWe97+7I0jHcgnFORSgminJo4K/VoAIbw+pZDwA+wUc7so3Kn/5Zykip+XDyazYrxe+byUlNFRyQdrJJk7SZ8mfLxgrlhYQ+eiWZPG5InnuoeZ9+ob8p1QhqQLdkntrXNrJb8wTD9YBJjWMR2g4jaKHcPj0SbgXbQrJ1BnnQImyQE9f7rxD467ckIDjye49w+A7vjgIax/xFjw3TpwIQ7+bDBb82sFi2ysPtXF6NngZBueqTWAtght7LuD1i9em2cma7GUG7luujyZYa+SXdyfg6KGlwrCaTiYFS9uso8Y3xQX6fPz9dMO1HEkmdAKRAdt6Ox4hR1YOd/lJxIn6LjsJqG9WY/4wCCZDCYe4tKu1zTJ3u13nKxSg==,{ , "Records", :, [ , { , "messageId", :, "a78cdb8b-d8cb-4c28-be77-fc89608f022c", , "receiptHandle", :, "AQEBI91B0b3bpIffKQMxxG4CAISSeiN2Fd4HA0OT57vJNPeI1H/vtx9Pf/gas6P7maYdACB3hfrh5XCzyDgaT2paTkfthZh2KsUeiRCb7iF9RINZ5DjsCSmSt34Yjr/N2cQcZRaVWeQ6ZOBhxfMcEUTNOgsvEfi7RlSQYE8NvsygEm6NL/rbqcDV2N8azu1sCxKn7+TzVf4sFbM+fXk4vLifMgN4gakCf132MD2JQ67shMD6nxq86IHJbEKMMIICnxF8Yi1O7NBLqXQvTevvDnxqrdYAcuyfVhNtjc/p8Qk4Fpx5ATsKD2hqhW0DeYclx3r2pkeBF88zS2VakAR4SBInuOCZlLsiS9t1POzSwOC2FRVxgKDkfDJuIkQr71Ta3gx+Ui+B2phz0IXqwD6zuv4T8Q==", , "body", :, "I,, am,, the,, one,, and,, only", , "attributes", :, { , "ApproximateReceiveCount", :, "1", , "SentTimestamp", :, "1670426083016", , "SenderId", :, "538645939706", , "ApproximateFirstReceiveTimestamp", :, "1670426083021" , }, , "messageAttributes", :, { , , }, , "md5OfBody", :, "a390ff989d692670fa09d8d64b134179", , "eventSource", :, "aws:sqs", , "eventSourceARN", :, "arn:aws:sqs:eu-west-2:538645939706:test-queue", , "awsRegion", :, "eu-west-2" , } , ] },Map(ApproximateReceiveCount -> 1, SentTimestamp -> 1670587354782, SenderId -> 538645939706, ApproximateFirstReceiveTimestamp -> 1670587354789),Map(),3ec707dc9de9badd0863fa3ac9c4766f,aws:sqs,arn:aws:sqs:eu-west-2:538645939706:test-queue,eu-west-2))
2022-12-09T12:02:34.819+00:00	[main] INFO Main$ - processed message {"Records":[{"messageId":"ee7462a0-2b6c-40d5-9ad6-1b977fe98c0a","receiptHandle":"AQEBz3dRWT8md9T01R/cPcWqvYUkSZ7E3NhRWe97+7I0jHcgnFORSgminJo4K/VoAIbw+pZDwA+wUc7so3Kn/5Zykip+XDyazYrxe+byUlNFRyQdrJJk7SZ8mfLxgrlhYQ+eiWZPG5InnuoeZ9+ob8p1QhqQLdkntrXNrJb8wTD9YBJjWMR2g4jaKHcPj0SbgXbQrJ1BnnQImyQE9f7rxD467ckIDjye49w+A7vjgIax/xFjw3TpwIQ7+bDBb82sFi2ysPtXF6NngZBueqTWAtght7LuD1i9em2cma7GUG7luujyZYa+SXdyfg6KGlwrCaTiYFS9uso8Y3xQX6fPz9dMO1HEkmdAKRAdt6Ox4hR1YOd/lJxIn6LjsJqG9WY/4wCCZDCYe4tKu1zTJ3u13nKxSg==","body":"{\n, \"Records\", :, [\n, {\n, \"messageId\", :, \"a78cdb8b-d8cb-4c28-be77-fc89608f022c\",\n, \"receiptHandle\", :, \"AQEBI91B0b3bpIffKQMxxG4CAISSeiN2Fd4HA0OT57vJNPeI1H/vtx9Pf/gas6P7maYdACB3hfrh5XCzyDgaT2paTkfthZh2KsUeiRCb7iF9RINZ5DjsCSmSt34Yjr/N2cQcZRaVWeQ6ZOBhxfMcEUTNOgsvEfi7RlSQYE8NvsygEm6NL/rbqcDV2N8azu1sCxKn7+TzVf4sFbM+fXk4vLifMgN4gakCf132MD2JQ67shMD6nxq86IHJbEKMMIICnxF8Yi1O7NBLqXQvTevvDnxqrdYAcuyfVhNtjc/p8Qk4Fpx5ATsKD2hqhW0DeYclx3r2pkeBF88zS2VakAR4SBInuOCZlLsiS9t1POzSwOC2FRVxgKDkfDJuIkQr71Ta3gx+Ui+B2phz0IXqwD6zuv4T8Q==\",\n, \"body\", :, \"I,, am,, the,, one,, and,, only\",\n, \"attributes\", :, {\n, \"ApproximateReceiveCount\", :, \"1\",\n, \"SentTimestamp\", :, \"1670426083016\",\n, \"SenderId\", :, \"538645939706\",\n, \"ApproximateFirstReceiveTimestamp\", :, \"1670426083021\"\n, },\n, \"messageAttributes\", :, {\n, \n, },\n, \"md5OfBody\", :, \"a390ff989d692670fa09d8d64b134179\",\n, \"eventSource\", :, \"aws:sqs\",\n, \"eventSourceARN\", :, \"arn:aws:sqs:eu-west-2:538645939706:test-queue\",\n, \"awsRegion\", :, \"eu-west-2\"\n, }\n, ]\n}","attributes":{"ApproximateReceiveCount":"1","SentTimestamp":"1670587354782","SenderId":"538645939706","ApproximateFirstReceiveTimestamp":"1670587354789"},"messageAttributes":{},"md5OfBody":"3ec707dc9de9badd0863fa3ac9c4766f","eventSource":"aws:sqs","eventSourceARN":"arn:aws:sqs:eu-west-2:538645939706:test-queue","awsRegion":"eu-west-2"}]}
2022-12-09T12:02:34.837+00:00	{"status":"OK"}
2022-12-09T12:02:34.839+00:00	xxxxxxxxxx
2022-12-09T12:02:34.856+00:00	xxxxxxxxxb
2022-12-09T12:02:34.862+00:00	END RequestId: 6968b141-7b46-5c64-a8fd-ed4c5d54d369
2022-12-09T12:02:34.862+00:00	REPORT RequestId: 6968b141-7b46-5c64-a8fd-ed4c5d54d369 Duration: 52.30 ms Billed Duration: 53 ms Memory Size: 512 MB Max Memory Used: 47 MB
```

### Cold start for a 1024MB docker image - Billed Duration: 261 ms
```
022-12-15T13:08:04.465+00:00	START RequestId: b1c94f19-6ba5-5bde-b7dc-130c75cd44aa Version: $LATEST
2022-12-15T13:08:04.667+00:00	[main] DEBUG Main$ - mooo
2022-12-15T13:08:04.672+00:00	[main] INFO Main$ - I haz cheezeburgers {"Records":[{"messageId":"a7273082-2064-4b11-b740-84068f524f55","receiptHandle":"AQEBQaQ5SVS9KAzbnlGATBP3hwN13G3cIMg93n1cgp6KtkU5S9LsCWvyNIQ7h8NlBRX9JXWVjJtFei0odlcuZGwB63N9GRhJQXtmESeCAvcMQtLmC4RZiSYbl/IL4lX9T833sugZ+CKG5GKc8V8L7/3A+w0Kl/OZEBhNT9pGhIF8MJx/zkaNxu+hg8q0mT8EhVGpMyS8Gf4F5aeooZQQRS7uLpnYGSeMUHK58kFIHfs2BKlcATPUHZ5SppMAHaJDtBJCrEr9b6wk068NK1Vu144nzFVY9un5hjqaAvJWvduitMQCleXjvmH7cfj6rjM0yVRW/1lxHJGLIwck5oboZefdQx3dcCkkN4nj6IeHUs77k7C7LtwcyboLrvB603pzsWLxvgvGUZOdSWyd5sqbPE9ocA==","body":"cowabunga","attributes":{"ApproximateReceiveCount":"1","SentTimestamp":"1671109684185","SenderId":"538645939706","ApproximateFirstReceiveTimestamp":"1671109684190"},"messageAttributes":{},"md5OfBody":"f0baf065f0f54d7c2db3e29e718c7f31","eventSource":"aws:sqs","eventSourceARN":"arn:aws:sqs:eu-west-2:538645939706:test-queue","awsRegion":"eu-west-2"}]}
2022-12-15T13:08:04.672+00:00	[main] ERROR Main$ - test error java.lang.RuntimeException: a at lambda.Main$.main(Main.scala:39) at lambda.Main.main(Main.scala) Caused by: java.lang.RuntimeException: b ... 2 more
2022-12-15T13:08:04.672+00:00	[main] INFO Main$ - processing message {"Records":[{"messageId":"a7273082-2064-4b11-b740-84068f524f55","receiptHandle":"AQEBQaQ5SVS9KAzbnlGATBP3hwN13G3cIMg93n1cgp6KtkU5S9LsCWvyNIQ7h8NlBRX9JXWVjJtFei0odlcuZGwB63N9GRhJQXtmESeCAvcMQtLmC4RZiSYbl/IL4lX9T833sugZ+CKG5GKc8V8L7/3A+w0Kl/OZEBhNT9pGhIF8MJx/zkaNxu+hg8q0mT8EhVGpMyS8Gf4F5aeooZQQRS7uLpnYGSeMUHK58kFIHfs2BKlcATPUHZ5SppMAHaJDtBJCrEr9b6wk068NK1Vu144nzFVY9un5hjqaAvJWvduitMQCleXjvmH7cfj6rjM0yVRW/1lxHJGLIwck5oboZefdQx3dcCkkN4nj6IeHUs77k7C7LtwcyboLrvB603pzsWLxvgvGUZOdSWyd5sqbPE9ocA==","body":"cowabunga","attributes":{"ApproximateReceiveCount":"1","SentTimestamp":"1671109684185","SenderId":"538645939706","ApproximateFirstReceiveTimestamp":"1671109684190"},"messageAttributes":{},"md5OfBody":"f0baf065f0f54d7c2db3e29e718c7f31","eventSource":"aws:sqs","eventSourceARN":"arn:aws:sqs:eu-west-2:538645939706:test-queue","awsRegion":"eu-west-2"}]}
2022-12-15T13:08:04.672+00:00	{"Records":[{"messageId":"a7273082-2064-4b11-b740-84068f524f55","receiptHandle":"AQEBQaQ5SVS9KAzbnlGATBP3hwN13G3cIMg93n1cgp6KtkU5S9LsCWvyNIQ7h8NlBRX9JXWVjJtFei0odlcuZGwB63N9GRhJQXtmESeCAvcMQtLmC4RZiSYbl/IL4lX9T833sugZ+CKG5GKc8V8L7/3A+w0Kl/OZEBhNT9pGhIF8MJx/zkaNxu+hg8q0mT8EhVGpMyS8Gf4F5aeooZQQRS7uLpnYGSeMUHK58kFIHfs2BKlcATPUHZ5SppMAHaJDtBJCrEr9b6wk068NK1Vu144nzFVY9un5hjqaAvJWvduitMQCleXjvmH7cfj6rjM0yVRW/1lxHJGLIwck5oboZefdQx3dcCkkN4nj6IeHUs77k7C7LtwcyboLrvB603pzsWLxvgvGUZOdSWyd5sqbPE9ocA==","body":"cowabunga","attributes":{"ApproximateReceiveCount":"1","SentTimestamp":"1671109684185","SenderId":"538645939706","ApproximateFirstReceiveTimestamp":"1671109684190"},"messageAttributes":{},"md5OfBody":"f0baf065f0f54d7c2db3e29e718c7f31","eventSource":"aws:sqs","eventSourceARN":"arn:aws:sqs:eu-west-2:538645939706:test-queue","awsRegion":"eu-west-2"}]}
2022-12-15T13:08:04.673+00:00	[main] INFO SqsOperation$ - decoded List(SqsDecoding(a7273082-2064-4b11-b740-84068f524f55,AQEBQaQ5SVS9KAzbnlGATBP3hwN13G3cIMg93n1cgp6KtkU5S9LsCWvyNIQ7h8NlBRX9JXWVjJtFei0odlcuZGwB63N9GRhJQXtmESeCAvcMQtLmC4RZiSYbl/IL4lX9T833sugZ+CKG5GKc8V8L7/3A+w0Kl/OZEBhNT9pGhIF8MJx/zkaNxu+hg8q0mT8EhVGpMyS8Gf4F5aeooZQQRS7uLpnYGSeMUHK58kFIHfs2BKlcATPUHZ5SppMAHaJDtBJCrEr9b6wk068NK1Vu144nzFVY9un5hjqaAvJWvduitMQCleXjvmH7cfj6rjM0yVRW/1lxHJGLIwck5oboZefdQx3dcCkkN4nj6IeHUs77k7C7LtwcyboLrvB603pzsWLxvgvGUZOdSWyd5sqbPE9ocA==,cowabunga,Map(ApproximateReceiveCount -> 1, SentTimestamp -> 1671109684185, SenderId -> 538645939706, ApproximateFirstReceiveTimestamp -> 1671109684190),Map(),f0baf065f0f54d7c2db3e29e718c7f31,aws:sqs,arn:aws:sqs:eu-west-2:538645939706:test-queue,eu-west-2))
2022-12-15T13:08:04.673+00:00	[main] INFO Main$ - processed message {"Records":[{"messageId":"a7273082-2064-4b11-b740-84068f524f55","receiptHandle":"AQEBQaQ5SVS9KAzbnlGATBP3hwN13G3cIMg93n1cgp6KtkU5S9LsCWvyNIQ7h8NlBRX9JXWVjJtFei0odlcuZGwB63N9GRhJQXtmESeCAvcMQtLmC4RZiSYbl/IL4lX9T833sugZ+CKG5GKc8V8L7/3A+w0Kl/OZEBhNT9pGhIF8MJx/zkaNxu+hg8q0mT8EhVGpMyS8Gf4F5aeooZQQRS7uLpnYGSeMUHK58kFIHfs2BKlcATPUHZ5SppMAHaJDtBJCrEr9b6wk068NK1Vu144nzFVY9un5hjqaAvJWvduitMQCleXjvmH7cfj6rjM0yVRW/1lxHJGLIwck5oboZefdQx3dcCkkN4nj6IeHUs77k7C7LtwcyboLrvB603pzsWLxvgvGUZOdSWyd5sqbPE9ocA==","body":"cowabunga","attributes":{"ApproximateReceiveCount":"1","SentTimestamp":"1671109684185","SenderId":"538645939706","ApproximateFirstReceiveTimestamp":"1671109684190"},"messageAttributes":{},"md5OfBody":"f0baf065f0f54d7c2db3e29e718c7f31","eventSource":"aws:sqs","eventSourceARN":"arn:aws:sqs:eu-west-2:538645939706:test-queue","awsRegion":"eu-west-2"}]}
2022-12-15T13:08:04.688+00:00	{"status":"OK"}
2022-12-15T13:08:04.695+00:00	END RequestId: b1c94f19-6ba5-5bde-b7dc-130c75cd44aa
2022-12-15T13:08:04.695+00:00	REPORT RequestId: b1c94f19-6ba5-5bde-b7dc-130c75cd44aa Duration: 229.29 ms Billed Duration: 261 ms Memory Size: 1024 MB Max Memory Used: 45 MB Init Duration: 30.88 ms
```

### Warm start at 1024MB - Billed Duration: 31 ms
```
2022-12-15T13:19:11.111+00:00	START RequestId: a0d4fa75-e5b6-590f-a9cd-a660c548d3c0 Version: $LATEST
2022-12-15T13:19:11.119+00:00	[main] DEBUG Main$ - mooo
2022-12-15T13:19:11.119+00:00	[main] INFO Main$ - I haz cheezeburgers {"Records":[{"messageId":"7c890a56-781d-4686-ac74-78599eee2625","receiptHandle":"AQEBNOYvUFVOzzwZYKvUS/u3OiNn5SxF7dY6WHGMYkxP21O2vv4g6ONfHL3UydiaKMQ4afLltt5v9y1wOased8JW2kaA16igOPYkSnIeE3LD2/C2sPO6JPGm6BH8J6DCyHF1w0jcTAJu7d8E/p9c1jmeodzQl2F5AOEC7Y0RDddcHrIphKkDi70ibN8mGl2LvCJ4v32pnNDkoqfSAqbOSAJZtNhMlu1qH8werTL1LuPbqlVYY/RUotP5jNLpU5RErxdI8c7+lVEaMGdT+k8Gi4VTteoFS6EzVIHIAinK3O/XHfC9sPNsoMOE4/FNjPWv5tGDbxs0qGlfyre8CxFwO0KNN0A2TzGsbwOOrx+EHsOf8w56T2BK4R0W3QU+MCP9Kdwr78KkxJhZy4GCM4R9XyIa1g==","body":"cowabunga","attributes":{"ApproximateReceiveCount":"1","SentTimestamp":"1671110351089","SenderId":"538645939706","ApproximateFirstReceiveTimestamp":"1671110351095"},"messageAttributes":{},"md5OfBody":"f0baf065f0f54d7c2db3e29e718c7f31","eventSource":"aws:sqs","eventSourceARN":"arn:aws:sqs:eu-west-2:538645939706:test-queue","awsRegion":"eu-west-2"}]}
2022-12-15T13:19:11.119+00:00	[main] ERROR Main$ - test error java.lang.RuntimeException: a at lambda.Main$.main(Main.scala:39) at lambda.Main.main(Main.scala) Caused by: java.lang.RuntimeException: b ... 2 more
2022-12-15T13:19:11.119+00:00	[main] INFO Main$ - processing message {"Records":[{"messageId":"7c890a56-781d-4686-ac74-78599eee2625","receiptHandle":"AQEBNOYvUFVOzzwZYKvUS/u3OiNn5SxF7dY6WHGMYkxP21O2vv4g6ONfHL3UydiaKMQ4afLltt5v9y1wOased8JW2kaA16igOPYkSnIeE3LD2/C2sPO6JPGm6BH8J6DCyHF1w0jcTAJu7d8E/p9c1jmeodzQl2F5AOEC7Y0RDddcHrIphKkDi70ibN8mGl2LvCJ4v32pnNDkoqfSAqbOSAJZtNhMlu1qH8werTL1LuPbqlVYY/RUotP5jNLpU5RErxdI8c7+lVEaMGdT+k8Gi4VTteoFS6EzVIHIAinK3O/XHfC9sPNsoMOE4/FNjPWv5tGDbxs0qGlfyre8CxFwO0KNN0A2TzGsbwOOrx+EHsOf8w56T2BK4R0W3QU+MCP9Kdwr78KkxJhZy4GCM4R9XyIa1g==","body":"cowabunga","attributes":{"ApproximateReceiveCount":"1","SentTimestamp":"1671110351089","SenderId":"538645939706","ApproximateFirstReceiveTimestamp":"1671110351095"},"messageAttributes":{},"md5OfBody":"f0baf065f0f54d7c2db3e29e718c7f31","eventSource":"aws:sqs","eventSourceARN":"arn:aws:sqs:eu-west-2:538645939706:test-queue","awsRegion":"eu-west-2"}]}
2022-12-15T13:19:11.120+00:00	{"Records":[{"messageId":"7c890a56-781d-4686-ac74-78599eee2625","receiptHandle":"AQEBNOYvUFVOzzwZYKvUS/u3OiNn5SxF7dY6WHGMYkxP21O2vv4g6ONfHL3UydiaKMQ4afLltt5v9y1wOased8JW2kaA16igOPYkSnIeE3LD2/C2sPO6JPGm6BH8J6DCyHF1w0jcTAJu7d8E/p9c1jmeodzQl2F5AOEC7Y0RDddcHrIphKkDi70ibN8mGl2LvCJ4v32pnNDkoqfSAqbOSAJZtNhMlu1qH8werTL1LuPbqlVYY/RUotP5jNLpU5RErxdI8c7+lVEaMGdT+k8Gi4VTteoFS6EzVIHIAinK3O/XHfC9sPNsoMOE4/FNjPWv5tGDbxs0qGlfyre8CxFwO0KNN0A2TzGsbwOOrx+EHsOf8w56T2BK4R0W3QU+MCP9Kdwr78KkxJhZy4GCM4R9XyIa1g==","body":"cowabunga","attributes":{"ApproximateReceiveCount":"1","SentTimestamp":"1671110351089","SenderId":"538645939706","ApproximateFirstReceiveTimestamp":"1671110351095"},"messageAttributes":{},"md5OfBody":"f0baf065f0f54d7c2db3e29e718c7f31","eventSource":"aws:sqs","eventSourceARN":"arn:aws:sqs:eu-west-2:538645939706:test-queue","awsRegion":"eu-west-2"}]}
2022-12-15T13:19:11.120+00:00	[main] INFO SqsOperation$ - decoded List(SqsDecoding(7c890a56-781d-4686-ac74-78599eee2625,AQEBNOYvUFVOzzwZYKvUS/u3OiNn5SxF7dY6WHGMYkxP21O2vv4g6ONfHL3UydiaKMQ4afLltt5v9y1wOased8JW2kaA16igOPYkSnIeE3LD2/C2sPO6JPGm6BH8J6DCyHF1w0jcTAJu7d8E/p9c1jmeodzQl2F5AOEC7Y0RDddcHrIphKkDi70ibN8mGl2LvCJ4v32pnNDkoqfSAqbOSAJZtNhMlu1qH8werTL1LuPbqlVYY/RUotP5jNLpU5RErxdI8c7+lVEaMGdT+k8Gi4VTteoFS6EzVIHIAinK3O/XHfC9sPNsoMOE4/FNjPWv5tGDbxs0qGlfyre8CxFwO0KNN0A2TzGsbwOOrx+EHsOf8w56T2BK4R0W3QU+MCP9Kdwr78KkxJhZy4GCM4R9XyIa1g==,cowabunga,Map(ApproximateReceiveCount -> 1, SentTimestamp -> 1671110351089, SenderId -> 538645939706, ApproximateFirstReceiveTimestamp -> 1671110351095),Map(),f0baf065f0f54d7c2db3e29e718c7f31,aws:sqs,arn:aws:sqs:eu-west-2:538645939706:test-queue,eu-west-2))
2022-12-15T13:19:11.120+00:00	[main] INFO Main$ - processed message {"Records":[{"messageId":"7c890a56-781d-4686-ac74-78599eee2625","receiptHandle":"AQEBNOYvUFVOzzwZYKvUS/u3OiNn5SxF7dY6WHGMYkxP21O2vv4g6ONfHL3UydiaKMQ4afLltt5v9y1wOased8JW2kaA16igOPYkSnIeE3LD2/C2sPO6JPGm6BH8J6DCyHF1w0jcTAJu7d8E/p9c1jmeodzQl2F5AOEC7Y0RDddcHrIphKkDi70ibN8mGl2LvCJ4v32pnNDkoqfSAqbOSAJZtNhMlu1qH8werTL1LuPbqlVYY/RUotP5jNLpU5RErxdI8c7+lVEaMGdT+k8Gi4VTteoFS6EzVIHIAinK3O/XHfC9sPNsoMOE4/FNjPWv5tGDbxs0qGlfyre8CxFwO0KNN0A2TzGsbwOOrx+EHsOf8w56T2BK4R0W3QU+MCP9Kdwr78KkxJhZy4GCM4R9XyIa1g==","body":"cowabunga","attributes":{"ApproximateReceiveCount":"1","SentTimestamp":"1671110351089","SenderId":"538645939706","ApproximateFirstReceiveTimestamp":"1671110351095"},"messageAttributes":{},"md5OfBody":"f0baf065f0f54d7c2db3e29e718c7f31","eventSource":"aws:sqs","eventSourceARN":"arn:aws:sqs:eu-west-2:538645939706:test-queue","awsRegion":"eu-west-2"}]}
2022-12-15T13:19:11.126+00:00	{"status":"OK"}
2022-12-15T13:19:11.141+00:00	END RequestId: a0d4fa75-e5b6-590f-a9cd-a660c548d3c0
2022-12-15T13:19:11.141+00:00	REPORT RequestId: a0d4fa75-e5b6-590f-a9cd-a660c548d3c0 Duration: 30.15 ms Billed Duration: 31 ms Memory Size: 1024 MB Max Memory Used: 47 MB
```
