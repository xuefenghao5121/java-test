package com.kunpeng.math;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 性能对比：Original vs Optimized vs Standard
 */
public class BenchmarkOptimized {

    private static final int WARMUP = 10000;
    private static final int ITERATIONS = 200000;

    public static void main(String[] args) {
        System.out.println("=== 性能对比测试 (优化版本) ===\n");

        if (!FastBigDecimal.isNativeAvailable()) {
            System.out.println("Native 库不可用");
            return;
        }

        System.out.println("Standard vs Original vs Optimized\n");

        testDivide();
        testMultiply();
        testSetScale();
    }

    private static void testDivide() {
        System.out.println("--- Divide ---");
        BigDecimal a = new BigDecimal("100");
        BigDecimal b = new BigDecimal("3");

        // Warmup
        for (int i = 0; i < WARMUP; i++) {
            a.divide(b, 2, RoundingMode.HALF_UP);
            FastBigDecimal.divide(a, b, 2, RoundingMode.HALF_UP);
            FastBigDecimalOptimized.divide(a, b, 2, RoundingMode.HALF_UP);
        }

        // Standard
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            a.divide(b, 2, RoundingMode.HALF_UP);
        }
        long std = System.nanoTime() - t0;

        // Original
        long t1 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            FastBigDecimal.divide(a, b, 2, RoundingMode.HALF_UP);
        }
        long orig = System.nanoTime() - t1;

        // Optimized
        long t2 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            FastBigDecimalOptimized.divide(a, b, 2, RoundingMode.HALF_UP);
        }
        long opt = System.nanoTime() - t2;

        printResults(std, orig, opt);
    }

    private static void testMultiply() {
        System.out.println("\n--- Multiply ---");
        BigDecimal x = new BigDecimal("12.34");
        BigDecimal y = new BigDecimal("5.6");

        // Warmup
        for (int i = 0; i < WARMUP; i++) {
            x.multiply(y);
            FastBigDecimal.multiply(x, y);
            FastBigDecimalOptimized.multiply(x, y);
        }

        // Standard
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            x.multiply(y);
        }
        long std = System.nanoTime() - t0;

        // Original
        long t1 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            FastBigDecimal.multiply(x, y);
        }
        long orig = System.nanoTime() - t1;

        // Optimized
        long t2 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            FastBigDecimalOptimized.multiply(x, y);
        }
        long opt = System.nanoTime() - t2;

        printResults(std, orig, opt);
    }

    private static void testSetScale() {
        System.out.println("\n--- SetScale ---");
        BigDecimal s = new BigDecimal("123.4567");

        // Warmup
        for (int i = 0; i < WARMUP; i++) {
            s.setScale(2, RoundingMode.HALF_UP);
            FastBigDecimal.setScale(s, 2, RoundingMode.HALF_UP);
            FastBigDecimalOptimized.setScale(s, 2, RoundingMode.HALF_UP);
        }

        // Standard
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            s.setScale(2, RoundingMode.HALF_UP);
        }
        long std = System.nanoTime() - t0;

        // Original
        long t1 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            FastBigDecimal.setScale(s, 2, RoundingMode.HALF_UP);
        }
        long orig = System.nanoTime() - t1;

        // Optimized
        long t2 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            FastBigDecimalOptimized.setScale(s, 2, RoundingMode.HALF_UP);
        }
        long opt = System.nanoTime() - t2;

        printResults(std, orig, opt);
    }

    private static void printResults(long stdNs, long origNs, long optNs) {
        System.out.printf("Standard:  %8.2f us (%.3f ns/op)%n", stdNs/1000.0, stdNs/(double)ITERATIONS);
        System.out.printf("Original:  %8.2f us (%.3f ns/op) [%.2fx]%n",
            origNs/1000.0, origNs/(double)ITERATIONS, stdNs/(double)origNs);
        System.out.printf("Optimized: %8.2f us (%.3f ns/op) [%.2fx, %.2fx vs Original]%n",
            optNs/1000.0, optNs/(double)ITERATIONS, stdNs/(double)optNs, origNs/(double)optNs);
    }
}
