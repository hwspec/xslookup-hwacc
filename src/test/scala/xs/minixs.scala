// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2025, UChicago Argonne, LLC.
// Main author: Kazutomo Yoshii <kazutomo@anl.gov>. See LICENSE in project root.

package xs

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashSet
import scala.collection.parallel.CollectionConverters._


// implement mini xsbench in Scala
object minixs {

  val EXPECTED_CHECKSUM = 28 // for the default problem size
  val NXS = 5 // the number of reaction channels stored for each energy level

  var seed : Long = 42 // is updated by LCG_random_float
  val n_particles = 10
  val n_nuclides = 6
  val n_gridpoints_per_nuclide = 225

  // 2D array (stored in 1D) with dimensions of size [n_nuclides, n_gridpoints_per_nuclide].
  val nuclide_energy_grids = ArrayBuffer.fill(n_nuclides * n_gridpoints_per_nuclide)(0.0f)

  // 3D array (stored in 1D) with dimensions of size [n_nuclides, n_gridpoints_per_nuclide, NXS].
  val nuclide_xs_data = ArrayBuffer.fill(n_nuclides * n_gridpoints_per_nuclide * NXS)(0.0f)

  // 1D array with length n_nuclides.
  val densities = ArrayBuffer.fill(n_nuclides)(0.0f)

  // 1D array of lightweight particle objects with length n_particles.
  class Particle {
    var energy: Float = 0.0f
    val macro_xs = ArrayBuffer.fill(NXS)(0.0f)
  }
  val particles = ArrayBuffer.fill(n_particles)(new Particle)

  def binarysearch[T : Ordering](table : Array[T], key: T, debugprint: Boolean = false):
  Int = {
    var left : Int = 0
    var right : Int = table.length - 1

    while ((right-left)>1) {
      val mid = left + (right - left) / 2
      if(debugprint) println(s"low=$left high=$right pos=$mid data=${table(mid)}")
      if (implicitly[Ordering[T]].gt(table(mid), key)) {
        right = mid
      } else {
        left = mid
      }
    }
    left
  }

  def LCG_random_float(): Float = {
    val m: Long = 4294967291L  // 2^32 - 5
    val a: Long = 1588635695L
    val c: Long = 12345L

    seed = ((a * seed + c) & 0xffffffffL) % m
    seed.toFloat / m.toFloat
  }

  def info(): Unit = {
    val kb_xs_total = (n_nuclides * n_gridpoints_per_nuclide
      + n_nuclides * n_gridpoints_per_nuclide * NXS
      + n_nuclides ) * 4
    val kb_particle_total = n_particles * (4 + 4 * NXS)

    println(s"seed: $seed")
    println(s"Particles (xs lookup work items): $n_particles")
    println(s"Number of nuclides in material: $n_nuclides")
    println(s"Number of energy gridpoints per material: $n_gridpoints_per_nuclide")
    println(s"Estimated XS data size: $kb_xs_total bytes")
    println(s"Estimated particle data size: $kb_particle_total bytes")
  }

