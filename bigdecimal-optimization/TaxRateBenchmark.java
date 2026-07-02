import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Tax Rate Calculation Performance Benchmark
 *
 * 测试税率计算场景（含长尾数）的性能：
 * - price × taxRate
 * - price ÷ (1 + taxRate)
 * - price × (1 + taxRate)
 */
public class TaxRateBenchmark {

    // 典型税率
    private static final BigDecimal[] TAX_RATES = {
        new BigDecimal("0.06"),   // 6%
        new BigDecimal("0.13"),   // 13%
        new BigDecimal("0.25"),   // 25%
        new BigDecimal("0.35")    // 35%
    };

    // 1 + 税率（用于反向计算）
    private static final BigDecimal[] ONE_PLUS_TAX_RATES = {
        new BigDecimal("1.06"),
        new BigDecimal("1.13"),
        new BigDecimal("1.25"),
        new BigDecimal("1.35")
    };

    // 金额范围（长尾分布）
    private static final long[] AMOUNTS = {
        100L,             // $1.00 - 小额
        10_000L,          // $100.00 - 中小额
        1_000_000L,       // $10,000.00 - 中额
        100_000_000L,     // $1,000,000.00 - 大额
        10_000_000_000L   // $100,000,000.00 - 超大额（长尾）
    };

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("Tax Rate Calculation Performance Benchmark");
        System.out.println("Testing with long-tail distribution (small to very large amounts)");
        System.out.println("=".repeat(80));
        System.out.println("JDK: " + System.getProperty("java.version"));
        System.out.println();

        // Warmup
        System.out.println("Warming up...");
        for (int i = 0; i < 200_000; i++) {
            warmup();
        }
        System.out.println("Warmup complete.\n");

        int iterations = 5_000_000;

        // Test 1: 计算税额 price × taxRate
        System.out.println("Test 1: Calculate tax amount (price × taxRate)");
        System.out.println("-".repeat(80));
        runTaxMultiplyBenchmark("Small amount ($100)", 10_000L, "0.13", iterations);
        runTaxMultiplyBenchmark("Medium amount ($10K)", 1_000_000L, "0.13", iterations);
        runTaxMultiplyBenchmark("Large amount ($1M)", 100_000_000L, "0.13", iterations);
        runTaxMultiplyBenchmark("X-Large amount ($100M)", 10_000_000_000L, "0.13", iterations / 2);

        // Test 2: 反向计算 price ÷ (1 + taxRate)
        System.out.println("\nTest 2: Reverse calculation (price ÷ (1 + taxRate))");
        System.out.println("-".repeat(80));
        runTaxDivideBenchmark("Small amount ($100)", 10_000L, "1.13", iterations);
        runTaxDivideBenchmark("Medium amount ($10K)", 1_000_000L, "1.13", iterations);
        runTaxDivideBenchmark("Large amount ($1M)", 100_000_000L, "1.13", iterations);
        runTaxDivideBenchmark("X-Large amount ($100M)", 10_000_000_000L, "1.13", iterations / 2);

        // Test 3: 含税价格 price × (1 + taxRate)
        System.out.println("\nTest 3: Calculate price with tax (price × (1 + taxRate))");
        System.out.println("-".repeat(80));
        runTotalPriceBenchmark("Small amount ($100)", 10_000L, "1.13", iterations);
        runTotalPriceBenchmark("Medium amount ($10K)", 1_000_000L, "1.13", iterations);
        runTotalPriceBenchmark("Large amount ($1M)", 100_000_000L, "1.13", iterations);
        runTotalPriceBenchmark("X-Large amount ($100M)", 10_000_000_000L, "1.13", iterations / 2);

        // Test 4: 混合场景（模拟真实交易）
        System.out.println("\nTest 4: Mixed trading scenario (real-world simulation)");
        System.out.println("-".repeat(80));
        runTradingSimulation("Small trades (<$1K)", iterations);
        runTradingSimulation("Medium trades ($1K-$100K)", iterations);
        runTradingSimulation("Large trades (>$100K)", iterations / 2);

        // Test 5: 快速路径覆盖分析
        System.out.println("\nTest 5: Fast path coverage analysis");
        System.out.println("-".repeat(80));
        analyzeFastPathCoverage();

