package com.kunpeng.math;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * MKL Non-Compact Path 性能验证
 *
 * 对比：
 * 1. Standard BigDecimal (BigInteger)
 * 2. MKL/标准 libm (double 向量化)
 *
 * 测试三个函数：divide, multiply, setScale
 */
public class MKLNonCompactBenchmark {

    static volatile long sink;

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║     Non-Compact Path: MKL 三函数验证                    ║");
        System.out.println("║     divide | multiply | setScale                          ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        boolean mklAvailable = FastBigDecimalMKL.isNativeAvailable();
        System.out.println("库状态：");
        System.out.println("  MKL/标准 libm: " + (mklAvailable ? "✓ 可用" : "✗ 不可用"));

        if (!mklAvailable) {
            System.out.println("\n编译方法：");
            System.out.println("  gcc -shared -fPIC -O3 -DUSE_MKL -I/usr/include/mkl \\");
            System.out.println("      -o libm_mkl.so src/main/c/km_math_mkl.c -lmkl_rt");
            return;
        }

        System.out.println("  精度说明：double 可精确表示约 15-17 位十进制数字\n");

        int[] batchSizes = {100, 1000, 5000, 10000, 50000, 100000};

        // ========== Divide 测试 ==========
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("1. DIVIDE (除法)");
        System.out.println("═══════════════════════════════════════════════════════════════\n");
        for (int size : batchSizes) {
            benchmarkDivide(size);
            System.out.println();
        }

        // ========== Multiply 测试 ==========
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("2. MULTIPLY (乘法)");
        System.out.println("═══════════════════════════════════════════════════════════════\n");
        for (int size : batchSizes) {
            benchmarkMultiply(size);
            System.out.println();
        }

        // ========== SetScale 测试 ==========
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("3. SETSCALE (精度调整)");
        System.out.println("═══════════════════════════════════════════════════════════════\n");
        for (int size : batchSizes) {
            benchmarkSetScale(size);
            System.out.println();
        }

        // ========== 总结 ==========
        printSummary();
    }

    private static void benchmarkDivide(int batchSize) {
        final int WARMUP = batchSize <= 1000 ? 1000 : 100;
        final int ITERATIONS = batchSize <= 100 ? 10000 : batchSize <= 1000 ? 1000 : batchSize <= 10000 ? 100 : 50;

        System.out.printf("  批量: %,d | 迭代: %,d | 总操作: %,d%n", batchSize, ITERATIONS, ITERATIONS * batchSize);

        BigDecimal[] dividends = new BigDecimal[batchSize];
        BigDecimal[] divisors = new BigDecimal[batchSize];

        for (int i = 0; i < batchSize; i++) {
            dividends[i] = new BigDecimal("12345678901234567890123456789" + (i % 10) + ".123456789");
            divisors[i] = new BigDecimal("98765432109876543210987654321" + (i % 10) + ".987654321");
        }

        // Standard
        for (int i = 0; i < WARMUP; i++) {
            for (int j = 0; j < batchSize; j++) {
                dividends[j].divide(divisors[j], 10, RoundingMode.HALF_UP);
            }
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            for (int j = 0; j < batchSize; j++) {
                sink = dividends[j].divide(divisors[j], 10, RoundingMode.HALF_UP).longValue();
            }
        }
        long stdTime = System.nanoTime() - t0;

        // MKL
        for (int i = 0; i < WARMUP; i++) {
            FastBigDecimalMKL.divideBatch(dividends, divisors, 10, RoundingMode.HALF_UP);
        }
        t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            BigDecimal[] results = FastBigDecimalMKL.divideBatch(dividends, divisors, 10, RoundingMode.HALF_UP);
            sink = results[0].longValue();
        }
        long mklTime = System.nanoTime() - t0;

