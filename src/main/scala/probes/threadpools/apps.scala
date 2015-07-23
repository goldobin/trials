package probes.threadpools

import probes.AbstractTrialApp


object SingleThreadPoolTrailApp extends AbstractTrialApp(new SingleThreadPoolTrial())
object CachedThreadPoolTrailApp extends AbstractTrialApp(new CachedThreadPoolTrial())