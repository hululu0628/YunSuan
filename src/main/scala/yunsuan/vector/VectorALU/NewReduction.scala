package yunsuan.vector.alu

import chisel3._
import chisel3.util._
import scala.language.postfixOps
import yunsuan.vector._
import yunsuan.vector.alu.VAluOpcode._

trait ReductionParam {
  val VLEN = VIFuParam.VLEN
  val XLEN = VIFuParam.XLEN
  val vlenb = VIFuParam.VLENB
}

class NewReduction extends Module with ReductionParam {
  val io = IO(new Bundle {
    val in = Flipped(ValidIO(new VIFuInput))
    val out = Output(new VIFuOutput)
  })
  val vs1 = io.in.bits.vs1
  val vs2 = io.in.bits.vs2
  val old_vd = io.in.bits.old_vd
  val vmask = io.in.bits.mask
  val opcode = io.in.bits.opcode
  val srcTypeVs2 = io.in.bits.srcType(0)
  val vsew = srcTypeVs2(1, 0)
  val srcTypeVs1 = io.in.bits.srcType(1)
  val vdType = io.in.bits.vdType
  val vm = io.in.bits.info.vm
  val ma = io.in.bits.info.ma
  val ta = io.in.bits.info.ta
  val vlmul = io.in.bits.info.vlmul
  val vl = io.in.bits.info.vl
  val uopIdx = io.in.bits.info.uopIdx
  val InValid = io.in.valid

  val vlRemain = vl
  val vlRemainBytes = vlRemain << vsew
  val signed = srcTypeVs2(3, 2) === 1.U
  val widen = vdType(1, 0) === (srcTypeVs2(1, 0) + 1.U)
  val max = opcode.isVredmax

  val vredsum_vs = opcode.isVredsum
  val vredmax_vs = opcode.isVredmax && srcTypeVs2(2).asBool
  val vredmaxu_vs = opcode.isVredmax && !srcTypeVs2(2).asBool
  val vredmin_vs = opcode.isVredmin && srcTypeVs2(2).asBool
  val vredminu_vs = opcode.isVredmin && !srcTypeVs2(2).asBool
  val vredand_vs = opcode.isVredand
  val vredor_vs = opcode.isVredor
  val vredxor_vs = opcode.isVredxor
  val vwredsum_vs = opcode.isVredsum && srcTypeVs2(2).asBool && (vdType(1, 0) === (srcTypeVs2(1, 0) + 1.U))
  val vwredsumu_vs = opcode.isVredsum && !srcTypeVs2(2).asBool && (vdType(1, 0) === (srcTypeVs2(1, 0) + 1.U))

  val vs12 = Cat(vs1, vs2)
  val vs12_bytes = vs12.asTypeOf(Vec(2 * vlenb, UInt(8.W)))
  val vs12_masked = Wire(UInt((2 * VLEN).W))
  val vs12m_bytes = Wire(Vec(2 * vlenb, UInt(8.W)))

  val valid_s1_r = RegNext(InValid)
  val vredand_vs_s1_r = RegEnable(vredand_vs, false.B, InValid)
  val vredor_vs_s1_r = RegEnable(vredor_vs, false.B, InValid)
  val vredxor_vs_s1_r = RegEnable(vredxor_vs, false.B, InValid)
  val vredsum_s1_r = RegEnable(vredsum_vs || vwredsum_vs || vwredsumu_vs, false.B, InValid)
  val vredcomp_s1_r = RegEnable(vredmax_vs || vredmaxu_vs || vredmin_vs || vredminu_vs, false.B, InValid)
  val vs12m_s1_r = RegEnable(vs12_masked, 0.U, InValid)
  val vsew_s1_r = RegEnable(vsew, 0.U, InValid)
  val signed_s1_r = RegEnable(signed, 0.U, InValid)
  val widen_s1_r = RegEnable(widen, 0.U, InValid)
  val max_s1_r = RegEnable(max, 0.U, InValid)
  val logicRes_s1 = Wire(UInt(VLEN.W))

