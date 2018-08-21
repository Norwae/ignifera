package com.github.norwae.ignifera

import java.util.UUID
import java.util.concurrent.Semaphore

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import org.openjdk.jmh.annotations.{Scope, Setup, State, TearDown}
import org.openjdk.jmh.infra.Blackhole

import scala.concurrent.Await
import scala.concurrent.duration._


@State(Scope.Benchmark)
class Harness(transformer: Flow[HttpRequest, HttpResponse, Any] ⇒ Flow[HttpRequest, HttpResponse, Any]) {
  private val request = HttpRequest()
  private val semaphore = new Semaphore(0)

  private implicit var system: ActorSystem = _
  private implicit var mat: Materializer = _
  private var graphInput: ActorRef = _

  @Setup
  def start(): Unit = {
    system = ActorSystem(UUID.randomUUID().toString)
    mat = ActorMaterializer()

    val inner = transformer(Flow[HttpRequest].map { _ ⇒
      Blackhole.consumeCPU(1000)
      HttpResponse(StatusCodes.NotFound)
    })
    val handler =
      Source.actorRef[HttpRequest](1 << 16, OverflowStrategy.fail).
        via(inner).
        to(Sink.foreach(_ ⇒ semaphore.release()))

    graphInput = handler.run()

    runRequest()
  }

  def runRequest(): Unit ={
    graphInput ! request
    semaphore.acquire()
  }

  @TearDown
  def shutdown(): Unit = {
    Await.ready(system.terminate(), 10.seconds)
  }
}