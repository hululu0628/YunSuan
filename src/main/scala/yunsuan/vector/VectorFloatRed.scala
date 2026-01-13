package yunsuan.vector

import chisel3._
import chisel3.util._
import yunsuan.fpu._
import yunsuan.fpu.falu._
import yunsuan.fpu.fmul.FMULToFADDCtrlBundle

// TODO: need vl, vstart logic
class VFRedComp extends Module {
  val io = IO(new Bundle() {
    val vsew = Input(UInt(2.W))
    val vs1 = Input(UInt(128.W))
    val vs2 = Input(UInt(128.W))
    val oldvd = Input(UInt(128.W))
    val mask = Input(UInt(128.W))
    val isMax = Input(Bool())
    val outRes = Output(UInt(128.W))
    val outFlags = Output(UInt(5.W))
  })
}

class fcomp_2to1(val totalWidth: Int) extends Module with FloatParams {
  val io = IO(new Bundle() {
    val fpA = Input(UInt(totalWidth.W))
    val fpB = Input(UInt(totalWidth.W))
    val activeMask = Input(UInt(2.W)) // 1-bit: B active, 0-bit: A active
    val isMax = Input(Bool())
    val fpC = Output(UInt(totalWidth.W))
    val outActive = Output(Bool())
    val outFlags = Output(UInt(5.W))
  })
  val fpA = io.fpA
  val fpB = io.fpB
  val fpC = io.fpC
  val activeMask = io.activeMask
  val isMax = io.isMax

  val signA = fpA.head(1).asBool
  val signB = fpB.head(1).asBool
  val magA = fpA.tail(1) // magnitude = exponent + fraction
  val magB = fpB.tail(1)
  val expA = magA.head(exponentWidth)
  val expB = magB.head(exponentWidth)
  val fracA = magA.tail(exponentWidth)
  val fracB = magB.tail(exponentWidth)

  val expAIsZero = !expA.orR
  val expBIsZero = !expB.orR
  val expAIsAllOne = expA.andR
  val expBIsAllOne = expB.andR
  val fracAIsZero = !fracA.orR
  val fracBIsZero = !fracB.orR

  val quietBitA = fracA.head(1).asBool
  val quietBitB = fracB.head(1).asBool

  val magAGB = magA > magB
  val diffSignAB = signA ^ signB

  val selAFromAB = Mux(diffSignAB, signB, Mux(signA ^ isMax, magAGB, !magAGB))

  val fpAIsNaN = expAIsAllOne & fracAIsZero
  val fpBIsNaN = expBIsAllOne & fracBIsZero

  val fpAInvalid = fpAIsNaN | !activeMask(0)
  val fpBInvalid = fpBIsNaN | !activeMask(1)

  io.fpC := cNaN.U
  when((!fpAInvalid & fpBInvalid) | (!fpAInvalid & selAFromAB)) {
    io.fpC := fpA
  }.elsewhen((fpAInvalid & !fpBInvalid) | (!fpBInvalid & !selAFromAB)) {
    io.fpC := fpB
  }

  io.outActive := activeMask.orR

  val flagsNV = (!quietBitA & activeMask(0).asBool) | (!quietBitB & activeMask(1).asBool)
  val flagsDZ = false.B
  val flagsOF = false.B
  val flagsUF = false.B
  val flagsNX = false.B
  io.outFlags := Cat(flagsNV, flagsDZ, flagsOF, flagsUF, flagsNX)
}

class fcomp_3to1(val totalWidth: Int) extends Module with FloatParams {
  val io = IO(new Bundle() {
    val fpA = Input(UInt(totalWidth.W))
    val fpB = Input(UInt(totalWidth.W))
    val fpC = Input(UInt(totalWidth.W))
    val activeMask = Input(UInt(3.W)) // 2-bit: C active, 1-bit: B active, 0-bit: A active
    val isMax = Input(Bool())
    val fpD = Output(UInt(totalWidth.W))
    val outActive = Output(Bool())
    val outFlags = Output(UInt(5.W))
  })
  val fpA = io.fpA
  val fpB = io.fpB
  val fpC = io.fpC
  val activeMask = io.activeMask
  val isMax = io.isMax

  val signA = fpA.head(1).asBool
  val signB = fpB.head(1).asBool
  val signC = fpC.head(1).asBool
  val magA = fpA.tail(1) // magnitude = exponent + fraction
  val magB = fpB.tail(1)
  val magC = fpC.tail(1)
  val expA = magA.head(exponentWidth)
  val expB = magB.head(exponentWidth)
  val expC = magC.head(exponentWidth)
  val fracA = magA.tail(exponentWidth)
  val fracB = magB.tail(exponentWidth)
  val fracC = magC.tail(exponentWidth)

  val expAIsZero = !expA.orR
  val expBIsZero = !expB.orR
  val expCIsZero = !expC.orR
  val expAIsAllOne = expA.andR
  val expBIsAllOne = expB.andR
  val expCIsAllOne = expC.andR
  val fracAIsZero = !fracA.orR
  val fracBIsZero = !fracB.orR
  val fracCIsZero = !fracC.orR

