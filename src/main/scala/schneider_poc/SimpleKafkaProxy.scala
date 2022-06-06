package schneider_poc

import zhttp.http._
import zhttp.service.Server
import zio.{ExitCode, URIO, ZIO}

object SimpleKafkaProxy {
  private val kafkaService = ZIO.environment[KafkaService].map(_.get)

  private val kafkaProxy = Http.collectZIO[Request] {
    case req @ Method.POST -> !! / topic =>
      for {
        kafka   <- kafkaService
        message <- req.bodyAsString
        _       <- kafka.produce(topic, message)
      } yield Response.ok
  }

  def start(port: Int): URIO[KafkaService, ExitCode] = {
    Server
      .start(port, kafkaProxy)
      .exitCode
  }
}
