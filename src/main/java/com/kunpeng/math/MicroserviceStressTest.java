package com.kunpeng.math;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;

/**
 * 微服务高压打流场景验证
 *
 * 模拟场景：
 * - 金融风控服务：批量计算风险指标
 * - 实时统计服务：聚合计算
 * - 定价服务：批量价格计算
 *
 * 负载特征：
 * - 高并发（多线程）
 * - 批量请求（每请求包含多个操作）
 * - Non-Compact Path（大数值计算）
 */
public class MicroserviceStressTest {

    // 测试配置 - 多规模验证
    private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int REQUESTS_PER_THREAD = 10000;
    private static final int[] BATCH_SIZES = {100, 1000, 5000};  // 多规模测试

    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║     微服务高压打流场景验证                                 ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        System.out.println("环境配置：");
        System.out.println("  CPU 核心数: " + THREAD_COUNT);
        System.out.println("  并发线程数: " + THREAD_COUNT);
        System.out.println("  每线程请求数: " + REQUESTS_PER_THREAD);
        System.out.println("  测试规模: " + Arrays.toString(BATCH_SIZES));
        System.out.println();

        boolean mklAvailable = FastBigDecimalMKL.isNativeAvailable();
        System.out.println("库状态：");
        System.out.println("  MKL/标准 libm: " + (mklAvailable ? "✓ 可用" : "✗ 不可用"));
        System.out.println();

        // ========== 场景 1: 金融风控服务 ==========
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("场景 1: 金融风控服务 - 批量风险计算 (divide)");
        System.out.println("═══════════════════════════════════════════════════════════════\n");

        for (int batchSize : BATCH_SIZES) {
            System.out.println("┌─ 批量大小: " + batchSize + " ──────────────────────────────────────────┐");
            StressResult stdRisk = stressTestRiskStandard(batchSize);
            StressResult mklRisk = mklAvailable ? stressTestRiskMKL(batchSize) : null;
            printResult("风控服务", stdRisk, mklRisk);
            System.out.println();
        }

        // ========== 场景 2: 实时统计服务 ==========
        System.out.println("\n═══════════════════════════════════════════════════════════════");
        System.out.println("场景 2: 实时统计服务 - 聚合计算 (multiply)");
        System.out.println("═══════════════════════════════════════════════════════════════\n");

        for (int batchSize : BATCH_SIZES) {
            System.out.println("┌─ 批量大小: " + batchSize + " ──────────────────────────────────────────┐");
            StressResult stdStats = stressTestStatsStandard(batchSize);
            StressResult mklStats = mklAvailable ? stressTestStatsMKL(batchSize) : null;
            printResult("统计服务", stdStats, mklStats);
            System.out.println();
        }

        // ========== 场景 3: 定价服务 ==========
        System.out.println("\n═══════════════════════════════════════════════════════════════");
        System.out.println("场景 3: 定价服务 - 批量定价 (setScale)");
        System.out.println("═══════════════════════════════════════════════════════════════\n");

