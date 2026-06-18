#ifndef KM_MATH_OPTIMIZED_H
#define KM_MATH_OPTIMIZED_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// ========== 方案 1：编码返回值（推荐）==========

/**
 * Divide - 使用编码返回值（避免 Arena）
 *
 * 编码格式：uint64_t = (高32位=scale) | (低32位=sig_低32位)
 *
 * 限制：sig 必须 < 2^32（BigDecimal compact path 满足）
 */
uint64_t km_divide_encoded(int64_t sig1, int32_t scale1,
                            int64_t sig2, int32_t scale2,
                            int32_t target_scale, int32_t rounding);

uint64_t km_multiply_encoded(int64_t sig1, int32_t scale1,
                             int64_t sig2, int32_t scale2);

uint64_t km_setscale_encoded(int64_t sig, int32_t scale,
                             int32_t new_scale, int32_t rounding);

// ========== 方案 2：指针参数（需要预分配缓冲区）==========

/**
 * Divide - 使用指针参数（caller 提供内存）
 *
 * Java 侧需要预分配输出缓冲区
 */
void km_divide_ptr(int64_t sig1, int32_t scale1,
                   int64_t sig2, int32_t scale2,
                   int32_t target_scale, int32_t rounding,
                   int64_t* out_sig, int32_t* out_scale);

void km_multiply_ptr(int64_t sig1, int32_t scale1,
                     int64_t sig2, int32_t scale2,
                     int64_t* out_sig, int32_t* out_scale);

void km_setscale_ptr(int64_t sig, int32_t scale,
                     int32_t new_scale, int32_t rounding,
                     int64_t* out_sig, int32_t* out_scale);

// ========== 方案 3：双返回值（使用寄存器）==========

/**
 * Divide - 返回 sig，scale 通过第二个返回值
 *
 * 注意：Panama FFI 对多返回值支持有限
 * 这在 C 侧有效，但 Java 侧需要特殊处理
 */
int64_t km_divide_dual(int64_t sig1, int32_t scale1,
                       int64_t sig2, int32_t scale2,
                       int32_t target_scale, int32_t rounding,
                       int32_t* out_scale);

#ifdef __cplusplus
}
#endif

#endif /* KM_MATH_OPTIMIZED_H */
