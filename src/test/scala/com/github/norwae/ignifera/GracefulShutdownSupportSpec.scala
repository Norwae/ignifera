package com.github.norwae.ignifera

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import akka.http.scaladsl.client.RequestBuilding._

import scala.concurrent.Await
import scala.concurrent.duration._

class GracefulShutdownSupportSpec extends FlatSpec with BeforeAndAfterAll with Matchers {
  private implicit val actorSystem: ActorSystem = ActorSystem("unittest")
  private implicit val mat: Materializer = ActorMaterializer()

  private val unexpected = Flow[Any].map(_ ⇒ HttpResponse(StatusCodes.NotImplemented))
  private def expectHealth(expected: HealthCheckType) =
    Flow[HealthCheckType].map(it ⇒ HttpResponse(if (it == expected) StatusCodes.OK else StatusCodes.NotAcceptable))

  "The graceful shutdown support" should "route GET /health to the health check flow" in {
    runRequest(expectHealth(HealthCheckType.Health), unexpected, Get("/health")) shouldEqual StatusCodes.OK
  }

  it should "route GET /health/readiness to the health check flow" in {
    runRequest(expectHealth(HealthCheckType.Readiness), unexpected, Get("/health/readiness")) shouldEqual StatusCodes.OK
  }
  it should "route DELETE /health/readiness to the health check flow" in {
    runRequest(expectHealth(HealthCheckType.RequestShutdown), unexpected, Delete("/health/readiness")) shouldEqual StatusCodes.OK
  }

  it should "route other requests to the application flow" in {
    runRequest(unexpected, Flow[Any].map(_ ⇒ HttpResponse(StatusCodes.OK)), Get("/some/other/route")) shouldEqual StatusCodes.OK
  }

  it should "observe strict FIFO ordering even with a slow application route" in {
    runRequests(
      Flow[Any].map(_ ⇒ HttpResponse(StatusCodes.NoContent)),
      Flow[Any].delay(100.millis).map(_ ⇒ HttpResponse(StatusCodes.NotFound)),
      Delete("/foo/bar"),
      Get("/health"),
      Get("/health/readiness"),
      Get("/health"),
      Post("/"),
      Get("/health"),
      Get("/health/readiness"),
      Get("/health"),
      Options("/endpoint")
    ) should contain theSameElementsInOrderAs
      Seq(
        StatusCodes.NotFound,
        StatusCodes.NoContent,
        StatusCodes.NoContent,
        StatusCodes.NoContent,
        StatusCodes.NotFound,
        StatusCodes.NoContent,
        StatusCodes.NoContent,
        StatusCodes.NoContent,
        StatusCodes.NotFound)
  }

  it should "load and resolve the configuration successfully" in {
    GracefulShutdownSupport.config.isResolved shouldBe true
  }

  private def runRequest(healthFlow: Flow[HealthCheckType, HttpResponse, Any],
                 appFlow: Flow[HttpRequest, HttpResponse, Any],
                 rq: HttpRequest) = runRequests(healthFlow, appFlow, rq).head
  private def runRequests(healthFlow: Flow[HealthCheckType, HttpResponse, Any],
                 appFlow: Flow[HttpRequest, HttpResponse, Any],
                 rq: HttpRequest*) = {
    val sut = GracefulShutdownSupport(healthFlow, appFlow)
    Await.result(Source(rq.toList).via(sut).runWith(Sink.seq), 10.seconds).map(_.status)
  }

  override protected def afterAll(): Unit = actorSystem.terminate()
}
