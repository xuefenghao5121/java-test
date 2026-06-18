package com.kunpeng.math;

/**
 * FFI 开销摊薄演示
 *
 * 说明：为什么小请求多，FFI 无法摊薄
 */
public class FFIAmortizationDemo {

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║           FFI 开销摊薄原理演示                              ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        demonstrateAmortization();
        demonstrateMicroserviceScenario();
    }

    /**
     * 演示 1：批量大小对摊薄效果的影响
     */
    private static void demonstrateAmortization() {
        System.out.println("┌─ 演示 1：批量大小对摊薄效果的影响 ──────────────────────────┐\n");

        final long FFI_FIXED_COST = 100;  // ns
        final long CALCULATION_COST = 5;   // ns

        System.out.println("假设：");
        System.out.println("  FFI 固定开销 = 100 ns");
        System.out.println("  单次计算开销 = 5 ns\n");

        System.out.println("┌──────────────┬─────────────┬─────────────┬─────────────┐");
        System.out.println("│ 批量大小 N   │ FFI 总开销   │ 单次开销     │ 摊薄倍数    │");
        System.out.println("├──────────────┼─────────────┼─────────────┼─────────────┤");

        for (int n : new int[]{1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024}) {
            // 单次 FFI，计算 N 次
            long totalFFI = FFI_FIXED_COST;
            long totalCalc = CALCULATION_COST * n;
            long total = totalFFI + totalCalc;
            long perOp = total / n;
            double amortization = (double) (FFI_FIXED_COST + CALCULATION_COST) / perOp;

            System.out.printf("│ %12d │ %11d ns │ %11d ns │ %9.1fx │%n",
                n, totalFFI, perOp, amortization);
        }

        System.out.println("└──────────────┴─────────────┴─────────────┴─────────────┘\n");

        System.out.println("关键观察：");
        System.out.println("  • N=1:   每个操作承担全部 100ns FFI 开销");
        System.out.println("  • N=1024: 每个操作只承担 ~0.1ns FFI 开销");
        System.out.println("  • 摊薄倍数 = (单次总开销) / (批量单次开销)\n");
    }

    /**
     * 演示 2：微服务场景下的困境
     */
    private static void demonstrateMicroserviceScenario() {
        System.out.println("┌─ 演示 2：微服务场景下的困境 ──────────────────────────────┐\n");

        System.out.println("微服务场景特点：");
        System.out.println("  • 每个请求独立（来自不同用户）");
        System.out.println("  • 请求之间无法合并");
        System.out.println("  • 每个请求只需要 1-3 次计算\n");

        System.out.println("典型请求处理流程：");
        System.out.println("  ┌────────────────────────────────────────────────┐");
        System.out.println("  │ HTTP 请求到达                                   │");
        System.out.println("  │ ↓                                               │");
        System.out.println("  │ 解析参数、验证                                   │");
        System.out.println("  │ ↓                                               │");
        System.out.println("  │ 执行业务逻辑：                                   │");
        System.out.println("  │   - 计算价格    (1次 divide)                    │");
        System.out.println("  │   - 计算折扣    (1次 multiply)                  │");
        System.out.println("  │   - 计算税费    (1次 multiply)                  │");
        System.out.println("  │ ↓                                               │");
        System.out.println("  │ 返回响应                                         │");
        System.out.println("  └────────────────────────────────────────────────┘\n");

        System.out.println("问题：每个请求的计算是独立的，无法批量！\n");

        // 模拟不同场景
        simulateScenario("金融交易", 3);
        simulateScenario("电商订单", 5);
        simulateScenario("支付网关", 2);

        System.out.println("结论：");
        System.out.println("  ✓ 每个请求的计算次数有限（1-5次）");
        System.out.println("  ✓ 请求之间无法合并（不同用户、不同时间）");
        System.out.println("  ✓ FFI 开销无法摊薄（每次计算都要付出 100ns）");
        System.out.println("  ✗ Batch 接口无法使用（没有批量需求）\n");
    }

    /**
     * 模拟特定场景
     */
    private static void simulateScenario(String name, int calculations) {
        final long FFI_FIXED_COST = 100;  // ns per FFI call
        final long CALCULATION_COST = 5;   // ns per calculation
        final long STANDARD_COST = 6;      // ns (Standard BigDecimal)

        // Native 实现
        long nativeTotal = 0;
        for (int i = 0; i < calculations; i++) {
            nativeTotal += FFI_FIXED_COST + CALCULATION_COST;
        }

        // Standard 实现
        long standardTotal = calculations * STANDARD_COST;

        System.out.println("  场景: " + name);
        System.out.printf("    计算次数: %d 次%n", calculations);
        System.out.printf("    Native 总开销: %.0f ns (%.0f ns/op)%n",
            (double) nativeTotal, (double) nativeTotal / calculations);
        System.out.printf("    Standard 总开销: %.0f ns (%.0f ns/op)%n",
            (double) standardTotal, (double) STANDARD_COST);
        System.out.printf("    比率: %.1fx (", (double) nativeTotal / standardTotal);

        if (nativeTotal > standardTotal) {
            System.out.print("Native 更慢");
        } else {
            System.out.print("Native 更快");
        }
        System.out.println(")\n");
    }

    /**
     * 演示 3：什么时候 FFI 可以摊薄？
     */
    private static void demonstrateWhenFFIWorks() {
        System.out.println("┌─ 演示 3：什么时候 FFI 可以摊薄？ ────────────────────────┐\n");

        System.out.println("FFI 可以摊薄的条件：");
        System.out.println("  1. 有大量独立的计算需要执行");
        System.out.println("  2. 这些计算可以一次性提交");
        System.out.println("  3. 不需要立即返回结果给用户\n");

        System.out.println("适用场景对比：");
        System.out.println("┌────────────────────┬───────────────┬───────────────┐");
        System.out.println("│ 场景               │ 可批量？      │ FFI 有效？     │");
        System.out.println("├────────────────────┼───────────────┼───────────────┤");
        System.out.println("│ 在线交易请求       │ ✗ (独立请求)  │ ✗             │");
        System.out.println("│ 实时支付结算       │ ✗ (立即返回)  │ ✗             │");
        System.out.println("│ 批量报表生成       │ ✓ (可合并)    │ ✓             │");
        System.out.println("│ 离线数据清洗       │ ✓ (可合并)    │ ✓             │");
        System.out.println("│ 金融日终结算       │ ✓ (可合并)    │ ✓             │");
        System.out.println("│ 科学计算批量处理   │ ✓ (可合并)    │ ✓             │");
        System.out.println("└────────────────────┴───────────────┴───────────────┘\n");

        System.out.println("批量场景示例：");
        System.out.println("  ┌────────────────────────────────────────────────┐");
        System.out.println("  │ 日终结算任务                                   │");
        System.out.println("  │                                               │");
        System.out.println("  │ 输入：100万笔交易                             │");
        System.out.println("  │                                               │");
        System.out.println("  │ 处理：                                         │");
        System.out.println("  │   - 加载所有交易到内存                         │");
        System.out.println("  │   - 批量计算：divide(金额, 汇率)              │");
        System.out.println("  │   - 使用 batch API，一次 FFI 处理 1024 笔    │");
        System.out.println("  │                                               │");
        System.out.println("  │ 开销：                                         │");
        System.out.println("  │   - FFI 调用次数：1000000 / 1024 ≈ 977 次    │");
        System.out.println("  │   - 每笔交易分摊 FFI：100ns / 1024 ≈ 0.1ns   │");
        System.out.println("  └────────────────────────────────────────────────┘\n");
    }
}
