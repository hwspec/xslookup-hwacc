package xs
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

import scala.util.Random

class InterpolationSpec extends AnyFlatSpec {
  behavior of "Interpolation"

  val rng = new Random(123)

  // y = (h - e) / (h - l)
  case class intpParam(h: Float, e: Float, l: Float)

  "Testing RIAL-based interpolation" should "pass" in {
    def runtest(p: Array[intpParam]): Unit = {
      simulate(new Interpolation) { dut =>

        val latency = 3
        for (i <- 0 until p.length + latency) {
          if (i < p.length) {
            val fp_h = RialUtil.fp2bigint(p(i).h)
            val fp_e = RialUtil.fp2bigint(p(i).e)
            val fp_l = RialUtil.fp2bigint(p(i).l)
            dut.io.h.poke(fp_h)
            dut.io.e.poke(fp_e)
            dut.io.l.poke(fp_l)
          }
          if (i >= latency) {
            val res : Float = RialUtil.bigint2fp(dut.io.y.peek().litValue.toInt)
            val t = p(i - latency)
            val ref : Float = (t.h - t.e) / (t.h - t.l)
            assert(res == ref, f"$i: dut:$res did not match with ref:$ref")
          }
          dut.clock.step()
        }
      }
    }

    val fixed_inputs = Array(intpParam(3f, 1f, 2f), intpParam(4f, 1f, 2f), intpParam(11f, 1f, 1f))
    val rnd_inputs = Array.tabulate(3) { _ =>
      val h = rng.nextFloat()
      val e = rng.nextFloat()
      val d = rng.nextFloat() + 0.5f
      val l = -d + h
      intpParam(h, e, l) }
    val ps = fixed_inputs ++ rnd_inputs
    runtest(ps)
  }

  "Testing Hardfloat-based interpolation" should "pass" in {
    def runtest(p: Array[intpParam]): Unit = {
      simulate(new InterpolationWithHardFloat(debugprint=true)) { dut =>
        dut.io.in.valid.poke(true)

        dut.io.in.bits.h.poke(FPBigIntUtil.Float2BigInt(p(0).h))
        dut.io.in.bits.e.poke(FPBigIntUtil.Float2BigInt(-p(0).e))
        dut.io.in.bits.l.poke(FPBigIntUtil.Float2BigInt(-p(0).l))
        while (dut.io.in.ready.peek().litValue == 0) { dut.clock.step() }
        dut.clock.step()
        dut.io.in.valid.poke(false)
        dut.io.out.ready.poke(true)

        while (dut.io.out.valid.peek().litValue == 0) { dut.clock.step() }
        println("two")
        //val res = FPBigIntUtil.BigInt2Float(dut.io.out.bits.litValue)
        //println(s"res=$res")
      }
    }

    val fixed_inputs = Array(intpParam(3f, 1f, 2f), intpParam(4f, 1f, 2f), intpParam(11f, 1f, 1f))
    val rnd_inputs = Array.tabulate(3) { _ =>
      val h = rng.nextFloat()
      val e = rng.nextFloat()
      val d = rng.nextFloat() + 0.5f
      val l = -d + h
      intpParam(h, e, l)
    }
    //val ps = fixed_inputs ++ rnd_inputs
    val ps = Array(intpParam(3f, 1f, 2f))
    runtest(ps)
  }
}
