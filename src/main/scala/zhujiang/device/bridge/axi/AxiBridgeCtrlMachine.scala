package zhujiang.device.bridge.axi

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import xijiang.Node
import zhujiang.axi.{ARFlit, AWFlit, AxiParams, BFlit, WFlit}
import zhujiang.chi.DatOpcode
import zhujiang.device.bridge.{BaseCtrlMachine, IcnIoDevCtrlOpVecCommon}

class AxiBridgeCtrlMachine(
  icnNode: Node,
  axiParams:AxiParams,
  outstanding:Int,
  compareTag: (UInt, UInt) => Bool
)(implicit p: Parameters)
  extends BaseCtrlMachine(
    genOpVec = new AxiBridgeCtrlOpVec,
    genInfo = new AxiCtrlInfo,
    genRsEntry = new AxiRsEntry,
    node = icnNode,
    outstanding = outstanding,
    ioDataBits = 0,
    compareTag = compareTag
  ){
  val axi = IO(new Bundle {
    val aw = Decoupled(new AWFlit(axiParams))
    val ar = Decoupled(new ARFlit(axiParams))
    val w = Decoupled(new WFlit(axiParams))
    val b = Flipped(Decoupled(new BFlit(axiParams)))
  })
  val dataBufferAlloc = IO(Decoupled(new AxiDataBufferAllocReq(outstanding)))

  dataBufferAlloc.valid := valid && !payload.state.bufferAllocated
  dataBufferAlloc.bits.idx := io.idx
  dataBufferAlloc.bits.size := payload.info.size

  when(dataBufferAlloc.fire) {
    payloadMiscNext.state.bufferAllocated := true.B || payload.state.bufferAllocated
  }

  when(icn.rx.data.valid) {
    assert(icn.rx.data.bits.Opcode === DatOpcode.NCBWrDataCompAck)
  }

  wakeupOutCond := allDone && valid
  when(io.wakeupOut.valid) {
    payloadMiscNext.info.isSnooped := false.B
  }

  axi.b.ready := true.B
  private val plmnd = payloadMiscNext.state.d
  private val pld = payload.state.d

  when(axi.b.valid) {
    plmnd.wresp := true.B
  }

  when(io.readDataFire) {
    plmnd.rdata := io.readDataLast || pld.rdata
  }

  private val busSize = log2Ceil(axiParams.dataBits / 8).U
  private val largeLen = (1.U(8.W) << (payload.info.size - busSize)).asUInt
  private val len = Mux(payload.info.size > busSize, largeLen - 1.U, 0.U)
  private val size = Mux(payload.info.size > busSize, busSize, payload.info.size)
  axi.aw.valid := valid && payload.state.axiWaddr && !waiting.orR
  axi.aw.bits := DontCare
  axi.aw.bits.id := io.idx
  axi.aw.bits.addr := payload.info.addr
  axi.aw.bits.len := len
  axi.aw.bits.burst := 1.U
  axi.aw.bits.size := size

  axi.ar.valid := valid && payload.state.axiRaddr && !waiting.orR
  axi.ar.bits := DontCare
  axi.ar.bits.id := io.idx
  axi.ar.bits.addr := payload.info.addr
  axi.ar.bits.len := len
  axi.ar.bits.burst := 1.U
  axi.ar.bits.size := size

  axi.w.valid := valid && payload.state.axiWdata && !waiting.orR
  axi.w.bits := DontCare

  when(axi.aw.fire) {
    plmnd.waddr := true.B || pld.waddr
  }
  when(axi.ar.fire) {
    plmnd.raddr := true.B || pld.raddr
  }
  when(axi.w.fire) {
    plmnd.wdata := true.B || pld.wdata
  }
}