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
 * AVX 优化版本 - 测试性能提升潜力
 *
 * 使用 Intel AVX 指令集向量化计算
 */
public class FastBigDecimalAVX {

    private static final MethodHandle DIVIDE_SINGLE_AVX_HANDLE;
    private static final MethodHandle MULTIPLY_SINGLE_AVX_HANDLE;
    private static final MethodHandle DIVIDE_BATCH_AVX_HANDLE;
    private static final MethodHandle MULTIPLY_BATCH_AVX_HANDLE;

    private static final long SCALE_MASK = 0xFFFFFFFF00000000L;
    private static final long SIG_MASK = 0x00000000FFFFFFFFL;
    private static final int SCALE_SHIFT = 32;

    private static final boolean NATIVE_AVAILABLE;
    private static final int MAX_COMPACT_DIGITS = 18;

    static {
        MethodHandle divideAvx = null, multiplyAvx = null;
        MethodHandle divideBatch = null, multiplyBatch = null;
        boolean ok = false;

        try {
            System.loadLibrary("m_avx");

            SymbolLookup lookup = SymbolLookup.loaderLookup();
            Linker linker = Linker.nativeLinker();

            divideAvx = linker.downcallHandle(
                lookup.find("km_divide_single_avx").orElseThrow(),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT
                )
            );

            multiplyAvx = linker.downcallHandle(
                lookup.find("km_multiply_single_avx").orElseThrow(),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT
                )
            );

