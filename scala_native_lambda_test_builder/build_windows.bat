rem vcvars64.bat needs to be run, avira also blocks things as viruses so need to turn off
native-image  --no-server ^
  --no-fallback ^
  --allow-incomplete-classpath ^
  --report-unsupported-elements-at-runtime ^
  --static ^
  --initialize-at-build-time=scala.runtime.Statics ^
  -H:ConfigurationFileDirectories=src/main/resources/META-INF/native-image ^
  -H:+ReportExceptionStackTraces ^
  -H:TraceClassInitialization=true ^
  -jar target/scala-2.13/graalvm-scala-lambda.jar