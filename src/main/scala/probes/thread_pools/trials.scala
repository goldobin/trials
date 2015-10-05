package probes.thread_pools

import java.util.concurrent.{Executors, CountDownLatch}

import probes.Trial

object ThreadPoolTrial {

  val Iterations = 4000

  val Job = new Runnable {
    override def run(): Unit = {
      var counter = 0
      for (_ <- 1 to 100) counter += 1
    }
  }
}

trait ThreadPoolTrial extends Trial{

  def iterations: Int

  val executor = scala.concurrent.ExecutionContext.global

  val submisions = metrics.meter("submissions")
  val submissionTime = metrics.timer("submission-time")

  override def performTrial(): Unit = {

    val latch = new CountDownLatch(iterations)

    for (_ <- 1 to iterations) {
      executor.execute(new Runnable {
        override def run(): Unit = {
          submisions.mark()
          val t = submissionTime.time()

          performSubmit(ThreadPoolTrial.Job)

          t.stop()

          latch.countDown()
        }
      })
    }

    latch.await()
  }

  def performSubmit(job: Runnable)
}

class SingleThreadPoolTrial(override val iterations: Int = ThreadPoolTrial.Iterations) extends ThreadPoolTrial {

  val name = "Single Thread Executor"

  override def performSubmit(job: Runnable): Unit = {
    Executors.newSingleThreadExecutor().submit(job)
  }
}

class CachedThreadPoolTrial(override val iterations: Int = ThreadPoolTrial.Iterations) extends ThreadPoolTrial {

  val name = "Cached Thread Executor"
  val testedThreadPool = Executors.newCachedThreadPool()

  override def performSubmit(job: Runnable): Unit = {
    testedThreadPool.submit(job)
  }
}