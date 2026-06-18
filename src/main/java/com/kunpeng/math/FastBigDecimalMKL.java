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
 * BigDecimal Native 加速 - Non-Compact Path 优化
 *
 * == 架构说明 ==
 *
 * BigDecimal 有两种存储路径：
 *
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │ Compact Path (≤18 位)        │ Non-Compact Path (>18 位)                   │
 * ├────────────────────────────────────┼────────────────────────────────────────┤
 * │ 存储：long intCompact              │ 存储：BigInteger intVal                   │
 * │ 性能：9-27 ns/op（JIT 优化）       │ 性能：260-400 ns/op（大整数运算）       │
 * │ 建议：使用 Standard 实现           │ 建议：使用 Native 向量化（本类）          │
 * └────────────────────────────────────┴────────────────────────────────────────┘
 *
 * == 为什么只优化 Non-Compact Path ==
 *
 * 1. Compact Path 已极致优化 — Standard 使用 long 存储，JIT 完全内联优化
 * 2. Native FFI 有固定开销 (~8-13 ns) — 无法超越 Compact Path
 * 3. Non-Compact Path 是真正的瓶颈 — BigInteger 运算慢 10-40 倍
 *
 * == 优化策略 ==
 *
 * 使用 double 向量化运算（接受精度权衡）：
 * - Intel MKL VML (AVX/AVX2/AVX-512)
 * - 标准libm (备选)
 * - 鲲鹏 libm NEON/SVE (同样适用)
 *
 * == 精度说明 ==
 *
 * - double 有 53 位尾数，可精确表示约 15-17 位十进制数字
 * - 对于 >18 位的 BigDecimal，会有精度损失
 * - 适用于可接受浮点近似的场景（科学计算、统计、金融近似）
 *
 * @author kunpeng-math
 * @since 0.1.0
 */
public class FastBigDecimalMKL {

    // ========== FFI 句柄 ==========

    private static final MethodHandle DIVIDE_BATCH_DOUBLE_HANDLE;
    private static final MethodHandle MULTIPLY_BATCH_DOUBLE_HANDLE;
    private static final MethodHandle SETSCALE_BATCH_DOUBLE_HANDLE;

    private static final long SCALE_MASK = 0xFFFFFFFF00000000L;
    private static final long SIG_MASK = 0x00000000FFFFFFFFL;
    private static final int SCALE_SHIFT = 32;

    private static final boolean NATIVE_AVAILABLE;
    private static final int MAX_COMPACT_DIGITS = 18;  // Compact Path 上限

    static {
        MethodHandle divideBatch = null, multiplyBatch = null, setScaleBatch = null;
        boolean ok = false;

        try {
            System.loadLibrary("m_mkl");

            SymbolLookup lookup = SymbolLookup.loaderLookup();
            Linker linker = Linker.nativeLinker();

            // void km_divide_batch_double(double* a, double* b, int32_t scale, uint64_t* results, int count)
            divideBatch = linker.downcallHandle(
                lookup.find("km_divide_batch_double").orElseThrow(),
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
                )
            );

            // void km_multiply_batch_double(double* a, double* b, int32_t* scale_a, int32_t* scale_b, uint64_t* results, int count)
            multiplyBatch = linker.downcallHandle(
                lookup.find("km_multiply_batch_double").orElseThrow(),
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
                )
            );

