# Download virtualbox image
You can use docker for this but it is easier to experiment and debug in a mutable image then solidify into a docker after.
The whole thing can turn into a docker adventure versus a graalvm image build adventure. We need to compile the image
natively etc. Graalvm cannot pick reflectively loaded libraries so we need to build a canary to force loading while the image
is built.

1. Download https://cdn.amazonlinux.com/os-images/2.0.20210219.0/
2. Follow instructions here https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/amazon-linux-2-virtual-machine.html
   as you cannot log into the image without setting up the seedconfig as that sets the password up
3. Make sure the vm has enough RAM (3GB+). Default GB is not enough.
4. After logging in enable SSH https://medium.com/shehuawwal/download-and-run-amazon-linux-2-ami-locally-on-your-virtualbox-or-vmware-b554a98dcb1c
   ```
   vi /etc/ssh/sshd_config
   ```
   ```
   PasswordAuthentication yes
   ```
   Restart ssh so you can login via your terminal
   ```
   service sshd restart
   ```

5. Enable virtualbox port forwarding (https://medium.com/nycdev/how-to-ssh-from-a-host-to-a-guest-vm-on-your-local-machine-6cb4c91acc2e).
   You just need to enable the 5679 port bit on card 1.


User is ec2-user
Password is ec2-user


ssh 127.0.0.1 -l ec2-user -p 5679
yum install sudo



# Download an install graalvm within virtual box

1. Install https://sdkman.io/
2. sdk list java
3. Install graalvm e.g. ```sdk install java 20.3.1.2.r11-grl```
4. gu install native-image
5. Install sbt e.g. ```sdk install sbt```

# Create key download project from github

```ssh-keygen -t ed25519 -C "pbyrne84@gmail.com"```

# Install the compiler libraries
https://github.com/rwhaling/native-lambda/blob/master/native-lambda-base/Dockerfile

```
sudo yum install -y -q yum-utils
yum-config-manager --enable epel > /dev/null
sudo yum install zlib1g-dev gcc glibc-devel
sudo yum -y group install "development tools"
sudo yum install -y https://dl.fedoraproject.org/pub/epel/epel-release-latest-7.noarch.rpm
sudo yum install gcc-c++ binutils-devel glibc-devel glibc-static zlib-static
```

Not sure if the rest is needed as the problems were from missing glibc-static and zlib-static
```
sudo -i
echo $'[alonid-llvm-3.9.0] \n\
name=Copr repo for llvm-3.9.0 owned by alonid \n\
baseurl=https://copr-be.cloud.fedoraproject.org/results/alonid/llvm-3.9.0/epel-7-$basearch/ \n\
type=rpm-md \n\
skip_if_unavailable=True \n\
gpgcheck=1 \n\
gpgkey=https://copr-be.cloud.fedoraproject.org/results/alonid/llvm-3.9.0/pubkey.gpg \n\
repo_gpgcheck=0 \n\
enabled=1 \n\
enabled_metadata=1' >> /etc/yum.repos.d/epel.repo
```

sudo yum install -y clang-3.9.0
sudo yum install -y llvm-3.9.0 llvm-3.9.0-devel
pip install awscli
sudo mkdir -p /build/runtime/lib/ && cp /usr/lib64/libunwind.so /build/runtime/lib/libunwind.so.8 && cp /usr/lib64/libunwind-x86_64.so.8 /build/runtime/lib/libunwind-x86_64.so.8

