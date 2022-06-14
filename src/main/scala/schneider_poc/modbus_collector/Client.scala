package schneider_poc.modbus_collector

import com.typesafe.scalalogging.LazyLogging
import io.circe.{Encoder, Json}
import zhttp.http.{HttpData, Method}
import zhttp.service.{ChannelFactory, EventLoopGroup, Client => ZClient}
import zio.ZIO

case class MeasuredGauge(id: String, value: Measured)

object MeasuredGauge {
  implicit val measuredGaugeEncoder: Encoder[MeasuredGauge] = Encoder.instance {
    case MeasuredGauge(id, Numeric(timestamp, value)) =>
      Json.obj(
        "id"        -> Json.fromString(id),
        "timestamp" -> Json.fromLong(timestamp.toEpochMilli),
        "value"     -> Json.fromBigDecimal(value)
      )
  }

  def apply(gatewayId: String, deviceId: Int, gaugeId: String, value: Measured) = new MeasuredGauge(
    s"${gatewayId}+${deviceId}+${gaugeId}",
    value
  )
}

trait Client {
  def send[T: Encoder](message: T): ZIO[EventLoopGroup with ChannelFactory, Throwable, Unit]
}

object Client extends LazyLogging {
  def rest(endpoint: String): Client = new Client {
    import io.circe.syntax._

    override def send[T: Encoder](message: T) =
      for {
        r <- ZClient.request(
              url = endpoint,
              method = Method.POST,
              content = HttpData.fromString(message.asJson.noSpaces)
            )

        response <- r.bodyAsString
        _        = logger.debug(s"The message was sent, response=$response")

      } yield ()
  }
}
