#include "km_math.h"
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <math.h>

// 简单实现用于验证接口正确性

void km_divide(int64_t sig1, int32_t scale1,
               int64_t sig2, int32_t scale2,
               int32_t target_scale, int32_t rounding,
               int64_t* out_sig, int32_t* out_scale) {

    if (sig2 == 0) {
        // 除零错误
        *out_sig = 0;
        *out_scale = 0;
        return;
    }

    // 先调整为整数除法：计算需要的位数
    // value = sig1 * 10^(-scale1) / (sig2 * 10^(-scale2))
    //       = (sig1 / sig2) * 10^(scale2 - scale1)
    // 结果 scale = scale1 - scale2

    // 为了避免小数，我们先放大到足够精度
    // 精度 = target_scale + 舍入精度(1位) + 当前精度缓冲

    int32_t natural_scale = scale1 - scale2;

    // 计算需要的放大倍数
    int32_t precision_scale = target_scale;
    if (precision_scale < natural_scale) {
        precision_scale = natural_scale;
    }
    // 额外加一位用于舍入判断
    precision_scale += 1;

    int32_t scale_up = precision_scale - natural_scale;
    int32_t scale_down = precision_scale - target_scale;

    // 放大被除数
    int64_t scaled_sig1 = sig1;
    for (int i = 0; i < scale_up; i++) {
        if (scaled_sig1 > INT64_MAX / 10) {
            // 溢出，fallback
            *out_sig = 0;
            *out_scale = 0;
            return;
        }
        scaled_sig1 *= 10;
    }

    // 执行整数除法
    int64_t quotient = scaled_sig1 / sig2;
    int64_t remainder = scaled_sig1 % sig2;

    // 现在需要缩小到 target_scale（除以 10^scale_down）
    int64_t divisor = 1;
    for (int i = 0; i < scale_down; i++) {
        divisor *= 10;
    }

    // 提取要舍入的部分
    int64_t result_main = quotient / divisor;
    int64_t result_frac = quotient % divisor;  // 这部分决定舍入

    // 舍入逻辑
    int abs_remainder = remainder < 0 ? -remainder : remainder;
    int abs_result_frac = result_frac < 0 ? -result_frac : result_frac;

    int should_increment = 0;

    // 计算舍入阈值（需要考虑 remainder 和 result_frac）
    // 真正的小数部分 = (result_frac * sig2 + remainder) / (sig2 * divisor)
    // 简化：比较 result_frac * 2 * sig2 + 2 * remainder 与 divisor * sig2

    switch (rounding) {
        case 0: // UP - always round away from zero
            should_increment = (result_frac != 0 || remainder != 0);
            if (quotient < 0) should_increment = -should_increment;
            break;
        case 1: // DOWN - never round up
            should_increment = 0;
            break;
        case 2: // CEILING - round toward positive infinity
            should_increment = (result_frac > 0 || remainder > 0);
            break;
        case 3: // FLOOR - round toward negative infinity
            should_increment = (result_frac < 0 || remainder < 0);
            break;
        case 4: { // HALF_UP - round half up (>0.5)
            // 检查是否 > 0.5
            int64_t threshold = divisor * sig2 / 2;
            int64_t frac_value = abs_result_frac * sig2 + abs_remainder;
            should_increment = (frac_value > threshold);
            if (quotient < 0) should_increment = -should_increment;
            break;
        }
        case 5: { // HALF_DOWN - round half down (>=0.5)
            int64_t threshold = divisor * sig2 / 2;
            int64_t frac_value = abs_result_frac * sig2 + abs_remainder;
            should_increment = (frac_value >= threshold);
            if (quotient < 0) should_increment = -should_increment;
            break;
        }
        case 6: { // HALF_EVEN - round half to even (银行家舍入)
            int64_t threshold = divisor * sig2 / 2;
            int64_t frac_value = abs_result_frac * sig2 + abs_remainder;
            if (frac_value > threshold) {
                should_increment = 1;
            } else if (frac_value == threshold) {
                // 恰好 0.5，看是否为偶数
                should_increment = (result_main % 2 != 0);
            } else {
                should_increment = 0;
            }
            if (quotient < 0) should_increment = -should_increment;
            break;
        }
        case 7: // UNNECESSARY - exact only
            should_increment = 0;
            if (result_frac != 0 || remainder != 0) {
                // 无法精确表示，标记错误
                result_main = 0;
            }
            break;
    }

    if (should_increment != 0) {
        if (should_increment > 0) {
            if (result_main < INT64_MAX) result_main++;
        } else {
            if (result_main > INT64_MIN) result_main--;
        }
    }

    *out_sig = result_main;
    *out_scale = target_scale;
}

void km_multiply(int64_t sig1, int32_t scale1,
                 int64_t sig2, int32_t scale2,
                 int64_t* out_sig, int32_t* out_scale) {

    // 简单乘法
    int64_t product_sig = sig1 * sig2;
    int32_t product_scale = scale1 + scale2;

    *out_sig = product_sig;
    *out_scale = product_scale;
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

    if (scale_diff > 0) {
        // 增加 scale（乘以 10^diff）
        int64_t result = sig;
        for (int i = 0; i < scale_diff; i++) {
            if (result > INT64_MAX / 10) {
                *out_sig = INT64_MAX;
                *out_scale = new_scale;
                return;
            }
            result *= 10;
        }
        *out_sig = result;
        *out_scale = new_scale;
    } else {
        // 减少 scale（除以 10^(-diff)，需要舍入）
        int64_t divisor = 1;
        for (int i = 0; i < -scale_diff; i++) {
            divisor *= 10;
        }

        int64_t quotient = sig / divisor;
        int64_t remainder = sig % divisor;

        // 舍入逻辑
        int64_t abs_remainder = remainder < 0 ? -remainder : remainder;
        int64_t half_divisor = divisor / 2;

        int should_increment = 0;
        switch (rounding) {
            case 0: // UP - always round away from zero
                should_increment = (remainder != 0) ? 1 : 0;
                if (sig < 0) should_increment = -should_increment;
                break;
            case 1: // DOWN - never round up
                should_increment = 0;
                break;
            case 2: // CEILING - round toward positive infinity
                should_increment = (remainder > 0) ? 1 : 0;
                break;
            case 3: // FLOOR - round toward negative infinity
                should_increment = (remainder < 0) ? 1 : 0;
                break;
            case 4: // HALF_UP - round half up (>0.5)
                should_increment = (abs_remainder * 2 > divisor) ? 1 : 0;
                if (sig < 0) should_increment = -should_increment;
                break;
            case 5: // HALF_DOWN - round half down (>=0.5)
                should_increment = (abs_remainder * 2 >= divisor) ? 1 : 0;
                if (sig < 0) should_increment = -should_increment;
                break;
            case 6: { // HALF_EVEN - round half to even
                if (abs_remainder * 2 > divisor) {
                    should_increment = 1;
                } else if (abs_remainder * 2 == divisor) {
                    // 恰好 0.5，看是否为偶数
                    should_increment = (quotient % 2 != 0) ? 1 : 0;
                } else {
                    should_increment = 0;
                }
                if (sig < 0) should_increment = -should_increment;
                break;
            }
            case 7: // UNNECESSARY - exact only
                should_increment = 0;
                if (remainder != 0) {
                    quotient = 0; // error indicator
                }
                break;
        }

        if (should_increment && quotient < INT64_MAX) {
            quotient++;
        }

        *out_sig = quotient;
        *out_scale = new_scale;
    }
}
