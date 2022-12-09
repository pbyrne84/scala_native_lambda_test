CALL sbt test
CALL sbt assembly
CALL docker build -t amazon_scala_vm .
CALL docker run --name amazon_lambda_compile --mount src="$(pwd)/../",target=/root/project_mount,type=bind -t -d amazon_scala_vm

# Single line
CALL docker exec -it  amazon_lambda_compile  /bin/bash -c "cd /root/project_mount/scala_native_lambda_test_builder;./linux_build.sh"

# Log in and play around manually
CALL docker exec -it amazon_lambda_compile bash
