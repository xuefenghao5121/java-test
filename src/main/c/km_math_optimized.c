#include <stdint.h>
#include <stdlib.h>
#include "km_math_optimized.h"

// ========== 编码/解码辅助函数 ==========

/**
 * 编码结果：将 sig 和 scale 编码为单个 uint64_t
 *
 * 编码格式：
 *   [63:32] scale (32位有符号)
 *   [31:0]  sig   (32位无符号)
 *
 * 限制：sig 必须 >= 0 且 < 2^32
 *       scale 必须在 int32 范围内
 */
static inline uint64_t encode_result(int64_t sig, int32_t scale) {
    // Zig-zag 编码处理负数 sig
    uint32_t sig_encoded;
    if (sig < 0) {
        sig_encoded = ((uint32_t)(-sig) << 1) | 1;
    } else {
        sig_encoded = ((uint32_t)sig << 1);
    }

    // Scale 直接使用（保证范围）
    uint32_t scale_encoded = (uint32_t)scale;

    return ((uint64_t)scale_encoded << 32) | sig_encoded;
}

/**
 * 解码 sig（从编码值）
 */
static inline int64_t decode_sig(uint64_t encoded) {
    uint32_t sig_encoded = (uint32_t)(encoded & 0xFFFFFFFFULL);

    // Zig-zag 解码
    if (sig_encoded & 1) {
        return -((int64_t)(sig_encoded >> 1));
    } else {
        return (int64_t)(sig_encoded >> 1);
    }
}

/**
 * 解码 scale（从编码值）
 */
static inline int32_t decode_scale(uint64_t encoded) {
    return (int32_t)(encoded >> 32);
}

// ========== 舍入函数 ==========

static int64_t apply_rounding(int64_t sig, int32_t current_scale,
                              int32_t target_scale, int32_t rounding) {
    int32_t scale_diff = current_scale - target_scale;

    if (scale_diff <= 0) return sig;

    int64_t divisor = 1;
    for (int i = 0; i < scale_diff; i++) divisor *= 10;

    int64_t quotient = sig / divisor;
    int64_t remainder = sig % divisor;

    int is_negative = (sig < 0);
    if (is_negative) remainder = -remainder;

    switch (rounding) {
        case 0: // UP
            if (remainder != 0) {
                quotient = is_negative ? quotient - 1 : quotient + 1;
            }
            break;
        case 1: // DOWN
            break;
        case 2: // CEILING
            if (!is_negative && remainder != 0) {
                quotient = quotient + 1;
            }
            break;
        case 3: // FLOOR
            if (is_negative && remainder != 0) {
                quotient = quotient - 1;
            }
            break;
        case 4: // HALF_UP
            if (remainder >= (divisor >> 1) + (divisor & 1)) {
                quotient = is_negative ? quotient - 1 : quotient + 1;
            }
            break;
        case 5: // HALF_DOWN
            if (remainder > (divisor >> 1)) {
                quotient = is_negative ? quotient - 1 : quotient + 1;
            }
            break;
        case 6: // HALF_EVEN
            if (remainder > (divisor >> 1) ||
                (remainder == (divisor >> 1) && (quotient & 1))) {
                quotient = is_negative ? quotient - 1 : quotient + 1;
            }
            break;
        case 7: // UNNECESSARY
            if (remainder != 0) {
                // 应该抛异常，这里返回原值
                return sig;
            }
            break;
    }

    return quotient * divisor;
}

// ========== 方案 1：编码返回值实现 ==========

uint64_t km_divide_encoded(int64_t sig1, int32_t scale1,
                            int64_t sig2, int32_t scale2,
                            int32_t target_scale, int32_t rounding) {
    if (sig2 == 0) {
        return encode_result(0, 0);
    }

    // BigDecimal 除法逻辑：
    // value1 / value2 = (sig1 / sig2) * 10^(-(scale1 - scale2))
    //
    // 例如：100 / 3 = 33.333... (scale=0)
    //       要得到 scale=2: 3333 / 100 = 33.33

    // 先计算原始除法
    int64_t quotient = sig1 / sig2;
    int64_t remainder = sig1 % sig2;
    int32_t result_scale = scale1 - scale2;

    // 调整到目标 scale
    // 如果需要增加小数位数，需要乘以 10
    while (result_scale < target_scale) {
        quotient = quotient * 10;
        remainder = remainder * 10;
        int64_t add = remainder / sig2;
        quotient += add;
        remainder = remainder % sig2;
        result_scale++;
    }

    // 如果需要减少小数位数，需要舍入
    if (result_scale > target_scale) {
        quotient = apply_rounding(quotient, result_scale, target_scale, rounding);
        result_scale = target_scale;
    }

    return encode_result(quotient, result_scale);
}

uint64_t km_multiply_encoded(int64_t sig1, int32_t scale1,
                             int64_t sig2, int32_t scale2) {
    int64_t result_sig = sig1 * sig2;
    int32_t result_scale = scale1 + scale2;

    return encode_result(result_sig, result_scale);
}

uint64_t km_setscale_encoded(int64_t sig, int32_t scale,
                             int32_t new_scale, int32_t rounding) {
    int64_t result_sig = apply_rounding(sig, scale, new_scale, rounding);

    return encode_result(result_sig, new_scale);
}

// ========== 方案 2：指针参数实现 ==========

void km_divide_ptr(int64_t sig1, int32_t scale1,
                   int64_t sig2, int32_t scale2,
                   int32_t target_scale, int32_t rounding,
                   int64_t* out_sig, int32_t* out_scale) {
    if (sig2 == 0) {
        *out_sig = 0;
        *out_scale = 0;
        return;
    }

    // 同样的逻辑
    int64_t quotient = sig1 / sig2;
    int64_t remainder = sig1 % sig2;
    int32_t result_scale = scale1 - scale2;

    while (result_scale < target_scale) {
        quotient = quotient * 10;
        remainder = remainder * 10;
        int64_t add = remainder / sig2;
        quotient += add;
        remainder = remainder % sig2;
        result_scale++;
    }

    if (result_scale > target_scale) {
        quotient = apply_rounding(quotient, result_scale, target_scale, rounding);
        result_scale = target_scale;
    }

    *out_sig = quotient;
    *out_scale = result_scale;
}

void km_multiply_ptr(int64_t sig1, int32_t scale1,
                     int64_t sig2, int32_t scale2,
                     int64_t* out_sig, int32_t* out_scale) {
    *out_sig = sig1 * sig2;
    *out_scale = scale1 + scale2;
}

void km_setscale_ptr(int64_t sig, int32_t scale,
                     int32_t new_scale, int32_t rounding,
                     int64_t* out_sig, int32_t* out_scale) {
    *out_sig = apply_rounding(sig, scale, new_scale, rounding);
    *out_scale = new_scale;
}

// ========== 方案 3：双返回值实现 ==========

int64_t km_divide_dual(int64_t sig1, int32_t scale1,
                       int64_t sig2, int32_t scale2,
                       int32_t target_scale, int32_t rounding,
                       int32_t* out_scale) {
    if (sig2 == 0) {
        *out_scale = 0;
        return 0;
    }

    int64_t result_sig = sig1 / sig2;
    result_sig = apply_rounding(result_sig, 0, target_scale, rounding);

    *out_scale = target_scale;
    return result_sig;
}
