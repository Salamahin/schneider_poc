package schneider_poc.modbus_collector

import zio.{IO, Task, ZLayer}

sealed trait Gauge
case class Uint32(offset: Int, scale: Int) extends Gauge
case class Uint16(offset: Int, scale: Int) extends Gauge
case class Int16(offset: Int, scale: Int)  extends Gauge
case class Float32(offset: Int)            extends Gauge

case class Device(deviceId: Int, gauges: Map[String, Gauge])
case class Gateway(id: String, host: String, port: Int, devices: List[Device])
case class Registry(registry: Seq[Gateway])

trait DeviceRegistry {
  def listGateways: Task[Seq[Gateway]]
}

object DeviceRegistry {
  def fromFile(file: String) =
    ZLayer.succeed(new DeviceRegistry {
      override def listGateways: Task[Seq[Gateway]] = IO.attempt {
        import pureconfig._
        import pureconfig.generic.auto._

        ConfigSource.file(file).loadOrThrow[Registry].registry
      }
    })
}
