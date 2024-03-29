#!/bin/bash

cd "$(dirname "$0")"
source ~/.bashrc
native-image  --no-server \
  --no-fallback \
  --allow-incomplete-classpath \
  --report-unsupported-elements-at-runtime \
  --static \
  --initialize-at-build-time=scala.runtime.Statics \
  -H:ConfigurationFileDirectories=lambdas/src/main/resources/META-INF/native-image \
  -H:+ReportExceptionStackTraces \
  -H:TraceClassInitialization=true \
  -jar target/scala-2.13/graalvm-scala-lambda.jar
ls -l
./graalvm-scala-lambda "$(cat deployable/exampleSqsMessage.json)"



