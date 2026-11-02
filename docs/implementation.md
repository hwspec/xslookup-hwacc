# xslookup-hwacc: implementation notes

Chisel/RTL hardware building blocks (search, interpolation) that accelerate
the cross-section (XS) lookup kernel from Monte Carlo neutron transport codes
(XSBench). This is the successor/active repo to an earlier exploration
(`xsbench-chisel`); the core kernel modules are carried over largely
unchanged.

The kernel being modeled, per particle energy `e` against a nuclide's sorted
energy grid, per reaction channel `xs`:

```
lower_index = binary_search(energy_grid, e)        # find lower bound
f = (e_higher - e) / (e_higher - e_lower)            # interpolation factor
macro_xs[xs] += density * (xs_higher - f * (xs_higher - xs_lower))
```

Two resource-usage comparisons this repo is built around:
1. **Energy-grid lookup**: binary search vs. CAM-style parallel search.
2. **Interpolation**: division vs. Newton-Raphson reciprocal estimate.

Related publication: https://www.sciencedirect.com/science/article/pii/S0167819125000444

## Repo layout

- `src/main/scala/xs/` — the kernel hardware modules (see below).
- `src/main/scala/common/GenVerilog.scala` — Verilog emission helper used by
  the kernel modules' standalone drivers.
- `src/test/scala/xs/` — ChiselSim/scalatest specs for the kernel modules,
  plus FP bit-twiddling test utilities.
