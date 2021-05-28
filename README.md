# Scala native lambda test

## Purpose of the experiment
Startup times with jvm based languages can make scala non-ideal for lambdas that have a high call rate that are expected to finish quickly such
as sqs message consumption. There is some argument for anything of real complication alpakka is a better solution as lambdas seem like
they are prone to being very happy path oriented. They seem to be very unpleasant to debug in cloudtrail etc. They are also not very nice to deploy.

Hopefully this investigation helps with the perceived need to jump to other languages for things that need to be easily well tested etc. Ability to  
test easily and elegantly while also allowing change by anyone is usually where languages fall down. Easily and elegantly is subjective to the observer
due to their ability, priorities and personal philosophy.

Rust could be an interesting experiment at some point.

I would personally not use native images for things like http servers as we rely on runtime instrumentation and that will break. We would have to look
at getting the instrumentation in as they would have to be converted to a native implementation as well within the fat jar we convert.


## Technologies
1. Docker image based upon amazonlinux:2 (CentOS based) with dev tools as amazonlinux is a very light image
2. Graal vm (sdk install java 21.1.0.r11-grl (sdkman install))
3. Scala 2.13
4. Native image <https://www.graalvm.org/reference-manual/native-image/> (gu install native-image)


## Graalvm, jdk and reflection
Building a native image only the code from explicit execution calls gets auto bound in. Calls that are done via reflection which java based dependencies
have a lot more favour of doing that so all things are not compiled in. This leads to NoSuchMethodException being thrown and other similar errors exampled here
<https://github.com/oracle/graal/issues/1261>. There are a set of configuration files that can be added to the resources that can manage these sorts of problems.

### reflect-config.json
<https://www.graalvm.org/reference-manual/native-image/Reflection/>

This is very boring to manually manage but dependencies can be added here. There are ways to make this much less boring only if you find good test coverage
non-boring.

### agentlib:native-image-agent
The **agentlib:native-image-agent** instrumentation scans the execution paths making note of things like reflection calls generating all the configuration.
Note how I said you need like good test coverage to make life simple? We can run this while running tests to build the config.

#### Running the instrumentation when running tests via SBT

```scala
fork := true // This start a new java process as we cannot add instrumentation post start in the current

javaOptions in Test += "-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image"
```


#### Cleaning up the configs post running tests
The issue with running the instrumentation while running tests in sbt is we pick up stragglers that break the conversion. To solve this problem
I created a test shutdown hook that filters things out via circe.

```scala
testOptions in Test += Tests.Cleanup(
  () =>
    (for {
      _ <- CleanNativeImageConf.cleanResourceBundles(List("org/scalatest/.*")) // custom filter code
      _ <- CleanNativeImageConf.cleanReflectConfig(List("org\\.scalatest.*"))
    } yield true).left
      .map(error => throw error)
)
```
This could also be modified to add things that we cannot get via coverage. Java is the main culprit when it comes to runtime reflection as
scala prefers compile time safety via macros etc. Log4j is full of reflection.

There are warnings when compiling about things in the reflection-config not existing, these can be ignored if wished.

## Building the images

The image creation requires a fat jar as we pass the path of that to the **native-image** location. There may be a way other than assembly
as this can cause fun with merge strategies.

1. We run assembly with a test hook before it is build or singularly
```bash
sbt test
sbt assembly
```

2. We start the image with this project mounted
```bash
docker build -t amazon_scala_vm .
docker run --name amazon_lambda_compile --mount src="$(pwd)",target=/root/project_mount,type=bind -t -d amazon_scala_vm
```

3. We get docker to build it
```bash
docker exec -it amazon_lambda_compile bash
```
then when in the image we can build it.
```bash
cd /root/project_mount;./linux_build.sh
```

We should then get an executable that can be run in amazon lambda in the root of the project called
```
graalvm-scala-lambda
```

Note:
In theory we should be able to run
```bash
docker exec -it  amazon_lambda_compile  cd /root/project_mount;./linux_build.sh
```

This behaves differently and doesn't work. I am a docker noooooob.


## Running the file in aws lambda

We need to package our executable up with a bootstrap file into a zip to upload. Amazon uses a **bootstrap** file to run
any native executables.

Example modified from https://docs.amazonaws.cn/en_us/lambda/latest/dg/runtimes-walkthrough.html

```bash
#!/bin/sh
set -euo pipefail
while true
do
  HEADERS="$(mktemp)"
  # Get an event. The HTTP request will block until one is received
  EVENT_DATA=$(curl -sS -LD "$HEADERS" -X GET "http://${AWS_LAMBDA_RUNTIME_API}/2018-06-01/runtime/invocation/next")
# Extract request ID by scraping response headers received above
  REQUEST_ID=$(grep -Fi Lambda-Runtime-Aws-Request-Id "$HEADERS" | tr -d '[:space:]' | cut -d: -f2)
# Execute the binary
  RESPONSE=$(./graalvm-scala-lambda -Xmx128m -Djava.library.path=$(pwd))
# Send the response
  curl -sS -X POST "http://${AWS_LAMBDA_RUNTIME_API}/2018-06-01/runtime/invocation/$REQUEST_ID/response"  -d "$RESPONSE"
done

```

There are other examples

<https://github.com/aws-samples/aws-lambda-extensions/blob/main/custom-runtime-extension-demo/runtime/bootstrap>

<https://github.com/aws-samples/aws-lambda-extensions/blob/main/custom-runtime-extension-demo/extensionssrc/extensions/extension2.sh>


