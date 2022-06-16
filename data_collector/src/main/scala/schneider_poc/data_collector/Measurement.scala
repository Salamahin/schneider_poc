package schneider_poc.data_collector

import com.typesafe.scalalogging.LazyLogging
import zio.Schedule.fixed
import zio.{ZIO, Duration => ZDuration}

import scala.concurrent.duration.FiniteDuration

object Measurement extends LazyLogging {
  def program(dc: DataCollector, client: Client)(gw: Gateway, periodicity: FiniteDuration) = {
    ZIO
      .foreachParDiscard(gw.devices) { device =>
        val measurement = ZIO.foreachDiscard(device.gauges) {
          case (gaugeId, g) =>
            dc.measure(gaugeId, device.deviceId, g)
              .map(m => MeasuredGauge(gw.id, device.deviceId, gaugeId, m))
              .flatMap(client.send[MeasuredGauge])
              .foldCause(
                cause => logger.error(s"Failed to sent measurement gateway=$gaugeId, deviceId=${device.deviceId}, gaugeId=${gaugeId} because\n${cause.prettyPrint}"),
                _ => ()
              )
        }

        measurement.repeat(fixed(ZDuration fromScala periodicity))
      }
  }
}
