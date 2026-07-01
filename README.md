A Chisel-based cross-section lookup accelerator
=======================

This repository contains hardware building blocks (search, interpolation, etc) that accelerate a cross-section lookup kernel, a key computational kernel in the Monte Carlo neutron transport algorithm. These building blocks are implemented in the Chisel hardware construction language.

### Dependencies

#### JDK 8 or newer

We recommend LTS releases Java 8 and Java 11. You can install the JDK as recommended by your operating system, or use the prebuilt binaries from [AdoptOpenJDK](https://adoptopenjdk.net/).

#### SBT

SBT is the most common built tool in the Scala community. You can download it [here](https://www.scala-sbt.org/download.html).  

#### Checkout

```bash
git clone --recurse-submodules https://github.com/hwspec/xslookup-hwacc.git
```

#### Verilator

Verilator 5.044 is recommended.

To build it locally:

```bash
sh chisel-axi-utils/misc/build_verilator.sh INSTDIR
```

NOTE: add INSTDIR/bin to PATH

### To run tests

```bash
$ sbt test
```






Please contact Kazutomo Yoshii <kazutomo@anl.gov> if you have any question.
