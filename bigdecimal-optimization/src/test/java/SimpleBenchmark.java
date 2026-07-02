import java.math.BigDecimal;

/**
 * Simple Multiplication Benchmark
 *
 * Tests basic multiplication performance across different value ranges.
 */
public class SimpleBenchmark {

    private static final int WARMUP_ITERATIONS = 10000;
    private static final int TEST_ITERATIONS = 10000000;

    public static void main(String[] args) {
        System.out.println("BigDecimal Simple Multiplication Benchmark");
        System.out.println("===========================================");

        // Test pairs with different magnitudes
        benchmarkMultiply("Small × Small (10 × 10)", "10", "10");
        benchmarkMultiply("Small × Small (1K × 1K)", "1000", "1000");
        benchmarkMultiply("Small × Small (1M × 1M)", "1000000", "1000000");

        benchmarkMultiply("Large × Small (1B × 10)", "1000000000", "10");
        benchmarkMultiply("Large × Small (10B × 100)", "10000000000", "100");

        benchmarkMultiply("Large × Large (10B × 10B)", "10000000000", "10000000000");
    }

    private static void benchmarkMultiply(String label, String a, String b) {
        BigDecimal x = new BigDecimal(a);
        BigDecimal y = new BigDecimal(b);

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            x.multiply(y);
        }

        // Test
        long start = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            x.multiply(y);
        }
        long end = System.nanoTime();

        double avgTime = (end - start) / (double) TEST_ITERATIONS;
        System.out.printf("%s: %.2f ns/op%n", label, avgTime);
    }
}
