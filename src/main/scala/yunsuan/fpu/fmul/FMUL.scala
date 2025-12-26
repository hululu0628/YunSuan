package yunsuan.fpu.fmul

import chisel3._
import yunsuan.fpu._

class FMUL(val totalWidth: Int) extends Module with FloatParams{
  val io = IO(new Bundle() {
    val fpA = Input(UInt(totalWidth.W))
    val fpB = Input(UInt(totalWidth.W))
    val rm  = Input(UInt(rmWidth.W))
    val outResToFADD = Output(UInt((totalWidth + decimalWidth).W))
    val outCtrlToFADD = Output(new FMULToFADDCtrlBundle(totalWidth))
    val outRes = Output(UInt((totalWidth).W))
    val outFlags = Output(UInt((flagsWidth).W))
  })
  val FMULS0 = Module(new FMULS0(totalWidth))
  FMULS0.io.fp_a := io.fpA
  FMULS0.io.fp_b := io.fpB
  FMULS0.io.rm := io.rm
  val FMULS1 = Module(new FMULS1(totalWidth))
  FMULS1.io.inFromS0 := FMULS0.io.outToS1
  io.outResToFADD := FMULS1.io.outResToFADD
  val FMULS2 = Module(new FMULS2(totalWidth))
  FMULS2.io.inFromS1 := FMULS1.io.outToS2
  io.outRes := FMULS2.io.outRes
  io.outFlags := FMULS2.io.outFlags
  io.outCtrlToFADD := FMULS1.io.outCtrlToFADD
}