#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

// ========== MKL/BLAS 配置 ==========

// 如果有 MKL，使用 MKL; 否则使用标准 libm
#ifdef USE_MKL
#include <mkl_vml.h>
#define VML_DIV vdDiv
#define VML_MUL vdMul
#else
// 标准 libm 备选方案
#include "km_math_optimized.h"
#endif

// ========== 编码/解码辅助函数 ==========

static inline uint64_t encode_result(int64_t sig, int32_t scale) {
    uint32_t sig_encoded;
    if (sig < 0) {
        sig_encoded = ((uint32_t)(-sig) << 1) | 1;
    } else {
        sig_encoded = ((uint32_t)sig << 1);
    }
    uint32_t scale_encoded = (uint32_t)scale;
    return ((uint64_t)scale_encoded << 32) | sig_encoded;
}

// ========== Non-Compact Path：使用 double 数组 ==========

/**
 * 批量除法 - Non-Compact Path（使用 double 优化）
 *
 * 输入：double 数组（大数已转换为 double）
 * 输出：编码结果数组
 *
 * 精度说明：
 * - double 有 53 位尾数，可精确表示约 15-17 位十进制数字
 * - 对于 >18 位的 BigDecimal，会有精度损失
 * - 适用于可接受浮点近似的场景
 */
void km_divide_batch_double(
    const double* a_array,
    const double* b_array,
    int32_t target_scale,
    uint64_t* results,
    int count) {

#ifdef USE_MKL
    // 使用 MKL VML 向量化除法
    VML_DIV(a_array, b_array, results, count);

    // 编码结果（注意：这里直接用 double 结果，会有精度损失）
    for (int i = 0; i < count; i++) {
        // 调整到目标 scale（乘以 10^scale）
        double scaled = results[i] * pow(10.0, target_scale);
        results[i] = encode_result((int64_t)round(scaled), target_scale);
    }
#else
    // 标准 libm 备选 - 手动向量化（每次处理 4 个）
    int i = 0;
    for (; i + 4 <= count; i += 4) {
        // 手动展开，模拟 SIMD
        results[i] = encode_result(
            (int64_t)round(a_array[i] / b_array[i] * pow(10.0, target_scale)),
            target_scale
        );
        results[i+1] = encode_result(
            (int64_t)round(a_array[i+1] / b_array[i+1] * pow(10.0, target_scale)),
            target_scale
        );
        results[i+2] = encode_result(
            (int64_t)round(a_array[i+2] / b_array[i+2] * pow(10.0, target_scale)),
            target_scale
        );
        results[i+3] = encode_result(
            (int64_t)round(a_array[i+3] / b_array[i+3] * pow(10.0, target_scale)),
            target_scale
        );
    }

    // 处理剩余元素
    for (; i < count; i++) {
        results[i] = encode_result(
            (int64_t)round(a_array[i] / b_array[i] * pow(10.0, target_scale)),
            target_scale
        );
    }
#endif
}

/**
 * 批量乘法 - Non-Compact Path（使用 double 优化）
 */
void km_multiply_batch_double(
    const double* a_array,
    const double* b_array,
    const int32_t* scale_a_array,
    const int32_t* scale_b_array,
    uint64_t* results,
    int count) {

#ifdef USE_MKL
    // 使用 MKL VML 向量化乘法
    double* temp = malloc(count * sizeof(double));
    VML_MUL(a_array, b_array, temp, count);

    for (int i = 0; i < count; i++) {
        int32_t result_scale = scale_a_array[i] + scale_b_array[i];
        results[i] = encode_result((int64_t)round(temp[i]), result_scale);
    }
    free(temp);
#else
    // 标准 libm 备选
    int i = 0;
    for (; i + 4 <= count; i += 4) {
        double p0 = a_array[i] * b_array[i];
        double p1 = a_array[i+1] * b_array[i+1];
        double p2 = a_array[i+2] * b_array[i+2];
        double p3 = a_array[i+3] * b_array[i+3];

        results[i] = encode_result((int64_t)round(p0), scale_a_array[i] + scale_b_array[i]);
        results[i+1] = encode_result((int64_t)round(p1), scale_a_array[i+1] + scale_b_array[i+1]);
        results[i+2] = encode_result((int64_t)round(p2), scale_a_array[i+2] + scale_b_array[i+2]);
        results[i+3] = encode_result((int64_t)round(p3), scale_a_array[i+3] + scale_b_array[i+3]);
    }

    for (; i < count; i++) {
        double p = a_array[i] * b_array[i];
        results[i] = encode_result((int64_t)round(p), scale_a_array[i] + scale_b_array[i]);
    }
#endif
}

/**
 * SetScale 批处理 - Non-Compact Path
 */
void km_setscale_batch_double(
    const double* a_array,
    const int32_t* old_scale_array,
    int32_t new_scale,
    uint64_t* results,
    int count) {

    double scale_factor = pow(10.0, new_scale);

    for (int i = 0; i < count; i++) {
        double old_scale_factor = pow(10.0, old_scale_array[i]);
        double scaled = a_array[i] * (scale_factor / old_scale_factor);
        results[i] = encode_result((int64_t)round(scaled), new_scale);
    }
}

// ========== 精度分析函数 ==========

/**
 * 计算精度损失估计
 * 返回：最大可精确表示的十进制位数
 */
int km_get_max_precise_digits() {
    return 15;  // double 的 53 位尾数 ≈ 15-17 位十进制
}

/**
 * 检查数值是否在 double 精确表示范围内
 */
int km_can_represent_precisely(double value) {
    if (fabs(value) > 1e15) {
        return 0;  // 超过精确表示范围
    }
    return 1;
}
