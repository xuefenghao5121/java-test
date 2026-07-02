import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Tax Rate Calculation Benchmark
 *
 * Tests performance of tax calculations with optimized BigDecimal operations.
 * Scenarios:
 * 1. Small amount × tax rate
 * 2. Large amount × tax rate
 * 3. Price ÷ (1 + taxRate)
 */
public class TaxRateBenchmark {

    private static final int WARMUP_ITERATIONS = 10000;
    private static final int TEST_ITERATIONS = 10000000;

    public static void main(String[] args) {
        System.out.println("BigDecimal Tax Rate Calculation Benchmark");
        System.out.println("==========================================");

        // Common tax rates
        BigDecimal taxRate8 = new BigDecimal("0.08");   // 8%
        BigDecimal taxRate10 = new BigDecimal("0.10");  // 10%
        BigDecimal taxRate20 = new BigDecimal("0.20");  // 20%
        BigDecimal onePlusTax8 = new BigDecimal("1.08");
        BigDecimal onePlusTax10 = new BigDecimal("1.10");
        BigDecimal onePlusTax20 = new BigDecimal("1.20");

        // Small amounts (< $10K)
        BigDecimal amount10 = new BigDecimal("10.00");
        BigDecimal amount100 = new BigDecimal("100.00");
        BigDecimal amount1000 = new BigDecimal("1000.00");
        BigDecimal amount10000 = new BigDecimal("10000.00");

        // Large amounts ($10K - $100M) - optimized fast path
        BigDecimal amount100k = new BigDecimal("100000.00");
        BigDecimal amount1M = new BigDecimal("1000000.00");
        BigDecimal amount10M = new BigDecimal("10000000.00");
        BigDecimal amount100M = new BigDecimal("100000000.00");

        System.out.println("\n--- Multiplication: Small Amounts × Tax Rate ---");
        benchmarkMultiply("10 × 8%", amount10, taxRate8);
        benchmarkMultiply("100 × 10%", amount100, taxRate10);
        benchmarkMultiply("1K × 20%", amount1000, taxRate20);
        benchmarkMultiply("10K × 8%", amount10000, taxRate8);

        System.out.println("\n--- Multiplication: Large Amounts × Tax Rate (Fast Path) ---");
        benchmarkMultiply("100K × 10%", amount100k, taxRate10);
        benchmarkMultiply("1M × 8%", amount1M, taxRate8);
        benchmarkMultiply("10M × 20%", amount10M, taxRate20);
        benchmarkMultiply("100M × 10%", amount100M, taxRate10);

        System.out.println("\n--- Division: Price ÷ (1 + Tax Rate) ---");
        benchmarkDivide("10 ÷ 1.08", amount10, onePlusTax8);
        benchmarkDivide("100 ÷ 1.10", amount100, onePlusTax10);
        benchmarkDivide("1K ÷ 1.20", amount1000, onePlusTax20);
        benchmarkDivide("10K ÷ 1.08", amount10000, onePlusTax8);
        benchmarkDivide("100K ÷ 1.10", amount100k, onePlusTax10);
        benchmarkDivide("1M ÷ 1.08", amount1M, onePlusTax8);
        benchmarkDivide("10M ÷ 1.20", amount10M, onePlusTax20);
        benchmarkDivide("100M ÷ 1.10", amount100M, onePlusTax10);
    }

    private static void benchmarkMultiply(String label, BigDecimal a, BigDecimal b) {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            a.multiply(b);
        }

        // Test
        long start = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            a.multiply(b);
        }
        long end = System.nanoTime();

        double avgTime = (end - start) / (double) TEST_ITERATIONS;
        System.out.printf("%s: %.2f ns/op%n", label, avgTime);
    }

    private static void benchmarkDivide(String label, BigDecimal a, BigDecimal b) {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            a.divide(b, 2, RoundingMode.HALF_UP);
        }

        // Test
        long start = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            a.divide(b, 2, RoundingMode.HALF_UP);
        }
        long end = System.nanoTime();

        double avgTime = (end - start) / (double) TEST_ITERATIONS;
        System.out.printf("%s: %.2f ns/op%n", label, avgTime);
    }
}
