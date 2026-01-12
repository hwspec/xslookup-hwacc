// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2025, UChicago Argonne, LLC.
// Main author: Kazutomo Yoshii <kazutomo@anl.gov>. See LICENSE in project root.

package Temp

import chisel3.fromBooleanToLiteral
//import chiseltest._
import chisel3.simulator.ChiselSim

import org.scalatest.flatspec.AnyFlatSpec

class TempSpec extends AnyFlatSpec with ChiselSim {
  behavior of "Temp"
//
//  "Simple test" should "pass" in {
//    test(new Temp()).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { dut =>
//      dut.io.re.poke(false.B)
//      dut.io.we.poke(true.B)
//      dut.io.addr.poke(0)
//      dut.io.wdata.poke(0x0102030405060708L)
//      dut.clock.step()
//      dut.io.addr.poke(1)
//      dut.io.wdata.poke(0x090a0b0c0d0e0f10L)
//      dut.clock.step()
//      dut.io.we.poke(false.B)
//      dut.io.re.poke(true.B)
//      dut.io.addr.poke(0)
//      dut.clock.step()
//      println(f"${dut.io.rdata.peek().litValue}%016x")
//      dut.io.addr.poke(1)
//      dut.clock.step()
//      println(f"${dut.io.rdata.peek().litValue}%016x")
//      //
//    }
//  }
}
