// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2025, UChicago Argonne, LLC.
// Main author: Kazutomo Yoshii <kazutomo@anl.gov>. See LICENSE in project root.

package xs

import scala.math._
import rial.arith.{FusedMulAddFPGeneric, MultFPGeneric, RealGeneric, RealSpec, RoundSpec}
import rial.math.FuncKind._
import rial.math.{MathFuncConfig, MathFuncPipelineConfig, MathFunctions}
import rial.util.PipelineStageConfig
import rial.util.ScalaUtil._

object RialUtil {
  val fpspec = RealSpec.Float32Spec

  def fp2bigint(v: Float) : BigInt = {
    val x = new RealGeneric(fpspec, v)
    x.value.toBigInt
  }
  def bigint2fp(b: BigInt) : Float = {
    val sign = bit(fpspec.W-1, b)
    val expo = slice(fpspec.manW, fpspec.exW, b).toInt
    val mant = slice(0, fpspec.manW, b).toInt
    new RealGeneric(fpspec,  sign, expo, mant).toFloat
  }
}
