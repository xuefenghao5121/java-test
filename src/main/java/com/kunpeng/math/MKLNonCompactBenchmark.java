package com.kunpeng.math;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * MKL Non-Compact Path 性能验证
 *
 * 对比：
 * 1. Standard BigDecimal (BigInteger)
 * 2. MKL/标准 libm (double 向量化)
 */
public class MKLNonCompactBenchmark {

    static volatile long sink;

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║         Non-Compact Path: MKL 验证                      ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        boolean mklAvailable = FastBigDecimalMKL.isNativeAvailable();
        System.out.println("库状态：");
        System.out.println("  MKL/标准 libm: " + (mklAvailable ? "✓ 可用" : "✗ 不可用"));

        if (!mklAvailable) {
            System.out.println("\n编译方法：");
            System.out.println("  gcc -shared -fPIC -O3 -o libm_mkl.so src/main/c/km_math_mkl.c -lm");
            System.out.println("\n或者使用 MKL：");
            System.out.println("  gcc -shared -fPIC -O3 -DUSE_MKL -o libm_mkl.so \\");
            System.out.println("      src/main/c/km_math_mkl.c -lmkl_core -lmkl_intel_thread -lmkl_intel_layer -liomp5 -lpthread -lm -ldl");
            return;
        }

        System.out.println("  精度说明：double 可精确表示约 15-17 位十进制数字\n");

        // 准备非 compact path 测试数据
        String largeNumStr = "123456789012345678901234567890.123456789";  // 30 位整数 + 9 位小数
        BigDecimal largeA = new BigDecimal(largeNumStr);
        BigDecimal largeB = new BigDecimal("987654321098765432109876543210.987654321");

        System.out.println("测试数据（非 compact path）：");
        System.out.println("  A: " + largeNumStr);
        System.out.println("  B: 987654321098765432109876543210.987654321\n");

        // ========== 单次操作对比 ==========
        System.out.println("┌─ 单次操作对比（Non-Compact Path）─────────────────────────────┐");
        benchmarkSingleOperation(largeA, largeB);

        // ========== 批处理对比 ==========
        System.out.println("┌─ 批处理对比（100 个操作）─────────────────────────────────────┐");
        benchmarkBatchOperation(100);

        // ========== 精度分析 ==========
        System.out.println("┌─ 精度损失分析 ──────────────────────────────────────────────┐");
        analyzePrecisionLoss();

        // ========== 总结 ==========
        printSummary();
    }

    private static void benchmarkSingleOperation(BigDecimal a, BigDecimal b) {
        final int WARMUP = 10000;
        final int ITERATIONS = 100000;

        // Standard (BigInteger)
        for (int i = 0; i < WARMUP; i++) {
            a.divide(b, 10, RoundingMode.HALF_UP);
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            sink = a.divide(b, 10, RoundingMode.HALF_UP).longValue();
        }
        long stdTime = System.nanoTime() - t0;

        double stdNs = stdTime / (double) ITERATIONS;

        System.out.printf("  Standard (BigInteger): %.2f ns/op%n", stdNs);
        System.out.println("    → 大整数精确运算\n");
    }

    private static void benchmarkBatchOperation(int batchSize) {
        final int WARMUP = 1000;
        final int ITERATIONS = 10000;

        // 准备批量数据
        BigDecimal[] aArray = new BigDecimal[batchSize];
        BigDecimal[] bArray = new BigDecimal[batchSize];

        for (int i = 0; i < batchSize; i++) {
            aArray[i] = new BigDecimal("12345678901234567890123456789" + (i % 10) + ".123456789");
            bArray[i] = new BigDecimal("98765432109876543210987654321" + (i % 10) + ".987654321");
        }

        // Standard (循环调用)
        for (int i = 0; i < WARMUP; i++) {
            for (int j = 0; j < batchSize; j++) {
                aArray[j].divide(bArray[j], 10, RoundingMode.HALF_UP);
            }
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            for (int j = 0; j < batchSize; j++) {
                sink = aArray[j].divide(bArray[j], 10, RoundingMode.HALF_UP).longValue();
            }
        }
        long stdTime = System.nanoTime() - t0;

        // MKL/标准 libm 批处理
        for (int i = 0; i < WARMUP; i++) {
            FastBigDecimalMKL.divideBatch(aArray, bArray, 10, RoundingMode.HALF_UP);
        }
        t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            BigDecimal[] results = FastBigDecimalMKL.divideBatch(aArray, bArray, 10, RoundingMode.HALF_UP);
            sink = results[0].longValue();
        }
        long mklTime = System.nanoTime() - t0;

        double stdNsPerOp = stdTime / (double) (ITERATIONS * batchSize);
        double mklNsPerOp = mklTime / (double) (ITERATIONS * batchSize);

        System.out.printf("%-25s %-15s %-15s%n", "方案", "单次 (ns/op)", "比率");
        System.out.println("────────────────────────────────────────────────────────────");
        System.out.printf("%-25s %-15.2f %-15.2fx%n", "Standard (循环)", stdNsPerOp, 1.0);
        System.out.printf("%-25s %-15.2f %-15.2fx%n%n", "MKL/标准 libm (批处理)", mklNsPerOp, mklNsPerOp / stdNsPerOp);
    }

    private static void analyzePrecisionLoss() {
        System.out.println("  Double 精度：53 位尾数 ≈ 15-17 位十进制数字");
        System.out.println("  BigDecimal 精度：任意精度\n");

        // 测试精度损失
        BigDecimal exact = new BigDecimal("123456789012345678901234567890.123456789");
        double approx = exact.doubleValue();
        BigDecimal fromDouble = BigDecimal.valueOf(approx);

        System.out.println("  精度示例：");
        System.out.println("    原始值: " + exact);
        System.out.println("    double: " + approx);
        System.out.println("    转回:  " + fromDouble);
        System.out.println("    差异:   " + exact.subtract(fromDouble).abs());
        System.out.println("");
        System.out.println("  结论：");
        System.out.println("    ✓ 数值越大，精度损失越明显");
        System.out.println("    ✓ 适用于可接受近似值的场景");
    }

    private static void printSummary() {
        System.out.println("═══════════════════════════════════════════════════════════════\n");
        System.out.println("MKL 验证总结：\n");
        System.out.println("  优势：");
        System.out.println("    ✓ 向量化运算（AVX/AVX2/AVX-512）");
        System.out.println("    ✓ 批处理摊薄 FFI 开销");
        System.out.println("    ✓ Non-Compact Path 性能可能优于 BigInteger\n");
        System.out.println("  限制：");
        System.out.println("    ⚠ Double 精度限制（15-17 位）");
        System.out.println("    ⚠ 有精度损失");
        System.out.println("    ⚠ 不适用于需要精确 BigDecimal 的场景\n");
        System.out.println("  适用场景：");
        System.out.println("    → 科学计算（可接受浮点近似）");
        System.out.println("    → 金融近似计算");
        System.out.println("    → 统计分析");
    }
}
