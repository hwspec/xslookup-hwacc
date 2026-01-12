// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2025, UChicago Argonne, LLC.
// Main author: Kazutomo Yoshii <kazutomo@anl.gov>. See LICENSE in project root.

package xs

//import hardfloat._
import chisel3._
import chisel3.util._
import common.GenVerilog

class BSRes(bwpos: Int = 8, bwdata: Int = 32) extends Bundle {
  val found = Bool()
  val pos  = UInt(bwpos.W)
  val data = UInt(bwdata.W)
}

class BinarySearch(n_entries: Int = 225, expW: Int = 8, sigW: Int = 23,
                   debugprint: Boolean = false) extends Module {

  val bwdata: Int = 1 + expW + sigW
  val bwpos: Int = log2Ceil(n_entries + 1)

  val io = IO(new Bundle {
    val in = Flipped(Decoupled(UInt(bwdata.W)))
    val out = Decoupled(new BSRes(bwpos, bwdata))
    // update the table
    val we_table = Input(Bool())
    val addr_table = Input(UInt(log2Ceil(n_entries).W))
    val data_table = Input(UInt(bwdata.W))
  })

  val stepsReg = RegInit(0.U(bwpos.W))
  stepsReg := stepsReg + 1.U

  val mem = SyncReadMem(n_entries, UInt(bwdata.W))

  // these regs are initialized when io.in.valid
  val leftReg = RegInit(0.U(bwpos.W))
  val rightReg = RegInit((n_entries-1).U(bwpos.W))
  val midReg = RegInit(((n_entries-1)>>1).U(bwpos.W))
  val keyReg = RegInit(0.U(bwdata.W))
  val insearchReg = RegInit(false.B)

  // regs for successful search
  val foundReg = RegInit(false.B)
  val posReg = RegInit(0.U(bwpos.W))
  val outValidReg = RegInit(false.B)

  val readposreq = Wire(UInt(bwpos.W))
  val readdata = Wire(UInt(bwdata.W))

  readposreq := leftReg + ((rightReg - leftReg) >> 1)

  when(io.we_table) {
    mem.write(io.addr_table, io.data_table)
  }

  // default values
  io.out.valid := false.B
  io.out.bits.found := false.B
  io.out.bits.pos := 0.U
  io.out.bits.data := 0.U

  // receive a new request
  io.in.ready := !insearchReg && !io.we_table
  when(io.in.valid && io.in.ready) {
    keyReg := io.in.bits
    leftReg := 0.U
    rightReg := (n_entries-1).U
    midReg := (n_entries-1).U >> 1
    insearchReg := true.B
    outValidReg := false.B
    stepsReg := 0.U
    if(debugprint) {
      printf("new request: key=%d\n", io.in.bits)
    }
  }

  when(insearchReg) {
    midReg := readposreq

    when(rightReg > leftReg + 1.U) {
      when(readdata > keyReg) {
        rightReg := midReg
      }.otherwise {
        leftReg := midReg
      }
    }.otherwise { // the search completes with a lower-bound hit
      insearchReg := false.B
      foundReg := true.B
      posReg := leftReg
      outValidReg := true.B

      readposreq := leftReg // request a read for the search result

      leftReg := 0.U
      rightReg := (n_entries-1).U
      midReg := (n_entries-1).U >> 1
    }

    if (debugprint) {
      printf("steps=%d low=%d high=%d len=%d pos=%d data=%d\n",
        stepsReg, leftReg, rightReg, (rightReg - leftReg), readposreq, readdata)
    }
  }
  readdata := mem.read(readposreq, io.in.valid || insearchReg) // only request a read when needed

  io.out.valid := outValidReg
  when(io.out.ready && outValidReg) {
    io.out.bits.found := foundReg
    io.out.bits.pos := posReg
    io.out.bits.data := readdata
    outValidReg := false.B
    if (debugprint) {
      printf("out: found=%d pos=%d data=%d\n", foundReg, posReg, readdata)
    }
  }
}

object BinarySearchDriver extends App {
  GenVerilog.generate(new BinarySearch(expW=8, sigW=24))
}



