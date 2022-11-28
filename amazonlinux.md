
1. DockerFile in directory build
2. docker build -t scala-native-lambda .
3. cd .. (go down to root of project as we need to publish local the cleaner)
4. docker-compose down
4. docker container prune
3. docker run --name scala-native-lambda-compile --mount src="$(pwd)",target=/root/project_mount,type=bind -t -d scala-native-lambda 
4. docker exec -it scala-native-lambda-compile bash
5. cd /root/project_mount/scala_native_lambda_test_cleanser
6. sbt +publishLocal
7. cd ..
8. cd scala_native_lambda_test_builder
9. rm -rf src/main/resources/META-INF/native-image/*
9. sbt test
10. sbt assembly
11. ```bash
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
        ./graalvm-scala-lambda
    ```