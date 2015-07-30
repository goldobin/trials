package probes.redis

import org.slf4j.{MDC, LoggerFactory}
import probes.redis.RedisDriverTrial.NodeAddress

object RedisDriverTrialApp extends App {

  val log = LoggerFactory.getLogger(RedisDriverTrialApp.getClass)

  case class AppConfig(
    driver: String = "jedis",
    nodes: List[NodeAddress] = List(NodeAddress("localhost", 6379)),
    cluster: Boolean = false,
    rate: Int = 100, // flows per second
    duration: Int = 60,
    freq: Int = 10
  )

  val Trials = Map(
    "jedis" -> JedisTrial,
    "redisson" -> RedissonTrial,
    "rediscala" -> RediscalaTrial
  )

  val parser = new scopt.OptionParser[AppConfig]("scopt") {
    head("Redis Driver Trial", "0.1")

    opt[String]("driver") required() action { (x, c) =>
      c.copy(driver = x.toLowerCase)
    } validate { x =>
      if (Trials.keySet.contains(x.toLowerCase)) success
      else failure(s"Unsupported driver name: $x")
    } valueName "<driver_name>" text s"possible values: ${Trials.keySet.toList.sorted.mkString(" | ")}"

    opt[Seq[String]]('h', "nodes") valueName "<host:port>,..." action { (x, c) =>
      val nodes = x map { s =>
        val (host :: port :: _) = s.split(":").toList
        NodeAddress(host, port.toInt)
      }
      c.copy(nodes = nodes.toList)
    } text "redis nodes"

    opt[Boolean]('c', "cluster") action { (x, c) =>
      c.copy(cluster = x)
    } text "cluster mode"

    opt[Int]('r', "rate") action { (x, c) =>
      c.copy(rate = x)
    } validate { x =>
      if (x > 0) success
      else failure("the rate should be positive")
    } text "the flow rate flows/second"

    opt[Int]('d', "duration") action { (x, c) =>
      c.copy(duration = x)
    } validate { x =>
      if (x > 0) success
      else failure("the duration should be positive")
    } text "the duration of load"

    opt[Int]("freq") action { (x, c) =>
      c.copy(freq = x)
    } validate { x =>
      if (x > 0) success
      else failure("the freq should be positive")
    } text "the frequency of injections per second"
  }

  parser.parse(args, AppConfig()) match {
    case Some(config) =>

      MDC.put("trial", "Init")

      val trialSettings = RedisDriverTrial.Settings(config.nodes, config.cluster)
      val trialExecutionSettings = TrialExecutionSettings.FixedRate(
        rate = config.rate,
        duration = config.duration,
        injectionFreq = config.freq
      )

      if (log.isInfoEnabled) {
        val mode = if (config.cluster) "cluster" else "single master"
        val nodes = {
          for { NodeAddress(host, port) <- config.nodes } yield s"$host:$port"
        }.mkString(" ")

        log.info(s"Redis nodes in $mode mode are: $nodes")
        log.info(
          "The trial will run with rate {} flows/s by {} seconds",
          trialExecutionSettings.rate,
          trialExecutionSettings.duration
        )
      }

      val trial = Trials(config.driver)(trialSettings, trialExecutionSettings)
      
      trial.run()

      System.exit(0)

    case None =>
    // arguments are bad, error message will have been displayed
  }
}