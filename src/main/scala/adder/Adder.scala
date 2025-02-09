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

/** Case class defining the parameters for the Adder module.
  *
  * @param numAdders
  *   Number of parallel adders in the module.
  * @param operandWidth
  *   Bit width of each operand.
  */
case class AdderParams(
    numAdders: Int,
    operandWidth: Int
)

/** IO Bundle for the Adder module, defining input and output ports.
  *
  * @param params
  *   Parameters specifying number of adders and operand width.
  */
class AdderIO(val params: AdderParams) extends Bundle {
  // A vector of operand pairs for each parallel adder.
  val operands = Input(
    Vec(params.numAdders, Vec(2, UInt(params.operandWidth.W)))
  )

  // Sum results, each one bit wider to accommodate carry.
  val sums = Output(Vec(params.numAdders, UInt((params.operandWidth + 1).W)))
}

/** Chisel Module implementing parallel adders.
  *
  * @param params
  *   Specifies the number of adders and operand width.
  */
class AdderModule(val params: AdderParams) extends Module {
  val io = IO(new AdderIO(params))

  // Perform addition in parallel for all adders with width extension.
  io.sums.zip(io.operands).foreach {
    case (sum, ops) => { sum := ops.reduce(_ +& _) }
  }
}

/** TileLink-compatible Adder module with register-mapped interface.
  *
  * @param params
  *   Specifies the number of adders and operand width.
  * @param address
  *   Base address for memory-mapped register access.
  * @param beatBytes
  *   Number of bytes per beat for TileLink communication.
  */
class AdderTileLink(
    params: AdderParams,
    address: AddressSet,
    beatBytes: Int
)(implicit p: Parameters)
    extends LazyModule {

  // Device descriptor for Linux Device Tree compatibility.
  // Probably irrelevant for bare-metal so the strings are stand-ins.
  val device = new SimpleDevice("adder", Seq("adder"))

  val node = TLRegisterNode(
    address = Seq(address),
    device = device,
    beatBytes = beatBytes
  )

  override lazy val module = new LazyModuleImp(this) {

    // Operand registers to store input values (numAdders x (A, B)).
    val operands = Seq.fill(params.numAdders)(
      (Reg(UInt((beatBytes * 8).W)), Reg(UInt((beatBytes * 8).W))) // (a, b)
    )

    // Instantiate core adder module.
    val adder = Module(new AdderModule(params))

    // Connect operand registers to the Adder module inputs.
    operands.zipWithIndex.foreach {
      case ((a, b), idx) => {
        adder.io.operands(idx) := VecInit(a, b)
      }
    }

    // Register fields to expose operands and results via TileLink.
    val regFields = operands.zip(adder.io.sums).zipWithIndex.flatMap {
      case (((a, b), sum), idx) =>
        Seq(
          // Operand A register field
          RegField(
            beatBytes * 8,
            a,
            RegFieldDesc(s"a$idx", s"Operand A$idx", reset = Some(0))
          ),
          // Operand B register field
          RegField(
            beatBytes * 8,
            b,
            RegFieldDesc(s"b$idx", s"Operand B$idx", reset = Some(0))
          ),
          // Sum register field (read-only)
          RegField.r(
            beatBytes * 8,
            sum,
            RegFieldDesc(s"sum$idx", s"Sum $idx", volatile = true)
          )
        )
    }

    // Map register fields to memory offsets three at a time.
    node.regmap(regFields.zipWithIndex.map { case (rf, i) =>
      (i * beatBytes, Seq(rf))
    }: _*)
  }
}

// Configuration key for enabling the Adder module.
case object AdderEnableKey extends Field[Option[AdderParams]](None)

/** Configuration mixin to enable the Adder module in a RocketConfig.
  *
  * @param numAdders
  *   Number of parallel adders.
  * @param operandWidth
  *   Bit width of operands.
  */
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

// Trait to conditionally instantiate an Adder module.
trait CanHavePeripheryAdder { this: BaseSubsystem =>

  // Retrieve Adder configuration parameters (if enabled).
  private val adderParamsOpt = p(AdderEnableKey)

  // Locate the Peripheral Bus (PBUS).
  private val pbus = locateTLBusWrapper(PBUS)

  // Debug print PBUS properties.
  println(
    s"PBUS has beatBytes: ${pbus.beatBytes} and blockBytes: ${pbus.blockBytes}"
  )

  // Instantiate and connect the Adder module if enabled.
  adderParamsOpt.foreach { params =>
    {
      println(
        s"Instantiating Adder with ${params.numAdders} adders of width ${params.operandWidth} bits"
      )

      // Create a synchronous domain for the Adder module.
      val domain = pbus.generateSynchronousDomain.suggestName("adder_domain")

      // Instantiate the TileLink-based Adder module.
      val adder = domain {
        LazyModule(
          new AdderTileLink(
            params = params,
            address = AddressSet(0x4000, 4096 - 1),
            beatBytes = pbus.beatBytes
          )
        )
      }

      // Connect the Adder module to the PBUS using a TLFragmenter
      // inside the previously created domain.
      pbus.coupleTo("adderWrite") {
        domain {
          adder.node := TLFragmenter(pbus.beatBytes, pbus.blockBytes)
        } := _
      }
    }
  }
}

// Object to generate SystemVerilog for testing.
object AdderModule extends App {
  ChiselStage.emitSystemVerilogFile(
    new AdderModule(new AdderParams(2, 32)),
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info")
  )
}
