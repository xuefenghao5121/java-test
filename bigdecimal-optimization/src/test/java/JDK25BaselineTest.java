import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * JDK 25 Baseline Performance Test
 *
 * Tests native BigDecimal performance on JDK 25 before applying patch.
 * This establishes the baseline for comparison with patched version.
 */
public class JDK25BaselineTest {

    private static final int WARMUP_ITERATIONS = 100000;
    private static final int TEST_ITERATIONS = 10000000;

    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║           JDK 25 BigDecimal Baseline Performance Test        ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝\n");

        System.out.println("JDK Version: " + System.getProperty("java.version"));
        System.out.println("JDK Vendor:  " + System.getProperty("java.vendor"));
        System.out.println("OS:          " + System.getProperty("os.name"));
        System.out.println("Arch:        " + System.getProperty("os.arch"));
        System.out.println();

        // Phase 1: 乘法性能
        System.out.println("═══ Phase 1: Multiply Performance ═══");
        testMultiplyPerformance();

        // Phase 2: 除法性能
        System.out.println("\n═══ Phase 2: Divide Performance ═══");
        testDividePerformance();

        // Phase 3: 税务场景测试
        System.out.println("\n═══ Phase 3: Tax Scenario Performance ═══");
        testTaxScenarios();

        // Phase 4: 字符串解析性能
        System.out.println("\n═══ Phase 4: String Parse Performance ═══");
        testStringParsePerformance();
    }

    private static void testMultiplyPerformance() {
        System.out.println("Testing: price × taxRate");
        System.out.println("─────────────────────────────────────────");
        System.out.printf("%-12s | %-15s%n", "Price", "ns/op");
        System.out.println("─────────────┼────────────────");

        String[] prices = {"10", "100", "1000", "10000", "100000", "1000000"};
        BigDecimal taxRate = new BigDecimal("0.08");

        for (String priceStr : prices) {
            BigDecimal price = new BigDecimal(priceStr);

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                price.multiply(taxRate);
            }

            // Test
            long start = System.nanoTime();
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                price.multiply(taxRate);
            }
            long end = System.nanoTime();

            double avgTime = (end - start) / (double) TEST_ITERATIONS;
            System.out.printf("%-12s | %.2f ns%n", priceStr, avgTime);
        }
    }

    private static void testDividePerformance() {
        System.out.println("Testing: price ÷ (1 + taxRate)");
        System.out.println("─────────────────────────────────────────");
        System.out.printf("%-12s | %-15s%n", "Price", "ns/op");
        System.out.println("─────────────┼────────────────");

        String[] prices = {"10", "100", "1000", "10000", "100000", "1000000"};
        BigDecimal onePlusTax = new BigDecimal("1.08");

        for (String priceStr : prices) {
            BigDecimal price = new BigDecimal(priceStr);

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                price.divide(onePlusTax, 2, RoundingMode.HALF_UP);
            }

            // Test
            long start = System.nanoTime();
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                price.divide(onePlusTax, 2, RoundingMode.HALF_UP);
            }
            long end = System.nanoTime();

            double avgTime = (end - start) / (double) TEST_ITERATIONS;
            System.out.printf("%-12s | %.2f ns%n", priceStr, avgTime);
        }
    }

    private static void testTaxScenarios() {
        System.out.println("Tax Calculation Scenarios");
        System.out.println("─────────────────────────────────────────");
        System.out.printf("%-20s | %-15s | %-15s%n", "Scenario", "Multiply ns", "Divide ns");
        System.out.println("────────────────────┼─────────────────┼─────────────────");

        String[][] scenarios = {
            {"Micro ($10)", "10"},
            {"Small ($100)", "100"},
            {"Medium ($1K)", "1000"},
            {"Large ($10K)", "10000"},
            {"XL ($100K)", "100000"},
            {"XXL ($1M)", "1000000"},
        };

        for (String[] scenario : scenarios) {
            BigDecimal price = new BigDecimal(scenario[1]);
            BigDecimal taxRate = new BigDecimal("0.08");
            BigDecimal onePlusTax = new BigDecimal("1.08");

            // Multiply
            long multTime = benchmarkMultiply(price, taxRate);

            // Divide
            long divTime = benchmarkDivide(price, onePlusTax);

            System.out.printf("%-20s | %-15.2f | %-15.2f%n",
                    scenario[0], multTime / 1.0, divTime / 1.0);
        }
    }

    private static void testStringParsePerformance() {
        System.out.println("Financial Format String Parsing");
        System.out.println("─────────────────────────────────────────");
        System.out.printf("%-20s | %-15s%n", "Format", "ns/op");
        System.out.println("────────────────────┼─────────────────");

        String[] formats = {
            "123.45",       // Currency (2 decimals)
            "12345.67",     // Currency (2 decimals)
            "12.3456",      // Percentage (4 decimals)
            "123456.7890",  // Percentage (4 decimals)
            "-123.45",      // Negative currency
        };

        for (String format : formats) {
            long parseTime = benchmarkParse(format);
            System.out.printf("%-20s | %.2f ns%n", "\"" + format + "\"", parseTime / 1.0);
        }
    }

    private static long benchmarkMultiply(BigDecimal a, BigDecimal b) {
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

        return (end - start) / TEST_ITERATIONS;
    }

    private static long benchmarkDivide(BigDecimal a, BigDecimal b) {
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

        return (end - start) / TEST_ITERATIONS;
    }

    private static long benchmarkParse(String str) {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            new BigDecimal(str);
        }

        // Test
        long start = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            new BigDecimal(str);
        }
        long end = System.nanoTime();

        return (end - start) / TEST_ITERATIONS;
    }
}
