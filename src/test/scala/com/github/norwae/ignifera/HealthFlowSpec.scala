package com.github.norwae.ignifera

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.github.norwae.ignifera.HealthCheckType.{Health, Readiness, RequestShutdown}
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.PushGateway
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.mockito.integrations.scalatest.MockitoFixture

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class HealthFlowSpec extends FlatSpec with Matchers with BeforeAndAfterAll with MockitoFixture {
  implicit val actorSystem: ActorSystem = ActorSystem("unittest")
  implicit val mat: Materializer = ActorMaterializer()

  "The health check" should "return 204 on a GET /health" in {
    val sut = new DefaultHealthFlow(HealthFlowConfiguration(() ⇒ Future.failed(new NoSuchElementException), () ⇒ (), None))
    runRequest(sut, Health) shouldEqual StatusCodes.NoContent
  }

  it should "return a 204 for each request" in {
    val sut = new DefaultHealthFlow(HealthFlowConfiguration(() ⇒ Future.failed(new NoSuchElementException), () ⇒ (), None))
    runRequests(sut, Health, Health, Health) should contain theSameElementsInOrderAs Seq.fill(3)(StatusCodes.NoContent)
  }

  "The readiness check" should "return 204 if its callback succeeds" in {
    val sut = new DefaultHealthFlow(HealthFlowConfiguration(() ⇒ Future.successful(Done), () ⇒ (), None))
    runRequest(sut, Readiness) shouldEqual StatusCodes.NoContent
  }
  it should "return 500 if the callback fails" in {
    val sut = new DefaultHealthFlow(HealthFlowConfiguration(() ⇒ Future.failed(new NoSuchElementException), () ⇒ (), None))
    runRequest(sut, Readiness) shouldEqual StatusCodes.InternalServerError
  }
  it should "call the callback for each request" in {
    var count = 0
    val sut = new DefaultHealthFlow(HealthFlowConfiguration(() ⇒ {
      count += 1; Future.successful(Done)
    }, () ⇒ (), None))
    runRequests(sut, Readiness, Readiness, Readiness, Readiness)
    count shouldEqual 4
  }

  "The graceful shutdown route" should "return 204 for the initial call" in {
    val sut = new DefaultHealthFlow(HealthFlowConfiguration(() ⇒ Future.successful(Done), () ⇒ (), None))
    runRequest(sut, RequestShutdown) shouldEqual StatusCodes.NoContent
  }

  it should "push metrics if push endpoint is set" in {
    val sut = new DefaultHealthFlow(HealthFlowConfiguration(() ⇒ Future.successful(Done), () ⇒ (), Some("127.0.0.1")))
    val pgMock = mock[PushGateway]
    sut.pushGateway = Some(pgMock)
    runRequest(sut, RequestShutdown) shouldEqual StatusCodes.NoContent
    verify(pgMock).push(any[CollectorRegistry], any[String])
  }

  it should "return 500 on subsequent calls" in {
    val sut = new DefaultHealthFlow(HealthFlowConfiguration(() ⇒ Future.successful(Done), () ⇒ (), None))
    runRequests(sut, RequestShutdown, RequestShutdown) should contain theSameElementsInOrderAs Seq(StatusCodes.NoContent, StatusCodes.InternalServerError)
  }

  it should "switch the /health/readiness route to 500 (without invoking the callback anymore)" in {
    var count = 0
    val sut = new DefaultHealthFlow(HealthFlowConfiguration(() ⇒ {
      count += 1; Future.successful(Done)
    }, () ⇒ (), None))
    runRequests(sut, RequestShutdown, Readiness) should contain theSameElementsInOrderAs Seq(StatusCodes.NoContent, StatusCodes.InternalServerError)
    count shouldEqual 0
  }

  private def runRequest(sut: DefaultHealthFlow, request: HealthCheckType) = runRequests(sut, request).head

  private def runRequests(sut: DefaultHealthFlow, requests: HealthCheckType*) = {
    Await.result(Source(requests.toList).via(Flow.fromGraph(sut)).runWith(Sink.seq), 10.seconds).map(_.status)
  }

  override protected def afterAll(): Unit = actorSystem.terminate()
}
