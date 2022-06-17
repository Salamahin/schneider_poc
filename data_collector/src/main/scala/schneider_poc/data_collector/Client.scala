package schneider_poc.data_collector

import com.typesafe.scalalogging.LazyLogging
import io.circe.Encoder
import zhttp.http.{HttpData, Method}
import zhttp.service.{ChannelFactory, EventLoopGroup, Client => ZClient}
import zio.ZIO

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
        _        = logger.debug(s"The message=$message was sent, response=$response")

      } yield ()
  }
}
