package com.github.norwae.ignifera

import org.openjdk.jmh.annotations._

class BaselineHarness extends Harness(identity)
class StatsHarness extends Harness(StatsCollector.apply(_))
class GSSHarness extends Harness(GracefulShutdownSupport.apply(_))

class StageBenchmarks {
  @Benchmark @BenchmarkMode(Array(Mode.Throughput))
  def baseline(harness: BaselineHarness): Unit = {
    import harness._
    runRequest()
  }

  @Benchmark @BenchmarkMode(Array(Mode.Throughput))
  def stats(harness: StatsHarness): Unit = {
    import harness._
    runRequest()
  }

  @Benchmark @BenchmarkMode(Array(Mode.Throughput))
  def gss(harness: GSSHarness): Unit = {
    import harness._
    runRequest()
  }
}
