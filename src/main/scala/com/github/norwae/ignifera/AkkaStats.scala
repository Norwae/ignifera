package com.github.norwae.ignifera

import akka.actor.{Actor, ActorSystem, DeadLetter, Props, UnhandledMessage}
import io.prometheus.client.Counter

/**
  * Optional additional statistics describing the status of the actor
  * system.
  */
class AkkaStats private extends Actor {
  context.system.eventStream.subscribe(self, classOf[DeadLetter])
  context.system.eventStream.subscribe(self, classOf[UnhandledMessage])

  def receive: Receive = {
    case _: DeadLetter =>
      AkkaStats.deadLetterCount.inc()
    case _: UnhandledMessage =>
      AkkaStats.unhandledCount.inc()
  }

}

object AkkaStats {

  private val deadLetterCount =
    Counter.build("app_akka_dead_letters", "Nr of dead letters encountered").create()

  private val unhandledCount =
    Counter.build("app_akka_unhandled_msg", "Nr of unhandled messages in the system").create()

  /**
    * Register the additional stats. The default implementation will register two additional
    * stats, counting the dead letters and unhandled messages.
    *
    * @param system actor system to observe.
    */
  def register()(implicit system: ActorSystem): Unit = {
    system.actorOf(Props(new AkkaStats))
    deadLetterCount.register()
    unhandledCount.register()
  }
}
