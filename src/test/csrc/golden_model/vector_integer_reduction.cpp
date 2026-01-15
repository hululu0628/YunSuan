#include "../include/gm_common.h"

VecOutput VGMReduction::get_expected_output(VecInput input) {
  VecOutput output;
  output = get_output_vred(input);
  return output;
}


VecOutput VGMReduction::get_output_vred(VecInput input) {
    switch (input.fuOpType) {
        case VREDSUM: return get_output_vredsum(input); break;
        case VREDMAX: return get_output_vredmax(input); break;
        case VREDMIN: return get_output_vredmin(input); break;
        case VREDAND: return get_output_vredand(input); break;
        case VREDOR:  return get_output_vredor(input); break;
        case VREDXOR: return get_output_vredxor(input); break;
        default: printf("VRED: bad fuOpType %d\n", input.fuOpType); exit(1);
    }
}

VecOutput VGMReduction::get_output_vredsum(VecInput input) {
    int widen = input.widen;
    int is_signed = input.is_signed;

    // wrap later
    // int lmul = input.vinfo.vlmul; // lmul always equals 1 for vector reduction
    int sew = input.sew;
    int one[2] = {-1, -1};
    __uint128_t bodyMask =  (*(__uint128_t *)one >> input.vinfo.vstart << input.vinfo.vstart) 
                       & (*(__uint128_t *)one << (128-input.vinfo.vl) >> (128-input.vinfo.vl));
    __uint128_t activeMask = ((input.vinfo.vm == 0) ? *(__uint128_t *)input.src4 : (__uint128_t)-1) & bodyMask;
    int mask_start_idx = 0; // always 0 in vector reduction
    __uint32_t mask_selected = activeMask >> mask_start_idx;

    if(widen && sew == 3) {
        printf("VRED Modle, bad widen sew %d\n", input.sew);
        exit(1);
    }

    int numMax = (VLEN / 8) >> sew; // because LMUL always 1
    uint64_t src1;
    uint64_t selected_src2[numMax];
    for (int i = 0; i < numMax; i++) {
        switch (sew) {
            case 0: 
                selected_src2[i] = (is_signed) ? (*((__int8_t  *)input.src2 + i)) : (*((__uint8_t  *)input.src2 + i));
                break;
            case 1:
                selected_src2[i] = (is_signed) ? (*((__int16_t *)input.src2 + i)) : (*((__uint16_t *)input.src2 + i));
                break;
            case 2:
                selected_src2[i] = (is_signed) ? (*((__int32_t *)input.src2 + i)) : (*((__uint32_t *)input.src2 + i));
                break;
            case 3:
                selected_src2[i] = (*((__uint64_t *)input.src2 + i));
                break;
            default: printf("VRED Modle, bad sew %d\n", input.sew); exit(1);
        }
        int active = (mask_selected >> i) & 0x1;
        selected_src2[i] = active ? selected_src2[i] : 0; // set inactive element to 0
    }
    switch (sew) {
        case 0: src1 = widen ? (*((__uint16_t *)&input.src1)) : (*((__uint8_t  *)&input.src1)); break;
        case 1: src1 = widen ? (*((__uint32_t *)&input.src1)) : (*((__uint16_t *)&input.src1)); break;
        case 2: src1 = widen ? (*((__uint64_t *)&input.src1)) : (*((__uint32_t *)&input.src1)); break;
        case 3: src1 = (*((__uint64_t *)&input.src1)); break;
        default: printf("VRED Modle, bad sew %d\n", input.sew); exit(1);
    }

    uint64_t result = src1;
    for(int i = 0; i < numMax; i++) {
        result += selected_src2[i];
    }

    // wrap later
    VecOutput output;
    if(input.vinfo.ta) {
        output.result[0] = (uint64_t)-1;
        output.result[1] = (uint64_t)-1;
    } else {
        output.result[0] = input.src3[0];
        output.result[1] = input.src3[1];
    }
    switch (sew) {
        case 0: 
            if(widen)
                *((uint16_t *)&output.result[0]) = (uint16_t)(result & 0xFFFF);
            else
                *((uint8_t  *)&output.result[0]) = (uint8_t)(result & 0xFF);
            break;
        case 1:
            if(widen)
                *((uint32_t *)&output.result[0]) = (uint32_t)(result & 0xFFFFFFFF);
            else
                *((uint16_t *)&output.result[0]) = (uint16_t)(result & 0xFFFF);
            break;
        case 2:
            if(widen)
                *((uint64_t *)&output.result[0]) = (uint64_t)(result);
            else
                *((uint32_t *)&output.result[0]) = (uint32_t)(result & 0xFFFFFFFF);
            break;
        case 3:
            *((uint64_t *)&output.result[0]) = (uint64_t)(result);
            break;
        default: printf("VRED Modle, bad sew %d\n", input.sew); exit(1);
    }

    return output;
}

