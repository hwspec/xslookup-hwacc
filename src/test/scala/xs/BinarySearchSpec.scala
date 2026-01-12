// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2025, UChicago Argonne, LLC.
// Main author: Kazutomo Yoshii <kazutomo@anl.gov>. See LICENSE in project root.

package xs

import chisel3.simulator.ChiselSim
import org.scalatest.flatspec.AnyFlatSpec
//import xs.BinarySearch
import xs.FPBigIntUtil.{BigInt2Float, Float2BigInt}

//import java.lang.{Float => javaFloat}
//import scala.util.Random

object BinarySearchRef {
//  def search(key: Int, data: Array[Int], debugprint: Boolean = false): Int = {
//    var left = 0
//    var right = data.length - 1
//
//    while ((right-left)>1) {
//      val mid = left + (right - left) / 2
//      if(debugprint) println(s"low=${left} high=${right} pos=${mid} data=${data(mid)}")
//      if (data(mid) > key) {
//        right = mid
//      } else {
//        left = mid
//      }
//    }
//    left
//  }

  def search[T: Ordering](key: T, data: Array[T], debugprint: Boolean = false): Int = {
    val ord = implicitly[Ordering[T]] // Get the implicit ordering instance for T
    var left = 0
    var right = data.length - 1

    while ((right - left) > 1) {
      val mid = left + (right - left) / 2
      if (debugprint) println(s"low=$left high=$right pos=$mid data=${data(mid)}")

      if (ord.gt(data(mid), key)) { // Compare using Ordering
        right = mid
      } else {
        left = mid
      }
    }
    left
  }

  def main(args: Array[String]) : Unit = {
    val n_entries = 225
    val table = Array.tabulate(n_entries) { i => i*2 + 1}
    val tablefp = table.map(x => x.toFloat)
    //val minval = table(0)
    val maxval = table(n_entries - 1)

    val key = maxval/2
    val pos = BinarySearchRef.search[Int](key, table)
    val data = table(pos)
    println(s"int: key=$key pos=$pos data=$data")

    val keyfp = (maxval/2).toFloat
    val posfp = BinarySearchRef.search[Float](keyfp, tablefp)
    val datafp = tablefp(pos)
    println(s"fp: key=$keyfp pos=$posfp data=$datafp")
  }
}

class BinarySearchSpec extends AnyFlatSpec with ChiselSim  {
  behavior of "BinarySearch"
  //
  val n_entries = 225
  val table : Array[Int] = Array.tabulate(n_entries) { i => i*2 + 1}
  val tablefp : Array[Float] = table.map(x => x.toFloat)
  val minval : Int = table(0)
  val maxval : Int = table(n_entries - 1)


  "BinarySearch: Int" should "pass" in {
    val debugprint: Boolean = false
    simulate(new BinarySearch(n_entries=n_entries, debugprint = debugprint))
      //.withAnnotations(Seq(WriteVcdAnnotation))
      { c =>
      def settable() : Unit = {
        c.io.we_table.poke(true)
        for(i <- 0 until n_entries) {
          c.io.addr_table.poke(i)
          c.io.data_table.poke(table(i))
          c.clock.step()
        }
        c.io.we_table.poke(false)
      }

      def runbinarysearch(v: Int) : Unit = {
        while (c.io.in.ready.peek().litValue == 0) {
          c.clock.step()
        }
        c.io.in.valid.poke(true)
        c.io.in.bits.poke(v)
        c.clock.step()

        c.io.in.valid.poke(false)

        c.io.out.ready.poke(true)
        while (c.io.out.valid.peek().litValue == 0) {
          c.clock.step()
        }

        val pos = c.io.out.bits.pos.peek().litValue.toInt
        val data = c.io.out.bits.data.peek().litValue.toInt
        val posref = BinarySearchRef.search(v, table, debugprint)
        if(debugprint)
          println(f"pos=$pos:$posref data=$data:${table(posref)}")
        assert(pos == posref, s"position no match: dut=$pos ref=$posref")
        assert(data == table(posref), s"data no match: dut=$data ref=${table(posref)}")
        c.clock.step()
        c.io.out.ready.poke(false)
      }

      settable()

      runbinarysearch(minval-1)
      runbinarysearch(maxval+1)

      for (offset <- -5 until 5)
        runbinarysearch(maxval/2 + offset)
      runbinarysearch(minval)
      runbinarysearch(maxval)
    }
  }


  "BinarySearch: FP" should "pass" in {
    val debugprint: Boolean = false
    simulate(new BinarySearchFP(n_entries=n_entries, debugprint = debugprint))
      // .withAnnotations(Seq(WriteVcdAnnotation))
      { c =>
      def settable() : Unit = {
        c.io.we_table.poke(true)
        for(i <- 0 until n_entries) {
          c.io.addr_table.poke(i)
          c.io.data_table.poke(Float2BigInt(table(i)))
          c.clock.step()
        }
        c.io.we_table.poke(false)
      }

      def runbinarysearch(v: Float) : Unit = {
        while (c.io.in.ready.peek().litValue == 0) {
          c.clock.step()
        }
        c.io.in.valid.poke(true)
        c.io.in.bits.poke(Float2BigInt(v))
        c.clock.step()

        c.io.in.valid.poke(false)

        c.io.out.ready.poke(true)
        while (c.io.out.valid.peek().litValue == 0) {
          c.clock.step()
        }
        val pos = c.io.out.bits.pos.peek().litValue.toInt
        val data = c.io.out.bits.data.peek().litValue.toInt
        val fpval = BigInt2Float(data)
        val posref = BinarySearchRef.search[Float](v, tablefp, debugprint)
        if(debugprint)
          println(f"pos=$pos:$posref data=$fpval:${tablefp(posref)}")
        assert(pos == posref, s"position no match: dut=$pos ref=$posref")
        assert(fpval == tablefp(posref), s"data no match: dut=$fpval ref=${tablefp(posref)}")
        c.clock.step()
        c.io.out.ready.poke(false)
      }

      settable()

      runbinarysearch(minval-1)
      runbinarysearch(maxval+1)

      for (offset <- -5 until 5)
        runbinarysearch(maxval/2 + offset)
      runbinarysearch(minval)
      runbinarysearch(maxval)
    }
  }
}
