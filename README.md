# Scala native lambdas

## Purpose of the experiment
Startup times with jvm based languages can make scala non-ideal for lambdas that have a high call rate that are expected to finish quickly such
as sqs message consumption. There is some argument for anything of real complication using dockerized sqs stream processing as it can 
fit nicely into the current dockerized workflow and easily fit into the same tracing and logging strategies. A pure function is observable
so can be ideal for serverless but when things start involving a lot of side effect the design can up being like a Rube Goldberg machine,
all the issues of microservices but multiplied.

I would personally not use native images for things like http servers as they can rely on runtime instrumentation and that will break.

The size of the lambdas memory does affect cold starts, default was about 1.5 seconds, 512MB about .5 seconds.

<https://arnoldgalovics.com/java-cold-start-aws-lambda-graalvm/> has examples showing how memory affects start up time.

[testRunLog.txt](scala_native_lambda_test_builder%2FtestRunLog.txt) has my own findings. Once warm 53ms.


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


