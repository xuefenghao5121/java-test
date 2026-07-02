import java.math.BigDecimal;

/**
 * Simple BigDecimal Multiply Performance Benchmark
 *
 * This benchmark measures the performance of BigDecimal multiplication
 * for different value sizes. Run with both original and optimized JDK
 * to see the improvement.
 */
public class SimpleBenchmark {

    // Value ranges for testing
    private static final long[] TINY_VALUES = generateValues(100_000, 200_000, 1000);
    private static final long[] SMALL_VALUES = generateValues(1_000_000, 2_000_000, 1000);
    private static final long[] MEDIUM_VALUES = generateValues(10_000_000, 20_000_000, 1000);
    private static final long[] LARGE_VALUES = generateValues(100_000_000, 200_000_000, 1000);

    private static long[] generateValues(int min, int max, int count) {
        long[] values = new long[count];
        java.util.Random rng = new java.util.Random(42);
        for (int i = 0; i < count; i++) {
            values[i] = (rng.nextInt(max - min) - (max - min) / 2) * 100L;
        }
        return values;
    }

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("BigDecimal Multiply Performance Benchmark");
        System.out.println("=".repeat(80));
        System.out.println("JDK Version: " + System.getProperty("java.version"));
        System.out.println("JVM: " + System.getProperty("java.vm.name"));
        System.out.println();

        // Warmup
        System.out.println("Warming up...");
        for (int i = 0; i < 100_000; i++) {
            warmup();
        }
        System.out.println("Warmup complete.\n");

        // Run benchmarks
        int iterations = 10_000_000;

        runBenchmark("Tiny Values (±$100K, 8 digits)", TINY_VALUES, iterations);
        runBenchmark("Small Values (±$1M, 9 digits)", SMALL_VALUES, iterations);
        runBenchmark("Medium Values (±$10M, 10 digits)", MEDIUM_VALUES, iterations / 2);
        runBenchmark("Large Values (±$100M, 11 digits)", LARGE_VALUES, iterations / 4);

        // Chained operations
        runChainedBenchmark("Chained Tiny Values (5 multiplications)", TINY_VALUES, iterations / 10);
        runChainedBenchmark("Chained Small Values (5 multiplications)", SMALL_VALUES, iterations / 10);

        // With MathContext
        runBenchmarkWithMathContext("Small Values with MathContext(10)", SMALL_VALUES, iterations);
        runBenchmarkWithMathContext("Small Values with UNLIMITED", SMALL_VALUES, iterations);

        System.out.println("=".repeat(80));
    }

    private static void warmup() {
        int idx = (int) (System.nanoTime() % 1000);
        BigDecimal a = BigDecimal.valueOf(TINY_VALUES[idx]);
        BigDecimal b = BigDecimal.valueOf(TINY_VALUES[(idx + 1) % 1000]);
        a.multiply(b);
    }

    private static void runBenchmark(String name, long[] values, int iterations) {
        long start = System.nanoTime();
        long result = 0;

        for (int i = 0; i < iterations; i++) {
            int idx = (int) (i % values.length);
            int idx2 = (int) ((i + 1) % values.length);
            BigDecimal a = BigDecimal.valueOf(values[idx]);
            BigDecimal b = BigDecimal.valueOf(values[idx2]);
            BigDecimal product = a.multiply(b);
            result += product.scale(); // prevent optimization
        }

        long end = System.nanoTime();
        long elapsedMs = (end - start) / 1_000_000;
        double opsPerSec = (iterations * 1000.0) / elapsedMs;
        double nsPerOp = (end - start) / (double) iterations;

        System.out.printf("%-40s: %,12d ops in %,6d ms | %,12.0f ops/ms | %.2f ns/op%n",
                name, iterations, elapsedMs, opsPerSec, nsPerOp);
    }

    private static void runChainedBenchmark(String name, long[] values, int iterations) {
        long start = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            int idx = (int) (i % values.length);
            BigDecimal value = BigDecimal.valueOf(values[idx]);
            for (int j = 0; j < 5; j++) {
                int idx2 = (int) ((i + j) % values.length);
                value = value.multiply(BigDecimal.valueOf(values[idx2]));
            }
        }

        long end = System.nanoTime();
        long elapsedMs = (end - start) / 1_000_000;
        double opsPerSec = (iterations * 5 * 1000.0) / elapsedMs;
        double nsPerOp = (end - start) / (double) (iterations * 5);

        System.out.printf("%-40s: %,12d ops in %,6d ms | %,12.0f ops/ms | %.2f ns/op%n",
                name, iterations * 5, elapsedMs, opsPerSec, nsPerOp);
    }

    private static void runBenchmarkWithMathContext(String name, long[] values, int iterations) {
        java.math.MathContext mc = name.contains("UNLIMITED")
                ? java.math.MathContext.UNLIMITED
                : new java.math.MathContext(10, java.math.RoundingMode.HALF_UP);

        long start = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            int idx = (int) (i % values.length);
            int idx2 = (int) ((i + 1) % values.length);
            BigDecimal a = BigDecimal.valueOf(values[idx]);
            BigDecimal b = BigDecimal.valueOf(values[idx2]);
            a.multiply(b, mc);
        }

        long end = System.nanoTime();
        long elapsedMs = (end - start) / 1_000_000;
        double opsPerSec = (iterations * 1000.0) / elapsedMs;
        double nsPerOp = (end - start) / (double) iterations;

        System.out.printf("%-40s: %,12d ops in %,6d ms | %,12.0f ops/ms | %.2f ns/op%n",
                name, iterations, elapsedMs, opsPerSec, nsPerOp);
    }
}
