#ifndef KM_MATH_H
#define KM_MATH_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * 鲲鹏数学库 - BigDecimal 优化接口
 *
 * <p>面向 Java Panama FFI 对接的 C 接口规范。
 * 所有函数使用 km_ 前缀避免与现有 libm 符号冲突。
 *
 * <p>数据表示：BigDecimal = significand × 10^(-scale)
 * <ul>
 * <li>significand：有符号整数，使用 int64_t 表示（compact 路径限制 18 位）</li>
 * <li>scale：小数点后的位数（负数表示 ×10^|scale|）</li>
 * </ul>
 *
 * <p>示例：
 * <ul>
 * <li>"123.45" → sig=12345, scale=2</li>
 * <li>"1.23E3" → sig=123, scale=-1</li>
 * </ul>
 *
 * <p>舍入模式（与 java.math.RoundingMode.ordinal() 一致）：
 * <ul>
 * <li>0: UP - 远离零</li>
 * <li>1: DOWN - 趋向零</li>
 * <li>2: CEILING - 趋向正无穷</li>
 * <li>3: FLOOR - 趋向负无穷</li>
 * <li>4: HALF_UP - 五舍六入（最常用）</li>
 * <li>5: HALF_DOWN - 五舍六入（向零方向）</li>
 * <li>6: HALF_EVEN - 银行家舍入</li>
 * <li>7: UNNECESSARY - 不需要舍入，精确计算</li>
 * </ul>
 */

/**
 * 除法：result = (sig1 × 10^-scale1) / (sig2 × 10^-scale2)
 *
 * @param sig1         被除数的 significand
 * @param scale1       被除数的 scale
 * @param sig2         除数的 significand（不能为 0）
 * @param scale2       除数的 scale
 * @param target_scale 结果的目标 scale
 * @param rounding     舍入模式（0-7，见上方说明）
 * @param out_sig      [输出] 结果的 significand
 * @param out_scale    [输出] 结果的 scale
 */
void km_divide(
    int64_t sig1, int32_t scale1,
    int64_t sig2, int32_t scale2,
    int32_t target_scale, int32_t rounding,
    int64_t* out_sig,
    int32_t* out_scale
);

/**
 * 乘法：result = (sig1 × 10^-scale1) × (sig2 × 10^-scale2)
 *
 * @param sig1      第一个因子的 significand
 * @param scale1    第一个因子的 scale
 * @param sig2      第二个因子的 significand
 * @param scale2    第二个因子的 scale
 * @param out_sig   [输出] 结果的 significand
 * @param out_scale [输出] 结果的 scale
 */
void km_multiply(
    int64_t sig1, int32_t scale1,
    int64_t sig2, int32_t scale2,
    int64_t* out_sig,
    int32_t* out_scale
);

/**
 * 设置 scale：调整 value = sig × 10^-scale 到 new_scale
 *
 * @param sig        原始 significand
 * @param scale      原始 scale
 * @param new_scale  目标 scale
 * @param rounding   舍入模式（0-7）
 * @param out_sig    [输出] 结果的 significand
 * @param out_scale  [输出] 结果的 scale
 */
void km_setscale(
    int64_t sig, int32_t scale,
    int32_t new_scale, int32_t rounding,
    int64_t* out_sig,
    int32_t* out_scale
);

#ifdef __cplusplus
}
#endif

#endif // KM_MATH_H
