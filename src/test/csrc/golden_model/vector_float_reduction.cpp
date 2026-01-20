#include "../include/vfpu_functions.h"
#include <typeinfo>

VecOutput VGMFReduction::get_expected_output(VecInput input) {
  VecOutput output;
  output = get_output_vfred(input);
  return output;
}

VecOutput VGMFReduction::get_output_vfred(VecInput input) {
  switch (input.fuOpType) {
    case VFREDUSUM: return get_output_vfredusum(input); break;
    case VFREDOSUM: return get_output_vfredosum(input); break;
    case VFREDMIN:  return get_output_vfredmin(input); break;
    case VFREDMAX:  return get_output_vfredmax(input); break;
    default: printf("VFRED: bad fuOpType %d\n", input.fuOpType); exit(1);
  }
}

VecOutput VGMFReduction::get_output_vfredosum(VecInput input) {
	// regardless of lmul > 1
	int widen = input.widen;
  int sew = input.sew;
  int one[2] = {-1, -1};
  __uint128_t bodyMask =  (*(__uint128_t *)one >> input.vinfo.vstart << input.vinfo.vstart) 
                     & (*(__uint128_t *)one << (128-input.vinfo.vl) >> (128-input.vinfo.vl));
  __uint128_t activeMask = ((input.vinfo.vm == 0) ? *(__uint128_t *)input.src4 : (__uint128_t)-1) & bodyMask;
  int mask_start_idx = 0; // always 0 in vector reduction
  __uint32_t mask_selected = activeMask >> mask_start_idx;

	if(sew < 1) {
		printf("VFRed Module: bad sew value 8.\n");
		exit(1);
	}

	fp_set_rm(input.rm);
  fp_clear_exception();

	int numMax = (VLEN / 8) >> sew; // assume that LMUL always 1 in this function, even input.lmul > 1
	uint64_t result;
	switch(sew + widen) {
		case 1: result = *((uint16_t *)&input.src1); break;
		case 2: result = *((uint32_t *)&input.src1); break;
		case 3: result = *((uint64_t *)&input.src1); break;
		default: printf("VFRed Module: bad element width %d.\n", sew + widen); exit(1);
	}
	for(int i = 0; i < numMax; i++) {
		if(!((mask_selected >> i) && 0x1)) continue;
		switch(sew + widen) {
			case 1: result = f16_add(i2f16((uint16_t)result), i2f16(*(((uint16_t *)&input.src2) + i))).v; break;
			case 2:
				if(widen)
					result = f32_add(i2f32((uint32_t)result), f16_to_f32(i2f16(*(((uint16_t *)&input.src2) + i)))).v;
				else
					result = f32_add(i2f32((uint32_t)result), i2f32(*(((uint32_t *)&input.src2) + i))).v;
				break;
			case 3:
				if(widen)
					result = f64_add(i2f64((uint64_t)result), f32_to_f64(i2f32(*(((uint32_t *)&input.src2) + i)))).v;
				else
					result = f64_add(i2f64((uint64_t)result), i2f64(*(((uint64_t *)&input.src2) + i))).v;
				break;
			default: printf("VFRed Module: bad sew/widen\n");
		}
	}

	VecOutput output;
	output.fflags[0] = softfloat_exceptionFlags & 0x1f;
	if(input.vinfo.ta) {
    output.result[0] = (uint64_t)-1;
    output.result[1] = (uint64_t)-1;
  } else {
    output.result[0] = input.src3[0];
    output.result[1] = input.src3[1];
	}
	switch(sew + widen) {
		case 1: *((uint16_t *)&output.result) = (uint16_t)(result & 0xFF);
		case 2: *((uint32_t *)&output.result) = (uint32_t)(result & 0xFFFF);
		case 3: *((uint64_t *)&output.result) = (uint64_t)(result & 0xFFFFFFFF);
	}
}

