package com.kunpeng.math;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * 鲲鹏数学库 (libm) Java 21+ 对接
 *
 * <p>通过 Panama FFI 调用 ARM64 优化的数学库，加速 BigDecimal 的 divide/multiply/setScale 操作。
 *
 * <p>使用方式：
 * <pre>{@code
 * import static com.kunpeng.math.FastBigDecimal.*;
 *
 * BigDecimal result = divide(a, b, 2, RoundingMode.HALF_UP);
 * BigDecimal product = multiply(x, y);
 * BigDecimal scaled = setScale(value, 4);
 * }</pre>
 */
public final class FastBigDecimal {

    private static final boolean NATIVE_AVAILABLE;
    private static final MethodHandle DIVIDE_HANDLE;
    private static final MethodHandle MULTIPLY_HANDLE;
    private static final MethodHandle SETSCALE_HANDLE;

    /** compact 路径最大位数（18 位十进制数可放入 long） */
    private static final int MAX_COMPACT_DIGITS = 18;

    static {
        boolean ok = false;
        MethodHandle divideH = null, multiplyH = null, setScaleH = null;

        try {
            System.loadLibrary("m");  // libm.so (Kunpeng math library)

            SymbolLookup lookup = SymbolLookup.loaderLookup();
            Linker linker = Linker.nativeLinker();

            // void km_divide(int64_t sig1, int32_t scale1, int64_t sig2, int32_t scale2,
            //                int32_t target_scale, int32_t rounding,
            //                int64_t* out_sig, int32_t* out_scale)
            divideH = linker.downcallHandle(
                lookup.findOrThrow("km_divide"),
                FunctionDescriptor.ofVoid(
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS
                )
            );

            // void km_multiply(int64_t sig1, int32_t scale1, int64_t sig2, int32_t scale2,
            //                  int64_t* out_sig, int32_t* out_scale)
            multiplyH = linker.downcallHandle(
                lookup.findOrThrow("km_multiply"),
                FunctionDescriptor.ofVoid(
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS
                )
            );

            // void km_setscale(int64_t sig, int32_t scale, int32_t new_scale, int32_t rounding,
            //                   int64_t* out_sig, int32_t* out_scale)
            setScaleH = linker.downcallHandle(
                lookup.findOrThrow("km_setscale"),
                FunctionDescriptor.ofVoid(
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS
                )
            );

            ok = true;
        } catch (Throwable t) {
            System.err.println("FastBigDecimal: libm not available - " + t.getMessage());
        }

        NATIVE_AVAILABLE = ok;
        DIVIDE_HANDLE = divideH;
        MULTIPLY_HANDLE = multiplyH;
        SETSCALE_HANDLE = setScaleH;
    }

    /**
     * 检查 native 库是否可用
     */
    public static boolean isNativeAvailable() {
        return NATIVE_AVAILABLE;
    }

    // ========== divide ==========

    /**
     * {@code dividend / divisor}，指定 scale 和舍入模式
     *
     * @see BigDecimal#divide(BigDecimal, int, RoundingMode)
     */
    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor,
                                     int scale, RoundingMode rounding) {
        if (NATIVE_AVAILABLE && isCompactPath(dividend, divisor)) {
            try (Arena arena = Arena.ofConfined()) {
                var outSig = arena.allocate(ValueLayout.JAVA_LONG);
                var outScale = arena.allocate(ValueLayout.JAVA_INT);

                long sig1 = dividend.unscaledValue().longValueExact();
                int scale1 = dividend.scale();
                long sig2 = divisor.unscaledValue().longValueExact();
                int scale2 = divisor.scale();

                DIVIDE_HANDLE.invokeExact(sig1, scale1, sig2, scale2, scale, rounding.ordinal(),
                                          outSig, outScale);

                return fromResult(outSig.get(ValueLayout.JAVA_LONG, 0),
                                  outScale.get(ValueLayout.JAVA_INT, 0));
            } catch (Throwable t) {
                // fallback to standard implementation
            }
        }
        return dividend.divide(divisor, scale, rounding);
    }

    /**
     * {@code dividend / divisor}，使用舍入模式
     *
     * @see BigDecimal#divide(BigDecimal, RoundingMode)
     */
    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor, RoundingMode rounding) {
        return divide(dividend, divisor, dividend.scale() - divisor.scale(), rounding);
    }

    /**
     * {@code dividend / divisor}，精确除法
     *
     * @see BigDecimal#divide(BigDecimal)
     */
    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        return divide(dividend, divisor, dividend.scale() - divisor.scale(), RoundingMode.UNNECESSARY);
    }

    // ========== multiply ==========

    /**
     * {@code a × b}
     *
     * @see BigDecimal#multiply(BigDecimal)
     */
    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        if (NATIVE_AVAILABLE && isCompactPath(a, b)) {
            try (Arena arena = Arena.ofConfined()) {
                var outSig = arena.allocate(ValueLayout.JAVA_LONG);
                var outScale = arena.allocate(ValueLayout.JAVA_INT);

                long sig1 = a.unscaledValue().longValueExact();
                int scale1 = a.scale();
                long sig2 = b.unscaledValue().longValueExact();
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

    // ========== setScale ==========

    /**
     * 设置 scale，使用舍入模式
     *
     * @see BigDecimal#setScale(int, RoundingMode)
     */
    public static BigDecimal setScale(BigDecimal value, int newScale, RoundingMode rounding) {
        if (NATIVE_AVAILABLE && isCompactPath(value)) {
            try (Arena arena = Arena.ofConfined()) {
                var outSig = arena.allocate(ValueLayout.JAVA_LONG);
                var outScale = arena.allocate(ValueLayout.JAVA_INT);

                long sig = value.unscaledValue().longValueExact();
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

    /**
     * 设置 scale，要求精确（无舍入）
     *
     * @see BigDecimal#setScale(int)
     */
    public static BigDecimal setScale(BigDecimal value, int newScale) {
        return setScale(value, newScale, RoundingMode.UNNECESSARY);
    }

    // ========== 内部方法 ==========

    /**
     * 判断是否可以使用 fast compact 路径
     *
     * @param values 要检查的 BigDecimal
     * @return 如果所有值都在 18 位十进制数范围内，返回 true
     */
    private static boolean isCompactPath(BigDecimal... values) {
        for (BigDecimal v : values) {
            if (v.precision() > MAX_COMPACT_DIGITS) {
                return false;
            }
        }
        return true;
    }

    /**
     * 从 C 库返回的结果构造 BigDecimal
     *
     * @param sig   significand (无符号值)
     * @param scale scale
     * @return value = sig × 10^(-scale)
     */
    private static BigDecimal fromResult(long sig, int scale) {
        return BigDecimal.valueOf(sig, scale);
    }
}
