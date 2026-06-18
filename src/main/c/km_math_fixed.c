#include "km_math_fixed.h"
#include <stdint.h>
#include <string.h>

// 预计算 10^0 到 10^18 表
static const int64_t POW10[19] = {
    1LL, 10LL, 100LL, 1000LL, 10000LL, 100000LL, 1000000LL,
    10000000LL, 100000000LL, 1000000000LL, 10000000000LL,
    100000000000LL, 1000000000000LL, 10000000000000LL,
    100000000000000LL, 1000000000000000LL, 10000000000000000LL,
    100000000000000000LL, 1000000000000000000LL
};

// 优化的 divide - 返回 sig，scale 通过指针返回
int64_t km_divide(int64_t sig1, int32_t scale1,
                  int64_t sig2, int32_t scale2,
                  int32_t target_scale, int32_t rounding,
                  int32_t* out_scale) {

    if (sig2 == 0) {
        *out_scale = 0;
        return 0;
    }

    int32_t natural_scale = scale1 - scale2;
    int32_t scale_diff = target_scale - natural_scale;

    int64_t scaled_sig1 = sig1;

    // 放大/缩小被除数
    if (scale_diff >= 0 && scale_diff < 19) {
        int64_t factor = POW10[scale_diff];
        if (sig1 > INT64_MAX / factor) {
            *out_scale = target_scale;
            return INT64_MAX;
        }
        scaled_sig1 = sig1 * factor;
    } else if (scale_diff < 0 && -scale_diff < 19) {
        int64_t divisor = POW10[-scale_diff];
        scaled_sig1 = sig1 / divisor;
    }

    // 执行除法
    int64_t quotient = scaled_sig1 / sig2;
    int64_t remainder = scaled_sig1 % sig2;

    // 舍入处理
    if (scale_diff < 0 && -scale_diff < 19) {
        int64_t divisor = POW10[-scale_diff];
        int64_t result_main = quotient / divisor;
        int64_t result_frac = quotient % divisor;

        int64_t abs_remainder = remainder < 0 ? -remainder : remainder;
        int64_t abs_result_frac = result_frac < 0 ? -result_frac : result_frac;

        // HALF_UP 舍入
        int64_t threshold = divisor * sig2 / 2;
        int64_t frac_value = abs_result_frac * sig2 + abs_remainder;

        int should_increment = 0;
        switch (rounding) {
            case 4: // HALF_UP
                should_increment = (frac_value > threshold) ? 1 : 0;
                if (quotient < 0) should_increment = -should_increment;
                break;
            default:
                should_increment = 0;
                break;
        }

        if (should_increment != 0) {
            if (should_increment > 0 && result_main < INT64_MAX) result_main++;
            else if (should_increment < 0 && result_main > INT64_MIN) result_main--;
        }

        *out_scale = target_scale;
        return result_main;
    }

    *out_scale = target_scale;
    return quotient;
}

// 优化的 multiply - 返回 sig，scale 通过指针返回
int64_t km_multiply(int64_t sig1, int32_t scale1,
                    int64_t sig2, int32_t scale2,
                    int32_t* out_scale) {

    int64_t product_sig = sig1 * sig2;
    int32_t product_scale = scale1 + scale2;

    *out_scale = product_scale;
    return product_sig;
}

// 优化的 setScale - 返回 sig，scale 通过指针返回
int64_t km_setscale(int64_t sig, int32_t scale,
                    int32_t new_scale, int32_t rounding,
                    int32_t* out_scale) {

    if (new_scale == scale) {
        *out_scale = scale;
        return sig;
    }

    int32_t scale_diff = new_scale - scale;

    if (scale_diff >= 0 && scale_diff < 19) {
        int64_t factor = POW10[scale_diff];
        if (sig > INT64_MAX / factor) {
            *out_scale = new_scale;
            return INT64_MAX;
        }
        *out_scale = new_scale;
        return sig * factor;
    }

    if (scale_diff < 0 && -scale_diff < 19) {
        int64_t divisor = POW10[-scale_diff];
        int64_t quotient = sig / divisor;
        int64_t remainder = sig % divisor;

        int64_t abs_remainder = remainder < 0 ? -remainder : remainder;
        int64_t threshold = divisor / 2;

        int should_increment = 0;
        switch (rounding) {
            case 4: // HALF_UP
                should_increment = (abs_remainder * 2 > divisor) ? 1 : 0;
                if (sig < 0) should_increment = -should_increment;
                break;
            default:
                should_increment = 0;
                break;
        }

        if (should_increment != 0) {
            if (should_increment > 0 && quotient < INT64_MAX) quotient++;
            else if (should_increment < 0 && quotient > INT64_MIN) quotient--;
        }

        *out_scale = new_scale;
        return quotient;
    }

    *out_scale = scale;
    return sig;
}
