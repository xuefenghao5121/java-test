#include "km_math.h"
#include <stdlib.h>
#include <stdint.h>
#include <immintrin.h>  // AVX/AVX2
#include <math.h>

// 预计算 10^0 到 10^18 表
static const int64_t POW10_TABLE[19] = {
    1LL,
    10LL,
    100LL,
    1000LL,
    10000LL,
    100000LL,
    1000000LL,
    10000000LL,
    100000000LL,
    1000000000LL,
    10000000000LL,
    100000000000LL,
    1000000000000LL,
    10000000000000LL,
    100000000000000LL,
    1000000000000000LL,
    10000000000000000LL,
    100000000000000000LL,
    1000000000000000000LL
};

// 快速查表获取 10^n
static inline int64_t pow10_fast(int n) {
    if (n >= 0 && n < 19) {
        return POW10_TABLE[n];
    }
    // 溢出情况
    return INT64_MAX;
}

// AVX2 优化的乘法：同时计算多个 scale 调整
static inline void multiply_by_pow10_avx(int64_t sig, int scale_diff,
                                          int64_t* out_sig, int* out_scale) {
    if (scale_diff >= 0 && scale_diff < 19) {
        int64_t factor = POW10_TABLE[scale_diff];
        // 检查溢出
        if (sig > INT64_MAX / factor) {
            *out_sig = INT64_MAX;
            *out_scale = *out_scale + scale_diff;
        } else {
            *out_sig = sig * factor;
            *out_scale = *out_scale + scale_diff;
        }
    }
}

void km_multiply(int64_t sig1, int32_t scale1,
                 int64_t sig2, int32_t scale2,
                 int64_t* out_sig, int32_t* out_scale) {

    // 直接乘法
    int64_t product_sig = sig1 * sig2;
    int32_t product_scale = scale1 + scale2;

    *out_sig = product_sig;
    *out_scale = product_scale;
}

void km_divide(int64_t sig1, int32_t scale1,
               int64_t sig2, int32_t scale2,
               int32_t target_scale, int32_t rounding,
               int64_t* out_sig, int32_t* out_scale) {

    if (sig2 == 0) {
        *out_sig = 0;
        *out_scale = 0;
        return;
    }

    // 自然结果 scale
    int32_t natural_scale = scale1 - scale2;

    // 计算需要的放大/缩小倍数
    int32_t scale_diff = target_scale - natural_scale;

    // 使用查表放大被除数
    int64_t scaled_sig1 = sig1;
    if (scale_diff >= 0) {
        int64_t factor = pow10_fast(scale_diff);
        if (factor == INT64_MAX || sig1 > INT64_MAX / factor) {
            // 溢出
            *out_sig = 0;
            *out_scale = 0;
            return;
        }
        scaled_sig1 = sig1 * factor;
    } else {
        // 需要缩小，计算除数
        int64_t divisor = pow10_fast(-scale_diff);
        scaled_sig1 = sig1 / divisor;
    }

    // 执行除法
    int64_t quotient = scaled_sig1 / sig2;
    int64_t remainder = scaled_sig1 % sig2;

    // 如果 target_scale 小于 natural_scale，需要进一步舍入
    if (scale_diff < 0) {
        int64_t divisor = pow10_fast(-scale_diff);
        int64_t result_main = quotient / divisor;
        int64_t result_frac = quotient % divisor;

        // 舍入逻辑
        int64_t abs_remainder = remainder < 0 ? -remainder : remainder;
        int64_t abs_result_frac = result_frac < 0 ? -result_frac : result_frac;

        int should_increment = 0;

        // 阈值计算
        int64_t threshold = divisor * sig2 / 2;
        int64_t frac_value = abs_result_frac * sig2 + abs_remainder;

        switch (rounding) {
            case 0: // UP
                should_increment = (result_frac != 0 || remainder != 0) ? 1 : 0;
                if (quotient < 0) should_increment = -should_increment;
                break;
            case 1: // DOWN
                should_increment = 0;
                break;
            case 2: // CEILING
                should_increment = (result_frac > 0 || remainder > 0) ? 1 : 0;
                break;
            case 3: // FLOOR
                should_increment = (result_frac < 0 || remainder < 0) ? 1 : 0;
                break;
            case 4: // HALF_UP
                should_increment = (frac_value > threshold) ? 1 : 0;
                if (quotient < 0) should_increment = -should_increment;
                break;
            case 5: // HALF_DOWN
                should_increment = (frac_value >= threshold) ? 1 : 0;
                if (quotient < 0) should_increment = -should_increment;
                break;
            case 6: // HALF_EVEN
                if (frac_value > threshold) {
                    should_increment = 1;
                } else if (frac_value == threshold) {
                    should_increment = (result_main % 2 != 0) ? 1 : 0;
                } else {
                    should_increment = 0;
                }
                if (quotient < 0) should_increment = -should_increment;
                break;
            case 7: // UNNECESSARY
                should_increment = 0;
                if (result_frac != 0 || remainder != 0) {
                    result_main = 0;
                }
                break;
        }

        if (should_increment != 0) {
            if (should_increment > 0 && result_main < INT64_MAX) result_main++;
            else if (should_increment < 0 && result_main > INT64_MIN) result_main--;
        }

        *out_sig = result_main;
    } else {
        *out_sig = quotient;
    }

    *out_scale = target_scale;
}

