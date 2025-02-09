Diplomacy for the Uninitiated
=============================

```console
$ cd $chipyard/tests
$ cmake -S ./ -B ./build/ -D CMAKE_BUILD_TYPE=Debug
$ cmake --build ./build/ --target adder
```

```console
$ cd $chipyard/sims/verilator
$ make run-binary CONFIG=AdderRocketConfig BINARY=../../tests/adder.riscv
```

