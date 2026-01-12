// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2025, UChicago Argonne, LLC.
// Main author: Kazutomo Yoshii <kazutomo@anl.gov>. See LICENSE in project root.

package xs

import chisel3._
import chisel3.util._
import chisel3.util.log2Ceil

class SRAMtest(n:Int = 8) extends Module {
  val addrsz = log2Ceil(n)

  val io = IO(new Bundle {
    val addr = Input(UInt(addrsz.W))
    val rdData = Output(UInt(32.W))
  })

  val depth = n
  val syncReadMem = SyncReadMem(depth, UInt(32.W))

  io.rdData := syncReadMem.read(io.addr)

  // Initialize the SyncReadMem with default values
  for (i <- 0 until depth) {
    syncReadMem.write(i.U, i.U) // Write default values to each memory word
  }
}

import common.GenVerilog
object SRAMtest extends App {
  GenVerilog.generate(new SRAMtest(225))
}
