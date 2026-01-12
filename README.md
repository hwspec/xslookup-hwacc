A Chisel version of mini XSBench
=======================

TBD

## Make your own Chisel3 project

### Dependencies

#### JDK 8 or newer

We recommend LTS releases Java 8 and Java 11. You can install the JDK as recommended by your operating system, or use the prebuilt binaries from [AdoptOpenJDK](https://adoptopenjdk.net/).

#### SBT

SBT is the most common built tool in the Scala community. You can download it [here](https://www.scala-sbt.org/download.html).  

#### Note:

With a small problem:

n_nuclides=6                 # Number of nuclides in material
n_gridpoints_per_nuclide=225 # Number of energy gridpoints per material
NXS=5                        # The number of reaction channels stored for each energy level

nuclide_energy_grids is 5400 bytes (6*255*4)  # binary search (read-only)
nuclide_xs_data is 27000 bytes  # xs_lower, xs_higher (read-only)
densities is 24 bytes # read-only

particles[p].macro_xs[xs] is 24 Mbytes for 1M particles # update sequentially  n_particles * (1+NXS) * sizeof(float)

typedef struct{
  float energy;
  float macro_xs[NXS];
} Particle;


Requiread floating point operations: comparison, addition,
subtraction, multiplication, division (all in single precision)
