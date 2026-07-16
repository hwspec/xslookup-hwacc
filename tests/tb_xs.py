import cocotb
from axi_test_bridge.cocotb_bridge import COCOTB_Bridge
import random

def binsearch(data, key):
    left = 0
    right = len(data) - 1

    while (right - left) > 1:
        mid = left + (right - left) // 2
        if data[mid] > key:
            right = mid
        else:
            left = mid

    return left

@cocotb.test()
async def sim_cmd(cocotb_dut):
    dut = COCOTB_Bridge(cocotb_dut)
    await dut.setup()

    await dut.expectWord(dut.p.const1_r, dut.p.const1)
    await dut.expectWord(dut.p.const2_r, dut.p.const2)
    await dut.softReset()
    await dut.expectWord(dut.p.inQCnt_r, 0)
    await dut.expectWord(dut.p.outQCnt_r, 0)

    async def testbinsearch(data, skeys):
        # fill CAM data
        for i, d in enumerate(data):
            await dut.writeWord(dut.p.fillCAM_w + i*4, d)

        # fill in inputQ
        for k in skeys:
            await dut.writeWord(dut.p.pushInQ_w, k)

        inqlen = await dut.readWord(dut.p.inQCnt_r)
        #dut.log.info(f"inqlen = {inqlen}")
        await dut.expectWord(dut.p.inQCnt_r, len(skeys))

        # feeding
        await dut.writeWord(dut.p.startFeed_w, 1)
        while True:
            tmplen = await dut.readWord(dut.p.inQCnt_r)
            if tmplen == 0:
                break

        # pop Q
        outqlen = await dut.readWord(dut.p.outQCnt_r)
        #dut.log.info(f"outqlen = {outqlen}")
        idx = 0
        while True:
            tmplen = await dut.readWord(dut.p.outQCnt_r)
            if tmplen == 0:
                break

            pos = await dut.readWord(dut.p.popOutQ_r)
            refpos = binsearch(data, skeys[idx])
            dut.log.info(f"pos={pos} refpos={refpos}")
            assert pos == refpos
            idx += 1

    # test data
    lowval = 1000
    delta = 10
    highval = lowval + dut.p.n_entries * delta

    data = [x for x in range(lowval, highval+1, delta)]
    nskeys = 20

    skeys = [x - 1 for x in range(lowval, highval+delta + 1, delta)]
    await testbinsearch(data, skeys)

    skeys = [random.randint(lowval, highval) for _ in range(nskeys)]
    await testbinsearch(data, skeys)

    dut.log.info("Done!!\n")