VecOutput VGMReduction::get_output_vredmax(VecInput input) {
    int widen = 0;
    int is_signed = input.is_signed;

    // wrap later
    // int lmul = input.vinfo.vlmul; // lmul always equals 1 for vector reduction
    int sew = input.sew;
    int one[2] = {-1, -1};
    __uint128_t bodyMask =  (*(__uint128_t *)one >> input.vinfo.vstart << input.vinfo.vstart) 
                       & (*(__uint128_t *)one << (128-input.vinfo.vl) >> (128-input.vinfo.vl));
    __uint128_t activeMask = ((input.vinfo.vm == 0) ? *(__uint128_t *)input.src4 : (__uint128_t)-1) & bodyMask;
    int mask_start_idx = 0; // always 0 in vector reduction
    __uint32_t mask_selected = activeMask >> mask_start_idx;

    if(widen && sew == 3) {
        printf("VRED Modle, bad widen sew %d\n", input.sew);
        exit(1);
    }

    int numMax = (VLEN / 8) >> sew; // because LMUL always 1
    uint64_t src1;
    uint64_t selected_src2[numMax];
    for (int i = 0; i < numMax; i++) {
        switch (sew) {
            case 0: 
                selected_src2[i] = (is_signed) ? (*((__int8_t  *)input.src2 + i)) : (*((__uint8_t  *)input.src2 + i));
                break;
            case 1:
                selected_src2[i] = (is_signed) ? (*((__int16_t *)input.src2 + i)) : (*((__uint16_t *)input.src2 + i));
                break;
            case 2:
                selected_src2[i] = (is_signed) ? (*((__int32_t *)input.src2 + i)) : (*((__uint32_t *)input.src2 + i));
                break;
            case 3:
                selected_src2[i] = (*((__uint64_t *)input.src2 + i));
                break;
            default: printf("VRED Modle, bad sew %d\n", input.sew); exit(1);
        }
        int active = (mask_selected >> i) & 0x1;
        selected_src2[i] = active ? selected_src2[i] : (is_signed ? (((__uint64_t)-1) << ((8 << sew) - 1)) : 0); // set inactive element to min value
    }
    switch (sew) {
        case 0: src1 = is_signed ? (*((__int8_t *)&input.src1)) : (*((__uint8_t  *)&input.src1)); break;
        case 1: src1 = is_signed ? (*((__int16_t *)&input.src1)) : (*((__uint16_t *)&input.src1)); break;
        case 2: src1 = is_signed ? (*((__int32_t *)&input.src1)) : (*((__uint32_t *)&input.src1)); break;
        case 3: src1 = (*((__uint64_t *)&input.src1)); break;
        default: printf("VRED Modle, bad sew %d\n", input.sew); exit(1);
    }

    uint64_t result = src1;
    for(int i = 0; i < numMax; i++) {
        if(is_signed)
            result = ((__int64_t)result > (__int64_t)selected_src2[i]) ? result : selected_src2[i];
        else
            result = (result > selected_src2[i]) ? result : selected_src2[i];
    }

    // wrap later
    VecOutput output;
    if(input.vinfo.ta) {
        output.result[0] = (uint64_t)-1;
        output.result[1] = (uint64_t)-1;
    } else {
        output.result[0] = input.src3[0];
        output.result[1] = input.src3[1];
    }
    switch (sew) {
        case 0: 
            if(widen)
                *((uint16_t *)&output.result[0]) = (uint16_t)(result & 0xFFFF);
            else
                *((uint8_t  *)&output.result[0]) = (uint8_t)(result & 0xFF);
            break;
        case 1:
            if(widen)
                *((uint32_t *)&output.result[0]) = (uint32_t)(result & 0xFFFFFFFF);
            else
                *((uint16_t *)&output.result[0]) = (uint16_t)(result & 0xFFFF);
            break;
        case 2:
            if(widen)
                *((uint64_t *)&output.result[0]) = (uint64_t)(result);
            else
                *((uint32_t *)&output.result[0]) = (uint32_t)(result & 0xFFFFFFFF);
            break;
        case 3:
            *((uint64_t *)&output.result[0]) = (uint64_t)(result);
            break;
        default: printf("VRED Modle, bad sew %d\n", input.sew); exit(1);
    }

    return output;
}

