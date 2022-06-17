package schneider_poc.data_collector

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
import com.ghgande.j2mod.modbus.procimg.SimpleRegister
import com.typesafe.scalalogging.LazyLogging
import zio.{Clock, URIO, ZIO, ZManaged}

trait DataCollector {
  def measure(m: Measurement): ZIO[Clock, Throwable, MeasuredGauge]
}

object DataCollector extends LazyLogging {
  private def connect(host: String, port: Int) = {
    val m = new ModbusTCPMaster(host, port)
    m.connect()

    logger.debug(s"Successfully connected to TCP master at $host:$port")

    m
  }

  class RealDataCollector(master: ModbusTCPMaster) extends DataCollector with LazyLogging {
    private def measureGauge(measurement: Measurement) = {
      import TypeConversions._

      measurement.gauge match {
        case Int16(offset, scale) =>
          val value = master.readMultipleRegisters(measurement.deviceId, offset, 1).head
          BigDecimal(value.asInstanceOf[SimpleRegister].getValue) / scale

        case Uint16(offset, scale) =>
          val value = master.readMultipleRegisters(measurement.deviceId, offset, 1).head
          BigDecimal(value.asInstanceOf[SimpleRegister].getValue.uint16) / scale

        case Uint32(offset, scale) =>
          val value = master
            .readMultipleRegisters(measurement.deviceId, offset, 2)
            .map(_.asInstanceOf[SimpleRegister])
            .map(_.getValue)
            .uint32
          BigDecimal(value) / scale

        case Float32(offset) =>
          master
            .readMultipleRegisters(measurement.deviceId, offset, 2)
            .map(_.asInstanceOf[SimpleRegister])
            .map(_.getValue)
            .float32

        case Int64(offset) =>
          val measured = master
            .readMultipleRegisters(measurement.deviceId, offset, 4)
            .map(_.asInstanceOf[SimpleRegister])
            .map(_.getValue)
            .int64

          BigDecimal(measured)
      }
    }

    override def measure(m: Measurement) =
      for {
        (measured, now) <- ZIO.attempt { measureGauge(m) } zipPar Clock.instant
        _               = logger.debug(s"Measurement complete, details=$m, result=$measured")
      } yield MeasuredGauge(m.gatewayId, m.deviceId, m.gaugeDescription, now.toEpochMilli, measured)
  }

  def live(host: String, port: Int) =
    ZManaged
      .acquireReleaseWith(ZIO.attempt(connect(host, port)))(m => ZIO.succeed(m.disconnect()))
      .map(m => new RealDataCollector(m))
}
