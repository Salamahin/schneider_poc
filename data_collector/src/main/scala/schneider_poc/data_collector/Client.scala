package schneider_poc.data_collector

import com.typesafe.scalalogging.LazyLogging
import io.circe.Encoder
import zhttp.http.{HttpData, Method}
import zhttp.service.{ChannelFactory, EventLoopGroup, Client => ZClient}
import zio.{Task, ZIO}

trait Client {
  def send[T: Encoder](message: T): Task[Unit]
}

object Client extends LazyLogging {
  def rest(endpoint: String): ZIO[EventLoopGroup with ChannelFactory, Nothing, Client] =
    for {
      env <- ZIO.environment[EventLoopGroup with ChannelFactory]
    } yield new Client {
      override def send[T: Encoder](message: T): Task[Unit] = {
        import io.circe.syntax._

        (for {
          r <- ZClient.request(
                url = endpoint,
                method = Method.POST,
                content = HttpData.fromString(message.asJson.noSpaces)
              )

          response <- r.bodyAsString
          _        = logger.debug(s"The message=$message was sent, response=$response")

        } yield ()).provideEnvironment(env)
      }
    }
}