        for (int batchSize : BATCH_SIZES) {
            System.out.println("┌─ 批量大小: " + batchSize + " ──────────────────────────────────────────┐");
            StressResult stdPrice = stressTestPricingStandard(batchSize);
            StressResult mklPrice = mklAvailable ? stressTestPricingMKL(batchSize) : null;
            printResult("定价服务", stdPrice, mklPrice);
            System.out.println();
        }
    }

    // ========== 场景 1: 金融风控服务 ==========

    private static StressResult stressTestRiskStandard(int batchSize) throws Exception {
        return runStressTest("风控-Standard", batchSize, () -> {
            BigDecimal[] exposures = generateBatch(batchSize);
            BigDecimal[] factors = generateBatch(batchSize);
            BigDecimal[] results = new BigDecimal[batchSize];

            for (int i = 0; i < batchSize; i++) {
                results[i] = exposures[i].divide(factors[i], 10, RoundingMode.HALF_UP);
            }
            return results;
        });
    }

    private static StressResult stressTestRiskMKL(int batchSize) throws Exception {
        return runStressTest("风控-MKL", batchSize, () -> {
            BigDecimal[] exposures = generateBatch(batchSize);
            BigDecimal[] factors = generateBatch(batchSize);
            return FastBigDecimalMKL.divideBatch(exposures, factors, 10, RoundingMode.HALF_UP);
        });
    }

    // ========== 场景 2: 实时统计服务 ==========

    private static StressResult stressTestStatsStandard(int batchSize) throws Exception {
        return runStressTest("统计-Standard", batchSize, () -> {
            BigDecimal[] values = generateBatch(batchSize);
            BigDecimal[] weights = generateBatch(batchSize);
            BigDecimal[] results = new BigDecimal[batchSize];

            for (int i = 0; i < batchSize; i++) {
                results[i] = values[i].multiply(weights[i]);
            }
            return results;
        });
    }

    private static StressResult stressTestStatsMKL(int batchSize) throws Exception {
        return runStressTest("统计-MKL", batchSize, () -> {
            BigDecimal[] values = generateBatch(batchSize);
            BigDecimal[] weights = generateBatch(batchSize);
            return FastBigDecimalMKL.multiplyBatch(values, weights);
        });
    }

    // ========== 场景 3: 定价服务 ==========

    private static StressResult stressTestPricingStandard(int batchSize) throws Exception {
        return runStressTest("定价-Standard", batchSize, () -> {
            BigDecimal[] prices = generateBatch(batchSize);
            BigDecimal[] results = new BigDecimal[batchSize];

            for (int i = 0; i < batchSize; i++) {
                results[i] = prices[i].setScale(2, RoundingMode.HALF_UP);
            }
            return results;
        });
    }

    private static StressResult stressTestPricingMKL(int batchSize) throws Exception {
        return runStressTest("定价-MKL", batchSize, () -> {
            BigDecimal[] prices = generateBatch(batchSize);
            return FastBigDecimalMKL.setScaleBatch(prices, 2, RoundingMode.HALF_UP);
        });
    }

    // ========== 框架方法 ==========

    private static StressResult runStressTest(String name, int batchSize, BatchOperation op) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);
        LongAdder errorCount = new LongAdder();

        long startTime = System.nanoTime();

        for (int t = 0; t < THREAD_COUNT; t++) {
            executor.submit(() -> {
                try {
                    for (int r = 0; r < REQUESTS_PER_THREAD; r++) {
                        long reqStart = System.nanoTime();
                        try {
                            BigDecimal[] results = op.execute();
                            long reqTime = System.nanoTime() - reqStart;
                            totalLatency.addAndGet(reqTime);
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            errorCount.increment();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long endTime = System.nanoTime();
        executor.shutdown();

        long totalTime = endTime - startTime;
        long avgLatency = totalLatency.get() / successCount.get();
        double qps = (successCount.get() * 1e9) / totalTime;
        double throughput = (successCount.get() * batchSize * 1e9) / totalTime;

        return new StressResult(name, totalTime, avgLatency, qps, throughput,
            successCount.get(), errorCount.sum(), batchSize);
    }

    private static BigDecimal[] generateBatch(int size) {
        BigDecimal[] batch = new BigDecimal[size];
        for (int i = 0; i < size; i++) {
            // 生成 Non-Compact Path 数值（>18 位）
            batch[i] = new BigDecimal("12345678901234567890123456789" + (i % 10) + ".123456789");
        }
        return batch;
    }

    private static void printResult(String scenario, StressResult std, StressResult mkl) {
        System.out.printf("%-22s %-16s %-16s %-14s%n", "指标", "Standard", "MKL", "差异");
        System.out.println("──────────────────────────────────────────────────────────────────");
        System.out.printf("%-22s %-16s %-16s %-14s%n", "QPS (请求/秒)",
            String.format("%.0f", std.qps),
            mkl != null ? String.format("%.0f", mkl.qps) : "N/A",
            mkl != null ? String.format("%+.1f%%", (mkl.qps - std.qps) / std.qps * 100) : "N/A");
        System.out.printf("%-22s %-16s %-16s %-14s%n", "吞吐量 (Mops/秒)",
            String.format("%.2f", std.throughput / 1e6),
            mkl != null ? String.format("%.2f", mkl.throughput / 1e6) : "N/A",
            mkl != null ? String.format("%+.1f%%", (mkl.throughput - std.throughput) / std.throughput * 100) : "N/A");
        System.out.printf("%-22s %-16s %-16s %-14s%n", "平均延迟 (ms/请求)",
            String.format("%.2f", std.avgLatency / 1e6),
            mkl != null ? String.format("%.2f", mkl.avgLatency / 1e6) : "N/A",
            mkl != null ? String.format("%+.1f%%", (mkl.avgLatency - std.avgLatency) / (double)std.avgLatency * 100) : "N/A");
        System.out.printf("%-22s %-16d %-16d %-14s%n", "成功请求", std.successCount,
            mkl != null ? mkl.successCount : 0,
            mkl != null ? String.format("%+d", mkl.successCount - std.successCount) : "N/A");
        System.out.printf("%-22s %-16d %-16d %-14s%n", "错误数", std.errorCount,
            mkl != null ? mkl.errorCount : 0,
            mkl != null ? String.format("%+d", mkl.errorCount - std.errorCount) : "N/A");
    }

    private static void printSummary(StressResult stdRisk, StressResult mklRisk,
                                     StressResult stdStats, StressResult mklStats,
                                     StressResult stdPrice, StressResult mklPrice) {
        System.out.println("\n═══════════════════════════════════════════════════════════════");
        System.out.println("微服务高压场景总结");
        System.out.println("═══════════════════════════════════════════════════════════════\n");

        System.out.println("场景对比 (QPS)：");
        System.out.printf("  %-15s %-12s %-12s %-12s%n", "场景", "Standard", "MKL", "差异");
        System.out.println("  " + "─".repeat(55));
        if (mklRisk != null) {
            System.out.printf("  %-15s %-12.0f %-12.0f %-12.1f%%%n", "风控服务",
                stdRisk.qps, mklRisk.qps, (mklRisk.qps - stdRisk.qps) / stdRisk.qps * 100);
        }
        if (mklStats != null) {
            System.out.printf("  %-15s %-12.0f %-12.0f %-12.1f%%%n", "统计服务",
                stdStats.qps, mklStats.qps, (mklStats.qps - stdStats.qps) / stdStats.qps * 100);
        }
        if (mklPrice != null) {
            System.out.printf("  %-15s %-12.0f %-12.0f %-12.1f%%%n", "定价服务",
                stdPrice.qps, mklPrice.qps, (mklPrice.qps - stdPrice.qps) / stdPrice.qps * 100);
        }

        System.out.println("\n关键发现：");
        if (mklStats != null && mklStats.qps > stdStats.qps * 1.5) {
            System.out.println("  ✓ 统计服务（multiply）MKL 优势显著 (>50%)");
        }
        if (mklPrice != null && mklPrice.qps > stdPrice.qps * 1.3) {
            System.out.println("  ✓ 定价服务（setScale）MKL 明显优势 (>30%)");
        }
        if (mklRisk != null && mklRisk.qps > stdRisk.qps * 1.1) {
            System.out.println("  ✓ 风控服务（divide）MKL 有优势");
        }

        System.out.println("\n生产环境建议：");
        System.out.println("  • Multiply/SetScale 场景优先使用 MKL");
        System.out.println("  • Divide 场景根据实际数据特征选择");
        System.out.println("  • 鲲鹏平台可使用 NEON/SVE 获得类似收益");
    }

    // ========== 数据结构 ==========

    @FunctionalInterface
    private interface BatchOperation {
        BigDecimal[] execute() throws Exception;
    }

    private static class StressResult {
        String name;
        long totalTime;
        long avgLatency;
        double qps;
        double throughput;
        int successCount;
        long errorCount;
        int batchSize;

        StressResult(String name, long totalTime, long avgLatency, double qps,
                    double throughput, int successCount, long errorCount, int batchSize) {
            this.name = name;
            this.totalTime = totalTime;
            this.avgLatency = avgLatency;
            this.qps = qps;
            this.throughput = throughput;
            this.successCount = successCount;
            this.errorCount = errorCount;
            this.batchSize = batchSize;
        }
    }
}
