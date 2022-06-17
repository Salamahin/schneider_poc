package schneider_poc.data_collector

import zio.{Task, ZIO}

trait DeviceRegistry {
  def listMeasurements: Task[Map[Connection, List[Measurement]]]
}

object DeviceRegistry {
  case class Device(deviceId: Int, gauges: Map[String, Gauge])
  case class Gateway(id: String, host: String, port: Int, devices: List[Device])
  case class Registry(registry: Seq[Gateway])

  def fromFile(file: String): DeviceRegistry = new DeviceRegistry {
    override def listMeasurements = ZIO.attempt {
      import pureconfig._
      import pureconfig.generic.auto._

      val registry = ConfigSource.file(file).loadOrThrow[Registry].registry

      registry.map {
        case Gateway(gatewayId, host, port, devices) =>
          val connection = Connection(host, port)
          val measurements = for {
            d            <- devices
            (gaugeId, g) <- d.gauges
          } yield Measurement(gatewayId, d.deviceId, gaugeId, g)

          connection -> measurements
      }.toMap
    }
  }
}
