#include <stdio.h>
#include <stdint.h>
#include <immintrin.h>
#include <time.h>
#include <string.h>

#define ARRAY_SIZE 10000
#define ITERATIONS 1000

static int64_t a[ARRAY_SIZE], b[ARRAY_SIZE];
static volatile int64_t sink;  // 防止编译器优化

// 禁止内联以确保公平比较
__attribute__((noinline))
void multiply_scalar() {
    for (int i = 0; i < ARRAY_SIZE; i++) {
        sink = a[i] * b[i];  // 使用结果防止优化
    }
}

__attribute__((noinline))
void multiply_avx2_loadstore() {
    // AVX2 批量加载/存储版本
    int i = 0;
    for (; i + 8 <= ARRAY_SIZE; i += 8) {
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

        // 累积防止优化
        sink += tr[0] + tr[1] + tr[2] + tr[3] + tr[4] + tr[5] + tr[6] + tr[7];
    }

    // 剩余
    for (; i < ARRAY_SIZE; i++) {
        sink = a[i] * b[i];
    }
}

__attribute__((noinline))
void divide_scalar() {
    for (int i = 0; i < ARRAY_SIZE; i++) {
        sink = (b[i] != 0) ? a[i] / b[i] : 0;
    }
}

__attribute__((noinline))
void divide_avx2_batch() {
    int i = 0;
    for (; i + 8 <= ARRAY_SIZE; i += 8) {
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
        tr[0] = (tc[0] != 0) ? ta[0] / tc[0] : 0;
        tr[1] = (tc[1] != 0) ? ta[1] / tc[1] : 0;
        tr[2] = (tc[2] != 0) ? ta[2] / tc[2] : 0;
        tr[3] = (tc[3] != 0) ? ta[3] / tc[3] : 0;
        tr[4] = (td[0] != 0) ? tb[0] / td[0] : 0;
        tr[5] = (td[1] != 0) ? tb[1] / td[1] : 0;
        tr[6] = (td[2] != 0) ? tb[2] / td[2] : 0;
        tr[7] = (td[3] != 0) ? tb[3] / td[3] : 0;

        sink += tr[0];
    }
    for (; i < ARRAY_SIZE; i++) {
        sink = (b[i] != 0) ? a[i] / b[i] : 0;
    }
}

double benchmark(void (*func)(), const char* name) {
    struct timespec start, end;

    // Warmup
    for (int i = 0; i < 100; i++) {
        func();
    }

    clock_gettime(CLOCK_MONOTONIC, &start);
    for (int i = 0; i < ITERATIONS; i++) {
        func();
    }
    clock_gettime(CLOCK_MONOTONIC, &end);

    double elapsed_ms = (end.tv_sec - start.tv_sec) * 1000.0 + (end.tv_nsec - start.tv_nsec) / 1e6;
    double ns_per_op = elapsed_ms * 1e6 / (ITERATIONS * ARRAY_SIZE);

    printf("│ %-16s│ %10.2f ms │ %10.3f ns/op │", name, elapsed_ms, ns_per_op);
    return elapsed_ms;
}

int main() {
    printf("╔════════════════════════════════════════════════════════════╗\n");
    printf("║         AVX2 批处理性能测试 (C 侧，无 FFI 开销)             ║\n");
    printf("╚════════════════════════════════════════════════════════════╝\n\n");
    printf("配置: 数组=%d, 迭代=%d\n\n", ARRAY_SIZE, ITERATIONS);

    // 初始化数据
    for (int i = 0; i < ARRAY_SIZE; i++) {
        a[i] = 1000 + i;
        b[i] = 100 + i % 100;
    }

    printf("┌────────────────────┬──────────────┬──────────────┐\n");
    printf("│ 测试               │ 总时间       │ 单次 (ns/op) │\n");
    printf("├────────────────────┼──────────────┼──────────────┤\n");

    double scalar_mul = benchmark(multiply_scalar, "标量 Multiply");
    printf("\n");

    double avx2_mul = benchmark(multiply_avx2_loadstore, "AVX2 Multiply");
    printf(" %.2fx │\n", scalar_mul / avx2_mul);

    double scalar_div = benchmark(divide_scalar, "标量 Divide");
    printf("\n");

    double avx2_div = benchmark(divide_avx2_batch, "AVX2 Divide");
    printf(" %.2fx │\n", scalar_div / avx2_div);

    printf("└────────────────────┴──────────────┴──────────────┘\n\n");

    printf("═══════════════════════════════════════════════════════════════\n");
    printf("分析：\n\n");

    printf("1. AVX2 批量加载/存储的作用：\n");
    printf("   • 8 个操作数只需 2 条加载指令 (vs 8 条)\n");
    printf("   • 8 个结果只需 2 条存储指令 (vs 8 条)\n");
    printf("   • 减少指令数量，提高内存带宽利用率\n\n");

    printf("2. 但计算仍是标量：\n");
    printf("   • AVX2 没有 64 位整数乘法指令\n");
    printf("   • AVX2 没有整数除法指令\n");
    printf("   • 计算时间无法减少\n\n");

    printf("3. 为什么 AVX2 版本可能更慢：\n");
    printf("   • 额外的数据复制 (寄存器→栈→寄存器)\n");
    printf("   • 更复杂的代码降低流水线效率\n\n");

    printf("结论：\n");
    printf("   对 64 位整数运算，AVX2 的批处理能力有限\n");
    printf("   真正的 SIMD 优势需要向量化的计算指令\n");
    printf("   或使用浮点运算 (AVX2 完全支持)\n");

    return 0;
}
