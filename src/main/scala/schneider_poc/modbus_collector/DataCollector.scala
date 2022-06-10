package schneider_poc.modbus_collector

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
import com.ghgande.j2mod.modbus.procimg.SimpleRegister
import com.typesafe.scalalogging.LazyLogging
import zio.{Clock, ULayer, URIO, ZIO, ZLayer, ZManaged}

import java.time.Instant

sealed trait Measured {
  val timestamp: Instant
}
case class Numeric(override val timestamp: Instant, value: Double) extends Measured

trait DataCollector {
  def measure(deviceId: Int, gauge: Gauge): URIO[Clock, Measured]
}

object DataCollector {
  class RealDataCollector(master: ModbusTCPMaster) extends DataCollector with LazyLogging {
    override def measure(deviceId: Int, gauge: Gauge) = gauge match {
      case NumericGauge(offset, scale) =>
        for {
          measured <- URIO {
                       master
                         .readMultipleRegisters(deviceId, offset, 1)
                         .head
                         .asInstanceOf[SimpleRegister]
                         .getValue / scale
                     }

          now <- Clock.instant

          _ = logger.debug(s"Measurement complete, deviceId=$deviceId, gauge=$gauge, result=$measured")

        } yield Numeric(now, measured)
    }
  }

  def live(host: String, port: Int): ULayer[DataCollector] = {
    ZManaged
      .acquireReleaseWith(ZIO.succeed {
        val m = new ModbusTCPMaster(host, port)
        m.connect()
        m
      })(master => ZIO.succeed(master.disconnect()))
      .map(master => new RealDataCollector(master))
      .toLayer
  }
}
