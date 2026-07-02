import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Division Benchmark
 *
 * Tests division performance with different dividend/divisor combinations
 * and scale differences.
 */
public class DivideBenchmark {

    private static final int WARMUP_ITERATIONS = 10000;
    private static final int TEST_ITERATIONS = 1000000;

    public static void main(String[] args) {
        System.out.println("BigDecimal Division Benchmark");
        System.out.println("=============================");

        System.out.println("\n--- Same Scale Division ---");
        benchmarkDivide("1000 ÷ 100 (scale 0)", "1000", "100", 0, 0);
        benchmarkDivide("10000 ÷ 10 (scale 0)", "10000", "10", 0, 0);
        benchmarkDivide("100.00 ÷ 10.00 (scale 2)", "100.00", "10.00", 2, 2);

        System.out.println("\n--- Division with Small Scale Difference ---");
        benchmarkDivide("100.0000 ÷ 10.00 (diff 2)", "100.0000", "10.00", 4, 2);
        benchmarkDivide("100.00000 ÷ 10.0 (diff 3)", "100.00000", "10.0", 5, 1);
        benchmarkDivide("100.000000 ÷ 10 (diff 4)", "100.000000", "10", 6, 0);

        System.out.println("\n--- Percentage Division ---");
        benchmarkDivide("1000 ÷ 100", "1000", "100", 0, 0);
        benchmarkDivide("10000 ÷ 1000", "10000", "1000", 0, 0);
        benchmarkDivide("100000 ÷ 10000", "100000", "10000", 0, 0);

        System.out.println("\n--- Large Value Division ---");
        benchmarkDivide("1M ÷ 100", "1000000", "100", 0, 0);
        benchmarkDivide("10M ÷ 1000", "10000000", "1000", 0, 0);
        benchmarkDivide("100M ÷ 10000", "100000000", "10000", 0, 0);
    }

    private static void benchmarkDivide(String label, String dividendStr, String divisorStr,
                                         int dividendScale, int divisorScale) {
        BigDecimal dividend = new BigDecimal(dividendStr);
        BigDecimal divisor = new BigDecimal(divisorStr);

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            dividend.divide(divisor, 2, RoundingMode.HALF_UP);
        }

        // Test
        long start = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            dividend.divide(divisor, 2, RoundingMode.HALF_UP);
        }
        long end = System.nanoTime();

        double avgTime = (end - start) / (double) TEST_ITERATIONS;
        System.out.printf("%s: %.2f ns/op (scale diff: %d)%n",
                label, avgTime, Math.abs(dividendScale - divisorScale));
    }
}
