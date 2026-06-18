package com.kunpeng.math;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

/**
 * AVX 性能基准测试
 *
 * 对比：
 * 1. Standard BigDecimal
 * 2. 简单 C 实现（while 循环）
 * 3. AVX 单次操作
 * 4. AVX 批处理
 */
public class AVXPerformanceBenchmark {

    static volatile long sink;

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║         AVX 性能基准测试                                  ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        boolean avxAvailable = FastBigDecimalAVX.isNativeAvailable();
        boolean optimizedAvailable = FastBigDecimalOptimizedFinal.isNativeAvailable();

        System.out.println("库状态：");
        System.out.println("  AVX: " + (avxAvailable ? "✓ 可用" : "✗ 不可用"));
        System.out.println("  Optimized (简单 C): " + (optimizedAvailable ? "✓ 可用" : "✗ 不可用"));
        System.out.println();

        if (!avxAvailable) {
            System.out.println("⚠ AVX 库不可用，请先编译");
            System.out.println("  gcc -shared -fPIC -O3 -mavx2 -o libm_avx.so src/main/c/km_math_avx.c libm_optimized.so");
            return;
        }

        // 准备测试数据
        BigDecimal[] testValues = prepareTestData(1000);
        BigDecimal a = testValues[0];
        BigDecimal b = testValues[1];

        // ========== 单次操作测试 ==========
        System.out.println("┌─ 单次 Divide 测试 ────────────────────────────────────────┐");
        benchmarkSingleDivide(a, b);

        System.out.println("┌─ 单次 Multiply 测试 ──────────────────────────────────────┐");
        benchmarkSingleMultiply(a, b);

        // ========== 批处理测试 ==========
        System.out.println("┌─ 批处理 Divide 测试 ────────────────────────────────────────┐");
        benchmarkBatchDivide(testValues);

        System.out.println("┌─ 批处理 Multiply 测试 ─────────────────────────────────────┐");
        benchmarkBatchMultiply(testValues);

        // ========== 微服务场景测试 ==========
        System.out.println("┌─ 微服务场景（100 商品/订单）──────────────────────────────────┐");
        benchmarkMicroserviceScenario(testValues);

