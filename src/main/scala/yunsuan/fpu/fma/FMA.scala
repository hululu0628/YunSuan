package yunsuan.fpu.fma

import yunsuan.fpu.falu.FALU
import yunsuan.fpu.fmul.FMUL
import yunsuan.fpu.FloatParams
import chisel3._
import chisel3.util._

class FMA(val totalWidth: Int) extends Module with FloatParams{
  val io = IO(new Bundle() {
    val fpA = Input(UInt(totalWidth.W))
    val fpB = Input(UInt(totalWidth.W))
    val fpC = Input(UInt(totalWidth.W))
    val rm  = Input(UInt(rmWidth.W))
    val outRes = Output(UInt((totalWidth).W))
    val outFlags = Output(UInt((flagsWidth).W))
  })
  val FMUL = Module(new FMUL(totalWidth))
  FMUL.io.fpA := io.fpA
  FMUL.io.fpB := io.fpB
  FMUL.io.rm := io.rm
  dontTouch(FMUL.io.outRes)
  dontTouch(FMUL.io.outFlags)
  val FALU = Module(new FALU(totalWidth))
  FALU.io.fpA := FMUL.io.outResToFADD.head(totalWidth)
  FALU.io.fpAAppend := FMUL.io.outResToFADD.tail(totalWidth)
  FALU.io.inCtrlFromFMUL := FMUL.io.outCtrlToFADD
  FALU.io.fpB := io.fpC
  FALU.io.rm := FMUL.io.outCtrlToFADD.rm
  FALU.io.isSub := false.B
  io.outRes := FALU.io.outRes
  io.outFlags := FALU.io.outFlags
}
