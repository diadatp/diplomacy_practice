Diplomacy for the Uninitiated
=============================

`$chipyard` is the root of a set up chipyard repository. Remember to `source ./env.sh` before executing the following commands.

Building the bare-metal test program.

```bash
cd $chipyard/tests
cmake -S ./ -B ./build/ -D CMAKE_BUILD_TYPE=Debug
cmake --build ./build/ --target adder
```

Building a Rocket Core with the Adder Module as a Peripheral.

```bash
cd $chipyard/sims/verilator
make run-binary CONFIG=AdderRocketConfig BINARY=../../tests/adder.riscv
```

