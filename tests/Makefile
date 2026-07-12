#
# This is Makefile for cocotb
#

SIM ?= verilator
TOPLEVEL_LANG ?= verilog
TOPNAME=user_accel_bd_wrapper
HWMODULENAME=XSAXI
SRCDIR = $(abspath ../generated/$(HWMODULENAME))
VERILOG_SOURCES += $(SRCDIR)/user_accel_bd_wrapper.v $(SRCDIR)/*.sv
VERILOG_SOURCES += $(SRCDIR)/verification/*.sv

WAVES = 1
EXTRA_ARGS += --trace --trace-structs --x-assign unique --x-initial unique
EXTRA_ARGS += +define+RANDOMIZE_REG_INIT +define+RANDOMIZE_MEM_INIT +define+RANDOMIZE_GARBAGE_ASSIGN

test : $(SRCDIR)/$(HWMODULENAME).sv
	@echo $(VERILOG_SOURCES)
	@PARAMFN=$(SRCDIR)/XS_params.json $(MAKE) sim MODULE=tb_xs TOPLEVEL=$(TOPNAME)
	@echo -e "\n[Output]"
	@cat output.log

$(SRCDIR)/$(HWMODULENAME).sv gen:
	(cd ../ ; sbt "runMain xs.$(HWMODULENAME)")

include $(shell cocotb-config --makefiles)/Makefile.sim

clean::
	rm -f output.txt results.xml *.vcd fpga_simple.py
	rm -rf __pycache__