VecOutput VGMReduction::get_output_vredmin(VecInput input) {
    int widen = 0;
    int is_signed = input.is_signed;

    // wrap later
    // int lmul = input.vinfo.vlmul; // lmul always equals 1 for vector reduction
    int sew = input.sew;
    int one[2] = {-1, -1};
    __uint128_t bodyMask =  (*(__uint128_t *)one >> input.vinfo.vstart << input.vinfo.vstart) 
                       & (*(__uint128_t *)one << (128-input.vinfo.vl) >> (128-input.vinfo.vl));
    __uint128_t activeMask = ((input.vinfo.vm == 0) ? *(__uint128_t *)input.src4 : (__uint128_t)-1) & bodyMask;
    int mask_start_idx = 0; // always 0 in vector reduction
    __uint32_t mask_selected = activeMask >> mask_start_idx;

    if(widen && sew == 3) {
        printf("VRED Module, bad widen sew %d\n", input.sew);
        exit(1);
    }

    int numMax = (VLEN / 8) >> sew; // because LMUL always 1
    uint64_t src1;
    uint64_t selected_src2[numMax];
    for (int i = 0; i < numMax; i++) {
        switch (sew) {
            case 0: 
                selected_src2[i] = (is_signed) ? (*((__int8_t  *)input.src2 + i)) : (*((__uint8_t  *)input.src2 + i));
                break;
            case 1:
                selected_src2[i] = (is_signed) ? (*((__int16_t *)input.src2 + i)) : (*((__uint16_t *)input.src2 + i));
                break;
            case 2:
                selected_src2[i] = (is_signed) ? (*((__int32_t *)input.src2 + i)) : (*((__uint32_t *)input.src2 + i));
                break;
            case 3:
                selected_src2[i] = (*((__uint64_t *)input.src2 + i));
                break;
            default: printf("VRED Modle, bad sew %d\n", input.sew); exit(1);
        }
        int active = (mask_selected >> i) & 0x1;
        selected_src2[i] = active ? selected_src2[i] : (is_signed ? ~(((__uint64_t)-1) << ((8 << sew) - 1)) : ~0); // set inactive element to max value
    }
    switch (sew) {
        case 0: src1 = is_signed ? (*((__int8_t *)&input.src1)) : (*((__uint8_t  *)&input.src1)); break;
        case 1: src1 = is_signed ? (*((__int16_t *)&input.src1)) : (*((__uint16_t *)&input.src1)); break;
        case 2: src1 = is_signed ? (*((__int32_t *)&input.src1)) : (*((__uint32_t *)&input.src1)); break;
        case 3: src1 = (*((__uint64_t *)&input.src1)); break;
        default: printf("VRED Modle, bad sew %d\n", input.sew); exit(1);
    }

    uint64_t result = src1;
    for(int i = 0; i < numMax; i++) {
        if(is_signed)
            result = ((__int64_t)result < (__int64_t)selected_src2[i]) ? result : selected_src2[i];
        else
            result = (result < selected_src2[i]) ? result : selected_src2[i];
    }

    // wrap later
    VecOutput output;
    if(input.vinfo.ta) {
        output.result[0] = (uint64_t)-1;
        output.result[1] = (uint64_t)-1;
    } else {
        output.result[0] = input.src3[0];
        output.result[1] = input.src3[1];
    }
    switch (sew) {
        case 0: 
            if(widen)
                *((uint16_t *)&output.result[0]) = (uint16_t)(result & 0xFFFF);
            else
                *((uint8_t  *)&output.result[0]) = (uint8_t)(result & 0xFF);
            break;
        case 1:
            if(widen)
                *((uint32_t *)&output.result[0]) = (uint32_t)(result & 0xFFFFFFFF);
            else
                *((uint16_t *)&output.result[0]) = (uint16_t)(result & 0xFFFF);
            break;
        case 2:
            if(widen)
                *((uint64_t *)&output.result[0]) = (uint64_t)(result);
            else
                *((uint32_t *)&output.result[0]) = (uint32_t)(result & 0xFFFFFFFF);
            break;
        case 3:
            *((uint64_t *)&output.result[0]) = (uint64_t)(result);
            break;
        default: printf("VRED Modle, bad sew %d\n", input.sew); exit(1);
    }

    return output;
}

