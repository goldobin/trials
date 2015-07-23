package probes.redis

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ThreadFactory, Executors, TimeUnit, CountDownLatch}

import com.codahale.metrics.{Timer, Meter}
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.redisson.{Redisson, Config => RedissonConfig}
import org.slf4j.LoggerFactory
import redis.RedisClient
import redis.clients.jedis._
import probes.Trial
import probes.redis.RedisDriverTrial.Settings

import scala.concurrent.{Promise, Future}
import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

import scala.concurrent.ExecutionContext.Implicits._

import scala.collection.JavaConverters._

sealed trait TrialExecutionSettings

object TrialExecutionSettings {
  case class NoOfIterations(iterations: Int) extends TrialExecutionSettings
  case class FixedRate(rate: Int, duration: Int = 360, injectionFreq: Int = 20) extends TrialExecutionSettings
}

object RedisDriverTrial {
  case class NodeAddress(host: String, port: Int) {
    require(port > -1)
  }

  case class Settings(
    nodes: List[NodeAddress],
    isCluster: Boolean
  )
}

trait RedisDriverTrialFactory[T <: RedisDriverTrial] {
  def apply(redisSettings: RedisDriverTrial.Settings, executionSpec: TrialExecutionSettings): T
}


trait RedisDriverTrial extends Trial {

  def executionSpec: TrialExecutionSettings

  def keyPrefix: String

  def performWriteExpireAndPush(key: String, value: String, ttl: Int, queueKey: String): Future[Unit]
  def performPopReadDelete(queueKey: String): Future[Unit]

  protected def randKeySuffix = ThreadLocalRandom.current().nextLong(0, Long.MaxValue - 1)
  protected def randQueueSuffix = ThreadLocalRandom.current().nextLong(0, 6)
  
  protected def nextTtl = ThreadLocalRandom.current().nextInt(1, 59)
  protected def nextValue = s"value-$randKeySuffix"

  protected def nextKey = s"$keyPrefix:key-$randKeySuffix"
  protected def nextQueueKey = s"$keyPrefix:queue-$randQueueSuffix"

  private val wepTimer = metrics.timer("write-expire-push:time")
  private val wepSuccess = metrics.meter("write-expire-push:success")
  private val wepError = metrics.meter("write-expire-push:error")

  private val prdTimer = metrics.timer("pop-read-delete:time")
  private val prdSuccess = metrics.meter("pop-read-delete:success")
  private val prdError = metrics.meter("pop-read-delete:error")


  private def execute(spec: TrialExecutionSettings)(f: => Future[Unit]) = {
    import TrialExecutionSettings._

    spec match {
      case NoOfIterations(n) =>
        val l1 = new CountDownLatch(n)
        for (_ <- 1 to n) {
          f.onComplete { _ => l1.countDown() }
        }

        l1.await()

      case FixedRate(rate, duration, injectionsPerSecond) =>

        val intervalMillis = 1000 / injectionsPerSecond
        val opsPerInjection = rate / injectionsPerSecond

        val threadFactory =  new ThreadFactory {

          val counter = new AtomicInteger(0)

          override def newThread(r: Runnable): Thread = new Thread(r, s"fixed-rate-scheduler-${counter.incrementAndGet()}")
        }

        val future = Executors.newScheduledThreadPool(
          4,
          threadFactory
        ).scheduleAtFixedRate(
          new Runnable {
            override def run(): Unit = {
              for (_ <- 1 to opsPerInjection) f
            }
          },
          0,
          intervalMillis,
          TimeUnit.MILLISECONDS
        )

        Executors.newScheduledThreadPool(1).schedule(
          new Runnable {
            override def run(): Unit = { future.cancel(false) }
          },
          duration,
          TimeUnit.SECONDS
        )
        .get()
    }
  }

  private def instrumentOperation[T](successMeter: Meter, errorMeter: Meter, timer: Timer)(op: => Future[T]): Future[Unit] = {
    val ctx = timer.time()

    val promise = Promise[Unit]()

    op.onComplete { t =>
      t match {
        case Success(_) =>
          successMeter.mark()

        case Failure(NonFatal(e)) => {
          log.error("Error", e)
          errorMeter.mark()
        }
      }
      ctx.stop()
      promise.complete(Success(Unit))
    }

    promise.future
  }

  private def writeExpireAndPush(): Future[Unit] = {
    val key = nextKey
    val value = nextValue
    val ttl = nextTtl

    instrumentOperation (wepSuccess, wepError, wepTimer) {
      performWriteExpireAndPush(key, value, ttl, nextQueueKey)
    }
  }

  private def popReadAndDelete(): Future[Unit] = {
    val key = nextKey
    val value = nextValue
    val ttl = nextTtl

    instrumentOperation (prdSuccess, prdError, prdTimer) {
      performWriteExpireAndPush(key, value, ttl, nextQueueKey)
    }
  }

  override def performTrial(): Unit = {
    execute(executionSpec) {
      writeExpireAndPush()
      popReadAndDelete()
    }
  }
}

object JedisTrial extends RedisDriverTrialFactory[JedisTrial] {
  override def apply(redisSettings: Settings, executionSpec: TrialExecutionSettings): JedisTrial =
    new JedisTrial(redisSettings, executionSpec)
}