VecOutput VGMFReduction::get_output_vfredusum(VecInput input) {
	// regardless of lmul > 1
	int widen = input.widen;
  int sew = input.sew;
  int one[2] = {-1, -1};
  __uint128_t bodyMask =  (*(__uint128_t *)one >> input.vinfo.vstart << input.vinfo.vstart) 
                     & (*(__uint128_t *)one << (128-input.vinfo.vl) >> (128-input.vinfo.vl));
  __uint128_t activeMask = ((input.vinfo.vm == 0) ? *(__uint128_t *)input.src4 : (__uint128_t)-1) & bodyMask;
  int mask_start_idx = 0; // always 0 in vector reduction
  __uint32_t mask_selected = activeMask >> mask_start_idx;

	if(sew < 1) {
		printf("VFRed Module: bad sew value 8.\n");
		exit(1);
	}

	fp_set_rm(input.rm);
  fp_clear_exception();

	int numMax = (VLEN / 8) >> sew; // assume that LMUL always 1 in this function, even input.lmul > 1
	int ele_num = numMax;
	int iter_num = 4 - sew;
	uint64_t tmp_result[8];
	uint8_t tmp_mask[8];
	uint64_t src1;
	switch(sew + widen) {
		case 1: src1 = *((uint16_t *)&input.src1); break;
		case 2: src1 = *((uint32_t *)&input.src1); break;
		case 3: src1 = *((uint64_t *)&input.src1); break;
		default: printf("VFRed Module: bad element width %d.\n", sew + widen); exit(1);
	}
	for(int i = 0; i < ele_num; i++) {
		switch(sew) {
			case 1: 
				tmp_result[i] = widen ? f16_to_f32(i2f16(((uint16_t *)&input.src2)[i])).v : ((uint16_t *)&input.src2)[i];
				break;
			case 2: 
				tmp_result[i] = widen ? f32_to_f64(i2f32(((uint32_t *)&input.src2)[i])).v : ((uint32_t *)&input.src2)[i];
				break;
			case 3: 
				tmp_result[i] = ((uint64_t *)&input.src2)[i];
				break;
			default: printf("VFRed Module: bad sew value.\n");
		}
		tmp_mask[i] = (mask_selected >> i) & 0x1;
	}
	for(int i = 0; i < iter_num; i++, ele_num = ele_num >> 1) {
		for(int j = 0; j < ele_num; j = j + 2) {
			switch(sew + widen) {
				case 1:
					if(!tmp_mask[j])
						tmp_result[j/2] = tmp_result[j + 1];
					else
						tmp_result[j/2] = f16_add(i2f16(tmp_result[j]), i2f16(tmp_result[j + 1])).v;
					tmp_mask[j/2] = tmp_mask[j] | tmp_mask[j + 1];
					break;
				case 2:
					if(!tmp_mask[j])
						tmp_result[j/2] = tmp_result[j + 1];
					else
						tmp_result[j/2] = f32_add(i2f32(tmp_result[j]), i2f32(tmp_result[j + 1])).v;
					tmp_mask[j/2] = tmp_mask[j] | tmp_mask[j + 1];
					break;
				case 3:
					if(!tmp_mask[j])
						tmp_result[j/2] = tmp_result[j + 1];
					else
						tmp_result[j/2] = f64_add(i2f64(tmp_result[j]), i2f64(tmp_result[j + 1])).v;
					tmp_mask[j/2] = tmp_mask[j] | tmp_mask[j + 1];
					break;
				default: printf("VFRed Module: bad sew/widen\n"); exit(1);
			}
		}
	}
	
	uint64_t result;
	switch(sew + widen) {
		case 1: result = f16_add(i2f16((uint16_t)src1), i2f16((uint16_t)tmp_result[0])).v; break;
		case 2: result = f32_add(i2f32((uint32_t)src1), i2f32((uint32_t)tmp_result[0])).v; break;
		case 3: result = f64_add(i2f64((uint64_t)src1), i2f64((uint64_t)tmp_result[0])).v; break;
		default: printf("VFRed Module: bad sew/widen\n"); exit(1);
	}

	VecOutput output;
	output.fflags[0] = softfloat_exceptionFlags & 0x1f;
	if(input.vinfo.ta) {
    output.result[0] = (uint64_t)-1;
    output.result[1] = (uint64_t)-1;
  } else {
    output.result[0] = input.src3[0];
    output.result[1] = input.src3[1];
	}
	switch(sew + widen) {
		case 1: *((uint16_t *)&output.result) = (uint16_t)(result & 0xFF);
		case 2: *((uint32_t *)&output.result) = (uint32_t)(result & 0xFFFF);
		case 3: *((uint64_t *)&output.result) = (uint64_t)(result & 0xFFFFFFFF);
	}
}