VecOutput VGMReduction::get_output_vredand(VecInput input) {
    int widen = 0;
    int is_signed = 0;

    // wrap later
    // int lmul = input.vinfo.vlmul; // lmul always equals 1 for vector reduction
    int sew = input.sew;
    int one[2] = {-1, -1};
    __uint128_t bodyMask =  (*(__uint128_t *)one >> input.vinfo.vstart << input.vinfo.vstart) 
                       & (*(__uint128_t *)one << (128-input.vinfo.vl) >> (128-input.vinfo.vl));
    __uint128_t activeMask = ((input.vinfo.vm == 0) ? *(__uint128_t *)input.src4 : (__uint128_t)-1) & bodyMask;
    int mask_start_idx = 0; // always 0 in vector reduction
    __uint32_t mask_selected = activeMask >> mask_start_idx;

    if(widen && sew == 3) {
        printf("VRED Modle, bad widen sew %d\n", input.sew);
        exit(1);
    }

    int numMax = (VLEN / 8) >> sew; // because LMUL always 1
    uint64_t src1;
    uint64_t selected_src2[numMax];
    for (int i = 0; i < numMax; i++) {
        switch (sew) {
            case 0: 
                selected_src2[i] = (is_signed) ? (*((__int8_t  *)input.src2 + i)) : (*((__uint8_t  *)input.src2 + i));
                break;
            case 1:
                selected_src2[i] = (is_signed) ? (*((__int16_t *)input.src2 + i)) : (*((__uint16_t *)input.src2 + i));
                break;
            case 2:
                selected_src2[i] = (is_signed) ? (*((__int32_t *)input.src2 + i)) : (*((__uint32_t *)input.src2 + i));
                break;
            case 3:
                selected_src2[i] = (*((__uint64_t *)input.src2 + i));
                break;
            default: printf("VRED Modle, bad sew %d\n", input.sew); exit(1);
        }
        int active = (mask_selected >> i) & 0x1;
        selected_src2[i] = active ? selected_src2[i] : ((uint64_t)-1); // set inactive element to all 1s
    }
    switch (sew) {
        case 0: src1 = widen ? (*((__uint16_t *)&input.src1)) : (*((__uint8_t  *)&input.src1)); break;
        case 1: src1 = widen ? (*((__uint32_t *)&input.src1)) : (*((__uint16_t *)&input.src1)); break;
        case 2: src1 = widen ? (*((__uint64_t *)&input.src1)) : (*((__uint32_t *)&input.src1)); break;
        case 3: src1 = (*((__uint64_t *)&input.src1)); break;
        default: printf("VRED Modle, bad sew %d\n", input.sew); exit(1);
    }

    uint64_t result = src1;
    for(int i = 0; i < numMax; i++) {
        result &= selected_src2[i];
    }

    // wrap later
    VecOutput output;
    if(input.vinfo.ta) {
        output.result[0] = (uint64_t)-1;
        output.result[1] = (uint64_t)-1;
    } else {
        output.result[0] = input.src3[0];
        output.result[1] = input.src3[1];
    }
    switch (sew) {
        case 0: 
            if(widen)
                *((uint16_t *)&output.result[0]) = (uint16_t)(result & 0xFFFF);
            else
                *((uint8_t  *)&output.result[0]) = (uint8_t)(result & 0xFF);
            break;
        case 1:
            if(widen)
                *((uint32_t *)&output.result[0]) = (uint32_t)(result & 0xFFFFFFFF);
            else
                *((uint16_t *)&output.result[0]) = (uint16_t)(result & 0xFFFF);
            break;
        case 2:
            if(widen)
                *((uint64_t *)&output.result[0]) = (uint64_t)(result);
            else
                *((uint32_t *)&output.result[0]) = (uint32_t)(result & 0xFFFFFFFF);
            break;
        case 3:
            *((uint64_t *)&output.result[0]) = (uint64_t)(result);
            break;
        default: printf("VRED Modle, bad sew %d\n", input.sew); exit(1);
    }

    return output;
}

