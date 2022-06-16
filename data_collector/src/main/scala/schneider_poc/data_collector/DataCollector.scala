package schneider_poc.data_collector

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
import com.ghgande.j2mod.modbus.procimg.SimpleRegister
import com.typesafe.scalalogging.LazyLogging
import zio.{Clock, URIO}

import java.time.Instant

case class Measured(timestamp: Instant, value: BigDecimal)

trait DataCollector {
  def measure(gaugeId: String, deviceId: Int, gauge: Gauge): URIO[Clock, Measured]
}

class RealDataCollector(host: String, port: Int) extends DataCollector with LazyLogging with AutoCloseable {
  private val master = new ModbusTCPMaster(host, port)
  master.connect()

  private def measureGauge(gaugeId: String, gauge: Gauge, master: ModbusTCPMaster, deviceId: Int) = {
    logger.debug(s"Measurement started, gaugeId=${gaugeId}, deviceId=$deviceId, gauge=$gauge")

    import TypeConversions._

    gauge match {
      case Int16(offset, scale) =>
        val value = master.readMultipleRegisters(deviceId, offset, 1).head
        BigDecimal(value.asInstanceOf[SimpleRegister].getValue) / scale

      case Uint16(offset, scale) =>
        val value = master.readMultipleRegisters(deviceId, offset, 1).head
        BigDecimal(value.asInstanceOf[SimpleRegister].getValue.uint16) / scale

      case Uint32(offset, scale) =>
        val value = master
          .readMultipleRegisters(deviceId, offset, 2)
          .map(_.asInstanceOf[SimpleRegister])
          .map(_.getValue)
          .uint32
        BigDecimal(value) / scale

      case Float32(offset) =>
        master
          .readMultipleRegisters(deviceId, offset, 2)
          .map(_.asInstanceOf[SimpleRegister])
          .map(_.getValue)
          .float32

      case Int64(offset) =>
        val measured = master
          .readMultipleRegisters(deviceId, offset, 4)
          .map(_.asInstanceOf[SimpleRegister])
          .map(_.getValue)
          .int64

        BigDecimal(measured)
    }
  }

  override def measure(gaugeId: String, deviceId: Int, gauge: Gauge) =
    for {
      measured <- URIO {
                   measureGauge(gaugeId, gauge, master, deviceId)
                 }
      now <- Clock.instant

      _ = logger.debug(s"Measurement complete, gaugeId=$gaugeId deviceId=$deviceId, gauge=$gauge, result=$measured")

    } yield Measured(now, measured)

  override def close(): Unit =
    master.disconnect()
}
