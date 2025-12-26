package yunsuan.fpu.falu

import chisel3._
import yunsuan.fpu._
import yunsuan.fpu.falu._
import yunsuan.fpu.fmul.FMULToFADDCtrlBundle

class FALU(val totalWidth: Int) extends Module with FloatParams{
  val io = IO(new Bundle() {
    val fpA = Input(UInt(totalWidth.W))
    val fpAAppend = Input(UInt(decimalWidth.W))
    val fpB = Input(UInt(totalWidth.W))
    val rm  = Input(UInt(rmWidth.W))
    val isSub  = Input(Bool())
    val outRes = Output(UInt((totalWidth).W))
    val outFlags = Output(UInt((flagsWidth).W))
    val inCtrlFromFMUL = Input(new FMULToFADDCtrlBundle(totalWidth))
  })
  val FALUS0 = Module(new FALUS0V2(totalWidth))
  FALUS0.io.fpA := io.fpA
  FALUS0.io.fpB := io.fpB
  FALUS0.io.isSub := io.isSub
  FALUS0.io.rm := io.rm
  FALUS0.io.fpAAppend := io.fpAAppend
  FALUS0.io.inCtrlFromFMUL := io.inCtrlFromFMUL
  val FMULS1 = Module(new FALUS1V2(totalWidth))
  FMULS1.io.fromS0 := FALUS0.io.toS1
  io.outRes := FMULS1.io.outRes
  io.outFlags := FMULS1.io.outFlags
}