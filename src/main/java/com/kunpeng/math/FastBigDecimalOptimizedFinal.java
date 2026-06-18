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
 * 优化方案对比 - 三种避免 struct 返回值的方法
 *
 * 测试目标：找到最高效的 FFI 调用方式
 */
public class FastBigDecimalOptimizedFinal {

    // ========== 方案 1：编码返回值 ==========
    private static final MethodHandle DIVIDE_ENCODED_HANDLE;
    private static final MethodHandle MULTIPLY_ENCODED_HANDLE;
    private static final MethodHandle SETSCALE_ENCODED_HANDLE;

    // ========== 方案 2：指针参数（预分配缓冲区）==========
    private static final MethodHandle DIVIDE_PTR_HANDLE;
    private static final MethodHandle MULTIPLY_PTR_HANDLE;
    private static final MethodHandle SETSCALE_PTR_HANDLE;

    // 预分配的输出缓冲区（每个线程一个）
    // 注意：需要使用 Arena.ofShared() 确保内存生命周期
    private static final ThreadLocal<OutputBuffer> TL_BUFFER = ThreadLocal.withInitial(() -> {
        Arena arena = Arena.ofShared();
        return new OutputBuffer(
            arena.allocate(ValueLayout.JAVA_LONG),
            arena.allocate(ValueLayout.JAVA_INT)
        );
    });

    // 编码/解码常量
    private static final long SCALE_MASK = 0xFFFFFFFF00000000L;
    private static final long SIG_MASK = 0x00000000FFFFFFFFL;
    private static final int SCALE_SHIFT = 32;

    private static final boolean NATIVE_AVAILABLE;
    private static final int MAX_COMPACT_DIGITS = 18;

    static {
        MethodHandle divideEncH = null, multiplyEncH = null, setScaleEncH = null;
        MethodHandle dividePtrH = null, multiplyPtrH = null, setScalePtrH = null;
        boolean ok = false;

        try {
            System.loadLibrary("m_optimized");

            SymbolLookup lookup = SymbolLookup.loaderLookup();
            Linker linker = Linker.nativeLinker();

            // 方案 1：编码返回值
            divideEncH = linker.downcallHandle(
                lookup.find("km_divide_encoded").orElseThrow(),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT
                )
            );

            multiplyEncH = linker.downcallHandle(
                lookup.find("km_multiply_encoded").orElseThrow(),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT
                )
            );

            setScaleEncH = linker.downcallHandle(
                lookup.find("km_setscale_encoded").orElseThrow(),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT
                )
            );

            // 方案 2：指针参数
            dividePtrH = linker.downcallHandle(
                lookup.find("km_divide_ptr").orElseThrow(),
                FunctionDescriptor.ofVoid(
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS
                )
            );

            multiplyPtrH = linker.downcallHandle(
                lookup.find("km_multiply_ptr").orElseThrow(),
                FunctionDescriptor.ofVoid(
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS
                )
            );

            setScalePtrH = linker.downcallHandle(
                lookup.find("km_setscale_ptr").orElseThrow(),
                FunctionDescriptor.ofVoid(
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS
                )
            );

            ok = true;
        } catch (Throwable t) {
            System.err.println("Native 库不可用: " + t.getMessage());
        }

        DIVIDE_ENCODED_HANDLE = divideEncH;
        MULTIPLY_ENCODED_HANDLE = multiplyEncH;
        SETSCALE_ENCODED_HANDLE = setScaleEncH;
        DIVIDE_PTR_HANDLE = dividePtrH;
        MULTIPLY_PTR_HANDLE = multiplyPtrH;
        SETSCALE_PTR_HANDLE = setScalePtrH;
        NATIVE_AVAILABLE = ok;
    }

    // ========== 方案 1：编码返回值 API ==========

    public static BigDecimal divideEncoded(BigDecimal dividend, BigDecimal divisor,
                                           int scale, RoundingMode rounding) {
        if (NATIVE_AVAILABLE && isCompactPath(dividend, divisor)) {
            try {
                long sig1 = getCompactSig(dividend);
                int scale1 = dividend.scale();
                long sig2 = getCompactSig(divisor);
                int scale2 = divisor.scale();

                long encoded = (long) DIVIDE_ENCODED_HANDLE.invokeExact(
                    sig1, scale1, sig2, scale2, scale, rounding.ordinal()
                );

                return decodeToBigDecimal(encoded);
            } catch (Throwable t) {
                // fallback
            }
        }
        return dividend.divide(divisor, scale, rounding);
    }

    public static BigDecimal multiplyEncoded(BigDecimal a, BigDecimal b) {
        if (NATIVE_AVAILABLE && isCompactPath(a, b)) {
            try {
                long sig1 = getCompactSig(a);
                int scale1 = a.scale();
                long sig2 = getCompactSig(b);
                int scale2 = b.scale();

                long encoded = (long) MULTIPLY_ENCODED_HANDLE.invokeExact(
                    sig1, scale1, sig2, scale2
                );

                return decodeToBigDecimal(encoded);
            } catch (Throwable t) {
                // fallback
            }
        }
        return a.multiply(b);
    }

    // ========== 方案 2：指针参数 API ==========

    public static BigDecimal dividePtr(BigDecimal dividend, BigDecimal divisor,
                                       int scale, RoundingMode rounding) {
        if (NATIVE_AVAILABLE && isCompactPath(dividend, divisor)) {
            try {
                OutputBuffer buf = TL_BUFFER.get();
                long sig1 = getCompactSig(dividend);
                int scale1 = dividend.scale();
                long sig2 = getCompactSig(divisor);
                int scale2 = divisor.scale();

                DIVIDE_PTR_HANDLE.invokeExact(
                    sig1, scale1, sig2, scale2, scale, rounding.ordinal(),
                    buf.sigSeg, buf.scaleSeg
                );

                long sig = buf.sigSeg.get(ValueLayout.JAVA_LONG, 0);
                int sc = buf.scaleSeg.get(ValueLayout.JAVA_INT, 0);

                return BigDecimal.valueOf(sig, sc);
            } catch (Throwable t) {
                // fallback
            }
        }
        return dividend.divide(divisor, scale, rounding);
    }

    // ========== 内部方法 ==========

    private static BigDecimal decodeToBigDecimal(long encoded) {
        int scale = (int) (encoded >>> SCALE_SHIFT);
        long sig = decodeSig(encoded);
        return BigDecimal.valueOf(sig, scale);
    }

    private static long decodeSig(long encoded) {
        long sigEncoded = encoded & SIG_MASK;
        // Zig-zag 解码
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

    /**
     * 输出缓冲区 - 使用预分配的 MemorySegment
     */
    static class OutputBuffer {
        final MemorySegment sigSeg;
        final MemorySegment scaleSeg;

        OutputBuffer(MemorySegment sigSeg, MemorySegment scaleSeg) {
            this.sigSeg = sigSeg;
            this.scaleSeg = scaleSeg;
        }
    }
}