VecOutput VGMFReduction::get_output_vfredmin(VecInput input) {
	// regardless of lmul > 1
	int widen = 0;
  int sew = input.sew;
  int one[2] = {-1, -1};
  __uint128_t bodyMask =  (*(__uint128_t *)one >> input.vinfo.vstart << input.vinfo.vstart) 
                     & (*(__uint128_t *)one << (128-input.vinfo.vl) >> (128-input.vinfo.vl));
  __uint128_t activeMask = ((input.vinfo.vm == 0) ? *(__uint128_t *)input.src4 : (__uint128_t)-1) & bodyMask;
  int mask_start_idx = 0; // always 0 in vector reduction
  __uint32_t mask_selected = activeMask >> mask_start_idx;

	if(sew < 1) {
		printf("VFRed Module: bad sew value 8.\n");
		exit(1);
	}

	fp_set_rm(input.rm);
  fp_clear_exception();

	int numMax = (VLEN / 8) >> sew; // assume that LMUL always 1 in this function, even input.lmul > 1
	int ele_num = numMax;
	int iter_num = 4 - sew;
	uint64_t tmp_result[8];
	uint8_t tmp_mask[8];
	uint64_t src1;
	switch(sew + widen) {
		case 1: src1 = *((uint16_t *)&input.src1); break;
		case 2: src1 = *((uint32_t *)&input.src1); break;
		case 3: src1 = *((uint64_t *)&input.src1); break;
		default: printf("VFRed Module: bad element width %d.\n", sew + widen); exit(1);
	}
	for(int i = 0; i < ele_num; i++) {
		switch(sew) {
			case 1: 
				tmp_result[i] = widen ? f16_to_f32(i2f16(((uint16_t *)&input.src2)[i])).v : ((uint16_t *)&input.src2)[i];
				break;
			case 2: 
				tmp_result[i] = widen ? f32_to_f64(i2f32(((uint32_t *)&input.src2)[i])).v : ((uint32_t *)&input.src2)[i];
				break;
			case 3: 
				tmp_result[i] = ((uint64_t *)&input.src2)[i];
				break;
			default: printf("VFRed Module: bad sew value.\n");
		}
		tmp_mask[i] = (mask_selected >> i) & 0x1;
	}
	for(int i = 0; i < iter_num; i++, ele_num = ele_num >> 1) {
		for(int j = 0; j < ele_num; j = j + 2) {
			switch(sew + widen) {
				case 1:
					if(!tmp_mask[j])
						tmp_result[j/2] = tmp_result[j + 1];
					else
						tmp_result[j/2] = f16_min(i2f16(tmp_result[j]), i2f16(tmp_result[j + 1])).v;
					tmp_mask[j/2] = tmp_mask[j] | tmp_mask[j + 1];
					break;
				case 2:
					if(!tmp_mask[j])
						tmp_result[j/2] = tmp_result[j + 1];
					else
						tmp_result[j/2] = f32_min(i2f32(tmp_result[j]), i2f32(tmp_result[j + 1])).v;
					tmp_mask[j/2] = tmp_mask[j] | tmp_mask[j + 1];
					break;
				case 3:
					if(!tmp_mask[j])
						tmp_result[j/2] = tmp_result[j + 1];
					else
						tmp_result[j/2] = f64_min(i2f64(tmp_result[j]), i2f64(tmp_result[j + 1])).v;
					tmp_mask[j/2] = tmp_mask[j] | tmp_mask[j + 1];
					break;
				default: printf("VFRed Module: bad sew/widen\n"); exit(1);
			}
		}
	}
	
	uint64_t result;
	switch(sew + widen) {
		case 1: result = f16_min(i2f16((uint16_t)src1), i2f16((uint16_t)tmp_result[0])).v; break;
		case 2: result = f32_min(i2f32((uint32_t)src1), i2f32((uint32_t)tmp_result[0])).v; break;
		case 3: result = f64_min(i2f64((uint64_t)src1), i2f64((uint64_t)tmp_result[0])).v; break;
		default: printf("VFRed Module: bad sew/widen\n"); exit(1);
	}

	VecOutput output;
	output.fflags[0] = softfloat_exceptionFlags & 0x1f;
	if(input.vinfo.ta) {
    output.result[0] = (uint64_t)-1;
    output.result[1] = (uint64_t)-1;
  } else {
    output.result[0] = input.src3[0];
    output.result[1] = input.src3[1];
	}
	switch(sew + widen) {
		case 1: *((uint16_t *)&output.result) = (uint16_t)(result & 0xFF);
		case 2: *((uint32_t *)&output.result) = (uint32_t)(result & 0xFFFF);
		case 3: *((uint64_t *)&output.result) = (uint64_t)(result & 0xFFFFFFFF);
	}
}

