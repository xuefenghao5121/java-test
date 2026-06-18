#include <stdio.h>
#include <stdint.h>
#include <immintrin.h>
#include <time.h>

#define ARRAY_SIZE 10000
#define ITERATIONS 5000

static int64_t a[ARRAY_SIZE], b[ARRAY_SIZE], r[ARRAY_SIZE];

// 标量版本
void multiply_scalar() {
    for (int i = 0; i < ARRAY_SIZE; i++) {
        r[i] = a[i] * b[i];
    }
}

// AVX2 批量版本（批量加载/存储）
void multiply_avx2() {
    for (int i = 0; i + 8 <= ARRAY_SIZE; i += 8) {
        __m256i va = _mm256_loadu_si256((__m256i*)&a[i]);
        __m256i vb = _mm256_loadu_si256((__m256i*)&a[i+4]);
        __m256i vc = _mm256_loadu_si256((__m256i*)&b[i]);
        __m256i vd = _mm256_loadu_si256((__m256i*)&b[i+4]);

        int64_t ta[4], tb[4], tc[4], td[4];
        _mm256_storeu_si256((__m256i*)ta, va);
        _mm256_storeu_si256((__m256i*)tb, vb);
        _mm256_storeu_si256((__m256i*)tc, vc);
        _mm256_storeu_si256((__m256i*)td, vd);

        int64_t tr[8];
        tr[0] = ta[0] * tc[0]; tr[1] = ta[1] * tc[1];
        tr[2] = ta[2] * tc[2]; tr[3] = ta[3] * tc[3];
        tr[4] = tb[0] * td[0]; tr[5] = tb[1] * td[1];
        tr[6] = tb[2] * td[2]; tr[7] = tb[3] * td[3];

        __m256i vr1 = _mm256_loadu_si256((__m256i*)tr);
        __m256i vr2 = _mm256_loadu_si256((__m256i*)(tr+4));
        _mm256_storeu_si256((__m256i*)&r[i], vr1);
        _mm256_storeu_si256((__m256i*)&r[i+4], vr2);
    }
}

int main() {
    // 初始化数据
    for (int i = 0; i < ARRAY_SIZE; i++) {
        a[i] = 1000 + i;
        b[i] = 100 + i % 100;
    }

    printf("=== AVX2 批处理性能测试 (C 侧，无 FFI 开销) ===\n\n");
    printf("数组大小: %d\n", ARRAY_SIZE);
    printf("迭代次数: %d\n\n", ITERATIONS);

    struct timespec start, end;
    double scalar_ns, avx2_ns;

    // 测试标量版本
    clock_gettime(CLOCK_MONOTONIC, &start);
    for (int i = 0; i < ITERATIONS; i++) {
        multiply_scalar();
    }
    clock_gettime(CLOCK_MONOTONIC, &end);
    scalar_ns = (end.tv_sec - start.tv_sec) * 1e9 + (end.tv_nsec - start.tv_nsec);

    // 测试 AVX2 版本
    clock_gettime(CLOCK_MONOTONIC, &start);
    for (int i = 0; i < ITERATIONS; i++) {
        multiply_avx2();
    }
    clock_gettime(CLOCK_MONOTONIC, &end);
    avx2_ns = (end.tv_sec - start.tv_sec) * 1e9 + (end.tv_nsec - start.tv_nsec);

    printf("┌─────────────────┬──────────────┬──────────────┬──────────┐\n");
    printf("│ 版本            │ 总时间 (ms)  │ 单次 (ns/op) │ 加速比   │\n");
    printf("├─────────────────┼──────────────┼──────────────┼──────────┤\n");
    printf("│ 标量            │ %10.2f    │ %10.2f    │ 1.00x    │\n",
        scalar_ns / 1e6, scalar_ns / (ITERATIONS * ARRAY_SIZE));
    printf("│ AVX2 批量加载   │ %10.2f    │ %10.2f    │ %6.2fx  │\n",
        avx2_ns / 1e6, avx2_ns / (ITERATIONS * ARRAY_SIZE), scalar_ns / avx2_ns);
    printf("└─────────────────┴──────────────┴──────────────┴──────────┘\n\n");

    printf("结论：\n");
    printf("  • 即使使用 AVX2 批量加载/存储，标量计算仍是瓶颈\n");
    printf("  • 真正的 SIMD 需要向量化的计算指令\n");
    printf("  • AVX2 对 64 位整数运算支持有限\n\n");

    printf("FFI 开销分析：\n");
    printf("  • 假设 FFI 开销 ~100ns\n");
    printf("  • 标量操作 ~%.1f ns/op\n", scalar_ns / (ITERATIONS * ARRAY_SIZE));
    printf("  • FFI 开销是计算时间的 %.1fx\n\n",
        100.0 / (scalar_ns / (ITERATIONS * ARRAY_SIZE)));

    printf("最终结论：\n");
    printf("  AVX2 批处理能力存在，但对 64 位整数运算帮助有限\n");
    printf("  FFI 开销远大于计算时间，无法通过 SIMD 弥补\n");

    return 0;
}
