sudo yum -y install sudo
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 20.3.1.2.r11-grl
gu install native-image
sdk install sbt
sudo yum install -y -q yum-utils
yum-config-manager --enable epel > /dev/null
sudo yum install -y zlib1g-dev gcc glibc-devel
sudo yum -y group install "development tools"
sudo yum install -y https://dl.fedoraproject.org/pub/epel/epel-release-latest-7.noarch.rpm
sudo yum install -y gcc-c++ binutils-devel glibc-devel glibc-static zlib-static