  val valid_s2_r = RegNext(valid_s1_r)
  val sumRes_s2 = Wire(UInt(VLEN.W))
  val compareRes_s2 = Wire(UInt(VLEN.W))
  val vredlogic_s2_r = RegEnable(vredand_vs_s1_r || vredor_vs_s1_r || vredxor_vs_s1_r, false.B, valid_s1_r)
  val vredsum_s2_r = RegEnable(vredsum_s1_r, false.B, valid_s1_r)
  val vredcomp_s2_r = RegEnable(vredcomp_s1_r, false.B, valid_s1_r)

  // generate masked source
  def umax(w: Int) = ~(0.U(w.W))
  def smax(w: Int) = Cat(0.U(1.W), ~(0.U((w - 1).W)))
  def smin(w: Int) = Cat(1.U(1.W), 0.U((w - 1).W))

  val fillValue8 = WireInit(UInt(8.W), 0.U)
  val fillValue16 = WireInit(UInt(16.W), 0.U)
  val fillValue32 = WireInit(UInt(32.W), 0.U)
  val fillValue64 = WireInit(UInt(64.W), 0.U)

  when(InValid) {
    when(vredmax_vs) {
      fillValue8 := smin(8)
      fillValue16 := smin(16)
      fillValue32 := smin(32)
      fillValue64 := smin(64)
    }.elsewhen(vredmin_vs) {
      fillValue8 := smax(8)
      fillValue16 := smax(16)
      fillValue32 := smax(32)
      fillValue64 := smax(64)
    }.elsewhen(vredminu_vs || vredand_vs) {
      fillValue8 := umax(8)
      fillValue16 := umax(16)
      fillValue32 := umax(32)
      fillValue64 := umax(64)
    }
  }

  for (i <- 0 until 2 * vlenb) {
    val fillByte = MuxLookup(vsew, 0.U)(Seq(
      VSew.e8 -> fillValue8,
      VSew.e16 -> fillValue16(8 * (i % 2) + 7, 8 * (i % 2)),
      VSew.e32 -> fillValue32(8 * (i % 4) + 7, 8 * (i % 4)),
      VSew.e64 -> fillValue64(8 * (i % 8) + 7, 8 * (i % 8)),
    ))
    vs12m_bytes(i) := Mux((!vm && !vmask(i)) || (i.U >= vlRemainBytes.asUInt), fillByte, vs12_bytes(i))
  }
  vs12_masked := Cat(vs12m_bytes.reverse)

  // stage 1
  val vredand = Module(new VRedAND())
  vredand.io.vsew := vsew_s1_r
  vredand.io.v1 := vs12m_s1_r(127, 0)
  vredand.io.v2 := vs12m_s1_r(191, 128)
  val vredor = Module(new VRedOR())
  vredor.io.vsew := vsew_s1_r
  vredor.io.v1 := vs12m_s1_r(127, 0)
  vredor.io.v2 := vs12m_s1_r(191, 128)
  val vredxor = Module(new VRedXOR())
  vredxor.io.vsew := vsew_s1_r
  vredxor.io.v1 := vs12m_s1_r(127, 0)
  vredxor.io.v2 := vs12m_s1_r(191, 128)

  logicRes_s1 := 0.U
  when(vredand_vs_s1_r) {
    logicRes_s1 := Cat(0.U((VLEN - XLEN).W), vredand.io.vout)
  }.elsewhen(vredor_vs_s1_r) {
    logicRes_s1 := Cat(0.U((VLEN - XLEN).W), vredor.io.vout)
  }.elsewhen(vredxor_vs_s1_r) {
    logicRes_s1 := Cat(0.U((VLEN - XLEN).W), vredand.io.vout)
  }

  val logicRes_s2_r = RegEnable(logicRes_s1, 0.U, valid_s1_r)

