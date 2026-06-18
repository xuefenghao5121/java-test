#include "km_math_batch.h"
#include <immintrin.h>
#include <stdint.h>
#include <stddef.h>

// 预计算 10^0 到 10^18 表
static const int64_t POW10[19] = {
    1LL, 10LL, 100LL, 1000LL, 10000LL, 100000LL, 1000000LL,
    10000000LL, 100000000LL, 1000000000LL, 10000000000LL,
    100000000000LL, 1000000000000LL, 10000000000000LL,
    100000000000000LL, 1000000000000000LL, 10000000000000000LL,
    100000000000000000LL, 1000000000000000000LL
};

// 单个 divide 实现（内联以提高性能）
static inline void divide_one(
    int64_t sig1, int32_t scale1,
    int64_t sig2, int32_t scale2,
    int32_t target_scale, int32_t rounding,
    int64_t* out_sig, int32_t* out_scale) {

    if (sig2 == 0) {
        *out_sig = 0;
        *out_scale = 0;
        return;
    }

    int32_t natural_scale = scale1 - scale2;
    int32_t scale_diff = target_scale - natural_scale;

    int64_t scaled_sig1 = sig1;

    // 放大
    if (scale_diff >= 0 && scale_diff < 19) {
        int64_t factor = POW10[scale_diff];
        if (sig1 > INT64_MAX / factor) {
            *out_sig = INT64_MAX;
            *out_scale = target_scale;
            return;
        }
        scaled_sig1 = sig1 * factor;
    } else if (scale_diff < 0 && -scale_diff < 19) {
        int64_t divisor = POW10[-scale_diff];
        scaled_sig1 = sig1 / divisor;
    }

    // 执行除法
    int64_t quotient = scaled_sig1 / sig2;
    int64_t remainder = scaled_sig1 % sig2;

    if (scale_diff < 0 && -scale_diff < 19) {
        int64_t divisor = POW10[-scale_diff];
        int64_t result_main = quotient / divisor;
        int64_t result_frac = quotient % divisor;

        // HALF_UP 舍入
        int64_t abs_remainder = remainder < 0 ? -remainder : remainder;
        int64_t abs_result_frac = result_frac < 0 ? -result_frac : result_frac;
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

        *out_sig = result_main;
    } else {
        *out_sig = quotient;
    }

    *out_scale = target_scale;
}

void km_divide_batch(
    int n,
    const int64_t* sig1, const int32_t* scale1,
    const int64_t* sig2, const int32_t* scale2,
    int32_t target_scale, int32_t rounding,
    int64_t* out_sig, int32_t* out_scale) {

    // 主循环：每次处理 8 个 (AVX2 256-bit = 4 x int64, 我们用逻辑展开)
    int i = 0;
    int batch_size = 8;

    for (; i + batch_size <= n; i += batch_size) {
        // 手动展开 8 次，避免循环开销
        divide_one(sig1[i], scale1[i], sig2[i], scale2[i],
                   target_scale, rounding, &out_sig[i], &out_scale[i]);
        divide_one(sig1[i+1], scale1[i+1], sig2[i+1], scale2[i+1],
                   target_scale, rounding, &out_sig[i+1], &out_scale[i+1]);
        divide_one(sig1[i+2], scale1[i+2], sig2[i+2], scale2[i+2],
                   target_scale, rounding, &out_sig[i+2], &out_scale[i+2]);
        divide_one(sig1[i+3], scale1[i+3], sig2[i+3], scale2[i+3],
                   target_scale, rounding, &out_sig[i+3], &out_scale[i+3]);
        divide_one(sig1[i+4], scale1[i+4], sig2[i+4], scale2[i+4],
                   target_scale, rounding, &out_sig[i+4], &out_scale[i+4]);
        divide_one(sig1[i+5], scale1[i+5], sig2[i+5], scale2[i+5],
                   target_scale, rounding, &out_sig[i+5], &out_scale[i+5]);
        divide_one(sig1[i+6], scale1[i+6], sig2[i+6], scale2[i+6],
                   target_scale, rounding, &out_sig[i+6], &out_scale[i+6]);
        divide_one(sig1[i+7], scale1[i+7], sig2[i+7], scale2[i+7],
                   target_scale, rounding, &out_sig[i+7], &out_scale[i+7]);
    }

    // 处理剩余元素
    for (; i < n; i++) {
        divide_one(sig1[i], scale1[i], sig2[i], scale2[i],
                   target_scale, rounding, &out_sig[i], &out_scale[i]);
    }
}

