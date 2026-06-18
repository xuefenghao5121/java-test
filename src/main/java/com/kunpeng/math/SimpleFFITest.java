package com.kunpeng.math;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * 简单 FFI 性能测试 - 使用最简单的函数避免复杂性
 *
 * 目的：测量纯粹的 FFI 调用开销，不涉及 struct、Arena 等
 */
public class SimpleFFITest {

    private static final MethodHandle IDENTITY_HANDLE;
    private static final MethodHandle ADD_HANDLE;
    private static final MethodHandle DIVIDE_HANDLE;
    private static final boolean NATIVE_AVAILABLE;

    static {
        MethodHandle identityH = null, addH = null, divideH = null;
        boolean ok = false;

        try {
            System.loadLibrary("simple_ffi");

            SymbolLookup lookup = SymbolLookup.loaderLookup();
            Linker linker = Linker.nativeLinker();

            identityH = linker.downcallHandle(
                lookup.find("simple_identity").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
            );

            addH = linker.downcallHandle(
                lookup.find("simple_add").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
            );

            divideH = linker.downcallHandle(
                lookup.find("simple_divide").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
            );

            ok = true;
        } catch (Throwable t) {
            System.err.println("Native 库不可用: " + t.getMessage());
        }

        IDENTITY_HANDLE = identityH;
        ADD_HANDLE = addH;
        DIVIDE_HANDLE = divideH;
        NATIVE_AVAILABLE = ok;
    }

    public static void main(String[] args) throws Throwable {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║            简单 FFI 性能测试（纯函数调用）                ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        if (!NATIVE_AVAILABLE) {
            System.out.println("Native 库不可用");
            return;
        }

        System.out.println("测试目标：测量纯粹的 FFI 调用开销\n");

        final int WARMUP = 100000;
        final int ITERATIONS = 10000000;

        // 测试 1：identity 函数
        System.out.println("┌─ 测试 1：identity(x) ───────────────────────────────────┐");
        testFFI("identity", IDENTITY_HANDLE, 1000L, WARMUP, ITERATIONS);

        // 测试 2：add 函数
        System.out.println("┌─ 测试 2：add(a, b) ──────────────────────────────────────┐");
        testFFI("add", ADD_HANDLE, 1000L, 2000L, WARMUP, ITERATIONS);

        // 测试 3：divide 函数
        System.out.println("┌─ 测试 3：divide(a, b) ───────────────────────────────────┐");
        testFFI("divide", DIVIDE_HANDLE, 1000L, 3L, WARMUP, ITERATIONS);

        // 对比 Java 方法调用
        System.out.println("┌─ 测试 4：Java 方法调用对比 ──────────────────────────────┐");
        testJavaMethod(WARMUP, ITERATIONS);

        System.out.println("\n═══════════════════════════════════════════════════════════════\n");

        System.out.println("结论分析：\n");
        System.out.println("  如果 FFI 开销 ~10-20ns：正常，FFI 有少量开销");
        System.out.println("  如果 FFI 开销 ~100-200ns：偏高，可能有优化空间");
        System.out.println("  如果 FFI 开销 >500ns：异常，需要进一步调查\n");
    }

    private static void testFFI(String name, MethodHandle handle, long arg1, int warmup, int iterations) throws Throwable {
        // Warmup
        for (int i = 0; i < warmup; i++) {
            long result = (long) handle.invokeExact(arg1);
        }

        // 测试
        long t0 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            long result = (long) handle.invokeExact(arg1);
        }
        long elapsed = System.nanoTime() - t0;

        double nsPerOp = elapsed / (double) iterations;
        System.out.printf("  %s: %.2f ns/op (%d 次迭代)%n", name, nsPerOp, iterations);
        System.out.println();
    }

    private static void testFFI(String name, MethodHandle handle, long arg1, long arg2, int warmup, int iterations) throws Throwable {
        // Warmup
        for (int i = 0; i < warmup; i++) {
            long result = (long) handle.invokeExact(arg1, arg2);
        }

        // 测试
        long t0 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            long result = (long) handle.invokeExact(arg1, arg2);
        }
        long elapsed = System.nanoTime() - t0;

        double nsPerOp = elapsed / (double) iterations;
        System.out.printf("  %s: %.2f ns/op (%d 次迭代)%n", name, nsPerOp, iterations);
        System.out.println();
    }

    private static void testJavaMethod(int warmup, int iterations) {
        // Warmup
        for (int i = 0; i < warmup; i++) {
            javaIdentity(1000 + i);
        }

        // 测试
        long t0 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            javaIdentity(1000 + i);
        }
        long elapsed = System.nanoTime() - t0;

        double nsPerOp = elapsed / (double) iterations;
        System.out.printf("  Java identity(): %.2f ns/op%n", nsPerOp);
        System.out.println();
    }

    private static long javaIdentity(long x) {
        return x;
    }
}
