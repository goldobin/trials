#!/usr/bin/env bash

SBT_VERSION="0.13.8"

wget https://dl.bintray.com/sbt/debian/sbt-${SBT_VERSION}.deb

echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections

add-apt-repository -y ppa:webupd8team/java
add-apt-repository -y ppa:chris-lea/redis-server
apt-get -y update
# apt-get -y upgrade

apt-get -y install oracle-java8-installer git redis-tools ruby

rm -rf /var/lib/apt/lists/*
rm -rf /var/cache/oracle-jdk8-installer

dpkg -i sbt-${SBT_VERSION}.deb

apt-get -y install sbt
apt-get -y autoremove

gem install redis

curl http://download.redis.io/redis-stable/src/redis-trib.rb > /usr/bin/redis-trib.rb
chmod 755 /usr/bin/redis-trib.rb

