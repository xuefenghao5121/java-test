import java.math.BigDecimal;

/**
 * BigDecimal Divide Performance Benchmark
 *
 * Target: Financial scenarios with small divisors (2-5 digits)
 * Focus: Division by fixed small values
 */
public class DivideBenchmark {

    // Common divisors in financial applications
    private static final int[] SMALL_DIVISORS = {
        2, 3, 4, 5, 10, 100, 1000,      // Very common
        25, 50, 125, 250,               // Commission rates
        7, 14, 28,                      // Tax rates (VAT etc)
        12, 24, 36, 52, 365              // Time-based divisions
    };

    // Dividend values (typical financial amounts)
    private static final long[] DIVIDENDS = {
        1_000L,          // $10.00
        10_000L,         // $100.00
        100_000L,        // $1,000.00
        1_000_000L,      // $10,000.00
        10_000_000L,     // $100,000.00
        100_000_000L,    // $1,000,000.00
        1_000_000_000L,  // $10,000,000.00
        10_000_000_000L  // $100,000,000.00
    };

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("BigDecimal Divide Performance Benchmark");
        System.out.println("Target: Small divisors (2-5 digits) for financial applications");
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

        // Test 1: Common divisors
        System.out.println("Test 1: Division by common financial divisors");
        System.out.println("-".repeat(80));
        runBenchmark("Divide by 100 (percentage)", 100L, iterations);
        runBenchmark("Divide by 1000 (per-thousand)", 1000L, iterations);
        runBenchmark("Divide by 25 (4% commission)", 25L, iterations);
        runBenchmark("Divide by 12 (months)", 12L, iterations);

        // Test 2: With scale
        System.out.println("\nTest 2: Division with scale (2 decimal places)");
        System.out.println("-".repeat(80));
        runBenchmarkWithScale("Divide by 100, scale=2", 100L, 2, iterations);
        runBenchmarkWithScale("Divide by 1000, scale=2", 1000L, 2, iterations);

        // Test 3: Pre-allocated divisors
        System.out.println("\nTest 3: Pre-allocated divisors");
        System.out.println("-".repeat(80));
        BigDecimal[] divisors = new BigDecimal[SMALL_DIVISORS.length];
        for (int i = 0; i < SMALL_DIVISORS.length; i++) {
            divisors[i] = BigDecimal.valueOf(SMALL_DIVISORS[i]);
        }
        runBenchmarkPreallocated("Various small divisors", divisors, iterations / SMALL_DIVISORS.length);

        // Test 4: Scale adjustment scenarios
        System.out.println("\nTest 4: Division with scale adjustment");
        System.out.println("-".repeat(80));
        runBenchmarkWithBothScales("Divide scale=4 by scale=0", 100L, 4, 0, 2, iterations);
        runBenchmarkWithBothScales("Divide scale=2 by scale=2", 100L, 2, 2, 2, iterations);
        runBenchmarkWithBothScales("Divide scale=0 by scale=4", 100L, 0, 4, 2, iterations);

        // Test 5: Real-world scenarios
        System.out.println("\nTest 5: Real-world financial calculations");
        System.out.println("-".repeat(80));
        runFinancialBenchmark("Price to cents (÷100)", 100L, iterations);
        runFinancialBenchmark("Per-unit calculation (÷1000)", 1000L, iterations);

