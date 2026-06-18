package com.kunpeng.math;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 真实场景 Benchmark - 模拟大型应用
 *
 * 场景：金融交易系统
 * - 计算投资组合收益
 * - 货币转换
 * - 风险评估
 * - 报表生成
 */
public class RealWorldBenchmark {

    private static final int THREADS = Runtime.getRuntime().availableProcessors();
    private static final int WARMUP_SECONDS = 5;
    private static final int MEASURED_SECONDS = 10;

    public static void main(String[] args) throws Exception {
        System.out.println("=== 真实场景 Benchmark ===\n");
        System.out.println("场景: 金融交易系统");
        System.out.println("线程数: " + THREADS);
        System.out.println("Warmup: " + WARMUP_SECONDS + "s");
        System.out.println("测量: " + MEASURED_SECONDS + "s\n");

        // 准备数据
        MarketData marketData = new MarketData(1000);

        // Warmup
        System.out.println("Warmup 中...");
        warmup(marketData);

        // 运行 Benchmark
        System.out.println("开始测量...\n");

        Result stdResult = runBenchmark("Standard", marketData, false);
        Result optResult = runBenchmark("Optimized", marketData, true);

        // 打印结果
        printResults(stdResult, optResult);
    }

    private static void warmup(MarketData data) throws Exception {
        CountDownLatch latch = new CountDownLatch(THREADS);
        long end = System.currentTimeMillis() + WARMUP_SECONDS * 1000;

        for (int i = 0; i < THREADS; i++) {
            new Thread(() -> {
                Random random = new Random();
                while (System.currentTimeMillis() < end) {
                    simulateTrading(random, data, false);
                }
                latch.countDown();
            }).start();
        }
        latch.await();
    }

