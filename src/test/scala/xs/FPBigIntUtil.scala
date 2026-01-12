// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2025, UChicago Argonne, LLC.
// Main author: Kazutomo Yoshii <kazutomo@anl.gov>. See LICENSE in project root.

package xs

object FPBigIntUtil {
  case class FPFormat(sign: Boolean, expo: Long, mant: Long)

  def UnpackFloat(v: Float): FPFormat = {
    val bits = java.lang.Float.floatToIntBits(v)

    val sign = if( ((bits >>> 31) & 1) > 0) true else false
    val expo = (bits >>> 23) & 0xff
    val mant = bits & 0x7fffff
    FPFormat(sign, expo, mant)
  }

  def PackFloat(p: FPFormat) : Float = {
    val sign = if(p.sign) BigInt(1)<<31 else BigInt(0)
    val expo = p.expo << 23
    BigInt2Float(sign | expo | BigInt(p.mant))
  }

  def Float2BigInt(v: Float): BigInt = {
    val bits = java.lang.Float.floatToIntBits(v)
    BigInt(bits & 0xFFFFFFFFL)
  }
  def BigInt2Float(v: BigInt): Float = {
    java.lang.Float.intBitsToFloat((v & 0xFFFFFFFFL).toInt)
  }

  def main(args: Array[String]): Unit = {
    val testvals = Seq(-1.1f, 0.0f, 2.2f)

    testvals.foreach { tv =>
      val v = Float2BigInt(tv)
      val f = BigInt2Float(v)
      assert(f == tv)
      val unpacked = UnpackFloat(tv)
      println(f"$tv => ${unpacked.sign} ${unpacked.expo}%x ${unpacked.mant}%x")
      val packed = PackFloat(unpacked)
      assert(packed == tv)
    }
  }
}
