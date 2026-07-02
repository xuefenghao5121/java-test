import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Tax Rate Fast Path Coverage Analysis
 *
 * Analyzes which tax rate calculations benefit from the optimized fast paths.
 */
public class TaxRateAnalysis {

    private static final BigDecimal[] TAX_RATES = {
        new BigDecimal("0.01"),   // 1%
        new BigDecimal("0.05"),   // 5%
        new BigDecimal("0.08"),   // 8%
        new BigDecimal("0.10"),   // 10%
        new BigDecimal("0.20"),   // 20%
        new BigDecimal("0.25"),   // 25%
    };

    private static final String[] AMOUNTS = {
        "10.00",          // $10
        "100.00",         // $100
        "1000.00",        // $1K
        "10000.00",       // $10K
        "100000.00",      // $100K
        "1000000.00",     // $1M
        "10000000.00",    // $10M
        "100000000.00",   // $100M
        "1000000000.00",  // $1B
        "10000000000.00", // $10B
    };

    public static void main(String[] args) {
        System.out.println("BigDecimal Fast Path Coverage Analysis");
        System.out.println("======================================\n");

        System.out.println("--- Multiplication Fast Path Coverage ---");
        analyzeMultiplyCoverage();

        System.out.println("\n--- Division Fast Path Coverage ---");
        analyzeDivideCoverage();

        System.out.println("\n--- Tax Rate Calculation Scenarios ---");
        analyzeTaxScenarios();
    }

    private static void analyzeMultiplyCoverage() {
        System.out.println("Amount Range        | Fast Path | Tax Rate Examples");
        System.out.println("---------------------|-----------|-------------------");

        for (String amount : AMOUNTS) {
            BigDecimal a = new BigDecimal(amount);
            List<String> fastPathRates = new ArrayList<>();
            List<String> slowPathRates = new ArrayList<>();

            for (BigDecimal rate : TAX_RATES) {
                if (wouldUseMultiplyFastPath(a, rate)) {
                    fastPathRates.add(rate.toPlainString());
                } else {
                    slowPathRates.add(rate.toPlainString());
                }
            }

            if (fastPathRates.isEmpty()) {
                System.out.printf("%-20s | No        | %s%n",
                        formatAmountRange(amount), String.join(", ", slowPathRates));
            } else {
                System.out.printf("%-20s | Yes       | %s%n",
                        formatAmountRange(amount), String.join(", ", fastPathRates));
            }
        }
    }

    private static void analyzeDivideCoverage() {
        System.out.println("Amount Range        | Divisor   | Fast Path | Scale Diff");
        System.out.println("---------------------|-----------|-----------|------------");

        String[] divisors = {"1.08", "1.10", "1.20", "100", "1000"};

        for (String amount : AMOUNTS) {
            for (String divisor : divisors) {
                BigDecimal dividend = new BigDecimal(amount);
                BigDecimal div = new BigDecimal(divisor);
                int scaleDiff = dividend.scale() - div.scale();

                boolean fastPath = wouldUseDivideFastPath(dividend, div, scaleDiff);
                System.out.printf("%-20s | %-9s | %-9s | %d%n",
                        formatAmountRange(amount), divisor,
                        fastPath ? "Yes" : "No", scaleDiff);
            }
            if (amount.equals("100000.00")) {
                System.out.println("---------------------|-----------|-----------|------------");
            }
        }
    }

    private static void analyzeTaxScenarios() {
        System.out.println("Scenario                    | Amount      | Rate  | Fast Path");
        System.out.println("----------------------------|-------------|-------|----------");

        // Common tax scenarios
        String[][] scenarios = {
            {"Sales tax (8%)", "100.00", "0.08"},
            {"Sales tax (8%)", "100000.00", "0.08"},
            {"Sales tax (8%)", "10000000.00", "0.08"},
            {"VAT (20%)", "100.00", "0.20"},
            {"VAT (20%)", "100000.00", "0.20"},
            {"VAT (20%)", "10000000.00", "0.20"},
            {"Service tax (10%)", "100.00", "0.10"},
            {"Service tax (10%)", "100000.00", "0.10"},
            {"Service tax (10%)", "10000000.00", "0.10"},
        };

        for (String[] scenario : scenarios) {
            BigDecimal amount = new BigDecimal(scenario[1]);
            BigDecimal rate = new BigDecimal(scenario[2]);
            boolean fastPath = wouldUseMultiplyFastPath(amount, rate);

            System.out.printf("%-27s | %-11s | %-5s | %s%n",
                    scenario[0], scenario[1], scenario[2],
                    fastPath ? "✓" : "✗");
        }
    }

    /**
     * Simulates the optimized isSmallMultiply check.
     */
    private static boolean wouldUseMultiplyFastPath(BigDecimal a, BigDecimal b) {
        // Extract compact values if available
        long longA = getCompactValue(a);
        long longB = getCompactValue(b);

        if (longA == INFLATED || longB == INFLATED) {
            return false;
        }

        long absA = Math.abs(longA);
        long absB = Math.abs(longB);

        // Fast path 1: both small (< 10^9)
        if (absA < 1_000_000_000L && absB < 1_000_000_000L) {
            return true;
        }

        // Fast path 2: one very small (< 1K), other up to 10^13
        if (absA < 1_000L && absB < 10_000_000_000_000L) {
            return true;
        }
        if (absB < 1_000L && absA < 10_000_000_000_000L) {
            return true;
        }

        return false;
    }

    /**
     * Simulates the canUseFastDivideWithScale check.
     */
    private static boolean wouldUseDivideFastPath(BigDecimal dividend, BigDecimal divisor, int scaleDiff) {
        long longDividend = getCompactValue(dividend);
        long longDivisor = getCompactValue(divisor);

        if (longDividend == INFLATED || longDivisor == INFLATED) {
            return false;
        }

        return longDivisor != 0 &&
               Math.abs(longDivisor) < 100_000L &&
               Math.abs(longDividend) < 10_000_000_000_000_000L &&
               Math.abs(scaleDiff) <= 4;
    }

    private static String formatAmountRange(String amount) {
        double value = Double.parseDouble(amount);
        if (value < 1000) return "$" + (int) value;
        if (value < 1_000_000) return "$" + (int) (value / 1000) + "K";
        if (value < 1_000_000_000) return "$" + (int) (value / 1_000_000) + "M";
        return "$" + (int) (value / 1_000_000_000) + "B";
    }

    private static final long INFLATED = Long.MIN_VALUE;

    private static long getCompactValue(BigDecimal bd) {
        if (bd.scale() != 0) {
            // For simplicity, only handle scale 0 in this simulation
            // Real implementation would adjust for scale
            return INFLATED;
        }

        // Check if value fits in long
        BigInteger intVal = bd.toBigIntegerExact();
        if (intVal == null) {
            return INFLATED;
        }

        try {
            return intVal.longValueExact();
        } catch (ArithmeticException e) {
            return INFLATED;
        }
    }
}