    private static Result runBenchmark(String name, MarketData data, boolean useNative) throws Exception {
        AtomicLong opCount = new AtomicLong(0);
        AtomicLong errorCount = new AtomicLong(0);
        AtomicBoolean running = new AtomicBoolean(true);
        CountDownLatch startLatch = new CountDownLatch(THREADS);
        CountDownLatch endLatch = new CountDownLatch(THREADS);

        // 启动工作线程
        for (int i = 0; i < THREADS; i++) {
            final int threadId = i;
            new Thread(() -> {
                Random random = new Random(threadId * 12345);
                startLatch.countDown();

                try {
                    startLatch.await();
                } catch (InterruptedException e) {}

                while (running.get()) {
                    try {
                        if (simulateTrading(random, data, useNative)) {
                            opCount.incrementAndGet();
                        } else {
                            errorCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    }
                }

                endLatch.countDown();
            }).start();
        }

        // 等待所有线程就绪
        startLatch.await();
        Thread.sleep(100); // 确保所有线程已启动

        // 开始测量
        long startTime = System.nanoTime();
        Thread.sleep(MEASURED_SECONDS * 1000);
        running.set(false);
        long endTime = System.nanoTime();

        // 等待完成
        endLatch.await(5, TimeUnit.SECONDS);

        long durationMs = (endTime - startTime) / 1_000_000;
        long ops = opCount.get();
        long errors = errorCount.get();

        return new Result(name, durationMs, ops, errors);
    }

    /**
     * 模拟真实交易场景
     * @return true if success
     */
    private static boolean simulateTrading(Random random, MarketData data, boolean useNative) {
        // 场景1: 计算持仓收益 (divide)
        if (random.nextFloat() < 0.3) {
            BigDecimal price = data.getPrice(random.nextInt(data.size()));
            BigDecimal shares = new BigDecimal(100 + random.nextInt(10000));
            BigDecimal cost = new BigDecimal(50 + random.nextInt(100));
            BigDecimal pnl = useNative ?
                FastBigDecimalFixed.divide(shares.multiply(price), cost, 2, RoundingMode.HALF_UP) :
                shares.multiply(price).divide(cost, 2, RoundingMode.HALF_UP);
            return pnl.compareTo(BigDecimal.ZERO) >= 0;
        }

        // 场景2: 货币转换 (multiply)
        if (random.nextFloat() < 0.6) {
            BigDecimal amount = new BigDecimal(1000 + random.nextInt(100000));
            BigDecimal rate = data.getRate(random.nextInt(data.size()));
            BigDecimal converted = useNative ?
                FastBigDecimalFixed.multiply(amount, rate) :
                amount.multiply(rate);
            return converted.compareTo(BigDecimal.ZERO) > 0;
        }

        // 场景3: 风险计算 (setScale)
        BigDecimal risk = new BigDecimal(random.nextDouble() * 100);
        BigDecimal scaled = useNative ?
            FastBigDecimalFixed.setScale(risk, 4, RoundingMode.HALF_UP) :
            risk.setScale(4, RoundingMode.HALF_UP);
        return scaled.scale() == 4;
    }

    private static void printResults(Result std, Result opt) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║                    Benchmark 结果                          ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        long totalOps = std.ops + opt.ops;
        double stdMs = std.durationMs;
        double optMs = opt.durationMs;

        System.out.printf("┌─────────────────┬──────────────┬──────────────┬──────────────┐%n");
        System.out.printf("│ 指标            │ Standard     │ Optimized    │ 差异         │%n");
        System.out.printf("├─────────────────┼──────────────┼──────────────┼──────────────┤%n");

        // 吞吐量
        double stdThroughput = std.ops / (stdMs / 1000.0);
        double optThroughput = opt.ops / (optMs / 1000.0);
        System.out.printf("│ 吞吐量 (ops/s) │ %,11.0f │ %,11.0f │ %11.2fx │%n",
            stdThroughput, optThroughput, optThroughput / stdThroughput);

        // 延迟
        double stdLatency = (stdMs * 1_000_000.0) / std.ops;
        double optLatency = (optMs * 1_000_000.0) / opt.ops;
        System.out.printf("│ 平均延迟 (ns)   │ %,11.0f │ %,11.0f │ %11.2fx │%n",
            stdLatency, optLatency, stdLatency / optLatency);

        // 错误率
        double stdErrorRate = (std.errors * 100.0) / std.ops;
        double optErrorRate = (opt.errors * 100.0) / opt.ops;
        System.out.printf("│ 错误率 (%%)      │ %11.4f │ %11.4f │ %11s │%n",
            stdErrorRate, optErrorRate, "");

        // 总操作数
        System.out.printf("│ 总操作数        │ %,11d │ %,11d │ %11s │%n",
            std.ops, opt.ops, "");

        System.out.printf("└─────────────────┴──────────────┴──────────────┴──────────────┘%n");

        // 结论
        System.out.println("\n结论:");
        if (optThroughput > stdThroughput) {
            System.out.printf("  ✓ Optimized 快 %.1fx%% - 推荐使用%n",
                ((optThroughput / stdThroughput - 1) * 100));
        } else {
            System.out.printf("  ✗ Optimized 慢 %.1fx%% - 推荐保持 Standard%n",
                ((1 - optThroughput / stdThroughput) * 100));
        }

        if (optErrorRate > stdErrorRate + 0.01) {
            System.out.printf("  ⚠ Optimized 错误率更高 (%.4f%% vs %.4f%%)%n",
                optErrorRate, stdErrorRate);
        }
    }

    static class Result {
        String name;
        long durationMs;
        long ops;
        long errors;

        Result(String name, long durationMs, long ops, long errors) {
            this.name = name;
            this.durationMs = durationMs;
            this.ops = ops;
            this.errors = errors;
        }
    }

    /**
     * 模拟市场数据
     */
    static class MarketData {
        private final BigDecimal[] prices;
        private final BigDecimal[] rates;
        private final int size;

        MarketData(int size) {
            this.size = size;
            this.prices = new BigDecimal[size];
            this.rates = new BigDecimal[size];

            Random random = new Random(42);
            for (int i = 0; i < size; i++) {
                prices[i] = new BigDecimal(10 + random.nextDouble() * 1000);
                rates[i] = new BigDecimal(0.5 + random.nextDouble() * 2);
            }
        }

        BigDecimal getPrice(int index) {
            return prices[index % prices.length];
        }

        BigDecimal getRate(int index) {
            return rates[index % rates.length];
        }

        int size() {
            return size;
        }
    }
}
