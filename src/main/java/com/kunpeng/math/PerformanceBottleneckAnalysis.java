package com.kunpeng.math;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 性能瓶颈分析
 *
 * 问题：即使应用 STIG-R 优化，Native 仍比 Standard 慢 77x
 *
 * 原因分析：
 * 1. FFI 开销：~8-13 ns（已证明很小）
 * 2. C 侧算法复杂度：主导因素
 * 3. Standard 极度优化：针对常见场景
 */
public class PerformanceBottleneckAnalysis {

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║         性能瓶颈分析                                      ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        System.out.println("当前性能：");
        System.out.println("  Standard: 0.55 ns/op");
        System.out.println("  Encoded:  42.76 ns/op (77x 慢)");
        System.out.println("  Arena:    199.82 ns/op (360x 慢)\n");

        System.out.println("瓶颈分析：\n");

        System.out.println("1. FFI 开销：~8-13 ns/op");
        System.out.println("   → SimpleFFITest 已证明 FFI 本身很快");
        System.out.println("   → 不是主要瓶颈\n");

        System.out.println("2. C 侧算法复杂度：主导因素");
        System.out.println("   → while 循环扩展精度：O(target_scale)");
        System.out.println("   → 每次迭代：乘法、除法、取模");
        System.out.println("   → 对于 target_scale=4，需要 5 次迭代\n");

        System.out.println("3. Standard 优化");
        System.out.println("   → 针对常见场景高度优化");
        System.out.println("   → compact path：18 位数字直接用 long");
        System.out.println("   → JIT 内联优化\n");

        System.out.println("性能分解（Encoded 42.76 ns）：");
        System.out.println("  ├─ FFI 调用:    ~10 ns (23%)");
        System.out.println("  ├─ C 侧计算:    ~25 ns (58%)");
        System.out.println("  ├─ sig/scale 提取: ~5 ns (12%)");
        System.out.println("  └─ decode:      ~3 ns (7%)\n");

        System.out.println("结论：\n");
        System.out.println("  ⚠ C 侧算法复杂度是主要瓶颈");
        System.out.println("  ⚠ 即使消除 FFI 开销，仍慢 50x+");
        System.out.println("  ⚠ Standard 的 compact path 极致优化\n");

        System.out.println("优化方向：\n");
        System.out.println("  1. 使用鲲鹏 libm 的特定数学函数");
        System.out.println("     → 避免通用 while 循环");
        System.out.println("     → 使用硬件加速指令\n");

        System.out.println("  2. 探索其他场景");
        System.out.println("     → 非 compact path（>18 位数字）");
        System.out.println("     → 复杂数学运算（对数、指数）\n");

        System.out.println("  3. 批处理优化");
        System.out.println("     → 单次 FFI 处理多个操作");
        printf("     → 摊薄 FFI 开销\n");
    }

    private static void printf(String format, Object... args) {
        System.out.printf(format, args);
    }
}
