package yunsuan.fpu

import chisel3._
import chisel3.util._
import yunsuan.vector.LZD

class AndReduction(width: Int) extends Module {
  println(this.getClass.getName)
  override def desiredName = (this.getClass.getName + s"_$width").split("\\.").last
  val io = IO(new Bundle {
    val opa = Input(UInt(width.W))
    val res = Output(Bool())
  })
  io.res := io.opa.andR
}

class AddBasic(width: Int) extends Module {
  println(this.getClass.getName)
  override def desiredName = (this.getClass.getName + s"_$width").split("\\.").last
  val io = IO(new Bundle {
    val opa = Input(UInt(width.W))
    val opb = Input(UInt(width.W))
    val res = Output(UInt(width.W))
  })
  io.res := io.opa + io.opb
}

class AddCarry(width: Int) extends Module {
  println(this.getClass.getName)
  override def desiredName = (this.getClass.getName + s"_$width").split("\\.").last
  val io = IO(new Bundle {
    val opa = Input(UInt(width.W))
    val opb = Input(UInt(width.W))
    val opc = Input(Bool())
    val res = Output(UInt(width.W))
  })
  io.res := io.opa + io.opb + io.opc
}

class AddFullCarry(width: Int) extends Module {
  println(this.getClass.getName)
  override def desiredName = (this.getClass.getName + s"_$width").split("\\.").last
  val io = IO(new Bundle {
    val opa = Input(UInt(width.W))
    val opb = Input(UInt(width.W))
    val opc = Input(UInt(width.W))
    val res = Output(UInt(width.W))
  })
  io.res := io.opa + io.opb + io.opc
}

class Sub(width: Int) extends Module {
  println(this.getClass.getName)
  override def desiredName = (this.getClass.getName + s"_$width").split("\\.").last
  val io = IO(new Bundle {
    val opa = Input(UInt(width.W))
    val opb = Input(UInt(width.W))
    val res = Output(UInt(width.W))
  })
  io.res := io.opa - io.opb
}

class Mul(width: Int) extends Module {
  println(this.getClass.getName)
  override def desiredName = (this.getClass.getName + s"_$width").split("\\.").last
  val io = IO(new Bundle {
    val opa = Input(UInt(width.W))
    val opb = Input(UInt(width.W))
    val res = Output(UInt((2*width).W))
  })
  io.res := io.opa * io.opb
}

class MulPipe(width: Int) extends Module {
  println(this.getClass.getName)
  override def desiredName = (this.getClass.getName + s"_$width").split("\\.").last
  val io = IO(new Bundle {
    val opa = Input(UInt(width.W))
    val opb = Input(UInt(width.W))
    val res = Output(UInt((2*width).W))
  })
  io.res := RegNext(RegNext(RegNext(RegNext(RegNext(RegNext(io.opa))) * RegNext(RegNext(RegNext(io.opb))))))
}

class RShift(width: Int) extends Module {
  println(this.getClass.getName)
  override def desiredName = (this.getClass.getName + s"_$width").split("\\.").last
  val io = IO(new Bundle {
    val opa = Input(UInt(512.W))
    val opb = Input(UInt(width.W))
    val res = Output(UInt(512.W))
  })
  io.res := io.opa >> io.opb
}

class Greater1OPT(width: Int) extends Module {
  println(this.getClass.getName)
  override def desiredName = (this.getClass.getName + s"_$width").split("\\.").last
  val io = IO(new Bundle {
    val opa = Input(UInt(width.W))
    val opb = Input(UInt(width.W))
    val res = Output(Bool())
  })
  io.res := ((Cat(io.opb(width-2,0) | (~io.opa(width-2,0)).asUInt,0.U)) ^ (io.opb ^ io.opa)).andR
}

class Greater1(width: Int) extends Module {
  println(this.getClass.getName)
  override def desiredName = (this.getClass.getName + s"_$width").split("\\.").last
  val io = IO(new Bundle {
    val opa = Input(UInt(width.W))
    val opb = Input(UInt(width.W))
    val res = Output(Bool())
  })
  io.res := (io.opb - io.opa) === 1.U
}

class ASubBSub1(width: Int) extends Module {
  println(this.getClass.getName)
  override def desiredName = (this.getClass.getName + s"_$width").split("\\.").last
  val io = IO(new Bundle {
    val opa = Input(UInt(width.W))
    val opb = Input(UInt(width.W))
    val res = Output(UInt(width.W))
  })
  io.res := io.opa - io.opb - 1.U
}

class ASubBSub1Fast(width: Int) extends Module {
  println(this.getClass.getName)
  override def desiredName = (this.getClass.getName + s"_$width").split("\\.").last
  val io = IO(new Bundle {
    val opa = Input(UInt(width.W))
    val opb = Input(UInt(width.W))
    val res = Output(UInt(width.W))
  })
  io.res := io.opa + ~io.opb
}

