import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 测试特定除法场景的精度问题
 */
public class DividePrecisionTest {

    // 测试的除数
    private static final BigDecimal[] DIVISORS = {
        new BigDecimal("0.001"),
        new BigDecimal("0.01"),
        new BigDecimal("1.2345"),
        new BigDecimal("99.999"),
        new BigDecimal("999.99")
    };

    // 被除数
    private static final BigDecimal DIVIDEND = new BigDecimal("100");

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("除法精度测试 - 100 ÷ divisor");
        System.out.println("=".repeat(80));
        System.out.println("JDK: " + System.getProperty("java.version"));
        System.out.println();

        int scale = 10;
        RoundingMode rounding = RoundingMode.HALF_UP;

        System.out.println("测试参数：scale=" + scale + ", rounding=" + rounding);
        System.out.println("-".repeat(80));

        for (BigDecimal divisor : DIVISORS) {
            testDivide(DIVIDEND, divisor, scale, rounding);
        }

        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("额外测试：不同 scale 和 rounding mode");
        System.out.println("=".repeat(80));

        // 测试 100 ÷ 0.001 的不同参数组合
        BigDecimal smallDivisor = new BigDecimal("0.001");
        for (int s : new int[]{2, 4, 10, 20}) {
            for (RoundingMode rm : new RoundingMode[]{RoundingMode.DOWN, RoundingMode.HALF_UP, RoundingMode.UP}) {
                testDivide(DIVIDEND, smallDivisor, s, rm);
            }
        }
    }

    private static void testDivide(BigDecimal dividend, BigDecimal divisor, int scale, RoundingMode rounding) {
        // 使用标准 divide
        BigDecimal result = dividend.divide(divisor, scale, rounding);

        // 计算预期值（使用高精度）
        java.math.MathContext mc128 = new java.math.MathContext(34, RoundingMode.HALF_UP);
        BigDecimal expected = dividend.divide(divisor, mc128);
        // 然后四舍五入到指定 scale
        expected = expected.setScale(scale, rounding);

        // 验证精度
        boolean correct = result.equals(expected);
        double difference = result.subtract(expected).abs().doubleValue();

        System.out.printf("100 ÷ %-10s = %-25s | 预期: %-25s | %s | 差异: %.2e%n",
                divisor + "   ",
                result,
                expected,
                correct ? "✓ 正确" : "✗ 错误",
                difference);

        if (!correct) {
            System.out.printf("    >>> 精度问题！结果: %s, 预期: %s%n", result, expected);
        }
    }

}
