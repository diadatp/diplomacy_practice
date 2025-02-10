# Diplomacy for the Uninitiated

## Overview

> **For the Software Archaeologists**: This project aims to spare you the pain of spelunking through abandoned GitHub issues, StackOverflow answers and cryptic build errors.

This project demonstrates how to integrate custom hardware blocks into a Rocket Core SoC using Chipyard's diplomacy-based infrastructure.

The project answers two fundamental questions:
 - How do we add a minimal custom hardware block to a Chipyard SoC?
 - What's the least-opaque path to a working system?

At its core, this project implements an Adder module - a custom Chisel3 peripheral that connects to a Rocket Core SoC via TileLink-based MMIO.

**Important Note on Repository Structure**: As noted by the Rocket-Chip maintainers in 2023:
> 
> "Unfortunately, the standalone rocket-chip build support has been deprecated and removed, due to lack of developer resources... To build and test SoCs with rocket-chip, users should seek out SoC frameworks."
> 
> [Source](https://github.com/chipsalliance/rocket-chip/issues/3483#issuecomment-1724857339)

This repository implements a minimal-but-complete example within those framework constraints.

## Project Structure

The `Adder.scala` file contains the Chisel3 implementation of a custom peripheral, including:
 - The TileLink slave interface definition
 - Register map for MMIO operations
 - The actual adder logic implementation
 - Integration hooks for the Chipyard framework


### Prerequisites

`$chipyard` is the root of [this](https://github.com/diadatp/chipyard) chipyard repository on the add_dpy branch. It contains a generator definition for this adder.

 - Set up the Chipyard repository according to its documentation
 - Set the `$chipyard` environment variable to your Chipyard repo's root
 - Sourced the environment setup: `source ./env.sh`

## Build & Simulation

### Compile Bare-Metal Test Program
```bash
cd $chipyard/tests
cmake -S ./ -B ./build/ -D CMAKE_BUILD_TYPE=Debug
cmake --build ./build/ --target adder
```

### Simulate with Rocket Core + Adder Peripheral
```bash
cd $chipyard/sims/verilator
make run-binary CONFIG=AdderRocketConfig BINARY=../../tests/adder.riscv
```

## Expected Output
```
[UART] UART0 is here (stdin/stdout).
Adder Tests
Exhaustive test of first 64 pairs:
Hardware res 0 is correct
Hardware res 1 is correct
Hardware res 2 is correct
 --- some lines omitted ---
Hardware res 12 is correct
Hardware res 13 is correct
Hardware res 14 is correct
32bit addition carry overflow test:
Hardware carry overflow is correct
Done.
```

