Diplomacy for the Uninitiated
=============================

`$chipyard` is the root of this [https://github.com/diadatp/chipyard/tree/add_dpy] chipyard repository. It contains a generator definition for this adder. Remember to set it up and `source ./env.sh` before executing the following commands.

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

This will be the output you see.

```
[UART] UART0 is here (stdin/stdout).
Adder Tests
Exhaustive test of first 64 pairs:
Hardware res 0 is correct
Hardware res 1 is correct
Hardware res 2 is correct
 --- some line ommited ---
Hardware res 12 is correct
Hardware res 13 is correct
Hardware res 14 is correct
32bit addition carry overflow test:
Hardware carry overflow is correct
Done.
```

Documented here is my attempt at understanding Berkeley Architecture Research Group's Chipyard framework for generating SoCs.

My hope is that this simple example will pull together the whole picture and save you from having to trudge through dozens of source files, StackOverflow answers and Github issues in the unenviable task of software archaeology. There is an associated repo [https://github.com/diadatp/chipyard/tree/add_dpy] that implements the chipyard glue to make it all work.

https://github.com/chipsalliance/rocket-chip/issues/3483#issuecomment-1724857339

> Unfortunately, the standalone rocket-chip build support has been deprecated and removed, due to lack of developer resources to maintain that feature. To build and test SoCs with rocket-chip, users should seek out SoC frameworks.
> 
> Two options include :
> 
>  - https://github.com/ucb-bar/chipyard, which provides a comprehensive mono-repo for hardware design
>  - https://github.com/chipsalliance/playground, which uses mill+nix for a lighter-weight, and more modular development environment
