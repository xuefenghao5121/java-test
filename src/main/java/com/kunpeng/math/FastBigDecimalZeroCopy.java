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
 * 零拷贝优化版本 - 使用 struct 返回值
 *
 * 优化策略：
 * 1. 使用 struct 返回值代替指针参数（零内存分配）
 * 2. 批量接口摊薄 FFI 开销
 * 3. Arena 只用于批量操作
 */
public final class FastBigDecimalZeroCopy {

    private static final boolean NATIVE_AVAILABLE;
    private static final MethodHandle DIVIDE_HANDLE;
    private static final MethodHandle MULTIPLY_HANDLE;
    private static final MethodHandle SETSCALE_HANDLE;
    private static final MethodHandle DIVIDE_BATCH_HANDLE;

    private static final int MAX_COMPACT_DIGITS = 18;

    // 结果 struct layout: { long sig; int scale; }
    private static final GroupLayout RESULT_LAYOUT;
    private static final long SIG_OFFSET;
    private static final long SCALE_OFFSET;

    static {
        GroupLayout resultLayout = MemoryLayout.structLayout(
            ValueLayout.JAVA_LONG.withName("sig"),
            ValueLayout.JAVA_INT.withName("scale"),
            MemoryLayout.paddingLayout(4) // 4 bytes padding
        );
        RESULT_LAYOUT = resultLayout;
        SIG_OFFSET = 0;
        SCALE_OFFSET = 8;

        boolean ok = false;
        MethodHandle divideH = null, multiplyH = null, setScaleH = null, batchH = null;

        try {
            System.loadLibrary("m_zero_copy");

            SymbolLookup lookup = SymbolLookup.loaderLookup();
            Linker linker = Linker.nativeLinker();

            // 方案1: 使用 struct 返回值
            // C 签名: km_result_t km_divide(...) 其中 km_result_t = { long sig; int scale; }
            divideH = linker.downcallHandle(
                lookup.find("km_divide_struct").orElseThrow(),
                FunctionDescriptor.of(
                    RESULT_LAYOUT,  // 返回 struct
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,  // sig1, scale1
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,  // sig2, scale2
                    ValueLayout.JAVA_INT,  // target_scale
                    ValueLayout.JAVA_INT   // rounding
                )
            );

            multiplyH = linker.downcallHandle(
                lookup.find("km_multiply_struct").orElseThrow(),
                FunctionDescriptor.of(
                    RESULT_LAYOUT,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT
                )
            );

            setScaleH = linker.downcallHandle(
                lookup.find("km_setscale_struct").orElseThrow(),
                FunctionDescriptor.of(
                    RESULT_LAYOUT,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT
                )
            );

            // 方案2: 批量接口（保留）
            batchH = linker.downcallHandle(
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
            System.err.println("FastBigDecimalZeroCopy: " + t.getMessage());
        }

        NATIVE_AVAILABLE = ok;
        DIVIDE_HANDLE = divideH;
        MULTIPLY_HANDLE = multiplyH;
        SETSCALE_HANDLE = setScaleH;
        DIVIDE_BATCH_HANDLE = batchH;
    }

    // ========== 单次操作（零拷贝）==========

    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor,
                                     int scale, RoundingMode rounding) {
        if (NATIVE_AVAILABLE && isCompactPath(dividend, divisor)) {
            try {
                long sig1 = getCompactSig(dividend);
                int scale1 = dividend.scale();
                long sig2 = getCompactSig(divisor);
                int scale2 = divisor.scale();

                // 调用返回 struct（通过寄存器传递，零内存分配）
                MemorySegment result = (MemorySegment) DIVIDE_HANDLE.invokeExact(
                    sig1, scale1, sig2, scale2, scale, rounding.ordinal()
                );

                long resultSig = result.get(ValueLayout.JAVA_LONG, SIG_OFFSET);
                int resultScale = result.get(ValueLayout.JAVA_INT, SCALE_OFFSET);

                return BigDecimal.valueOf(resultSig, resultScale);
            } catch (Throwable t) {
                // fallback
            }
        }
        return dividend.divide(divisor, scale, rounding);
    }

    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor, RoundingMode rounding) {
        return divide(dividend, divisor, dividend.scale() - divisor.scale(), rounding);
    }

    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        return divide(dividend, divisor, dividend.scale() - divisor.scale(), RoundingMode.UNNECESSARY);
    }

    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        if (NATIVE_AVAILABLE && isCompactPath(a, b)) {
            try {
                long sig1 = getCompactSig(a);
                int scale1 = a.scale();
                long sig2 = getCompactSig(b);
                int scale2 = b.scale();

                MemorySegment result = (MemorySegment) MULTIPLY_HANDLE.invokeExact(
                    sig1, scale1, sig2, scale2
                );

                long resultSig = result.get(ValueLayout.JAVA_LONG, SIG_OFFSET);
                int resultScale = result.get(ValueLayout.JAVA_INT, SCALE_OFFSET);

                return BigDecimal.valueOf(resultSig, resultScale);
            } catch (Throwable t) {
                // fallback
            }
        }
        return a.multiply(b);
    }

    public static BigDecimal setScale(BigDecimal value, int newScale, RoundingMode rounding) {
        if (NATIVE_AVAILABLE && isCompactPath(value)) {
            try {
                long sig = getCompactSig(value);
                int scale = value.scale();

                MemorySegment result = (MemorySegment) SETSCALE_HANDLE.invokeExact(
                    sig, scale, newScale, rounding.ordinal()
                );

                long resultSig = result.get(ValueLayout.JAVA_LONG, SIG_OFFSET);
                int resultScale = result.get(ValueLayout.JAVA_INT, SCALE_OFFSET);

                return BigDecimal.valueOf(resultSig, resultScale);
            } catch (Throwable t) {
                // fallback
            }
        }
        return value.setScale(newScale, rounding);
    }

    public static BigDecimal setScale(BigDecimal value, int newScale) {
        return setScale(value, newScale, RoundingMode.UNNECESSARY);
    }

    // ========== 批量操作 ==========

    public static BigDecimal[] divideBatch(BigDecimal[] dividends, BigDecimal[] divisors,
                                            int scale, RoundingMode rounding) {
        int n = dividends.length;
        BigDecimal[] results = new BigDecimal[n];

        if (!NATIVE_AVAILABLE || n < 10) {
            for (int i = 0; i < n; i++) {
                results[i] = divide(dividends[i], divisors[i], scale, rounding);
            }
            return results;
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment sig1Seg = arena.allocate(ValueLayout.JAVA_LONG, n);
            MemorySegment scale1Seg = arena.allocate(ValueLayout.JAVA_INT, n);
            MemorySegment sig2Seg = arena.allocate(ValueLayout.JAVA_LONG, n);
            MemorySegment scale2Seg = arena.allocate(ValueLayout.JAVA_INT, n);
            MemorySegment outSigSeg = arena.allocate(ValueLayout.JAVA_LONG, n);
            MemorySegment outScaleSeg = arena.allocate(ValueLayout.JAVA_INT, n);

            for (int i = 0; i < n; i++) {
                sig1Seg.setAtIndex(ValueLayout.JAVA_LONG, i, getCompactSig(dividends[i]));
                scale1Seg.setAtIndex(ValueLayout.JAVA_INT, i, dividends[i].scale());
                sig2Seg.setAtIndex(ValueLayout.JAVA_LONG, i, getCompactSig(divisors[i]));
                scale2Seg.setAtIndex(ValueLayout.JAVA_INT, i, divisors[i].scale());
            }

            DIVIDE_BATCH_HANDLE.invokeExact(n, sig1Seg, scale1Seg, sig2Seg, scale2Seg,
                                           scale, rounding.ordinal(), outSigSeg, outScaleSeg);

            for (int i = 0; i < n; i++) {
                long sig = outSigSeg.getAtIndex(ValueLayout.JAVA_LONG, i);
                int sc = outScaleSeg.getAtIndex(ValueLayout.JAVA_INT, i);
                results[i] = BigDecimal.valueOf(sig, sc);
            }

        } catch (Throwable t) {
            for (int i = 0; i < n; i++) {
                results[i] = dividends[i].divide(divisors[i], scale, rounding);
            }
        }

        return results;
    }

    // ========== 内部方法 ==========

    private static boolean isCompactPath(BigDecimal... values) {
        for (BigDecimal v : values) {
            int scale = v.scale();
            if (scale < -MAX_COMPACT_DIGITS || scale > MAX_COMPACT_DIGITS) {
                return false;
            }
            try {
                long compact = v.unscaledValue().longValueExact();
                if (compact != 0) {
                    int digits = (int) Math.log10(Math.abs(compact)) + 1;
                    if (digits > MAX_COMPACT_DIGITS) {
                        return false;
                    }
                }
            } catch (ArithmeticException e) {
                return false;
            }
        }
        return true;
    }

    private static long getCompactSig(BigDecimal value) {
        try {
            return value.unscaledValue().longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Value not compact", e);
        }
    }

    public static boolean isNativeAvailable() {
        return NATIVE_AVAILABLE;
    }
}