void km_multiply_batch(
    int n,
    const int64_t* sig1, const int32_t* scale1,
    const int64_t* sig2, const int32_t* scale2,
    int64_t* out_sig, int32_t* out_scale) {

    // 主循环：每次处理 8 个
    int i = 0;
    int batch_size = 8;

    for (; i + batch_size <= n; i += batch_size) {
        out_sig[i] = sig1[i] * sig2[i];
        out_scale[i] = scale1[i] + scale2[i];

        out_sig[i+1] = sig1[i+1] * sig2[i+1];
        out_scale[i+1] = scale1[i+1] + scale2[i+1];

        out_sig[i+2] = sig1[i+2] * sig2[i+2];
        out_scale[i+2] = scale1[i+2] + scale2[i+2];

        out_sig[i+3] = sig1[i+3] * sig2[i+3];
        out_scale[i+3] = scale1[i+3] + scale2[i+3];

        out_sig[i+4] = sig1[i+4] * sig2[i+4];
        out_scale[i+4] = scale1[i+4] + scale2[i+4];

        out_sig[i+5] = sig1[i+5] * sig2[i+5];
        out_scale[i+5] = scale1[i+5] + scale2[i+5];

        out_sig[i+6] = sig1[i+6] * sig2[i+6];
        out_scale[i+6] = scale1[i+6] + scale2[i+6];

        out_sig[i+7] = sig1[i+7] * sig2[i+7];
        out_scale[i+7] = scale1[i+7] + scale2[i+7];
    }

    // 处理剩余
    for (; i < n; i++) {
        out_sig[i] = sig1[i] * sig2[i];
        out_scale[i] = scale1[i] + scale2[i];
    }
}

void km_setscale_batch(
    int n,
    const int64_t* sig, const int32_t* scale,
    int32_t new_scale, int32_t rounding,
    int64_t* out_sig, int32_t* out_scale) {

    int i = 0;
    int batch_size = 8;

    for (; i + batch_size <= n; i += batch_size) {
        int32_t scale_diff = new_scale - scale[i];

        if (scale_diff >= 0 && scale_diff < 19) {
            int64_t factor = POW10[scale_diff];
            if (sig[i] > INT64_MAX / factor) {
                out_sig[i] = INT64_MAX;
            } else {
                out_sig[i] = sig[i] * factor;
            }
            out_scale[i] = new_scale;
        } else if (scale_diff < 0 && -scale_diff < 19) {
            int64_t divisor = POW10[-scale_diff];
            out_sig[i] = sig[i] / divisor;
            out_scale[i] = new_scale;
        } else {
            out_sig[i] = sig[i];
            out_scale[i] = scale[i];
        }

        // ... 展开其他 7 个（省略以简化）
        for (int j = 1; j < 8 && i + j < n; j++) {
            int32_t sd = new_scale - scale[i+j];
            if (sd >= 0 && sd < 19) {
                int64_t f = POW10[sd];
                out_sig[i+j] = (sig[i+j] > INT64_MAX / f) ? INT64_MAX : sig[i+j] * f;
                out_scale[i+j] = new_scale;
            } else if (sd < 0 && -sd < 19) {
                out_sig[i+j] = sig[i+j] / POW10[-sd];
                out_scale[i+j] = new_scale;
            } else {
                out_sig[i+j] = sig[i+j];
                out_scale[i+j] = scale[i+j];
            }
        }
    }
}
