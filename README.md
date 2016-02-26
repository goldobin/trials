# Build

    sbt clean compile

# Trials

A set of trials of various things realted to Java/Scala/JDK/JVM:

1. Different ThreadPool submission timings

2. Redis driver comparison


# Redis Driver Trials

Run `sbt`

From SBT shell run:

    test-redis-driver <options>

Examples:

- to test [Jedis](https://github.com/xetorthio/jedis) driver run:

```
test-redis-driver --driver jedis -d 180 -r 500 --cluster true -h 127.0.0.1:7501,127.0.0.1:7502,127.0.0.1:7503,127.0.0.1:7504,127.0.0.1:7505,127.0.0.1:7506
```

- to test [Lettuce](https://github.com/mp911de/lettuce) driver run: 

```
test-redis-driver --driver lettuce -d 180 -r 500 --cluster true -h 127.0.0.1:7501,127.0.0.1:7502,127.0.0.1:7503,127.0.0.1:7504,127.0.0.1:7505,127.0.0.1:7506
```

- to test [Rediscala](https://github.com/etaty/rediscala) driver run:

```    
test-redis-driver --driver rediscala -d 180 -r 500 --cluster true -h 127.0.0.1:7501,127.0.0.1:7502,127.0.0.1:7503,127.0.0.1:7504,127.0.0.1:7505,127.0.0.1:7506
```

# Docker

To package to the docker image the 
[sbt-native-packager](http://www.scala-sbt.org/sbt-native-packager/formats/docker.html) is used. To package locally run:

```
sbt clean install docker:publishLocal
```

After that you can run the trial just by running docker container:

```
docker run redis-trials --driver jedis --cluster true --nodes <redis-node-container-ip-1>:6379,<redis-node-container-ip-2>:6379
```
