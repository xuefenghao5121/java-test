#include <stdio.h>
#include <stdint.h>
#include <time.h>
#include <stdlib.h>

#define ITERATIONS 10000000

typedef struct {
    int64_t sig;
    int32_t scale;
} km_result_t;

// 防止编译器优化的 volatile
volatile int64_t sink;

// 模拟除法计算
__attribute__((noinline))
km_result_t divide_simple(int64_t sig1, int64_t sig2) {
    km_result_t result;
    result.sig = sig1 / sig2;
    result.scale = 0;
    return result;
}

// 测试 C 原生性能
double benchmark_c_native() {
    struct timespec start, end;
    
    // Warmup
    for (int i = 0; i < 100000; i++) {
        km_result_t r = divide_simple(1000 + i, 3 + i % 10);
        sink = r.sig;
    }
    
    clock_gettime(CLOCK_MONOTONIC, &start);
    for (int i = 0; i < ITERATIONS; i++) {
        km_result_t r = divide_simple(1000 + i, 3 + i % 10);
        sink = r.sig;
    }
    clock_gettime(CLOCK_MONOTONIC, &end);
    
    double elapsed_ns = (end.tv_sec - start.tv_sec) * 1e9 + (end.tv_nsec - start.tv_nsec);
    return elapsed_ns / ITERATIONS;
}

// 测试函数调用开销（空函数）
__attribute__((noinline))
void empty_function() {
    // 空
}

double benchmark_empty_call() {
    struct timespec start, end;
    
    for (int i = 0; i < 100000; i++) {
        empty_function();
    }
    
    clock_gettime(CLOCK_MONOTONIC, &start);
    for (int i = 0; i < ITERATIONS; i++) {
        empty_function();
    }
    clock_gettime(CLOCK_MONOTONIC, &end);
    
    double elapsed_ns = (end.tv_sec - start.tv_sec) * 1e9 + (end.tv_nsec - start.tv_nsec);
    return elapsed_ns / ITERATIONS;
}

int main() {
    printf("=== C 函数调用开销分析 ===\n\n");
    
    double empty_ns = benchmark_empty_call();
    double c_native_ns = benchmark_c_native();
    double calc_only_ns = c_native_ns - empty_ns;
    
    printf("测试结果:\n");
    printf("  空函数调用:   %.2f ns/op\n", empty_ns);
    printf("  divide 函数:   %.2f ns/op\n", c_native_ns);
    printf("  纯计算开销:    %.2f ns/op\n\n", calc_only_ns > 0 ? calc_only_ns : 0);
    
    printf("分析:\n");
    printf("  如果 FFI 调用应该和 C 原生一样\n");
    printf("  FFI 开销应该 ≈ %.2f ns/op\n\n", c_native_ns);
    
    printf("之前测试的异常:\n");
    printf("  实测 FFI: ~834 ns/op\n");
    printf("  C 原生:   ~%.2f ns/op\n", c_native_ns);
    printf("  差异:     %.0fx\n\n", 834.0 / c_native_ns);
    
    printf("可能原因:\n");
    printf("  1. Java 测试未 warmup\n");
    printf("  2. JIT 未优化\n");
    printf("  3. 符号查找未缓存\n");
    printf("  4. Arena/struct 处理有额外开销\n");
    
    return 0;
}
