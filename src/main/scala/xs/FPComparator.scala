// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2025, UChicago Argonne, LLC.
// Main author: Kazutomo Yoshii <kazutomo@anl.gov>. See LICENSE in project root.

package xs

import chisel3._
import chisel3.util._
import common.GenVerilog

class UnpackFP(expW: Int = 5, sigW: Int = 10) extends Module {
  val bw = 1 + expW + sigW
  val io = IO(new Bundle {
    val in = Input(UInt(bw.W))
    val out_sign = Output(Bool())
    val out_exp = Output(UInt(expW.W))
    val out_sig = Output(UInt(sigW.W))
  })

  io.out_sig := io.in(sigW-1, 0)
  io.out_exp := io.in(bw-2, sigW)
  io.out_sign := Mux(io.in(bw-1) === 0.U, false.B, true.B)
}

class FPComparator(expW: Int = 5, sigW: Int = 10) extends Module {
  val bw = 1 + expW + sigW

  val io = IO(new Bundle {
    val inA = Input(UInt(bw.W))
    val inB = Input(UInt(bw.W))
    val out_equal = Output(Bool())
    val out_greater = Output(Bool())
  })

  val A = Module(new UnpackFP(expW, sigW))
  val B = Module(new UnpackFP(expW, sigW))

  A.io.in := io.inA
  B.io.in := io.inB

  val signEq = A.io.out_sign === B.io.out_sign
  val expEq = A.io.out_exp === B.io.out_exp
  val sigEq = A.io.out_sig === B.io.out_sig

  val signGt = !A.io.out_sign && B.io.out_sign
  val expGt = A.io.out_exp > B.io.out_exp
  val sigGt = A.io.out_sig > B.io.out_sig

  val magnitudeGt = expGt || (expEq && sigGt)
  val magnitudeGtSign = Mux(A.io.out_sign, !magnitudeGt, magnitudeGt)

  io.out_greater := Mux(signEq, magnitudeGtSign, signGt)

  io.out_equal := signEq && expEq && sigEq
}

object FPComparator extends App {
  GenVerilog.generate(new FPComparator())
}

class FPComparatorLTV0(expW: Int = 5, sigW: Int = 10) extends Module {
  val bw = 1 + expW + sigW

  val io = IO(new Bundle {
    val inA = Input(UInt(bw.W))
    val inB = Input(UInt(bw.W))
    val out_less = Output(Bool())
  })

  val A = Module(new UnpackFP(expW, sigW))
  val B = Module(new UnpackFP(expW, sigW))

  A.io.in := io.inA
  B.io.in := io.inB

  val signLt = A.io.out_sign && !B.io.out_sign
  val expLt = A.io.out_exp < B.io.out_exp
  val sigLt = A.io.out_sig < B.io.out_sig

  val magnitudeLt = expLt || (A.io.out_exp === B.io.out_exp && sigLt)
  val magnitudeLtSign = Mux(A.io.out_sign, !magnitudeLt, magnitudeLt)

  io.out_less := Mux(A.io.out_sign === B.io.out_sign, magnitudeLtSign, signLt)
}

object FPComparatorLTV0 extends App {
  GenVerilog.generate(new FPComparatorLTV0())
}

class FPComparatorLT(expW: Int = 5, sigW: Int = 10) extends Module {
  val bw = 1 + expW + sigW

  val io = IO(new Bundle {
    val inA = Input(UInt(bw.W))
    val inB = Input(UInt(bw.W))
    val out_less = Output(Bool())
  })

  // extract sign, exp, sig directly here
  val signA = io.inA(bw - 1)
  val expA = io.inA(bw - 2, sigW)
  val sigA  = io.inA(sigW-1, 0)
  val signB = io.inB(bw - 1)
  val expB  = io.inB(bw-2, sigW)
  val sigB  = io.inB(sigW-1, 0)

  val expLt = expA < expB
  val sigLt = sigA < sigB
  val magnitudeAbsLt = expLt || (expA === expB && sigLt)
  val magnitudeLt = Mux(signA, !magnitudeAbsLt, magnitudeAbsLt)

  val isLess = Mux(signA === signB, magnitudeLt, signA)

  io.out_less := isLess
}

class FPComparatorLTNoSign(expW: Int = 5, sigW: Int = 10) extends Module {
  val bw = 1 + expW + sigW

  val io = IO(new Bundle {
    val inA = Input(UInt(bw.W))
    val inB = Input(UInt(bw.W))
    val out_less = Output(Bool())
  })

  // extract sign, exp, sig directly here
  val expA = io.inA(bw - 2, sigW)
  val sigA  = io.inA(sigW-1, 0)
  val expB  = io.inB(bw-2, sigW)
  val sigB  = io.inB(sigW-1, 0)

  val expLt = expA < expB
  val sigLt = sigA < sigB
  val magnitudeAbsLt = expLt || (expA === expB && sigLt)
  val magnitudeLt = magnitudeAbsLt

  val isLess = magnitudeLt

  io.out_less := isLess
}


object FPComparatorLT extends App {
  GenVerilog.generate(new FPComparatorLT())
  GenVerilog.generate(new FPComparatorLTNoSign())
}


class FPComparatorGT(expW: Int = 5, sigW: Int = 10) extends Module {
  val bw = 1 + expW + sigW

  val io = IO(new Bundle {
    val inA = Input(UInt(bw.W))
    val inB = Input(UInt(bw.W))
    val out_greater = Output(Bool())
  })

  // extract sign, exp, sig directly here
  val signA = io.inA(bw-1)
  val expA  = io.inA(bw-2, sigW)
  val sigA  = io.inA(sigW-1, 0)
  val signB = io.inB(bw-1)
  val expB  = io.inB(bw-2, sigW)
  val sigB  = io.inB(sigW-1, 0)

  val expGt = expA > expB
  val sigGt = sigA > sigB
  val magnitudeAbsGt = expGt || (expA === expB && sigGt)
  val magnitudeGt = Mux(signA, !magnitudeAbsGt, magnitudeAbsGt)

  val isGreater = Mux(signA === signB, magnitudeGt, !signA)

  io.out_greater := isGreater
}

object FPComparatorGT extends App {
  GenVerilog.generate(new FPComparatorGT())
}
