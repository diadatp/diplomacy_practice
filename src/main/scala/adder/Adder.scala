// SPDX-License-Identifier: MIT

package adder

import chisel3._
import chisel3.util._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem.{BaseSubsystem, PBUS}
import freechips.rocketchip.tilelink._
import org.chipsalliance.cde.config.{Parameters, Field, Config}
import org.chipsalliance.diplomacy.lazymodule.{LazyModule, LazyModuleImp}

// _root_ disambiguates from package chisel3.util.circt if user imports chisel3.util._
import _root_.circt.stage.ChiselStage

// parameters that fully specify the module
case class AdderParams(
    numAdders: Int, // number of parallel adders
    operandWidth: Int // bit width of input operands
)

// Separate IO Bundle definition from Module as it will be reused often
class AdderIO(val params: AdderParams) extends Bundle {
  val operands = Input(
    Vec(params.numAdders, Vec(2, UInt(params.operandWidth.W)))
  )
  // Wider by one for the carry
  val sums = Output(Vec(params.numAdders, UInt((params.operandWidth + 1).W)))
}

// core adder logic as plain Chisel Module
class AdderModule(val params: AdderParams) extends Module {
  val io = IO(new AdderIO(params))
  io.sums.zip(io.operands) foreach {
    case (sum, ops) => { sum := ops.reduce(_ +& _) }
  }
}

class AdderTileLink(
    params: AdderParams,
    address: AddressSet,
    beatBytes: Int
)(implicit p: Parameters)
    extends LazyModule {

  // For linux device tree
  val device = new SimpleDevice("adder", Seq("adder"))

  val node = TLRegisterNode(
    address = Seq(address),
    device = device,
    beatBytes = beatBytes
  )

  override lazy val module = new LazyModuleImp(this) {

    // Create operand registers (numAdders x a/b)
    val operands = Seq.fill(params.numAdders)(
      (Reg(UInt((beatBytes * 8).W)), Reg(UInt((beatBytes * 8).W))) // (a, b)
    )

    // Instantiate core adder from external repo
    val adder = Module(new AdderModule(params))

    // Wire registers to core adder
    operands.zipWithIndex.foreach {
      case ((a, b), idx) => {
        adder.io.operands(idx) := VecInit(a, b)
      }
    }

    // Generate regfields for each adder
    val regFields = operands.zip(adder.io.sums).zipWithIndex.flatMap {
      case (((a, b), sum), idx) =>
        Seq(
          RegField(
            beatBytes * 8,
            a, // Operand A
            RegFieldDesc(s"a$idx", s"Operand A$idx", reset = Some(0))
          ),
          RegField(
            beatBytes * 8,
            b, // Operand B
            RegFieldDesc(s"b$idx", s"Operand B$idx", reset = Some(0))
          ),
          RegField.r(
            beatBytes * 8,
            sum, // Sum (read-only)
            RegFieldDesc(s"sum$idx", s"Sum $idx", volatile = true)
          )
        )
    }

    // Map registers to address offsets
    node.regmap(regFields.zipWithIndex.map { case (rf, i) =>
      (i * beatBytes, Seq(rf))
    }: _*)
  }
}

case object AdderEnableKey extends Field[Option[AdderParams]](None)

// defaults are here
class WithAdder(
    numAdders: Int = 2,
    operandWidth: Int = 32
) extends Config((site, here, up) => { case AdderEnableKey =>
      Some(
        AdderParams(
          numAdders = numAdders,
          operandWidth = operandWidth
        )
      )
    })

trait CanHavePeripheryAdder { this: BaseSubsystem =>

  // Check if Adder is enabled via the config
  private val adderParamsOpt = p(AdderEnableKey)

  private val pbus = locateTLBusWrapper(PBUS)

  println(
    s"PBUS has beatBytes: ${pbus.beatBytes} and blockBytes: ${pbus.blockBytes}"
  )

  // If enabled, instantiate the Adder and connect it to the PBUS
  adderParamsOpt.foreach { params =>
    {
      println(
        s"Instantiating Adder with ${params.numAdders} adders of width ${params.operandWidth} bits"
      )

      val domain = pbus.generateSynchronousDomain.suggestName("adder_domain")

      val adder = domain {
        LazyModule(
          new AdderTileLink(
            params = params,
            address = AddressSet(0x4000, 4096 - 1),
            beatBytes = pbus.beatBytes
          )
        )
      }

      pbus.coupleTo("adderWrite") {
        domain {
          adder.node := TLFragmenter(pbus.beatBytes, pbus.blockBytes)
        } := _
      }

    }
  }
}

// Generate verilog for quick sanity check
object AdderModule extends App {
  ChiselStage.emitSystemVerilogFile(
    new AdderModule(new AdderParams(2, 32)),
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info")
  )
}
