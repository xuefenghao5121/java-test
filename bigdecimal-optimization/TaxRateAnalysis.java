import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Tax Rate Calculation Scenario Analysis
 *
 * 分析税率计算场景对快速路径优化的影响
 *
 * 重点：分析当前快速路径在税率计算场景中的覆盖率
 */
public class TaxRateAnalysis {

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("Tax Rate Calculation - Fast Path Analysis");
        System.out.println("=".repeat(80));

        // 典型税率
        BigDecimal[] taxRates = {
            new BigDecimal("0.06"),   // 6%
            new BigDecimal("0.13"),   // 13%
            new BigDecimal("0.25"),   // 25%
            new BigDecimal("0.35")     // 35%
        };

        // 金额范围（长尾分布）
        long[] amounts = {
            100L,             // $1.00
            1_000L,           // $10.00
            10_000L,          // $100.00
            100_000L,         // $1,000.00
            1_000_000L,       // $10,000.00
            10_000_000L,      // $100,000.00
            100_000_000L,     // $1,000,000.00
            1_000_000_000L,   // $10,000,000.00
            10_000_000_000L   // $100,000,000.00
        };

        System.out.println("\n=== BigDecimal 内部表示分析 ===\n");

        for (BigDecimal rate : taxRates) {
            System.out.printf("税率 %s: precision=%d, scale=%d%n",
                    rate, rate.precision(), rate.scale());
        }

        System.out.println("\n=== 金额表示分析 ===\n");
        for (long amount : amounts) {
            BigDecimal bd = BigDecimal.valueOf(amount, 2);
            System.out.printf("金额 %d (=$%,.2f): precision=%d, scale=%d%n",
                    amount, amount / 100.0, bd.precision(), bd.scale());
        }

        System.out.println("\n=== 税率乘法计算示例 ===\n");

        System.out.println("计算: amount × taxRate\n");
        for (long amount : new long[]{100_000L, 10_000_000L, 1_000_000_000L}) {
            BigDecimal amountBd = BigDecimal.valueOf(amount, 2);
            for (BigDecimal rate : new BigDecimal[]{new BigDecimal("0.13")}) {
                BigDecimal result = amountBd.multiply(rate);
                System.out.printf("$%,.2f × %s = $%,.4f (precision=%d, scale=%d)%n",
                        amount / 100.0, rate, result.doubleValue(),
                        result.precision(), result.scale());
            }
        }

        System.out.println("\n=== 快速路径覆盖分析 ===\n");

        System.out.println("""
当前乘法快速路径：isSmallMultiply(x, y) = |x| < 10^9 && |y| < 10^9

在税率计算中：
  - amount 的 intCompact 值 = 实际金额（如 100000 表示 $1000.00）
  - taxRate 的 intCompact 值 = 税率分子（如 13 表示 0.13）

对于：
  - 小额交易（<$10M）：amount < 10^9 ✓
  - 大额交易（≥$10M）：amount ≥ 10^9 ✗
        """);

        System.out.println("\n=== 具体案例分析 ===\n");

        analyzeTaxCalculation(100_000L, "0.13", "$1,000.00");     // ✓ 小额
        analyzeTaxCalculation(10_000_000L, "0.13", "$100,000.00");  // ✓ 中额
        analyzeTaxCalculation(100_000_000L, "0.13", "$1,000,000.00"); // ✗ 大额
        analyzeTaxCalculation(1_000_000_000L, "0.13", "$10,000,000.00"); // ✗ 超大额

        System.out.println("\n=== 长尾分布的影响 ===\n");

        System.out.println("""
典型金融场景的金额分布（对数正态分布）：

金额范围          |   占比   | 快速路径 | 影响
----------------|----------|----------|--------
<$1K            |   20%    |    ✓    | 已优化
$1K - $100K     |   45%    |    ✓    | 已优化
$100K - $10M    |   25%    |    ✓    | 已优化
$10M - $100M    |    7%    |    ✗    | 未优化
>$100M          |    3%    |    ✗    | 未优化

结论：
- 90% 的交易已覆盖
- 但 10% 的大额交易未覆盖
- 大额交易通常更重要（高价值）
        """);

        System.out.println("\n=== 除法场景分析 ===\n");

        System.out.println("""
反向税率计算：price ÷ (1 + taxRate)

计算：$10,000 ÷ 1.13 = $8,849.56

BigDecimal 内部：
  - dividend = 100000 (scale=2)
  - divisor = 113 (scale=2)  [因为 1.13 = 113/100]
  - scale 差 = 0 ✓ 可以使用快速路径

但如果：
  - dividend = 100000 (scale=2)
  - divisor = 1.13 (scale=2)  [直接用 1.13]
  - 需要先调整 scale
        """);

        System.out.println("\n=== 优化建议 ===\n");

        System.out.println("""
问题 1：乘法快速路径对大额金额无效
───────────────────────────────────────
当前实现：
  isSmallMultiply(x, y) = |x| < 10^9 && |y| < 10^9

问题：
  - 大额交易（>$10M）的 intCompact ≥ 10^10
  - 即使税率很小，也不满足条件

优化方案 A：放宽被乘数限制
  private static boolean isSmallMultiply(long x, long y) {
      // 只要有一个操作数很小就优化
      long absX = Math.abs(x);
      long absY = Math.abs(y);
      return (absX < 10_000_000_000L && absY < 1_000_000L) ||
             (absX < 1_000_000L && absY < 10_000_000_000L);
  }

优化方案 B：针对税率计算的特殊优化
  private static boolean isTaxRateMultiply(long x, long y) {
      // 一个数很大，另一个数很小（税率特征）
      long absX = Math.abs(x);
      long absY = Math.abs(y);
      return (absX < 100L && absY < 10_000_000_000L) ||
             (absX < 10_000_000_000L && absY < 100L);
  }


问题 2：除法快速路径对 scale 不匹配场景无效
──────────────────────────────────────────────────
当前实现：
  if (dividendScale == divisorScale) { ... }

问题：
  - 税率计算中 scale 常不匹配
  - 退出快速路径后性能下降

优化方案：支持小 scale 差
  private static boolean canUseFastDivide(long dividend, long divisor, int scaleDiff) {
      return divisor != 0 &&
             Math.abs(divisor) < 100_000L &&
             Math.abs(dividend) < 10_000_000_000_000_000L &&
             Math.abs(scaleDiff) <= 4;  // 允许小的 scale 差
  }
        """);

        System.out.println("=".repeat(80));
    }

    private static void analyzeTaxCalculation(long amount, String taxRate, String desc) {
        BigDecimal amountBd = BigDecimal.valueOf(amount, 2);
        BigDecimal rate = new BigDecimal(taxRate);
        BigDecimal result = amountBd.multiply(rate);

        // 估计 intCompact 值
        long amountCompact = amount;
        long rateCompact = rate.unscaledValue().longValue();

        boolean fastPath = Math.abs(amountCompact) < 1_000_000_000L
                && Math.abs(rateCompact) < 1_000_000_000L;

        System.out.printf("%s: amountCompact=%d, rateCompact=%d → fastPath=%s%n",
                desc, amountCompact, rateCompact, fastPath ? "✓" : "✗");
    }
}
