package xs

import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.flatspec.AnyFlatSpec
import axi._
import axi.AxiModuleParamsHelper._

class XSAXISpec extends AnyFlatSpec with ChiselSim {
  val const1val = 0xbeefcafeL
  val const2val = getGitHash
  val p = XSModuleParams.default(const1val, const2val)

  "basictest" should "pass" in {
    simulate(new XSAXI(p, debugprint = true)) { dut =>
      val bfm = new Axi4Lite32BFM[XSAXI](dut)
      bfm.initMaster()

      bfm.expectVal(p.const1_r, const1val)
      bfm.expectVal(p.const2_r, const2val)

    }
  }
}
