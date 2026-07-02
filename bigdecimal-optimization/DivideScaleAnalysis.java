import java.math.BigDecimal;
import java.math.RoundingMode;
import java.lang.reflect.Field;

/**
 * 分析除法场景的 scale 和内部表示
 */
public class DivideScaleAnalysis {

    private static final Field INFLATED_FIELD;
    private static final Long INFLATED_VALUE;

    static {
        try {
            INFLATED_FIELD = BigDecimal.class.getDeclaredField("INFLATED");
            INFLATED_FIELD.setAccessible(true);
            INFLATED_VALUE = (Long) INFLATED_FIELD.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        System.out.println("=".repeat(100));
        System.out.println("除法场景 Scale 分析");
        System.out.println("=".repeat(100));

        BigDecimal dividend = new BigDecimal("100");
        BigDecimal[] divisors = {
            new BigDecimal("0.001"),
            new BigDecimal("0.01"),
            new BigDecimal("1.2345"),
            new BigDecimal("99.999"),
            new BigDecimal("999.99")
        };

        System.out.println("\n被除数: " + dividend);
        System.out.println("  scale: " + dividend.scale());
        System.out.println("  precision: " + dividend.precision());
        try {
            System.out.println("  intCompact: " + getIntCompact(dividend));
        } catch (Exception e) {
            System.out.println("  intCompact: [error]");
        }
        System.out.println();

        for (BigDecimal divisor : divisors) {
            analyzeDivisor(dividend, divisor);
        }

        System.out.println("\n" + "=".repeat(100));
        System.out.println("快速路径检查");
        System.out.println("=".repeat(100));

        for (BigDecimal divisor : divisors) {
            checkFastPath(dividend, divisor);
        }
    }

    private static long getIntCompact(BigDecimal bd) throws Exception {
        Field field = BigDecimal.class.getDeclaredField("intCompact");
        field.setAccessible(true);
        return field.getLong(bd);
    }

    private static void analyzeDivisor(BigDecimal dividend, BigDecimal divisor) {
        System.out.println("除数: " + divisor);
        System.out.println("  scale: " + divisor.scale());
        System.out.println("  precision: " + divisor.precision());
        try {
            System.out.println("  intCompact: " + getIntCompact(divisor));
        } catch (Exception e) {
            System.out.println("  intCompact: [error]");
        }

        int scaleDiff = dividend.scale() - divisor.scale();
        System.out.println("  scaleDiff (dividend - divisor): " + scaleDiff);

        // 计算 100 ÷ divisor
        BigDecimal result = dividend.divide(divisor, 10, RoundingMode.HALF_UP);
        System.out.println("  100 ÷ " + divisor + " = " + result);
        System.out.println();
    }

    private static void checkFastPath(BigDecimal dividend, BigDecimal divisor) {
        System.out.printf("100 ÷ %-10s: ", divisor);

        try {
            long dividendCompact = getIntCompact(dividend);
            long divisorCompact = getIntCompact(divisor);
            int dividendScale = dividend.scale();
            int divisorScale = divisor.scale();
            int scaleDiff = dividendScale - divisorScale;

            // 检查快速路径条件
            boolean compact = dividendCompact != INFLATED_VALUE && divisorCompact != INFLATED_VALUE;
            boolean smallDivisor = Math.abs(divisorCompact) < 100_000L;
            boolean smallDividend = Math.abs(dividendCompact) < 10_000_000_000_000_000L;
            boolean smallScaleDiff = Math.abs(scaleDiff) <= 4;

            boolean wouldUseFastPath = compact && smallDivisor && smallDividend && smallScaleDiff;

            if (!compact) {
                System.out.println("✗ 不满足快速路径（使用 BigInteger）");
            } else if (!smallDivisor) {
                System.out.printf("✗ 不满足快速路径（除数 %d 太大）%n", divisorCompact);
            } else if (!smallDividend) {
                System.out.printf("✗ 不满足快速路径（被除数 %d 太大）%n", dividendCompact);
            } else if (!smallScaleDiff) {
                System.out.printf("✗ 不满足快速路径（scale 差 %d 太大）%n", scaleDiff);
            } else {
                System.out.printf("✓ 满足快速路径（scaleDiff=%d）%n", scaleDiff);
                simulateFastPath(dividendCompact, divisorCompact, scaleDiff, divisor);
            }
        } catch (Exception e) {
            System.out.println("✗ 分析失败: " + e.getMessage());
        }
    }

    private static void simulateFastPath(long dividend, long divisor, int scaleDiff, BigDecimal originalDivisor) {
        // 快速路径模拟
        long absDividend = Math.abs(dividend);
        long absDivisor = Math.abs(divisor);

        if (scaleDiff == 0) {
            long q = absDividend / absDivisor;
            long r = absDividend % absDivisor;
            System.out.printf("    同 scale: q=%d, r=%d%n", q, r);
        } else if (scaleDiff > 0) {
            // dividend 需要缩小（乘以 10^scaleDiff）
            System.out.printf("    scaleDiff > 0: 被除数需要 × 10^%d%n", scaleDiff);
        } else {
            // divisor 需要调整（乘以 10^(-scaleDiff)）
            int adjustFactor = -scaleDiff;
            long scaledDivisor = absDivisor;
            System.out.printf("    scaleDiff < 0: 除数需要 × 10^%d (原始: %d)%n", adjustFactor, scaledDivisor);

            // 检查是否会溢出
            for (int i = 0; i < adjustFactor; i++) {
                if (scaledDivisor > Long.MAX_VALUE / 10) {
                    System.out.printf("    ✗ 溢出风险（%d × 10 会超过 Long.MAX_VALUE）%n", scaledDivisor);
                    return;
                }
                scaledDivisor *= 10;
            }

            long q = absDividend / scaledDivisor;
            long r = absDividend % scaledDivisor;

            // 使用标准除法验证
            BigDecimal standardResult = new BigDecimal("100").divide(originalDivisor, 10, RoundingMode.HALF_UP);
            BigDecimal fastPathResult = BigDecimal.valueOf(q, scaleDiff);

            boolean match = standardResult.setScale(scaleDiff, RoundingMode.DOWN).equals(fastPathResult);

            System.out.printf("    调整后除数: %d, q=%d, r=%d%n", scaledDivisor, q, r);
            System.out.printf("    标准结果: %s, 快速路径: %s, 匹配: %s%n",
                    standardResult, fastPathResult, match ? "✓" : "✗");

            if (!match) {
                System.out.printf("    ⚠️  精度差异: 标准=%.10f, 快速=%.10f%n",
                        standardResult.doubleValue(), fastPathResult.doubleValue());
            }
        }
    }
}
