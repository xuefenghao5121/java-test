package com.kunpeng.math;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 修复版本测试
 */
public class TestFixed {

    public static void main(String[] args) {
        System.out.println("=== 修复版本测试 ===\n");

        if (!FastBigDecimalFixed.isNativeAvailable()) {
            System.out.println("Native 库不可用");
            return;
        }

        // 正确性测试
        testCorrectness();

        // 性能对比
        testPerformance();
    }

    private static void testCorrectness() {
        System.out.println("--- 正确性测试 ---");

        boolean pass = true;

        // Divide
        BigDecimal a = new BigDecimal("100");
        BigDecimal b = new BigDecimal("3");
        BigDecimal expected = a.divide(b, 2, RoundingMode.HALF_UP);
        BigDecimal result = FastBigDecimalFixed.divide(a, b, 2, RoundingMode.HALF_UP);
        if (!expected.equals(result)) {
            System.out.println("✗ divide: expected=" + expected + ", got=" + result);
            pass = false;
        } else {
            System.out.println("✓ divide(100/3): " + result);
        }

        // Multiply
        BigDecimal x = new BigDecimal("12.34");
        BigDecimal y = new BigDecimal("5.6");
        expected = x.multiply(y);
        result = FastBigDecimalFixed.multiply(x, y);
        if (!expected.equals(result)) {
            System.out.println("✗ multiply: expected=" + expected + ", got=" + result);
            pass = false;
        } else {
            System.out.println("✓ multiply(12.34 × 5.6): " + result);
        }

        // SetScale
        BigDecimal s = new BigDecimal("123.4567");
        expected = s.setScale(2, RoundingMode.HALF_UP);
        result = FastBigDecimalFixed.setScale(s, 2, RoundingMode.HALF_UP);
        if (!expected.equals(result)) {
            System.out.println("✗ setScale: expected=" + expected + ", got=" + result);
            pass = false;
        } else {
            System.out.println("✓ setScale(123.4567 → 2): " + result);
        }

        System.out.println(pass ? "\n所有测试通过 ✓" : "\n有测试失败 ✗");
    }

    private static void testPerformance() {
        System.out.println("\n--- 性能测试 (非 JMH，仅供参考) ---");

        final int WARMUP = 10000;
        final int ITERATIONS = 100000;

        BigDecimal a = new BigDecimal("100");
        BigDecimal b = new BigDecimal("3");

        // Warmup
        for (int i = 0; i < WARMUP; i++) {
            a.divide(b, 2, RoundingMode.HALF_UP);
            FastBigDecimalFixed.divide(a, b, 2, RoundingMode.HALF_UP);
        }

        // Standard
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            a.divide(b, 2, RoundingMode.HALF_UP);
        }
        long stdTime = System.nanoTime() - t0;

        // Fixed
        long t1 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            FastBigDecimalFixed.divide(a, b, 2, RoundingMode.HALF_UP);
        }
        long fixedTime = System.nanoTime() - t1;

        System.out.printf("Standard: %.2f us (%.3f ns/op)%n",
            stdTime / 1000.0, stdTime / (double) ITERATIONS);
        System.out.printf("Fixed:    %.2f us (%.3f ns/op)%n",
            fixedTime / 1000.0, fixedTime / (double) ITERATIONS);
        System.out.printf("加速比:   %.2fx%n", stdTime / (double) fixedTime);

        // 分析修复带来的改进
        System.out.println("\n修复说明:");
        System.out.println("1. ✓ 移除 ThreadLocal Arena 内存泄漏");
        System.out.println("2. ✓ 使用 try-with-resources 自动管理 Arena");
        System.out.println("3. ✓ 返回值传递 sig，减少内存写入");
        System.out.println("4. ✓ 使用特化 ValueLayout.OfLong/OfInt");
        System.out.println("5. ✓ 优化 compact 路径检查，避免 toString()");
    }
}
