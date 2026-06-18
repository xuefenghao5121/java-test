package com.kunpeng.math;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * FastBigDecimal 接口验证测试
 */
public class FastBigDecimalTest {

    public static void main(String[] args) {
        System.out.println("=== FastBigDecimal 接口验证 ===\n");

        System.out.println("Native 库状态: " + FastBigDecimal.isNativeAvailable());

        if (!FastBigDecimal.isNativeAvailable()) {
            System.out.println("Native 库不可用，请检查 libm.so");
            return;
        }

        int passed = 0;
        int failed = 0;

        // ========== divide 测试 ==========
        System.out.println("\n--- divide 测试 ---");

        BigDecimal a = new BigDecimal("100");
        BigDecimal b = new BigDecimal("3");
        int scale = 2;
        RoundingMode rounding = RoundingMode.HALF_UP;

        BigDecimal expected = a.divide(b, scale, rounding);
        BigDecimal result = FastBigDecimal.divide(a, b, scale, rounding);

        if (expected.equals(result)) {
            System.out.println("✓ divide(100 / 3, scale=2, HALF_UP): " + result);
            passed++;
        } else {
            System.out.println("✗ divide(100 / 3): expected=" + expected + ", got=" + result);
            failed++;
        }

        // 测试精确除法
        BigDecimal c = new BigDecimal("100");
        BigDecimal d = new BigDecimal("4");
        expected = c.divide(d);
        result = FastBigDecimal.divide(c, d);

        if (expected.equals(result)) {
            System.out.println("✓ divide(100 / 4): " + result);
            passed++;
        } else {
            System.out.println("✗ divide(100 / 4): expected=" + expected + ", got=" + result);
            failed++;
        }

        // 测试负数
        BigDecimal e = new BigDecimal("-100");
        BigDecimal f = new BigDecimal("3");
        expected = e.divide(f, 2, RoundingMode.HALF_UP);
        result = FastBigDecimal.divide(e, f, 2, RoundingMode.HALF_UP);

        if (expected.equals(result)) {
            System.out.println("✓ divide(-100 / 3, HALF_UP): " + result);
            passed++;
        } else {
            System.out.println("✗ divide(-100 / 3): expected=" + expected + ", got=" + result);
            failed++;
        }

        // ========== multiply 测试 ==========
        System.out.println("\n--- multiply 测试 ---");

        BigDecimal x = new BigDecimal("12.34");  // sig=1234, scale=2
        BigDecimal y = new BigDecimal("5.6");   // sig=56, scale=1
        expected = x.multiply(y);  // 69.104
        result = FastBigDecimal.multiply(x, y);

        if (expected.equals(result)) {
            System.out.println("✓ multiply(12.34 × 5.6): " + result);
            passed++;
        } else {
            System.out.println("✗ multiply(12.34 × 5.6): expected=" + expected + ", got=" + result);
            failed++;
        }

        // 测试整数乘法
        BigDecimal m1 = new BigDecimal("123");
        BigDecimal m2 = new BigDecimal("456");
        expected = m1.multiply(m2);
        result = FastBigDecimal.multiply(m1, m2);

        if (expected.equals(result)) {
            System.out.println("✓ multiply(123 × 456): " + result);
            passed++;
        } else {
            System.out.println("✗ multiply(123 × 456): expected=" + expected + ", got=" + result);
            failed++;
        }

        // ========== setScale 测试 ==========
        System.out.println("\n--- setScale 测试 ---");

        BigDecimal s = new BigDecimal("123.4567");
        expected = s.setScale(2, RoundingMode.HALF_UP);
        result = FastBigDecimal.setScale(s, 2, RoundingMode.HALF_UP);

        if (expected.equals(result)) {
            System.out.println("✓ setScale(123.4567 → 2, HALF_UP): " + result);
            passed++;
        } else {
            System.out.println("✗ setScale(123.4567 → 2): expected=" + expected + ", got=" + result);
            failed++;
        }

        // 测试增加 scale
        BigDecimal s2 = new BigDecimal("123");
        expected = s2.setScale(4);
        result = FastBigDecimal.setScale(s2, 4);

        if (expected.equals(result)) {
            System.out.println("✓ setScale(123 → 4): " + result);
            passed++;
        } else {
            System.out.println("✗ setScale(123 → 4): expected=" + expected + ", got=" + result);
            failed++;
        }

        // 测试 HALF_EVEN（银行家舍入）
        BigDecimal s3 = new BigDecimal("2.5");
        expected = s3.setScale(0, RoundingMode.HALF_EVEN);
        result = FastBigDecimal.setScale(s3, 0, RoundingMode.HALF_EVEN);

        if (expected.equals(result)) {
            System.out.println("✓ setScale(2.5 → 0, HALF_EVEN): " + result);
            passed++;
        } else {
            System.out.println("✗ setScale(2.5 → 0, HALF_EVEN): expected=" + expected + ", got=" + result);
            failed++;
        }

        // ========== compact 路径测试 ==========
        System.out.println("\n--- compact 路径测试 ---");

        // 大于 18 位，应 fallback 到 BigDecimal
        BigDecimal big = new BigDecimal("123456789012345678901234567890");  // 30 位
        BigDecimal small = new BigDecimal("2");
        expected = big.divide(small, 2, RoundingMode.HALF_UP);
        result = FastBigDecimal.divide(big, small, 2, RoundingMode.HALF_UP);

        if (expected.equals(result)) {
            System.out.println("✓ divide(大数): fallback 正常 - " + result);
            passed++;
        } else {
            System.out.println("✗ divide(大数): fallback 失败");
            failed++;
        }

        // ========== 总结 ==========
        System.out.println("\n=== 测试结果 ===");
        System.out.println("通过: " + passed);
        System.out.println("失败: " + failed);
        System.out.println("总计: " + (passed + failed));

        if (failed == 0) {
            System.out.println("\n✓ 所有测试通过");
        } else {
            System.out.println("\n✗ 有 " + failed + " 个测试失败");
        }
    }
}