class LZDMy(width: Int) extends Module {
  println(this.getClass.getName)
  override def desiredName = (this.getClass.getName + s"_$width").split("\\.").last
  val io = IO(new Bundle {
    val opa = Input(UInt(width.W))
    val res = Output(UInt(width.U.getWidth.W))
  })

  def lzd(in: UInt): UInt = {
    val w = in.getWidth
    if (w == 1) {
      (~in(0)).asUInt
    }
    else if (w == 2) {
      Cat(~in.orR, (~in(1)).asUInt & in(0))
    }
    else {
      val h = 1 << (log2Ceil(w) - 1)
      val nh = lzd(in.head(h))
      val nl = lzd(in.tail(h))
      val vh = nh.head(1).asBool
      val vl = nl.head(1).asBool
      if (h == w - h)
        Cat(vh & vl, vh & ~vl, Mux(vh, nl.tail(1), nh.tail(1)))
      else
        Cat(vh, Mux(vh, nl, nh.tail(1)))
    }
  }

  io.res := lzd(io.opa)
}

class Mux1HSelFilled(num: Int) extends Module {
  override def desiredName = (this.getClass.getName + s"_$num").split("\\.").last
  val io = IO(new Bundle {
    val opa = Input(Vec(num, UInt(64.W)))
    val sel = Input(Vec(num, UInt(64.W)))
    val res = Output(UInt(64.W))
  })
  io.res := io.opa.zip(io.sel).map{case (o, s) =>
    o & s
  }.reduce(_ | _)
}

class Mux1HSelOH(num: Int) extends Module {
  override def desiredName = (this.getClass.getName + s"_$num").split("\\.").last
  val io = IO(new Bundle {
    val opa = Input(Vec(num, UInt(64.W)))
    val sel = Input(Vec(num, Bool()))
    val res = Output(UInt(64.W))
  })
  io.res := Mux1H(io.sel, io.opa)
}


class IBufferTest(num: Int) extends Module {
  override def desiredName = (this.getClass.getName + s"_$num").split("\\.").last
  val io = IO(new Bundle {
    val data_inst = Input(Vec(64, UInt(32.W)))
    val data_isVset = Input(Vec(64, Bool()))
    val addr = Input(Vec(8, UInt(6.W)))
    val spec_vtype = Input(UInt(6.W))
    val res_inst = Output(Vec(8, UInt(32.W)))
    val res_vtype = Output(Vec(8, UInt(6.W)))
  })
  val readInst = VecInit(io.addr.map(x => io.data_inst(x)))
  val readIsVset = VecInit(io.addr.map(x => io.data_isVset(x)))
  val readVtype = VecInit(io.addr.map(x => io.data_inst(x)(5, 0)))
  io.res_inst := readInst
  val cond = true.B +: io.data_isVset
  val vtype = io.spec_vtype +: readVtype
  for (i <- 0 until 8) {
    io.res_vtype(i) := PriorityMux(cond.zip(vtype).take(i+1).reverse)
  }
}

class VAddTest(num: Int) extends Module {
  override def desiredName = (this.getClass.getName + s"_$num").split("\\.").last
  val io = IO(new Bundle {
    val dataIn0 = Input(Vec(8, UInt(8.W)))
    val dataIn1 = Input(Vec(8, UInt(8.W)))
    val isE8 = Input(Bool())
    val res = Output(UInt(64.W))
  })
  val src0Append = VecInit(io.dataIn0.map(x => Cat(!io.isE8, x)))
  val src1Append = VecInit(io.dataIn1.map(x => Cat(0.U, x)))
  val src0 = src0Append.asUInt(64-1+7, 0)
  val src1 = src1Append.asUInt(64-1+7, 0)
  val result0 = VecInit(Cat(0.U, (src0 + src1)).asTypeOf(src0Append).map(x => x(7, 0)))
  val result1 = Wire(Vec(8, UInt(8.W)))
  val carry = Wire(Vec(9, UInt(1.W)))
  carry(0) := 0.U
  for (i <- 0 until 8) {
    val addCarryi = io.dataIn0(i) +& io.dataIn1(i) + carry(i)
    val sumi = addCarryi(7, 0)
    val carryi = addCarryi(8)
    result1(i) := sumi
    carry(i + 1) := carryi && !io.isE8
  }
  if (num == 0) io.res := result0.asUInt
  else if (num == 1) io.res := result1.asUInt
}