class JedisTrial(
  settings: RedisDriverTrial.Settings,
  override val executionSpec: TrialExecutionSettings
) extends RedisDriverTrial {

  val modeName = if (settings.isCluster) "cluster" else "single-master"

  override def name: String = s"Jedis ($modeName)"
  override def keyPrefix: String = s"jedis:$modeName"

  lazy val poolConfig = {
    val c = new GenericObjectPoolConfig
    //c.setMaxTotal(100)
    // Tests whether connection is dead when connection
    // retrieval method is called
    //c.setTestOnBorrow(true)
    /* Some extra configuration */
    // Tests whether connection is dead when returning a
    // connection to the pool
    //c.setTestOnReturn(true)
    // Number of connections to Redis that just sit there
    // and do nothing
    //c.setMaxIdle(5)
    // Minimum number of idle connections to Redis
    // These can be seen as always open and ready to serve
    //c.setMinIdle(1)
    // Tests whether connections are dead during idle periods
    //c.setTestWhileIdle(true)
    // Maximum number of connections to test in each idle check
    //c.setNumTestsPerEvictionRun(10)
    // Idle connection checking period
    //c.setTimeBetweenEvictionRunsMillis(60000)
    c
  }

  lazy val pool = {
    val (RedisDriverTrial.NodeAddress(host, port) :: _) = settings.nodes
    new JedisPool(poolConfig, host, port)
  }

  lazy val cluster = {
    val jedisClusterNodes = {
      settings.nodes map { case RedisDriverTrial.NodeAddress(host, port) => new HostAndPort(host, port)}
    }.toSet.asJava

    new JedisCluster(jedisClusterNodes, poolConfig)
  }

  def withRedis[U](block: JedisCommands => U) = {
    if (settings.isCluster) {
      block(cluster)
    } else {
      val redis = pool.getResource
      val result = block(redis)
      redis.close()
      result
    }
  }

  override def performWriteExpireAndPush(
    key: String,
    value: String,
    ttl: Int,
    queueKey: String) = Future { withRedis { redis =>

    redis.set(key, value)
    redis.expire(key, ttl)
    redis.lpush(queueKey, key)
  }}

  override def performPopReadDelete(queueKey: String) = Future { withRedis { redis =>
    val key = redis.lpop(queueKey)
    val value = redis.get(key)
    redis.del(key)
  }}
}

object RedissonTrial extends RedisDriverTrialFactory[RedissonTrial] {
  override def apply(redisSettings: Settings, executionSpec: TrialExecutionSettings): RedissonTrial =
    new RedissonTrial(redisSettings, executionSpec)
}

class RedissonTrial(
  settings: RedisDriverTrial.Settings,
  override val executionSpec: TrialExecutionSettings
) extends RedisDriverTrial {

  val modeName = if (settings.isCluster) "cluster" else "single-master"

  override def name: String = s"Redisson ($modeName)"
  override def keyPrefix: String = s"redisson: $modeName"

  val config = {
    val c = new RedissonConfig

    c.setThreads(4)

    if (settings.isCluster) {

      val clusterNodes = settings.nodes map { case RedisDriverTrial.NodeAddress(host, port) => s"$host:$port" }

      c.useClusterServers()
        .setMasterConnectionPoolSize(100)
        .addNodeAddress(clusterNodes:_*)

    } else {

      val (RedisDriverTrial.NodeAddress(host, port) :: _) = settings.nodes

      c.useSingleServer()
        .setConnectionPoolSize(100)
        .setAddress(s"$host:$port")
    }

    c
  }

  val redisson = Redisson.create(config)

  override def performWriteExpireAndPush(
    key: String,
    value: String,
    ttl: Int,
    queueKey: String) = Future {

    val bucket = redisson.getBucket[String](key)

    bucket.set(value, ttl, TimeUnit.SECONDS)

    val queue = redisson.getQueue[String](queueKey)
    queue.offer(key)
  }

  override def performPopReadDelete(queueKey: String) = Future {
    val queue = redisson.getQueue[String](queueKey)
    val key = queue.poll()

    val bucket = redisson.getBucket[String](key)

    bucket.delete()
  }
}

object RediscalaTrial extends RedisDriverTrialFactory[RediscalaTrial] {
  override def apply(redisSettings: Settings, executionSpec: TrialExecutionSettings): RediscalaTrial =
    new RediscalaTrial(redisSettings, executionSpec)
}

class RediscalaTrial(settings: RedisDriverTrial.Settings, override val executionSpec: TrialExecutionSettings) extends RedisDriverTrial {

  assert(!settings.isCluster, "Cluster mode is not supported by this driver")

  val (RedisDriverTrial.NodeAddress(host, port) :: _) = settings.nodes

  implicit val akkaSystem = akka.actor.ActorSystem()

  val redis = RedisClient(host, port)

  override def name: String = "Rediscala (single-master)"
  override def keyPrefix: String = "rediscala:single-master"

  override def performWriteExpireAndPush(
    key: String,
    value: String,
    ttl: Int,
    queueKey: String) = {

    val f1 = redis.set(key, value, Some(ttl))
    val f2 = redis.lpush(queueKey, key)

    for {
      _ <- f1
      _ <- f2
    } yield Unit
  }

  override def performPopReadDelete(queueKey: String) = {
    for {
      Some(key) <- redis.lpop[String](queueKey)
      v <- redis.get[String](key)
      _ <- redis.del(key)
    } yield Unit
  }
}