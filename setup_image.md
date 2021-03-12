# Download virtualbox image
You can use docker for this but it is easier to experiment and debug in a mutable image then solidify into a docker after.
The whole thing can turn into a docker adventure versus a graalvm image build adventure. We need to compile the image
natively etc. Graalvm cannot pick reflectively loaded libraries out so we need to build a canary to froce loading while the image
is built.

1. Download https://cdn.amazonlinux.com/os-images/2.0.20210219.0/
2. Follow instructions here https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/amazon-linux-2-virtual-machine.html
   as you cannot log into the image without setting up the seedconfig as that sets the password up
3. After logging in enable SSH https://medium.com/shehuawwal/download-and-run-amazon-linux-2-ami-locally-on-your-virtualbox-or-vmware-b554a98dcb1c
   ```
   PasswordAuthentication yes
   ```
   Restart ssh so you can login via your terminal
   ```
   service sshd restart
   ```
4. Enable virtualbox port forwarding (https://medium.com/nycdev/how-to-ssh-from-a-host-to-a-guest-vm-on-your-local-machine-6cb4c91acc2e).
   You just need to enable the 5679 port bit on card 1.



# Download an install graalvm within virtual box

1. Install https://sdkman.io/
2. sdk list java
3. Install graalvm e.g. ```sdk install java 20.3.1.2.r11-grl```
3. Install sbt e.g. ```sdk install sbt```

# Create key download project from github

```ssh-keygen -t ed25519 -C "pbyrne84@gmail.com"```
