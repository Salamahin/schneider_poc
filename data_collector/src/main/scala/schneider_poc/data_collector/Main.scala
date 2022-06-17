package schneider_poc.data_collector

import com.typesafe.scalalogging.LazyLogging
import org.rogach.scallop.ScallopConf
import zhttp.service.{ChannelFactory, EventLoopGroup}
import zio.{Clock, ExitCode, ZEnv, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer}

import scala.concurrent.duration.FiniteDuration

class ApplicationCli(args: Seq[String]) extends ScallopConf(args) {
  val serviceUrl   = opt[String](required = true)
  val registryFile = opt[String](required = true)
  val periodicity  = opt[FiniteDuration](required = true)

  verify()
}

object Main extends ZIOAppDefault with LazyLogging {
  private def program(measurementPeriodicity: FiniteDuration): ZIO[Clock with DeviceRegistry with DataCollectorFactory with Client, Throwable, Unit] =
    for {
      restClient  <- ZIO.service[Client]
      dcFactory   <- ZIO.service[DataCollectorFactory]
      devRegistry <- ZIO.service[DeviceRegistry]

      measurements <- devRegistry.listMeasurements

      _ <- ZIO
            .foreachParDiscard(measurements) {
              case (Connection(host, port), measurements) =>
                dcFactory
                  .make(host, port)
                  .use { dc => new MeasurementsController(dc, restClient).startMeasurementsPar(measurements, measurementPeriodicity) }
                  .foldCause(
                    cause => logger.error(s"Failed to connect to Modbus TCP master at $host:$port because\n${cause.prettyPrint}"),
                    _ => ()
                  )
            }
    } yield ()

  override def run: ZIO[ZEnv with ZIOAppArgs, Any, Any] = {
    for {
      args  <- getArgs
      clock <- ZIO.service[Clock]

      cli       = new ApplicationCli(args)
      registry  = DeviceRegistry.fromFile(cli.registryFile())
      dcFactory <- DataCollectorFactory.live

      rest <- Client
               .rest(cli.serviceUrl())
               .provide(
                 EventLoopGroup.auto(),
                 ChannelFactory.auto
               )

      exitCode <- program(cli.periodicity())
                   .provide(
                     ZLayer.succeed(registry),
                     ZLayer.succeed(dcFactory),
                     ZLayer.succeed(rest),
                     ZLayer.succeed(clock)
                   )
                   .foldCause(
                     failure => { logger.error(s"Unexpected failure:\n${failure.prettyPrint}"); ExitCode.failure },
                     _ => ExitCode.success
                   )

    } yield exitCode
  }
}
