package com.github.norwae.ignifera

import java.util.concurrent.atomic.AtomicInteger

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import io.prometheus.client.CollectorRegistry
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Milliseconds, Seconds, Span}

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}

class StatsCollectorStageSpec extends FlatSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll with Eventually {
  implicit val actorSystem: ActorSystem = ActorSystem("unittest")
  implicit val mat = ActorMaterializer()

  val collectors = new HttpCollectors(ConfigFactory.empty())

  override implicit def patienceConfig = PatienceConfig(Span(10, Seconds), Span(100, Milliseconds))

  import actorSystem.dispatcher

  "The stats collector" should "track requests in flight" in {
    val route = Directives.get {
      val delayed = Promise[Done]()
      actorSystem.scheduler.scheduleOnce(250.millis)(delayed.success(Done))

      Directives.complete(delayed.future.map(_ => "Hello World"))
    }

    runRequest(route, 5)
    eventually {
      val initialSample = CollectorRegistry.defaultRegistry.getSampleValue("http_requests_in_flight")
      initialSample.intValue() should be >= 4
    }

    eventually {
      val completeSample = CollectorRegistry.defaultRegistry.getSampleValue("http_requests_in_flight")
      completeSample should not be null
      completeSample.intValue() shouldEqual 0
      val processingTimeSample = CollectorRegistry.defaultRegistry.getSampleValue("http_request_duration_microseconds", Array("quantile"), Array("0.95"))
      processingTimeSample should not be null
      processingTimeSample.intValue().micros should be >= 250.millis
    }
  }

  it should "track request sizes" in {
    def buildRequest(port: Int) = {
      val body = Array.tabulate(1000)(_.toByte)
      Post(s"http://localhost:$port/", body)
    }

    val route = Directives.post {
      Directives.complete("All good")
    }

    runRequest(route, 10, buildRequest)

    eventually {
      val sample = CollectorRegistry.defaultRegistry.getSampleValue("http_request_size_bytes", Array("quantile"), Array("0.95"))
      sample should not be null
      sample.intValue() should (be >= 1000 and be < 1100)
    }
  }

  it should "track response sizes" in {
    val body = Array.tabulate(1000)(_.toByte)

    def buildRequest(port: Int) = {
      Put(s"http://localhost:$port/", body)
    }

    val route = Directives.put {
      Directives.complete(body)
    }

    runRequest(route, 10, buildRequest)

    eventually {
      val sample = CollectorRegistry.defaultRegistry.getSampleValue("http_response_size_bytes", Array("quantile"), Array("0.95"))
      sample should not be null
      sample.intValue() should (be >= 1000 and be < 1100)
    }
  }

  it should "track of responses begun" in {
    val beforeSample = CollectorRegistry.defaultRegistry.getSampleValue("http_requests_total", Array("method", "status"), Array("GET", "200"))
    val route = Directives.get {
      Directives.complete("Hello World")
    }

    runRequest(route, 25)
    eventually {
      val afterSample = CollectorRegistry.defaultRegistry.getSampleValue("http_requests_total", Array("method", "status"), Array("GET", "200"))
      afterSample.intValue() shouldEqual beforeSample.intValue() + 25
    }
  }

  private def simpleBuild(port: Int) = Get(s"http://localhost:$port/")

  private def runRequest(route: Route, count: Int, requestBuilder: Int => HttpRequest = simpleBuild) = {
    val server = Http().bindAndHandle(StatsCollector(route, collectors), "localhost", 0)
    val srv = Await.result(server, Duration.Inf)

    val futures = for (_ <- 0 until count) yield {
      val rq = requestBuilder(srv.localAddress.getPort)
      Http().singleRequest(rq).map(_.discardEntityBytes().future())
    }

    futures.last andThen {
      case _ => srv.unbind()
    }

    futures
  }

  override protected def afterAll(): Unit = Await.ready(actorSystem.terminate(), Duration.Inf)
}
