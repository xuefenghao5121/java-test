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
 * 完全修复版本 - 解决所有资源管理问题
 *
 * 修复点：
 * 1. 移除 ThreadLocal Arena（内存泄漏风险）
 * 2. 使用 try-with-resources 自动管理
 * 3. 使用特化 ValueLayout.OfLong / OfInt
 * 4. 值传递替代指针传递（小结构体）
 * 5. 预编译 ValueLayout 实例
 */
public final class FastBigDecimalFixed {

    private static final boolean NATIVE_AVAILABLE;
    private static final MethodHandle DIVIDE_HANDLE;
    private static final MethodHandle MULTIPLY_HANDLE;
    private static final MethodHandle SETSCALE_HANDLE;

    private static final int MAX_COMPACT_DIGITS = 18;

    // 预编译 ValueLayout 实例（避免重复查找）
    private static final ValueLayout.OfLong LONG_LAYOUT = ValueLayout.JAVA_LONG;
    private static final ValueLayout.OfInt INT_LAYOUT = ValueLayout.JAVA_INT;

    // 预计算偏移量（避免运行时计算）
    private static final long LONG_SIZE = LONG_LAYOUT.byteSize();
    private static final long INT_SIZE = INT_LAYOUT.byteSize();

    static {
        boolean ok = false;
        MethodHandle divideH = null, multiplyH = null, setScaleH = null;

        try {
            System.loadLibrary("m");

            SymbolLookup lookup = SymbolLookup.loaderLookup();
            Linker linker = Linker.nativeLinker();

            // 优化设计：返回 sig (long)，scale 通过指针传递
            // C 签名: int64_t km_divide(..., int32_t* out_scale)
            divideH = linker.downcallHandle(
                lookup.find("km_divide").orElseThrow(),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_LONG,  // 返回 sig
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,   // sig1, scale1
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,   // sig2, scale2
                    ValueLayout.JAVA_INT,   // target_scale
                    ValueLayout.JAVA_INT,   // rounding
                    ValueLayout.ADDRESS      // out_scale 指针
                )
            );

            multiplyH = linker.downcallHandle(
                lookup.find("km_multiply").orElseThrow(),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_LONG,  // 返回 sig
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS      // out_scale 指针
                )
            );

            setScaleH = linker.downcallHandle(
                lookup.find("km_setscale").orElseThrow(),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_LONG,  // 返回 sig
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,   // new_scale
                    ValueLayout.JAVA_INT,   // rounding
                    ValueLayout.ADDRESS      // out_scale 指针
                )
            );

            ok = true;
        } catch (Throwable t) {
            System.err.println("FastBigDecimalFixed: libm not available - " + t.getMessage());
        }

        NATIVE_AVAILABLE = ok;
        DIVIDE_HANDLE = divideH;
        MULTIPLY_HANDLE = multiplyH;
        SETSCALE_HANDLE = setScaleH;
    }

    public static boolean isNativeAvailable() {
        return NATIVE_AVAILABLE;
    }

    /**
     * Divide - 使用值传递返回结果
     *
     * 注意：C 函数需要改为返回 long，scale 通过指针返回
     * 或者使用 struct 返回
     */
    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor,
                                     int scale, RoundingMode rounding) {
        if (NATIVE_AVAILABLE && isCompactPath(dividend, divisor)) {
            // 使用 Arena 自动管理资源
            try (Arena arena = Arena.ofConfined()) {
                // 分配输出参数（仅 scale 需要指针，sig 通过返回值）
                MemorySegment outScale = arena.allocate(INT_LAYOUT);

                long sig1 = getCompactSig(dividend);
                int scale1 = dividend.scale();
                long sig2 = getCompactSig(divisor);
                int scale2 = divisor.scale();

                // 调用：返回 sig，通过指针获取 scale
                long resultSig = (long) DIVIDE_HANDLE.invokeExact(
                    sig1, scale1, sig2, scale2, scale, rounding.ordinal(), outScale
                );
                int resultScale = outScale.get(INT_LAYOUT, 0);

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
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment outScale = arena.allocate(INT_LAYOUT);

                long sig1 = getCompactSig(a);
                int scale1 = a.scale();
                long sig2 = getCompactSig(b);
                int scale2 = b.scale();

                long resultSig = (long) MULTIPLY_HANDLE.invokeExact(
                    sig1, scale1, sig2, scale2, outScale
                );
                int resultScale = outScale.get(INT_LAYOUT, 0);

                return BigDecimal.valueOf(resultSig, resultScale);
            } catch (Throwable t) {
                // fallback
            }
        }
        return a.multiply(b);
    }

    public static BigDecimal setScale(BigDecimal value, int newScale, RoundingMode rounding) {
        if (NATIVE_AVAILABLE && isCompactPath(value)) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment outScale = arena.allocate(INT_LAYOUT);

                long sig = getCompactSig(value);
                int scale = value.scale();

                long resultSig = (long) SETSCALE_HANDLE.invokeExact(
                    sig, scale, newScale, rounding.ordinal(), outScale
                );
                int resultScale = outScale.get(INT_LAYOUT, 0);

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

    // ========== 内部方法 ==========

    /**
     * 优化的 compact 路径检查 - 避免 toString()
     */
    private static boolean isCompactPath(BigDecimal... values) {
        for (BigDecimal v : values) {
            // 快速拒绝：检查 scale 范围
            int scale = v.scale();
            if (scale < -MAX_COMPACT_DIGITS || scale > MAX_COMPACT_DIGITS) {
                return false;
            }

            // 使用 intCompact 检查（如果 available）
            try {
                long compact = v.unscaledValue().longValueExact();
                // 检查位数：log10(abs(compact)) + scale 估算
                if (compact != 0) {
                    int digits = (int) Math.log10(Math.abs(compact)) + 1;
                    if (digits > MAX_COMPACT_DIGITS) {
                        return false;
                    }
                }
            } catch (ArithmeticException e) {
                // 不是 compact 值
                return false;
            }
        }
        return true;
    }

    /**
     * 快速获取 significand
     */
    private static long getCompactSig(BigDecimal value) {
        try {
            return value.unscaledValue().longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Value not compact", e);
        }
    }
}
