package schneider_poc.data_collector

import com.typesafe.scalalogging.LazyLogging
import org.rogach.scallop.ScallopConf
import schneider_poc.data_collector.DeviceRegistry
import zhttp.service.{ChannelFactory, EventLoopGroup}
import zio.{Clock, ZEnv, ZEnvironment, ZIO, ZIOAppArgs, ZIOAppDefault}

import scala.concurrent.duration.FiniteDuration

class ApplicationCli(args: Seq[String]) extends ScallopConf(args) {
  val serviceUrl   = opt[String](required = true)
  val registryFile = opt[String](required = true)
  val periodicity  = opt[FiniteDuration](required = true)

  verify()
}

object Main extends ZIOAppDefault with LazyLogging {

  override def run: ZIO[ZEnv with ZIOAppArgs, Any, Any] = {
    val cliLayer = ZIO
      .environment[ZIOAppArgs]
      .map(args => new ApplicationCli(args.get.getArgs))
      .toLayer

    val registryLayer = cliLayer
      .flatMap(cli => DeviceRegistry.fromFile(cli.get.registryFile()))

    val restLayer = cliLayer
      .map(cli => ZEnvironment(Client.rest(cli.get.serviceUrl())))

    (for {
      devRegistry <- ZIO.environmentWith[DeviceRegistry](_.get)
      restClient  <- ZIO.environmentWith[Client](_.get)
      cli         <- ZIO.environmentWith[ApplicationCli](_.get)

      gateways <- devRegistry.listGateways

      _ <- ZIO
            .foreachParDiscard(gateways) { gw =>
              ZIO.acquireReleaseWith[EventLoopGroup with ChannelFactory with Clock, Throwable, RealDataCollector, Unit](
                ZIO.attempt(new RealDataCollector(gw.host, gw.port)),
                dc => ZIO.succeed(dc.close()),
                dc => Measurement.program(dc, restClient)(gw, cli.periodicity())
              )
            }
    } yield ())
      .provideSomeLayer(
        EventLoopGroup.auto() ++
          ChannelFactory.auto ++
          Clock.live ++
          cliLayer ++
          restLayer ++
          registryLayer
      )
      .foldCause(
        failure => logger.error(s"Unexpected failure:\n${failure.prettyPrint}"),
        _ => ()
      )
  }
}
