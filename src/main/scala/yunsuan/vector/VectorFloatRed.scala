package yunsuan.vector

import chisel3._
import chisel3.util._
import yunsuan.fpu._
import yunsuan.fpu.falu._
import yunsuan.fpu.fmul.FMULToFADDCtrlBundle

object VSew {
  val e8  = "b00".U(2.W)
  val e16 = "b01".U(2.W)
  val e32 = "b10".U(2.W)
  val e64 = "b11".U(2.W)
}

class VFInfo extends Bundle {
  val vm = Bool()
  val ma = Bool()
  val ta = Bool()
  val vlmul = UInt(3.W)
  val vl = UInt(8.W)
  val vstart = UInt(7.W)
  val uopIdx = UInt(6.W)
  val rm = UInt(3.W)
}

class VFCompInput extends Bundle {
  val info = new VFInfo()
  val vsew = UInt(2.W)
  val vs1 = UInt(128.W)
  val vs2 = UInt(128.W)
  val old_vd = UInt(128.W)
  val mask = UInt(128.W)
  val isMax = Bool()
}
class VFSumInput extends  Bundle {
  val info = new VFInfo()
  val vsew = UInt(2.W)
  val vs1 = UInt(128.W)
  val vs2 = UInt(128.W)
  val old_vd = UInt(128.W)
  val mask = UInt(128.W)
  val widen = Bool()
}

class VFOutput extends Bundle {
  val vd = UInt(128.W)
  val fflags = UInt(5.W)
  val vxsat = Bool()
}

// TODO: need vl, vstart logic
// FIXME: considering vstart
class VFRedComp extends Module {
  val VLEN = 128
  val io = IO(new Bundle() {
    val in = Input(new VFCompInput)
    val out = Output(new VFOutput)
  })
  val vsew = io.in.vsew
  val vs1 = io.in.vs1
  val vs2 = io.in.vs2
  val old_vd = io.in.old_vd
  val mask = io.in.mask
  val isMax = io.in.isMax
  val vm = io.in.info.vm
  val ta = io.in.info.ta
  val vl = io.in.info.vl

  def IsActive(i: Int): Bool = {
    mask(i).asBool && (i.U < vl)
  }

  // vector f64
  val scalar64 = vs1(63, 0)
  val vec64 = vs2.asTypeOf(Vec(2, UInt(64.W)))
  val f64_0_0 = Module(new fcomp_3to1(64))
  f64_0_0.io.fpA := vec64(0)
  f64_0_0.io.fpB := vec64(1)
  f64_0_0.io.fpC := scalar64
  f64_0_0.io.activeMask := Cat(true.B, IsActive(1), IsActive(0))
  f64_0_0.io.isMax := isMax
  val f64_res = f64_0_0.io.fpD
  val f64_flags = f64_0_0.io.outFlags

  // vector f32
	val scalar32 = vs1(31, 0)
	val vec32 = vs2.asTypeOf(Vec(4, UInt(32.W)))
  val f32_0_0 = Module(new fcomp_3to1(32))
  f32_0_0.io.fpA := vec32(0)
  f32_0_0.io.fpB := vec32(1)
  f32_0_0.io.fpC := scalar32
  f32_0_0.io.activeMask := Cat(true.B, IsActive(1), IsActive(0))
  f32_0_0.io.isMax := isMax
  val f32_0_1 = Module(new fcomp_2to1(32))
  f32_0_1.io.fpA := vec32(2)
  f32_0_1.io.fpB := vec32(3)
  f32_0_1.io.activeMask := Cat(IsActive(3), IsActive(2))
  f32_0_1.io.isMax := isMax
  val f32_1_0 = Module(new fcomp_2to1(32))
  f32_1_0.io.fpA := f32_0_0.io.fpD
  f32_1_0.io.fpB := f32_0_1.io.fpC
  f32_1_0.io.activeMask := Cat(f32_0_1.io.outActive, f32_0_0.io.outActive)
  f32_1_0.io.isMax := isMax
  val f32_res = f32_1_0.io.fpC
  val f32_flags = f32_0_0.io.outFlags | f32_0_1.io.outFlags | f32_1_0.io.outFlags