VecOutput VGMReduction::get_output_vredor(VecInput input) {
    int widen = 0;
    int is_signed = 0;

    // wrap later
    // int lmul = input.vinfo.vlmul; // lmul always equals 1 for vector reduction
    int sew = input.sew;
    int one[2] = {-1, -1};
    __uint128_t bodyMask =  (*(__uint128_t *)one >> input.vinfo.vstart << input.vinfo.vstart) 
                       & (*(__uint128_t *)one << (128-input.vinfo.vl) >> (128-input.vinfo.vl));
    __uint128_t activeMask = ((input.vinfo.vm == 0) ? *(__uint128_t *)input.src4 : (__uint128_t)-1) & bodyMask;
    int mask_start_idx = 0; // always 0 in vector reduction
    __uint32_t mask_selected = activeMask >> mask_start_idx;

    if(widen && sew == 3) {
        printf("VRED Modle, bad widen sew %d\n", input.sew);
        exit(1);
    }

    int numMax = (VLEN / 8) >> sew; // because LMUL always 1
    uint64_t src1;
    uint64_t selected_src2[numMax];
    for (int i = 0; i < numMax; i++) {
        switch (sew) {
            case 0: 
                selected_src2[i] = (is_signed) ? (*((__int8_t  *)input.src2 + i)) : (*((__uint8_t  *)input.src2 + i));
                break;
            case 1:
                selected_src2[i] = (is_signed) ? (*((__int16_t *)input.src2 + i)) : (*((__uint16_t *)input.src2 + i));
                break;
            case 2:
                selected_src2[i] = (is_signed) ? (*((__int32_t *)input.src2 + i)) : (*((__uint32_t *)input.src2 + i));
                break;
            case 3:
                selected_src2[i] = (*((__uint64_t *)input.src2 + i));
                break;
            default: printf("VRED Modle, bad sew %d\n", input.sew); exit(1);
        }
        int active = (mask_selected >> i) & 0x1;
        selected_src2[i] = active ? selected_src2[i] : 0; // set inactive element to 0
    }
    switch (sew) {
        case 0: src1 = widen ? (*((__uint16_t *)&input.src1)) : (*((__uint8_t  *)&input.src1)); break;
        case 1: src1 = widen ? (*((__uint32_t *)&input.src1)) : (*((__uint16_t *)&input.src1)); break;
        case 2: src1 = widen ? (*((__uint64_t *)&input.src1)) : (*((__uint32_t *)&input.src1)); break;
        case 3: src1 = (*((__uint64_t *)&input.src1)); break;
        default: printf("VRED Modle, bad sew %d\n", input.sew); exit(1);
    }

    uint64_t result = src1;
    for(int i = 0; i < numMax; i++) {
        result |= selected_src2[i];
    }

    // wrap later
    VecOutput output;
    if(input.vinfo.ta) {
        output.result[0] = (uint64_t)-1;
        output.result[1] = (uint64_t)-1;
    } else {
        output.result[0] = input.src3[0];
        output.result[1] = input.src3[1];
    }
    switch (sew) {
        case 0: 
            if(widen)
                *((uint16_t *)&output.result[0]) = (uint16_t)(result & 0xFFFF);
            else
                *((uint8_t  *)&output.result[0]) = (uint8_t)(result & 0xFF);
            break;
        case 1:
            if(widen)
                *((uint32_t *)&output.result[0]) = (uint32_t)(result & 0xFFFFFFFF);
            else
                *((uint16_t *)&output.result[0]) = (uint16_t)(result & 0xFFFF);
            break;
        case 2:
            if(widen)
                *((uint64_t *)&output.result[0]) = (uint64_t)(result);
            else
                *((uint32_t *)&output.result[0]) = (uint32_t)(result & 0xFFFFFFFF);
            break;
        case 3:
            *((uint64_t *)&output.result[0]) = (uint64_t)(result);
            break;
        default: printf("VRED Modle, bad sew %d\n", input.sew); exit(1);
    }

    return output;
}

