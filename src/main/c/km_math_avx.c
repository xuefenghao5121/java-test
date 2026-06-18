#include <stdint.h>
#include <immintrin.h>
#include <string.h>

// ========== 编码/解码辅助函数（从 km_math_optimized.c 复制） ==========

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

// ========== AVX 优化实现 ==========

void km_divide_batch_avx(
    const int64_t* sig1_array, const int32_t* scale1_array,
    const int64_t* sig2_array, const int32_t* scale2_array,
    int32_t target_scale, int32_t rounding,
    uint64_t* results, int count) {

    int i = 0;
    for (; i + 4 <= count; i += 4) {
        __m256d a_vals = _mm256_set_pd(
            (double)sig1_array[i+3], (double)sig1_array[i+2],
            (double)sig1_array[i+1], (double)sig1_array[i]
        );
        __m256d b_vals = _mm256_set_pd(
            (double)sig2_array[i+3], (double)sig2_array[i+2],
            (double)sig2_array[i+1], (double)sig2_array[i]
        );

        __m256d div_results = _mm256_div_pd(a_vals, b_vals);

        double temp[4];
        _mm256_storeu_pd(temp, div_results);

        for (int j = 0; j < 4; j++) {
            results[i+j] = encode_result((int64_t)temp[j], target_scale);
        }
    }

    for (; i < count; i++) {
        double result = (double)sig1_array[i] / (double)sig2_array[i];
        results[i] = encode_result((int64_t)result, target_scale);
    }
}

void km_multiply_batch_avx(
    const int64_t* sig1_array, const int32_t* scale1_array,
    const int64_t* sig2_array, const int32_t* scale2_array,
    uint64_t* results, int count) {

    int i = 0;
    for (; i + 4 <= count; i += 4) {
        __m256d a_vals = _mm256_set_pd(
            (double)sig1_array[i+3], (double)sig1_array[i+2],
            (double)sig1_array[i+1], (double)sig1_array[i]
        );
        __m256d b_vals = _mm256_set_pd(
            (double)sig2_array[i+3], (double)sig2_array[i+2],
            (double)sig2_array[i+1], (double)sig2_array[i]
        );

        __m256d mul_results = _mm256_mul_pd(a_vals, b_vals);

        double temp[4];
        _mm256_storeu_pd(temp, mul_results);

        for (int j = 0; j < 4; j++) {
            int32_t result_scale = scale1_array[i+j] + scale2_array[i+j];
            results[i+j] = encode_result((int64_t)temp[j], result_scale);
        }
    }

    for (; i < count; i++) {
        double result = (double)sig1_array[i] * (double)sig2_array[i];
        int32_t result_scale = scale1_array[i] + scale2_array[i];
        results[i] = encode_result((int64_t)result, result_scale);
    }
}

uint64_t km_divide_single_avx(int64_t sig1, int32_t scale1,
                               int64_t sig2, int32_t scale2,
                               int32_t target_scale, int32_t rounding) {
    if (sig2 == 0) {
        return encode_result(0, 0);
    }

    __m256d a = _mm256_set_pd(0, 0, 0, (double)sig1);
    __m256d b = _mm256_set_pd(0, 0, 0, (double)sig2);
    __m256d result = _mm256_div_pd(a, b);

    double temp;
    _mm256_storeu_pd(&temp, result);

    return encode_result((int64_t)temp, target_scale);
}

uint64_t km_multiply_single_avx(int64_t sig1, int32_t scale1,
                                int64_t sig2, int32_t scale2) {
    __m256d a = _mm256_set_pd(0, 0, 0, (double)sig1);
    __m256d b = _mm256_set_pd(0, 0, 0, (double)sig2);
    __m256d result = _mm256_mul_pd(a, b);

    double temp;
    _mm256_storeu_pd(&temp, result);

    int32_t result_scale = scale1 + scale2;
    return encode_result((int64_t)temp, result_scale);
}
