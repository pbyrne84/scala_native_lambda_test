sbt -v -Jagentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image test
sbt -v -J-Xmx2048m  "run test"



JAVA_OPTS

SET JAVA_OPTS=-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image


 $env:JAVA_OPTS = '-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image'