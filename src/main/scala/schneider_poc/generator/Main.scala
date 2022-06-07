package schneider_poc.generator

import com.typesafe.scalalogging.LazyLogging
import org.rogach.scallop.ScallopConf
import zhttp.http.{HttpData, Method}
import zio.{Schedule, ZEnv, ZIO, ZIOAppArgs, ZIOAppDefault}

import scala.concurrent.duration.FiniteDuration

class ApplicationCli(args: Seq[String]) extends ScallopConf(args) {
  val serviceUrl  = opt[String](required = true)
  val topic       = opt[String](required = true)
  val id          = opt[String](required = true)
  val periodicity = opt[FiniteDuration](required = true)

  verify()
}

object Main extends ZIOAppDefault with LazyLogging {
  import io.circe.syntax._
  import zhttp.service._

  override def run: ZIO[ZEnv with ZIOAppArgs, Any, Any] = {
    def sendRandomRecord(cli: ApplicationCli) =
      (for {
        nextRecord <- Record.next(cli.id())
        serialized = nextRecord.asJson.noSpaces
        _ <- Client
              .request(
                url = if (cli.serviceUrl() endsWith "/") s"${cli.serviceUrl()}${cli.topic()}" else s"${cli.serviceUrl()}/${cli.topic()}",
                method = Method.POST,
                content = HttpData.fromString(serialized)
              )
              .provideCustomLayer(
                ChannelFactory.auto ++ EventLoopGroup.auto()
              )
        _ = logger.debug(s"A new message $serialized generated and sent")
      } yield ()).schedule(Schedule.fixed(zio.Duration.fromScala(cli.periodicity())))

    for {
      args <- getArgs
      cli  = new ApplicationCli(args)
      _    <- sendRandomRecord(cli)
    } yield ()
  }
}