  // stage 1 ~ 2
  val vredwsum = Module(new VRedWSUM())
  vredwsum.io.signed := signed_s1_r
  vredwsum.io.widen := widen_s1_r
  vredwsum.io.vsew := vsew_s1_r
  vredwsum.io.v1 := vs12m_s1_r(127, 0)
  vredwsum.io.v2 := vs12m_s1_r(191, 128)

  sumRes_s2 := Cat(0.U((VLEN - XLEN).W), vredwsum.io.vout)

  // stage 1 ~ 2
  val vredcomp = Module(new VRedComp())
  vredcomp.io.signed := signed_s1_r
  vredcomp.io.vsew := vsew_s1_r
  vredcomp.io.max := max_s1_r
  vredcomp.io.v1 := vs12m_s1_r(127, 0)
  vredcomp.io.v2 := vs12m_s1_r(191, 128)

  compareRes_s2 := Cat(0.U((VLEN - XLEN).W), vredcomp.io.vout)

  io.out.vd := 0.U
  when(vredlogic_s2_r) {
    io.out.vd := logicRes_s2_r
  }.elsewhen(vredsum_s2_r) {
    io.out.vd := sumRes_s2
  }.elsewhen(vredcomp_s2_r) {
    io.out.vd := compareRes_s2
  }
  io.out.vxsat := false.B
}

class VRedAND extends Module with ReductionParam {
  val io = IO(new Bundle {
    val vsew = Input(UInt(2.W))
    val v1 = Input(UInt(VLEN.W))
    val v2 = Input(UInt(XLEN.W))
    val vout = Output(UInt(XLEN.W))
  })
  val v1 = io.v1
  val v2 = MuxLookup(io.vsew, 0.U)(Seq(
    VSew.e8 -> Cat(Fill(XLEN - 8, 1.U), io.v2(7,0)),
    VSew.e16 -> Cat(Fill(XLEN - 16, 1.U), io.v2(15,0)),
    VSew.e32 -> Cat(Fill(XLEN - 32, 1.U), io.v2(31,0)),
    VSew.e64 -> io.v2,
  ))
  val in1 = v1(63, 0) & v2
  val in2 = v1(127, 64)

  val out64 = in1 & in2
  val out32 = out64(31,0) & out64(63,32)
  val out16 = out32(15,0) & out32(31,0)
  val out8 = out16(7,0) & out16(15,8)

  io.vout := MuxLookup(io.vsew, 0.U)(Seq(
    VSew.e8 -> Cat(0.U((XLEN - 8).W), out8),
    VSew.e16 -> Cat(0.U((XLEN - 16).W), out16),
    VSew.e32 -> Cat(0.U((XLEN - 32).W), out32),
    VSew.e64 -> out64
  ))
}

class VRedOR extends Module with ReductionParam {
  val io = IO(new Bundle {
    val vsew = Input(UInt(2.W))
    val v1 = Input(UInt(VLEN.W))
    val v2 = Input(UInt(XLEN.W))
    val vout = Output(UInt(XLEN.W))
  })
  val v1 = io.v1
  val v2 = MuxLookup(io.vsew, 0.U)(Seq(
    VSew.e8 -> Cat(Fill(XLEN - 8, 1.U), io.v2(7,0)),
    VSew.e16 -> Cat(Fill(XLEN - 16, 1.U), io.v2(15,0)),
    VSew.e32 -> Cat(Fill(XLEN - 32, 1.U), io.v2(31,0)),
    VSew.e64 -> io.v2,
  ))
  val in1 = v1(63, 0) & v2
  val in2 = v1(127, 64)

  val out64 = in1 & in2
  val out32 = out64(31,0) & out64(63,32)
  val out16 = out32(15,0) & out32(31,0)
  val out8 = out16(7,0) & out16(15,8)

  io.vout := MuxLookup(io.vsew, 0.U)(Seq(
    VSew.e8 -> Cat(0.U((XLEN - 8).W), out8),
    VSew.e16 -> Cat(0.U((XLEN - 16).W), out16),
    VSew.e32 -> Cat(0.U((XLEN - 32).W), out32),
    VSew.e64 -> out64
  ))
}

