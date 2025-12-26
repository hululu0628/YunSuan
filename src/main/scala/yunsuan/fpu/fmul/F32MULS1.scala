package yunsuan.fpu.fmul
import chisel3._
import chisel3.util._
import yunsuan.fpu._

class F32MULS1(val totalWidth: Int, val version: Int = 0) extends Module with FloatParams {
  override def desiredName = (this.getClass.getName + s"_$version").split("\\.").last
  val io = IO(new Bundle() {
    val inFromS0 = Input(new FMULS0ToS1Bundle(totalWidth))
    val outToS2 = Output(new FMULS1ToS2Bundle(totalWidth))
    val outResToFADD = Output(UInt((totalWidth + decimalWidth).W))
    val outRes = Output(UInt(totalWidth.W))
    val outFlags = Output(UInt(flagsWidth.W))
  })
  val isLeftShift = io.inFromS0.needLeftShift
  val fracMul = io.inFromS0.out_sum
  val leftShiftOverFlowMask = (((1.U << (2 * decimalWidth - 1)) >> io.inFromS0.shiftBits) >> (io.inFromS0.expc === 0.U)).asUInt
  val fracOverFlowLeftShift = (fracMul & leftShiftOverFlowMask)(2 * decimalWidth - 1, significandWidth).orR && !io.inFromS0.leftShiftNoOverFlow
  val fracOverFlowRightShift = fracMul.head(1).asBool
  val fracOverFlow = isLeftShift && fracOverFlowLeftShift || !isLeftShift && (io.inFromS0.shiftBits === 1.U) && fracOverFlowRightShift
  val fracMulLeftShift = Wire(UInt((2 * decimalWidth).W))
  val fracMulRightShift = Wire(UInt((2 * decimalWidth).W))
  val rightShiftBits = Mux(isLeftShift, 0.U, io.inFromS0.shiftBits)
  val rightShiftStickyMask = (Fill(totalWidth - 1, 1.U) << rightShiftBits)(2 * (totalWidth - 1) - 1, totalWidth - 1)
  val rightShiftSticky = (rightShiftStickyMask & fracMul(totalWidth - 2, 0)).orR
  io.outToS2.sticky := rightShiftSticky
  fracMulLeftShift := fracMul << io.inFromS0.shiftBits
  fracMulRightShift := fracMul >> io.inFromS0.shiftBits
  val outFracRaw = Mux(isLeftShift, fracMulLeftShift, fracMulRightShift)
  val outFrac = Mux(isLeftShift && fracOverFlow && (io.inFromS0.expc =/= 0.U), outFracRaw(2 * significandWidth, 0), Cat(outFracRaw(2 * significandWidth - 1, 0), 0.U))
  val exp = io.inFromS0.expc
  val expAdd1 = io.inFromS0.expc + 1.U
  val expIsRoundUp = fracOverFlow

  val expIsOverFlow = exp.andR || exp(exponentWidth - 1, 1).andR && fracOverFlow
  val outExp = Mux(expIsOverFlow, Fill(exponentWidth, 1.U), Mux(expIsRoundUp, expAdd1, exp))
  io.outResToFADD := Cat(io.inFromS0.sign, outExp, outFrac)
  io.outToS2.resForRounding := Cat(io.inFromS0.sign, outExp, outFrac)
  io.outToS2.rm := io.inFromS0.rm
  io.outToS2.resIsNAN := io.inFromS0.resIsNAN
  io.outToS2.resIsZero := io.inFromS0.resIsZero
  io.outToS2.resIsInf := io.inFromS0.resIsInf
  io.outToS2.flagsNV := io.inFromS0.flagsNV

  dontTouch(fracMul)
  dontTouch(leftShiftOverFlowMask)
  dontTouch(fracOverFlow)
  dontTouch(fracMulLeftShift)
  dontTouch(fracMulRightShift)
  dontTouch(outFracRaw)
  dontTouch(outFrac)
  dontTouch(exp)
  dontTouch(expAdd1)
  dontTouch(expIsRoundUp)
  dontTouch(expIsOverFlow)


