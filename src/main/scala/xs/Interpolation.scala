// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2025, UChicago Argonne, LLC.
// Main author: Kazutomo Yoshii <kazutomo@anl.gov>. See LICENSE in project root.

package xs

import chisel3._
import chisel3.util._
import common.GenVerilog
import hardfloat.{AddRecFN, DivSqrtRecFN_small, fNFromRecFN, recFNFromFN}
import rial.arith._
import rial.math.FuncKind._
import rial.math._
import rial.util.PipelineStageConfig

object IEEEBits {
  val bitsmap = Map(
    16 -> (5, 10),
    32 -> (8, 23),
    64 -> (11, 52),
    128 -> (15, 112)
  )
  def nbits_expo(bw: Int): Int = bitsmap(bw)._1
  def nbits_mant(bw: Int): Int = bitsmap(bw)._2
}

/*
 * Interpolation using Rial arithmetic and reciprocal estimate
 *
 * y = (h - e) / (h - l)
 *
 * 3-cycle latency (substract, reciprocal estimate, and then multipy)
 * 1-cycle throughput
 *
 */
class Interpolation(bw: Int = 32) extends Module {
  val io = IO(new Bundle {
    val e = Input(UInt(bw.W))
    val h = Input(UInt(bw.W))
    val l = Input(UInt(bw.W))
    val y = Output(UInt(bw.W))
  })

  val spec = RealSpec.Float32Spec
  val submod1 = Module(new AddFPGeneric(spec, spec, spec, RoundSpec.roundToEven, PipelineStageConfig.none) )
  val submod2 = Module(new AddFPGeneric(spec, spec, spec, RoundSpec.roundToEven, PipelineStageConfig.none) )

  // param for recp estimate
  val nOrderFP32 = 2
  val adrWFP32 = 8
  val extraBitsFP32 = 3
  val fncfg: MathFuncConfig = new MathFuncConfig(Seq(Reciprocal))

  val recestmod = Module(new MathFunctions(fncfg, spec, nOrderFP32, adrWFP32, extraBitsFP32,
    MathFuncPipelineConfig.none, None, false, false))

  val mulmod = Module(new MultFPGeneric(spec, spec, spec, RoundSpec.roundToEven, PipelineStageConfig.none))

  //
  val neg_e = Wire(UInt(32.W))
  val neg_l = Wire(UInt(32.W))
  neg_e := io.e ^ 0x80000000L.U
  neg_l := io.l ^ 0x80000000L.U

  submod1.io.x := io.h
  submod1.io.y := neg_e

  submod2.io.x := io.h
  submod2.io.y := neg_l

  val hmeReg1 = RegNext(submod1.io.z)
  val hmeReg2 = RegNext(hmeReg1)
  val hmlReg = RegNext(submod2.io.z)

  recestmod.io.sel := fncfg.signal(Reciprocal)
  recestmod.io.x := hmlReg

  val rechmlReg = RegNext(recestmod.io.z)

  mulmod.io.x := hmeReg2
  mulmod.io.y := rechmlReg

  val mulReg = RegNext(mulmod.io.z)

  io.y := mulReg
}

object Interpolation extends App {
  GenVerilog.generate(new Interpolation)
}


class InterpolationWithHardFloat(bw: Int = 32, debugprint : Boolean = false) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new Bundle {
      val e = UInt(bw.W)
      val h = UInt(bw.W)
      val l = UInt(bw.W)
    }))
    val out = Decoupled(UInt(bw.W))
  })

  // default values
  io.out.bits := 0.U
  io.out.valid := false.B

  // module configs
  val expoW = IEEEBits.nbits_expo(bw)
  val mantW = IEEEBits.nbits_mant(bw)

  val subm1 = Module(new AddRecFN(expoW, mantW))
  val subm2 = Module(new AddRecFN(expoW, mantW))
  subm1.io.subOp := false.B
  subm1.io.roundingMode := 0.U
  subm1.io.detectTininess := 0.U
  subm1.io.a := 0.U
  subm1.io.b := 0.U
  subm2.io.subOp := false.B
  subm2.io.roundingMode := 0.U
  subm2.io.detectTininess := 0.U
  subm2.io.a := 0.U
  subm2.io.b := 0.U

  val divm = Module(new DivSqrtRecFN_small(expoW, mantW, options = 0)) // XXX: what is options?
  divm.io.sqrtOp := false.B
  divm.io.roundingMode := 0.U
  divm.io.detectTininess := 0.U
  divm.io.a := 0.U
  divm.io.b := 0.U
  divm.io.inValid := false.B


  object interState extends ChiselEnum {
    val sIdle, sSub2Div, sDiv, sDone = Value
  }
  import interState._

  val stateReg = RegInit(sIdle)
  io.in.ready := stateReg === sIdle

  val cntReg = RegInit(0.U(8.W))
  cntReg := cntReg + 1.U

  val sub1Reg = RegInit(0.U(bw.W))
  val sub2Reg = RegInit(0.U(bw.W))

  switch(stateReg) {
    is(sIdle) {
      when(io.in.valid) {
        if(debugprint) printf("%d: received new inputs\n", cntReg)
        subm1.io.a := recFNFromFN(expoW, mantW, io.in.bits.h)
        subm1.io.b := recFNFromFN(expoW, mantW, io.in.bits.e)
        subm2.io.a := recFNFromFN(expoW, mantW, io.in.bits.h)
        subm2.io.b := recFNFromFN(expoW, mantW, io.in.bits.l)
        sub1Reg := subm1.io.out
        sub2Reg := subm2.io.out
        stateReg := sSub2Div
      }
    }
    is(sSub2Div) {
      if(debugprint) printf("%d: sub to div\n", cntReg)
      divm.io.inValid := true.B
      printf("%d: divready=%d\n", cntReg, divm.io.inReady)
      val one = fNFromRecFN(expoW, mantW,  sub1Reg)
      val two = fNFromRecFN(expoW, mantW,  sub2Reg)
      printf("%d: subm1=%x/%x subm2=%x/%x\n", cntReg, sub1Reg, one, sub2Reg, two)
      divm.io.a := sub1Reg
      divm.io.b := sub2Reg
      stateReg := sDiv
    }
    is(sDiv) {
      when(cntReg > 30.U) {
        if(debugprint) printf("%d: div timeout\n", cntReg)
        stateReg := sDone
      }
      when(divm.io.outValid_div) {
        if(debugprint) printf("%d: div completed\n", cntReg)
        stateReg := sDone
      }
    }
    is(sDone) {
      when(io.out.ready) {
        if (debugprint) printf("%d: output\n", cntReg)

        io.out.valid := true.B
        io.out.bits :=  fNFromRecFN(expoW, mantW, divm.io.out)

        stateReg := sIdle
      }
    }
  }
}

object InterpolationWithHardFloat extends App {
  GenVerilog(new InterpolationWithHardFloat())
}


class InterpolationWithCFG(bw: Int = 32) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new Bundle {
      val e = UInt(bw.W)
      val h = UInt(bw.W)
      val l = UInt(bw.W)
    }))
    val out = Decoupled(UInt(bw.W))
  })

  io.in.ready := true.B
  io.out.bits := 0.U
  io.out.valid := true.B
}

object InterpolationWithCFG extends App {
  GenVerilog(new InterpolationWithCFG())
}
