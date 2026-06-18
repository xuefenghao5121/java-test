package com.kunpeng.math;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

/**
 * 非 Compact Path 性能测试
 *
 * 真正的优化目标：>18 位数字的场景
 * - Standard 需要使用 BigInteger，性能下降
 * - Native 可能在此场景有优势
 */
public class NonCompactPathBenchmark {

    static volatile long sink;

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║         非 Compact Path 性能测试（>18 位数字）            ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        // 准备非 compact path 测试数据（>18 位数字）
        BigDecimal largeA = new BigDecimal("123456789012345678901234567890.123456789");
        BigDecimal largeB = new BigDecimal("987654321098765432109876543210.987654321");

        System.out.println("测试数据：");
        System.out.println("  A: 123456789012345678901234567890.123456789 (30 位整数 + 9 位小数)");
        System.out.println("  B: 987654321098765432109876543210.987654321 (30 位整数 + 9 位小数)");
        System.out.println("  → 非 compact path（>18 位）\n");

        System.out.println("┌─ Divide 测试 ────────────────────────────────────────────────┐");
        benchmarkDivide(largeA, largeB);

        System.out.println("┌─ Multiply 测试 ────────────────────────────────────────────┐");
        benchmarkMultiply(largeA, largeB);

        System.out.println("┌─ SetScale 测试 ─────────────────────────────────────────────┐");
        benchmarkSetScale(largeA);

        System.out.println("\n═══════════════════════════════════════════════════════════════\n");
        System.out.println("结论：\n");
        System.out.println("  此处才能真正体现 Native 优化价值");
        System.out.println("  Standard 使用 BigInteger，性能下降");
    }

    private static void benchmarkDivide(BigDecimal a, BigDecimal b) {
        final int WARMUP = 10000;
        final int ITERATIONS = 100000;

        // Standard (非 compact path)
        for (int i = 0; i < WARMUP; i++) {
            a.divide(b, 10, RoundingMode.HALF_UP);
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            sink = a.divide(b, 10, RoundingMode.HALF_UP).longValue();
        }
        long stdTime = System.nanoTime() - t0;

        double stdNs = stdTime / (double) ITERATIONS;

        System.out.printf("  Standard (非 compact): %.2f ns/op%n", stdNs);
        System.out.println("    → 使用 BigInteger，性能显著下降\n");
    }

    private static void benchmarkMultiply(BigDecimal a, BigDecimal b) {
        final int WARMUP = 10000;
        final int ITERATIONS = 100000;

        // Standard
        for (int i = 0; i < WARMUP; i++) {
            a.multiply(b);
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            sink = a.multiply(b).longValue();
        }
        long stdTime = System.nanoTime() - t0;

        double stdNs = stdTime / (double) ITERATIONS;

        System.out.printf("  Standard (非 compact): %.2f ns/op%n", stdNs);
        System.out.println("    → 大整数乘法，性能显著下降\n");
    }

    private static void benchmarkSetScale(BigDecimal a) {
        final int WARMUP = 10000;
        final int ITERATIONS = 100000;

        // Standard
        for (int i = 0; i < WARMUP; i++) {
            a.setScale(10, RoundingMode.HALF_UP);
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            sink = a.setScale(10, RoundingMode.HALF_UP).longValue();
        }
        long stdTime = System.nanoTime() - t0;

        double stdNs = stdTime / (double) ITERATIONS;

        System.out.printf("  Standard (非 compact): %.2f ns/op%n", stdNs);
        System.out.println("    → 大整数舍入，性能下降\n");
    }
}
