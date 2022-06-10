package schneider_poc.modbus_collector

import org.rogach.scallop.ScallopConf
import zhttp.service.{ChannelFactory, EventLoopGroup}
import zio.{Clock, Schedule, ZEnv, ZIO, ZIOAppArgs, ZIOAppDefault}

import scala.concurrent.duration.FiniteDuration

class ApplicationCli(args: Seq[String]) extends ScallopConf(args) {
  val serviceUrl   = opt[String](required = true)
  val registryFile = opt[String](required = true)
  val periodicity  = opt[FiniteDuration](required = true)

  verify()
}

object Main extends ZIOAppDefault {

  def measure(gatewayId: String, device: Device) = {
    ZIO.foreachDiscard(device.gauges) {
      case (gaugeId, g) =>
        for {
          client        <- ZIO.environment[Client].map(_.get)
          dataCollector <- ZIO.environment[DataCollector].map(_.get)

          _ <- dataCollector
                .measure(device.deviceId, g)
                .map(m => MeasuredGauge(gatewayId, device.deviceId, gaugeId, m))
                .flatMap(client.send[MeasuredGauge])
        } yield ()

    }
  }

  override def run: ZIO[ZEnv with ZIOAppArgs, Any, Any] = {
    val cliLayer = ZIO
      .environment[ZIOAppArgs]
      .map(args => new ApplicationCli(args.get.getArgs))
      .toLayer

    val clientLayer = cliLayer
      .flatMap(cli => Client.rest(cli.get.serviceUrl()))

    val registryLayer = cliLayer
      .flatMap(cli => DeviceRegistry.fromFile(cli.get.registryFile()))

    val collectorLayer = registryLayer
      .flatMap(reg => DataCollector.live(reg.get.host, reg.get.port))

    val program = for {
      cli     <- ZIO.environment[ApplicationCli].map(_.get)
      gateway <- ZIO.environment[Gateway].map(_.get)

      _ <- ZIO.foreachParDiscard(gateway.devices) { d => measure(gateway.id, d).repeat(Schedule.fixed(zio.Duration.fromScala(cli.periodicity()))) }
    } yield ()

    program
      .retry(Schedule.forever)
      .provideSomeLayer(
        EventLoopGroup.auto() ++
          ChannelFactory.auto ++
          Clock.live ++
          cliLayer ++
          clientLayer ++
          registryLayer ++
          collectorLayer
      )
      .exitCode
  }
}
