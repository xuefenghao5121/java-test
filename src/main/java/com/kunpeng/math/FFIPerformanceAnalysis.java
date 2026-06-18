package com.kunpeng.math;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * FFI 性能分析 - 仔细研究 FFI 调用开销
 *
 * 目的：验证 FFI 调用是否真的需要 800+ ns，还是测试方法有问题
 */
public class FFIPerformanceAnalysis {

    private static final MethodHandle DIVIDE_STRUCT_HANDLE;
    private static final boolean NATIVE_AVAILABLE;

    private static final GroupLayout RESULT_LAYOUT;
    private static final long SIG_OFFSET = 0;
    private static final long SCALE_OFFSET = 8;

    static {
        GroupLayout resultLayout = MemoryLayout.structLayout(
            ValueLayout.JAVA_LONG.withName("sig"),
            ValueLayout.JAVA_INT.withName("scale"),
            MemoryLayout.paddingLayout(4)
        );
        RESULT_LAYOUT = resultLayout;

        MethodHandle handle = null;
        boolean ok = false;

        try {
            System.loadLibrary("m_zero_copy");

            SymbolLookup lookup = SymbolLookup.loaderLookup();
            Linker linker = Linker.nativeLinker();

            handle = linker.downcallHandle(
                lookup.find("km_divide_struct").orElseThrow(),
                FunctionDescriptor.of(
                    RESULT_LAYOUT,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT
                )
            );

            ok = true;
        } catch (Throwable t) {
            System.err.println("Native 库不可用: " + t.getMessage());
            t.printStackTrace();
        }

        DIVIDE_STRUCT_HANDLE = handle;
        NATIVE_AVAILABLE = ok;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║              FFI 性能深度分析                              ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        if (!NATIVE_AVAILABLE) {
            System.out.println("Native 库不可用，退出");
            return;
        }

        // 测试 1：检查符号查找是否只执行一次
        testSymbolLookup();

        // 测试 2：不同 warmup 次数的影响
        testWarmupEffect();

        // 测试 3：JIT 编译前后的性能
        testJITEffect();

        // 测试 4：FFI vs Java 方法调用
        testJavaMethodCall();

        // 测试 5：对比 C 原生性能
        compareWithCNative();
    }

    /**
     * 测试 1：符号查找是否只执行一次
     */
    private static void testSymbolLookup() {
        System.out.println("┌─ 测试 1：符号查找是否只执行一次 ─────────────────────────┐\n");

        // SymbolLookup 在 static 块中只执行一次
        // 验证：多次调用性能是否一致

        final int ITERATIONS = 1000000;

        // 第一次调用
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            invokeDivideStruct(1000 + i, 0, 3, 0, 2, 4);
        }
        long firstCall = System.nanoTime() - t0;

