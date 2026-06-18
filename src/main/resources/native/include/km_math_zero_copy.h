#ifndef KM_MATH_ZERO_COPY_H
#define KM_MATH_ZERO_COPY_H

#include <stdint.h>
#include <math.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * 结果结构体 - 用于零拷贝返回
 *
 * Panama FFI 支持 struct 返回值，可以通过寄存器传递
 * 避免 Arena 内存分配开销
 */
typedef struct {
    int64_t sig;        /* significand */
    int32_t scale;      /* scale (小数位数) */
    /* 4 bytes padding implicit */
} km_result_t;

/**
 * Divide - 返回 struct（零拷贝）
 *
 * @param sig1        被除数 significand
 * @param scale1      被除数 scale
 * @param sig2        除数 significand
 * @param scale2      除数 scale
 * @param target_scale 目标精度
 * @param rounding    舍入模式 (0-7, 对应 RoundingMode ordinal)
 * @return            {sig, scale} 结果
 */
km_result_t km_divide_struct(int64_t sig1, int32_t scale1,
                              int64_t sig2, int32_t scale2,
                              int32_t target_scale, int32_t rounding);

/**
 * Multiply - 返回 struct（零拷贝）
 */
km_result_t km_multiply_struct(int64_t sig1, int32_t scale1,
                               int64_t sig2, int32_t scale2);

/**
 * SetScale - 返回 struct（零拷贝）
 */
km_result_t km_setscale_struct(int64_t sig, int32_t scale,
                                int32_t new_scale, int32_t rounding);

/**
 * 批量 Divide - 用于摊薄 FFI 开销
 *
 * @param n            批量大小
 * @param sig1         被除数 significand 数组
 * @param scale1       被除数 scale 数组
 * @param sig2         除数 significand 数组
 * @param scale2       除数 scale 数组
 * @param target_scale 目标精度
 * @param rounding     舍入模式
 * @param out_sig      输出 significand 数组
 * @param out_scale    输出 scale 数组
 */
void km_divide_batch(int n,
                     const int64_t* sig1, const int32_t* scale1,
                     const int64_t* sig2, const int32_t* scale2,
                     int32_t target_scale, int32_t rounding,
                     int64_t* out_sig, int32_t* out_scale);

#ifdef __cplusplus
}
#endif

#endif /* KM_MATH_ZERO_COPY_H */
