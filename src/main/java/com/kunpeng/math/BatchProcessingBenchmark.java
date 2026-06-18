package com.kunpeng.math;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

/**
 * 批处理 Benchmark - 验证批量操作时 FFI 开销摊薄
 *
 * 核心假设：批量操作可以摊薄 FFI 调用开销
 */
public class BatchProcessingBenchmark {

    static volatile long sink;

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║         批处理 Benchmark（FFI 开销摊薄）                   ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        boolean nativeAvailable = FastBigDecimalOptimizedFinal.isNativeAvailable();

        if (!nativeAvailable) {
            System.out.println("⚠ Native 库不可用，只测试 Standard");
            return;
        }

        // 测试不同批量大小
        int[] batchSizes = {1, 10, 100, 1000};

        System.out.println("┌─ Divide 批处理测试 ─────────────────────────────────────────┐");
        System.out.println("  测试场景：批量除法 (a[i] / b[i])");
        System.out.println("  目标 scale: 2, 舍入: HALF_UP\n");
        System.out.printf("%-10s %-15s %-15s %-15s%n", "BatchSize", "Standard(ns)", "Encoded(ns)", "比率");
        System.out.println("────────────────────────────────────────────────────────────");

        for (int batchSize : batchSizes) {
            BatchResult std = benchmarkBatchDivide("Standard", batchSize, false);
            BatchResult enc = benchmarkBatchDivide("Encoded", batchSize, true);
            double ratio = enc.perOp / std.perOp;

            System.out.printf("%-10d %-15.2f %-15.2f %-15.2fx%n",
                batchSize, std.perOp, enc.perOp, ratio);
        }

        System.out.println("\n┌─ Multiply 批处理测试 ──────────────────────────────────────┐");
        System.out.println("  测试场景：批量乘法 (a[i] * b[i])\n");
        System.out.printf("%-10s %-15s %-15s %-15s%n", "BatchSize", "Standard(ns)", "Encoded(ns)", "比率");
        System.out.println("────────────────────────────────────────────────────────────");

        for (int batchSize : batchSizes) {
            BatchResult std = benchmarkBatchMultiply("Standard", batchSize, false);
            BatchResult enc = benchmarkBatchMultiply("Encoded", batchSize, true);
            double ratio = enc.perOp / std.perOp;

            System.out.printf("%-10d %-15.2f %-15.2f %-15.2fx%n",
                batchSize, std.perOp, enc.perOp, ratio);
        }

