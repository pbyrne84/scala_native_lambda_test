FROM springci/graalvm-ce:master-java11

WORKDIR /tmp/dist
CMD native-image -jar /tmp/target/graalvm-scala-lambda.jar --enable-url-protocols=http graalvm-scala-lambda-linux  -Dagentlib:native-image-agent=config-output-dir=/tmp/target/scala-2.13/classes/META-INF/native-image
