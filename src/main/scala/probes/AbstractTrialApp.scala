package probes

abstract class AbstractTrialApp(trial: Trial) extends App {
  trial.run()
  System.exit(0)
}
