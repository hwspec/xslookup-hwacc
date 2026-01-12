// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2025, UChicago Argonne, LLC.
// Main author: Kazutomo Yoshii <kazutomo@anl.gov>. See LICENSE in project root.

package xs

import chisel3._
import chisel3.util._
import common.GenVerilog

class CAMRes(bwpos: Int = 8, bwdata: Int = 32) extends Bundle {
  val pos  = UInt(bwpos.W)
  val data = UInt(bwdata.W)
}

class CAMSearch(n_entries: Int = 225, expW: Int = 8, sigW: Int = 23,
                   debugprint: Boolean = false) extends Module {

  val bwdata: Int = 1 + expW + sigW
  val bwpos: Int = log2Ceil(n_entries + 1)

  val io = IO(new Bundle {
    val in = Flipped(Decoupled(UInt(bwdata.W)))
    val out = Decoupled(new CAMRes(bwpos, bwdata))
    // update the table
    val updateCam = Input(Bool())
    val camaddr = Input(UInt(log2Ceil(n_entries).W))
    val camdata = Input(UInt(bwdata.W))
  })

  val camReg = RegInit(VecInit(Seq.fill(n_entries)(0.U(bwdata.W))))
  when(io.updateCam) {
    camReg(io.camaddr) := io.camdata
  }

  val matchVec = VecInit(camReg.map(x => io.in.bits < x))
  val matchIndex = PriorityEncoder(matchVec)

  val idx = Mux(io.in.bits < camReg(0), 0.U,
    Mux(io.in.bits < camReg(n_entries-1),
      matchIndex - 1.U, (n_entries-2).U))

  io.in.ready := true.B
  io.out.valid := io.in.valid
  io.out.bits.pos := idx
  io.out.bits.data := camReg(idx)
}

object CAMSearch extends App {
  GenVerilog.generate(new CAMSearch())
}



class CAMSearchFP(n_entries: Int = 225, expW: Int = 8, sigW: Int = 23,
                debugprint: Boolean = false) extends Module {

  val bwdata: Int = 1 + expW + sigW
  val bwpos: Int = log2Ceil(n_entries + 1)

  val io = IO(new Bundle {
    val in = Flipped(Decoupled(UInt(bwdata.W)))
    val out = Decoupled(new CAMRes(bwpos, bwdata))
    // update the table
    val updateCam = Input(Bool())
    val camaddr = Input(UInt(log2Ceil(n_entries).W))
    val camdata = Input(UInt(bwdata.W))
  })

  val camReg = RegInit(VecInit(Seq.fill(n_entries)(0.U(bwdata.W))))
  when(io.updateCam) {
    camReg(io.camaddr) := io.camdata
  }

  val comperators = Array.tabulate(n_entries) { _ => Module(new FPComparatorLT(expW, sigW))}
  for(i <- 0 until n_entries) {
    comperators(i).io.inA := io.in.bits
    comperators(i).io.inB := camReg(i)
  }

  val matchVec = VecInit(comperators.map( x => x.io.out_less))
  val matchIndex = PriorityEncoder(matchVec)


  val cmphead = Module(new FPComparatorLT(expW, sigW))
  val cmplast = Module(new FPComparatorLT(expW, sigW))
  cmphead.io.inA := io.in.bits
  cmphead.io.inB := camReg(0)
  cmplast.io.inA := io.in.bits
  cmplast.io.inB := camReg(n_entries-1)

  val idx = Mux(cmphead.io.out_less, 0.U,
    Mux(cmplast.io.out_less,
      matchIndex - 1.U, (n_entries-2).U))

  io.in.ready := true.B
  io.out.valid := io.in.valid
  io.out.bits.pos := idx
  io.out.bits.data := camReg(idx)
}

object CAMSearchFP extends App {
  GenVerilog.generate(new CAMSearchFP())
}

class CAMSearchFPNoSign(n_entries: Int = 225, expW: Int = 8, sigW: Int = 23,
                  debugprint: Boolean = false) extends Module {

  val bwdata: Int = 1 + expW + sigW
  val bwpos: Int = log2Ceil(n_entries + 1)

  val io = IO(new Bundle {
    val in = Flipped(Decoupled(UInt(bwdata.W)))
    val out = Decoupled(new CAMRes(bwpos, bwdata))
    // update the table
    val updateCam = Input(Bool())
    val camaddr = Input(UInt(log2Ceil(n_entries).W))
    val camdata = Input(UInt(bwdata.W))
  })

  val camReg = RegInit(VecInit(Seq.fill(n_entries)(0.U(bwdata.W))))
  when(io.updateCam) {
    camReg(io.camaddr) := io.camdata
  }

  val comperators = Array.tabulate(n_entries) { _ => Module(new FPComparatorLTNoSign(expW, sigW))}
  for(i <- 0 until n_entries) {
    comperators(i).io.inA := io.in.bits
    comperators(i).io.inB := camReg(i)
  }

  val matchVec = VecInit(comperators.map( x => x.io.out_less))
  val matchIndex = PriorityEncoder(matchVec)


  val cmphead = Module(new FPComparatorLTNoSign(expW, sigW))
  val cmplast = Module(new FPComparatorLTNoSign(expW, sigW))
  cmphead.io.inA := io.in.bits
  cmphead.io.inB := camReg(0)
  cmplast.io.inA := io.in.bits
  cmplast.io.inB := camReg(n_entries-1)

  val idx = Mux(cmphead.io.out_less, 0.U,
    Mux(cmplast.io.out_less,
      matchIndex - 1.U, (n_entries-2).U))

  io.in.ready := true.B
  io.out.valid := io.in.valid
  io.out.bits.pos := idx
  io.out.bits.data := camReg(idx)
}

object CAMSearchFPNoSign extends App {
  GenVerilog.generate(new CAMSearchFPNoSign())
}