class VRedXOR extends Module with ReductionParam {
  val io = IO(new Bundle {
    val vsew = Input(UInt(2.W))
    val v1 = Input(UInt(VLEN.W))
    val v2 = Input(UInt(XLEN.W))
    val vout = Output(UInt(XLEN.W))
  })
  val v1 = io.v1
  val v2 = MuxLookup(io.vsew, 0.U)(Seq(
    VSew.e8 -> Cat(Fill(XLEN - 8, 1.U), io.v2(7,0)),
    VSew.e16 -> Cat(Fill(XLEN - 16, 1.U), io.v2(15,0)),
    VSew.e32 -> Cat(Fill(XLEN - 32, 1.U), io.v2(31,0)),
    VSew.e64 -> io.v2,
  ))
  val in1 = v1(63, 0) & v2
  val in2 = v1(127, 64)

  val out64 = in1 & in2
  val out32 = out64(31,0) & out64(63,32)
  val out16 = out32(15,0) & out32(31,0)
  val out8 = out16(7,0) & out16(15,8)

  io.vout := MuxLookup(io.vsew, 0.U)(Seq(
    VSew.e8 -> Cat(0.U((XLEN - 8).W), out8),
    VSew.e16 -> Cat(0.U((XLEN - 16).W), out16),
    VSew.e32 -> Cat(0.U((XLEN - 32).W), out32),
    VSew.e64 -> out64
  ))
}

class CarryAdder_xy(val xbyte: Int = 1, val ycarry: Int = 0) extends Module {
  val io = IO(new Bundle() {
    val v1 = Input(Vec(xbyte, UInt(8.W)))
    val v2 = Input(Vec(xbyte, UInt(8.W)))
    val v1_ucarry = Input(Vec(xbyte, UInt(ycarry.W)))
    val v1_scarry = Input(Vec(xbyte, UInt(ycarry.W)))
    val v2_ucarry = Input(Vec(xbyte, UInt(ycarry.W)))
    val v2_scarry = Input(Vec(xbyte, UInt(ycarry.W)))
    val vout = Output(Vec(xbyte, UInt(8.W)))
    val vout_ucarry = Output(Vec(xbyte, UInt((ycarry+1).W)))
    val vout_scarry = Output(Vec(xbyte, UInt((ycarry+1).W)))
    val vout_nocarry = Output(Vec(xbyte, UInt(8.W)))
  })

  val v1 = io.v1
  val v2 = io.v2
  val v1_ucarry = io.v1_ucarry
  val v1_scarry = io.v1_scarry
  val v2_ucarry = io.v2_ucarry
  val v2_scarry = io.v2_scarry
  val vout = io.vout
  val vout_ucarry = io.vout_ucarry
  val vout_scarry = io.vout_scarry
  val carry = Wire(Vec(xbyte, UInt(1.W)))
  val out = Wire(UInt((8 * xbyte).W))

  for (i <- 0 until xbyte) {
    vout(i) := (v1(i) +& v2(i))(7,0)
    carry(i) := (v1(i) +& v2(i))(8)
    if(ycarry > 0) {
      vout_ucarry(i) := Cat(0.U(1.W), v1_ucarry(i)) + Cat(0.U(1.W), v2_ucarry(i)) + carry(i)
      vout_scarry(i) := Cat(v1_scarry(i)(ycarry-1), v1_scarry(i)) + Cat(v2_scarry(i)(ycarry-1), v2_scarry(i)) + carry(i)
    } else {
      vout_ucarry(i) := carry(i)
      vout_scarry(i) := v1(i)(7) + v2(i)(7) + carry(i)
    }
  }
  out := Cat(io.v1.reverse) + Cat(io.v2.reverse)
  for (i <- 0 until xbyte) {
    io.vout_nocarry(i) := out(8*i+7, 8*i)
  }
}

