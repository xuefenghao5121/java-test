import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Random;

/**
 * 长尾税务计算性能基准测试
 *
 * 模拟真实企业税务场景：
 * - 长尾分布：90% 交易 < 10K，8% 交易 10K-1M，2% 交易 > 1M
 * - 多档税率：6%（基础服务）、9%（建筑）、13%（商品）
 * - 复杂计算：增值税、进项税、销项税、跨期汇总
 */
public class LongTailTaxBenchmark {

    // 增值税税率档位
    private static final BigDecimal[] VAT_RATES = {
        new BigDecimal("0.06"),  // 6% - 基础服务
        new BigDecimal("0.09"),  // 9% - 建筑服务
        new BigDecimal("0.13")   // 13% - 商品销售
    };

    // 1 + 税率（用于反向计算）
    private static final BigDecimal[] ONE_PLUS_VAT = {
        new BigDecimal("1.06"),
        new BigDecimal("1.09"),
        new BigDecimal("1.13")
    };

    // 小规模纳税人征收率
    private static final BigDecimal SMALL_TAX_RATE = new BigDecimal("0.01");  // 1%

    // 交易金额范围（长尾分布）
    private static final long[] AMOUNT_RANGES = {
        100L,              // ¥1 - 微额
        1_000L,            // ¥10 - 小额
        10_000L,           // ¥100 - 中小额
        100_000L,          // ¥1,000 - 中额
        1_000_000L,        // ¥10,000 - 大额
        10_000_000L,       // ¥100,000 - 超大额
        100_000_000L,      // ¥1,000,000 - 巨额
        1_000_000_000L,    // ¥10,000,000 - 超巨额
        10_000_000_000L    // ¥100,000,000 - 极巨额（长尾）
    };

    // 长尾分布概率（对应 AMOUNT_RANGES）
    private static final double[] DISTRIBUTION = {
        0.35,   // 35% - 微额交易
        0.30,   // 30% - 小额交易
        0.20,   // 20% - 中小额交易
        0.10,   // 10% - 中额交易
        0.03,   // 3%  - 大额交易
        0.015,  // 1.5% - 超大额交易
        0.003,  // 0.3% - 巨额交易
        0.0015, // 0.15% - 超巨额交易
        0.0005  // 0.05% - 极巨额交易（长尾）
    };

    public static void main(String[] args) {
        System.out.println("=".repeat(100));
        System.out.println("长尾税务计算性能基准测试");
        System.out.println("=".repeat(100));
        System.out.println("JDK: " + System.getProperty("java.version"));
        System.out.println("长尾分布: 90% 交易 < ¥1K, 8% 交易 ¥1K-¥10K, 2% 交易 > ¥10K");
        System.out.println();

        // Warmup
        System.out.println("Warming up...");
        for (int i = 0; i < 200_000; i++) {
            warmup();
        }
        System.out.println("Warmup complete.\n");

        int iterations = 5_000_000;

        // Test 1: 单笔交易税额计算
        System.out.println("Test 1: 单笔交易税额计算 (price × taxRate)");
        System.out.println("-".repeat(100));
        runSingleTransactionBenchmark(iterations);

        // Test 2: 含税价格分解
        System.out.println("\nTest 2: 含税价格分解 (priceWithTax → priceWithoutTax + tax)");
        System.out.println("-".repeat(100));
        runPriceBreakdownBenchmark(iterations);

        // Test 3: 进项税与销项税计算
        System.out.println("\nTest 3: 进项税与销项税计算");
        System.out.println("-".repeat(100));
        runInputOutputTaxBenchmark(iterations);

        // Test 4: 跨期税额汇总
        System.out.println("\nTest 4: 跨期税额汇总 (模拟月度申报)");
        System.out.println("-".repeat(100));
        runMonthlyTaxFilingBenchmark(iterations);

        // Test 5: 多档税率混合计算
        System.out.println("\nTest 5: 多档税率混合计算 (模拟企业真实场景)");
        System.out.println("-".repeat(100));
        runMultiRateCalculationBenchmark(iterations);

        // Test 6: 小规模纳税人计算
        System.out.println("\nTest 6: 小规模纳税人征收计算 (1% 征收率)");
        System.out.println("-".repeat(100));
        runSmallTaxpayerBenchmark(iterations);

        // Test 7: 完整税务流程模拟
        System.out.println("\nTest 7: 完整税务流程模拟 (最复杂场景)");
        System.out.println("-".repeat(100));
        runFullTaxProcessBenchmark(iterations / 10);  // 更复杂，减少迭代

        // Test 8: 长尾分布分析
        System.out.println("\nTest 8: 长尾分布价值占比分析");
        System.out.println("-".repeat(100));
        analyzeLongTailDistribution();

        System.out.println("\n" + "=".repeat(100));
        System.out.println("""
测试场景说明:
1. 单笔交易: 计算单笔交易的税额
2. 价格分解: 从含税价格中分解出不含税价和税额
3. 进销项: 计算进项税额和销项税额
4. 跨期汇总: 模拟月度税务申报的汇总计算
5. 多档税率: 混合使用 6%/9%/13% 三档税率
6. 小规模: 1% 征收率的简易计算
7. 完整流程: 包含所有税务操作的复杂场景

预期优化效果:
- 大额交易(>¥1M): 50-70% 性能提升
- 中额交易(¥1K-¥1M): 30-50% 性能提升
- 小额交易(<¥1K): 20-30% 性能提升
        """);
        System.out.println("=".repeat(100));
    }

