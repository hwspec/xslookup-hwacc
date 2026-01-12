// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2025, UChicago Argonne, LLC.
// Main author: Kazutomo Yoshii <kazutomo@anl.gov>. See LICENSE in project root.

package xs

import chisel3.simulator.ChiselSim
//import chisel3.simulator.EphemeralSimulator._

import org.scalatest.flatspec.AnyFlatSpec
import xs.FPBigIntUtil.Float2BigInt

class CAMSearchSpec extends AnyFlatSpec with ChiselSim {
  behavior of "CAMSearch"

  val n_entries = 225
  val table = Array.tabulate(n_entries) { i => i*2 + 1}
  val tablefp = table.map( x => x.toFloat)

  "basictest: Integer" should "pass" in {
    simulate(new CAMSearch()) { dut =>
      dut.io.updateCam.poke(true)
      for(i <- 0 until n_entries) {
        dut.io.camaddr.poke(i)
        dut.io.camdata.poke(table(i))
        dut.clock.step()
      }
      dut.io.updateCam.poke(false)
      //
      dut.io.in.valid.poke(true)
      for(k <- Seq(table.head -1, table.head, table(10), table(11), table.last, table.last+1)) {
        dut.io.in.bits.poke(k)

        val refpos = BinarySearchRef.search(k, table)
        val pos = dut.io.out.bits.pos.peek().litValue
        val data = dut.io.out.bits.data.peek().litValue
        // println(s"key=$k pos=$pos data=$data refpos=$refpos")

        assert(pos == refpos, s"key=$k pos=$pos data=$data refpos=$refpos")

        dut.clock.step()
      }
    }
  }


  "basictest: FP" should "pass" in {
    simulate(new CAMSearchFP()) { dut =>
      dut.io.updateCam.poke(true)
      for(i <- 0 until n_entries) {
        dut.io.camaddr.poke(i)
        dut.io.camdata.poke(Float2BigInt(tablefp(i)))
        dut.clock.step()
      }
      dut.io.updateCam.poke(false)
      //
      dut.io.in.valid.poke(true)
      for(k <- Seq(tablefp.head - 1f, tablefp.head, tablefp(10), tablefp(11), tablefp.last, tablefp.last+1f)) {
        dut.io.in.bits.poke(Float2BigInt(k))

        val refpos = BinarySearchRef.search[Float](k, tablefp)
        val pos = dut.io.out.bits.pos.peek().litValue
        val data = dut.io.out.bits.data.peek().litValue
        //val fpval = BigInt2Float(data)
        // println(s"key=$k pos=$pos data=$fpval refpos=$refpos")

        assert(pos == refpos, s"key=$k pos=$pos data=$data refpos=$refpos")

        dut.clock.step()
      }
    }
  }
}