class VRedWSUM extends Module with ReductionParam {
  val io = IO(new Bundle {
    val widen = Input(Bool())
    val signed = Input(Bool())
    val vsew = Input(UInt(2.W))
    val v1 = Input(UInt(128.W))
    val v2 = Input(UInt(64.W))
    val vout = Output(UInt(64.W))
  })
  val v1_64 = io.v1(63,0).asTypeOf(Vec(8, UInt(8.W)))
  val v2_64 = io.v1(127,64).asTypeOf(Vec(8, UInt(8.W)))
  val scalar = io.v2

  val out64 = Wire(UInt(64.W))
  val out32 = Wire(UInt(32.W))
  val out16 = Wire(UInt(16.W))
  val out8 = Wire(UInt(8.W))
  val out32_carry = Wire(UInt(2.W))
  val out16_carry = Wire(UInt(3.W))
  val out8_carry = Wire(UInt(4.W))

  val adder64 = Module(new CarryAdder_xy(8, 0))
  adder64.io.v1 := v1_64
  adder64.io.v2 := v2_64
  adder64.io.v1_ucarry := DontCare
  adder64.io.v1_scarry := DontCare
  adder64.io.v2_ucarry := DontCare
  adder64.io.v2_scarry := DontCare
  val v1_32 = adder64.io.vout.take(4)
  val v2_32 = adder64.io.vout.drop(4)
  val ucarry_8x1 = adder64.io.vout_ucarry
  val scarry_8x1 = adder64.io.vout_scarry

  val adder32 = Module(new CarryAdder_xy(4,1))
  adder32.io.v1 := v1_32
  adder32.io.v2 := v2_32
  adder32.io.v1_ucarry := ucarry_8x1.take(4)
  adder32.io.v2_ucarry := ucarry_8x1.drop(4)
  adder32.io.v1_scarry := scarry_8x1.take(4)
  adder32.io.v2_scarry := scarry_8x1.drop(4)
  val v1_16 = RegNext(VecInit(adder32.io.vout.take(2)))
  val v2_16 = RegNext(VecInit(adder32.io.vout.drop(2)))
  val ucarry_4x2 = RegNext(adder32.io.vout_ucarry)
  val scarry_4x2 = RegNext(adder32.io.vout_scarry)

  val adder16 = Module(new CarryAdder_xy(2,2))
  adder16.io.v1 := v1_16
  adder16.io.v2 := v2_16
  adder16.io.v1_ucarry := ucarry_4x2.take(2)
  adder16.io.v2_ucarry := ucarry_4x2.drop(2)
  adder16.io.v1_scarry := scarry_4x2.take(2)
  adder16.io.v2_scarry := scarry_4x2.drop(2)
  val v1_8 = adder16.io.vout.take(1)
  val v2_8 = adder16.io.vout.drop(1)
  val ucarry_2x3 = adder16.io.vout_ucarry
  val scarry_2x3 = adder16.io.vout_scarry

  val adder8 = Module(new CarryAdder_xy(1,3))
  adder8.io.v1 := v1_8
  adder8.io.v2 := v2_8
  adder8.io.v1_ucarry := ucarry_2x3.take(1)
  adder8.io.v2_ucarry := ucarry_2x3.drop(1)
  adder8.io.v1_scarry := scarry_2x3.take(1)
  adder8.io.v2_scarry := scarry_2x3.drop(1)
  val ucarry_1x4 = adder8.io.vout_ucarry
  val scarry_1x4 = adder8.io.vout_scarry

