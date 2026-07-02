import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 深入分析：为什么 scale 差异场景可以使用快速路径
 *
 * 数学原理： dividend ÷ divisor
 * 当 dividend 和 divisor 的 scale 不同时，可以调整为：
 * (dividend × 10^s1) ÷ (divisor × 10^s2) = (dividendCompact) ÷ (divisorCompact × 10^(s2-s1))
 */
public class ScaleDiffAnalysis {

    public static void main(String[] args) {
        System.out.println("=".repeat(100));
        System.out.println("Scale 差异场景快速路径分析");
        System.out.println("=".repeat(100));

        // 场景 1: scaleDiff < 0
        analyzeCase1();

        // 场景 2: scaleDiff > 0
        analyzeCase2();

        // 场景 3: scaleDiff == 0
        analyzeCase3();

        // 通用公式推导
        deriveGeneralFormula();

        // 正确的快速路径实现
        implementCorrectFastPath();
    }

    /**
     * 场景 1: dividend scale < divisor scale (scaleDiff < 0)
     *
     * 示例: 100.00 (scale=2) ÷ 0.01 (scale=4)
     * 实际上 BigDecimal 内部表示：
     *   100.00 → intCompact=10000, scale=2
     *   0.01 → intCompact=1, scale=2
     *
     * 这里应该修正示例为更典型的场景
     */
    private static void analyzeCase1() {
        System.out.println("\n场景 1: scaleDiff < 0 (被除数 scale 更小)");
        System.out.println("-".repeat(100));

        // 修正示例：100 (scale=0) ÷ 0.01 (scale=2)
        System.out.println("示例: 100 ÷ 0.01");
        System.out.println("  被除数: 100 (scale=0, intCompact=100)");
        System.out.println("  除数: 0.01 (scale=2, intCompact=1)");
        System.out.println("  scaleDiff = 0 - 2 = -2");
        System.out.println();

        // 错误的做法
        System.out.println("❌ 错误做法（之前的实现）：");
        System.out.println("  1. 调整除数: 1 × 10^2 = 100");
        System.out.println("  2. 除法: 100 ÷ 100 = 1");
        System.out.println("  3. 返回 valueOf(1, scale) → 1.00");
        System.out.println("  问题：结果是 1.00，但正确结果是 10000.00！");
        System.out.println();

        // 正确的做法
        System.out.println("✓ 正确做法：");
        System.out.println("  方法 A：调整被除数");
        System.out.println("    1. 调整被除数: 100 × 10^2 = 10000");
        System.out.println("    2. 除法: 10000 ÷ 1 = 10000");
        System.out.println("    3. 返回 valueOf(10000, scale+(-scaleDiff)) = valueOf(10000, -2+2) = valueOf(10000, 0)");
        System.out.println("    结果：10000 ✓");
        System.out.println();

        System.out.println("  方法 B：调整除数，同时调整结果 scale");
        System.out.println("    1. 调整除数: 1 × 10^2 = 100");
        System.out.println("    2. 除法: 100 ÷ 100 = 1");
        System.out.println("    3. 返回 valueOf(1, scale-scaleDiff) = valueOf(1, 0-(-2)) = valueOf(1, 2)");
        System.out.println("    valueOf(1, 2) = 0.01，这是错的！");
        System.out.println("    等等，让我重新推导...");
        System.out.println();

        // 重新推导
        System.out.println("  重新推导方法 B：");
        System.out.println("    数学上: 100 ÷ 0.01 = 10000");
        System.out.println("    当我们做 100 ÷ 100 时，实际是：");
        System.out.println("      100 ÷ (1 × 10^2) = (100 ÷ 1) × 10^(-2) = 100 × 0.01 = 1");
        System.out.println("    这不对！应该是：");
        System.out.println("      100 ÷ 0.01 = 100 ÷ (1 × 10^(-2)) = 100 × 10^2 = 10000");
        System.out.println("    问题在于：0.01 的 intCompact=1, scale=2 表示的是 1×10^(-2)");
        System.out.println("    所以调整除数时应该除以 10^2，而不是乘以 10^2");
        System.out.println();

        System.out.println("  正确的方法 B（调整除数）：");
        System.out.println("    除数 0.01 = 1 × 10^(-2)");
        System.out.println("    被除数 100 = 100 × 10^0");
        System.out.println("    100 ÷ 0.01 = 100 ÷ (1 × 10^(-2)) = 100 × 10^2 ÷ 1 = 10000");
        System.out.println("    所以应该：调整被除数 (×10^2)，然后除以除数");
    }

