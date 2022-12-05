docker build -t amazon_scala_vm .
docker run --name amazon_lambda_compile --mount src="$(pwd)/../",target=/root/project_mount,type=bind -t -d amazon_scala_vm
docker exec -it amazon_lambda_compile bash
