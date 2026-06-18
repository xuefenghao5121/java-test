package com.kunpeng.math;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 零拷贝版本测试
 */
public class TestZeroCopy {

    public static void main(String[] args) {
        System.out.println("=== 零拷贝版本测试 ===\n");

        // 检查 Native 是否可用
        if (!FastBigDecimalZeroCopy.isNativeAvailable()) {
            System.out.println("Native 库不可用");
            System.out.println("提示：需要编译 km_math_zero_copy.c 并加载");
            return;
        }

        System.out.println("Native 库已加载 ✓\n");

        // 测试 divide
        testDivide();

        // 测试 multiply
        testMultiply();

        // 测试 setScale
        testSetScale();

        // 性能对比
        performanceCompare();
    }

    private static void testDivide() {
        System.out.println("┌─ Divide 测试 ─────────────────────────┐");

        BigDecimal a = new BigDecimal("100");
        BigDecimal b = new BigDecimal("3");

        BigDecimal std = a.divide(b, 2, RoundingMode.HALF_UP);
        BigDecimal nat = FastBigDecimalZeroCopy.divide(a, b, 2, RoundingMode.HALF_UP);

        System.out.println("  " + a + " / " + b + " = " + std + " (scale=2, HALF_UP)");
        System.out.println("  Standard: " + std);
        System.out.println("  Native:   " + nat);
        System.out.println("  匹配: " + (std.equals(nat) ? "✓" : "✗"));
        System.out.println();
    }

    private static void testMultiply() {
        System.out.println("┌─ Multiply 测试 ──────────────────────┐");

        BigDecimal a = new BigDecimal("123.456");
        BigDecimal b = new BigDecimal("78.9");

        BigDecimal std = a.multiply(b);
        BigDecimal nat = FastBigDecimalZeroCopy.multiply(a, b);

        System.out.println("  " + a + " * " + b);
        System.out.println("  Standard: " + std);
        System.out.println("  Native:   " + nat);
        System.out.println("  匹配: " + (std.equals(nat) ? "✓" : "✗"));
        System.out.println();
    }

    private static void testSetScale() {
        System.out.println("┌─ SetScale 测试 ──────────────────────┐");

        BigDecimal value = new BigDecimal("123.4567");

        BigDecimal std = value.setScale(2, RoundingMode.HALF_UP);
        BigDecimal nat = FastBigDecimalZeroCopy.setScale(value, 2, RoundingMode.HALF_UP);

        System.out.println("  " + value + ".setScale(2, HALF_UP)");
        System.out.println("  Standard: " + std);
        System.out.println("  Native:   " + nat);
        System.out.println("  匹配: " + (std.equals(nat) ? "✓" : "✗"));
        System.out.println();
    }

    private static void performanceCompare() {
        System.out.println("┌─ 性能对比 (单次操作) ─────────────────┐");

        BigDecimal a = new BigDecimal("100");
        BigDecimal b = new BigDecimal("3");

        final int WARMUP = 10000;
        final int ITERATIONS = 1000000;

        // Warmup
        for (int i = 0; i < WARMUP; i++) {
            a.divide(b, 2, RoundingMode.HALF_UP);
            FastBigDecimalZeroCopy.divide(a, b, 2, RoundingMode.HALF_UP);
        }

        // Standard
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            a.divide(b, 2, RoundingMode.HALF_UP);
        }
        long stdTime = System.nanoTime() - t0;

        // Native
        long t1 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            FastBigDecimalZeroCopy.divide(a, b, 2, RoundingMode.HALF_UP);
        }
        long natTime = System.nanoTime() - t1;

        System.out.printf("  Standard: %.2f ns/op%n", stdTime / (double) ITERATIONS);
        System.out.printf("  Native:   %.2f ns/op%n", natTime / (double) ITERATIONS);
        System.out.printf("  比率:    %.2fx%n", (double) stdTime / natTime);
        System.out.println();
    }
}