  val quietBitA = fracA.head(1).asBool
  val quietBitB = fracB.head(1).asBool
  val quietBitC = fracC.head(1).asBool

  val magAGB = magA > magB
  val magAGC = magA > magC
  val magBGC = magB > magC
  val diffSignAB = signA ^ signB
  val diffSignAC = signA ^ signC
  val diffSignBC = signB ^ signC

  val selAFromAB = Mux(diffSignAB, signB, Mux(signA ^ isMax, magAGB, !magAGB))
  val selAFromAC = Mux(diffSignAC, signC, Mux(signA ^ isMax, magAGC, !magAGC))
  val selBFromBC = Mux(diffSignBC, signC, Mux(signB ^ isMax, magBGC, !magBGC))

  val fpAIsNaN = expAIsAllOne & fracAIsZero
  val fpBIsNaN = expBIsAllOne & fracBIsZero
  val fpCIsNaN = expCIsAllOne & fracCIsZero

  val fpAInvalid = fpAIsNaN | !activeMask(0)
  val fpBInvalid = fpBIsNaN | !activeMask(1)
  val fpCInvalid = fpCIsNaN | !activeMask(2)

  io.fpD := cNaN.U
  when((!fpAInvalid & fpBInvalid & fpCInvalid) | (!fpAInvalid & fpBInvalid & selAFromAC) | (!fpAInvalid & fpCInvalid & selAFromAB) | (!fpAInvalid & selAFromAB & selAFromAC)) {
    io.fpD := fpA
  }.elsewhen((fpAInvalid & !fpBInvalid & fpCInvalid) | (fpAInvalid & !fpBInvalid & selBFromBC) | (!fpBInvalid & fpCInvalid & !selAFromAB) | (!fpBInvalid & !selAFromAB & selBFromBC)) {
    io.fpD := fpB
  }.elsewhen((fpAInvalid & fpBInvalid & !fpCInvalid) | (fpAInvalid & !fpCInvalid & !selAFromAC) | (fpBInvalid & !fpCInvalid & !selBFromBC) | (!fpCInvalid & !selAFromAC & !selBFromBC)) {
    io.fpD := fpC
  }

  io.outActive := activeMask.orR

  val flagsNV = (!quietBitA & activeMask(0).asBool) | (!quietBitB & activeMask(1).asBool) | (!quietBitC & activeMask(2).asBool)
  val flagsDZ = false.B
  val flagsOF = false.B
  val flagsUF = false.B
  val flagsNX = false.B
  io.outFlags := Cat(flagsNV, flagsDZ, flagsOF, flagsUF, flagsNX)
}

class VFRedSumU extends Module {

}

class Adder_w(w: Int) extends Module {
  val io = IO(new Bundle() {
    val fpA = Input(UInt(w.W))
    val fpB = Input(UInt(w.W))
    val mask = Input(UInt(2.W))
    val rm = Input(UInt(3.W))
    val outActive = Output(Bool())
    val outRes = Output(UInt(w.W))
    val outFlags = Output(UInt(w.W))
  })
  val fpA = io.fpA
  val fpB = io.fpB
  val mask = io.mask
  val rm = io.rm

  val not_masked = mask.andR
  val both_masked = !mask.orR
  val masked_result = Mux(mask(0), fpA, fpB)
  val not_masked_s1_r = RegNext(not_masked)
  val both_masked_s1_r = RegNext(both_masked)
  val masked_result_s1_r = RegNext(masked_result)

  val fadder = Module(new FloatAdder(w))
  fadder.io.fpA := fpA
  fadder.io.fpB := fpB
  fadder.io.rm := rm

  io.outActive := both_masked_s1_r
  io.outRes := Mux(not_masked_s1_r, fadder.io.outRes, masked_result_s1_r)
  io.outFlags := Mux(not_masked_s1_r, fadder.io.outFlags, Fill(5, false.B)) // Note: when vs1 is sNaN but vs2 all masked
}

class FloatAdder(val totalWidth: Int) extends Module with FloatParams {
  val io = IO(new Bundle() {
    val fpA = Input(UInt(totalWidth.W))
    val fpB = Input(UInt(totalWidth.W))
    val rm  = Input(UInt(rmWidth.W))
    val outRes = Output(UInt(totalWidth.W))
    val outFlags = Output(UInt(flagsWidth.W))
  })
  val FALUS0 = Module(new FALUS0V2(totalWidth))
  FALUS0.io.fpA := io.fpA
  FALUS0.io.fpB := io.fpB
  FALUS0.io.isSub := false.B
  FALUS0.io.rm := io.rm
  FALUS0.io.fpAAppend := 0.U
  FALUS0.io.inCtrlFromFMUL := 0.U.asTypeOf(new FMULToFADDCtrlBundle(totalWidth))
  val FMULS1 = Module(new FALUS1V2(totalWidth))
  FMULS1.io.fromS0 := RegNext(FALUS0.io.toS1)
  io.outRes := FMULS1.io.outRes
  io.outFlags := FMULS1.io.outFlags
}
