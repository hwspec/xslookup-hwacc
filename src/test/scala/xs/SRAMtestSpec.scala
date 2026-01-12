package xs

//import chisel3.simulator.ChiselSim
import chisel3.simulator.EphemeralSimulator._

import org.scalatest.flatspec.AnyFlatSpec

class SRAMtestSpec extends AnyFlatSpec /* with ChiselSim */ {
  behavior of "SRAMtest"

  val n = 225*6

  "basic test" should "pass" in {
    simulate(new SRAMtest(n))
      { c=>
        (0 until n).foreach { i =>
          c.io.addr.poke(i)
          c.clock.step()
          c.io.rdData.expect(i)
        }
      }
  }
}