        System.out.println("\n┌─ 微服务打流场景模拟 ──────────────────────────────────────────┐");
        benchmarkMicroserviceScenario();
    }

    private static BatchResult benchmarkBatchDivide(String name, int batchSize, boolean useNative) {
        final int WARMUP = 10000;
        final int ITERATIONS = 100000;

        // 准备测试数据
        BigDecimal[] a = new BigDecimal[batchSize];
        BigDecimal[] b = new BigDecimal[batchSize];
        Random random = new Random(42);

        for (int i = 0; i < batchSize; i++) {
            a[i] = new BigDecimal(100 + random.nextInt(10000))
                .divide(new BigDecimal(100), 4, RoundingMode.HALF_UP);
            b[i] = new BigDecimal(100 + random.nextInt(1000))
                .divide(new BigDecimal(100), 4, RoundingMode.HALF_UP);
        }

        // Warmup
        for (int i = 0; i < WARMUP; i++) {
            for (int j = 0; j < batchSize; j++) {
                BigDecimal result = useNative ?
                    FastBigDecimalOptimizedFinal.divideEncoded(a[j], b[j], 2, RoundingMode.HALF_UP) :
                    a[j].divide(b[j], 2, RoundingMode.HALF_UP);
                sink = result.longValue();
            }
        }

        // 测试
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            for (int j = 0; j < batchSize; j++) {
                BigDecimal result = useNative ?
                    FastBigDecimalOptimizedFinal.divideEncoded(a[j], b[j], 2, RoundingMode.HALF_UP) :
                    a[j].divide(b[j], 2, RoundingMode.HALF_UP);
                sink = result.longValue();
            }
        }
        long elapsed = System.nanoTime() - t0;

        double perOp = elapsed / (double) (ITERATIONS * batchSize);
        return new BatchResult(name, batchSize, perOp);
    }

    private static BatchResult benchmarkBatchMultiply(String name, int batchSize, boolean useNative) {
        final int WARMUP = 10000;
        final int ITERATIONS = 100000;

        BigDecimal[] a = new BigDecimal[batchSize];
        BigDecimal[] b = new BigDecimal[batchSize];
        Random random = new Random(42);

        for (int i = 0; i < batchSize; i++) {
            a[i] = new BigDecimal(100 + random.nextInt(10000))
                .divide(new BigDecimal(100), 4, RoundingMode.HALF_UP);
            b[i] = new BigDecimal(100 + random.nextInt(1000))
                .divide(new BigDecimal(100), 4, RoundingMode.HALF_UP);
        }

        // Warmup
        for (int i = 0; i < WARMUP; i++) {
            for (int j = 0; j < batchSize; j++) {
                BigDecimal result = useNative ?
                    FastBigDecimalOptimizedFinal.multiplyEncoded(a[j], b[j]) :
                    a[j].multiply(b[j]);
                sink = result.longValue();
            }
        }

        // 测试
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            for (int j = 0; j < batchSize; j++) {
                BigDecimal result = useNative ?
                    FastBigDecimalOptimizedFinal.multiplyEncoded(a[j], b[j]) :
                    a[j].multiply(b[j]);
                sink = result.longValue();
            }
        }
        long elapsed = System.nanoTime() - t0;

        double perOp = elapsed / (double) (ITERATIONS * batchSize);
        return new BatchResult(name, batchSize, perOp);
    }

    private static void benchmarkMicroserviceScenario() {
        System.out.println("  场景：电商订单计算（单价 × 数量）");
        System.out.println("  单次请求：批量计算 100 个商品\n");

        final int ITEMS_PER_ORDER = 100;
        final int WARMUP_ORDERS = 1000;
        final int TEST_ORDERS = 10000;

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
        long t0 = System.nanoTime();
        for (int order = 0; order < WARMUP_ORDERS; order++) {
            BigDecimal total = BigDecimal.ZERO;
            for (int i = 0; i < ITEMS_PER_ORDER; i++) {
                BigDecimal lineTotal = prices[i].multiply(
                    BigDecimal.valueOf(quantities[i])
                );
                total = total.add(lineTotal);
            }
            sink = total.longValue();
        }

        t0 = System.nanoTime();
        for (int order = 0; order < TEST_ORDERS; order++) {
            BigDecimal total = BigDecimal.ZERO;
            for (int i = 0; i < ITEMS_PER_ORDER; i++) {
                BigDecimal lineTotal = prices[i].multiply(
                    BigDecimal.valueOf(quantities[i])
                );
                total = total.add(lineTotal);
            }
            sink = total.longValue();
        }
        long stdTime = System.nanoTime() - t0;

        // Encoded
        for (int order = 0; order < WARMUP_ORDERS; order++) {
            BigDecimal total = BigDecimal.ZERO;
            for (int i = 0; i < ITEMS_PER_ORDER; i++) {
                BigDecimal lineTotal = FastBigDecimalOptimizedFinal.multiplyEncoded(
                    prices[i],
                    BigDecimal.valueOf(quantities[i])
                );
                total = total.add(lineTotal);
            }
            sink = total.longValue();
        }

        t0 = System.nanoTime();
        for (int order = 0; order < TEST_ORDERS; order++) {
            BigDecimal total = BigDecimal.ZERO;
            for (int i = 0; i < ITEMS_PER_ORDER; i++) {
                BigDecimal lineTotal = FastBigDecimalOptimizedFinal.multiplyEncoded(
                    prices[i],
                    BigDecimal.valueOf(quantities[i])
                );
                total = total.add(lineTotal);
            }
            sink = total.longValue();
        }
        long encTime = System.nanoTime() - t0;

        double stdPerItem = stdTime / (double) (TEST_ORDERS * ITEMS_PER_ORDER);
        double encPerItem = encTime / (double) (TEST_ORDERS * ITEMS_PER_ORDER);

        System.out.printf("  Standard: %.2f ns/item%n", stdPerItem);
        System.out.printf("  Encoded:  %.2f ns/item%n", encPerItem);
        System.out.printf("  比率:    %.2fx%n", encPerItem / stdPerItem);

        System.out.println("\n  订单级延迟（100 商品/订单）：");
        System.out.printf("  Standard: %.2f μs/order%n", stdTime / (double) TEST_ORDERS / 1000);
        System.out.printf("  Encoded:  %.2f μs/order%n", encTime / (double) TEST_ORDERS / 1000);

        System.out.println("\n  结论：");
        if (encPerItem < stdPerItem) {
            System.out.println("    ✓ 批处理场景下 Native 更快");
        } else {
            double overheadPerOrder = (encTime - stdTime) / (double) TEST_ORDERS / 1000; // μs
            System.out.printf("    ⚠ Native 每订单慢 %.2f μs%n", overheadPerOrder);
            System.out.println("    在高 QPS 场景下，这个差异可能影响吞吐");
        }
    }

    static class BatchResult {
        final String name;
        final int batchSize;
        final double perOp;

        BatchResult(String name, int batchSize, double perOp) {
            this.name = name;
            this.batchSize = batchSize;
            this.perOp = perOp;
        }
    }
}
