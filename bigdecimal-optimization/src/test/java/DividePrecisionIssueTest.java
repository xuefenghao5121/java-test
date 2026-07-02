import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Division Precision Issue Test
 *
 * Tests specific cases where precision issues were reported.
 * Dividend = 100, Divisor = 0.001, 0.01, 1.2345, 99.999, 999.99
 */
public class DividePrecisionIssueTest {

    public static void main(String[] args) {
        System.out.println("Division Precision Issue Test");
        System.out.println("==============================\n");

        BigDecimal dividend = new BigDecimal("100");
        int targetScale = 8;  // Use high precision to see differences

        String[] divisors = {"0.001", "0.01", "1.2345", "99.999", "999.99"};

        System.out.println("Dividend: 100");
        System.out.println("Target Scale: " + targetScale);
        System.out.println("Rounding Mode: HALF_UP");
        System.out.println();

        for (String divisorStr : divisors) {
            BigDecimal divisor = new BigDecimal(divisorStr);

            // Get the result
            BigDecimal result = dividend.divide(divisor, targetScale, RoundingMode.HALF_UP);

            // Also compute using different scales for comparison
            BigDecimal resultScale2 = dividend.divide(divisor, 2, RoundingMode.HALF_UP);
            BigDecimal resultScale4 = dividend.divide(divisor, 4, RoundingMode.HALF_UP);

            // Manual calculation for verification
            double doubleResult = 100.0 / Double.parseDouble(divisorStr);

            System.out.println("─────────────────────────────────────────────");
            System.out.println("Divisor: " + divisorStr);
            System.out.println("  BigDecimal result (scale=8): " + result);
            System.out.println("  BigDecimal result (scale=4): " + resultScale4);
            System.out.println("  BigDecimal result (scale=2): " + resultScale2);
            System.out.println("  Double calculation (approx):  " + doubleResult);

            // Check consistency across scales
            BigDecimal expectedFromScale8 = result.setScale(2, RoundingMode.HALF_UP);
            if (expectedFromScale8.compareTo(resultScale2) != 0) {
                System.out.println("  ⚠️  INCONSISTENCY: scale=8 rounded to 2 != direct scale=2");
                System.out.println("      scale=8 rounded: " + expectedFromScale8);
                System.out.println("      direct scale=2:  " + resultScale2);
            } else {
                System.out.println("  ✓ Consistent across scales");
            }

            // Check dividend/divisor scale info
            System.out.println("  Dividend scale: " + dividend.scale() + ", compact: " + dividend.unscaledValue());
            System.out.println("  Divisor scale:  " + divisor.scale() + ", compact: " + divisor.unscaledValue());
            System.out.println("  Scale difference: " + (dividend.scale() - divisor.scale()));
        }

        System.out.println("\n─────────────────────────────────────────────");
        System.out.println("\nDetailed Case Analysis:");
        detailedAnalysis();
    }

    private static void detailedAnalysis() {
        System.out.println("\n--- Case: 100 ÷ 0.001 ---");
        analyzeCase("100", "0.001");

        System.out.println("\n--- Case: 100 ÷ 0.01 ---");
        analyzeCase("100", "0.01");

        System.out.println("\n--- Case: 100 ÷ 1.2345 ---");
        analyzeCase("100", "1.2345");

        System.out.println("\n--- Case: 100 ÷ 99.999 ---");
        analyzeCase("100", "99.999");

        System.out.println("\n--- Case: 100 ÷ 999.99 ---");
        analyzeCase("100", "999.99");
    }

    private static void analyzeCase(String dividendStr, String divisorStr) {
        BigDecimal dividend = new BigDecimal(dividendStr);
        BigDecimal divisor = new BigDecimal(divisorStr);

        System.out.println("  Dividend: intCompact=" + dividend.unscaledValue() + ", scale=" + dividend.scale());
        System.out.println("  Divisor:  intCompact=" + divisor.unscaledValue() + ", scale=" + divisor.scale());

        int scaleDiff = dividend.scale() - divisor.scale();
        System.out.println("  Scale diff: " + scaleDiff);

        // Check if fast path would be used
        long dividendCompact = dividend.unscaledValue().longValue();
        long divisorCompact = divisor.unscaledValue().longValue();

        boolean wouldUseFastPath =
            divisorCompact != 0 &&
            Math.abs(divisorCompact) < 100_000L &&
            Math.abs(dividendCompact) < 10_000_000_000_000_000L &&
            Math.abs(scaleDiff) <= 4;

        System.out.println("  Would use fast path: " + wouldUseFastPath);

        // Actual result
        BigDecimal result = dividend.divide(divisor, 8, RoundingMode.HALF_UP);
        System.out.println("  Result: " + result);
    }
}