    /**
     * 场景 2: dividend scale > divisor scale (scaleDiff > 0)
     */
    private static void analyzeCase2() {
        System.out.println("\n场景 2: scaleDiff > 0 (被除数 scale 更大)");
        System.out.println("-".repeat(100));

        System.out.println("示例: 1.00 (scale=2) ÷ 1 (scale=0)");
        System.out.println("  被除数: 1.00 (scale=2, intCompact=100)");
        System.out.println("  除数: 1 (scale=0, intCompact=1)");
        System.out.println("  scaleDiff = 2 - 0 = 2");
        System.out.println();

        System.out.println("✓ 正确做法：");
        System.out.println("  1. 调整被除数: 100 ÷ 10^2 = 1（但这样会丢失精度）");
        System.out.println("  更好的做法：调整除数");
        System.out.println("    除数: 1 × 10^2 = 100");
        System.out.println("    除法: 100 ÷ 100 = 1");
        System.out.println("    返回 valueOf(1, scale-scaleDiff) = valueOf(1, 2-2) = valueOf(1, 0)");
        System.out.println("    结果：1 ✓");
        System.out.println();

        BigDecimal result = new BigDecimal("1.00").divide(new BigDecimal("1"), 2, RoundingMode.HALF_UP);
        System.out.println("  验证: 1.00 ÷ 1 = " + result);
    }

    /**
     * 场景 3: scaleDiff == 0
     */
    private static void analyzeCase3() {
        System.out.println("\n场景 3: scaleDiff == 0 (scale 相等)");
        System.out.println("-".repeat(100));

        System.out.println("示例: 100.00 (scale=2) ÷ 0.25 (scale=2)");
        System.out.println("  被除数: 100.00 (scale=2, intCompact=10000)");
        System.out.println("  除数: 0.25 (scale=2, intCompact=25)");
        System.out.println("  scaleDiff = 2 - 2 = 0");
        System.out.println();

        System.out.println("✓ 直接除法：");
        System.out.println("  10000 ÷ 25 = 400");
        System.out.println("  返回 valueOf(400, scale) = valueOf(400, 2)");
        System.out.println("  结果：4.00 ✓");

        BigDecimal result = new BigDecimal("100.00").divide(new BigDecimal("0.25"), 2, RoundingMode.HALF_UP);
        System.out.println("  验证: 100.00 ÷ 0.25 = " + result);
    }

    /**
     * 通用公式推导
     */
    private static void deriveGeneralFormula() {
        System.out.println("\n通用公式推导");
        System.out.println("=".repeat(100));

        System.out.println("\n设：");
        System.out.println("  被除数 = dividend (intCompact = d, scale = sd)");
        System.out.println("  除数 = divisor (intCompact = r, scale = sr)");
        System.out.println("  目标 scale = s（结果的小数位数）");
        System.out.println("  scaleDiff = sd - sr");

        System.out.println("\n数学原理：");
        System.out.println("  被除数实际值 = d × 10^(-sd)");
        System.out.println("  除数实际值 = r × 10^(-sr)");
        System.out.println("  除法结果 = (d × 10^(-sd)) ÷ (r × 10^(-sr))");
        System.out.println("           = (d ÷ r) × 10^(-sd + sr)");
        System.out.println("           = (d ÷ r) × 10^(scaleDiff)");

        System.out.println("\n快速路径实现策略：");
        System.out.println();
        System.out.println("情况 1: scaleDiff == 0");
        System.out.println("  结果 = (d ÷ r) × 10^0 = d ÷ r");
        System.out.println("  实现: 直接除法，返回 valueOf(q, s)");
        System.out.println();
        System.out.println("情况 2: scaleDiff > 0 (被除数小数位更多)");
        System.out.println("  结果 = (d ÷ r) × 10^(scaleDiff)");
        System.out.println("  实现: 调整除数 r' = r × 10^(scaleDiff)");
        System.out.println("       计算 q = d ÷ r'");
        System.out.println("       返回 valueOf(q, s - scaleDiff)");
        System.out.println();
        System.out.println("情况 3: scaleDiff < 0 (除数小数位更多)");
        System.out.println("  结果 = (d ÷ r) × 10^(scaleDiff) [scaleDiff 是负数]");
        System.out.println("  实现: 调整被除数 d' = d × 10^(-scaleDiff)");
        System.out.println("       计算 q = d' ÷ r");
        System.out.println("       返回 valueOf(q, s + (-scaleDiff))");
    }

