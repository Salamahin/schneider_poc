package schneider_poc.data_collector

object TypeConversions {
  implicit class _2BytesConversions(bi: Int) {
    def uint16: BigInt = BigInt(bi & 0xffff)
  }

  implicit class _NBytesConversions(bi: Array[Int]) {
    def uint32: BigInt = {
      val Array(left, right) = bi.take(2).map(_.asInstanceOf[Long])
      BigInt((left << 16) | (right & 0xffff))
    }

    def float32: BigDecimal = {
      val Array(left, right) = bi.take(2)
      BigDecimal(java.lang.Float.intBitsToFloat((left << 16) | (right & 0xffff)))
    }

    def int64: BigInt = {
      val Array(a, b, c, d) = bi.take(4).map(_.asInstanceOf[Long])
      BigInt(a << 48 | b << 32 | c << 16 | d)
    }
  }
}