  out8 := adder8.io.vout(0)
  out8_carry := Mux(io.signed, scarry_1x4(0), ucarry_1x4(0))
  out16 := (Cat(Mux(io.signed, scarry_2x3(1), ucarry_2x3(1)), Cat(v2_8.reverse), Cat(v1_8.reverse)) + Cat(ucarry_2x3(0), 0.U(8.W)))(15,0)
  out16_carry := (Cat(Mux(io.signed, scarry_2x3(1), ucarry_2x3(1)), Cat(v2_8.reverse), Cat(v1_8.reverse)) + Cat(ucarry_2x3(0), 0.U(8.W)))(18,16)
  out32 := (Cat(Mux(io.signed, scarry_4x2(3), ucarry_4x2(3)), Cat(v2_16.reverse), Cat(v1_16.reverse)) +
    Cat(ucarry_4x2(2), 0.U(6.W), ucarry_4x2(1), 0.U(6.W), ucarry_4x2(0), 0.U(8.W)))(31,0)
  out32_carry := (Cat(Mux(io.signed, scarry_4x2(3), ucarry_4x2(3)), Cat(v2_16.reverse), Cat(v1_16.reverse)) +
    Cat(ucarry_4x2(2), 0.U(6.W), ucarry_4x2(1), 0.U(6.W), ucarry_4x2(0), 0.U(8.W)))(33,32)
  out64 := Cat(adder64.io.vout_nocarry.reverse)

  val res64 = RegNext(out64 + scalar)
  val res32 = out32 + scalar(31,0)
  val res32_widen = Cat(Fill(30, out32_carry(1)), out32_carry, out32) + scalar
  val res16 = out16 + scalar(15,0)
  val res16_widen = Cat(Fill(13, out16_carry(2)), out16_carry, out16) + scalar(31,0)
  val res8 = out8 + scalar(7, 0)
  val res8_widen = Cat(Fill(4, out8_carry(3)), out8_carry, out8) + scalar(15, 0)

  io.vout := MuxLookup(io.vsew, 0.U)(Seq(
    VSew.e8 -> Mux(io.widen, res8_widen.asUInt.pad(64), res8.asUInt.pad(64)),
    VSew.e16 -> Mux(io.widen, res16_widen.asUInt.pad(64), res16.asUInt.pad(64)),
    VSew.e32 -> Mux(io.widen, res32_widen.asUInt.pad(64), res32.asUInt.pad(64)),
    VSew.e64 -> res64.asUInt
  ))
}

class VRedComp extends Module {
  val io = IO(new Bundle {
    val max = Input(Bool())
    val signed = Input(Bool())
    val vsew = Input(UInt(2.W))
    val v1 = Input(UInt(128.W))
    val v2 = Input(UInt(64.W))
    val vout = Output(UInt(64.W))
  })
  val max = io.max
  val signed = io.signed
  val v1 = io.v1
  val v2 = io.v2

  val max_reg = RegNext(max)
  val signed_reg = RegNext(signed)
  val vsew_reg = RegNext(io.vsew)

  // sew64 max/min
  val vd_max_sew64 = Wire(UInt(64.W))
  // stage 1
  val compare_3to1_sew64 = Module(new compare_3to1(w = 64))
  compare_3to1_sew64.io.a := v1(63, 0)
  compare_3to1_sew64.io.b := v1(127, 64)
  compare_3to1_sew64.io.c := v2
  compare_3to1_sew64.io.max := max
  compare_3to1_sew64.io.signed := signed
  vd_max_sew64 := compare_3to1_sew64.io.d
  // stage 2
  val vd_max_sew64_s2_r = RegNext(vd_max_sew64)

  // sew32 max/min
  // stage 1
  val vd0_max_sew32 = Wire(Vec(2, UInt(32.W)))
  val vd1_max_sew32 = Wire(UInt(32.W))

  val compare_3to1_sew32 = Module(new compare_3to1(w = 32))
  compare_3to1_sew32.io.a := v1(31, 0)
  compare_3to1_sew32.io.b := v1(63, 32)
  compare_3to1_sew32.io.c := v1(95, 64)
  compare_3to1_sew32.io.max := max
  compare_3to1_sew32.io.signed := signed
  vd0_max_sew32(0) := compare_3to1_sew32.io.d

  val compare0_2to1_sew32 = Module(new compare_2to1(w = 32))
  compare0_2to1_sew32.io.a := v1(127, 96)
  compare0_2to1_sew32.io.b := v2(31, 0)
  compare0_2to1_sew32.io.max := max
  compare0_2to1_sew32.io.signed := signed
  vd0_max_sew32(1) := compare0_2to1_sew32.io.c