    /**
     * Warmup - 使用所有操作类型
     */
    private static void warmup() {
        BigDecimal price = new BigDecimal("10000");
        BigDecimal rate = VAT_RATES[0];
        price.multiply(rate);
        price.divide(ONE_PLUS_VAT[0], 2, RoundingMode.HALF_UP);
        price.setScale(2);
        new BigDecimal("123.45");
    }

    /**
     * Test 1: 单笔交易税额计算
     * 操作：税额 = 不含税价 × 税率
     */
    private static void runSingleTransactionBenchmark(int iterations) {
        Random rng = new Random(42);
        long start = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            // 随机选择金额范围（长尾分布）
            long baseAmount = generateLongTailAmount(rng);
            BigDecimal price = BigDecimal.valueOf(baseAmount, 2);

            // 随机选择税率
            BigDecimal taxRate = VAT_RATES[rng.nextInt(VAT_RATES.length)];

            // 计算税额
            BigDecimal tax = price.multiply(taxRate);
        }

        long end = System.nanoTime();
        printResult("单笔税额计算", iterations, start, end);
    }

    /**
     * Test 2: 含税价格分解
     * 操作：不含税价 = 含税价 ÷ (1 + 税率)，税额 = 含税价 - 不含税价
     */
    private static void runPriceBreakdownBenchmark(int iterations) {
        Random rng = new Random(42);
        long start = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            long baseAmount = generateLongTailAmount(rng);
            BigDecimal priceWithTax = BigDecimal.valueOf(baseAmount, 2);

            BigDecimal taxRate = VAT_RATES[rng.nextInt(VAT_RATES.length)];
            BigDecimal onePlusTax = ONE_PLUS_VAT[Arrays.asList(VAT_RATES).indexOf(taxRate)];

            // 分解为不含税价和税额
            BigDecimal priceWithoutTax = priceWithTax.divide(onePlusTax, 2, RoundingMode.HALF_UP);
            BigDecimal tax = priceWithTax.subtract(priceWithoutTax);
        }

        long end = System.nanoTime();
        printResult("含税价格分解", iterations, start, end);
    }

    /**
     * Test 3: 进项税与销项税计算
     * 模拟：采购商品(进项) vs 销售商品(销项)
     */
    private static void runInputOutputTaxBenchmark(int iterations) {
        Random rng = new Random(42);
        long start = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            // 采购价格（进项）
            long purchaseAmount = generateLongTailAmount(rng);
            BigDecimal purchasePrice = BigDecimal.valueOf(purchaseAmount, 2);

            // 销售价格（销项，通常比采购高 10-30%）
            long salesAmount = (long)(purchaseAmount * (1.1 + rng.nextDouble() * 0.2));
            BigDecimal salesPrice = BigDecimal.valueOf(salesAmount, 2);

            BigDecimal taxRate = VAT_RATES[rng.nextInt(VAT_RATES.length)];

            // 计算进项税额和销项税额
            BigDecimal inputTax = purchasePrice.multiply(taxRate);
            BigDecimal outputTax = salesPrice.multiply(taxRate);

            // 应纳税额 = 销项税 - 进项税
            BigDecimal taxPayable = outputTax.subtract(inputTax);
        }

        long end = System.nanoTime();
        printResult("进销项税计算", iterations, start, end);
    }

    /**
     * Test 4: 跨期税额汇总
     * 模拟月度税务申报：汇总当月所有交易的税额
     */
    private static void runMonthlyTaxFilingBenchmark(int iterations) {
        Random rng = new Random(42);
        int transactionsPerMonth = 1000;

        long start = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            BigDecimal totalSales = BigDecimal.ZERO;
            BigDecimal totalOutputTax = BigDecimal.ZERO;
            BigDecimal totalInputTax = BigDecimal.ZERO;

            // 汇总当月所有交易
            for (int j = 0; j < transactionsPerMonth; j++) {
                long amount = generateLongTailAmount(rng);
                BigDecimal price = BigDecimal.valueOf(amount, 2);

                int rateIdx = rng.nextInt(VAT_RATES.length);
                BigDecimal taxRate = VAT_RATES[rateIdx];

                // 累计销售额
                totalSales = totalSales.add(price);

                // 累计税额
                BigDecimal tax = price.multiply(taxRate);

                // 70% 销项（销售），30% 进项（采购）
                if (rng.nextDouble() < 0.7) {
                    totalOutputTax = totalOutputTax.add(tax);
                } else {
                    totalInputTax = totalInputTax.add(tax);
                }
            }

            // 计算当月应纳税额
            BigDecimal monthlyTaxPayable = totalOutputTax.subtract(totalInputTax);
        }

        long end = System.nanoTime();
        printResult("月度税额汇总(1000笔/月)", iterations, start, end);
    }

    /**
     * Test 5: 多档税率混合计算
     * 模拟企业同时经营多种业务
     */
    private static void runMultiRateCalculationBenchmark(int iterations) {
        Random rng = new Random(42);
        long start = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            // 按税率档位分别汇总
            BigDecimal sales6Rate = BigDecimal.ZERO;   // 6% 销售额
            BigDecimal sales9Rate = BigDecimal.ZERO;   // 9% 销售额
            BigDecimal sales13Rate = BigDecimal.ZERO;  // 13% 销售额

            BigDecimal tax6Rate = BigDecimal.ZERO;
            BigDecimal tax9Rate = BigDecimal.ZERO;
            BigDecimal tax13Rate = BigDecimal.ZERO;

            // 处理 100 笔交易
            for (int j = 0; j < 100; j++) {
                long amount = generateLongTailAmount(rng);
                BigDecimal price = BigDecimal.valueOf(amount, 2);

                int rateIdx = rng.nextInt(3);
                BigDecimal taxRate = VAT_RATES[rateIdx];
                BigDecimal tax = price.multiply(taxRate);

                switch (rateIdx) {
                    case 0 -> { sales6Rate = sales6Rate.add(price); tax6Rate = tax6Rate.add(tax); }
                    case 1 -> { sales9Rate = sales9Rate.add(price); tax9Rate = tax9Rate.add(tax); }
                    case 2 -> { sales13Rate = sales13Rate.add(price); tax13Rate = tax13Rate.add(tax); }
                }
            }

            // 汇总总税额
            BigDecimal totalTax = tax6Rate.add(tax9Rate).add(tax13Rate);
        }

        long end = System.nanoTime();
        printResult("多档税率混合(100笔)", iterations, start, end);
    }

    /**
     * Test 6: 小规模纳税人计算
     * 1% 征收率，计算更简单但量可能很大
     */
    private static void runSmallTaxpayerBenchmark(int iterations) {
        Random rng = new Random(42);
        long start = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            // 小规模纳税人交易金额通常较小
            long baseAmount = 100 + rng.nextInt(9900);  // ¥1 - ¥100
            BigDecimal price = BigDecimal.valueOf(baseAmount, 2);

            // 1% 征收率
            BigDecimal tax = price.multiply(SMALL_TAX_RATE);
        }

        long end = System.nanoTime();
        printResult("小规模纳税人(1%征收)", iterations, start, end);
    }

    /**
     * Test 7: 完整税务流程模拟
     * 包含：字符串解析 → 税额计算 → 汇总 → 申报格式化
     */
    private static void runFullTaxProcessBenchmark(int iterations) {
        Random rng = new Random(42);
        long start = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            // 模拟从外部系统读取价格（字符串）
            String priceStr = String.format("%.2f", rng.nextDouble() * 100000);
            BigDecimal price = new BigDecimal(priceStr);

            // 选择税率
            int rateIdx = rng.nextInt(3);
            BigDecimal taxRate = VAT_RATES[rateIdx];
            BigDecimal onePlusTax = ONE_PLUS_VAT[rateIdx];

            // 计算含税价
            BigDecimal priceWithTax = price.multiply(onePlusTax).setScale(2, RoundingMode.HALF_UP);

            // 计算税额
            BigDecimal tax = priceWithTax.subtract(price);

            // 汇总到申报表（模拟）
            BigDecimal totalTax = tax.setScale(2, RoundingMode.HALF_UP);

            // 格式化为申报字符串
            String taxStr = totalTax.toPlainString();
        }

        long end = System.nanoTime();
        printResult("完整税务流程", iterations, start, end);
    }

    /**
     * Test 8: 长尾分布分析
     * 分析不同金额段的交易数量和价值占比
     */
    private static void analyzeLongTailDistribution() {
        System.out.println("\n长尾分布分析（100万笔交易模拟）：");
        System.out.println("-".repeat(100));

        int[] countByRange = new int[AMOUNT_RANGES.length];
        long[] valueByRange = new long[AMOUNT_RANGES.length];

        Random rng = new Random(42);
        int totalTransactions = 1_000_000;

        for (int i = 0; i < totalTransactions; i++) {
            int rangeIdx = selectRangeByDistribution(rng);
            countByRange[rangeIdx]++;
            // 生成该范围内的随机金额
            long amount = generateAmountInRange(rangeIdx, rng);
            valueByRange[rangeIdx] += amount;
        }

        System.out.printf("%-15s %12s %12s %12s %12s%n",
                "金额范围", "交易笔数", "数量占比", "金额占比", "平均金额");
        System.out.println("-".repeat(100));

        long totalValue = Arrays.stream(valueByRange).sum();

        for (int i = 0; i < AMOUNT_RANGES.length; i++) {
            double countPercent = (double) countByRange[i] / totalTransactions * 100;
            double valuePercent = (double) valueByRange[i] / totalValue * 100;
            long avgAmount = countByRange[i] > 0 ? valueByRange[i] / countByRange[i] : 0;

            String rangeLabel = formatRangeLabel(i);
            System.out.printf("%-15s %,12d %11.2f%% %11.2f%% %,12d%n",
                    rangeLabel, countByRange[i], countPercent, valuePercent, avgAmount);
        }

        System.out.println("-".repeat(100));
        System.out.printf("%-15s %,12d %11.2f%% %11.2f%%%n",
                "总计", totalTransactions, 100.0, 100.0);
    }

    /**
     * 根据长尾分布生成金额
     */
    private static long generateLongTailAmount(Random rng) {
        int rangeIdx = selectRangeByDistribution(rng);
        return generateAmountInRange(rangeIdx, rng);
    }

    /**
     * 按分布概率选择金额范围
     */
    private static int selectRangeByDistribution(Random rng) {
        double r = rng.nextDouble();
        double cumulative = 0;

        for (int i = 0; i < DISTRIBUTION.length; i++) {
            cumulative += DISTRIBUTION[i];
            if (r <= cumulative) {
                return i;
            }
        }
        return DISTRIBUTION.length - 1;
    }

    /**
     * 在指定范围内生成金额
     */
    private static long generateAmountInRange(int rangeIdx, Random rng) {
        long minAmount = (rangeIdx == 0) ? 100 : AMOUNT_RANGES[rangeIdx - 1];
        long maxAmount = AMOUNT_RANGES[rangeIdx];

        // 在范围内随机生成
        return minAmount + (long)(rng.nextDouble() * (maxAmount - minAmount));
    }

    /**
     * 格式化金额范围标签
     */
    private static String formatRangeLabel(int idx) {
        if (idx == 0) {
            return "< ¥10";
        }
        long min = AMOUNT_RANGES[idx - 1] / 100;
        long max = AMOUNT_RANGES[idx] / 100;
        if (max >= 10000) {
            return String.format("¥%dK-¥%dM", min / 1000, max / 10000);
        } else if (max >= 1000) {
            return String.format("¥%d-¥%dK", min, max / 1000);
        } else {
            return String.format("¥%d-¥%d", min, max);
        }
    }

    /**
     * 打印测试结果
     */
    private static void printResult(String name, int iterations, long start, long end) {
        double elapsedMs = (end - start) / 1_000_000.0;
        double opsPerMs = iterations / elapsedMs;
        double nsPerOp = (end - start) / (double) iterations;

        System.out.printf("%-35s: %,10d ops in %8.2f ms | %,12.0f ops/ms | %8.2f ns/op%n",
                name, iterations, elapsedMs, opsPerMs, nsPerOp);
    }
}