        // ========== 总结 ==========
        printSummary();
    }

    private static BigDecimal[] prepareTestData(int count) {
        BigDecimal[] data = new BigDecimal[count];
        Random random = new Random(42);

        for (int i = 0; i < count; i++) {
            data[i] = new BigDecimal(100 + random.nextInt(10000))
                .divide(new BigDecimal(100), 4, RoundingMode.HALF_UP);
        }

        return data;
    }

    private static void benchmarkSingleDivide(BigDecimal a, BigDecimal b) {
        final int WARMUP = 100000;
        final int ITERATIONS = 10000000;

        // Standard
        for (int i = 0; i < WARMUP; i++) {
            a.divide(b, 4, RoundingMode.HALF_UP);
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            sink = a.divide(b, 4, RoundingMode.HALF_UP).longValue();
        }
        long stdTime = System.nanoTime() - t0;

        // Optimized (简单 C)
        long optTime = Long.MAX_VALUE;
        if (FastBigDecimalOptimizedFinal.isNativeAvailable()) {
            for (int i = 0; i < WARMUP; i++) {
                FastBigDecimalOptimizedFinal.divideEncoded(a, b, 4, RoundingMode.HALF_UP);
            }
            t0 = System.nanoTime();
            for (int i = 0; i < ITERATIONS; i++) {
                sink = FastBigDecimalOptimizedFinal.divideEncoded(a, b, 4, RoundingMode.HALF_UP).longValue();
            }
            optTime = System.nanoTime() - t0;
        }

        // AVX
        for (int i = 0; i < WARMUP; i++) {
            FastBigDecimalAVX.divide(a, b, 4, RoundingMode.HALF_UP);
        }
        t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            sink = FastBigDecimalAVX.divide(a, b, 4, RoundingMode.HALF_UP).longValue();
        }
        long avxTime = System.nanoTime() - t0;

        double stdNs = stdTime / (double) ITERATIONS;
        double optNs = optTime / (double) ITERATIONS;
        double avxNs = avxTime / (double) ITERATIONS;

        System.out.printf("%-20s %-15s %-15s %-10s%n", "方案", "单次 (ns/op)", "vs Standard", "比率");
        System.out.println("────────────────────────────────────────────────────────────");
        System.out.printf("%-20s %-15.2f %-15.2f %-10.2fx%n", "Standard", stdNs, 0.0, 1.0);
        if (optTime < Long.MAX_VALUE) {
            System.out.printf("%-20s %-15.2f %-15.2f %-10.2fx%n", "简单 C (while)", optNs, optNs - stdNs, optNs / stdNs);
        }
        System.out.printf("%-20s %-15.2f %-15.2f %-10.2fx%n%n", "AVX 单次", avxNs, avxNs - stdNs, avxNs / stdNs);
    }

    private static void benchmarkSingleMultiply(BigDecimal a, BigDecimal b) {
        final int WARMUP = 100000;
        final int ITERATIONS = 10000000;

        // Standard
        for (int i = 0; i < WARMUP; i++) {
            a.multiply(b);
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            sink = a.multiply(b).longValue();
        }
        long stdTime = System.nanoTime() - t0;

        // Optimized
        long optTime = Long.MAX_VALUE;
        if (FastBigDecimalOptimizedFinal.isNativeAvailable()) {
            for (int i = 0; i < WARMUP; i++) {
                FastBigDecimalOptimizedFinal.multiplyEncoded(a, b);
            }
            t0 = System.nanoTime();
            for (int i = 0; i < ITERATIONS; i++) {
                sink = FastBigDecimalOptimizedFinal.multiplyEncoded(a, b).longValue();
            }
            optTime = System.nanoTime() - t0;
        }

        // AVX
        for (int i = 0; i < WARMUP; i++) {
            FastBigDecimalAVX.multiply(a, b);
        }
        t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            sink = FastBigDecimalAVX.multiply(a, b).longValue();
        }
        long avxTime = System.nanoTime() - t0;

        double stdNs = stdTime / (double) ITERATIONS;
        double optNs = optTime / (double) ITERATIONS;
        double avxNs = avxTime / (double) ITERATIONS;

        System.out.printf("%-20s %-15s %-15s %-10s%n", "方案", "单次 (ns/op)", "vs Standard", "比率");
        System.out.println("────────────────────────────────────────────────────────────");
        System.out.printf("%-20s %-15.2f %-15.2f %-10.2fx%n", "Standard", stdNs, 0.0, 1.0);
        if (optTime < Long.MAX_VALUE) {
            System.out.printf("%-20s %-15.2f %-15.2f %-10.2fx%n", "简单 C", optNs, optNs - stdNs, optNs / stdNs);
        }
        System.out.printf("%-20s %-15.2f %-15.2f %-10.2fx%n%n", "AVX 单次", avxNs, avxNs - stdNs, avxNs / stdNs);
    }

    private static void benchmarkBatchDivide(BigDecimal[] data) {
        final int WARMUP = 1000;
        final int ITERATIONS = 10000;
        final int BATCH_SIZE = 100;

        // 准备批量数据
        BigDecimal[] dividends = new BigDecimal[BATCH_SIZE];
        BigDecimal[] divisors = new BigDecimal[BATCH_SIZE];
        for (int i = 0; i < BATCH_SIZE; i++) {
            dividends[i] = data[i % data.length];
            divisors[i] = data[(i + 1) % data.length];
        }

        // Standard (循环)
        for (int i = 0; i < WARMUP; i++) {
            for (int j = 0; j < BATCH_SIZE; j++) {
                dividends[j].divide(divisors[j], 4, RoundingMode.HALF_UP);
            }
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            for (int j = 0; j < BATCH_SIZE; j++) {
                sink = dividends[j].divide(divisors[j], 4, RoundingMode.HALF_UP).longValue();
            }
        }
        long stdTime = System.nanoTime() - t0;

        // AVX 批处理
        for (int i = 0; i < WARMUP; i++) {
            FastBigDecimalAVX.divideBatch(dividends, divisors, 4, RoundingMode.HALF_UP);
        }
        t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            BigDecimal[] results = FastBigDecimalAVX.divideBatch(dividends, divisors, 4, RoundingMode.HALF_UP);
            sink = results[0].longValue();
        }
        long avxTime = System.nanoTime() - t0;

        double stdNsPerOp = stdTime / (double) (ITERATIONS * BATCH_SIZE);
        double avxNsPerOp = avxTime / (double) (ITERATIONS * BATCH_SIZE);

        System.out.printf("%-20s %-15s %-15s %-10s%n", "方案", "单次 (ns/op)", "vs Standard", "比率");
        System.out.println("────────────────────────────────────────────────────────────");
        System.out.printf("%-20s %-15.2f %-15.2f %-10.2fx%n", "Standard (循环)", stdNsPerOp, 0.0, 1.0);
        System.out.printf("%-20s %-15.2f %-15.2f %-10.2fx%n%n", "AVX 批处理", avxNsPerOp, avxNsPerOp - stdNsPerOp, avxNsPerOp / stdNsPerOp);
    }

    private static void benchmarkBatchMultiply(BigDecimal[] data) {
        final int WARMUP = 1000;
        final int ITERATIONS = 10000;
        final int BATCH_SIZE = 100;

        BigDecimal[] aArray = new BigDecimal[BATCH_SIZE];
        BigDecimal[] bArray = new BigDecimal[BATCH_SIZE];
        for (int i = 0; i < BATCH_SIZE; i++) {
            aArray[i] = data[i % data.length];
            bArray[i] = data[(i + 1) % data.length];
        }

        // Standard
        for (int i = 0; i < WARMUP; i++) {
            for (int j = 0; j < BATCH_SIZE; j++) {
                aArray[j].multiply(bArray[j]);
            }
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            for (int j = 0; j < BATCH_SIZE; j++) {
                sink = aArray[j].multiply(bArray[j]).longValue();
            }
        }
        long stdTime = System.nanoTime() - t0;

        // AVX 批处理
        for (int i = 0; i < WARMUP; i++) {
            FastBigDecimalAVX.multiplyBatch(aArray, bArray);
        }
        t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            BigDecimal[] results = FastBigDecimalAVX.multiplyBatch(aArray, bArray);
            sink = results[0].longValue();
        }
        long avxTime = System.nanoTime() - t0;

        double stdNsPerOp = stdTime / (double) (ITERATIONS * BATCH_SIZE);
        double avxNsPerOp = avxTime / (double) (ITERATIONS * BATCH_SIZE);

        System.out.printf("%-20s %-15s %-15s %-10s%n", "方案", "单次 (ns/op)", "vs Standard", "比率");
        System.out.println("────────────────────────────────────────────────────────────");
        System.out.printf("%-20s %-15.2f %-15.2f %-10.2fx%n", "Standard (循环)", stdNsPerOp, 0.0, 1.0);
        System.out.printf("%-20s %-15.2f %-15.2f %-10.2fx%n%n", "AVX 批处理", avxNsPerOp, avxNsPerOp - stdNsPerOp, avxNsPerOp / stdNsPerOp);
    }

    private static void benchmarkMicroserviceScenario(BigDecimal[] data) {
        final int WARMUP = 1000;
        final int ITERATIONS = 10000;
        final int ITEMS_PER_ORDER = 100;

        // 准备商品数据
        BigDecimal[] prices = new BigDecimal[ITEMS_PER_ORDER];
        int[] quantities = new int[ITEMS_PER_ORDER];
        Random random = new Random(42);

        for (int i = 0; i < ITEMS_PER_ORDER; i++) {
            prices[i] = new BigDecimal(10 + random.nextInt(1000))
                .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
            quantities[i] = 1 + random.nextInt(100);
        }

        // Standard
        for (int i = 0; i < WARMUP; i++) {
            BigDecimal total = BigDecimal.ZERO;
            for (int j = 0; j < ITEMS_PER_ORDER; j++) {
                total = total.add(prices[j].multiply(BigDecimal.valueOf(quantities[j])));
            }
            sink = total.longValue();
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            BigDecimal total = BigDecimal.ZERO;
            for (int j = 0; j < ITEMS_PER_ORDER; j++) {
                total = total.add(prices[j].multiply(BigDecimal.valueOf(quantities[j])));
            }
            sink = total.longValue();
        }
        long stdTime = System.nanoTime() - t0;

        // AVX 批处理
        BigDecimal[] priceValues = new BigDecimal[ITEMS_PER_ORDER];
        BigDecimal[] qtyValues = new BigDecimal[ITEMS_PER_ORDER];
        for (int i = 0; i < ITEMS_PER_ORDER; i++) {
            priceValues[i] = prices[i];
            qtyValues[i] = BigDecimal.valueOf(quantities[i]);
        }

        for (int i = 0; i < WARMUP; i++) {
            BigDecimal[] lineTotals = FastBigDecimalAVX.multiplyBatch(priceValues, qtyValues);
            BigDecimal total = BigDecimal.ZERO;
            for (BigDecimal lt : lineTotals) {
                total = total.add(lt);
            }
            sink = total.longValue();
        }
        t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            BigDecimal[] lineTotals = FastBigDecimalAVX.multiplyBatch(priceValues, qtyValues);
            BigDecimal total = BigDecimal.ZERO;
            for (BigDecimal lt : lineTotals) {
                total = total.add(lt);
            }
            sink = total.longValue();
        }
        long avxTime = System.nanoTime() - t0;

        double stdPerOrder = stdTime / (double) ITERATIONS / 1000; // μs
        double avxPerOrder = avxTime / (double) ITERATIONS / 1000; // μs

        System.out.printf("  Standard: %.2f μs/order%n", stdPerOrder);
        System.out.printf("  AVX 批处理: %.2f μs/order%n", avxPerOrder);
        System.out.printf("  比率: %.2fx%n%n", avxPerOrder / stdPerOrder);
    }

    private static void printSummary() {
        System.out.println("═══════════════════════════════════════════════════════════════\n");
        System.out.println("AVX 优化总结：\n");
        System.out.println("  ✗ 单次操作：AVX 无明显优势（单次操作无法利用 SIMD）");
        System.out.println("  ✓ 批处理：AVX 向量化可显著提升性能");
        System.out.println("  ⚠ 需要批量数据才能发挥 AVX 优势\n");
        System.out.println("建议：");
        System.out.println("  → 批量计算场景使用 AVX API");
        System.out.println("  → 单次计算继续使用 Standard");
    }
}
