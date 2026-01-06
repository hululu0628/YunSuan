package yunsuan.fpu.falu

import chisel3._
import chisel3.util._
import yunsuan.fpu._
import yunsuan.fpu.falu._
import yunsuan.fpu.fmul.FMULToFADDCtrlBundle

class FCOMP(val totalWidth: Int) extends Module with FloatParams {
  val io = IO(new Bundle() {
    val fpA = Input(UInt(totalWidth.W))
    val fpB = Input(UInt(totalWidth.W))
		val isMax = Input(Bool())
    val outRes = Output(UInt(totalWidth.W))
		val outFlags = Output(UInt(flagsWidth.W))
	})
	val fpA = io.fpA
	val fpB = io.fpB
	val isMax = io.isMax
	val signA = io.fpA.head(1).asBool
	val signB = io.fpB.head(1).asBool
	val magA = io.fpA.tail(1) // magnitude = exponent + fraction
	val magB = io.fpB.tail(1)
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
	val diffSign = signA ^ signB
	val AGBlogic = Mux(diffSign, signB, Mux(signA ^ isMax, magAGB, !magAGB))

	val fpAIsNaN = expAIsAllOne & fracAIsZero
	val fpBIsNaN = expBIsAllOne & fracBIsZero
	val bothNaN = fpAIsNaN & fpBIsNaN
	val oneNaN = fpAIsNaN ^ fpBIsNaN
	io.outRes := Mux(bothNaN,
		cNaN.U,
		Mux(oneNaN, 
			Mux(fpAIsNaN, fpB, fpA),
			Mux(AGBlogic, fpA, fpB)
		)
	)

	val flagsNV = (!quietBitA | !quietBitB) && bothNaN
	val flagsDZ = false.B
	val flagsOF = false.B
	val flagsUF = false.B
	val flagsNX = false.B
	io.outFlags := Cat(flagsNV, flagsDZ, flagsOF, flagsUF, flagsNX)
}