    /**
     * 正确的快速路径实现
     */
    private static void implementCorrectFastPath() {
        System.out.println("\n正确的快速路径实现");
        System.out.println("=".repeat(100));

        System.out.println("\n```java");
        System.out.println("private static BigDecimal divide(long dividend, int dividendScale,");
        System.out.println("                               long divisor, int divisorScale,");
        System.out.println("                               int scale, int roundingMode) {");
        System.out.println("    int scaleDiff = dividendScale - divisorScale;");
        System.out.println();
        System.out.println("    // 快速路径：只处理紧凑表示且数值在合理范围内");
        System.out.println("    if (isCompact(dividend) && isCompact(divisor) &&");
        System.out.println("        canUseFastDivide(dividend, divisor)) {");
        System.out.println();
        System.out.println("        int qsign = ((dividend < 0) == (divisor < 0)) ? 1 : -1;");
        System.out.println("        long absDividend = Math.abs(dividend);");
        System.out.println("        long absDivisor = Math.abs(divisor);");
        System.out.println();
        System.out.println("        if (scaleDiff == 0) {");
        System.out.println("            // 情况 1: scale 相等，直接除法");
        System.out.println("            return divideAndRound(absDividend, absDivisor,");
        System.out.println("                                  qsign, scale, roundingMode);");
        System.out.println("        }");
        System.out.println("        else if (scaleDiff > 0) {");
        System.out.println("            // 情况 2: 被除数小数位更多");
        System.out.println("            // 调整除数，同时调整目标 scale");
        System.out.println("            long scaledDivisor = longMultiplyPowerTen(absDivisor, scaleDiff);");
        System.out.println("            if (scaledDivisor != INFLATED) {");
        System.out.println("                BigDecimal result = divideAndRound(absDividend, scaledDivisor,");
        System.out.println("                                                      qsign, scale - scaleDiff, roundingMode);");
        System.out.println("                // 恢复到目标 scale");
        System.out.println("                return result.setScale(scale, roundingMode);");
        System.out.println("            }");
        System.out.println("        }");
        System.out.println("        else { // scaleDiff < 0");
        System.out.println("            // 情况 3: 除数小数位更多");
        System.out.println("            // 调整被除数，同时调整目标 scale");
        System.out.println("            int adjust = -scaleDiff;");
        System.out.println("            long scaledDividend = longMultiplyPowerTen(absDividend, adjust);");
        System.out.println("            if (scaledDividend != INFLATED) {");
        System.out.println("                BigDecimal result = divideAndRound(scaledDividend, absDivisor,");
        System.out.println("                                                      qsign, scale + adjust, roundingMode);");
        System.out.println("                // 恢复到目标 scale");
        System.out.println("                return result.setScale(scale, roundingMode);");
        System.out.println("            }");
        System.out.println("        }");
        System.out.println("    }");
        System.out.println();
        System.out.println("    // 标准路径");
        System.out.println("    return ...");
        System.out.println("}");
        System.out.println("```");
    }

    /**
     * 辅助方法模拟
     */
    private static BigDecimal divideAndRound(long dividend, long divisor, int sign, int scale, int rounding) {
        long q = dividend / divisor;
        long r = dividend % divisor;
        return BigDecimal.valueOf(q * sign).setScale(scale, RoundingMode.HALF_UP);
    }
}
