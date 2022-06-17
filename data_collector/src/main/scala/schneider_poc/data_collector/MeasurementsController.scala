package schneider_poc.data_collector

import com.typesafe.scalalogging.LazyLogging
import zio.Schedule.fixed
import zio.{ZIO, Duration => ZDuration}

import scala.concurrent.duration.FiniteDuration

class MeasurementsController(dc: DataCollector, client: Client) extends LazyLogging {

  def startMeasurementsPar(measurements: Seq[Measurement], periodicity: FiniteDuration) = {
    ZIO
      .foreachParDiscard(measurements) { measurement =>
        dc.measure(measurement)
          .flatMap(client.send[MeasuredGauge])
          .foldCause(
            cause => logger.error(s"Failed to sent measurement, details=$measurement because\n${cause.prettyPrint}"),
            _ => ()
          )
          .repeat(fixed(ZDuration fromScala periodicity))
      }
  }
}
