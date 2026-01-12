package xs

import chisel3._
import chisel3.util._
import common.GenVerilog
import rial.arith.RealSpec
import rial.math.{DecomposeReal, PolynomialSpec, ReciprocalOtherPath, ReciprocalPreProcess, ReciprocalTableCoeff}

class RecEst() {
  println("dummy class")
}

//class RecEst(
//            val spec: RealSpec,
//            val nOrder: Int,
//            val adrW : Int, val extraBits : Int,
//            val dxW0 : Option[Int] = None,
//            val enableRangeCheck : Boolean = true,
//            val enablePolynomialRounding : Boolean = false,
//            )
//  extends Module {
//
//  val io = IO(new Bundle {
//    val x = Input(UInt(spec.W.W))
//    val y = Output(UInt(spec.W.W))
//  })
//
// val polySpec = new PolynomialSpec(spec.manW, nOrder, adrW, extraBits, dxW0,
//    enableRangeCheck, enablePolynomialRounding)
//
//  val xdecomp = Module(new DecomposeReal(spec))
//  xdecomp.io.real := io.x
//
//  val recPre   = Module(new ReciprocalPreProcess (spec, polySpec, stage.preStage))
//  val recTab   = Module(new ReciprocalTableCoeff (spec, polySpec, maxCbit))
//  val recOther = Module(new ReciprocalOtherPath  (spec, polySpec, otherStage))
//
//
//}
