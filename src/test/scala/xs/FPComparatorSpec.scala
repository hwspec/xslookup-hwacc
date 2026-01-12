package xs

import chisel3.simulator.ChiselSim

import org.scalatest.Ignore
import org.scalatest.flatspec.AnyFlatSpec
import xs.FPBigIntUtil._

import scala.util.Random

@Ignore
class FPComparatorSpec extends AnyFlatSpec with ChiselSim {
  behavior of "FPComparator"

  "Random test: GTEQ" should "pass" in {
    simulate(new FPComparator(expW=8, sigW=23)) {dut =>
      for(_ <- 0 until 300) {
        val a = (Random.nextFloat()*2) - 1.0f
        val b = (Random.nextFloat()*2) - 1.0f

        val ia = Float2BigInt(a)
        val ib = Float2BigInt(b)
        val ref_gt = if (a  > b) true else false
        val ref_eq = if (a == b) true else false
        // println(s"testing: ${a} ${b}")
        dut.io.inA.poke(ia)
        dut.io.inB.poke(ib)
        dut.clock.step()
        val gt = dut.io.out_greater.peekBoolean()
        val eq = dut.io.out_equal.peekBoolean()
        assert(gt == ref_gt, f"${a}  > ${b} : dut=${gt} ref=${ref_gt}")
        assert(eq == ref_eq, f"${a} == ${b} : dut=${eq} ref=${ref_eq}")
      }
    }
  }

  "Random test: LT" should "pass" in {
    simulate(new FPComparatorLT(expW=8, sigW=23)) {dut =>
      for(_ <- 0 until 300) {
        val a = (Random.nextFloat()*2) - 1.0f
        val b = (Random.nextFloat()*2) - 1.0f

        val ia = Float2BigInt(a)
        val ib = Float2BigInt(b)
        val ref_lt = if (a < b) true else false
            // println(s"testing: ${a} ${b}")
        dut.io.inA.poke(ia)
        dut.io.inB.poke(ib)
        dut.clock.step()
        val lt = dut.io.out_less.peekBoolean()
        assert(lt == ref_lt, f"${a} < ${b} : dut=${lt} ref=${ref_lt}")
      }
    }
  }


  "Random test: GT" should "pass" in {
    simulate(new FPComparatorGT(expW=8, sigW=23)) {dut =>
      for(_ <- 0 until 300) {
        val a = (Random.nextFloat()*2) - 1.0f
        val b = (Random.nextFloat()*2) - 1.0f

        val ia = Float2BigInt(a)
        val ib = Float2BigInt(b)
        val ref_gt = if (a > b) true else false
        // println(s"testing: ${a} ${b}")
        dut.io.inA.poke(ia)
        dut.io.inB.poke(ib)
        dut.clock.step()
        val gt = dut.io.out_greater.peekBoolean()
        assert(gt == ref_gt, f"${a} > ${b} : dut=${gt} ref=${ref_gt}")
      }
    }
  }

}
