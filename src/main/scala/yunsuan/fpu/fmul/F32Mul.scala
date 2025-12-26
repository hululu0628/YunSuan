package yunsuan.fpu.fmul

import chisel3._
import yunsuan.fpu._

class F32MUL(val totalWidth: Int = 32) extends Module with FloatParams{
  val io = IO(new Bundle() {
    val fp_a = Input(UInt(totalWidth.W))
    val fp_b = Input(UInt(totalWidth.W))
    val rm  = Input(UInt(rmWidth.W))
    val outResToFADD = Output(UInt((totalWidth + decimalWidth).W))
    val outRes = Output(UInt((totalWidth).W))
    val outFlags = Output(UInt((flagsWidth).W))
  })
  val FMULS0 = Module(new F32MULS0(totalWidth))
  FMULS0.io.fp_a := io.fp_a
  FMULS0.io.fp_b := io.fp_b
  FMULS0.io.rm := io.rm
  val FMULS1 = Module(new F32MULS1(totalWidth))
  FMULS1.io.inFromS0 := FMULS0.io.outToS1
  io.outResToFADD := FMULS1.io.outResToFADD
  io.outRes := FMULS1.io.outRes
  io.outFlags := FMULS1.io.outFlags
}