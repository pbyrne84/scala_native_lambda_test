#!/bin/bash
: '
scp -P 5679 ec2-user@127.0.0.1:/home/ec2-user/development/scala_native_lambda_test/graalvm-scala-lambda ./
zip -j graalvm-scala-lambda.zip graalvm-scala-lambda bootstrap
aws s3api create-bucket --bucket pb-lambdas-deployment-native-packages --region us-east-1
aws s3api put-public-access-block \
  --bucket pb-lambdas-deployment-native-packages \
  --public-access-block-configuration "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"

aws s3 cp graalvm-scala-lambda.zip "s3://pb-lambdas-deployment-native-packages/" --acl bucket-owner-full-control


Create role
https://docs.aws.amazon.com/lambda/latest/dg/runtimes-custom.html
pb-lambda-test-graalvm-scala-lambda-role

aws lambda create-function --function-name pb-lambda-test-graalvm-scala-lambda \
  --zip-file fileb://graalvm-scala-lambda.zip --handler function.handler --runtime provided \
  --role arn:aws:iam::242194143705:role/pb-lambda-test-graalvm-scala-lambda-role


aws lambda invoke --function-name pb-lambda-test-graalvm-scala-lambda --payload '{"text":"Hello"}' response.txt --cli-binary-format raw-in-base64-out
'

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


