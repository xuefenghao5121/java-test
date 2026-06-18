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
 * 批量处理优化版 - 减少内存分配和复制
 */
public final class FastBigDecimalBatchOptimized {

    private static final boolean NATIVE_AVAILABLE;
    private static final MethodHandle DIVIDE_BATCH_HANDLE;

    static {
        boolean ok = false;
        MethodHandle divideH = null;

        try {
            System.loadLibrary("m");

            SymbolLookup lookup = SymbolLookup.loaderLookup();
            Linker linker = Linker.nativeLinker();

            divideH = linker.downcallHandle(
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
            System.err.println("FastBigDecimalBatchOptimized: libm not available - " + t.getMessage());
        }

        NATIVE_AVAILABLE = ok;
        DIVIDE_BATCH_HANDLE = divideH;
    }

    public static boolean isNativeAvailable() {
        return NATIVE_AVAILABLE;
    }

    public static BigDecimal[] divideBatch(
            BigDecimal[] dividends, BigDecimal[] divisors,
            int scale, RoundingMode rounding) {

        if (!NATIVE_AVAILABLE || dividends.length != divisors.length) {
            return divideStandard(dividends, divisors, scale, rounding);
        }

        int n = dividends.length;
        BigDecimal[] results = new BigDecimal[n];

        try (Arena arena = Arena.ofConfined()) {
            // 预分配所有需要的内存
            long size = (8L * 4 + 4L * 4) * n;
            MemorySegment buffer = arena.allocate(size);

            long sig1Offset = 0;
            long scale1Offset = 8L * n;
            long sig2Offset = scale1Offset + 4L * n;
            long scale2Offset = sig2Offset + 8L * n;
            long outSigOffset = scale2Offset + 4L * n;
            long outScaleOffset = outSigOffset + 8L * n;

            MemorySegment sig1Seg = buffer.asSlice(sig1Offset, 8L * n);
            MemorySegment scale1Seg = buffer.asSlice(scale1Offset, 4L * n);
            MemorySegment sig2Seg = buffer.asSlice(sig2Offset, 8L * n);
            MemorySegment scale2Seg = buffer.asSlice(scale2Offset, 4L * n);
            MemorySegment outSigSeg = buffer.asSlice(outSigOffset, 8L * n);
            MemorySegment outScaleSeg = buffer.asSlice(outScaleOffset, 4L * n);

            // 填充输入
            for (int i = 0; i < n; i++) {
                sig1Seg.set(ValueLayout.JAVA_LONG, i * 8L, dividends[i].unscaledValue().longValueExact());
                scale1Seg.set(ValueLayout.JAVA_INT, i * 4L, dividends[i].scale());
                sig2Seg.set(ValueLayout.JAVA_LONG, i * 8L, divisors[i].unscaledValue().longValueExact());
                scale2Seg.set(ValueLayout.JAVA_INT, i * 4L, divisors[i].scale());
            }

            DIVIDE_BATCH_HANDLE.invokeExact(n, sig1Seg, scale1Seg, sig2Seg, scale2Seg,
                                          scale, rounding.ordinal(), outSigSeg, outScaleSeg);

            // 读取结果
            for (int i = 0; i < n; i++) {
                long sig = outSigSeg.get(ValueLayout.JAVA_LONG, i * 8L);
                int s = outScaleSeg.get(ValueLayout.JAVA_INT, i * 4L);
                results[i] = BigDecimal.valueOf(sig, s);
            }

            return results;
        } catch (Throwable t) {
            return divideStandard(dividends, divisors, scale, rounding);
        }
    }

    private static BigDecimal[] divideStandard(
            BigDecimal[] dividends, BigDecimal[] divisors,
            int scale, RoundingMode rounding) {
        BigDecimal[] results = new BigDecimal[dividends.length];
        for (int i = 0; i < dividends.length; i++) {
            results[i] = dividends[i].divide(divisors[i], scale, rounding);
        }
        return results;
    }
}
