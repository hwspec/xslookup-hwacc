A Chisel-based cross-section lookup accelerator
=======================

This repository contains hardware building blocks (search, interpolation, etc) that accelerate a cross-section lookup kernel, a key computational kernel in the Monte Carlo neutron transport algorithm. These building blocks are implemented in the Chisel hardware construction language.

### Dependencies

#### JDK 8 or newer

We recommend LTS releases Java 8 and Java 11. You can install the JDK as recommended by your operating system, or use the prebuilt binaries from [AdoptOpenJDK](https://adoptopenjdk.net/).

#### SBT

SBT is the most common built tool in the Scala community. You can download it [here](https://www.scala-sbt.org/download.html).  

#### Verilator

Verilator is required. Below is a local build instruction:

```bash
git clone https://github.com/verilator/verilator.git && cd verilator
git checkout tags/v5.010  -b v5.010build
autoconf
./configure --prefix=__INSTALLDIR__  # replace __INSTALLDIR__
make
make install
```

#### Checkout

```bash
git clone --recurse-submodules https://github.com/hwspec/xslookup-hwacc.git
```

### To run tests

```bash
$ sbt test
```

Note: parallel sbt test is disabled for now.

Please contact Kazutomo Yoshii <kazutomo@anl.gov> if you have any question.


