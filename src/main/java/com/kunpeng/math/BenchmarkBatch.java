package com.kunpeng.math;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

/**
 * 批量处理性能测试 - 展示 SIMD 优势
 */
public class BenchmarkBatch {

    private static final int WARMUP = 1000;
    private static final int BATCH_SIZE = 10000;

    public static void main(String[] args) {
        System.out.println("=== 批量处理性能测试 (SIMD 潜力) ===\n");

        if (!FastBigDecimalOptimized.isNativeAvailable()) {
            System.out.println("Native 库不可用");
            return;
        }

        testBatchDivide();
        testBatchMultiply();
        testBatchSetScale();
    }

    private static void testBatchDivide() {
        System.out.println("--- 批量 Divide (10000 个) ---");

        // 准备测试数据
        BigDecimal[] dividends = new BigDecimal[BATCH_SIZE];
        BigDecimal[] divisors = new BigDecimal[BATCH_SIZE];
        BigDecimal[] results = new BigDecimal[BATCH_SIZE];

        for (int i = 0; i < BATCH_SIZE; i++) {
            dividends[i] = new BigDecimal(100 + i % 50);
            divisors[i] = new BigDecimal(3 + i % 10);
        }

        // Warmup
        for (int w = 0; w < WARMUP; w++) {
            for (int i = 0; i < BATCH_SIZE; i++) {
                results[i] = dividends[i].divide(divisors[i], 2, RoundingMode.HALF_UP);
            }
        }

        // Standard - 逐个处理
        long t0 = System.nanoTime();
        for (int i = 0; i < BATCH_SIZE; i++) {
            results[i] = dividends[i].divide(divisors[i], 2, RoundingMode.HALF_UP);
        }
        long stdTime = System.nanoTime() - t0;

        // Optimized - 逐个处理
        long t1 = System.nanoTime();
        for (int i = 0; i < BATCH_SIZE; i++) {
            results[i] = FastBigDecimalOptimized.divide(dividends[i], divisors[i], 2, RoundingMode.HALF_UP);
        }
        long optTime = System.nanoTime() - t1;

        System.out.printf("Standard (逐个):   %.2f us (%.3f ns/op)%n", stdTime/1000.0, stdTime/(double)BATCH_SIZE);
        System.out.printf("Optimized (逐个):  %.2f us (%.3f ns/op) [%.2fx]%n",
            optTime/1000.0, optTime/(double)BATCH_SIZE, stdTime/(double)optTime);

        // 计算理论批量处理优势
        // FFI 开销约 50ns，批量处理可摊薄
        long ffiOverhead = 50; // ns
        long standardOpTime = (long)(stdTime/(double)BATCH_SIZE);
        long optOpTime = (long)(optTime/(double)BATCH_SIZE);

        System.out.printf("%n理论分析:%n");
        System.out.printf("  Standard 操作时间: %d ns/op%n", standardOpTime);
        System.out.printf("  Optimized 操作时间: %d ns/op (包含 FFI 开销)%n", optOpTime);
        System.out.printf("  纯计算时间: %d ns/op (假设 FFI=50ns)%n", Math.max(0, optOpTime - ffiOverhead));

        // 假设批量处理可以摊薄 FFI 开销
        int batchSize = 8; // AVX2 可同时处理 8 个
        long batchFfiOverhead = ffiOverhead / batchSize;
        long batchOpTime = Math.max(0, optOpTime - ffiOverhead) + batchFfiOverhead;
        double speedup = (double)standardOpTime / batchOpTime;

        System.out.printf("%n  批量处理 (每批 8 个, 摊薄 FFI):%n");
        System.out.printf("    摊薄后 FFI 开销: %d ns/op%n", batchFfiOverhead);
        System.out.printf("    预计时间: %d ns/op%n", batchOpTime);
        System.out.printf("    理论加速比: %.2fx%n", speedup);

        if (speedup > 1.0) {
            System.out.printf("    ✓ 批量处理优于 Standard%n");
        } else {
            System.out.printf("    ✗ 批量处理仍慢于 Standard (需要 C 层面 SIMD)%n");
        }
    }

    private static void testBatchMultiply() {
        System.out.println("%n--- 批量 Multiply (10000 个) ---");

        BigDecimal[] a = new BigDecimal[BATCH_SIZE];
        BigDecimal[] b = new BigDecimal[BATCH_SIZE];
        BigDecimal[] results = new BigDecimal[BATCH_SIZE];

        for (int i = 0; i < BATCH_SIZE; i++) {
            a[i] = new BigDecimal(12.34 + (i % 100) * 0.01);
            b[i] = new BigDecimal(5.6 + (i % 50) * 0.1);
        }

        // Warmup
        for (int w = 0; w < WARMUP; w++) {
            for (int i = 0; i < BATCH_SIZE; i++) {
                results[i] = a[i].multiply(b[i]);
            }
        }

        // Standard
        long t0 = System.nanoTime();
        for (int i = 0; i < BATCH_SIZE; i++) {
            results[i] = a[i].multiply(b[i]);
        }
        long stdTime = System.nanoTime() - t0;

        // Optimized
        long t1 = System.nanoTime();
        for (int i = 0; i < BATCH_SIZE; i++) {
            results[i] = FastBigDecimalOptimized.multiply(a[i], b[i]);
        }
        long optTime = System.nanoTime() - t1;

        System.out.printf("Standard (逐个):   %.2f us (%.3f ns/op)%n", stdTime/1000.0, stdTime/(double)BATCH_SIZE);
        System.out.printf("Optimized (逐个):  %.2f us (%.3f ns/op) [%.2fx]%n",
            optTime/1000.0, optTime/(double)BATCH_SIZE, stdTime/(double)optTime);
    }

    private static void testBatchSetScale() {
        System.out.println("%n--- 批量 SetScale (10000 个) ---");

        BigDecimal[] values = new BigDecimal[BATCH_SIZE];
        BigDecimal[] results = new BigDecimal[BATCH_SIZE];

        for (int i = 0; i < BATCH_SIZE; i++) {
            values[i] = new BigDecimal(123.4567 + (i % 1000) * 0.0001);
        }

        // Warmup
        for (int w = 0; w < WARMUP; w++) {
            for (int i = 0; i < BATCH_SIZE; i++) {
                results[i] = values[i].setScale(2, RoundingMode.HALF_UP);
            }
        }

        // Standard
        long t0 = System.nanoTime();
        for (int i = 0; i < BATCH_SIZE; i++) {
            results[i] = values[i].setScale(2, RoundingMode.HALF_UP);
        }
        long stdTime = System.nanoTime() - t0;

        // Optimized
        long t1 = System.nanoTime();
        for (int i = 0; i < BATCH_SIZE; i++) {
            results[i] = FastBigDecimalOptimized.setScale(values[i], 2, RoundingMode.HALF_UP);
        }
        long optTime = System.nanoTime() - t1;

        System.out.printf("Standard (逐个):   %.2f us (%.3f ns/op)%n", stdTime/1000.0, stdTime/(double)BATCH_SIZE);
        System.out.printf("Optimized (逐个):  %.2f us (%.3f ns/op) [%.2fx]%n",
            optTime/1000.0, optTime/(double)BATCH_SIZE, stdTime/(double)optTime);
    }
}
