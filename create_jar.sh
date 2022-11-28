cd scala_native_lambda_test_cleanser
sbt +publishLocal
cd ..
cd scala_native_lambda_test_builder
sbt test
sbt assembly