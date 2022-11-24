sbt assembly
java -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image -cp target/scala-2.13/graalvm-scala-lambda.jar "lambda.Main"

