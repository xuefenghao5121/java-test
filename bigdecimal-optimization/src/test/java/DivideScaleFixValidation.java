import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 验证除法快速路径 scale 计算的正确性
 *
 * 问题：Case 3 (scaleDiff < 0) 的 intermediateScale 计算错误
 */
public class DivideScaleFixValidation {

    public static void main(String[] args) {
        System.out.println("除法快速路径 Scale 修复验证");
        System.out.println("================================\n");

        // 测试用例：100 ÷ 0.001 = 100000
        testCase1();
        testCase2();
        testCase3();

        System.out.println("\n--- 根本原因分析 ---");
        explainRootCause();
    }

    /**
     * Case 1: 100 ÷ 0.001
     * dividend = 100 (scale=0)
     * divisor = 0.001 (scale=3)
     * scaleDiff = -3, adjust = 3
     */
    private static void testCase1() {
        System.out.println("【Case 1】100 ÷ 0.001");

        BigDecimal dividend = new BigDecimal("100");
        BigDecimal divisor = new BigDecimal("0.001");

        // 标准结果
        BigDecimal standard = dividend.divide(divisor, 8, RoundingMode.HALF_UP);
        System.out.println("标准结果: " + standard);

        // 模拟快速路径计算
        long dividendCompact = 100;  // 100 * 10^0
        long divisorCompact = 1;     // 1 * 10^(-3)
        int adjust = 3;
        int targetScale = 8;

        // 放大被除数：100 * 10^3 = 100000
        long scaledDividend = dividendCompact * (long)Math.pow(10, adjust);

        // 除法：100000 / 1 = 100000
        long q = scaledDividend / divisorCompact;

        System.out.println("scaledDividend: " + scaledDividend);
        System.out.println("q: " + q);

        // ========== 补丁中的错误计算 ==========
        int buggyIntermediateScale = targetScale + adjust;  // 8 + 3 = 11 ❌
        BigDecimal buggyResult = BigDecimal.valueOf(q, buggyIntermediateScale)
            .setScale(targetScale, RoundingMode.HALF_UP);
        System.out.println("❌ 补丁结果 (scale+adjust): " + buggyResult);

        // ========== 正确的计算 ==========
        int correctIntermediateScale = targetScale - adjust;  // 8 - 3 = 5 ✓
        BigDecimal correctResult = BigDecimal.valueOf(q, correctIntermediateScale)
            .setScale(targetScale, RoundingMode.HALF_UP);
        System.out.println("✓ 修复结果 (scale-adjust): " + correctResult);

        // 验证
        if (correctResult.compareTo(standard) == 0) {
            System.out.println("✓ 修复后结果正确！");
        } else {
            System.out.println("✗ 修复后仍有问题");
        }
        System.out.println();
    }

    /**
     * Case 2: 100 ÷ 1.2345
     * dividend = 100 (scale=0)
     * divisor = 1.2345 (scale=4)
     * scaleDiff = -4, adjust = 4
     */
    private static void testCase2() {
        System.out.println("【Case 2】100 ÷ 1.2345");

        BigDecimal dividend = new BigDecimal("100");
        BigDecimal divisor = new BigDecimal("1.2345");

        BigDecimal standard = dividend.divide(divisor, 8, RoundingMode.HALF_UP);
        System.out.println("标准结果: " + standard);

        long dividendCompact = 100;
        long divisorCompact = 12345;
        int adjust = 4;
        int targetScale = 8;

        long scaledDividend = dividendCompact * (long)Math.pow(10, adjust);
        long q = scaledDividend / divisorCompact;
        long r = scaledDividend % divisorCompact;

        System.out.println("scaledDividend: " + scaledDividend);
        System.out.println("q: " + q + ", r: " + r);

        // 补丁错误
        int buggyIntermediateScale = targetScale + adjust;
        BigDecimal buggyResult = BigDecimal.valueOf(q, buggyIntermediateScale)
            .setScale(targetScale, RoundingMode.HALF_UP);
        System.out.println("❌ 补丁结果: " + buggyResult);

        // 正确
        int correctIntermediateScale = targetScale - adjust;
        BigDecimal correctResult = BigDecimal.valueOf(q, correctIntermediateScale)
            .setScale(targetScale, RoundingMode.HALF_UP);
        System.out.println("✓ 修复结果: " + correctResult);
        System.out.println("标准结果: " + standard);

        if (correctResult.compareTo(standard) == 0) {
            System.out.println("✓ 修复后结果正确！");
        } else {
            System.out.println("✗ 修复后仍有问题，差异: " + standard.subtract(correctResult));
        }
        System.out.println();
    }

    /**
     * Case 3: 100 ÷ 99.999
     * dividend = 100 (scale=0)
     * divisor = 99.999 (scale=3)
     * scaleDiff = -3, adjust = 3
     */
    private static void testCase3() {
        System.out.println("【Case 3】100 ÷ 99.999");

        BigDecimal dividend = new BigDecimal("100");
        BigDecimal divisor = new BigDecimal("99.999");

        BigDecimal standard = dividend.divide(divisor, 8, RoundingMode.HALF_UP);
        System.out.println("标准结果: " + standard);

        long dividendCompact = 100;
        long divisorCompact = 99999;
        int adjust = 3;
        int targetScale = 8;

        long scaledDividend = dividendCompact * (long)Math.pow(10, adjust);
        long q = scaledDividend / divisorCompact;
        long r = scaledDividend % divisorCompact;

        System.out.println("scaledDividend: " + scaledDividend);
        System.out.println("q: " + q + ", r: " + r);

        // 正确计算
        int correctIntermediateScale = targetScale - adjust;
        BigDecimal correctResult = BigDecimal.valueOf(q, correctIntermediateScale)
            .setScale(targetScale, RoundingMode.HALF_UP);
        System.out.println("✓ 修复结果: " + correctResult);
        System.out.println("标准结果: " + standard);

        if (correctResult.compareTo(standard) == 0) {
            System.out.println("✓ 修复后结果正确！");
        } else {
            System.out.println("✗ 需要处理余数，差异: " + standard.subtract(correctResult));
        }
        System.out.println();
    }

    /**
     * 解释根本原因
     */
    private static void explainRootCause() {
        System.out.println("根本原因：");
        System.out.println();
        System.out.println("当除数小数位更多时 (scaleDiff < 0)：");
        System.out.println("1. 我们将 dividend 乘以 10^adjust 来匹配除数的位数");
        System.out.println("2. 这相当于把结果 *缩小* 了 10^adjust 倍");
        System.out.println("3. 为了补偿，intermediateScale 应该是 targetScale - adjust");
        System.out.println();
        System.out.println("补丁中错误地使用了 scale + adjust，");
        System.out.println("这导致结果被 10^-(targetScale+adjust) 缩放，完全错误！");
        System.out.println();
        System.out.println("修复方法：");
        System.out.println("  Case 2 (scaleDiff > 0): intermediateScale = scale - scaleDiff ✓ 正确");
        System.out.println("  Case 3 (scaleDiff < 0): intermediateScale = scale - adjust  ✓ 需修改");
        System.out.println("                              当前是: scale + adjust ✗ 错误");
    }
}
