package com.github.norwae.ignifera

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Directives, RequestContext, Route}
import akka.http.scaladsl.client.RequestBuilding._
import akka.stream.ActorMaterializer
import io.prometheus.client.CollectorRegistry
import org.scalactic.source.Position
import org.scalatest.concurrent.PatienceConfiguration.Interval
import org.scalatest.concurrent.{Eventually, PatienceConfiguration}
import org.scalatest.time.{Milliseconds, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._

class StatsCollectorStageSpec extends FlatSpec with Matchers with BeforeAndAfterAll with Eventually {
  implicit val actorSystem: ActorSystem = ActorSystem("unittest")
  implicit val mat = ActorMaterializer()

  override implicit def patienceConfig = PatienceConfig(Span(10, Seconds), Span(100, Milliseconds))

  import actorSystem.dispatcher

  "The stats collector" should "track requests in flight" in {
    val route = Directives.get { ctx =>
      val delayed = Promise[Done]()
      actorSystem.scheduler.scheduleOnce(250.millis)(delayed.success(Done))

      delayed.future.flatMap(_ => ctx.complete("Hello World"))
    }

    runRequest(route, 5)
    eventually {
      val initialSample = CollectorRegistry.defaultRegistry.getSampleValue("http_requests_in_flight")
      initialSample.intValue() should be >= 4
    }

    eventually {
      val completeSample = CollectorRegistry.defaultRegistry.getSampleValue("http_requests_in_flight")
      completeSample.intValue() shouldEqual 0
    }
  }

  it should "track request sizes" in pending
  it should "track response sizes" in pending
  it should "track of responses begun" in pending

  def runRequest(route: Route, count: Int) = {
    val server = Http().bindAndHandle(StatsCollector(route), "localhost", 0)
    val srv = Await.result(server, Duration.Inf)
    val rq = Get(s"http://localhost:${srv.localAddress.getPort}/")
    val futures = for (_ <- 0 until count) yield {
      Http().singleRequest(rq)
    }

    futures.last andThen {
      case _ => srv.unbind()
    }

    futures
  }

  override protected def afterAll(): Unit = Await.ready(actorSystem.terminate(), Duration.Inf)
}
