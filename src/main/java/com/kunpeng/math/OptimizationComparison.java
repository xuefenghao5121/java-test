package com.kunpeng.math;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 优化方案性能对比
 *
 * 对比三种避免 struct 返回值的方法：
 * 1. 编码返回值（推荐）
 * 2. 指针参数（预分配缓冲区）
 * 3. 方案 3：双返回值（暂不实现，Panama 支持有限）
 */
public class OptimizationComparison {

    public static void main(String[] args) throws Throwable {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║         优化方案性能对比（避免 struct 返回）              ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        if (!FastBigDecimalOptimizedFinal.isNativeAvailable()) {
            System.out.println("Native 库不可用，请先编译 libm_optimized.so");
            return;
        }

        System.out.println("测试设置：");
        System.out.println("  Warmup: 100,000 次");
        System.out.println("  Iterations: 10,000,000 次\n");

        BigDecimal a = new BigDecimal("100");
        BigDecimal b = new BigDecimal("3");

        // 测试方案 1：编码返回值
        System.out.println("┌─ 方案 1：编码返回值 ──────────────────────────────────────┐");
        double encodedTime = benchmarkApproach("Encoded", () ->
            FastBigDecimalOptimizedFinal.divideEncoded(a, b, 2, RoundingMode.HALF_UP)
        );

        // 测试方案 2：指针参数
        System.out.println("┌─ 方案 2：指针参数（预分配缓冲区）─────────────────────────┐");
        double ptrTime = benchmarkApproach("Ptr", () ->
            FastBigDecimalOptimizedFinal.dividePtr(a, b, 2, RoundingMode.HALF_UP)
        );

        // 测试 Standard
        System.out.println("┌─ Standard BigDecimal ──────────────────────────────────────┐");
        double standardTime = benchmarkApproach("Standard", () ->
            a.divide(b, 2, RoundingMode.HALF_UP)
        );

        // 打印对比
        printComparison(encodedTime, ptrTime, standardTime);

        // 测试正确性
        System.out.println("┌─ 正确性验证 ──────────────────────────────────────────────┐");
        testCorrectness();
    }

    private static double benchmarkApproach(String name, Runnable op) {
        final int WARMUP = 100000;
        final int ITERATIONS = 10000000;

        // Warmup
        for (int i = 0; i < WARMUP; i++) {
            op.run();
        }

        // 测试
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            op.run();
        }
        long elapsed = System.nanoTime() - t0;

        double nsPerOp = elapsed / (double) ITERATIONS;
        System.out.printf("  单次开销: %.2f ns/op (%,d 次迭代)%n", nsPerOp, ITERATIONS);
        System.out.println();

        return nsPerOp;
    }

    private static void printComparison(double encodedTime, double ptrTime, double standardTime) {
        System.out.println("═══════════════════════════════════════════════════════════════\n");
        System.out.println("性能对比：\n");
        System.out.println("┌────────────────┬──────────────┬──────────────┬──────────┐");
        System.out.println("│ 方案           │ 单次 (ns/op) │ vs Standard  │ 比率     │");
        System.out.println("├────────────────┼──────────────┼──────────────┼──────────┤");
        System.out.printf("│ Encoded        │ %12.2f │ %12.2f │ %8.2fx │%n",
            encodedTime, encodedTime - standardTime, encodedTime / standardTime);
        System.out.printf("│ Ptr            │ %12.2f │ %12.2f │ %8.2fx │%n",
            ptrTime, ptrTime - standardTime, ptrTime / standardTime);
        System.out.printf("│ Standard       │ %12.2f │ %12.2f │ %8.2fx │%n",
            standardTime, 0.0, 1.0);
        System.out.println("└────────────────┴──────────────┴──────────────┴──────────┘\n");

        System.out.println("结论：\n");
        if (encodedTime < ptrTime && encodedTime < standardTime * 1.5) {
            System.out.println("  ✓ 方案 1 (编码返回值) 性能最佳");
            System.out.println("  ✓ 推荐使用编码返回值方案");
        } else if (ptrTime < encodedTime && ptrTime < standardTime * 1.5) {
            System.out.println("  ✓ 方案 2 (指针参数) 性能最佳");
            System.out.println("  ✓ 推荐使用指针参数方案");
        } else {
            System.out.println("  ⚠ Native 方案性能不如 Standard");
            System.out.println("  建议：保持使用 Standard BigDecimal");
        }
        System.out.println();
    }

    private static void testCorrectness() {
        BigDecimal a = new BigDecimal("100");
        BigDecimal b = new BigDecimal("3");

        BigDecimal std = a.divide(b, 2, RoundingMode.HALF_UP);
        BigDecimal encoded = FastBigDecimalOptimizedFinal.divideEncoded(a, b, 2, RoundingMode.HALF_UP);
        BigDecimal ptr = FastBigDecimalOptimizedFinal.dividePtr(a, b, 2, RoundingMode.HALF_UP);

        System.out.println("  计算: 100 / 3 (scale=2, HALF_UP)");
        System.out.printf("  Standard: %s%n", std);
        System.out.printf("  Encoded:  %s (匹配: %s)%n", encoded, std.equals(encoded) ? "✓" : "✗");
        System.out.printf("  Ptr:      %s (匹配: %s)%n", ptr, std.equals(ptr) ? "✓" : "✗");
        System.out.println();
    }
}
