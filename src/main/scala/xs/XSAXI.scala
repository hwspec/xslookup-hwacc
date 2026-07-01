package xs

import axi._
import axi.AxiLiteResp._
import axi.AxiModuleParamsHelper._
import upickle.default._

import chisel3._
import chisel3.util._
import chisel3.ExtModule

case class XSModuleParams( // Note: do not put default value here
                           // params suffixed with _r, _w, or _rw represent addresses
                           // DefParams
                           soft_reset_rw : Long,
                           // module params
                           const1_r : Long, const2_r : Long,
                           inQCnt : Long,
                           outQCnt : Long,
                           startFeed : Long,
                           fillCAM_rw : Long,
                           pushInQ_w : Long,
                           popOutQ_r : Long,
                           // constant definition
                           const1: Long, const2: Long,
                           reset_cycles : Int, // soft reset cycles
                           // design params
                           n_entries : Int,
                           q_len : Int, // for InQ and OutQ
                         ) extends AxiModuleParams with AxiModuleDefParams
{
  val moduleName = "XS"
}

object XSModuleParams {
  implicit val rw: ReadWriter[XSModuleParams] = macroRW

  def default(const1: Long, const2: Long) : XSModuleParams =
    new XSModuleParams(
      soft_reset_rw = 0x0, const1_r = 0x10, const2_r = 0x14,
      inQCnt = 0x20, outQCnt = 0x24,
      startFeed = 0x30,
      fillCAM_rw = 0x1000,
      pushInQ_w = 0x2000,
      popOutQ_r = 0x3000,
      const1 = const1, const2 = const2, reset_cycles = 8,
      n_entries = 225,
      q_len = 64,
    )
}


