package schneider_poc.proxy

import com.typesafe.scalalogging.LazyLogging
import io.circe.Encoder
import zhttp.http._
import zhttp.service.Server
import zio.{ExitCode, IO, URIO, ZIO}

sealed trait Response
case class SingleMessageLocation(locations: MessageLocation)                  extends Response
case class MultipleMessagesLocations(locations: Map[String, MessageLocation]) extends Response

object SimpleKafkaProxy extends LazyLogging {
  import io.circe.generic.auto._
  import io.circe.syntax._

  implicit val responseEncoder: Encoder[Response] = Encoder.instance {
    case x @ SingleMessageLocation(_)     => x.asJson
    case x @ MultipleMessagesLocations(_) => x.asJson
  }

  private val kafkaService = ZIO.environment[KafkaService].map(_.get)

  private val kafkaProxy = Http.collectZIO[Request] {
    case req @ Method.POST -> !! / topic =>
      val keys = req.url.queryParams.get("key").iterator.to(Seq).flatten

      logger.debug(s"New request for topic=$topic, keys=$keys received")

      for {
        kafka   <- kafkaService
        message <- req.bodyAsString

        producingResult <- if (keys.isEmpty)
                            kafka
                              .produce(topic, message)
                              .map[Response](SingleMessageLocation)
                              .either
                          else
                            IO.foreachPar(keys)(key => kafka.produce(topic, key, message).map(key -> _))
                              .map(_.toMap)
                              .map[Response](MultipleMessagesLocations)
                              .either

      } yield producingResult match {
        case Left(_)          => Response.fromHttpError(HttpError.InternalServerError("Unknown error"))
        case Right(locations) => Response.json(locations.asJson.noSpaces)
      }
  }

  def start(port: Int): URIO[KafkaService, ExitCode] = {
    Server
      .start(port, kafkaProxy)
      .exitCode
  }
}
