package com.github.norwae.ignifera

import com.typesafe.config.ConfigFactory
import org.openjdk.jmh.annotations._
import org.slf4j.LoggerFactory

class BaselineHarness extends Harness(identity)
class StatsHarness extends Harness(StatsCollector.apply(_, Nil : _*))
class StatsBothListenersHarness extends Harness(StatsCollector.apply(_,
  new PrometheusStatisticsListener(new HttpCollectors(ConfigFactory.empty())),
  new AccessLogWriterListener(LoggerFactory.getLogger(classOf[StageBenchmarks]))
))
class GSSHarness extends Harness(GracefulShutdownSupport.apply(_))
class FullHarness extends Harness(flow ⇒
  GracefulShutdownSupport(
    StatsCollector.apply(flow,
      new PrometheusStatisticsListener(new HttpCollectors(ConfigFactory.empty())),
      new AccessLogWriterListener(LoggerFactory.getLogger(classOf[StageBenchmarks]))
    )
  )
)

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
  def statsWithBothListeners(harness: StatsBothListenersHarness): Unit = {
    import harness._
    runRequest()
  }

  @Benchmark @BenchmarkMode(Array(Mode.Throughput))
  def gss(harness: GSSHarness): Unit = {
    import harness._
    runRequest()
  }

  @Benchmark @BenchmarkMode(Array(Mode.Throughput))
  def full(harness: FullHarness): Unit = {
    import harness._
    runRequest()
  }
}
