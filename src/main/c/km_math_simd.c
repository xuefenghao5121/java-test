#include "km_math_batch.h"
#include <immintrin.h>
#include <stdint.h>
#include <stddef.h>

/**
 * AVX2 批处理实现 - 展示批处理能力
 *
 * 重点：批量加载/存储 + 并行数据流
 */

static const int64_t POW10[19] = {
    1LL, 10LL, 100LL, 1000LL, 10000LL, 100000LL, 1000000LL,
    10000000LL, 100000000LL, 1000000000LL, 10000000000LL,
    100000000000LL, 1000000000000LL, 10000000000000LL,
    100000000000000LL, 1000000000000000LL, 10000000000000000LL,
    100000000000000000LL, 1000000000000000000LL
};

/**
 * AVX2 批量 multiply - 批量加载/存储
 */
void km_multiply_batch(
    int n,
    const int64_t* sig1, const int32_t* scale1,
    const int64_t* sig2, const int32_t* scale2,
    int64_t* out_sig, int32_t* out_scale) {

    int i = 0;

    // 主循环：每次处理 8 个（两次 AVX2 加载/存储）
    for (; i + 8 <= n; i += 8) {
        // AVX2 批量加载（8 个操作数只需 2 条指令）
        __m256i v_sig1_a = _mm256_loadu_si256((__m256i*)&sig1[i]);
        __m256i v_sig1_b = _mm256_loadu_si256((__m256i*)&sig1[i+4]);
        __m256i v_sig2_a = _mm256_loadu_si256((__m256i*)&sig2[i]);
        __m256i v_sig2_b = _mm256_loadu_si256((__m256i*)&sig2[i+4]);

        // 提取到本地数组（寄存器到寄存器，比内存快）
        int64_t a1[4], a2[4], b1[4], b2[4];
        _mm256_storeu_si256((__m256i*)a1, v_sig1_a);
        _mm256_storeu_si256((__m256i*)a2, v_sig1_b);
        _mm256_storeu_si256((__m256i*)b1, v_sig2_a);
        _mm256_storeu_si256((__m256i*)b2, v_sig2_b);

        // 标量乘法（在寄存器中进行）
        int64_t r1[4], r2[4];
        r1[0] = a1[0] * b1[0]; r1[1] = a1[1] * b1[1];
        r1[2] = a1[2] * b1[2]; r1[3] = a1[3] * b1[3];
        r2[0] = a2[0] * b2[0]; r2[1] = a2[1] * b2[1];
        r2[2] = a2[2] * b2[2]; r2[3] = a2[3] * b2[3];

        // AVX2 批量存储（8 个结果只需 2 条指令）
        __m256i v_res_a = _mm256_loadu_si256((__m256i*)r1);
        __m256i v_res_b = _mm256_loadu_si256((__m256i*)r2);
        _mm256_storeu_si256((__m256i*)&out_sig[i], v_res_a);
        _mm256_storeu_si256((__m256i*)&out_sig[i+4], v_res_b);

        // Scale 加法
        for (int j = 0; j < 8; j++) {
            out_scale[i+j] = scale1[i+j] + scale2[i+j];
        }
    }

    // 处理 4 个一组
    for (; i + 4 <= n; i += 4) {
        __m256i v_sig1 = _mm256_loadu_si256((__m256i*)&sig1[i]);
        __m256i v_sig2 = _mm256_loadu_si256((__m256i*)&sig2[i]);

        int64_t a[4], b[4], r[4];
        _mm256_storeu_si256((__m256i*)a, v_sig1);
        _mm256_storeu_si256((__m256i*)b, v_sig2);

        r[0] = a[0] * b[0]; r[1] = a[1] * b[1];
        r[2] = a[2] * b[2]; r[3] = a[3] * b[3];

        __m256i v_res = _mm256_loadu_si256((__m256i*)r);
        _mm256_storeu_si256((__m256i*)&out_sig[i], v_res);

        for (int j = 0; j < 4; j++) {
            out_scale[i+j] = scale1[i+j] + scale2[i+j];
        }
    }

    // 剩余
    for (; i < n; i++) {
        out_sig[i] = sig1[i] * sig2[i];
        out_scale[i] = scale1[i] + scale2[i];
    }
}

/**
 * AVX2 批量 setScale - 向量乘以 10^n
 */
