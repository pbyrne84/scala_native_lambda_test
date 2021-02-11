sbt assembly
java -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image -jar target/scala-2.13/main.jar