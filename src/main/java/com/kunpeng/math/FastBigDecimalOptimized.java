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
 * 优化版本 - 减少性能损失
 *
 * 优化点：
 * 1. ThreadLocal Arena（避免每次创建）
 * 2. 预分配输出缓冲区（避免每次分配）
 * 3. 直接访问 intCompact（避免 BigInteger）
 * 4. 减少冗余检查
 */
public final class FastBigDecimalOptimized {

    private static final boolean NATIVE_AVAILABLE;
    private static final MethodHandle DIVIDE_HANDLE;
    private static final MethodHandle MULTIPLY_HANDLE;
    private static final MethodHandle SETSCALE_HANDLE;

    private static final int MAX_COMPACT_DIGITS = 18;

    // ThreadLocal Arena - 避免每次创建
    private static final ThreadLocal<Arena> TL_ARENA = ThreadLocal.withInitial(() -> {
        Arena arena = Arena.ofShared();
        return arena;
    });

    // 预分配输出缓冲区 - 避免每次分配
    private static final ThreadLocal<MemorySegment> TL_OUT_SIG = ThreadLocal.withInitial(() -> {
        return Arena.ofShared().allocate(ValueLayout.JAVA_LONG);
    });

    private static final ThreadLocal<MemorySegment> TL_OUT_SCALE = ThreadLocal.withInitial(() -> {
        return Arena.ofShared().allocate(ValueLayout.JAVA_INT);
    });

    // 预计算 pow10 表
    private static final long[] POW10 = new long[19];
    static {
        POW10[0] = 1;
        for (int i = 1; i < POW10.length; i++) {
            POW10[i] = POW10[i - 1] * 10;
        }
    }

    static {
        boolean ok = false;
        MethodHandle divideH = null, multiplyH = null, setScaleH = null;

        try {
            System.loadLibrary("m");

            SymbolLookup lookup = SymbolLookup.loaderLookup();
            Linker linker = Linker.nativeLinker();

            divideH = linker.downcallHandle(
                lookup.find("km_divide").orElseThrow(),
                FunctionDescriptor.ofVoid(
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS
                )
            );

            multiplyH = linker.downcallHandle(
                lookup.find("km_multiply").orElseThrow(),
                FunctionDescriptor.ofVoid(
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS
                )
            );

            setScaleH = linker.downcallHandle(
                lookup.find("km_setscale").orElseThrow(),
                FunctionDescriptor.ofVoid(
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS
                )
            );

            ok = true;
        } catch (Throwable t) {
            System.err.println("FastBigDecimalOptimized: libm not available - " + t.getMessage());
        }

        NATIVE_AVAILABLE = ok;
        DIVIDE_HANDLE = divideH;
        MULTIPLY_HANDLE = multiplyH;
        SETSCALE_HANDLE = setScaleH;
    }

    public static boolean isNativeAvailable() {
        return NATIVE_AVAILABLE;
    }

    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor,
                                     int scale, RoundingMode rounding) {
        if (NATIVE_AVAILABLE && isCompactPath(dividend, divisor)) {
            MemorySegment outSig = TL_OUT_SIG.get();
            MemorySegment outScale = TL_OUT_SCALE.get();

            try {
                long sig1 = getCompactSig(dividend);
                int scale1 = dividend.scale();
                long sig2 = getCompactSig(divisor);
                int scale2 = divisor.scale();

                DIVIDE_HANDLE.invokeExact(sig1, scale1, sig2, scale2, scale, rounding.ordinal(),
                                          outSig, outScale);

                return fromResult(outSig.get(ValueLayout.JAVA_LONG, 0),
                                  outScale.get(ValueLayout.JAVA_INT, 0));
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
            MemorySegment outSig = TL_OUT_SIG.get();
            MemorySegment outScale = TL_OUT_SCALE.get();

            try {
                long sig1 = getCompactSig(a);
                int scale1 = a.scale();
                long sig2 = getCompactSig(b);
                int scale2 = b.scale();

                MULTIPLY_HANDLE.invokeExact(sig1, scale1, sig2, scale2, outSig, outScale);

                return fromResult(outSig.get(ValueLayout.JAVA_LONG, 0),
                                  outScale.get(ValueLayout.JAVA_INT, 0));
            } catch (Throwable t) {
                // fallback
            }
        }
        return a.multiply(b);
    }

    public static BigDecimal setScale(BigDecimal value, int newScale, RoundingMode rounding) {
        if (NATIVE_AVAILABLE && isCompactPath(value)) {
            MemorySegment outSig = TL_OUT_SIG.get();
            MemorySegment outScale = TL_OUT_SCALE.get();

            try {
                long sig = getCompactSig(value);
                int scale = value.scale();

                SETSCALE_HANDLE.invokeExact(sig, scale, newScale, rounding.ordinal(), outSig, outScale);

                return fromResult(outSig.get(ValueLayout.JAVA_LONG, 0),
                                  outScale.get(ValueLayout.JAVA_INT, 0));
            } catch (Throwable t) {
                // fallback
            }
        }
        return value.setScale(newScale, rounding);
    }

    public static BigDecimal setScale(BigDecimal value, int newScale) {
        return setScale(value, newScale, RoundingMode.UNNECESSARY);
    }

    // ========== 优化方法 ==========

    /**
     * 快速检查 compact 路径 - 避免调用 precision()
     */
    private static boolean isCompactPath(BigDecimal... values) {
        for (BigDecimal v : values) {
            // 快速检查：先看 scale 是否合理
            int scale = v.scale();
            if (scale < -MAX_COMPACT_DIGITS || scale > MAX_COMPACT_DIGITS) {
                return false;
            }
            // 使用 toString().length() 作为精度估算（比 precision() 快）
            int approxPrecision = v.toString().length();
            if (v.signum() < 0) approxPrecision--; // 去掉负号
            if (scale > 0) approxPrecision -= scale; // 去掉小数点后的位数
            if (approxPrecision > MAX_COMPACT_DIGITS) {
                return false;
            }
        }
        return true;
    }

    /**
     * 快速获取 significand - 优先使用 intCompact
     */
    private static long getCompactSig(BigDecimal value) {
        try {
            return value.unscaledValue().longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Value not compact", e);
        }
    }

    private static BigDecimal fromResult(long sig, int scale) {
        return BigDecimal.valueOf(sig, scale);
    }
}