- `rial-tmpfix/` (git submodule, https://github.com/hwspec/rial-tmpfix.git) —
  generic real-number arithmetic library (adders/multipliers, polynomial
  reciprocal/etc. "math functions"), used by `Interpolation`. Pulled in as an
  `unmanagedSourceDirectory` in `build.sbt`, not a published binary dependency.
- `berkeley-hardfloat/` (git submodule, ucb-bar/berkeley-hardfloat) — IEEE754
  recoded-format FP primitives (`AddRecFN`, `DivSqrtRecFN_small`), used by
  `InterpolationWithHardFloat`. Same unmanaged-source setup.

## Build setup

- `build.sbt`: Scala 2.13.18, Chisel `7.13.0`, `project/build.properties` sbt.
- No `chiseltest` dependency; tests use the `chisel3.simulator.ChiselSim`
  trait + `scalatest` `AnyFlatSpec`.
- `rial-tmpfix` and `berkeley-hardfloat` sources are compiled directly as part
  of this project's `Compile` scope rather than resolved as artifacts.

## Kernel hardware modules (`src/main/scala/xs/`)

### `FPComparator.scala`
IEEE754-shaped single-precision comparators, parameterized by `expW`/`sigW`
(FP32 widths 8/23 passed explicitly at call sites). Operate on raw packed
`UInt` bit patterns (sign in MSB, no hardfloat recoding).

- `UnpackFP` — splits a packed FP bit pattern into sign/exp/mantissa.
- `FPComparator` — `out_equal`/`out_greater`, built from two `UnpackFP`
  submodules.
- `FPComparatorLTV0` — first less-than attempt, also via `UnpackFP` submodules.
- `FPComparatorLT` / `FPComparatorGT` — the versions actually used elsewhere;
  sign/exp/mantissa are sliced directly from the input bits (no submodules),
  cheaper to instantiate many of.
- `FPComparatorLTNoSign` — like `FPComparatorLT` but skips sign handling
  entirely (valid only when both operands are non-negative, e.g. XS data);
  used by `CAMSearchFPNoSign` to shave gates.

### `BinarySearch.scala`
- `BinarySearch(n_entries=225, expW=8, sigW=23)` — `Decoupled` in/out FSM.
  Table is a `SyncReadMem(n_entries, UInt)` loaded externally via
  `we_table`/`addr_table`/`data_table`. On a new request it initializes
  `left=0, right=n_entries-1`, repeatedly computes `mid = left + (right-left)>>1`,
  reads `mem(mid)`, and narrows the interval using plain `UInt` `>` comparison
  until `right <= left+1`, at which point `pos=left` is the lower-bound result
  (`found`, `pos`, `data` returned via `BSRes`). One comparison step per clock
  (~`log2(n_entries)` cycles).
- `BinarySearchFP` — structurally identical FSM, but the `rightReg > leftReg+1`
  and `readdata > keyReg` comparisons are replaced with two `FPComparatorGT`
  instances, so the search operates on packed FP32 bit patterns instead of
  raw integers.
- Both have `App` drivers (`BinarySearchDriver`, `BinarySearchFPDriver`) that
  call `GenVerilog.generate`.

### `CAMSearch.scala`
Fully combinational / single-cycle alternative to binary search: the whole
table is compared in parallel every cycle, no FSM.

- `CAMSearch` — `camReg: Vec[UInt]` (225 entries), with separate `writeCam`
  and `readCam` control signals (`camaddr`/`camWData` write, `camaddr`/
  `camRData` read) so the table can be both loaded and read back.
  `matchVec = camReg.map(_ > in.bits)` (integer `<` against the key) combined
  with `PriorityEncoder` to find the first entry greater than the key, then
  `idx` is adjusted at the boundaries (below first entry -> 0, above last
  entry -> `n_entries-2`, otherwise `matchIndex-1`).
- `CAMSearchFP` — same idea, but instantiates one `FPComparatorLT` *per table
  entry* (225 separate comparator modules) instead of a bare `<`; also has
  `writeCam`/`readCam`.
- `CAMSearchFPNoSign` — identical to `CAMSearchFP` but uses
  `FPComparatorLTNoSign` (cheaper per-entry comparator since XS energy values
  are always non-negative); still uses the older single `updateCam`/`camdata`
  write-only interface.
- This is the "CAM-based search" side of comparison #1: single-cycle latency
  at the cost of one comparator per table row, vs. binary search's small
  footprint but ~`log2(n_entries)` sequential cycles.

### `Interpolation.scala`
Three variants of `y = (h - e) / (h - l)` (the interpolation factor `f` in the
kernel formula above):

- `Interpolation` — uses the **rial** library: two `AddFPGeneric` subtractors
  (`h - e`, `h - l`, via XOR-flipping the sign bit of `e`/`l` and adding),
  `rial.math.MathFunctions` configured with `fncfg = MathFuncConfig(Seq(Reciprocal))`
  (2nd-order polynomial reciprocal estimate, 8-bit address width, 3 extra
  bits) applied to `h - l`, and a final `MultFPGeneric` multiply. Fixed
  3-cycle latency, 1 input/cycle throughput — a straight-through pipeline
  using plain `RegNext` stages, no `Decoupled`/FSM. This is the "reciprocal
  estimate" side of comparison #2.
- `InterpolationWithHardFloat` — same formula using Berkeley hardfloat:
  `AddRecFN` x2 for the subtractions (recoded-format, via `recFNFromFN`) and
  `DivSqrtRecFN_small` for the actual division. Explicit FSM
  (`sIdle -> sSub2Div -> sDiv -> sDone`) with `Decoupled` in/out, polls
  `divm.io.outValid_div`, with a 30-cycle timeout counter as a fallback exit
  from `sDiv`. Variable latency depending on the divider. This is the "real
  division" side of comparison #2.
- `InterpolationWithCFG` — empty placeholder module (`io.in.ready := true`,
  `io.out.valid := true`, no actual computation); not implemented.
- `IEEEBits` — small helper mapping total bit-width (16/32/64/128) to
  (exponent, mantissa) widths for hardfloat's recoded-format calls.

### `SRAMtest.scala`
Standalone `SyncReadMem` microbenchmark (default 225 entries, self-initializing
with `i -> i`), used purely to get a yosys baseline gate/LUT count for the
memory primitive itself, independent of the search logic.

### `common/GenVerilog.scala`
Thin wrapper around `circt.stage.ChiselStage.emitSystemVerilogFile`. The
kernel modules' `object X extends App { GenVerilog.generate(new X(...)) }`
drivers, when run, write SystemVerilog to `generated/<ModuleName>.sv` with
`--disable-all-randomization --strip-debug-info
--lowering-options=disallowLocalVariables,disallowPackedArrays`.

## Tests (`src/test/scala/xs/`)

- `BinarySearchSpec` — loads a 225-entry odd-number table, runs both the Int
  (`BinarySearch`) and FP (`BinarySearchFP`) variants against a Scala
  reference `BinarySearchRef.search` (generic over `Ordering`), checking
  below-range, above-range, near-middle, and exact min/max keys. Passing.
- `CAMSearchSpec` — same reference; exercises `CAMSearch` and `CAMSearchFP`,
  including a write-then-read-back check of the CAM contents via
  `writeCam`/`readCam`, at table-boundary and interior keys. Passing.
- `FPComparatorSpec` / `FPComparatorSimSpec` — randomized cross-checks of the
  FP comparators against Scala float comparisons.
- `InterpolationSpec` — feeds fixed + randomized `(h, e, l)` triples through
  both `Interpolation` (checked against a 3-cycle-latency Scala float
  reference `(h-e)/(h-l)`) and `InterpolationWithHardFloat` (currently only
  drives one fixed input through and prints, doesn't assert equality).
- `SRAMtestSpec` — basic read/write sanity check for `SRAMtest`.
- `FPBigIntUtil.scala` — `Float2BigInt`/`BigInt2Float`/`UnpackFloat`/`PackFloat`
  helpers used throughout the specs to poke/peek packed FP32 bit patterns.
- `RialUtil.scala` — small helper used by `InterpolationSpec` for the rial
  variant (`fp2bigint`/`bigint2fp`).

Run with `sbt test` (per `README.md`); CI (`.github/workflows/test.yml`) does
the same on Ubuntu with Verilator.

## Known state of the kernel modules

- `BinarySearch`/`BinarySearchFP`, `CAMSearch`/`CAMSearchFP`, and both
  `Interpolation` variants are implemented and exercised by passing tests
  (module-level, not yet composed into an end-to-end pipeline).
- `CAMSearchFPNoSign` still uses the older single-`updateCam` write-only
  interface (not yet updated to the `writeCam`/`readCam` split used by
  `CAMSearch`/`CAMSearchFP`).
- `InterpolationWithCFG` is an unimplemented stub.
- `InterpolationWithHardFloat`'s test only drives one input through and
  prints the result; it doesn't assert correctness the way the rial-based
  `Interpolation` test does.
- None of the kernel modules are composed into a single top-level pipeline
  matching the full kernel formula above — each is still exercised and
  evaluated (functionally and for resource usage) in isolation.