  val v32_reg = RegNext(vd0_max_sew32)
  //stage 2
  val compare1_2to1_sew32 = Module(new compare_2to1(w = 32))
  compare1_2to1_sew32.io.a := v32_reg(0)
  compare1_2to1_sew32.io.b := v32_reg(1)
  compare1_2to1_sew32.io.max := max_reg
  compare1_2to1_sew32.io.signed := signed_reg
  vd1_max_sew32 := compare1_2to1_sew32.io.c

  // sew16 max/min
  // stage 1
  val vd0_max_sew16 = Wire(Vec(3, UInt(16.W)))
  val vd1_max_sew16 = Wire(UInt(16.W))
  val in0_max_sew16 = Cat(v2(15, 0), v1(127, 0))

  for (i <- 0 until 3) {
    val compare_3to1_sew16 = Module(new compare_3to1(w = 16))
    compare_3to1_sew16.io.a := in0_max_sew16(48 * i + 15, 48 * i + 0)
    compare_3to1_sew16.io.b := in0_max_sew16(48 * i + 31, 48 * i + 16)
    compare_3to1_sew16.io.c := in0_max_sew16(48 * i + 47, 48 * i + 32)
    compare_3to1_sew16.io.max := max
    compare_3to1_sew16.io.signed := signed
    vd0_max_sew16(i) := compare_3to1_sew16.io.d
  }
  val v16_reg = RegNext(vd0_max_sew16)
  // stage 2
  val compare1_3to1_sew16 = Module(new compare_3to1(w = 16))
  compare1_3to1_sew16.io.a := v16_reg(0)
  compare1_3to1_sew16.io.b := v16_reg(1)
  compare1_3to1_sew16.io.c := v16_reg(2)
  compare1_3to1_sew16.io.max := max_reg
  compare1_3to1_sew16.io.signed := signed_reg
  vd1_max_sew16 := compare1_3to1_sew16.io.d

  // sew8 max/min
  // stage 1
  val vd0_max_sew8 = Wire(Vec(6, UInt(8.W)))
  val vd1_max_sew8 = Wire(Vec(2, UInt(8.W)))
  val vd2_max_sew8 = Wire(UInt(8.W))
  val in2_max_sew8 = Cat(vd1_max_sew8.reverse)

  for (i <- 0 until 5) {
    val compare_3to1_sew8 = Module(new compare_3to1(w = 8))
    compare_3to1_sew8.io.a := v1(24 * i + 7, 24 * i + 0)
    compare_3to1_sew8.io.b := v1(24 * i + 15, 24 * i + 8)
    compare_3to1_sew8.io.c := v1(24 * i + 23, 24 * i + 16)
    compare_3to1_sew8.io.max := max
    compare_3to1_sew8.io.signed := signed
    vd0_max_sew8(i) := compare_3to1_sew8.io.d
  }

  val compare0_2to1_sew8 = Module(new compare_2to1(w = 8))
  compare0_2to1_sew8.io.a := v1(127, 120)
  compare0_2to1_sew8.io.b := v2(7, 0)
  compare0_2to1_sew8.io.max := max
  compare0_2to1_sew8.io.signed := signed
  vd0_max_sew8(5) := compare0_2to1_sew8.io.c

  val v8_reg = RegNext(vd0_max_sew8)
  // stage 2
  for (i <- 0 until 2) {
    val compare_3to1_sew8 = Module(new compare_3to1(w = 8))
    compare_3to1_sew8.io.a := v8_reg(3 * i)
    compare_3to1_sew8.io.b := v8_reg(3 * i + 1)
    compare_3to1_sew8.io.c := v8_reg(3 * i + 2)
    compare_3to1_sew8.io.max := max_reg
    compare_3to1_sew8.io.signed := signed_reg
    vd1_max_sew8(i) := compare_3to1_sew8.io.d
  }

