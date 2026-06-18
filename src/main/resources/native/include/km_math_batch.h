#ifndef KM_MATH_BATCH_H
#define KM_MATH_BATCH_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * 批量除法 - AVX2 优化
 *
 * @param n          批量大小
 * @param sig1       被除数 significand 数组
 * @param scale1     被除数 scale 数组
 * @param sig2       除数 significand 数组
 * @param scale2     除数 scale 数组
 * @param target_scale 统一的目标 scale
 * @param rounding   舍入模式
 * @param out_sig    [输出] 结果 significand 数组
 * @param out_scale  [输出] 结果 scale 数组
 */
void km_divide_batch(
    int n,
    const int64_t* sig1, const int32_t* scale1,
    const int64_t* sig2, const int32_t* scale2,
    int32_t target_scale, int32_t rounding,
    int64_t* out_sig, int32_t* out_scale
);

/**
 * 批量乘法 - AVX2 优化
 */
void km_multiply_batch(
    int n,
    const int64_t* sig1, const int32_t* scale1,
    const int64_t* sig2, const int32_t* scale2,
    int64_t* out_sig, int32_t* out_scale
);

/**
 * 批量 setScale - AVX2 优化
 */
void km_setscale_batch(
    int n,
    const int64_t* sig, const int32_t* scale,
    int32_t new_scale, int32_t rounding,
    int64_t* out_sig, int32_t* out_scale
);

#ifdef __cplusplus
}
#endif

#endif // KM_MATH_BATCH_H
