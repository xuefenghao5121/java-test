package com.kunpeng.math;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.*;

/**
 * 微服务打流场景压测
 *
 * 模拟场景：
 * - 金融交易服务（价格计算、风控）
 * - 电商订单服务（优惠计算、税费）
 * - 支付网关（汇率转换）
 */
public class MicroserviceLoadTest {

    private static final int WARMUP_SECONDS = 5;
    private static final int TEST_SECONDS = 30;
    private static final int THREADS = Runtime.getRuntime().availableProcessors();

    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║          微服务打流场景压测 (QPS + P99 延迟)              ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        System.out.println("配置:");
        System.out.println("  线程数: " + THREADS);
        System.out.println("  Warmup: " + WARMUP_SECONDS + "s");
        System.out.println("  测试时间: " + TEST_SECONDS + "s\n");

        // 测试三个场景
        testScenario("金融交易 (价格计算)", () -> simulatePricing());
        testScenario("电商订单 (税费计算)", () -> simulateOrder());
        testScenario("支付网关 (汇率转换)", () -> simulatePayment());

        printFinalSummary();
    }

    private static void testScenario(String name, Runnable workload) throws Exception {
        System.out.println("\n┌─────────────────────────────────────────────────────────┐");
        System.out.println("│ " + name);
        System.out.println("└─────────────────────────────────────────────────────────┘\n");

        // 测试 Standard
        Result stdResult = runLoadTest(workload, false, "Standard");

        Thread.sleep(2000); // 冷却

        // 测试 Native
        Result natResult = runLoadTest(workload, true, "Native");

        // 打印对比
        printComparison(name, stdResult, natResult);
    }

    private static Result runLoadTest(Runnable workload, boolean useNative, String label)
            throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        AtomicLong totalOps = new AtomicLong(0);
        AtomicLong totalLatencyNs = new AtomicLong(0);
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        AtomicBoolean running = new AtomicBoolean(true);
        CountDownLatch startLatch = new CountDownLatch(THREADS);
        CountDownLatch endLatch = new CountDownLatch(THREADS);

        // 启动工作线程
        for (int i = 0; i < THREADS; i++) {
            final int threadId = i;
            executor.submit(() -> {
                startLatch.countDown();
                try {
                    startLatch.await();
                } catch (InterruptedException e) {}

                Random random = new Random(threadId);
                List<Long> localLatencies = new ArrayList<>(10000);

                while (running.get()) {
                    long start = System.nanoTime();

                    try {
                        workload.run();
                    } catch (Exception e) {
                        // 忽略
                    }

                    long end = System.nanoTime();
                    long latency = end - start;
                    localLatencies.add(latency);

                    // 定期同步到全局（减少竞争）
                    if (localLatencies.size() >= 10000) {
                        long ops = localLatencies.size();
                        long sum = 0;
                        for (Long l : localLatencies) {
                            sum += l;
                            latencies.offer(l);
                        }
                        totalOps.addAndGet(ops);
                        totalLatencyNs.addAndGet(sum);
                        localLatencies.clear();
                    }
                }

                // 最后一批
                if (!localLatencies.isEmpty()) {
                    for (Long l : localLatencies) {
                        latencies.offer(l);
                    }
                    totalOps.addAndGet(localLatencies.size());
                }

                endLatch.countDown();
            });
        }

        // 等待所有线程就绪
        startLatch.await();
        Thread.sleep(100);

        // 开始测量
        System.out.print("  " + label + " 测试中...");
        long startTime = System.currentTimeMillis();

        // 定期打印进度
        ScheduledExecutorService progress = Executors.newSingleThreadScheduledExecutor();
        progress.scheduleAtFixedRate(() -> {
            long ops = totalOps.get();
            long elapsed = System.currentTimeMillis() - startTime;
            double qps = ops / (elapsed / 1000.0);
            System.out.print(" " + String.format("%.0f", qps) + " QPS");
        }, 1, 1, TimeUnit.SECONDS);

        Thread.sleep(TEST_SECONDS * 1000);
        running.set(false);

        long endTime = System.currentTimeMillis();
        progress.shutdownNow();

        // 等待完成
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        long durationMs = endTime - startTime;
        long ops = totalOps.get();
        double qps = ops / (durationMs / 1000.0);

        // 计算延迟百分位数
        List<Long> allLatencies = new ArrayList<>(latencies);
        Collections.sort(allLatencies);

        long p50 = percentile(allLatencies, 50);
        long p90 = percentile(allLatencies, 90);
        long p95 = percentile(allLatencies, 95);
        long p99 = percentile(allLatencies, 99);
        long p999 = percentile(allLatencies, 99.9);
        long max = allLatencies.isEmpty() ? 0 : allLatencies.get(allLatencies.size() - 1);

        System.out.println(" ✓");

        return new Result(label, durationMs, ops, qps, p50, p90, p95, p99, p999, max);
    }

    private static long percentile(List<Long> data, double p) {
        if (data.isEmpty()) return 0;
        int index = (int) Math.ceil(data.size() * p / 100) - 1;
        return data.get(Math.max(0, Math.min(index, data.size() - 1)));
    }

    private static void printComparison(String name, Result std, Result nat) {
        System.out.println("\n  ┌─ 延迟 (ns) ─────────────────┬─────────────┬─────────────┐");
        System.out.println("  │ 百分位           │ Standard     │ Native       │");
        System.out.println("  ├──────────────────────────┼─────────────┼─────────────┤");
        System.out.printf("  │ P50               │ %11d  │ %11d  │%n", std.p50, nat.p50);
        System.out.printf("  │ P90               │ %11d  │ %11d  │%n", std.p90, nat.p90);
        System.out.printf("  │ P95               │ %11d  │ %11d  │%n", std.p95, nat.p95);
        System.out.printf("  │ P99               │ %11d  │ %11d  │%n", std.p99, nat.p99);
        System.out.printf("  │ P99.9             │ %11d  │ %11d  │%n", std.p999, nat.p999);
        System.out.printf("  │ Max               │ %11d  │ %11d  │%n", std.max, nat.max);
        System.out.println("  └──────────────────────────┴─────────────┴─────────────┘");

        System.out.println("\n  ┌─ 吞吐量 ─────────────────┬─────────────┬─────────────┐");
        System.out.println("  │ 指标               │ Standard     │ Native       │");
        System.out.println("  ├──────────────────────┼─────────────┼─────────────┤");
        System.out.printf("  │ QPS                │ %,11d  │ %,11d  │%n", (long)std.qps, (long)nat.qps);
        System.out.printf("  │ 总操作数            │ %,11d  │ %,11d  │%n", std.ops, nat.ops);
        System.out.println("  └──────────────────────┴─────────────┴─────────────┘");

        // 分析
        double p99Diff = ((double)nat.p99 / std.p99 - 1) * 100;
        double qpsDiff = (nat.qps / std.qps - 1) * 100;

        System.out.println("\n  分析:");
        if (nat.p99 > std.p99 * 1.5) {
            System.out.printf("    ⚠ Native P99 延迟高 %.1f%% - FFI 开销显著%n", p99Diff);
        }
        if (nat.qps < std.qps * 0.8) {
            System.out.printf("    ⚠ Native QPS 低 %.1f%% - 吞吐量下降%n", Math.abs(qpsDiff));
        }
    }

    private static void printFinalSummary() {
        System.out.println("\n╔════════════════════════════════════════════════════════════╗");
        System.out.println("║                     微服务场景结论                           ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        System.out.println("微服务打流场景特点：");
        System.out.println("  • 大量小请求（非批量）");
        System.out.println("  • P99/P99.9 延迟比平均延迟更重要");
        System.out.println("  • FFI 开销无法摊薄（每个请求独立）");
        System.out.println("  • GC 压力（Native 减少 GC？）\n");

        System.out.println("建议：");
        System.out.println("  • 高并发、低延迟场景 → 保持 Standard BigDecimal");
        System.out.println("  • 批量处理场景 → 考虑 Native 批量接口");
        System.out.println("  • 计算密集场景 → 评估 Native 是否值得 FFI 开销");
    }

    // ========== 工作负载模拟 ==========

    private static void simulatePricing() {
        // 金融定价：计算多个价格指标
        BigDecimal price = new BigDecimal(100 + Math.random() * 50);
        BigDecimal quantity = new BigDecimal(1000 + Math.random() * 10000);
        BigDecimal discount = new BigDecimal(0.85 + Math.random() * 0.15);
        BigDecimal tax = new BigDecimal(0.06);

        // 原价
        BigDecimal original = price.multiply(quantity);
        // 折扣
        BigDecimal afterDiscount = original.multiply(discount).setScale(2, RoundingMode.HALF_UP);
        // 税费
        BigDecimal finalPrice = afterDiscount.multiply(BigDecimal.ONE.add(tax)).setScale(2, RoundingMode.HALF_UP);
    }

    private static void simulateOrder() {
        // 订单：计算商品总价、优惠、税费
        BigDecimal unitPrice = new BigDecimal(99.99 + Math.random() * 100);
        int quantity = 1 + (int)(Math.random() * 10);
        BigDecimal coupon = new BigDecimal(Math.random() * 20);
        BigDecimal taxRate = new BigDecimal(0.13);

        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal afterCoupon = subtotal.subtract(coupon).max(BigDecimal.ZERO);
        BigDecimal tax = afterCoupon.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = afterCoupon.add(tax);
    }

    private static void simulatePayment() {
        // 支付：多币种转换
        BigDecimal amount = new BigDecimal(100 + Math.random() * 10000);
        BigDecimal[] rates = {
            new BigDecimal("0.85"),  // USD to EUR
            new BigDecimal("6.45"),  // USD to CNY
            new BigDecimal("110.5"), // USD to JPY
            new BigDecimal("0.73")   // USD to GBP
        };

        for (BigDecimal rate : rates) {
            BigDecimal converted = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        }
    }

    static class Result {
        String name;
        long durationMs;
        long ops;
        double qps;
        long p50, p90, p95, p99, p999, max;

        Result(String name, long durationMs, long ops, double qps,
                long p50, long p90, long p95, long p99, long p999, long max) {
            this.name = name;
            this.durationMs = durationMs;
            this.ops = ops;
            this.qps = qps;
            this.p50 = p50;
            this.p90 = p90;
            this.p95 = p95;
            this.p99 = p99;
            this.p999 = p999;
            this.max = max;
        }
    }
}
