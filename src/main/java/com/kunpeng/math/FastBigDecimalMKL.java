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
 * MKL 优化版本 - Non-Compact Path 验证
 *
 * 使用 Intel MKL 或标准 libm 的 double 向量化运算
 *
 * 精度说明：
 * - double 有 53 位尾数，可精确表示约 15-17 位十进制数字
 * - 对于 >18 位的 BigDecimal，会有精度损失
 * - 适用于可接受浮点近似的场景（科学计算、金融近似）
 */
public class FastBigDecimalMKL {

    private static final MethodHandle DIVIDE_BATCH_DOUBLE_HANDLE;
    private static final MethodHandle MULTIPLY_BATCH_DOUBLE_HANDLE;
    private static final MethodHandle SETSCALE_BATCH_DOUBLE_HANDLE;

    private static final long SCALE_MASK = 0xFFFFFFFF00000000L;
    private static final long SIG_MASK = 0x00000000FFFFFFFFL;
    private static final int SCALE_SHIFT = 32;

    private static final boolean NATIVE_AVAILABLE;
    private static final int MAX_COMPACT_DIGITS = 18;

    static {
        MethodHandle divideBatch = null, multiplyBatch = null, setScaleBatch = null;
        boolean ok = false;

        try {
            System.loadLibrary("m_mkl");

            SymbolLookup lookup = SymbolLookup.loaderLookup();
            Linker linker = Linker.nativeLinker();

            divideBatch = linker.downcallHandle(
                lookup.find("km_divide_batch_double").orElseThrow(),
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
                )
            );

            multiplyBatch = linker.downcallHandle(
                lookup.find("km_multiply_batch_double").orElseThrow(),
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
                )
            );

            setScaleBatch = linker.downcallHandle(
                lookup.find("km_setscale_batch_double").orElseThrow(),
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
                )
            );

            ok = true;
        } catch (Throwable t) {
            System.err.println("MKL 库不可用: " + t.getMessage());
        }

        DIVIDE_BATCH_DOUBLE_HANDLE = divideBatch;
        MULTIPLY_BATCH_DOUBLE_HANDLE = multiplyBatch;
        SETSCALE_BATCH_DOUBLE_HANDLE = setScaleBatch;
        NATIVE_AVAILABLE = ok;
    }

    /**
     * 批量除法 - Non-Compact Path
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
            // 分配 double 数组
            MemorySegment aSeg = arena.allocate(ValueLayout.JAVA_DOUBLE, count);
            MemorySegment bSeg = arena.allocate(ValueLayout.JAVA_DOUBLE, count);
            MemorySegment resultsSeg = arena.allocate(ValueLayout.JAVA_LONG, count);

            // 转换为 double
            for (int i = 0; i < count; i++) {
                aSeg.set(ValueLayout.JAVA_DOUBLE, i * 8L, dividends[i].doubleValue());
                bSeg.set(ValueLayout.JAVA_DOUBLE, i * 8L, divisors[i].doubleValue());
            }

            // 调用 MKL/标准 libm 批量除法
            DIVIDE_BATCH_DOUBLE_HANDLE.invokeExact(
                aSeg, bSeg, scale, resultsSeg, (long) count
            );

            // 读取结果
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
     * 批量乘法 - Non-Compact Path
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
     * 批量 setScale - Non-Compact Path
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

    // ========== 辅助方法 ==========

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

    public static boolean isNativeAvailable() {
        return NATIVE_AVAILABLE;
    }

    /**
     * 获取可精确表示的位数
     */
    public static int getMaxPreciseDigits() {
        return 15;  // double 的精确位数
    }

    /**
     * 检查是否可以使用 double 近似
     */
    public static boolean canUseDoubleApproximation(BigDecimal value) {
        // 如果数值 > 1e15，double 无法精确表示
        return value.compareTo(new BigDecimal("1e15")) <= 0;
    }
}
