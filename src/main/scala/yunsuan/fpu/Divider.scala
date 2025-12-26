package yunsuan.fpu
import chisel3._
import chisel3.util._
import yunsuan.vector._
import yunsuan.fpu.fqrt._
import yunsuan.vector.vfsqrt.fpsqrt_vector_r16

class Divider() extends Module {
  val dividendWidth = 55
  val divisorWidth  = 55
  val quotientWidth = 55
  val perIterationQuotientNum = 1
  val quotientSetNum = 2 ^ perIterationQuotientNum
  assert(quotientWidth % perIterationQuotientNum == 0, "quotientWidth % perIterationQuotientNum should equal 0")
  val iterationsNum = quotientWidth / perIterationQuotientNum
  val io = IO(new Bundle {
    val dividend = Input(UInt(dividendWidth.W))
    val divisor = Input(UInt(divisorWidth.W))
    val quotient = Output(UInt(quotientWidth.W))
  })
  val dividendVec = Wire(Vec(iterationsNum + 1, UInt(dividendWidth.W)))
  val quotientVec = Wire(Vec(iterationsNum + 1, UInt(divisorWidth.W)))
  dividendVec(0) := io.dividend
  quotientVec(0) := 0.U
  for (i <- 0 until iterationsNum){
    val dividend = dividendVec(i)
    val divisor = io.divisor
    val q_this = Wire(UInt(perIterationQuotientNum.W))
    q_this := 0.U
    for (i <- 0 until quotientSetNum){
      when(dividend >= (i.U * divisor)){
        q_this := i.U
      }
    }
    dividendVec(i + 1) := dividend - q_this * divisor
    quotientVec(i + 1) := (quotientVec(i) << perIterationQuotientNum).asUInt + q_this
  }
  val rem = dividendVec(iterationsNum)
  io.quotient := quotientVec(iterationsNum)
}