            // void km_setscale_batch_double(double* a, int32_t* old_scale, int32_t new_scale, uint64_t* results, int count)
            setScaleBatch = linker.downcallHandle(
                lookup.find("km_setscale_batch_double").orElseThrow(),
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
                )
            );

            ok = true;
        } catch (Throwable t) {
            System.err.println("Native 库不可用: " + t.getMessage());
        }

        DIVIDE_BATCH_DOUBLE_HANDLE = divideBatch;
        MULTIPLY_BATCH_DOUBLE_HANDLE = multiplyBatch;
        SETSCALE_BATCH_DOUBLE_HANDLE = setScaleBatch;
        NATIVE_AVAILABLE = ok;
    }

    // ========== Public API ==========

    /**
     * 批量除法 - Non-Compact Path 优化
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
            return fallbackBatchDivide(dividends, divisors, scale, rounding);
        }

        int count = dividends.length;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment aSeg = arena.allocate(ValueLayout.JAVA_DOUBLE, count);
            MemorySegment bSeg = arena.allocate(ValueLayout.JAVA_DOUBLE, count);
            MemorySegment resultsSeg = arena.allocate(ValueLayout.JAVA_LONG, count);

            // 转换为 double（接受精度权衡）
            for (int i = 0; i < count; i++) {
                aSeg.set(ValueLayout.JAVA_DOUBLE, i * 8L, dividends[i].doubleValue());
                bSeg.set(ValueLayout.JAVA_DOUBLE, i * 8L, divisors[i].doubleValue());
            }

            // 调用 Native 批量除法
            DIVIDE_BATCH_DOUBLE_HANDLE.invokeExact(aSeg, bSeg, scale, resultsSeg, (long) count);

            // 解码结果
            BigDecimal[] results = new BigDecimal[count];
            for (int i = 0; i < count; i++) {
                long encoded = resultsSeg.get(ValueLayout.JAVA_LONG, i * 8L);
                results[i] = decodeToBigDecimal(encoded);
            }
            return results;
        } catch (Throwable t) {
            return fallbackBatchDivide(dividends, divisors, scale, rounding);
        }
    }

    /**
     * 批量乘法 - Non-Compact Path 优化
     */
    public static BigDecimal[] multiplyBatch(BigDecimal[] aArray, BigDecimal[] bArray) {
        if (!NATIVE_AVAILABLE || aArray.length != bArray.length) {
            return fallbackBatchMultiply(aArray, bArray);
        }

        int count = aArray.length;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment aSeg = arena.allocate(ValueLayout.JAVA_DOUBLE, count);
            MemorySegment bSeg = arena.allocate(ValueLayout.JAVA_DOUBLE, count);
            MemorySegment scaleASeg = arena.allocate(ValueLayout.JAVA_INT, count);
            MemorySegment scaleBSeg = arena.allocate(ValueLayout.JAVA_INT, count);
            MemorySegment resultsSeg = arena.allocate(ValueLayout.JAVA_LONG, count);

            for (int i = 0; i < count; i++) {
                aSeg.set(ValueLayout.JAVA_DOUBLE, i * 8L, aArray[i].doubleValue());
                bSeg.set(ValueLayout.JAVA_DOUBLE, i * 8L, bArray[i].doubleValue());
                scaleASeg.set(ValueLayout.JAVA_INT, i * 4L, aArray[i].scale());
                scaleBSeg.set(ValueLayout.JAVA_INT, i * 4L, bArray[i].scale());
            }

            MULTIPLY_BATCH_DOUBLE_HANDLE.invokeExact(
                aSeg, bSeg, scaleASeg, scaleBSeg, resultsSeg, (long) count
            );

            BigDecimal[] results = new BigDecimal[count];
            for (int i = 0; i < count; i++) {
                long encoded = resultsSeg.get(ValueLayout.JAVA_LONG, i * 8L);
                results[i] = decodeToBigDecimal(encoded);
            }
            return results;
        } catch (Throwable t) {
            return fallbackBatchMultiply(aArray, bArray);
        }
    }

    /**
     * 批量 setScale - Non-Compact Path 优化
     */
    public static BigDecimal[] setScaleBatch(BigDecimal[] values, int newScale, RoundingMode rounding) {
        if (!NATIVE_AVAILABLE) {
            return fallbackBatchSetScale(values, newScale, rounding);
        }

        int count = values.length;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment aSeg = arena.allocate(ValueLayout.JAVA_DOUBLE, count);
            MemorySegment scaleASeg = arena.allocate(ValueLayout.JAVA_INT, count);
            MemorySegment resultsSeg = arena.allocate(ValueLayout.JAVA_LONG, count);

            for (int i = 0; i < count; i++) {
                aSeg.set(ValueLayout.JAVA_DOUBLE, i * 8L, values[i].doubleValue());
                scaleASeg.set(ValueLayout.JAVA_INT, i * 4L, values[i].scale());
            }

            SETSCALE_BATCH_DOUBLE_HANDLE.invokeExact(
                aSeg, scaleASeg, newScale, resultsSeg, (long) count
            );

            BigDecimal[] results = new BigDecimal[count];
            for (int i = 0; i < count; i++) {
                long encoded = resultsSeg.get(ValueLayout.JAVA_LONG, i * 8L);
                results[i] = decodeToBigDecimal(encoded);
            }
            return results;
        } catch (Throwable t) {
            return fallbackBatchSetScale(values, newScale, rounding);
        }
    }

    // ========== Fallback 方法 ==========

    private static BigDecimal[] fallbackBatchDivide(BigDecimal[] dividends, BigDecimal[] divisors,
                                                     int scale, RoundingMode rounding) {
        BigDecimal[] results = new BigDecimal[dividends.length];
        for (int i = 0; i < dividends.length; i++) {
            results[i] = dividends[i].divide(divisors[i], scale, rounding);
        }
        return results;
    }

    private static BigDecimal[] fallbackBatchMultiply(BigDecimal[] aArray, BigDecimal[] bArray) {
        BigDecimal[] results = new BigDecimal[aArray.length];
        for (int i = 0; i < aArray.length; i++) {
            results[i] = aArray[i].multiply(bArray[i]);
        }
        return results;
    }

    private static BigDecimal[] fallbackBatchSetScale(BigDecimal[] values, int newScale, RoundingMode rounding) {
        BigDecimal[] results = new BigDecimal[values.length];
        for (int i = 0; i < values.length; i++) {
            results[i] = values[i].setScale(newScale, rounding);
        }
        return results;
    }

    // ========== 编码/解码辅助方法 ==========

    /**
     * 解码 Native 返回的编码结果为 BigDecimal
     *
     * 编码格式：[scale(32位) | sig(32位)]
     */
    private static BigDecimal decodeToBigDecimal(long encoded) {
        int scale = (int) (encoded >>> SCALE_SHIFT);
        long sig = decodeSig(encoded);
        return BigDecimal.valueOf(sig, scale);
    }

    /**
     * 解码 sig 部分（处理负数）
     */
    private static long decodeSig(long encoded) {
        long sigEncoded = encoded & SIG_MASK;
        if ((sigEncoded & 1) == 1) {
            return -(sigEncoded >>> 1);  // 负数
        } else {
            return sigEncoded >>> 1;     // 正数
        }
    }

    // ========== 工具方法 ==========

    /**
     * 检查 Native 库是否可用
     */
    public static boolean isNativeAvailable() {
        return NATIVE_AVAILABLE;
    }

    /**
     * 获取 double 可精确表示的位数
     */
    public static int getMaxPreciseDigits() {
        return 15;  // double 的 53 位尾数 ≈ 15-17 位十进制
    }

    /**
     * 检查数值是否可以使用 double 近似
     *
     * @param value 待检查的 BigDecimal
     * @return true 如果数值 ≤ 1e15（double 可精确表示范围）
     */
    public static boolean canUseDoubleApproximation(BigDecimal value) {
        return value.compareTo(new BigDecimal("1e15")) <= 0;
    }

    /**
     * 检查是否为 Compact Path
     *
     * @param value 待检查的 BigDecimal
     * @return true 如果数值 ≤ 18 位（建议使用 Standard）
     */
    public static boolean isCompactPath(BigDecimal value) {
        // 简单判断：如果 doubleValue() 能精确表示，可能是 Compact
        // 更精确的判断需要反射访问 intCompact 字段
        try {
            long precision = value.precision();
            return precision <= MAX_COMPACT_DIGITS;
        } catch (Exception e) {
            return false;
        }
    }
}