class AddCatTest(splitNum: Int) extends Module {
  override def desiredName = (this.getClass.getName + s"_$splitNum").split("\\.").last
  val io = IO(new Bundle {
    val dataIn0 = Input(UInt(64.W))
    val dataIn1 = Input(UInt(64.W))
    val res = Output(UInt(64.W))
  })
  val src0Split = Wire(Vec(splitNum, UInt((64 / splitNum).W)))
  val src1Split = Wire(Vec(splitNum, UInt((64 / splitNum).W)))
  src0Split := io.dataIn0.asTypeOf(src0Split)
  src1Split := io.dataIn1.asTypeOf(src1Split)
  val src0Append = VecInit(src0Split.map(x => Cat(1.U, x)))
  val src1Append = VecInit(src1Split.map(x => Cat(0.U, x)))
  val src0 = src0Append.asUInt.tail(1)
  val src1 = src1Append.asUInt.tail(1)
  val result0 = VecInit(Cat(0.U, src0 + src1).asTypeOf(src0Append).map(x => x.tail(1)))
  io.res := result0.asUInt
}

class RegFileTest(readPortNum: Int, writePortNum: Int = 1, bankNum: Int = 1) extends Module {
  override def desiredName = (this.getClass.getName + s"_r_${readPortNum}_s_${bankNum}").split("\\.").last
  val size = 256
  val addrWidth = (size - 1).U.getWidth
  val xlen = 64
  val io = IO(new Bundle {
    val readPortAddr = Input(Vec(readPortNum, Vec(bankNum, UInt(addrWidth.W))))
    val readPortData = Output(Vec(readPortNum, Vec(bankNum, UInt((xlen / bankNum).W))))
    val writePortEn = Input(Vec(writePortNum, Bool()))
    val writePortAddr = Input(Vec(writePortNum, UInt(addrWidth.W)))
    val writePortData = Input(Vec(writePortNum, UInt(xlen.W)))
  })
  val regfile = Reg(Vec(size, UInt(xlen.W)))
  for (i <- 0 until size){
    val writeHitOH = VecInit(io.writePortEn.zip(io.writePortAddr).map(x => x._1 && (x._2 === i.U)))
   when(writeHitOH.asUInt.orR){
     regfile(i) := Mux1H(writeHitOH, io.writePortData)
   }
  }
  val regfileBank = Wire(Vec(size, Vec(bankNum, UInt((xlen / bankNum).W))))
  regfileBank := regfile.asTypeOf(regfileBank)
  for (readPort <- 0 until readPortNum){
    for (readBank <- 0 until bankNum){
      io.readPortData(readPort)(readBank) := regfileBank(io.readPortAddr(readPort)(readBank))(readBank)
    }
  }
}

class RegFileTest2(readPortNum: Int, writePortNum: Int = 1, bankNum: Int = 1) extends Module {
  override def desiredName = (this.getClass.getName + s"_r_${readPortNum}_b_${bankNum}").split("\\.").last
  val size = 256
  val addrWidth = (size - 1).U.getWidth
  val xlen = 64
  val io = IO(new Bundle {
    val readPortAddr = Input(Vec(readPortNum, UInt(addrWidth.W)))
    val readPortData = Output(Vec(readPortNum, UInt(xlen.W)))
    val writePortEn = Input(Vec(writePortNum, Bool()))
    val writePortAddr = Input(Vec(writePortNum, UInt(addrWidth.W)))
    val writePortData = Input(Vec(writePortNum, UInt(xlen.W)))
  })
  val regfile = Reg(Vec(size, UInt(xlen.W)))
  for (i <- 0 until size){
    val writeHitOH = VecInit(io.writePortEn.zip(io.writePortAddr).map(x => x._1 && (x._2 === i.U)))
    when(writeHitOH.asUInt.orR){
      regfile(i) := Mux1H(writeHitOH, io.writePortData)
    }
  }
  val regfileBank = Wire(Vec(bankNum, Vec(size / bankNum, UInt(xlen.W))))
  regfileBank := regfile.asTypeOf(regfileBank)
  val bankReadAddr = Wire(Vec(readPortNum, UInt((addrWidth - log2Up(bankNum)).W)))
  bankReadAddr.zip(io.readPortAddr).map( x => x._1 := x._2.head(x._1.getWidth))
  val bankAddr = Wire(Vec(readPortNum, UInt(log2Up(bankNum).W)))
  bankAddr.zip(io.readPortAddr).map(x => x._1 := x._2(x._1.getWidth - 1, 0))
  val bankReadData = Wire(Vec(readPortNum, Vec(bankNum, UInt(xlen.W))))
  for (readPort <- 0 until readPortNum){
    for (readBank <- 0 until bankNum){
      bankReadData(readPort)(readBank) := regfileBank(readBank)(bankReadAddr(readPort))
  }
  }
  for (readPort <- 0 until readPortNum) {
      io.readPortData(readPort) := bankReadData(readPort)(bankAddr(readPort))
  }
}