        // 第二次调用
        long t1 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            invokeDivideStruct(1000 + i, 0, 3, 0, 2, 4);
        }
        long secondCall = System.nanoTime() - t1;

        System.out.println("  第一次调用: " + (firstCall / ITERATIONS) + " ns/op");
        System.out.println("  第二次调用: " + (secondCall / ITERATIONS) + " ns/op");
        System.out.println("  差异: " + (Math.abs(firstCall - secondCall) * 100.0 / firstCall) + "%\n");

        if (firstCall > secondCall * 2) {
            System.out.println("  ⚠ 第一次调用明显更慢，可能有初始化开销");
        } else {
            System.out.println("  ✓ 两次调用性能接近，符号查找只执行一次");
        }
        System.out.println();
    }

    /**
     * 测试 2：Warmup 效果
     */
    private static void testWarmupEffect() {
        System.out.println("┌─ 测试 2：不同 Warmup 次数的影响 ───────────────────────────┐\n");

        final int ITERATIONS = 1000000;
        final int[] WARMUP_SIZES = {0, 100, 1000, 10000, 100000};

        System.out.println("  Warmup 次数 → 单次开销 (ns/op)");
        System.out.println("  ─────────────────────────────────");

        for (int warmup : WARMUP_SIZES) {
            // Warmup
            for (int i = 0; i < warmup; i++) {
                invokeDivideStruct(1000 + i, 0, 3, 0, 2, 4);
            }

            // 测试
            long t0 = System.nanoTime();
            for (int i = 0; i < ITERATIONS; i++) {
                invokeDivideStruct(1000 + i, 0, 3, 0, 2, 4);
            }
            long elapsed = System.nanoTime() - t0;

            System.out.printf("  %,10d → %8.2f%n", warmup, elapsed / (double) ITERATIONS);
        }
        System.out.println();
    }

    /**
     * 测试 3：JIT 编译效果
     */
    private static void testJITEffect() {
        System.out.println("┌─ 测试 3：JIT 编译前后的性能 ───────────────────────────────┐\n");

        final int ITERATIONS = 1000000;

        // 多轮测试，观察 JIT 效果
        System.out.println("  轮次 → 单次开销 (ns/op)");
        System.out.println("  ─────────────────────────");

        for (int round = 1; round <= 10; round++) {
            long t0 = System.nanoTime();
            for (int i = 0; i < ITERATIONS; i++) {
                invokeDivideStruct(1000 + i, 0, 3, 0, 2, 4);
            }
            long elapsed = System.nanoTime() - t0;

            System.out.printf("  %4d → %8.2f", round, elapsed / (double) ITERATIONS);

            // 标记稳定点
            if (round > 1) {
                double change = Math.abs(elapsed - getLastElapsed()) * 100.0 / getLastElapsed();
                if (change < 5) {
                    System.out.print(" (稳定)");
                }
            }
            System.out.println();

            setLastElapsed(elapsed);
        }
        System.out.println();
    }

    /**
     * 测试 4：Java 方法调用开销
     */
    private static void testJavaMethodCall() {
        System.out.println("┌─ 测试 4：Java 方法调用开销 ─────────────────────────────────┐\n");

        final int ITERATIONS = 10000000;

        // 空方法调用
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            emptyMethod();
        }
        long emptyCall = System.nanoTime() - t0;

        // 带参数的方法调用
        t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            methodWithParams(1000 + i, 3);
        }
        long paramsCall = System.nanoTime() - t0;

        // 返回 long 的方法
        t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            long result = methodReturnsLong(1000 + i, 3);
        }
        long returnsCall = System.nanoTime() - t0;

        System.out.printf("  空方法调用:     %.2f ns/op%n", emptyCall / (double) ITERATIONS);
        System.out.printf("  带参数调用:     %.2f ns/op%n", paramsCall / (double) ITERATIONS);
        System.out.printf("  返回值调用:     %.2f ns/op%n", returnsCall / (double) ITERATIONS);
        System.out.println();
    }

    /**
     * 测试 5：与 C 原生对比
     */
    private static void compareWithCNative() throws Exception {
        System.out.println("┌─ 测试 5：与 C 原生性能对比 ─────────────────────────────────┐\n");

        final int WARMUP = 100000;
        final int ITERATIONS = 10000000;

        // FFI 调用
        for (int i = 0; i < WARMUP; i++) {
            invokeDivideStruct(1000 + i, 0, 3, 0, 2, 4);
        }

        long t0 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            invokeDivideStruct(1000 + i, 0, 3, 0, 2, 4);
        }
        long ffiTime = System.nanoTime() - t0;

        // C 原生性能（之前测试结果）
        double cNativeTime = 3.77;  // ns/op

        System.out.println("  C 原生:   ~3.77 ns/op");
        System.out.printf("  FFI:      %.2f ns/op%n", ffiTime / (double) ITERATIONS);
        System.out.printf("  比率:     %.1fx%n\n", ffiTime / ITERATIONS / cNativeTime);

        System.out.println("分析：");
        if (ffiTime / ITERATIONS > 100) {
            System.out.println("  ⚠ FFI 开销异常高，需要进一步调查");
            System.out.println("  可能原因：");
            System.out.println("    1. Struct 返回值处理开销");
            System.out.println("    2. Linker.downcallHandle 的开销");
            System.out.println("    3. MemorySegment 创建/访问开销");
            System.out.println("    4. 安全检查开销");
        } else if (ffiTime / ITERATIONS > 10) {
            System.out.println("  ⚠ FFI 有一定开销，但可以接受");
        } else {
            System.out.println("  ✓ FFI 接近原生性能");
        }
        System.out.println();
    }

    // ========== 辅助方法 ==========

    private static MemorySegment invokeDivideStruct(long sig1, int scale1, long sig2,
                                                    int scale2, int targetScale, int rounding) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment result = (MemorySegment) DIVIDE_STRUCT_HANDLE.invokeExact(
                (SegmentAllocator) arena, sig1, scale1, sig2, scale2, targetScale, rounding
            );
            // 复制结果到新分配的内存（Arena 会关闭）
            long sig = result.get(ValueLayout.JAVA_LONG, SIG_OFFSET);
            int scale = result.get(ValueLayout.JAVA_INT, SCALE_OFFSET);
            return arena.allocate(ValueLayout.JAVA_LONG).set(ValueLayout.JAVA_LONG, 0, sig);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static java.lang.foreign.SegmentAllocator;

    private static void emptyMethod() {
        // 空
    }

    private static void methodWithParams(long a, long b) {
        // 不使用参数，防止优化
    }

    private static long methodReturnsLong(long a, long b) {
        return a / b;
    }

    private static long lastElapsed = 0;

    private static long getLastElapsed() {
        return lastElapsed;
    }

    private static void setLastElapsed(long value) {
        lastElapsed = value;
    }
}