class XSAXI(p : XSModuleParams, debugprint: Boolean = false)
  extends chisel3.Module with axi.HasAxiLite32IO {

  val bw: Int = 32

  override val S = IO(new AxiLite32IO())

  // cycle counter for convenience
  val (cycles, wrap) = Counter(true.B, 1 << 16)

  // soft reset handling logic for dut
  val softResetReg = RegInit(false.B)
  val softResetDoneReg = RegInit(false.B)
  val resetCounterReg = RegInit(0.U(log2Ceil(p.reset_cycles).W))
  when(resetCounterReg > 0.U) {
    resetCounterReg := resetCounterReg - 1.U
  }.otherwise {
    softResetReg := false.B
    softResetDoneReg := true.B
  }
  val combinedReset = (softResetReg || reset.asBool)

  val dut = withReset(combinedReset) {
    Module(new CAMSearch(
      n_entries = p.n_entries,
      debugprint = debugprint
    ))
  }
  dut.io.in.bits := 0.U
  dut.io.in.valid := false.B
  dut.io.out.ready := false.B
  dut.io.updateCam := false.B
  dut.io.camaddr := 0.U
  dut.io.camdata := 0.U

  // test vector
  val inQ = Module(new Queue(UInt(bw.W), p.q_len))
  val outQ = Module(new Queue(new CAMRes(), p.q_len))
  inQ.io.enq.bits := 0.U
  inQ.io.enq.valid := false.B
  inQ.io.deq.ready := false.B
  outQ.io.enq.bits := WireDefault(0.U.asTypeOf(new CAMRes()))
  outQ.io.enq.valid := false.B
  outQ.io.deq.ready := false.B

  val inEnableReg = RegInit(false.B)

  dut.io.in.valid := inQ.io.deq.valid && inEnableReg
  dut.io.in.bits := inQ.io.deq.bits
  inQ.io.deq.ready := dut.io.in.ready && inEnableReg

  outQ.io.enq <> dut.io.out

  // -----------------------------
  // AXI-lite regs
  // -----------------------------
  val awHoldValidReg = RegInit(false.B)
  val awHoldAddrReg = Reg(UInt(bw.W))
  val wHoldValidReg = RegInit(false.B)
  val wHoldDataReg = Reg(UInt(32.W))
  val wHoldStrbReg = Reg(UInt(4.W))

  val bvalidReg = RegInit(false.B)
  val brespReg = RegInit(0.U(2.W))

  S.AXI.awready := !awHoldValidReg && !bvalidReg
  S.AXI.wready := !wHoldValidReg && !bvalidReg
  val awFire = S.AXI.awvalid && S.AXI.awready
  val wFire = S.AXI.wvalid && S.AXI.wready
  when(awFire) {
    awHoldValidReg := true.B;
    awHoldAddrReg := S.AXI.awaddr(19, 0) // in case MMIO range is 1MB
  }
  when(wFire) {
    wHoldValidReg := true.B
    wHoldDataReg := S.AXI.wdata
    wHoldStrbReg := S.AXI.wstrb
  }

  val doWrite = awHoldValidReg && wHoldValidReg && !bvalidReg
  val addrHoldReg = RegInit(0.U(bw.W))

  when(doWrite) {
    val a = awHoldAddrReg
    val fullWrite = (wHoldStrbReg === "b1111".U)

    val bresp = WireDefault(OKAY.U)

    when(!fullWrite) { // support full write only for this example
      bresp := SLVERR.U
    }.elsewhen(a === p.soft_reset_rw.U) {
      softResetReg := true.B
      resetCounterReg := p.reset_cycles.U
      softResetDoneReg := false.B
      bresp := OKAY.U
    }.elsewhen(a >= p.fillCAM_rw.U && a < (p.fillCAM_rw + p.n_entries*4).U) {
      val offset = a - p.fillCAM_rw.U
      dut.io.updateCam := true.B
      dut.io.camaddr := offset
      dut.io.camdata := wHoldDataReg
      if (debugprint) printf("%d: updateCAM %x at %x\n", cycles, offset, wHoldDataReg)
    }.otherwise {
      brespReg := AxiLiteResp.SLVERR.U
    }
    brespReg := bresp
    bvalidReg := true.B
    awHoldValidReg := false.B
    wHoldValidReg := false.B
  }

  when(bvalidReg && S.AXI.bready) {
    bvalidReg := false.B
  }
  S.AXI.bvalid := bvalidReg
  S.AXI.bresp := brespReg

  // -----------------------------
  // Read path: AR -> R
  // -----------------------------
  val rdataReg = Reg(UInt(32.W))
  val rrespReg = RegInit(0.U(2.W))

  object RState extends ChiselEnum {
    val READY2READ, COMPLETED = Value
  }

  val rstateReg = RegInit(RState.READY2READ)

  S.AXI.arready := rstateReg === RState.READY2READ
  S.AXI.rvalid := rstateReg === RState.COMPLETED
  S.AXI.rdata := rdataReg
  S.AXI.rresp := rrespReg

  val arFire = S.AXI.arvalid && S.AXI.arready

  when(arFire) {
    if (debugprint) printf("%d: arFire: %x\n", cycles, S.AXI.araddr)
    val araddr = S.AXI.araddr(19, 0) // 1MB range
    rrespReg := OKAY.U

    val rstate = WireDefault(RState.READY2READ)

    when(araddr === p.const1_r.U) {
      rdataReg := p.const1.U
      rstate := RState.COMPLETED
    }.elsewhen(araddr === p.const2_r.U) {
      rdataReg := p.const2.U
      rstate := RState.COMPLETED
    }.elsewhen(araddr === p.soft_reset_rw.U) {
      rdataReg := softResetDoneReg
      rstate := RState.COMPLETED
    }.otherwise {
      if (debugprint) printf("%d: bad read req %d\n", cycles, araddr)
      // rrespReg := SLVERR.U // with this, the host can only read 0xffffffff for any addresses on AVED
      rdataReg := 0xbad00000L.U | S.AXI.araddr(31, 0)
      if (debugprint) printf(cf"arFire otherwise: addr=${S.AXI.araddr}%16x\n")
      rstate := RState.COMPLETED
    }
    rstateReg := rstate
  }

  when(rstateReg === RState.COMPLETED && S.AXI.rready) {
    rstateReg := RState.READY2READ
  }
}

object XSAXI extends App {
  val const1 : Long = 0x58534c55L // module id  "XSLU"
  val const2 : Long = getGitHash

  val p = checkParamEnv(
    XSModuleParams.default(const1 = const1, const2 = const2),
    "XS_MODULE_PARAMS")

  EmitVerilog.generate(new XSAXI(p, debugprint=true), p)
}
