package xs

//import chisel3.simulator.ChiselSim
import chisel3.simulator.EphemeralSimulator._

import org.scalatest.flatspec.AnyFlatSpec
import xs.FPBigIntUtil._

object ReciprocalEstimateSW {

  def genRecLUT(lutW: Int, fixedW: Int) : Seq[Long] = {
    val lutsize = 1 << lutW
    val scaling = 1 << fixedW

    val recseq = Seq.tabulate(1 << lutW) { i =>
      val mval = 1.0 + i.toDouble / lutsize
      val recval = 1.0 / mval    // (0.5, 1.0]
      (recval * scaling).toLong & (scaling-1)
    }
    recseq
  }

  def recEstFloat(v: Float): Float = {
    val expoW: Int = 8
    val mantW: Int = 23
    // val bw = 1 + expoW + mantW

    val lutW : Int = 6   // the bit widths of lut
    val fixedW: Int = 16 // the bit widths of internal fixed-point

    val recLUT = genRecLUT(lutW, fixedW)
    // for (elem <- recLUT) {println(f"$elem%x")}

    val unpacked = UnpackFloat(v)
    // println(f"${unpacked.expo} ${unpacked.mant}%x")

    val mantidx = (unpacked.mant >> (mantW - lutW)).toInt
    val biasadj = if(mantidx>0) 1 else 0
    val lumant = recLUT(mantidx)
    val newmant = (lumant << (mantW - fixedW + 1)) & ((1<<mantW)-1)
    println(f"idx=$mantidx lumant=$lumant%x newmant=$newmant%x")

    // x    = 2^(e-bias)  * 1.M
    // x^-1 = 2^(-e+bias) * 1 / 1.M

    val bias = (1<<(expoW-1)) - 1
    val ve = unpacked.expo - bias  // expo = e + bias => e = expo - bias
    val recexpo = bias - ve - biasadj
    val newunpacked = FPFormat(unpacked.sign, recexpo, newmant)
    val fpval = PackFloat(newunpacked)
    // println(f"$bias ${unpacked.expo} $recexpo")
    // println(f"newmant=$newmant%x fpval=$fpval")

    // refine newmant with Newton-Raphson method
    // to find f(y) = 1/y + x = 0
    // y_(n+1) = y_n * (2 - x y_n)
    // let assume that (8 + mantW) bits storage for internal results

    // with floating point
    val fpint1 = fpval  * (2f - (v * fpval))
    val fpint2 = fpint1 * (2f - (v * fpint1))
    val fpref = 1f/v
    val fperr = (fpint2-fpref).abs/fpref
    println(f"Newton: in=$v ref=$fpref : $fpval $fpint1 $fpint2 err=$fperr%e")

    // with fixed point
    val x  = BigInt(unpacked.mant) | (BigInt(1) << mantW) // 1 + mantissa format
    val y0 = BigInt(newmant)>>biasadj | (BigInt(1) << mantW)

    val c  = BigInt(2) << mantW
    // 1st iteration
    val xy0 = (x * y0) >> mantW
    val y1 = (y0 * (c - xy0)) >> mantW
    val fpval1 = PackFloat(FPFormat(unpacked.sign, recexpo, (y1&((1<<mantW)-1)).toLong))
    println(f"x=$x%x y0=$y0%x => xy0=$xy0%x y1=$y1%x fpval1=$fpval1")

    // 2nd iteration
    val xy1 = (x * y1) >> mantW
    val y2 = (y1 * (c - xy1)) >> mantW
    val fpval2 = PackFloat(FPFormat(unpacked.sign, recexpo, y2.toLong))
    println(f"x=$x%x y1=$y1%x => xy1=$xy1%x y2=$y2%x fpval2=$fpval2")
    println("")
    fpval2
  }

  def main(args: Array[String]) : Unit = {
//    val unpacked = UnpackFloat(1.25f)
//    println(f"${unpacked.expo} ${unpacked.mant}%x")
//    val unpacked2 = UnpackFloat(0.8f)
//    println(f"${unpacked2.expo} ${unpacked2.mant}%x")

//    Seq(1f, 1.25f, 0.004589f, 123f).foreach(v =>
    Seq(.002f).foreach(v =>
      recEstFloat(v)
    )
  }
}




class ReciprocalEstimateSpec extends AnyFlatSpec /*with ChiselSim*/ {
  behavior of "ReciprocalEstimate"

  "basic test" should "pass" in {
    simulate(new ReciprocalEstimate()) { dut =>
      val testval = 1.25f
      val ref = 1.0f/testval

      dut.io.in.bits.poke(Float2BigInt(testval))
      val ret = dut.io.out.bits.peekValue().asBigInt
      val retfp = BigInt2Float(ret)

      println(s"in=$testval dut=$retfp ref=$ref")
    }
  }
}
