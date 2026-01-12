package Temp

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

class QTest extends Module {
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(UInt(32.W)))
    val res = Decoupled(UInt(32.W))
  })
//  val q = Module(new Queue(UInt(32.W),8))
//
//  q.io.enq <> io.req
//  io.res <> q.io.deq
  io.res <> io.req
}

object QTest extends App {
  val opts = Array("--disable-all-randomization",
    "--strip-debug-info",
    "--lowering-options=disallowLocalVariables",
    // "--split-verilog",
    //"--verilog",
    //"-o=generated/FPDIV_8_24.v"
  )
  ChiselStage.emitSystemVerilog(new QTest(), firtoolOpts = opts) // 5.0.0
}
