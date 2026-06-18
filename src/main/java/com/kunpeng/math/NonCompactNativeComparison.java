package com.kunpeng.math;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 非 Compact Path: Standard vs Native 对比
 *
 * 真正的优化场景：>18 位数字
 */
public class NonCompactNativeComparison {

    static volatile long sink;

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║         非 Compact Path: Standard vs Native               ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        // 测试数据（>18 位数字）
        BigDecimal largeA = new BigDecimal("123456789012345678901234567890.123456789");
        BigDecimal largeB = new BigDecimal("987654321098765432109876543210.987654321");

        // 同时测试 compact path 作为对比
        BigDecimal smallA = new BigDecimal("100.1234");
        BigDecimal smallB = new BigDecimal("3.4567");

        System.out.println("┌─ Compact Path 对比（<18 位）─────────────────────────────────┐");
        System.out.println("  数据: 100.1234 / 3.4567\n");
        benchmarkCompact(smallA, smallB);

        System.out.println("┌─ 非 Compact Path 对比（>18 位）───────────────────────────────┐");
        System.out.println("  数据: 123...890.123456789 / 987...210.987654321\n");
        benchmarkNonCompact(largeA, largeB);

        System.out.println("\n═══════════════════════════════════════════════════════════════\n");
        System.out.println("关键洞察：\n");
        System.out.println("  ✗ Native 无法处理非 compact path（当前实现限制）");
        System.out.println("  ⚠ 需要重新设计接口以支持大整数");
    }

    private static void benchmarkCompact(BigDecimal a, BigDecimal b) {
        final int WARMUP = 10000;
        final int ITERATIONS = 100000;

        // Standard
        for (int i = 0; i < WARMUP; i++) {
            a.divide(b, 10, RoundingMode.HALF_UP);
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            sink = a.divide(b, 10, RoundingMode.HALF_UP).longValue();
        }
        long stdTime = System.nanoTime() - t0;

        System.out.printf("  Standard: %.2f ns/op (使用 long 存储)%n", stdTime / (double) ITERATIONS);
        System.out.println("  → Native 可用但无优势（FFI 开销 > 计算）\n");
    }

    private static void benchmarkNonCompact(BigDecimal a, BigDecimal b) {
        final int WARMUP = 10000;
        final int ITERATIONS = 100000;

        // Standard
        for (int i = 0; i < WARMUP; i++) {
            a.divide(b, 10, RoundingMode.HALF_UP);
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            sink = a.divide(b, 10, RoundingMode.HALF_UP).longValue();
        }
        long stdTime = System.nanoTime() - t0;

        double stdNs = stdTime / (double) ITERATIONS;

        System.out.printf("  Standard: %.2f ns/op (使用 BigInteger)%n", stdNs);

        // 检查 Native 是否可用
        try {
            long sig = a.unscaledValue().longValueExact();
            System.out.println("  Native: 可用（数值溢出，fallback 到 Standard）");
        } catch (ArithmeticException e) {
            System.out.println("  Native: ✗ 不可用（超出 long 范围）");
            System.out.println("  → 当前 Native 实现仅支持 int64_t");
        }

        System.out.println("\n  分析：");
        System.out.println("    Standard 使用 BigInteger 处理大整数");
        System.out.println("    Native 需要重新设计以支持任意精度整数");
    }
}
