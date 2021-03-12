# scala_native_lambda_test

docker kill $(docker ps -q)
docker build -t amazon_scala_vm .
docker run  amazon_scala_vm
docker exec –it eager_pike /root


aws lambda invoke --function-name pb-lambda-test-graalvm-scala-lambda --log-type Tail \
--query 'LogResult' --output text |  base64 -d



aws lambda invoke --function-name pb-lambda-test-graalvm-scala-lambda  \output