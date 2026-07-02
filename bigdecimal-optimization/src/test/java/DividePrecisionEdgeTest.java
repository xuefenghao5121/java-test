import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Division Precision Edge Cases Test
 *
 * Tests extreme boundary conditions that might reveal precision issues.
 */
public class DividePrecisionEdgeTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("BigDecimal Division Precision Edge Cases");
        System.out.println("========================================\n");

        // Test rounding edge cases (X.5 situations)
        testRoundingEdges();

        // Test maximum scale difference
        testMaxScaleDiff();

        // Test large values
        testLargeValues();

        // Test precision loss scenarios
        testPrecisionLoss();

        // Summary
        System.out.println("\n========================================");
        System.out.println("Results: " + passed + " passed, " + failed + " failed");
        if (failed == 0) {
            System.out.println("✓ All edge case tests passed!");
        } else {
            System.out.println("✗ Some edge case tests failed!");
        }
    }

    private static void testRoundingEdges() {
        System.out.println("--- Rounding Edge Cases (X.5) ---");

        // These should round UP with HALF_UP
        testDivide("15", "10", 1, "1.5");
        testDivide("25", "10", 1, "2.5");
        testDivide("35", "10", 1, "3.5");

        // 1.005 should round to 1.01 (with scale=2, HALF_UP)
        testDivide("1005", "1000", 2, "1.01");
        testDivide("1505", "1000", 2, "1.51");

        // 2.5 should round to 3 (odd/even rule doesn't apply to HALF_UP)
        testDivide("5", "2", 0, "3");     // 2.5 -> 3
        testDivide("15", "4", 0, "4");    // 3.75 -> 4
    }

    private static void testMaxScaleDiff() {
        System.out.println("\n--- Maximum Scale Difference (4) ---");

        // scaleDiff = 4 (maximum allowed by fast path)
        testDivide("100.00000", "10.0", 2, "10.00");
        testDivide("100.000000", "10", 2, "10.00");

        // scaleDiff = 4, divisor case
        testDivide("100.0", "10.00000", 2, "10.00");
        testDivide("100", "10.000000", 2, "10.00");

        // scaleDiff = 5 (should NOT use fast path, fall back to standard)
        // This should still produce correct results
        testDivide("100.0000000", "10.0", 2, "10.00");
    }

    private static void testLargeValues() {
        System.out.println("\n--- Large Values ---");

        // Within fast path limits (< 10^16)
        testDivide("9999999999999999", "100", 2, "99999999999999.99");
        testDivide("10000000000000000", "100", 2, "100000000000000.00");

        // Just at fast path boundary
        testDivide("999999999999999", "1000", 2, "999999999999.99");
    }

    private static void testPrecisionLoss() {
        System.out.println("\n--- Precision Loss Scenarios ---");

        // Cases where we might lose precision
        testDivide("100.01", "10.00", 2, "10.00");
        testDivide("100.09", "10.00", 2, "10.01");
        testDivide("999.99", "100.00", 2, "10.00");
        testDivide("999.94", "100.00", 2, "9.99");

        // With scale diff
        testDivide("100.0100", "10.00", 2, "10.00");
        testDivide("100.0900", "10.00", 2, "10.01");
    }

    private static void testDivide(String dividendStr, String divisorStr,
                                    int scale, String expected) {
        try {
            BigDecimal dividend = new BigDecimal(dividendStr);
            BigDecimal divisor = new BigDecimal(divisorStr);
            BigDecimal expectedResult = new BigDecimal(expected);

            BigDecimal result = dividend.divide(divisor, scale, RoundingMode.HALF_UP);

            if (result.compareTo(expectedResult) == 0) {
                passed++;
                System.out.printf("✓ %s ÷ %s = %s (expected %s)%n",
                        dividendStr, divisorStr, result, expected);
            } else {
                failed++;
                System.out.printf("✗ %s ÷ %s = %s (expected %s) [FAIL]%n",
                        dividendStr, divisorStr, result, expected);
            }
        } catch (Exception e) {
            failed++;
            System.out.printf("✗ %s ÷ %s threw %s [FAIL]%n",
                    dividendStr, divisorStr, e.getMessage());
        }
    }
}
