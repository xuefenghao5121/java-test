import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Division Precision Test
 *
 * Verifies that divide operations produce correct results.
 * Tests cases that would use the fast path optimization.
 */
public class DividePrecisionTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("BigDecimal Division Precision Test");
        System.out.println("====================================\n");

        // Test same scale division
        testSameScale();

        // Test scale difference cases
        testScaleDifference();

        // Test edge cases
        testEdgeCases();

        // Test rounding modes
        testRoundingModes();

        // Summary
        System.out.println("\n====================================");
        System.out.println("Results: " + passed + " passed, " + failed + " failed");
        if (failed == 0) {
            System.out.println("✓ All tests passed!");
        } else {
            System.out.println("✗ Some tests failed!");
        }
    }

    private static void testSameScale() {
        System.out.println("--- Same Scale Division ---");

        testDivide("1000", "100", 0, "10");
        testDivide("10000", "10", 0, "1000");
        testDivide("100.00", "10.00", 2, "10.00");
        testDivide("55.00", "10.00", 2, "5.50");
        testDivide("100.00", "3.00", 2, "33.33");
        testDivide("1.00", "3.00", 2, "0.33");
    }

    private static void testScaleDifference() {
        System.out.println("\n--- Scale Difference Division ---");

        // dividend has more decimal places (scaleDiff > 0)
        testDivide("100.0000", "10.00", 2, "10.00");
        testDivide("100.00000", "10.0", 2, "10.00");
        testDivide("55.0000", "10.00", 2, "5.50");
        testDivide("33.3333", "10.00", 2, "3.33");

        // divisor has more decimal places (scaleDiff < 0)
        testDivide("100.00", "10.0000", 2, "10.00");
        testDivide("100.0", "10.00000", 2, "10.00");
        testDivide("55.00", "10.0000", 2, "5.50");
        testDivide("33.33", "10.0000", 2, "3.33");
    }

    private static void testEdgeCases() {
        System.out.println("\n--- Edge Cases ---");

        testDivide("1", "2", 2, "0.50");
        testDivide("1", "3", 2, "0.33");
        testDivide("1", "6", 2, "0.17");
        testDivide("1", "7", 2, "0.14");
        testDivide("1", "8", 2, "0.13");
        testDivide("10", "3", 2, "3.33");
        testDivide("100", "3", 2, "33.33");
        testDivide("1000", "3", 2, "333.33");
    }

    private static void testRoundingModes() {
        System.out.println("\n--- Rounding Modes ---");

        BigDecimal d = new BigDecimal("1.00");
        BigDecimal divisor = new BigDecimal("3.00");

        testDivideWithRounding(d, divisor, 2, RoundingMode.HALF_UP, "0.33");
        testDivideWithRounding(d, divisor, 2, RoundingMode.DOWN, "0.33");
        testDivideWithRounding(d, divisor, 2, RoundingMode.UP, "0.34");
        testDivideWithRounding(d, divisor, 2, RoundingMode.FLOOR, "0.33");
        testDivideWithRounding(d, divisor, 2, RoundingMode.CEILING, "0.34");
    }

    private static void testDivide(String dividendStr, String divisorStr,
                                    int scale, String expected) {
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
    }

    private static void testDivideWithRounding(BigDecimal dividend, BigDecimal divisor,
                                                int scale, RoundingMode roundingMode,
                                                String expected) {
        BigDecimal expectedResult = new BigDecimal(expected);
        BigDecimal result = dividend.divide(divisor, scale, roundingMode);

        if (result.compareTo(expectedResult) == 0) {
            passed++;
            System.out.printf("✓ %s ÷ %s (%s) = %s%n",
                    dividend, divisor, roundingMode, result);
        } else {
            failed++;
            System.out.printf("✗ %s ÷ %s (%s) = %s (expected %s) [FAIL]%n",
                    dividend, divisor, roundingMode, result, expected);
        }
    }
}
