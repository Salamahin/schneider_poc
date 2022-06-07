package schneider_poc.proxy

import com.typesafe.scalalogging.LazyLogging
import zhttp.http._
import zhttp.service.Server
import zio.{ExitCode, Task, URIO, ZIO}

object SimpleKafkaProxy extends LazyLogging {
  private val kafkaService = ZIO.environment[KafkaService].map(_.get)

  private val kafkaProxy = Http.collectZIO[Request] {
    case req @ Method.POST -> !! / topic =>
      logger.debug(s"New message for topic=$topic received")

      val keys = req.url.queryParams.get("key").iterator.to(Seq).flatten
      for {
        kafka   <- kafkaService
        message <- req.bodyAsString
        _       <- if (keys.isEmpty) kafka.produce(topic, message) else Task.foreachParDiscard(keys)(key => kafka.produce(topic, key, message))
      } yield Response.ok
  }

  def start(port: Int): URIO[KafkaService, ExitCode] = {
    Server
      .start(port, kafkaProxy)
      .exitCode
  }
}
