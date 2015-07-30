package probes

import java.util.concurrent.TimeUnit

import com.codahale.metrics.{ScheduledReporter, ConsoleReporter, Slf4jReporter, MetricRegistry}
import org.slf4j.{MDC, LoggerFactory}

object Trial {
  val MdcContext = "trial"
}

trait Trial {
  val metrics = new MetricRegistry()
  val log = LoggerFactory.getLogger(classOf[Trial])

  def name: String

  def createConsoleReporter(): ScheduledReporter = ConsoleReporter.forRegistry(metrics)
    .convertRatesTo(TimeUnit.SECONDS)
    .convertDurationsTo(TimeUnit.MILLISECONDS)
    .build()

  def createLogReporter(): ScheduledReporter = Slf4jReporter.forRegistry(metrics)
    .convertRatesTo(TimeUnit.SECONDS)
    .convertDurationsTo(TimeUnit.MILLISECONDS)
    .build()

  final def run() = {

    MDC.put(Trial.MdcContext, name)

    log.info("Starting trial...")
    log.info("Performing warm up step...")

    performWarmUp()

    log.info("Warm up step finished")

    val reporter = createLogReporter()

    reporter.start(5, TimeUnit.SECONDS)

    log.info("Performing main step...")

    performTrial()

    log.info("Main step finished")

    reporter.report()

    log.info(s"""Trial finished""")

    reporter.stop()
  }

  def performWarmUp(): Unit = {}
  def performTrial()
}
