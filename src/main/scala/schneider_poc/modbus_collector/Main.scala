package schneider_poc.modbus_collector

import org.rogach.scallop.ScallopConf
import zhttp.service.{ChannelFactory, EventLoopGroup}
import zio.Schedule.{fixed, forever}
import zio.{Clock, ZEnv, ZEnvironment, ZIO, ZIOAppArgs, ZIOAppDefault, Duration => ZDuration}

import scala.concurrent.duration.FiniteDuration

class ApplicationCli(args: Seq[String]) extends ScallopConf(args) {
  val serviceUrl   = opt[String](required = true)
  val registryFile = opt[String](required = true)
  val periodicity  = opt[FiniteDuration](required = true)

  verify()
}

object Main extends ZIOAppDefault {
  private def program(dc: DataCollector, client: Client)(gw: Gateway, periodicity: FiniteDuration) = {
    ZIO
      .foreachParDiscard(gw.devices) { device =>
        val measurement = ZIO.foreachDiscard(device.gauges) {
          case (gaugeId, g) =>
            dc.measure(device.deviceId, g)
              .map(m => MeasuredGauge(gw.id, device.deviceId, gaugeId, m))
              .flatMap(client.send[MeasuredGauge])
        }

        measurement.repeat(fixed(ZDuration fromScala periodicity))
      }
  }

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
              DataCollector
                .live(gw.host, gw.port)
                .flatMap(dc => program(dc, restClient)(gw, cli.periodicity()))
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
      .retry(forever)
      .exitCode
  }
}