  // vector f16
  val scalar16 = vs1(15, 0)
  val vec16 = vs2.asTypeOf(Vec(8, UInt(16.W)))
  val f16_0_0 = Module(new fcomp_3to1(16))
	f16_0_0.io.fpA := vec16(0)
	f16_0_0.io.fpB := vec16(1)
	f16_0_0.io.fpC := scalar16
	f16_0_0.io.activeMask := Cat(true.B, IsActive(1), IsActive(0))
	f16_0_0.io.isMax := isMax
	val f16_0_1 = Module(new fcomp_3to1(16))
	f16_0_1.io.fpA := vec16(2)
	f16_0_1.io.fpB := vec16(3)
	f16_0_1.io.fpC := vec16(4)
	f16_0_1.io.activeMask := Cat(IsActive(4), IsActive(3), IsActive(2))
	f16_0_1.io.isMax := isMax
	val f16_0_2 = Module(new fcomp_3to1(16))
	f16_0_2.io.fpA := vec16(5)
	f16_0_2.io.fpB := vec16(6)
	f16_0_2.io.fpC := vec16(7)
	f16_0_2.io.activeMask := Cat(IsActive(7), IsActive(6), IsActive(5))
	f16_0_2.io.isMax := isMax
	val f16_1_0 = Module(new fcomp_3to1(16))
	f16_1_0.io.fpA := f16_0_0.io.fpD
	f16_1_0.io.fpB := f16_0_1.io.fpD
	f16_1_0.io.fpC := f16_0_2.io.fpD
	f16_1_0.io.activeMask := Cat(f16_0_2.io.outActive, f16_0_1.io.outActive, f16_0_0.io.outActive)
	f16_1_0.io.isMax := isMax
	val f16_res = f16_1_0.io.fpD
	val f16_flags = f16_0_0.io.outFlags | f16_0_1.io.outFlags | f16_0_2.io.outFlags | f16_1_0.io.outFlags

  val old_vd_masked = old_vd | Fill(VLEN, ta)

	val vsew_s1_r = RegNext(vsew)
  val old_vd_masked_s1_r = RegNext(old_vd_masked)
  val f64_res_s1_r = RegNext(f64_res)
	val f32_res_s1_r = RegNext(f32_res)
	val f16_res_s1_r = RegNext(f16_res)
	val f64_flags_s1_r = RegNext(f64_flags)
	val f32_flags_s1_r = RegNext(f32_flags)
	val f16_flags_s1_r = RegNext(f16_flags)

  val vd_val = Wire(UInt(128.W))
  vd_val := MuxLookup(vsew_s1_r, 0.U)(Seq(
    VSew.e16 -> Cat(Fill(VLEN - 16, 1.U) & old_vd_masked_s1_r(VLEN - 1, 16), f16_res_s1_r),
    VSew.e32 -> Cat(Fill(VLEN - 32, 1.U) & old_vd_masked_s1_r(VLEN - 1, 32), f32_res_s1_r),
    VSew.e64 -> Cat(Fill(VLEN - 64, 1.U) & old_vd_masked_s1_r(VLEN - 1, 64), f64_res_s1_r),
  ))
  val outFlags = MuxLookup(vsew_s1_r, 0.U)(Seq(
    VSew.e16 -> f16_flags_s1_r,
    VSew.e32 -> f32_flags_s1_r,
    VSew.e64 -> f64_flags_s1_r,
  ))
  io.out.vd := vd_val
  io.out.fflags := outFlags
  io.out.vxsat := false.B
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

class VFRedUSum() extends Module {
  val VLEN = 128
  val io = IO(new Bundle() {
    val in = Input(new VFSumInput)
    val out = Output(new VFOutput)
  })
  val vsew = io.in.vsew
  val vs1 = io.in.vs1
  val vs2 = io.in.vs2
  val old_vd = io.in.old_vd
  val mask = io.in.mask
  val widen = io.in.widen
  val vm = io.in.info.vm
  val ta = io.in.info.ta
  val vl = io.in.info.vl
  val rm = io.in.info.rm

