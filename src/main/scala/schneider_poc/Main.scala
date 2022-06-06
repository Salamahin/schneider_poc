package schneider_poc

import org.rogach.scallop.ScallopConf
import zhttp.service.Server
import zio.{ZEnv, ZIO, ZIOAppArgs, ZIOAppDefault}

class ApplicationCli(args: Seq[String]) extends ScallopConf(args) {
  val bootstrapSever = opt[String](required = true)
  val port           = opt[Int](required = true)

  verify()
}

object Main extends ZIOAppDefault {
  override def run: ZIO[ZEnv with ZIOAppArgs, Any, Any] =
    for {
      args <- getArgs
      cli  = new ApplicationCli(args.toList)
      srv <- SimpleKafkaProxy
        .start(cli.port())
        .provideService(KafkaService.live(cli.bootstrapSever()))
    } yield srv
}
