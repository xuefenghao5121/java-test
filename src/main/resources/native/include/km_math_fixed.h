#ifndef KM_MATH_FIXED_H
#define KM_MATH_FIXED_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * 优化的 BigDecimal 接口 - 返回值传递
 *
 * 与原版区别：
 * - 返回 int64_t (significand) 而非 void
 * - scale 通过单个指针输出（而非两个）
 *
 * 这样减少了内存写入操作，提高性能。
 */

/**
 * 除法 - 返回 significand，scale 通过指针输出
 */
int64_t km_divide(
    int64_t sig1, int32_t scale1,
    int64_t sig2, int32_t scale2,
    int32_t target_scale, int32_t rounding,
    int32_t* out_scale
);

/**
 * 乘法 - 返回 significand
 */
int64_t km_multiply(
    int64_t sig1, int32_t scale1,
    int64_t sig2, int32_t scale2,
    int32_t* out_scale
);

/**
 * 设置 scale - 返回 significand
 */
int64_t km_setscale(
    int64_t sig, int32_t scale,
    int32_t new_scale, int32_t rounding,
    int32_t* out_scale
);

#ifdef __cplusplus
}
#endif

#endif // KM_MATH_FIXED_H
