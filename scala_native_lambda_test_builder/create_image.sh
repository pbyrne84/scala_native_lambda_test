sbt test
sbt assembly
docker build -t amazon_scala_vm .
docker run --name amazon_lambda_compile --mount src="$(pwd)",target=/root/project_mount,type=bind -t -d amazon_scala_vm


# This does not work as linux_build.sh behaves differently than logging in with bash and running it
docker exec -it  amazon_lambda_compile  cd /root/project_mount;./linux_build.sh

# Running this then  cd /root/project_mount;./linux_build.sh works ?
docker exec -it amazon_lambda_compile bash