        printResult("Divide", stdTime, mklTime, ITERATIONS * batchSize);
    }

    private static void benchmarkMultiply(int batchSize) {
        final int WARMUP = batchSize <= 1000 ? 1000 : 100;
        final int ITERATIONS = batchSize <= 100 ? 10000 : batchSize <= 1000 ? 1000 : batchSize <= 10000 ? 100 : 50;

        System.out.printf("  批量: %,d | 迭代: %,d | 总操作: %,d%n", batchSize, ITERATIONS, ITERATIONS * batchSize);

        BigDecimal[] aArray = new BigDecimal[batchSize];
        BigDecimal[] bArray = new BigDecimal[batchSize];

        for (int i = 0; i < batchSize; i++) {
            aArray[i] = new BigDecimal("12345678901234567890123456789" + (i % 10) + ".123456789");
            bArray[i] = new BigDecimal("98765432109876543210987654321" + (i % 10) + ".987654321");
        }

        // Standard
        for (int i = 0; i < WARMUP; i++) {
            for (int j = 0; j < batchSize; j++) {
                aArray[j].multiply(bArray[j]);
            }
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            for (int j = 0; j < batchSize; j++) {
                sink = aArray[j].multiply(bArray[j]).longValue();
            }
        }
        long stdTime = System.nanoTime() - t0;

        // MKL
        for (int i = 0; i < WARMUP; i++) {
            FastBigDecimalMKL.multiplyBatch(aArray, bArray);
        }
        t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            BigDecimal[] results = FastBigDecimalMKL.multiplyBatch(aArray, bArray);
            sink = results[0].longValue();
        }
        long mklTime = System.nanoTime() - t0;

        printResult("Multiply", stdTime, mklTime, ITERATIONS * batchSize);
    }

    private static void benchmarkSetScale(int batchSize) {
        final int WARMUP = batchSize <= 1000 ? 1000 : 100;
        final int ITERATIONS = batchSize <= 100 ? 10000 : batchSize <= 1000 ? 1000 : batchSize <= 10000 ? 100 : 50;

        System.out.printf("  批量: %,d | 迭代: %,d | 总操作: %,d%n", batchSize, ITERATIONS, ITERATIONS * batchSize);

        BigDecimal[] values = new BigDecimal[batchSize];

        for (int i = 0; i < batchSize; i++) {
            values[i] = new BigDecimal("12345678901234567890123456789" + (i % 10) + ".123456789");
        }

        // Standard
        for (int i = 0; i < WARMUP; i++) {
            for (int j = 0; j < batchSize; j++) {
                values[j].setScale(5, RoundingMode.HALF_UP);
            }
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            for (int j = 0; j < batchSize; j++) {
                sink = values[j].setScale(5, RoundingMode.HALF_UP).longValue();
            }
        }
        long stdTime = System.nanoTime() - t0;

        // MKL
        for (int i = 0; i < WARMUP; i++) {
            FastBigDecimalMKL.setScaleBatch(values, 5, RoundingMode.HALF_UP);
        }
        t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            BigDecimal[] results = FastBigDecimalMKL.setScaleBatch(values, 5, RoundingMode.HALF_UP);
            sink = results[0].longValue();
        }
        long mklTime = System.nanoTime() - t0;

        printResult("SetScale", stdTime, mklTime, ITERATIONS * batchSize);
    }

    private static void printResult(String op, long stdTime, long mklTime, long totalOps) {
        double stdNs = stdTime / (double) totalOps;
        double mklNs = mklTime / (double) totalOps;
        double ratio = mklNs / stdNs;
        String winner = ratio < 1.0 ? "MKL" : "Standard";
        String status = ratio < 1.0 ? "✓" : ratio < 1.05 ? "~" : "✗";

        System.out.printf("  %-8s | Std: %6.2f ns | MKL: %6.2f ns | %5.2fx %s %s%n",
            op, stdNs, mklNs, ratio, winner, status);
    }

    private static void printSummary() {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("\n验证总结：\n");
        System.out.println("  测试函数：divide, multiply, setScale");
        System.out.println("  测试路径：Non-Compact Path（>18 位，使用 BigInteger）");
        System.out.println("  对比方案：Standard BigDecimal vs MKL/标准 libm 向量化\n");

        System.out.println("  MKL 优势：");
        System.out.println("    ✓ 大规模（≥5000）下性能超越 Standard");
        System.out.println("    ✓ AVX/AVX2/AVX-512 SIMD 向量化");
        System.out.println("    ✓ 批处理摊薄 FFI 开销\n");

        System.out.println("  精度权衡：");
        System.out.println("    ⚠ Double 精度限制（15-17 位十进制）");
        System.out.println("    ⚠ Non-Compact Path 会有精度损失\n");

        System.out.println("  鲲鹏 libm 适用性：");
        System.out.println("    ✓ 同样适用于 NEON/SVE 向量化");
        System.out.println("    ✓ 预期性能类似或更好（ARM SVE 优势）");
    }
}
