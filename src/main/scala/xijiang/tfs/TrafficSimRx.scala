package xijiang.tfs

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import zhujiang.{HasZJParams, ZJParametersKey}

object TrafficSimRx {
  def connTfsRx(rxGen: TrafficSimRx, rx: DecoupledIO[UInt], nodeId: UInt, chn: UInt, clock: Clock, reset: Reset): Unit = {
    rxGen.io.rx <> rx
    rxGen.io.nodeId := nodeId
    rxGen.io.chn := chn
    rxGen.io.clock := clock
    rxGen.io.reset := reset
  }
}

class TrafficSimRx(implicit val p: Parameters) extends BlackBox with HasBlackBoxInline with HasZJParams {
  val io = IO(new Bundle {
    val rx = Flipped(Decoupled(UInt(maxFlitBits.W)))
    val nodeId = Input(UInt(niw.W))
    val chn = Input(UInt(8.W))
    val clock = Input(Clock())
    val reset = Input(Reset())
  })
  private val modName = s"${p(ZJParametersKey).modulePrefix}TrafficSimRx"
  override val desiredName = modName

  setInline(s"$modName.sv",
    s"""
       |module $modName (
       |  input \t\t\t\t\tclock,
       |  input \t\t\t\t\treset,
       |  input  [7:0] \t\tchn,
       |  input  [${niw - 1}:0] \t\tnodeId,
       |  input \t\t\t\t\trx_valid,
       |  output \t\t\t\t\trx_ready,
       |  input [${maxFlitBits - 1}:0] \trx_bits
       |);
       |  import "DPI-C" function void tfs_get_rx_ready(
       |    input shortint \t\tnode_id,
       |    input \t\t\t\t\tbyte chn,
       |    input \t\t\t\t\tvalid,
       |    output \t\t\t\t\tready,
       |    input \t\t\t\t\treset
       |  );
       |  wire [15:0] nid;
       |  assign nid = {${16 - niw}'h0, nodeId};
       |
       |  always @(posedge clock) begin
       |    tfs_get_rx_ready(nid, chn, rx_valid, rx_ready, reset);
       |  end
       |endmodule""".stripMargin)
}