VecOutput VGMFReduction::get_output_vfredmax(VecInput input) {
	// regardless of lmul > 1
	int widen = 0;
  int sew = input.sew;
  int one[2] = {-1, -1};
  __uint128_t bodyMask =  (*(__uint128_t *)one >> input.vinfo.vstart << input.vinfo.vstart) 
                     & (*(__uint128_t *)one << (128-input.vinfo.vl) >> (128-input.vinfo.vl));
  __uint128_t activeMask = ((input.vinfo.vm == 0) ? *(__uint128_t *)input.src4 : (__uint128_t)-1) & bodyMask;
  int mask_start_idx = 0; // always 0 in vector reduction
  __uint32_t mask_selected = activeMask >> mask_start_idx;

	if(sew < 1) {
		printf("VFRed Module: bad sew value 8.\n");
		exit(1);
	}

	fp_set_rm(input.rm);
  fp_clear_exception();

	int numMax = (VLEN / 8) >> sew; // assume that LMUL always 1 in this function, even input.lmul > 1
	int ele_num = numMax;
	int iter_num = 4 - sew;
	uint64_t tmp_result[8];
	uint8_t tmp_mask[8];
	uint64_t src1;
	switch(sew + widen) {
		case 1: src1 = *((uint16_t *)&input.src1); break;
		case 2: src1 = *((uint32_t *)&input.src1); break;
		case 3: src1 = *((uint64_t *)&input.src1); break;
		default: printf("VFRed Module: bad element width %d.\n", sew + widen); exit(1);
	}
	for(int i = 0; i < ele_num; i++) {
		switch(sew) {
			case 1: 
				tmp_result[i] = widen ? f16_to_f32(i2f16(((uint16_t *)&input.src2)[i])).v : ((uint16_t *)&input.src2)[i];
				break;
			case 2: 
				tmp_result[i] = widen ? f32_to_f64(i2f32(((uint32_t *)&input.src2)[i])).v : ((uint32_t *)&input.src2)[i];
				break;
			case 3: 
				tmp_result[i] = ((uint64_t *)&input.src2)[i];
				break;
			default: printf("VFRed Module: bad sew value.\n");
		}
		tmp_mask[i] = (mask_selected >> i) & 0x1;
	}
	for(int i = 0; i < iter_num; i++, ele_num = ele_num >> 1) {
		for(int j = 0; j < ele_num; j = j + 2) {
			switch(sew + widen) {
				case 1:
					if(!tmp_mask[j])
						tmp_result[j/2] = tmp_result[j + 1];
					else
						tmp_result[j/2] = f16_max(i2f16(tmp_result[j]), i2f16(tmp_result[j + 1])).v;
					tmp_mask[j/2] = tmp_mask[j] | tmp_mask[j + 1];
					break;
				case 2:
					if(!tmp_mask[j])
						tmp_result[j/2] = tmp_result[j + 1];
					else
						tmp_result[j/2] = f32_max(i2f32(tmp_result[j]), i2f32(tmp_result[j + 1])).v;
					tmp_mask[j/2] = tmp_mask[j] | tmp_mask[j + 1];
					break;
				case 3:
					if(!tmp_mask[j])
						tmp_result[j/2] = tmp_result[j + 1];
					else
						tmp_result[j/2] = f64_max(i2f64(tmp_result[j]), i2f64(tmp_result[j + 1])).v;
					tmp_mask[j/2] = tmp_mask[j] | tmp_mask[j + 1];
					break;
				default: printf("VFRed Module: bad sew/widen\n"); exit(1);
			}
		}
	}
	
	uint64_t result;
	switch(sew + widen) {
		case 1: result = f16_max(i2f16((uint16_t)src1), i2f16((uint16_t)tmp_result[0])).v; break;
		case 2: result = f32_max(i2f32((uint32_t)src1), i2f32((uint32_t)tmp_result[0])).v; break;
		case 3: result = f64_max(i2f64((uint64_t)src1), i2f64((uint64_t)tmp_result[0])).v; break;
		default: printf("VFRed Module: bad sew/widen\n"); exit(1);
	}

	VecOutput output;
	output.fflags[0] = softfloat_exceptionFlags & 0x1f;
	if(input.vinfo.ta) {
    output.result[0] = (uint64_t)-1;
    output.result[1] = (uint64_t)-1;
  } else {
    output.result[0] = input.src3[0];
    output.result[1] = input.src3[1];
	}
	switch(sew + widen) {
		case 1: *((uint16_t *)&output.result) = (uint16_t)(result & 0xFF);
		case 2: *((uint32_t *)&output.result) = (uint32_t)(result & 0xFFFF);
		case 3: *((uint64_t *)&output.result) = (uint64_t)(result & 0xFFFFFFFF);
	}
}

ElementOutput VGMFReduction::calculation_e16(ElementInput input) { VecOutput rs; return rs; }
ElementOutput VGMFReduction::calculation_e32(ElementInput input) { VecOutput rs; return rs; }
ElementOutput VGMFReduction::calculation_e64(ElementInput input) { VecOutput rs; return rs; }