        // Test 6: Rounding modes
        System.out.println("\nTest 6: Division with different rounding modes");
        System.out.println("-".repeat(80));
        runBenchmarkWithRounding("Divide by 100, HALF_UP", 100L,
                java.math.RoundingMode.HALF_UP, iterations);
        runBenchmarkWithRounding("Divide by 100, DOWN", 100L,
                java.math.RoundingMode.DOWN, iterations);
        runBenchmarkWithRounding("Divide by 7, HALF_UP", 7L,
                java.math.RoundingMode.HALF_UP, iterations);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("""
Summary:
- Small divisors (2-5 digits) show: X.XX ns/op
- Division is typically 5-10x slower than multiplication
- Fast path optimization targets: scale adjustment elimination
- Expected improvement: 15-25% for same-scale divisions
        """);
        System.out.println("=".repeat(80));
    }

    private static void warmup() {
        BigDecimal a = BigDecimal.valueOf(12345678L);
        BigDecimal b = BigDecimal.valueOf(100L);
        a.divide(b, 2, java.math.RoundingMode.HALF_UP);
    }

    private static void runBenchmark(String name, long divisor, int iterations) {
        long start = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            int idx = i % DIVIDENDS.length;
            BigDecimal dividend = BigDecimal.valueOf(DIVIDENDS[idx]);
            BigDecimal div = BigDecimal.valueOf(divisor);
            dividend.divide(div, 2, java.math.RoundingMode.HALF_UP);
        }

        long end = System.nanoTime();
        printResult(name, iterations, start, end);
    }

    private static void runBenchmarkWithScale(String name, long divisor, int scale, int iterations) {
        long start = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            int idx = i % DIVIDENDS.length;
            BigDecimal dividend = BigDecimal.valueOf(DIVIDENDS[idx], scale);
            BigDecimal div = BigDecimal.valueOf(divisor);
            dividend.divide(div, 2, java.math.RoundingMode.HALF_UP);
        }

        long end = System.nanoTime();
        printResult(name, iterations, start, end);
    }

    private static void runBenchmarkPreallocated(String name, BigDecimal[] divisors, int iterations) {
        long start = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            int idx = i % DIVIDENDS.length;
            int divIdx = i % divisors.length;
            BigDecimal dividend = BigDecimal.valueOf(DIVIDENDS[idx]);
            dividend.divide(divisors[divIdx], 2, java.math.RoundingMode.HALF_UP);
        }

        long end = System.nanoTime();
        printResult(name, iterations, start, end);
    }

    private static void runBenchmarkWithBothScales(String name, long divisor,
            int dividendScale, int divisorScale, int resultScale, int iterations) {
        long start = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            int idx = i % DIVIDENDS.length;
            BigDecimal dividend = BigDecimal.valueOf(DIVIDENDS[idx], dividendScale);
            BigDecimal div = BigDecimal.valueOf(divisor, divisorScale);
            dividend.divide(div, resultScale, java.math.RoundingMode.HALF_UP);
        }

        long end = System.nanoTime();
        printResult(name, iterations, start, end);
    }

    private static void runFinancialBenchmark(String name, long divisor, int iterations) {
        long start = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            // Simulate converting a price to different units
            int idx = i % DIVIDENDS.length;
            long amountInCents = DIVIDENDS[idx];
            BigDecimal price = BigDecimal.valueOf(amountInCents, 2); // dollars with cents
            BigDecimal units = BigDecimal.valueOf(divisor);
            price.divide(units, 2, java.math.RoundingMode.HALF_UP);
        }

        long end = System.nanoTime();
        printResult(name, iterations, start, end);
    }

    private static void runBenchmarkWithRounding(String name, long divisor,
            java.math.RoundingMode roundingMode, int iterations) {
        long start = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            int idx = i % DIVIDENDS.length;
            BigDecimal dividend = BigDecimal.valueOf(DIVIDENDS[idx]);
            BigDecimal div = BigDecimal.valueOf(divisor);
            dividend.divide(div, 2, roundingMode);
        }

        long end = System.nanoTime();
        printResult(name, iterations, start, end);
    }

    private static void printResult(String name, int iterations, long start, long end) {
        long elapsedMs = (end - start) / 1_000_000;
        double opsPerMs = iterations / (double) elapsedMs;
        double nsPerOp = (end - start) / (double) iterations;

        System.out.printf("%-40s: %,8d ops in %,5d ms | %,10.0f ops/ms | %6.2f ns/op%n",
                name, iterations, elapsedMs, opsPerMs, nsPerOp);
    }
}
