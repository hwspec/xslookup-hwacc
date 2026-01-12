package xs

import chisel3._
import chisel3.util._
import common.GenVerilog

class ReciprocalEstimate(expoW: Int = 8, mantW: Int = 23,
                         lutW : Int = 6, fixedW: Int = 16 // the bit widths of lut and internal fixed-point
                        ) extends Module {
  val bw = 1 + expoW + mantW

  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(UInt(bw.W)))
    val out = Decoupled(UInt(bw.W))
  })

  io.in.ready := true.B // accept always for now

  // compute the lut value
  val scaling = 1 << fixedW
  val lutsize =  (1<<lutW)
  val recseq = Seq.tabulate(1<< lutW) {i =>
    val mval = 1.0 + i.toDouble / lutsize
    val recval = 1.0 / mval
    val iv = (recval * scaling).toInt & (scaling - 1) // double check
    iv.U(fixedW.W)
  }

  val recLUT = VecInit(recseq)

  val fpsign = io.in.bits(bw-1)
  val fpexpo = io.in.bits(bw-2, mantW)
  val fpmant = io.in.bits(mantW-1, 0)

  val mantidx = fpmant(mantW-1, mantW-lutW)
  val mantrec = recLUT(mantidx)

  val bias = (1 << (expoW - 1)) - 1
  val expoupdated = (2 * bias).U - fpexpo


  // Newton-Raphson method to find f(x) = 1/x + a = 0
  // y_(n+1) = y_n * (2 - x y_n)

  val x  = Cat(1.U(1.W), fpmant)
  val y0 = Cat(1.U(1.W), mantrec(fixedW-1, 1))

  val tempW = mantW + 4

  val xy0  = Wire(UInt(tempW.W))
  val xy1  = Wire(UInt(tempW.W))
  val two0 = Wire(UInt(tempW.W))
  val two1 = Wire(UInt(tempW.W))
  val y1   = Wire(UInt(tempW.W))
  val y2   = Wire(UInt(tempW.W))

  // the first iteration
  xy0  := (x * y0) >> mantW
  two0 := 2.U((mantW+1).W) - xy0
  y1   := (y0 * two0) >> mantW

  // the second iteration
  xy1  := (x * y1) >> mantW
  two1 := 2.U((mantW+1).W) - xy1
  y2   := (y1 * two1) >> mantW

  io.out.bits := Cat(fpsign, expoupdated, y2(mantW - 1, 0))
  io.out.valid := true.B
}

object ReciprocalEstimate extends App {
  GenVerilog.generate(new ReciprocalEstimate())
}
