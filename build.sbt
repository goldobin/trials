import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.packager.docker.DockerPlugin

name := "trials"

version := "1.0"

scalaVersion := "2.11.7"

resolvers += "rediscala" at "http://dl.bintray.com/etaty/maven"

libraryDependencies ++= Seq(
  "io.dropwizard.metrics" % "metrics-core" % "3.1.2",
  "redis.clients" % "jedis" % "2.7.3",
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "ch.qos.logback" % "logback-classic" % "1.1.3",
//  "org.redisson" % "redisson" % "1.2.0",
  "com.etaty.rediscala" %% "rediscala" % "1.4.0",
  "biz.paluch.redis" % "lettuce" % "4.0.Beta1",
  "com.github.scopt" %% "scopt" % "3.3.0"
)

mainClass in Compile := Some("probes.redis_trials.RedisDriverTrialApp")

packageName in Docker := "components/redis-trials"

addCommandAlias("test-redis-driver", "runMain probes.redis_trials.RedisDriverTrialApp")

enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)
