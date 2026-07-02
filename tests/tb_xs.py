import cocotb
from axi_test_bridge.cocotb_bridge import COCOTB_Bridge

@cocotb.test()
async def sim_cmd(cocotb_dut):
    dut = COCOTB_Bridge(cocotb_dut)
    await dut.setup()
    
    await dut.expectWord(dut.p.const1_r, dut.p.const1)
    await dut.expectWord(dut.p.const2_r, dut.p.const2)

    dut.log.info("Done!!\n")