VecOutput VGMReduction::get_output_vredxor(VecInput input) {
    int widen = 0;
    int is_signed = 0;

    // wrap later
    // int lmul = input.vinfo.vlmul; // lmul always equals 1 for vector reduction
    int sew = input.sew;
    int one[2] = {-1, -1};
    __uint128_t bodyMask =  (*(__uint128_t *)one >> input.vinfo.vstart << input.vinfo.vstart) 
                       & (*(__uint128_t *)one << (128-input.vinfo.vl) >> (128-input.vinfo.vl));
    __uint128_t activeMask = ((input.vinfo.vm == 0) ? *(__uint128_t *)input.src4 : (__uint128_t)-1) & bodyMask;
    int mask_start_idx = 0; // always 0 in vector reduction
    __uint32_t mask_selected = activeMask >> mask_start_idx;

    if(widen && sew == 3) {
        printf("VRED Modle, bad widen sew %d\n", input.sew);
        exit(1);
    }

    int numMax = (VLEN / 8) >> sew; // because LMUL always 1
    uint64_t src1;
    uint64_t selected_src2[numMax];
    for (int i = 0; i < numMax; i++) {
        switch (sew) {
            case 0: 
                selected_src2[i] = (is_signed) ? (*((__int8_t  *)input.src2 + i)) : (*((__uint8_t  *)input.src2 + i));
                break;
            case 1:
                selected_src2[i] = (is_signed) ? (*((__int16_t *)input.src2 + i)) : (*((__uint16_t *)input.src2 + i));
                break;
            case 2:
                selected_src2[i] = (is_signed) ? (*((__int32_t *)input.src2 + i)) : (*((__uint32_t *)input.src2 + i));
                break;
            case 3:
                selected_src2[i] = (*((__uint64_t *)input.src2 + i));
                break;
            default: printf("VRED Modle, bad sew %d\n", input.sew); exit(1);
        }
        int active = (mask_selected >> i) & 0x1;
        selected_src2[i] = active ? selected_src2[i] : 0; // set inactive element to 0
    }
    switch (sew) {
        case 0: src1 = widen ? (*((__uint16_t *)&input.src1)) : (*((__uint8_t  *)&input.src1)); break;
        case 1: src1 = widen ? (*((__uint32_t *)&input.src1)) : (*((__uint16_t *)&input.src1)); break;
        case 2: src1 = widen ? (*((__uint64_t *)&input.src1)) : (*((__uint32_t *)&input.src1)); break;
        case 3: src1 = (*((__uint64_t *)&input.src1)); break;
        default: printf("VRED Modle, bad sew %d\n", input.sew); exit(1);
    }

    uint64_t result = src1;
    for(int i = 0; i < numMax; i++) {
        result ^= selected_src2[i];
    }

    // wrap later
    VecOutput output;
    if(input.vinfo.ta) {
        output.result[0] = (uint64_t)-1;
        output.result[1] = (uint64_t)-1;
    } else {
        output.result[0] = input.src3[0];
        output.result[1] = input.src3[1];
    }
    switch (sew) {
        case 0: 
            if(widen)
                *((uint16_t *)&output.result[0]) = (uint16_t)(result & 0xFFFF);
            else
                *((uint8_t  *)&output.result[0]) = (uint8_t)(result & 0xFF);
            break;
        case 1:
            if(widen)
                *((uint32_t *)&output.result[0]) = (uint32_t)(result & 0xFFFFFFFF);
            else
                *((uint16_t *)&output.result[0]) = (uint16_t)(result & 0xFFFF);
            break;
        case 2:
            if(widen)
                *((uint64_t *)&output.result[0]) = (uint64_t)(result);
            else
                *((uint32_t *)&output.result[0]) = (uint32_t)(result & 0xFFFFFFFF);
            break;
        case 3:
            *((uint64_t *)&output.result[0]) = (uint64_t)(result);
            break;
        default: printf("VRED Modle, bad sew %d\n", input.sew); exit(1);
    }

    return output;
}

ElementOutput VGMReduction::calculation_e8(ElementInput  input) {ElementOutput rs; return rs;}
ElementOutput VGMReduction::calculation_e16(ElementInput input) {ElementOutput rs; return rs;}
ElementOutput VGMReduction::calculation_e32(ElementInput input) {ElementOutput rs; return rs;}
ElementOutput VGMReduction::calculation_e64(ElementInput input) {ElementOutput rs; return rs;}
