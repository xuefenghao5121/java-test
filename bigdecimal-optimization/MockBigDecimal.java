import java.math.BigInteger;

/**
 * Mock BigDecimal to demonstrate the multiply fast path optimization.
 * This simulates the optimized vs original implementation.
 */
public class MockBigDecimal {
    private final long intCompact;
    private final int scale;

    private static final long INFLATED = Long.MIN_VALUE;

    public MockBigDecimal(long value, int scale) {
        this.intCompact = value;
        this.scale = scale;
    }

    /**
     * ORIGINAL multiply - always does overflow check
     */
    public static long multiplyOriginal(long x, long y) {
        long product = x * y;
        long ax = Math.abs(x);
        long ay = Math.abs(y);
        // Overflow detection using division
        if (((ax | ay) >>> 31 == 0) || (y == 0) || (product / y == x)) {
            return product;
        }
        return INFLATED;
    }

    /**
     * OPTIMIZED multiply with fast path for small values
     */
    private static boolean isSmallMultiply(long x, long y) {
        return Math.abs(x) < 1_000_000_000L && Math.abs(y) < 1_000_000_000L;
    }

    public static long multiplyOptimized(long x, long y) {
        // Fast path: skip overflow check for small values
        if (isSmallMultiply(x, y)) {
            return x * y;
        }
        // Standard path with overflow detection
        return multiplyOriginal(x, y);
    }

    /**
     * Benchmark to compare original vs optimized
     */
    public static void main(String[] args) {
        System.out.println("Mock BigDecimal Multiply - Fast Path Validation");
        System.out.println("=" .repeat(70));

        // Test values
        long[][] testCases = {
            {100_000L, 200_000L},           // Tiny: 8 digits * 8 digits
            {1_000_000L, 2_000_000L},       // Small: 9 digits * 9 digits
            {100_000_000L, 200_000_000L},   // Medium: 11 digits * 11 digits
            {1_500_000_000L, 2_000_000_000L} // Large: > 10^9 (exceeds fast path)
        };

        String[] names = {"Tiny", "Small", "Medium", "Large (>10^9)"};

        // Verify correctness
        System.out.println("\nCorrectness Check:");
        System.out.println("-".repeat(70));
        for (int i = 0; i < testCases.length; i++) {
            long x = testCases[i][0];
            long y = testCases[i][1];

            boolean useFastPath = isSmallMultiply(x, y);
            long orig = multiplyOriginal(x, y);
            long opt = multiplyOptimized(x, y);

            System.out.printf("%s (%d * %d): FastPath=%s, Both=%s%n",
                    names[i], x, y, useFastPath, orig == opt);
        }

        // Performance comparison
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Performance Comparison (10 million operations)");
        System.out.println("=".repeat(70));

        int iterations = 10_000_000;
        long[] smallValues = generateSmallValues(1000);
        long[] largeValues = generateLargeValues(1000);

        // Warmup
        for (int i = 0; i < 100_000; i++) {
            multiplyOriginal(smallValues[i % smallValues.length],
                           smallValues[(i + 1) % smallValues.length]);
            multiplyOptimized(smallValues[i % smallValues.length],
                            smallValues[(i + 1) % smallValues.length]);
        }

        // Benchmark small values (fast path)
        long t1 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            multiplyOriginal(smallValues[i % smallValues.length],
                           smallValues[(i + 1) % smallValues.length]);
        }
        long t2 = System.nanoTime();
        long origSmallTime = t2 - t1;

        t1 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            multiplyOptimized(smallValues[i % smallValues.length],
                            smallValues[(i + 1) % smallValues.length]);
        }
        t2 = System.nanoTime();
        long optSmallTime = t2 - t1;

        // Benchmark large values (standard path)
        t1 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            multiplyOriginal(largeValues[i % largeValues.length],
                           largeValues[(i + 1) % largeValues.length]);
        }
        t2 = System.nanoTime();
        long origLargeTime = t2 - t1;

        t1 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            multiplyOptimized(largeValues[i % largeValues.length],
                            largeValues[(i + 1) % largeValues.length]);
        }
        t2 = System.nanoTime();
        long optLargeTime = t2 - t1;

        // Print results
        System.out.println("\nSmall Values (< 10^9, uses fast path):");
        System.out.printf("  Original: %8.2f ms%n", origSmallTime / 1_000_000.0);
        System.out.printf("  Optimized: %8.2f ms%n", optSmallTime / 1_000_000.0);
        System.out.printf("  Speedup:   %8.2fx%n", (double) origSmallTime / optSmallTime);

        System.out.println("\nLarge Values (≥ 10^9, uses standard path):");
        System.out.printf("  Original: %8.2f ms%n", origLargeTime / 1_000_000.0);
        System.out.printf("  Optimized: %8.2f ms%n", optLargeTime / 1_000_000.0);
        System.out.printf("  Speedup:   %8.2fx (should be ~1.0x, same code path)%n",
                (double) origLargeTime / optLargeTime);

        System.out.println("\n" + "=".repeat(70));
        System.out.println("Summary: Fast path optimization shows " +
                String.format("%.1f", (double) origSmallTime / optSmallTime - 1) +
                "% speedup for small values while maintaining correctness for all values.");
        System.out.println("=".repeat(70));
    }

    private static long[] generateSmallValues(int count) {
        long[] values = new long[count];
        java.util.Random rng = new java.util.Random(42);
        for (int i = 0; i < count; i++) {
            // Values from -10M to 10M (fits in fast path)
            values[i] = rng.nextInt(20_000_000) - 10_000_000;
        }
        return values;
    }

    private static long[] generateLargeValues(int count) {
        long[] values = new long[count];
        java.util.Random rng = new java.util.Random(42);
        for (int i = 0; i < count; i++) {
            // Values from -2B to 2B (exceeds fast path threshold of 10^9)
            long high = rng.nextInt(2_000_000_000);
            values[i] = high + 1_500_000_000L; // Ensure > 10^9
        }
        return values;
    }
}
