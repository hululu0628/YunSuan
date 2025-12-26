//package yunsuan.fpu.falu
//
//import yunsuan.fpu.FloatParams
//import chisel3._
//import chisel3.util._
//
//class FALUS0ToS1Bundle(val totalWidth: Int) extends Bundle with FloatParams {
//  val round_mode = Input(UInt(rmWidth.W))
//  val fflagsNV = Output(Bool())
//  val absEaSubEb = Output(UInt(exponentWidth.W))
//  val isEfp_bGreater = Output(Bool())
//  val EOP = Output(Bool())
//  val EA = Output(UInt(exponentWidth.W))
//  val far_sign_result = Output(Bool())
//  val A_Wire = Output(UInt(decimalWidth.W))
//  val B_Wire = Output(UInt(decimalWidth.W))
//  val A_Wire_FS1 = Output(UInt(decimalWidth.W))
//  val B_guard_normal  = Output(Bool())
//  val B_round_normal  = Output(Bool())
//  val B_sticky_normal = Output(Bool())
//  val close_lzd = Output(UInt(decimalWidth.U.getWidth.W))
//  val close_result = Output(UInt(decimalWidth.W))
//}
//
////class FALUS1(val totalWidth: Int) extends Module with FloatParams{
////  val io = IO(new Bundle() {
////    val fromS0  = Input (Flipped(new FALUS0ToS1Bundle(totalWidth)))
////    val fp_c = Output(UInt(totalWidth.W))
////    val fflags = Output(UInt(flagsWidth.W))
////  })
////  val isRNE = io.fromS0.round_mode === RNE.U
////  val isRTZ = io.fromS0.round_mode === RTZ.U
////  val isRDN = io.fromS0.round_mode === RDN.U
////  val isRUP = io.fromS0.round_mode === RUP.U
////  val isRMM = io.fromS0.round_mode === RMM.U
////  // far path begin
////  val EOP = io.fromS0.EOP
////  val EA = io.fromS0.EA
////  val EA_add1 = EA + 1.U
////  val isEfp_bGreater = io.fromS0.isEfp_bGreater
////  val B_guard_normal  = io.fromS0.B_guard_normal
////  val B_round_normal  = io.fromS0.B_round_normal
////  val B_sticky_normal = io.fromS0.B_sticky_normal
////  val B_rsticky_normal = B_round_normal | B_sticky_normal
////  val U_FS0 = Module(new FarPathAdder(decimalWidth))
////  U_FS0.io.A := io.fromS0.A_Wire
////  U_FS0.io.B := io.fromS0.B_Wire
////  val FS0 = U_FS0.io.result
////  val U_FS1 = Module(new FarPathAdder(decimalWidth))
////  U_FS1.io.A := io.fromS0.A_Wire_FS1
////  U_FS1.io.B := io.fromS0.B_Wire
////  val FS1 = U_FS1.io.result
////  val far_case_normal = !FS0.head(1).asBool
////  val far_case_overflow = FS0.head(1).asBool
////  val lgs_normal = Cat(FS0(0), Mux(EOP, (~Cat(B_guard_normal, B_rsticky_normal)).asUInt + 1.U, Cat(B_guard_normal, B_rsticky_normal)))
////  val far_sign_result = io.fromS0.far_sign_result
////  val far_case_normal_round_up = (EOP && !lgs_normal(1) && !lgs_normal(0)) ||
////    (isRNE && lgs_normal(1) && (lgs_normal(2) || lgs_normal(0))) ||
////    (isRDN && far_sign_result && (lgs_normal(1) || lgs_normal(0))) ||
////    (isRUP && !far_sign_result && (lgs_normal(1) || lgs_normal(0))) ||
////    (isRMM && lgs_normal(1))
////  val normal_fsel0 = (!FS0(0) & far_case_normal_round_up) | !far_case_normal_round_up
////  val normal_fsel1 = FS0(0) & far_case_normal_round_up
////  val grs_overflow = Mux(EOP, Cat(io.fromS0.A_Wire(0), 0.U, 0.U) - Cat(~io.fromS0.B_Wire(0), B_guard_normal, B_rsticky_normal), Cat(FS0(0), B_guard_normal, B_rsticky_normal))
////  val lgs_overflow = Cat(FS0(1), grs_overflow(2), grs_overflow(1) || grs_overflow(0))
////  val far_case_overflow_round_up = (EOP && !lgs_overflow(1) && !lgs_overflow(0)) ||
////    (isRNE && lgs_overflow(1)  && (lgs_overflow(2) || lgs_overflow(0))) ||
////    (isRDN && far_sign_result  && (lgs_overflow(1) || lgs_overflow(0))) ||
////    (isRUP && !far_sign_result && (lgs_overflow(1) || lgs_overflow(0))) ||
////    (isRMM && lgs_overflow(1))
////  val overflow_fsel0 = (!FS0(1) & far_case_overflow_round_up) | !far_case_overflow_round_up
////  val overflow_fsel1 = FS0(1) & far_case_overflow_round_up
////  val far_exponent_result = Wire(UInt(exponentWidth.W))
////  val far_fraction_result = Wire(UInt((significandWidth - 1).W))
////  far_exponent_result := Mux(
////    far_case_overflow | (FS1.head(1).asBool & FS0(0) & far_case_normal_round_up) | !EA.orR & FS0.tail(1).head(1).asBool,
////    EA_add1,
////    EA,
////  )
////  val flagsOF_far = EA_add1.andR && (far_case_overflow || (FS1.head(1).asBool && FS0(0) && far_case_normal_round_up))
////  val flagsNX_far = Mux(far_case_normal, lgs_normal(1, 0).orR, lgs_overflow(1, 0).orR) || flagsOF_far
////  far_fraction_result := Mux1H(
////    Seq(
////      far_case_normal & normal_fsel0,
////      far_case_normal & normal_fsel1,
////      far_case_overflow & overflow_fsel0,
////      far_case_overflow & overflow_fsel1
////    ),
////    Seq(
////      Cat(FS0(significandWidth - 2, 1), FS0(0) ^ far_case_normal_round_up),
////      Cat(FS1(significandWidth - 2, 1), 0.U),
////      Cat(FS0(significandWidth - 1, 2), FS0(1) ^ far_case_overflow_round_up),
////      FS1(significandWidth - 1, 1)
////    )
////  )
////  val result_overflow = Mux(
////    isRTZ || (isRDN && !far_sign_result) || (isRUP && far_sign_result),
////    Cat(far_sign_result, Fill(exponentWidth - 1, 1.U), 0.U, Fill(significandWidth - 1, 1.U)),
////    Cat(far_sign_result, Fill(exponentWidth, 1.U), Fill(significandWidth - 1, 0.U))
////  )
////  val fp_c_far = Mux(flagsOF_far,
////    result_overflow,
////    Cat(far_sign_result, far_exponent_result, far_fraction_result)
////  )
////  val flags_far = Cat(0.U, 0.U, flagsOF_far, 0.U, flagsNX_far)
////  // far path end
////
////
////  //close path begin
////  val lzd_0123 = io.fromS0.lzd0123
////  val lzd_0123_reg = RegEnable(lzd_0123, fire)
////  val U_Lshift = Module(new CloseShiftLeftWithMux(CS_0123_result.getWidth, priority_mask.getWidth.U.getWidth))
////  U_Lshift.io.src := RegEnable(CS_0123_result, fire)
////  U_Lshift.io.shiftValue := lzd_0123_reg
////  val CS_0123_lshift_result_reg = U_Lshift.io.result(significandWidth, 1)
////  NX := RegEnable(Mux(
////    (sel_CS2 & (U_CS2.io.result.head(1).asBool & U_CS2.io.result(0).asBool & !CS2_round_up)) |
////      (sel_CS3 & (U_CS3.io.result.head(1).asBool & U_CS3.io.result(0).asBool & !CS3_round_up)) | sel_CS4,
////    B_guard,
////    0.U
////  ), fire)
////  close_fraction_result := Mux(RegEnable(sel_CS4, fire), CS4_reg.tail(1), CS_0123_lshift_result_reg.tail(1))
////  val lshift_result_head_is_one = ((RegEnable(EA - 1.U, fire) > lzd_0123_reg) & RegEnable(CS_0123_result, fire).orR) | RegEnable(mask_onehot & CS_0123_result, fire).orR
////  val EA_sub_value = Mux(
////    RegEnable(sel_CS4, fire),
////    0.U(exponentWidth.W),
////    Mux(
////      lshift_result_head_is_one,
////      lzd_0123_reg.asTypeOf(EA),
////      RegEnable(EA, fire)
////    )
////  )
////  close_exponent_result := RegEnable(EA, fire) - EA_sub_value
////  close_sign_result := RegEnable(Mux1H(
////    Seq(
////      sel_CS0 & (exp_is_equal & (U_CS0.io.result.head(1).asBool | U_CS1.io.result.head(1).asBool)),
////      sel_CS0 & exp_is_equal & (!U_CS0.io.result.head(1).asBool & !U_CS1.io.result.head(1).asBool),
////      sel_CS1,
////      sel_CS2,
////      sel_CS3,
////      sel_CS4
////    ),
////    Seq(
////      fp_a_sign,
////      RDN,
////      !fp_a_sign,
////      fp_a_sign,
////      !fp_a_sign,
////      Mux(Efp_b_is_greater, !fp_a_sign, fp_a_sign)
////    )
////  ), fire)
////  io.fp_c := Cat(close_sign_result, close_exponent_result, close_fraction_result)
////
////
////  //close path end
////
////}
