package schneider_poc.data_collector

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

sealed trait Gauge
case class Uint32(offset: Int, scale: Int) extends Gauge
case class Uint16(offset: Int, scale: Int) extends Gauge
case class Int16(offset: Int, scale: Int)  extends Gauge
case class Int64(offset: Int)              extends Gauge
case class Float32(offset: Int)            extends Gauge

case class Connection(host: String, port: Int)
case class Measurement(gatewayId: String, deviceId: Int, gaugeDescription: String, gauge: Gauge)

case class MeasuredGauge(gateway: String, device: Int, gauge: String, timestamp: Long, value: BigDecimal)
object MeasuredGauge {
  implicit val recordEncoder: Encoder[MeasuredGauge] = deriveEncoder[MeasuredGauge]
}
