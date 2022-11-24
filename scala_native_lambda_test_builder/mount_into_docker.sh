


docker run \
    -v /tmp:/container/directory \
    alpine \
    ls /container/directory

docker run -it --mount src="$(pwd)",target=/root/project_mount,type=bind amazon_scala_vm


docker exec -it eager_pike /root