class RegFileTest3(readPortNum: Int, writePortNum: Int = 1, bankNum: Int = 1) extends Module {
  override def desiredName = (this.getClass.getName + s"_r_${readPortNum}_sb_${bankNum}").split("\\.").last
  val size = 256
  val addrWidth = (size - 1).U.getWidth
  val xlen = 64
  val io = IO(new Bundle {
    val readPortAddr = Input(Vec(bankNum, Vec(readPortNum, UInt(addrWidth.W))))
    val readPortData = Output(Vec(readPortNum, UInt(xlen.W)))
    val writePortEn = Input(Vec(writePortNum, Bool()))
    val writePortAddr = Input(Vec(writePortNum, UInt(addrWidth.W)))
    val writePortData = Input(Vec(writePortNum, UInt(xlen.W)))
  })
  val regfile = Reg(Vec(size, UInt(xlen.W)))
  for (i <- 0 until size){
    val writeHitOH = VecInit(io.writePortEn.zip(io.writePortAddr).map(x => x._1 && (x._2 === i.U)))
    when(writeHitOH.asUInt.orR){
      regfile(i) := Mux1H(writeHitOH, io.writePortData)
    }
  }
  val regfileBank = Wire(Vec(bankNum, Vec(size / bankNum, UInt(xlen.W))))
  regfileBank := regfile.asTypeOf(regfileBank)
  val bankReadAddr = Wire(Vec(bankNum, Vec(readPortNum, UInt((addrWidth - log2Up(bankNum)).W))))
  bankReadAddr.zip(io.readPortAddr).map{ case (sinks, sources) =>
    sinks.zip(sources).map{ case (sink, source) =>
      sink := source.head(sink.getWidth)
  }}
  val bankAddr = Wire(Vec(readPortNum, UInt(log2Up(bankNum).W)))
  bankAddr.zip(io.readPortAddr(0)).map(x => x._1 := x._2(x._1.getWidth - 1, 0))
  val bankReadData = Wire(Vec(readPortNum, Vec(bankNum, UInt(xlen.W))))
  for (readPort <- 0 until readPortNum){
    for (readBank <- 0 until bankNum){
      bankReadData(readPort)(readBank) := regfileBank(readBank)(bankReadAddr(readBank)(readPort))
    }
  }
  for (readPort <- 0 until readPortNum) {
    io.readPortData(readPort) := bankReadData(readPort)(bankAddr(readPort))
  }
}


class RegFileTest4(readPortNum: Int, writePortNum: Int = 1, bankNum: Int = 2) extends Module {
  override def desiredName = (this.getClass.getName + s"_r_${readPortNum}_b_${bankNum}").split("\\.").last
  val size = 256
  val addrWidth = (size - 1).U.getWidth
  val readAddrWidth = size / bankNum
  val xlen = 64
  val io = IO(new Bundle {
    val readPortAddr = Input(Vec(readPortNum, UInt(readAddrWidth.W)))
    val readPortBankAddr = Input(Vec(readPortNum, UInt(log2Up(bankNum).W)))
    val readPortData = Output(Vec(readPortNum, UInt(xlen.W)))
    val writePortEn = Input(Vec(writePortNum, Bool()))
    val writePortAddr = Input(Vec(writePortNum, UInt(addrWidth.W)))
    val writePortData = Input(Vec(writePortNum, UInt(xlen.W)))
  })
  val regfile = Reg(Vec(size, UInt(xlen.W)))
  for (i <- 0 until size){
    val writeHitOH = VecInit(io.writePortEn.zip(io.writePortAddr).map(x => x._1 && (x._2 === i.U)))
    when(writeHitOH.asUInt.orR){
      regfile(i) := Mux1H(writeHitOH, io.writePortData)
    }
  }
  val regfileBank = Wire(Vec(bankNum, Vec(size / bankNum, UInt(xlen.W))))
  regfileBank := regfile.asTypeOf(regfileBank)
//  val bankReadAddr = Wire(Vec(bankNum, Vec(readPortNum, UInt((addrWidth - log2Up(bankNum)).W))))
//  bankReadAddr.zip(io.readPortAddr).map{ case (sinks, sources) =>
//    sinks.zip(sources).map{ case (sink, source) =>
//      sink := source.head(sink.getWidth)
//    }}
//  val bankAddr = Wire(Vec(readPortNum, UInt(log2Up(bankNum).W)))
//  bankAddr.zip(io.readPortAddr(0)).map(x => x._1 := x._2(x._1.getWidth - 1, 0))
  val bankReadData = Wire(Vec(readPortNum, Vec(bankNum, UInt(xlen.W))))
  for (readPort <- 0 until readPortNum){
    for (readBank <- 0 until bankNum){
      bankReadData(readPort)(readBank) := Mux1H(io.readPortAddr(readPort), regfileBank(readBank))
    }
  }
  for (readPort <- 0 until readPortNum) {
    io.readPortData(readPort) := bankReadData(readPort)(io.readPortBankAddr(readPort))
  }
}