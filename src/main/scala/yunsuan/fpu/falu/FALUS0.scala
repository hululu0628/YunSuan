//package yunsuan.fpu.falu
//
//import yunsuan.fpu.FloatParams
//import yunsuan.fpu.falu.FALUS0ToS1Bundle
//import chisel3._
//import chisel3.util._
//
//class FALUS0(val totalWidth: Int) extends Module with FloatParams{
//    val io = IO(new Bundle() {
//      val fp_a, fp_b  = Input (UInt(totalWidth.W))
//      val is_sub      = Input (Bool())
//      val round_mode  = Input (UInt(rmWidth.W))
//      val toS1 = Output(new FALUS0ToS1Bundle(totalWidth))
//    })
//  val isRNE = io.round_mode === RNE.U
//  val isRTZ = io.round_mode === RTZ.U
//  val isRDN = io.round_mode === RDN.U
//  val isRUP = io.round_mode === RUP.U
//  val isRMM = io.round_mode === RMM.U
//  // far path begin
//  val fp_a_sign = io.fp_a.head(1).asBool
//  val fp_b_sign = io.fp_b.head(1).asBool
//  val efficient_fp_b_sign = (fp_b_sign ^ io.is_sub).asBool
//  val EOP = ( fp_a_sign ^ efficient_fp_b_sign ).asBool
//  val fp_a_mantissa            = io.fp_a.tail(1 + exponentWidth)
//  val fp_b_mantissa            = io.fp_b.tail(1 + exponentWidth)
//  val fp_a_mantissa_isnot_zero = io.fp_a.tail(1 + exponentWidth).orR
//  val fp_b_mantissa_isnot_zero = io.fp_b.tail(1 + exponentWidth).orR
//  val Efp_a = io.fp_a.tail(1).head(exponentWidth)
//  val Efp_b = io.fp_b.tail(1).head(exponentWidth)
//  val Efp_a_is_not_zero  = Efp_a.orR
//  val Efp_b_is_not_zero  = Efp_b.orR
//  val Efp_a_is_greater_than_1= Efp_a.head(exponentWidth-1).orR
//  val Efp_b_is_greater_than_1= Efp_b.head(exponentWidth-1).orR
//  val Efp_a_is_all_one   = Efp_a.andR
//  val Efp_b_is_all_one   = Efp_b.andR
//  val U_Efp_aSubEfp_b = Module(new Adder(Efp_a.getWidth,Efp_a.getWidth,Efp_a.getWidth,is_sub = true))
//  U_Efp_aSubEfp_b.io.a := Efp_a | !Efp_a_is_not_zero
//  U_Efp_aSubEfp_b.io.b := Efp_b | !Efp_b_is_not_zero
//  val Efp_aSubEfp_b     = U_Efp_aSubEfp_b.io.c
//  val U_Efp_bSubEfp_a = Module(new Adder(Efp_a.getWidth,Efp_a.getWidth,Efp_a.getWidth,is_sub = true))
//  U_Efp_bSubEfp_a.io.a := Efp_b | !Efp_b_is_not_zero
//  U_Efp_bSubEfp_a.io.b := Efp_a | !Efp_a_is_not_zero
//  val Efp_bSubEfp_a     = U_Efp_bSubEfp_a.io.c
//  val isEfp_bGreater    = Efp_b > Efp_a
//  val Efp_a_sub_1       = Mux(Efp_a_is_not_zero,Efp_a - 1.U,0.U)
//  val Efp_b_sub_1       = Mux(Efp_b_is_not_zero,Efp_b - 1.U,0.U)
//  val absEaSubEb        = Wire(UInt(Efp_a.getWidth.W))
//  io.toS1.absEaSubEb := absEaSubEb
//  absEaSubEb := Mux(isEfp_bGreater, Efp_bSubEfp_a, Efp_aSubEfp_b)
//  val significand_fp_a   = Cat(Efp_a_is_not_zero,fp_a_mantissa)
//  val significand_fp_b   = Cat(Efp_b_is_not_zero,fp_b_mantissa)
//  val E_greater          = Mux(isEfp_bGreater, Efp_b, Efp_a)
//  val EA                 = Mux(EOP, E_greater-1.U, E_greater)
//  io.toS1.EA := EA
//  val greaterSignificand = Mux(isEfp_bGreater, significand_fp_b, significand_fp_a)
//  val smallerSignificand = Mux(isEfp_bGreater, significand_fp_a, significand_fp_b)
//  val farmaxShiftValue   = (significandWidth+2).U
//  val A_Wire              = Mux(EOP,Cat(greaterSignificand,0.U),Cat(0.U,greaterSignificand))
//  val A_Wire_FS1 = Mux(EOP,Cat(greaterSignificand,0.U),Cat(0.U,greaterSignificand)) + 2.U
//  val widenWidth = significandWidth + 3
//  val fp_b_mantissa_widen = Mux(EOP,Cat(~significand_fp_b,1.U,Fill(widenWidth,1.U)),Cat(0.U,significand_fp_b,0.U(widenWidth.W)))
//  val U_far_rshift_1 = Module(new FarShiftRightWithMuxInvFirst(fp_b_mantissa_widen.getWidth,farmaxShiftValue.getWidth))
//  U_far_rshift_1.io.src := fp_b_mantissa_widen
//  U_far_rshift_1.io.shiftValue := Efp_aSubEfp_b.asTypeOf(farmaxShiftValue)
//  U_far_rshift_1.io.EOP := EOP
//  val U_far_rshift_fp_b_result = U_far_rshift_1.io.result
//  val fp_a_mantissa_widen = Mux(EOP,Cat(~significand_fp_a,1.U,Fill(widenWidth,1.U)),Cat(0.U,significand_fp_a,0.U(widenWidth.W)))
//  val U_far_rshift_2 = Module(new FarShiftRightWithMuxInvFirst(fp_a_mantissa_widen.getWidth,farmaxShiftValue.getWidth))
//  U_far_rshift_2.io.src := fp_a_mantissa_widen
//  U_far_rshift_2.io.shiftValue := Efp_bSubEfp_a.asTypeOf(farmaxShiftValue)
//  U_far_rshift_2.io.EOP := EOP
//  val U_far_rshift_fp_a_result = U_far_rshift_2.io.result
//  val far_rshift_widen_result = Mux(isEfp_bGreater, U_far_rshift_fp_a_result, U_far_rshift_fp_b_result)
//  val absEaSubEb_is_greater = absEaSubEb > (significandWidth + 3).U
//  val B_Wire = Mux(absEaSubEb_is_greater,Fill(significandWidth+1,EOP),far_rshift_widen_result.head(significandWidth+1))
//  val B_guard_normal = Mux(
//    absEaSubEb_is_greater,
//    false.B,
//    Mux(EOP,!far_rshift_widen_result.head(significandWidth+2)(0).asBool,far_rshift_widen_result.head(significandWidth+2)(0).asBool)
//  )
//  val B_round_normal = Mux(
//    absEaSubEb_is_greater,
//    false.B,
//    Mux(EOP,!far_rshift_widen_result.head(significandWidth+3)(0).asBool,far_rshift_widen_result.head(significandWidth+3)(0).asBool)
//  )
//  val B_sticky_normal = Mux(
//    absEaSubEb_is_greater,
//    smallerSignificand.orR,
//    Mux(EOP, ~far_rshift_widen_result.tail(significandWidth+3).asUInt.orR, far_rshift_widen_result.tail(significandWidth+3).orR)
//  )
//  io.toS1.far_sign_result := Mux(isEfp_bGreater, efficient_fp_b_sign, fp_a_sign)
//  // far path end
//
//  //close path begin
//  val Efp_b_is_greater = (Efp_b(0) ^ Efp_a(0)) & !(Efp_a(1) ^ Efp_b(1) ^ Efp_a(0))
//  val absEaSubEb_close = Efp_a(0) ^ Efp_b(0)
//  val exp_is_equal = !absEaSubEb_close | (!Efp_a_is_not_zero ^ !Efp_b_is_not_zero)
//  val EA_close = Mux(Efp_b_is_greater, Efp_b, Efp_a)
//
//  val B_guard = Mux(Efp_b_is_greater, Mux(Efp_a_is_not_zero, fp_a_mantissa(0), false.B),
//    Mux(Efp_b_is_not_zero, fp_b_mantissa(0), false.B))
//  val B_round = false.B
//  val B_sticky = false.B
//
//  val mask_Efp_a_onehot = Cat(
//    Efp_a === 0.U | Efp_a === 1.U,
//    (for (i <- 2 until significandWidth + 1) yield
//      (Efp_a === i.U).asUInt
//      ).reduce(Cat(_, _))
//  )
//  val mask_Efp_b_onehot = Cat(
//    Efp_b === 0.U | Efp_b === 1.U,
//    (for (i <- 2 until significandWidth + 1) yield
//      (Efp_b === i.U).asUInt
//      ).reduce(Cat(_, _))
//  )
//
//  val U_CS0 = Module(new ClosePathAdder(adderWidth = significandWidth, adderType = "CS0"))
//  U_CS0.io.adder_op0 := significand_fp_a
//  U_CS0.io.adder_op1 := significand_fp_b
//  val CS0 = U_CS0.io.result(significandWidth - 1, 0)
//  val priority_lshift_0 = CS0 | mask_Efp_a_onehot
//
//  val U_CS1 = Module(new ClosePathAdder(adderWidth = significandWidth, adderType = "CS1"))
//  U_CS1.io.adder_op0 := significand_fp_b
//  U_CS1.io.adder_op1 := significand_fp_a
//  val CS1 = U_CS1.io.result(significandWidth - 1, 0)
//  val priority_lshift_1 = CS1 | mask_Efp_b_onehot
//
//  val U_CS2 = Module(new ClosePathAdder(adderWidth = significandWidth, adderType = "CS2"))
//  U_CS2.io.adder_op0 := Cat(1.U, fp_a_mantissa)
//  U_CS2.io.adder_op1 := Cat(1.U, fp_b_mantissa)
//  val CS2 = U_CS2.io.result(significandWidth, 0)
//  val priority_lshift_2 = CS2 | Cat(mask_Efp_a_onehot, Efp_a === (significandWidth + 1).U)
//
//  val U_CS3 = Module(new ClosePathAdder(adderWidth = significandWidth, adderType = "CS3"))
//  U_CS3.io.adder_op0 := Cat(1.U, fp_b_mantissa)
//  U_CS3.io.adder_op1 := Cat(1.U, fp_a_mantissa)
//  val CS3 = U_CS3.io.result(significandWidth, 0)
//  val priority_lshift_3 = CS3 | Cat(mask_Efp_b_onehot, Efp_b === (significandWidth + 1).U)
//
//  val U_CS4 = Module(new ClosePathAdder(adderWidth = significandWidth, adderType = "CS0"))
//  U_CS4.io.adder_op0 := Mux(Efp_b_is_greater, significand_fp_b, significand_fp_a)
//  U_CS4.io.adder_op1 := Mux(Efp_b_is_greater, Cat(0.U, significand_fp_a(significandWidth - 1, 1)), Cat(0.U, significand_fp_b(significandWidth - 1, 1)))
//  val CS4 = U_CS4.io.result
//
//  val CS2_round_up = significand_fp_b(0) & (
//    (isRUP && !fp_a_sign) || (isRDN && fp_a_sign) || (isRNE && CS2(1) && CS2(0)) || isRMM
//    )
//  val CS3_round_up = significand_fp_a(0) & (
//    (isRUP && fp_a_sign) || (isRDN && !fp_a_sign) || (isRNE && CS3(1) && CS3(0)) || isRMM
//    )
//  val sel_CS0 = exp_is_equal & !U_CS0.io.result.head(1).asBool
//  val sel_CS1 = exp_is_equal & U_CS0.io.result.head(1).asBool
//  val sel_CS2 = !exp_is_equal & !Efp_b_is_greater & ((!U_CS2.io.result.head(1).asBool | !U_CS2.io.result(0).asBool) | !CS2_round_up)
//  val sel_CS3 = !exp_is_equal & Efp_b_is_greater & ((!U_CS3.io.result.head(1).asBool | !U_CS3.io.result(0).asBool) | !CS3_round_up)
//  val sel_CS4 = !exp_is_equal & (
//    (!Efp_b_is_greater & U_CS2.io.result.head(1).asBool & U_CS2.io.result(0).asBool & CS2_round_up) |
//      (Efp_b_is_greater & U_CS3.io.result.head(1).asBool & U_CS3.io.result(0).asBool & CS3_round_up)
//    )
//  val CS_0123_result = Mux1H(
//    Seq(
//      sel_CS0,
//      sel_CS1,
//      sel_CS2,
//      sel_CS3
//    ),
//    Seq(
//      Cat(CS0, 0.U),
//      Cat(CS1, 0.U),
//      CS2,
//      CS3
//    )
//  )
//  val mask_onehot = Mux1H(
//    Seq(
//      sel_CS0,
//      sel_CS1,
//      sel_CS2,
//      sel_CS3
//    ),
//    Seq(
//      Cat(mask_Efp_a_onehot, 1.U),
//      Cat(mask_Efp_b_onehot, 1.U),
//      Cat(mask_Efp_a_onehot, Efp_a === (significandWidth + 1).U),
//      Cat(mask_Efp_b_onehot, Efp_b === (significandWidth + 1).U)
//    )
//  )
//  val priority_mask = CS_0123_result | mask_onehot
//  val lzd_0123 = LZD(priority_mask)
//  io.toS1.close_lzd := lzd_0123
//  io.toS1.close_result := priority_mask << lzd_0123
//  io.toS1.round_mode := io.round_mode
//  io.toS1.fflagsNV := false.B
//  io.toS1.isEfp_bGreater := isEfp_bGreater
//  io.toS1.EOP := EOP
//  io.toS1.A_Wire := A_Wire
//  io.toS1.B_Wire := B_Wire
//  io.toS1.A_Wire_FS1 := A_Wire_FS1
//  io.toS1.B_guard_normal := B_guard_normal
//  io.toS1.B_round_normal := B_round_normal
//  io.toS1.B_sticky_normal := B_sticky_normal
//  //close path end
//
//}