  def init_grids() : Unit = {
    val nuclide_energy_grids_len = n_nuclides * n_gridpoints_per_nuclide

    //val nuclide_energy_grids = ArrayBuffer.fill(nuclide_energy_grids_len)(0.0f)

    println(s"Allocating nuclide energy array of size: ${nuclide_energy_grids_len*4}")

    val nuclide_xs_data_len = n_nuclides * n_gridpoints_per_nuclide * NXS

    //val nuclide_xs_data = ArrayBuffer.fill(nuclide_xs_data_len)(0.0f)

    println(s"Allocating nuclide XS data of size: ${nuclide_xs_data_len * 4}")

    //val densities = ArrayBuffer.fill(n_nuclides)(0.0f)

    println(s"Allocating nuclide densities data of size: ${n_nuclides * 4}")

    println("Initializing energy grids...")

    for(n <- 0 until n_nuclides) {

      val rndnumhash = scala.collection.mutable.HashSet[Float]()

      // fill nuclide_energy_grids with unique random numbers
      for(e <- 0 until n_gridpoints_per_nuclide) {
        var uniqrnd = false
        var rnd = 0.0f
        while(!uniqrnd) {
          rnd = LCG_random_float()
          if (!rndnumhash.contains(rnd)) uniqrnd = true
          rndnumhash += rnd
        }
        nuclide_energy_grids(n * n_gridpoints_per_nuclide + e) = rnd
      }

      // sort a segment
      val pos1 = n * n_gridpoints_per_nuclide
      val pos2 = pos1 + n_gridpoints_per_nuclide
      val sorted = nuclide_energy_grids.slice(pos1, pos2).sorted
      if (sorted.distinct.length != sorted.length) {
        println(s"Warning: duplicated values ${sorted.distinct.length}!= ${sorted.length}")
      }
      // copy back
      nuclide_energy_grids.patchInPlace(pos1, sorted, sorted.length)
      // for (i <- pos1 until pos2) nuclide_energy_grids(i) = sorted(i - pos1)
    }

    println("Initializing XS data...")
    for (i <- 0 until n_nuclides * n_gridpoints_per_nuclide * NXS) {
      nuclide_xs_data(i) = LCG_random_float()
    }

    println("Initializing densities...")
    for (n <- 0 until n_nuclides) {
      densities(n) = LCG_random_float()
    }
  }

  def init_particles() : Unit = {
    println("Initializing particles...")
    println(s"Allocating particle data of size: ${n_particles * 4}")

    for (p <- 0 until n_particles) {
      particles(p).energy = LCG_random_float()
      for(xs <- 0 until NXS) {
        particles(p).macro_xs(xs) = 0.0f
      }
    }
  }

  def main(args: Array[String]): Unit = {

    info()
    init_grids()
    init_particles()

    val ntries = 1000

    val ts = System.nanoTime()
    for (_ <- 0 until ntries) {
      (0 until n_particles).par.foreach { p =>
        val e = particles(p).energy
        for (n <- 0 until n_nuclides) {
          val pos1 = n * n_gridpoints_per_nuclide
          val pos2 = pos1 + n_gridpoints_per_nuclide
          val a = nuclide_energy_grids.slice(pos1,pos2).toArray
          val lower_index = binarysearch[Float](a, e)
          //println(s"${e} : ${a.head} ${a.last}")
          // println(lower_index)
          val e_lower = nuclide_energy_grids(n * n_gridpoints_per_nuclide + lower_index)
          val e_higher = nuclide_energy_grids(n * n_gridpoints_per_nuclide + lower_index + 1)
          val f = (e_higher - e) / (e_higher - e_lower)

          for(xs <- 0 until NXS) {
            val xs_lower  = nuclide_xs_data(n * n_gridpoints_per_nuclide * NXS +  lower_index    * NXS + xs)
            val xs_higher = nuclide_xs_data(n * n_gridpoints_per_nuclide * NXS + (lower_index+1) * NXS + xs)
            particles(p).macro_xs(xs) += densities(n) * (xs_higher - f * (xs_higher - xs_lower))
          }
        }
      }
    }
    val te = System.nanoTime()
    val et = (te - ts) * 1e-9
    println(s"elapsed time: $et sec")
    println(s"Loopups/sec ${n_particles / et}")

    var checksum = 0
    for (p <- 0 until n_particles) {
      var max = -1.0f
      var max_idx = 0
      for (xs <- 0 until NXS) {
        if (particles(p).macro_xs(xs) > max) {
          max = particles(p).macro_xs(xs)
          max_idx = xs
        }
      }
      checksum += max_idx
    }
    if (checksum != EXPECTED_CHECKSUM) {
      println(s"checksum mismatch: $checksum expected $EXPECTED_CHECKSUM")
    } else {
      println("pass!")
    }
  }
}