  val compare1_2to1_sew8 = Module(new compare_2to1(w = 8))
  compare1_2to1_sew8.io.a := in2_max_sew8(15, 8)
  compare1_2to1_sew8.io.b := in2_max_sew8(7, 0)
  compare1_2to1_sew8.io.max := max_reg
  compare1_2to1_sew8.io.signed := signed_reg
  vd2_max_sew8 := compare1_2to1_sew8.io.c

  io.vout := vd_max_sew64_s2_r
  when(vsew_reg === 0.U) {
    io.vout := vd2_max_sew8
  }.elsewhen(vsew_reg === 1.U) {
    io.vout := vd1_max_sew16
  }.elsewhen(vsew_reg === 2.U) {
    io.vout := vd1_max_sew32
  }
}
/*
class Adder_xb(w: Int) extends Module {
  val io = IO(new Bundle() {
    val in1 = Input(UInt(w.W))
    val in2 = Input(UInt(w.W))
    val cin = Input(UInt(1.W))
    val cout = Output(UInt(1.W))
  })

  private val bits = Cat(0.U(1.W), io.in1, io.cin) + Cat(0.U(1.W), io.in2, io.cin)
  io.cout := bits(w + 1)
}

class compare_2to1(w: Int) extends Module {
  val io = IO(new Bundle() {
    val a = Input(UInt(w.W))
    val b = Input(UInt(w.W))
    val max = Input(Bool())
    val signed = Input(Bool())
    val c = Output(UInt(w.W))
  })

  // a-b
  val b_inv = ~io.b
  val cout = Wire(Bool())
  val less = Wire(Bool())

  val adder_xb = Module(new Adder_xb(w = w))
  adder_xb.io.in1 := b_inv
  adder_xb.io.in2 := io.a
  adder_xb.io.cin := 1.U
  cout := adder_xb.io.cout
  less := Mux(io.signed, io.a(w - 1) ^ b_inv(w - 1) ^ cout, !cout)
  io.c := Mux(less === io.max, io.b, io.a)
}

class compare_3to1(w: Int) extends Module {
  val io = IO(new Bundle() {
    val a = Input(UInt(w.W))
    val b = Input(UInt(w.W))
    val c = Input(UInt(w.W))
    val max = Input(Bool())
    val signed = Input(Bool())
    val d = Output(UInt(w.W))
  })

  // a-b, a-c, b-c
  val vs_hi = Cat(io.a, io.a, io.b)
  val vs_lo = Cat(io.b, io.c, io.c)
  val vs_lo_inv = ~vs_lo
  val cout = Wire(Vec(3, Bool()))
  val less = Wire(Vec(3, Bool()))

  for (i <- 0 until 3) {
    val adder_xb = Module(new Adder_xb(w = w))
    adder_xb.io.in1 := vs_lo_inv(w * (i + 1) - 1, w * i)
    adder_xb.io.in2 := vs_hi(w * (i + 1) - 1, w * i)
    adder_xb.io.cin := 1.U
    cout(i) := adder_xb.io.cout
    less(i) := Mux(io.signed, vs_hi(w * (i + 1) - 1) ^ vs_lo_inv(w * (i + 1) - 1) ^ cout(i), !cout(i))
  }

  io.d := 0.U
  when((less(2) && less(1) && !io.max) || (!less(2) && !less(1) && io.max)) {
    io.d := io.a
  }.elsewhen((!less(2) && less(0) && !io.max) || (less(2) && !less(0) && io.max)) {
    io.d := io.b
  }.elsewhen((!less(1) && !less(0) && !io.max) || (less(1) && less(0) && io.max)) {
    io.d := io.c
  }
}
*/
class Test extends Module {
  val io = IO(new Bundle(){
    val a = Input(UInt(8.W))
    val b = Output(UInt(8.W))
  })
  io.b := io.a + 1.U
}

object VerilogNewRed extends App {
  println("Generating the VPU Reduction hardware")
  emitVerilog(new NewReduction(), Array("--target-dir", "backend"))
}
