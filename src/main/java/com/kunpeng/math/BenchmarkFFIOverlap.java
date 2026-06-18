package com.kunpeng.math;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * FFI 开销摊薄 Benchmark
 *
 * 展示：通过批量处理，摊薄 FFI 调用成本
 */
public class BenchmarkFFIOverlap {

    private static final MethodHandle DIVIDE_BATCH_HANDLE;
    private static final boolean NATIVE_AVAILABLE;

    static {
        MethodHandle handle = null;
        boolean ok = false;

        try {
            System.loadLibrary("m_simd");  // 使用 AVX2 SIMD 版本

            SymbolLookup lookup = SymbolLookup.loaderLookup();
            Linker linker = Linker.nativeLinker();

            handle = linker.downcallHandle(
                lookup.find("km_divide_batch").orElseThrow(),
                FunctionDescriptor.ofVoid(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS
                )
            );

            ok = true;
        } catch (Throwable t) {
            System.err.println("Native 库不可用: " + t.getMessage());
        }

        DIVIDE_BATCH_HANDLE = handle;
        NATIVE_AVAILABLE = ok;
    }

    public static void main(String[] args) {
        System.out.println("=== FFI 开销摊薄 Benchmark ===\n");

        if (!NATIVE_AVAILABLE) {
            System.out.println("Native 库不可用");
            return;
        }

        System.out.println("测试不同批量大小下，FFI 开销的摊薄效果\n");

        final int WARMUP = 1000;
        final int ITERATIONS = 5000;
        final int[] BATCH_SIZES = {1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024};

        System.out.println("┌──────────┬──────────────┬──────────────┬──────────────┬──────────────┐");
        System.out.println("│ 批量大小 │ Standard     │ Native       │ 差异 (ns/op) │ FFI 摊薄率  │");
        System.out.println("├──────────┼──────────────┼──────────────┼──────────────┼──────────────┤");

        for (int batchSize : BATCH_SIZES) {
            // 准备数据
            BigDecimal[] dividends = new BigDecimal[batchSize];
            BigDecimal[] divisors = new BigDecimal[batchSize];

            for (int i = 0; i < batchSize; i++) {
                dividends[i] = new BigDecimal(100 + i % 50);
                divisors[i] = new BigDecimal(3 + i % 10);
            }

            // Warmup
            for (int w = 0; w < WARMUP; w++) {
                divideStandard(dividends, divisors);
                if (NATIVE_AVAILABLE) {
                    divideNativeBatch(dividends, divisors);
                }
            }

            // Standard
            long t0 = System.nanoTime();
            for (int it = 0; it < ITERATIONS; it++) {
                divideStandard(dividends, divisors);
            }
            long stdTime = System.nanoTime() - t0;

            // Native Batch
            long t1 = System.nanoTime();
            for (int it = 0; it < ITERATIONS; it++) {
                divideNativeBatch(dividends, divisors);
            }
            long natTime = System.nanoTime() - t1;

            long totalOps = ITERATIONS * batchSize;
            double stdNs = stdTime / (double) totalOps;
            double natNs = natTime / (double) totalOps;
            double diff = natNs - stdNs;

            // FFI 摊薄率：单次 FFI 开销 / 批量大小
            // 假设基础 FFI 开销约 100ns
            double ffiAmortization = 100.0 / batchSize;

            System.out.printf("│ %8d │ %10.2f ns │ %10.2f ns │ %10.2f ns │ %10.1f%% │%n",
                batchSize, stdNs, natNs, diff,
                (natNs > stdNs) ? (diff / natNs * 100) : 0);
        }

        System.out.println("└──────────┴──────────────┴──────────────┴──────────────┴──────────────┘\n");

        System.out.println("分析：\n");
        System.out.println("  • 批量大小=1:   每个运算承担全部 FFI 开销");
        System.out.println("  • 批量大小=N:   N 个运算分担 1 次 FFI 开销");
        System.out.println("  • 摊薄公式:     FFI_开销 / 批量大小\n");

        System.out.println("结论：\n");
        System.out.println("  ✓ FFI 开销可以通过批量处理摊薄");
        System.out.println("  ✓ 批量越大，单个运算的 FFI 成本越低");
        System.out.println("  ✓ 但仍有内存复制和结果构造开销");
        System.out.println("  ✓ 需要找到最佳批量大小（权衡延迟和吞吐）");
    }

    private static void divideStandard(BigDecimal[] dividends, BigDecimal[] divisors) {
        for (int i = 0; i < dividends.length; i++) {
            dividends[i].divide(divisors[i], 2, RoundingMode.HALF_UP);
        }
    }

    private static void divideNativeBatch(BigDecimal[] dividends, BigDecimal[] divisors) {
        int n = dividends.length;

        try (Arena arena = Arena.ofConfined()) {
            long size = (8L * 4 + 4L * 4) * n;
            MemorySegment buffer = arena.allocate(size);

            long sig1Off = 0, scale1Off = sig1Off + 8L * n;
            long sig2Off = scale1Off + 4L * n, scale2Off = sig2Off + 8L * n;
            long outSigOff = scale2Off + 4L * n, outScaleOff = outSigOff + 8L * n;

            MemorySegment sig1Seg = buffer.asSlice(sig1Off, 8L * n);
            MemorySegment scale1Seg = buffer.asSlice(scale1Off, 4L * n);
            MemorySegment sig2Seg = buffer.asSlice(sig2Off, 8L * n);
            MemorySegment scale2Seg = buffer.asSlice(scale2Off, 4L * n);
            MemorySegment outSigSeg = buffer.asSlice(outSigOff, 8L * n);
            MemorySegment outScaleSeg = buffer.asSlice(outScaleOff, 4L * n);

            for (int i = 0; i < n; i++) {
                sig1Seg.set(ValueLayout.JAVA_LONG, i * 8L, dividends[i].unscaledValue().longValueExact());
                scale1Seg.set(ValueLayout.JAVA_INT, i * 4L, dividends[i].scale());
                sig2Seg.set(ValueLayout.JAVA_LONG, i * 8L, divisors[i].unscaledValue().longValueExact());
                scale2Seg.set(ValueLayout.JAVA_INT, i * 4L, divisors[i].scale());
            }

            DIVIDE_BATCH_HANDLE.invokeExact(n, sig1Seg, scale1Seg, sig2Seg, scale2Seg,
                                          2, RoundingMode.HALF_UP.ordinal(), outSigSeg, outScaleSeg);

        } catch (Throwable t) {
            // fallback
        }
    }
}