  val rm = io.inFromS0.rm
  val sign = io.outToS2.resForRounding.head(signWidth).asBool
  val exp_s2 = io.outToS2.resForRounding.tail(signWidth).head(exponentWidth)
  val frac = io.outToS2.resForRounding.tail(signWidth + exponentWidth).head(significandWidth)
  val guard = frac(0)
  val round = io.outToS2.resForRounding(significandWidth)
  val sticky = io.outToS2.resForRounding(significandWidth - 1, 0).orR || io.outToS2.sticky
  val guard_uf = round
  val round_uf = io.outToS2.resForRounding(significandWidth - 1)
  val sticky_uf = io.outToS2.resForRounding(significandWidth - 2, 0).orR || io.outToS2.sticky
  val expAdd1_s2 = exp_s2 + 1.U
  val fracAdd1 = frac + 1.U
  val isRNE = rm === RNE.U
  val isRTZ = rm === RTZ.U
  val isRDN = rm === RDN.U
  val isRUP = rm === RUP.U
  val isRMM = rm === RMM.U
  val roundUp = isRNE & round & (guard | sticky) |
    isRDN & sign & (round | sticky) |
    isRUP & (!sign) & (round | sticky) |
    isRMM & round
  val roundUp_uf = isRNE & round_uf & (guard_uf | sticky_uf) |
    isRDN & sign & (round_uf | sticky_uf) |
    isRUP & (!sign) & (round_uf | sticky_uf) |
    isRMM & round_uf
  val fracRoundUp = Mux(roundUp, fracAdd1, frac)
  val expIsRoundUp_s2 = frac.andR & roundUp
  val expIsRoundUp_uf = frac.andR & guard_uf & roundUp_uf
  val expIsOverflow = Mux(expIsRoundUp_s2, exp_s2(exp_s2.getWidth - 1, 1).andR, exp_s2.andR)
  val expForOverflow = Cat(Fill(exponentWidth - 1, 1.U), !(isRTZ | !sign & isRDN | sign & isRUP))
  val fracForOverflow = Fill(significandWidth, isRTZ | !sign & isRDN | sign & isRUP)
  val expFinal = Mux(expIsOverflow, expForOverflow, Mux(expIsRoundUp_s2, expAdd1_s2, exp_s2))
  val fracFinal = Mux(expIsOverflow, fracForOverflow, fracRoundUp)
  val zero = Cat(sign, 0.U((totalWidth - 1).W))
  val inf = Cat(sign, Fill(exponentWidth, 1.U), 0.U(significandWidth.W))
  val isConst = io.outToS2.resIsNAN || io.outToS2.resIsZero || io.outToS2.resIsInf
  val const = Mux1H(
    Seq(io.outToS2.resIsNAN, io.outToS2.resIsZero, io.outToS2.resIsInf),
    Seq(cNaN.U, zero, inf)
  )
  io.outRes := Mux(isConst, const, Cat(sign, expFinal, fracFinal))
  val flagsNV = io.outToS2.flagsNV
  val flagsDZ = false.B
  val flagsOF = expIsOverflow
  val resIsZero = !expFinal.orR && !fracFinal.orR && !sticky || io.outToS2.resIsZero
  val flagsNX = expIsOverflow || (round || sticky)
  val flagsUF = (exp_s2 === 0.U) && !expIsRoundUp_uf && flagsNX
  io.outFlags := 0.U // Mux(isConst, Cat(flagsNV, 0.U((flagsWidth - 1).W)), Cat(0.U, flagsDZ, flagsOF, flagsUF, flagsNX))

  dontTouch(sign)
  dontTouch(rm)
  dontTouch(frac)
  dontTouch(fracAdd1)
  dontTouch(roundUp)
  dontTouch(guard)
  dontTouch(round)
  dontTouch(sticky)
  dontTouch(fracFinal)
  dontTouch(exp_s2)
  dontTouch(expAdd1_s2)
  dontTouch(expIsRoundUp_s2)
  dontTouch(expIsOverflow)
  dontTouch(expForOverflow)
  dontTouch(fracForOverflow)
  dontTouch(expFinal)
  dontTouch(fracFinal)
}