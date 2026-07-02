import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 分析除法快速路径中的 createDivideResult 精度问题
 *
 * 问题：createDivideResult 使用 setScale 从 intermediateScale 调整到 targetScale
 * 这可能导致精度损失或不正确的结果
 */
public class DivideFastPathAnalysis {

    public static void main(String[] args) {
        System.out.println("除法快速路径 createDivideResult 精度分析");
        System.out.println("===========================================\n");

        // 核心问题：当 intermediateScale > targetScale 时
        // setScale(targetScale, roundingMode) 会截断数字
        // 但这不等价于正确的除法舍入！

        System.out.println("--- 问题演示：setScale 截断 vs 正确舍入 ---\n");

        problemDemo1();
        problemDemo2();
        problemDemo3();

        System.out.println("\n--- 分析用户报告的具体场景 ---\n");
        analyzeUserCases();

        System.out.println("\n--- 结论 ---");
        System.out.println("createDivideResult 使用 setScale 可能导致精度问题！");
        System.out.println("正确做法：应该在除法阶段就正确舍入，而不是事后调整 scale。");
    }

    /**
     * 问题 1：setScale 截断数字，不考虑实际除法的余数
     */
    private static void problemDemo1() {
        System.out.println("【问题 1】setScale 截断 vs 正确保留小数");
        System.out.println();

        // 假设计算 100 ÷ 99.999，目标是 8 位小数
        BigDecimal dividend = new BigDecimal("100");
        BigDecimal divisor = new BigDecimal("99.999");

        // 标准路径的正确结果
        BigDecimal standardResult = dividend.divide(divisor, 8, RoundingMode.HALF_UP);
        System.out.println("标准路径结果: " + standardResult);

        // 模拟快速路径的问题
        // 快速路径计算：100000 / 99999 = 1 (余数 1)
        // 但实际应该是 1.00001000...

        long q = 100000 / 99999;  // = 1
        long r = 100000 % 99999;  // = 1

        System.out.println("快速路径 q = " + q + ", r = " + r);

        // 问题：快速路径没有正确处理余数导致的小数部分！
        // 它会返回 1.00000000 而不是 1.00001000
    }

    /**
     * 问题 2：当 intermediateScale > targetScale 时丢失信息
     */
    private static void problemDemo2() {
        System.out.println("\n【问题 2】intermediateScale > targetScale 时信息丢失");

        // 模拟情况 3（scaleDiff < 0）的计算
        // 100 ÷ 0.001，目标 scale = 2
        // 快速路径会：
        //   1. scaledDividend = 100 * 1000 = 100000
        //   2. q = 100000 / 1 = 100000
        //   3. intermediateScale = 2 + 3 = 5
        //   4. valueOf(100000, 5) = 100000.00000
        //   5. setScale(2) = 100000.00 ✓ 正确

        // 但考虑更复杂的情况：
        // 计算过程中有小数部分时，问题会暴露

        BigDecimal value = new BigDecimal("123.45678");
        BigDecimal afterSetScale = value.setScale(2, RoundingMode.HALF_UP);
        System.out.println("原始值: " + value);
        System.out.println("setScale(2): " + afterSetScale);
        System.out.println("信息丢失: " + value.subtract(afterSetScale));
    }

    /**
     * 问题 3：验证快速路径结果的正确性
     */
    private static void problemDemo3() {
        System.out.println("\n【问题 3】快速路径 vs 标准路径对比");

        String[] divisors = {"0.001", "0.01", "1.2345", "99.999", "999.99"};
        BigDecimal dividend = new BigDecimal("100");

        for (String divisorStr : divisors) {
            BigDecimal divisor = new BigDecimal(divisorStr);

            // 标准路径
            BigDecimal standard = dividend.divide(divisor, 8, RoundingMode.HALF_UP);

            // 检查快速路径条件
            int scaleDiff = dividend.scale() - divisor.scale();
            long dividendCompact = dividend.unscaledValue().longValue();
            long divisorCompact = divisor.unscaledValue().longValue();

            boolean usesFastPath =
                Math.abs(divisorCompact) < 100_000L &&
                Math.abs(dividendCompact) < 10_000_000_000_000_000L &&
                Math.abs(scaleDiff) <= 4;

            System.out.printf("%s ÷ %s = %s (快速路径: %s, scaleDiff: %d)%n",
                    dividend, divisor, standard,
                    usesFastPath ? "是" : "否", scaleDiff);
        }
    }

    /**
     * 分析用户报告的具体场景
     */
    private static void analyzeUserCases() {
        String[] divisors = {"0.001", "0.01", "1.2345", "99.999", "999.99"};
        BigDecimal dividend = new BigDecimal("100");

        System.out.println("被除数: 100, 目标 scale: 8");
        System.out.println();

        for (String divisorStr : divisors) {
            BigDecimal divisor = new BigDecimal(divisorStr);

            // 获取详细信息
            int dividendScale = dividend.scale();
            int divisorScale = divisor.scale();
            int scaleDiff = dividendScale - divisorScale;

            long dividendCompact = dividend.unscaledValue().longValue();
            long divisorCompact = divisor.unscaledValue().longValue();

            // 标准结果
            BigDecimal result = dividend.divide(divisor, 8, RoundingMode.HALF_UP);

            // 模拟快速路径计算（情况 3：scaleDiff < 0）
            if (scaleDiff < 0) {
                int adjust = -scaleDiff;
                long scaledDividend = dividendCompact * (long)Math.pow(10, adjust);
                long q = scaledDividend / divisorCompact;
                long r = scaledDividend % divisorCompact;

                // 这是快速路径会返回的结果（有问题）
                int intermediateScale = 8 + adjust;
                BigDecimal fastPathWrong = new BigDecimal(q).movePointLeft(intermediateScale)
                    .setScale(8, RoundingMode.HALF_UP);

                System.out.printf("除数: %s%n", divisorStr);
                System.out.printf("  scaleDiff: %d, adjust: %d%n", scaleDiff, adjust);
                System.out.printf("  标准结果: %s%n", result);
                System.out.printf("  快速路径(有bug): %s%n", fastPathWrong);
                System.out.printf("  scaledDividend: %d, q: %d, r: %d%n", scaledDividend, q, r);

                if (fastPathWrong.compareTo(result) != 0) {
                    System.out.printf("  ⚠️ 精度差异！%.10f vs %.10f%n",
                        fastPathWrong, result);
                } else {
                    System.out.printf("  ✓ 结果一致%n");
                }
                System.out.println();
            }
        }
    }
}