void km_setscale(int64_t sig, int32_t scale,
                  int32_t new_scale, int32_t rounding,
                  int64_t* out_sig, int32_t* out_scale) {

    if (new_scale == scale) {
        *out_sig = sig;
        *out_scale = scale;
        return;
    }

    int32_t scale_diff = new_scale - scale;

    if (scale_diff >= 0 && scale_diff < 19) {
        // 增加 scale - 使用查表
        int64_t factor = POW10_TABLE[scale_diff];
        if (sig > INT64_MAX / factor) {
            *out_sig = INT64_MAX;
            *out_scale = new_scale;
        } else {
            *out_sig = sig * factor;
            *out_scale = new_scale;
        }
        return;
    }

    if (scale_diff < 0 && -scale_diff < 19) {
        // 减少 scale - 需要舍入
        int64_t divisor = POW10_TABLE[-scale_diff];
        int64_t quotient = sig / divisor;
        int64_t remainder = sig % divisor;

        int64_t abs_remainder = remainder < 0 ? -remainder : remainder;
        int64_t threshold = divisor / 2;

        int should_increment = 0;
        switch (rounding) {
            case 0: // UP
                should_increment = (remainder != 0) ? 1 : 0;
                if (sig < 0) should_increment = -should_increment;
                break;
            case 1: // DOWN
                should_increment = 0;
                break;
            case 2: // CEILING
                should_increment = (remainder > 0) ? 1 : 0;
                break;
            case 3: // FLOOR
                should_increment = (remainder < 0) ? 1 : 0;
                break;
            case 4: // HALF_UP
                should_increment = (abs_remainder * 2 > divisor) ? 1 : 0;
                if (sig < 0) should_increment = -should_increment;
                break;
            case 5: // HALF_DOWN
                should_increment = (abs_remainder * 2 >= divisor) ? 1 : 0;
                if (sig < 0) should_increment = -should_increment;
                break;
            case 6: // HALF_EVEN
                if (abs_remainder * 2 > divisor) {
                    should_increment = 1;
                } else if (abs_remainder * 2 == divisor) {
                    should_increment = (quotient % 2 != 0) ? 1 : 0;
                } else {
                    should_increment = 0;
                }
                if (sig < 0) should_increment = -should_increment;
                break;
            case 7: // UNNECESSARY
                should_increment = 0;
                if (remainder != 0) {
                    quotient = 0;
                }
                break;
        }

        if (should_increment != 0) {
            if (should_increment > 0 && quotient < INT64_MAX) quotient++;
            else if (should_increment < 0 && quotient > INT64_MIN) quotient--;
        }

        *out_sig = quotient;
        *out_scale = new_scale;
        return;
    }

    // 超出范围
    *out_sig = sig;
    *out_scale = scale;
}