class BinarySearchFP(n_entries: Int = 225, expW: Int = 8, sigW: Int = 23,
                   debugprint: Boolean = false) extends Module {

  val bwdata: Int = 1 + expW + sigW
  val bwpos: Int = log2Ceil(n_entries + 1)

  val io = IO(new Bundle {
    val in = Flipped(Decoupled(UInt(bwdata.W)))
    val out = Decoupled(new BSRes(bwpos, bwdata))
    // update the table
    val we_table = Input(Bool())
    val addr_table = Input(UInt(log2Ceil(n_entries).W))
    val data_table = Input(UInt(bwdata.W))
  })

  val stepsReg = RegInit(0.U(bwpos.W))
  stepsReg := stepsReg + 1.U

  val mem = SyncReadMem(n_entries, UInt(bwdata.W))

  // these regs are initialized when io.in.valid
  val leftReg = RegInit(0.U(bwpos.W))
  val rightReg = RegInit((n_entries-1).U(bwpos.W))
  val midReg = RegInit(((n_entries-1)>>1).U(bwpos.W))
  val keyReg = RegInit(0.U(bwdata.W))
  val insearchReg = RegInit(false.B)

  // regs for successful search
  val foundReg = RegInit(false.B)
  val posReg = RegInit(0.U(bwpos.W))
  val outValidReg = RegInit(false.B)

  val readposreq = Wire(UInt(bwpos.W))
  val readdata = Wire(UInt(bwdata.W))

  readposreq := leftReg + ((rightReg - leftReg) >> 1)

  when(io.we_table) {
    mem.write(io.addr_table, io.data_table)
  }

  // default values
  io.out.valid := false.B
  io.out.bits.found := false.B
  io.out.bits.pos := 0.U
  io.out.bits.data := 0.U

  // receive a new request
  io.in.ready := !insearchReg && !io.we_table
  when(io.in.valid && io.in.ready) {
    keyReg := io.in.bits
    leftReg := 0.U
    rightReg := (n_entries-1).U
    midReg := (n_entries-1).U >> 1
    insearchReg := true.B
    outValidReg := false.B
    stepsReg := 0.U
    if(debugprint) {
      printf("new request: key=%d\n", io.in.bits)
    }
  }

  val gt1 = Module(new FPComparatorGT(expW, sigW))
  val gt2 = Module(new FPComparatorGT(expW, sigW))

  gt1.io.inA := rightReg
  gt1.io.inB := leftReg + 1.U
  gt2.io.inA := readdata
  gt2.io.inB := keyReg

  when(insearchReg) {
    midReg := readposreq

//    when(rightReg > leftReg + 1.U) {
//      when(readdata > keyReg) {
    when(gt1.io.out_greater) {
      when(gt2.io.out_greater) {
        rightReg := midReg
      }.otherwise {
        leftReg := midReg
      }
    }.otherwise { // the search completes with a lower-bound hit
      insearchReg := false.B
      foundReg := true.B
      posReg := leftReg
      outValidReg := true.B

      readposreq := leftReg // request a read for the search result

      leftReg := 0.U
      rightReg := (n_entries-1).U
      midReg := (n_entries-1).U >> 1
    }

    if (debugprint) {
      printf("steps=%d low=%d high=%d len=%d pos=%d data=%d\n",
        stepsReg, leftReg, rightReg, (rightReg - leftReg), readposreq, readdata)
    }
  }
  readdata := mem.read(readposreq, io.in.valid || insearchReg) // only request a read when needed

  io.out.valid := outValidReg
  when(io.out.ready && outValidReg) {
    io.out.bits.found := foundReg
    io.out.bits.pos := posReg
    io.out.bits.data := readdata
    outValidReg := false.B
    if (debugprint) {
      printf("out: found=%d pos=%d data=%d\n", foundReg, posReg, readdata)
    }
  }
}

object BinarySearchFPDriver extends App {
  GenVerilog.generate(new BinarySearchFP(expW=8, sigW=24))
}
