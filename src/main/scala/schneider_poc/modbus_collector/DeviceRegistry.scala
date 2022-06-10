package schneider_poc.modbus_collector

import zio.{ZIO, ZLayer}

sealed trait Gauge
case class NumericGauge(offset: Int, scale: Double) extends Gauge

case class Device(deviceId: Int, gauges: Map[String, Gauge])
case class Gateway(id: String, host: String, port: Int, devices: List[Device])

object DeviceRegistry {
  def fromFile(file: String) =
    ZLayer.fromZIO(ZIO.attempt {
      import pureconfig._
      import pureconfig.generic.auto._

      ConfigSource.file(file).loadOrThrow[Gateway]
    })
}
