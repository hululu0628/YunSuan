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

}

VecOutput VGMFReduction::get_output_vfredusum(VecInput input) {

}

VecOutput VGMFReduction::get_output_vfredmin(VecInput input) {

}

VecOutput VGMFReduction::get_output_vfredmax(VecInput input) {

}

ElementOutput VGMFReduction::calculation_e16(ElementInput input) { VecOutput rs; return rs; }
ElementOutput VGMFReduction::calculation_e32(ElementInput input) { VecOutput rs; return rs; }
ElementOutput VGMFReduction::calculation_e64(ElementInput input) { VecOutput rs; return rs; }
