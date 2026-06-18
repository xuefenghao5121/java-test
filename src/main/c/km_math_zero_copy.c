#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include "km_math_zero_copy.h"

// ========== 辅助函数 ==========

/**
 * 10 的幂次表（用于快速计算）
 */
static const double POW10[] = {
    1.0, 10.0, 100.0, 1000.0, 10000.0,
    1e5, 1e6, 1e7, 1e8, 1e9, 1e10,
    1e11, 1e12, 1e13, 1e14, 1e15, 1e16, 1e17, 1e18
};

/**
 * 计算调整后的 scale
 *
 * value = sig * 10^(-scale)
 * new_value = (sig * 10^(delta)) * 10^(-new_scale)
 */
static int64_t adjust_scale(int64_t sig, int32_t old_scale, int32_t new_scale) {
    int32_t delta = old_scale - new_scale;

    if (delta == 0) return sig;

    if (delta > 0) {
        // 需要乘以 10^delta（扩大）
        if (delta <= 18) {
            return sig * (int64_t)POW10[delta];
        }
        // 超出表范围，使用循环
        int64_t factor = 1;
        for (int i = 0; i < delta; i++) factor *= 10;
        return sig * factor;
    } else {
        // 需要除以 10^(-delta)（缩小）
        delta = -delta;
        if (delta <= 18) {
            return sig / (int64_t)POW10[delta];
        }
        int64_t factor = 1;
        for (int i = 0; i < delta; i++) factor *= 10;
        return sig / factor;
    }
}

/**
 * 应用舍入模式
 *
 * RoundingMode ordinal (Java):
 * 0 = UP, 1 = DOWN, 2 = CEILING, 3 = FLOOR,
 * 4 = HALF_UP, 5 = HALF_DOWN, 6 = HALF_EVEN, 7 = UNNECESSARY
 */
static int64_t apply_rounding(int64_t sig, int32_t scale, int32_t target_scale, int rounding) {
    int32_t scale_diff = scale - target_scale;

    // 不需要舍入
    if (scale_diff == 0) return sig;

    // 精度已经足够或更高
    if (scale_diff < 0) {
        return sig;
    }

    // 需要降低精度，进行舍入
    int64_t divisor = 1;
    for (int i = 0; i < scale_diff; i++) divisor *= 10;

    int64_t quotient = sig / divisor;
    int64_t remainder = sig % divisor;

    // 处理负数
    int is_negative = (sig < 0);
    if (is_negative) {
        remainder = -remainder;
    }

    int64_t result = quotient;

    switch (rounding) {
        case 0: // UP (向远离零方向)
            if (remainder != 0) {
                result = is_negative ? quotient - 1 : quotient + 1;
            }
            break;

        case 1: // DOWN (向零方向)
            result = quotient;
            break;

        case 2: // CEILING (向正无穷)
            if (!is_negative && remainder != 0) {
                result = quotient + 1;
            }
            break;

        case 3: // FLOOR (向负无穷)
            if (is_negative && remainder != 0) {
                result = quotient - 1;
            }
            break;

        case 4: // HALF_UP (四舍五入)
            if (remainder >= divisor / 2 + (divisor % 2)) {
                result = is_negative ? quotient - 1 : quotient + 1;
            }
            break;

        case 5: // HALF_DOWN (五舍六入)
            if (remainder > divisor / 2) {
                result = is_negative ? quotient - 1 : quotient + 1;
            }
            break;

        case 6: // HALF_EVEN (银行家舍入)
            if (remainder > divisor / 2 || (remainder == divisor / 2 && (quotient & 1))) {
                result = is_negative ? quotient - 1 : quotient + 1;
            }
            break;

        case 7: // UNNECESSARY (必须精确)
            if (remainder != 0) {
                // 理论上应该抛异常，这里返回原值
                return sig;
            }
            break;

        default:
            break;
    }

    return result * divisor;
}

// ========== 零拷贝接口实现 ==========

/**
 * 临时缓冲区 - 用于除法中间结果
 */
typedef struct {
    double adjusted_value;
    int32_t result_scale;
} divide_temp_t;

km_result_t km_divide_struct(int64_t sig1, int32_t scale1,
                              int64_t sig2, int32_t scale2,
                              int32_t target_scale, int32_t rounding)
{
    km_result_t result = {0, 0};

    if (sig2 == 0) {
        // 除零错误
        result.sig = 0;
        result.scale = 0;
        return result;
    }

    // 计算实际除法
    // value1 / value2 = (sig1 * 10^(-scale1)) / (sig2 * 10^(-scale2))
    //                 = (sig1 / sig2) * 10^(-(scale1 - scale2))

    // 先调整到相同 scale
    int32_t max_scale = (scale1 > scale2) ? scale1 : scale2;
    int64_t adjusted1 = adjust_scale(sig1, scale1, max_scale);
    int64_t adjusted2 = adjust_scale(sig2, scale2, max_scale);

    // 执行整数除法
    int64_t quotient = adjusted1 / adjusted2;
    int32_t result_scale_unrounded = 0;  // 两个数 scale 相同，结果 scale 为 0

    // 应用舍入
    int64_t rounded = apply_rounding(quotient, result_scale_unrounded, target_scale, rounding);

    result.sig = rounded;
    result.scale = target_scale;

    return result;
}

km_result_t km_multiply_struct(int64_t sig1, int32_t scale1,
                               int64_t sig2, int32_t scale2)
{
    km_result_t result;

    // (sig1 * 10^(-scale1)) * (sig2 * 10^(-scale2))
    // = (sig1 * sig2) * 10^(-(scale1 + scale2))

    result.sig = sig1 * sig2;
    result.scale = scale1 + scale2;

    return result;
}

km_result_t km_setscale_struct(int64_t sig, int32_t scale,
                                int32_t new_scale, int32_t rounding)
{
    km_result_t result;

    // 应用舍入
    int64_t rounded = apply_rounding(sig, scale, new_scale, rounding);

    result.sig = rounded;
    result.scale = new_scale;

    return result;
}

// ========== 批量接口实现 ==========

void km_divide_batch(int n,
                     const int64_t* sig1, const int32_t* scale1,
                     const int64_t* sig2, const int32_t* scale2,
                     int32_t target_scale, int32_t rounding,
                     int64_t* out_sig, int32_t* out_scale)
{
    // 简单的循环实现 - 可以优化为 SIMD
    for (int i = 0; i < n; i++) {
        km_result_t result = km_divide_struct(
            sig1[i], scale1[i],
            sig2[i], scale2[i],
            target_scale, rounding
        );
        out_sig[i] = result.sig;
        out_scale[i] = result.scale;
    }
}