            // 批处理方法
            divideBatch = linker.downcallHandle(
                lookup.find("km_divide_batch_avx").orElseThrow(),
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
                )
            );

            multiplyBatch = linker.downcallHandle(
                lookup.find("km_multiply_batch_avx").orElseThrow(),
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
                )
            );

            ok = true;
        } catch (Throwable t) {
            System.err.println("AVX 库不可用: " + t.getMessage());
        }

        DIVIDE_SINGLE_AVX_HANDLE = divideAvx;
        MULTIPLY_SINGLE_AVX_HANDLE = multiplyAvx;
        DIVIDE_BATCH_AVX_HANDLE = divideBatch;
        MULTIPLY_BATCH_AVX_HANDLE = multiplyBatch;
        NATIVE_AVAILABLE = ok;
    }

    /**
     * 单次除法 - AVX 优化
     */
    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor,
                                   int scale, RoundingMode rounding) {
        if (NATIVE_AVAILABLE && isCompactPath(dividend, divisor)) {
            try {
                long sig1 = getCompactSig(dividend);
                int scale1 = dividend.scale();
                long sig2 = getCompactSig(divisor);
                int scale2 = divisor.scale();

                long encoded = (long) DIVIDE_SINGLE_AVX_HANDLE.invokeExact(
                    sig1, scale1, sig2, scale2, scale, rounding.ordinal()
                );

                return decodeToBigDecimal(encoded);
            } catch (Throwable t) {
                // fallback
            }
        }
        return dividend.divide(divisor, scale, rounding);
    }

    /**
     * 单次乘法 - AVX 优化
     */
    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        if (NATIVE_AVAILABLE && isCompactPath(a, b)) {
            try {
                long sig1 = getCompactSig(a);
                int scale1 = a.scale();
                long sig2 = getCompactSig(b);
                int scale2 = b.scale();

                long encoded = (long) MULTIPLY_SINGLE_AVX_HANDLE.invokeExact(
                    sig1, scale1, sig2, scale2
                );

                return decodeToBigDecimal(encoded);
            } catch (Throwable t) {
                // fallback
            }
        }
        return a.multiply(b);
    }

    /**
     * 批量除法 - AVX 向量化
     *
     * @param dividends 被除数数组
     * @param divisors 除数数组
     * @param scale 目标 scale
     * @param rounding 舍入模式
     * @return 结果数组
     */
    public static BigDecimal[] divideBatch(BigDecimal[] dividends, BigDecimal[] divisors,
                                          int scale, RoundingMode rounding) {
        if (!NATIVE_AVAILABLE || dividends.length != divisors.length) {
            // Fallback to individual operations
            BigDecimal[] results = new BigDecimal[dividends.length];
            for (int i = 0; i < dividends.length; i++) {
                results[i] = dividends[i].divide(divisors[i], scale, rounding);
            }
            return results;
        }

        int count = dividends.length;
        try (Arena arena = Arena.ofConfined()) {
            // 分配 native 数组
            MemorySegment sig1Seg = arena.allocate(ValueLayout.JAVA_LONG, count);
            MemorySegment scale1Seg = arena.allocate(ValueLayout.JAVA_INT, count);
            MemorySegment sig2Seg = arena.allocate(ValueLayout.JAVA_LONG, count);
            MemorySegment scale2Seg = arena.allocate(ValueLayout.JAVA_INT, count);
            MemorySegment resultsSeg = arena.allocate(ValueLayout.JAVA_LONG, count);

            // 填充输入数据
            for (int i = 0; i < count; i++) {
                sig1Seg.set(ValueLayout.JAVA_LONG, i * 8L, getCompactSig(dividends[i]));
                scale1Seg.set(ValueLayout.JAVA_INT, i * 4L, dividends[i].scale());
                sig2Seg.set(ValueLayout.JAVA_LONG, i * 8L, getCompactSig(divisors[i]));
                scale2Seg.set(ValueLayout.JAVA_INT, i * 4L, divisors[i].scale());
            }

            // 调用批量 AVX 函数
            DIVIDE_BATCH_AVX_HANDLE.invokeExact(
                sig1Seg, scale1Seg, sig2Seg, scale2Seg,
                scale, rounding.ordinal(), resultsSeg, (long) count
            );

            // 读取结果
            BigDecimal[] results = new BigDecimal[count];
            for (int i = 0; i < count; i++) {
                long encoded = resultsSeg.get(ValueLayout.JAVA_LONG, i * 8L);
                results[i] = decodeToBigDecimal(encoded);
            }
            return results;
        } catch (Throwable t) {
            // Fallback
            BigDecimal[] results = new BigDecimal[count];
            for (int i = 0; i < count; i++) {
                results[i] = dividends[i].divide(divisors[i], scale, rounding);
            }
            return results;
        }
    }

    /**
     * 批量乘法 - AVX 向量化
     */
    public static BigDecimal[] multiplyBatch(BigDecimal[] aArray, BigDecimal[] bArray) {
        if (!NATIVE_AVAILABLE || aArray.length != bArray.length) {
            BigDecimal[] results = new BigDecimal[aArray.length];
            for (int i = 0; i < aArray.length; i++) {
                results[i] = aArray[i].multiply(bArray[i]);
            }
            return results;
        }

        int count = aArray.length;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment sig1Seg = arena.allocate(ValueLayout.JAVA_LONG, count);
            MemorySegment scale1Seg = arena.allocate(ValueLayout.JAVA_INT, count);
            MemorySegment sig2Seg = arena.allocate(ValueLayout.JAVA_LONG, count);
            MemorySegment scale2Seg = arena.allocate(ValueLayout.JAVA_INT, count);
            MemorySegment resultsSeg = arena.allocate(ValueLayout.JAVA_LONG, count);

            for (int i = 0; i < count; i++) {
                sig1Seg.set(ValueLayout.JAVA_LONG, i * 8L, getCompactSig(aArray[i]));
                scale1Seg.set(ValueLayout.JAVA_INT, i * 4L, aArray[i].scale());
                sig2Seg.set(ValueLayout.JAVA_LONG, i * 8L, getCompactSig(bArray[i]));
                scale2Seg.set(ValueLayout.JAVA_INT, i * 4L, bArray[i].scale());
            }

            MULTIPLY_BATCH_AVX_HANDLE.invokeExact(
                sig1Seg, scale1Seg, sig2Seg, scale2Seg, resultsSeg, (long) count
            );

            BigDecimal[] results = new BigDecimal[count];
            for (int i = 0; i < count; i++) {
                long encoded = resultsSeg.get(ValueLayout.JAVA_LONG, i * 8L);
                results[i] = decodeToBigDecimal(encoded);
            }
            return results;
        } catch (Throwable t) {
            BigDecimal[] results = new BigDecimal[count];
            for (int i = 0; i < count; i++) {
                results[i] = aArray[i].multiply(bArray[i]);
            }
            return results;
        }
    }

    private static BigDecimal decodeToBigDecimal(long encoded) {
        int scale = (int) (encoded >>> SCALE_SHIFT);
        long sig = decodeSig(encoded);
        return BigDecimal.valueOf(sig, scale);
    }

    private static long decodeSig(long encoded) {
        long sigEncoded = encoded & SIG_MASK;
        if ((sigEncoded & 1) == 1) {
            return -(sigEncoded >>> 1);
        } else {
            return sigEncoded >>> 1;
        }
    }

    private static boolean isCompactPath(BigDecimal... values) {
        for (BigDecimal v : values) {
            if (v.scale() < -MAX_COMPACT_DIGITS || v.scale() > MAX_COMPACT_DIGITS) {
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

    public static boolean hasAVXSupport() {
        return NATIVE_AVAILABLE;
    }
}