void km_setscale_batch(
    int n,
    const int64_t* sig, const int32_t* scale,
    int32_t new_scale, int32_t rounding,
    int64_t* out_sig, int32_t* out_scale) {

    int i = 0;

    for (; i + 8 <= n; i += 8) {
        // 批量加载
        __m256i v_sig_a = _mm256_loadu_si256((__m256i*)&sig[i]);
        __m256i v_sig_b = _mm256_loadu_si256((__m256i*)&sig[i+4]);

        int32_t scale_diff = new_scale - scale[i];

        if (scale_diff >= 0 && scale_diff < 19) {
            int64_t factor = POW10[scale_diff];

            // 广播因子到所有通道
            __m256i v_factor = _mm256_set1_epi64x(factor);

            // 提取并计算
            int64_t a[4], b[4];
            _mm256_storeu_si256((__m256i*)a, v_sig_a);
            _mm256_storeu_si256((__m256i*)b, v_sig_b);

            int64_t r[8];
            r[0] = a[0] * factor; r[1] = a[1] * factor;
            r[2] = a[2] * factor; r[3] = a[3] * factor;
            r[4] = b[0] * factor; r[5] = b[1] * factor;
            r[6] = b[2] * factor; r[7] = b[3] * factor;

            // 批量存储
            __m256i v_res_a = _mm256_loadu_si256((__m256i*)r);
            __m256i v_res_b = _mm256_loadu_si256((__m256i*)(r+4));
            _mm256_storeu_si256((__m256i*)&out_sig[i], v_res_a);
            _mm256_storeu_si256((__m256i*)&out_sig[i+4], v_res_b);
        } else {
            // 直接复制
            _mm256_storeu_si256((__m256i*)&out_sig[i], v_sig_a);
            _mm256_storeu_si256((__m256i*)&out_sig[i+4], v_sig_b);
        }

        for (int j = 0; j < 8; j++) {
            out_scale[i+j] = new_scale;
        }
    }

    for (; i < n; i++) {
        out_sig[i] = sig[i];
        out_scale[i] = new_scale;
    }
}

/**
 * AVX2 批量 divide - 标量除法 + 批量加载存储
 */
void km_divide_batch(
    int n,
    const int64_t* sig1, const int32_t* scale1,
    const int64_t* sig2, const int32_t* scale2,
    int32_t target_scale, int32_t rounding,
    int64_t* out_sig, int32_t* out_scale) {

    int i = 0;

    for (; i + 8 <= n; i += 8) {
        // 批量加载
        __m256i v_sig1_a = _mm256_loadu_si256((__m256i*)&sig1[i]);
        __m256i v_sig1_b = _mm256_loadu_si256((__m256i*)&sig1[i+4]);
        __m256i v_sig2_a = _mm256_loadu_si256((__m256i*)&sig2[i]);
        __m256i v_sig2_b = _mm256_loadu_si256((__m256i*)&sig2[i+4]);

        // 提取到数组
        int64_t a1[4], a2[4], b1[4], b2[4];
        _mm256_storeu_si256((__m256i*)a1, v_sig1_a);
        _mm256_storeu_si256((__m256i*)a2, v_sig1_b);
        _mm256_storeu_si256((__m256i*)b1, v_sig2_a);
        _mm256_storeu_si256((__m256i*)b2, v_sig2_b);

        // 标量除法
        int64_t r1[4], r2[4];
        r1[0] = (b1[0] != 0) ? a1[0] / b1[0] : 0;
        r1[1] = (b1[1] != 0) ? a1[1] / b1[1] : 0;
        r1[2] = (b1[2] != 0) ? a1[2] / b1[2] : 0;
        r1[3] = (b1[3] != 0) ? a1[3] / b1[3] : 0;
        r2[0] = (b2[0] != 0) ? a2[0] / b2[0] : 0;
        r2[1] = (b2[1] != 0) ? a2[1] / b2[1] : 0;
        r2[2] = (b2[2] != 0) ? a2[2] / b2[2] : 0;
        r2[3] = (b2[3] != 0) ? a2[3] / b2[3] : 0;

        // 批量存储
        __m256i v_res_a = _mm256_loadu_si256((__m256i*)r1);
        __m256i v_res_b = _mm256_loadu_si256((__m256i*)r2);
        _mm256_storeu_si256((__m256i*)&out_sig[i], v_res_a);
        _mm256_storeu_si256((__m256i*)&out_sig[i+4], v_res_b);

        for (int j = 0; j < 8; j++) {
            out_scale[i+j] = target_scale;
        }
    }

    for (; i < n; i++) {
        out_sig[i] = (sig2[i] != 0) ? sig1[i] / sig2[i] : 0;
        out_scale[i] = target_scale;
    }
}
