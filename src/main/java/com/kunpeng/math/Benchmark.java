package com.kunpeng.math;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 简单性能对比测试
 */
public class Benchmark {

    private static final int WARMUP_ITERATIONS = 5000;
    private static final int MEASURED_ITERATIONS = 100000;

    public static void main(String[] args) {
        System.out.println("=== BigDecimal 性能对比 ===\n");

        if (!FastBigDecimal.isNativeAvailable()) {
            System.out.println("Native 库不可用，跳过测试");
            return;
        }

        // Divide 测试
        benchmarkDivide();

        // Multiply 测试
        benchmarkMultiply();

        // SetScale 测试
        benchmarkSetScale();
    }

    private static void benchmarkDivide() {
        System.out.println("--- Divide ---");
        BigDecimal a = new BigDecimal("100");
        BigDecimal b = new BigDecimal("3");
        int scale = 2;
        RoundingMode rounding = RoundingMode.HALF_UP;

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            a.divide(b, scale, rounding);
            FastBigDecimal.divide(a, b, scale, rounding);
        }

        // Standard BigDecimal
        long start = System.nanoTime();
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            a.divide(b, scale, rounding);
        }
        long stdTime = System.nanoTime() - start;

        // FastBigDecimal
        start = System.nanoTime();
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            FastBigDecimal.divide(a, b, scale, rounding);
        }
        long fastTime = System.nanoTime() - start;

        printResult("divide(100/3, scale=2)", stdTime, fastTime);
    }

    private static void benchmarkMultiply() {
        System.out.println("\n--- Multiply ---");
        BigDecimal x = new BigDecimal("12.34");
        BigDecimal y = new BigDecimal("5.6");

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            x.multiply(y);
            FastBigDecimal.multiply(x, y);
        }

        // Standard BigDecimal
        long start = System.nanoTime();
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            x.multiply(y);
        }
        long stdTime = System.nanoTime() - start;

        // FastBigDecimal
        start = System.nanoTime();
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            FastBigDecimal.multiply(x, y);
        }
        long fastTime = System.nanoTime() - start;

        printResult("multiply(12.34 × 5.6)", stdTime, fastTime);
    }

    private static void benchmarkSetScale() {
        System.out.println("\n--- SetScale ---");
        BigDecimal s = new BigDecimal("123.4567");

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            s.setScale(2, RoundingMode.HALF_UP);
            FastBigDecimal.setScale(s, 2, RoundingMode.HALF_UP);
        }

        // Standard BigDecimal
        long start = System.nanoTime();
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            s.setScale(2, RoundingMode.HALF_UP);
        }
        long stdTime = System.nanoTime() - start;

        // FastBigDecimal
        start = System.nanoTime();
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            FastBigDecimal.setScale(s, 2, RoundingMode.HALF_UP);
        }
        long fastTime = System.nanoTime() - start;

        printResult("setScale(123.4567 → 2)", stdTime, fastTime);
    }

    private static void printResult(String name, long stdNs, long fastNs) {
        double stdUs = stdNs / 1000.0;
        double fastUs = fastNs / 1000.0;
        double speedup = (double) stdNs / fastNs;

        System.out.printf("%s%n", name);
        System.out.printf("  Standard: %.2f us (%.3f ns/op)%n", stdUs, stdNs / (double) MEASURED_ITERATIONS);
        System.out.printf("  Fast:     %.2f us (%.3f ns/op)%n", fastUs, fastNs / (double) MEASURED_ITERATIONS);
        System.out.printf("  加速比:   %.2fx %s%n", speedup, speedup > 1 ? "✓" : "✗");
    }
}
