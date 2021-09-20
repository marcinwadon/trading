package trading.alerts

import trading.core.AppTopic
import trading.domain.Alert
import trading.events.TradeEvent
import trading.lib.inject._
import trading.lib.{ Consumer, Producer }

import cats.effect._
import cr.pulsar.{ Config, Pulsar, Subscription }
import fs2.Stream

object Main extends IOApp.Simple {

  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap { case (engine, consumer) =>
        consumer.receive.evalMap(engine.run)
      }
      .compile
      .drain

  val config = Config.Builder.default

  val sub =
    Subscription.Builder
      .withName("alerts-sub")
      .withType(Subscription.Type.Shared)
      .build

  val topic = AppTopic.TradingEvents.make(config)

  def resources =
    for {
      pulsar <- Pulsar.make[IO](config.url)
      _      <- Resource.eval(IO.println(">>> Initializing alerts service <<<"))
      producer = Producer.stdout[IO, Alert]
      engine   = AlertEngine.make[IO](producer)
      consumer <- Consumer.pulsar[IO, TradeEvent](pulsar, topic, sub)
    } yield engine -> consumer

}