        System.out.println("\n" + "=".repeat(80));
        System.out.println("""
Summary:
- 优化后快速路径应覆盖 100M 以内的金额
- 长尾数场景：虽然大额交易占比小，但价值高
- 预期改进：大额交易性能提升 50-70%
        """);
        System.out.println("=".repeat(80));
    }

    private static void warmup() {
        BigDecimal price = BigDecimal.valueOf(100000L, 2);
        BigDecimal taxRate = new BigDecimal("0.13");
        price.multiply(taxRate);
        price.divide(new BigDecimal("1.13"), 2, RoundingMode.HALF_UP);
    }

    private static void runTaxMultiplyBenchmark(String name, long amount, String taxRate, int iterations) {
        BigDecimal rate = new BigDecimal(taxRate);
        long start = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            BigDecimal price = BigDecimal.valueOf(amount, 2);
            price.multiply(rate);
        }

        long end = System.nanoTime();
        printResult(name, iterations, start, end);
    }

    private static void runTaxDivideBenchmark(String name, long amount, String divisor, int iterations) {
        BigDecimal div = new BigDecimal(divisor);
        long start = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            BigDecimal price = BigDecimal.valueOf(amount, 2);
            price.divide(div, 2, RoundingMode.HALF_UP);
        }

        long end = System.nanoTime();
        printResult(name, iterations, start, end);
    }

    private static void runTotalPriceBenchmark(String name, long amount, String multiplier, int iterations) {
        BigDecimal mult = new BigDecimal(multiplier);
        long start = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            BigDecimal price = BigDecimal.valueOf(amount, 2);
            price.multiply(mult);
        }

        long end = System.nanoTime();
        printResult(name, iterations, start, end);
    }

    private static void runTradingSimulation(String name, int iterations) {
        java.util.Random rng = new java.util.Random(42);
        long start = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            // 模拟：计算含税价格
            long baseAmount;
            if (name.contains("Small")) {
                baseAmount = 100 + rng.nextInt(900); // $1 - $10
            } else if (name.contains("Medium")) {
                baseAmount = 1_000 + rng.nextInt(99_000); // $10 - $1,000
            } else {
                baseAmount = 10_000 + rng.nextInt(9_990_000); // $100 - $100,000
            }

            BigDecimal price = BigDecimal.valueOf(baseAmount, 2);
            BigDecimal taxRate = TAX_RATES[i % TAX_RATES.length];
            BigDecimal tax = price.multiply(taxRate);
            BigDecimal total = price.add(tax);
        }

        long end = System.nanoTime();
        printResult(name, iterations, start, end);
    }

    private static void analyzeFastPathCoverage() {
        System.out.println("\n当前快速路径条件:");
        System.out.println("  isSmallMultiply(x, y):");
        System.out.println("    - 两数都 < 10^9: ✓");
        System.out.println("    - 一数 < 1K 且另一数 < 10^13: ✓ (新增，支持大额×税率)");
        System.out.println();

        for (long amount : AMOUNTS) {
            long amountCompact = amount; // scale=2
            long rateCompact = 13; // 0.13

            boolean oldFastPath = Math.abs(amountCompact) < 1_000_000_000L
                    && Math.abs(rateCompact) < 1_000_000_000L;
            boolean newFastPath = (Math.abs(amountCompact) < 1_000_000_000L
                    && Math.abs(rateCompact) < 1_000_000_000L)
                    || (Math.abs(amountCompact) < 1_000L
                    && Math.abs(rateCompact) < 10_000_000_000_000L)
                    || (Math.abs(rateCompact) < 1_000L
                    && Math.abs(amountCompact) < 10_000_000_000_000L);

            System.out.printf("$%,15.2f × 0.13: oldFastPath=%s, newFastPath=%s%n",
                    amount / 100.0, oldFastPath ? "✓" : "✗", newFastPath ? "✓" : "✗");
        }

        System.out.println("\n覆盖提升:");
        System.out.println("  优化前: 覆盖到 $10M");
        System.out.println("  优化后: 覆盖到 $100M (10倍提升)");
    }

    private static void printResult(String name, int iterations, long start, long end) {
        long elapsedMs = (end - start) / 1_000_000;
        double opsPerMs = iterations / (double) elapsedMs;
        double nsPerOp = (end - start) / (double) iterations;

        System.out.printf("%-35s: %,8d ops in %,5d ms | %,10.0f ops/ms | %6.2f ns/op%n",
                name, iterations, elapsedMs, opsPerMs, nsPerOp);
    }
}
