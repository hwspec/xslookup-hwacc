import cocotb
from axi_test_bridge.cocotb_bridge import COCOTB_Bridge

@cocotb.test()
async def sim_cmd(cocotb_dut):
    dut = COCOTB_Bridge(cocotb_dut)
    await dut.setup()

    await dut.expectWord(dut.p.const1_r, dut.p.const1)
    await dut.expectWord(dut.p.const2_r, dut.p.const2)
    await dut.softReset()
    await dut.expectWord(dut.p.inQCnt_r, 0)
    await dut.expectWord(dut.p.outQCnt_r, 0)

    # fill CAM data
    for i in range(dut.p.n_entries):
        await dut.writeWord(dut.p.fillCAM_w + i*4, i + 100)

    # fill in inputQ
    for i in range(dut.p.n_entries):
        await dut.writeWord(dut.p.pushInQ_w, i + 100)

    inqlen = await dut.readWord(dut.p.inQCnt_r)
    dut.log.info(f"inqlen = {inqlen}")
    await dut.expectWord(dut.p.inQCnt_r, dut.p.n_entries)

    # feeding
    await dut.writeWord(dut.p.startFeed_w, 1)
    while True:
        tmplen = await dut.readWord(dut.p.inQCnt_r)
        if tmplen == 0:
            break

    # pop Q
    outqlen = await dut.readWord(dut.p.outQCnt_r)
    dut.log.info(f"outqlen = {outqlen}")
    while True:
        tmplen = await dut.readWord(dut.p.outQCnt_r)
        if tmplen == 0:
            break

        pos = await dut.readWord(dut.p.popOutQ_r)
        dut.log.info(f"pos={pos}")

    dut.log.info("Done!!\n")
