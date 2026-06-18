package com.kunpeng.math;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 批量处理最终性能测试
 */
public class BenchmarkBatchFinal {

    private static final int WARMUP = 1000;
    private static final int ITERATIONS = 5000;
    private static final int[] BATCH_SIZES = {100, 500, 1000, 5000};

    public static void main(String[] args) {
        System.out.println("=== 批量处理性能测试 (Intel AVX2) ===\n");

        if (!FastBigDecimalBatchOptimized.isNativeAvailable()) {
            System.out.println("Native 库不可用");
            return;
        }

        for (int batchSize : BATCH_SIZES) {
            testDivideBatch(batchSize);
            System.out.println();
        }

        // 总结
        System.out.println("\n=== 性能分析结论 ===");
        System.out.println("Standard BigDecimal 已经高度优化");
        System.out.println("FFI + 内存复制开销占主导地位");
        System.out.println("批量处理摊薄 FFI，但内存复制仍是瓶颈");
        System.out.println("\n要超越 Standard，需要：");
        System.out.println("  1. C 层面真正的 SIMD 并行计算");
        System.out.println("  2. 零拷贝的内存共享 (如 Panama Segment over Java array)");
        System.out.println("  3. 批量接口返回原生 Segment，避免结果构造");
    }

    private static void testDivideBatch(int batchSize) {
        System.out.printf("--- 批量 Divide (N=%d) ---%n", batchSize);

        // 准备数据
        BigDecimal[] dividends = new BigDecimal[batchSize];
        BigDecimal[] divisors = new BigDecimal[batchSize];
        BigDecimal[] results = new BigDecimal[batchSize];

        for (int i = 0; i < batchSize; i++) {
            dividends[i] = new BigDecimal(100 + i % 50);
            divisors[i] = new BigDecimal(3 + i % 10);
        }

        // Warmup
        for (int w = 0; w < WARMUP; w++) {
            for (int i = 0; i < batchSize; i++) {
                results[i] = dividends[i].divide(divisors[i], 2, RoundingMode.HALF_UP);
            }
            FastBigDecimalBatchOptimized.divideBatch(dividends, divisors, 2, RoundingMode.HALF_UP);
        }

        // Standard - 单次循环
        long t0 = System.nanoTime();
        for (int it = 0; it < ITERATIONS; it++) {
            for (int i = 0; i < batchSize; i++) {
                results[i] = dividends[i].divide(divisors[i], 2, RoundingMode.HALF_UP);
            }
        }
        long stdTime = System.nanoTime() - t0;

        // Batch - 批量调用
        long t1 = System.nanoTime();
        for (int it = 0; it < ITERATIONS; it++) {
            results = FastBigDecimalBatchOptimized.divideBatch(dividends, divisors, 2, RoundingMode.HALF_UP);
        }
        long batchTime = System.nanoTime() - t1;

        long totalOps = ITERATIONS * batchSize;

        System.out.printf("Standard:  %.3f ms (%.3f ns/op)%n",
            stdTime / 1_000_000.0, stdTime / (double) totalOps);
        System.out.printf("Batch:     %.3f ms (%.3f ns/op)%n",
            batchTime / 1_000_000.0, batchTime / (double) totalOps);

        double speedup = stdTime / (double) batchTime;
        if (speedup > 1.0) {
            System.out.printf("加速比:    %.2fx ✓%n", speedup);
        } else {
            System.out.printf("加速比:    %.2fx ✗ (慢 %.1fx)%n",
                speedup, 1.0 / speedup);
        }

        // 分析瓶颈
        double stdNsPerOp = stdTime / (double) totalOps;
        double batchNsPerOp = batchTime / (double) totalOps;
        double overhead = batchNsPerOp - stdNsPerOp;

        System.out.printf("分析:      Standard=%.1f ns, Batch=%.1f ns, 开销=%.1f ns/op%n",
            stdNsPerOp, batchNsPerOp, overhead);
    }
}