  def IsActive(i: Int): Bool = {
    mask(i).asBool && (i.U < vl)
  }
  def SrcWiden(in: UInt): UInt = {
    assert(in.getWidth == 16 || in.getWidth == 32)
    val w = in.getWidth
    val exp_w = if (in.getWidth == 16) 5 else 8
    val sig_w = if (in.getWidth == 16) 11 else 24
    val dest_exp_w = if (in.getWidth == 16) 8 else 11
    val dest_sig_w = if (in.getWidth == 16) 24 else 53
    val fp_a_is_denormal = !in(w-2,sig_w-1).orR
    val fp_a_lshift = Wire(UInt((sig_w-1).W))
    val fp_a_is_denormal_to_widen_exp = Wire(UInt(dest_exp_w.W))
    if (in.getWidth == 16) {
      val U_fp_a_is_denormal_to_widen = Module(new ShiftLeftPriorityWithF32EXPResult(srcW = sig_w-1, priorityShiftValueW = sig_w-1, expW = dest_exp_w))
      U_fp_a_is_denormal_to_widen.io.src := in(sig_w-2,0)
      U_fp_a_is_denormal_to_widen.io.priority_shiftValue := in(sig_w-2,0)
      fp_a_lshift := U_fp_a_is_denormal_to_widen.io.lshift_result
      fp_a_is_denormal_to_widen_exp := U_fp_a_is_denormal_to_widen.io.exp_result
    }
    else {
      val U_fp_a_is_denormal_to_widen = Module(new ShiftLeftPriorityWithF64EXPResult(srcW = sig_w-1, priorityShiftValueW = sig_w-1, expW = dest_exp_w))
      U_fp_a_is_denormal_to_widen.io.src := in(sig_w-2,0)
      U_fp_a_is_denormal_to_widen.io.priority_shiftValue := in(sig_w-2,0)
      fp_a_lshift := U_fp_a_is_denormal_to_widen.io.lshift_result
      fp_a_is_denormal_to_widen_exp := U_fp_a_is_denormal_to_widen.io.exp_result
    }
    val fp_a_widen_mantissa = Cat(
      Mux(fp_a_is_denormal,Cat(fp_a_lshift.tail(1),0.U),in(sig_w-2,0)),
      0.U((dest_sig_w-sig_w).W)
    )
    val const_1 =  if (in.getWidth == 16) "b1000".U else "b1000".U
    val const_0 =  if (in.getWidth == 16) "b0111".U else "b0111".U
    val fp_a_widen_exp = Mux(
      fp_a_is_denormal,
      fp_a_is_denormal_to_widen_exp,
      Mux(in(w-2), Cat(const_1,in(w-3,w-1-exp_w)), Cat(const_0,in(w-3,w-1-exp_w)))
    )
    Cat(in(w-1), fp_a_widen_exp, fp_a_widen_mantissa)
  }

  val widen_vector = Wire(UInt((2 * VLEN).W))
  widen_vector := Mux(
    vsew === VSew.e16,
    Cat(vs2.asTypeOf(Vec(8, UInt(16.W))).map(x => SrcWiden(x)).reverse),
    Cat(vs2.asTypeOf(Vec(4, UInt(32.W))).map(x => SrcWiden(x)).reverse)
  )

  // TODO:
  val vsew_s1_r = RegNext(vsew)
  val widen_s1_r = RegNext(widen)
  val widen_vector_s1_r = RegNext(widen_vector)
  // f64
  val f64_0 = Module(new FAdder_w(64))
  val f64_1 = Module(new FAdder_w(64))
  // f32
  val f32_0 = Module(new FAdder_w(32))
  val f32_1 = Module(new FAdder_w(32))
  val f32_2 = Module(new FAdder_w(32))
  val f32_3 = Module(new FAdder_w(32))
  // f16
  val f16_0 = Module(new FAdder_w(16))
  val f16_1 = Module(new FAdder_w(16))
  val f16_2 = Module(new FAdder_w(16))
  val f16_3 = Module(new FAdder_w(16))
}

class FAdder_w(w: Int) extends Module {
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
