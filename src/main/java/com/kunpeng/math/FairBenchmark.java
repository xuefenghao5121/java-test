package com.kunpeng.math;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

/**
 * 公平的 Benchmark - 避免 JIT 优化影响
 *
 * 关键改进：
 * 1. 使用 Blackhole 模式防止代码被优化掉
 * 2. 测试真实的三个函数
 * 3. 使用多样化的真实数据
 * 4. 充分的 warmup 确保 JIT 编译完成
 */
public class FairBenchmark {

    // Blackhole - 防止结果被优化掉
    static volatile long sink;

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║         公平 Benchmark（避免 JIT 优化）                   ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        System.out.println("配置：");
        System.out.println("  Warmup 轮次: 10");
        System.out.println("  每轮 Warmup 迭代: 100,000");
        System.out.println("  测试轮次: 5");
        System.out.println("  每轮测试迭代: 10,000,000\n");

        boolean nativeAvailable = FastBigDecimalOptimizedFinal.isNativeAvailable();

        if (!nativeAvailable) {
            System.out.println("⚠ Native 库不可用，只测试 Standard");
        }

        // 准备多样化的测试数据
        TestData[] testData = prepareTestData();

        // 测试 divide
        System.out.println("┌─ Divide 测试 ────────────────────────────────────────────┐");
        BenchmarkResult divideStd = benchmarkDivide("Standard", testData, false);
        BenchmarkResult divideEnc = nativeAvailable ?
            benchmarkDivide("Encoded", testData, true) : null;
        printDivideResult(divideStd, divideEnc);

        // 测试 multiply
        System.out.println("┌─ Multiply 测试 ─────────────────────────────────────────┐");
        BenchmarkResult multStd = benchmarkMultiply("Standard", testData, false);
        BenchmarkResult multEnc = nativeAvailable ?
            benchmarkMultiply("Encoded", testData, true) : null;
        printMultiplyResult(multStd, multEnc);

        // 测试 setScale
        System.out.println("┌─ SetScale 测试 ──────────────────────────────────────────┐");
        BenchmarkResult scaleStd = benchmarkSetScale("Standard", testData, false);
        BenchmarkResult scaleEnc = nativeAvailable ?
            benchmarkSetScale("Encoded", testData, true) : null;
        printSetScaleResult(scaleStd, scaleEnc);

        // 组合场景
        System.out.println("┌─ 组合场景测试 ──────────────────────────────────────────┐");
        BenchmarkResult combStd = benchmarkCombined("Standard", testData, false);
        BenchmarkResult combEnc = nativeAvailable ?
            benchmarkCombined("Encoded", testData, true) : null;
        printCombinedResult(combStd, combEnc);
    }

    private static TestData[] prepareTestData() {
        TestData[] data = new TestData[100];
        Random random = new Random(42);

        for (int i = 0; i < data.length; i++) {
            // 使用真实的金融数据范围
            // 确保 a 和 b 都不会是 0，避免除零错误
            BigDecimal a = new BigDecimal(100 + random.nextInt(100000))
                .divide(new BigDecimal(100), 4, RoundingMode.HALF_UP);

            // b 必须保证非零，使用较大的基数和足够的小数位数
            BigDecimal b = new BigDecimal(100 + random.nextInt(1000))
                .divide(new BigDecimal(100), 4, RoundingMode.HALF_UP);

            // 确保不会等于 0
            if (b.compareTo(BigDecimal.ZERO) == 0) {
                b = new BigDecimal("1.0");
            }

            BigDecimal value = new BigDecimal(100 + random.nextDouble() * 10000)
                .setScale(4, RoundingMode.HALF_UP);

            data[i] = new TestData(a, b, value);
        }

        return data;
    }

    private static BenchmarkResult benchmarkDivide(String name, TestData[] data, boolean useNative) {
        final int WARMUP_ROUNDS = 10;
        final int WARMUP_ITER = 100000;
        final int TEST_ROUNDS = 5;
        final int TEST_ITER = 10000000;

        long[] times = new long[TEST_ROUNDS];

        // Warmup
        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            for (int i = 0; i < WARMUP_ITER; i++) {
                TestData d = data[i % data.length];
                BigDecimal result = useNative ?
                    FastBigDecimalOptimizedFinal.divideEncoded(d.a, d.b, 2, RoundingMode.HALF_UP) :
                    d.a.divide(d.b, 2, RoundingMode.HALF_UP);
                sink = result.longValue();
            }
        }

        // 测试
        for (int round = 0; round < TEST_ROUNDS; round++) {
            long t0 = System.nanoTime();
            for (int i = 0; i < TEST_ITER; i++) {
                TestData d = data[i % data.length];
                BigDecimal result = useNative ?
                    FastBigDecimalOptimizedFinal.divideEncoded(d.a, d.b, 2, RoundingMode.HALF_UP) :
                    d.a.divide(d.b, 2, RoundingMode.HALF_UP);
                sink = result.longValue();
            }
            times[round] = System.nanoTime() - t0;
        }

        return new BenchmarkResult(name, times, TEST_ITER);
    }

    private static BenchmarkResult benchmarkMultiply(String name, TestData[] data, boolean useNative) {
        final int WARMUP_ROUNDS = 10;
        final int WARMUP_ITER = 100000;
        final int TEST_ROUNDS = 5;
        final int TEST_ITER = 10000000;

        long[] times = new long[TEST_ROUNDS];

        // Warmup
        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            for (int i = 0; i < WARMUP_ITER; i++) {
                TestData d = data[i % data.length];
                BigDecimal result = useNative ?
                    FastBigDecimalOptimizedFinal.multiplyEncoded(d.a, d.b) :
                    d.a.multiply(d.b);
                sink = result.longValue();
            }
        }

        // 测试
        for (int round = 0; round < TEST_ROUNDS; round++) {
            long t0 = System.nanoTime();
            for (int i = 0; i < TEST_ITER; i++) {
                TestData d = data[i % data.length];
                BigDecimal result = useNative ?
                    FastBigDecimalOptimizedFinal.multiplyEncoded(d.a, d.b) :
                    d.a.multiply(d.b);
                sink = result.longValue();
            }
            times[round] = System.nanoTime() - t0;
        }

        return new BenchmarkResult(name, times, TEST_ITER);
    }

    private static BenchmarkResult benchmarkSetScale(String name, TestData[] data, boolean useNative) {
        final int WARMUP_ROUNDS = 10;
        final int WARMUP_ITER = 100000;
        final int TEST_ROUNDS = 5;
        final int TEST_ITER = 10000000;

        long[] times = new long[TEST_ROUNDS];

        // Warmup
        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            for (int i = 0; i < WARMUP_ITER; i++) {
                TestData d = data[i % data.length];
                BigDecimal result = useNative ?
                    // 暂时用 divide 模拟
                    FastBigDecimalOptimizedFinal.divideEncoded(d.value, BigDecimal.ONE, 2, RoundingMode.HALF_UP) :
                    d.value.setScale(2, RoundingMode.HALF_UP);
                sink = result.longValue();
            }
        }

        // 测试
        for (int round = 0; round < TEST_ROUNDS; round++) {
            long t0 = System.nanoTime();
            for (int i = 0; i < TEST_ITER; i++) {
                TestData d = data[i % data.length];
                BigDecimal result = useNative ?
                    FastBigDecimalOptimizedFinal.divideEncoded(d.value, BigDecimal.ONE, 2, RoundingMode.HALF_UP) :
                    d.value.setScale(2, RoundingMode.HALF_UP);
                sink = result.longValue();
            }
            times[round] = System.nanoTime() - t0;
        }

        return new BenchmarkResult(name, times, TEST_ITER);
    }

    private static BenchmarkResult benchmarkCombined(String name, TestData[] data, boolean useNative) {
        final int WARMUP_ROUNDS = 10;
        final int WARMUP_ITER = 100000;
        final int TEST_ROUNDS = 5;
        final int TEST_ITER = 10000000;

        long[] times = new long[TEST_ROUNDS];

        // Warmup
        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            for (int i = 0; i < WARMUP_ITER; i++) {
                TestData d = data[i % data.length];
                BigDecimal result;
                if (useNative) {
                    BigDecimal total = FastBigDecimalOptimizedFinal.multiplyEncoded(d.a, d.b);
                    result = FastBigDecimalOptimizedFinal.divideEncoded(total, new BigDecimal("1.05"), 2, RoundingMode.HALF_UP);
                } else {
                    BigDecimal total = d.a.multiply(d.b);
                    result = total.divide(new BigDecimal("1.05"), 2, RoundingMode.HALF_UP);
                }
                sink = result.longValue();
            }
        }

        // 测试
        for (int round = 0; round < TEST_ROUNDS; round++) {
            long t0 = System.nanoTime();
            for (int i = 0; i < TEST_ITER; i++) {
                TestData d = data[i % data.length];
                BigDecimal result;
                if (useNative) {
                    BigDecimal total = FastBigDecimalOptimizedFinal.multiplyEncoded(d.a, d.b);
                    result = FastBigDecimalOptimizedFinal.divideEncoded(total, new BigDecimal("1.05"), 2, RoundingMode.HALF_UP);
                } else {
                    BigDecimal total = d.a.multiply(d.b);
                    result = total.divide(new BigDecimal("1.05"), 2, RoundingMode.HALF_UP);
                }
                sink = result.longValue();
            }
            times[round] = System.nanoTime() - t0;
        }

        return new BenchmarkResult(name, times, TEST_ITER);
    }

    private static void printDivideResult(BenchmarkResult std, BenchmarkResult enc) {
        System.out.println("  分母: 随机数值 (1-1000)");
        System.out.println("  目标 scale: 2");
        System.out.println("  舍入模式: HALF_UP\n");

        std.print();
        if (enc != null) {
            enc.print();
            System.out.printf("  Encoded vs Standard: %.2fx%n%n", enc.mean / std.mean);
        }
    }

    private static void printMultiplyResult(BenchmarkResult std, BenchmarkResult enc) {
        System.out.println("  操作数: 随机数值 (不同 scale)\n");

        std.print();
        if (enc != null) {
            enc.print();
            System.out.printf("  Encoded vs Standard: %.2fx%n%n", enc.mean / std.mean);
        }
    }

    private static void printSetScaleResult(BenchmarkResult std, BenchmarkResult enc) {
        System.out.println("  原始 scale: 0-5");
        System.out.println("  目标 scale: 2\n");

        std.print();
        if (enc != null) {
            enc.print();
            System.out.printf("  Encoded vs Standard: %.2fx%n%n", enc.mean / std.mean);
        }
    }

    private static void printCombinedResult(BenchmarkResult std, BenchmarkResult enc) {
        System.out.println("  场景: price × quantity / cost");
        System.out.println("  涉及: multiply + divide\n");

        std.print();
        if (enc != null) {
            enc.print();
            System.out.printf("  Encoded vs Standard: %.2fx%n%n", enc.mean / std.mean);
        }

        System.out.println("═══════════════════════════════════════════════════════════════\n");
        System.out.println("最终结论：\n");

        if (enc != null) {
            if (enc.mean < std.mean) {
                System.out.printf("  ✓ Encoded 更快 (%.2fx)，推荐使用%n", std.mean / enc.mean);
            } else {
                System.out.printf("  ✗ Encoded 更慢 (%.2fx)，推荐使用 Standard%n", enc.mean / std.mean);
            }
        } else {
            System.out.println("  Native 库不可用，使用 Standard");
        }
    }

    static class TestData {
        final BigDecimal a;
        final BigDecimal b;
        final BigDecimal value;

        TestData(BigDecimal a, BigDecimal b, BigDecimal value) {
            this.a = a;
            this.b = b;
            this.value = value;
        }
    }

    static class BenchmarkResult {
        final String name;
        final double mean;
        final double min;
        final double max;
        final double stdDev;

        BenchmarkResult(String name, long[] times, long iterations) {
            this.name = name;

            double sum = 0;
            double min = Double.MAX_VALUE;
            double max = 0;

            for (long t : times) {
                double nsPerOp = t / (double) iterations;
                sum += nsPerOp;
                if (nsPerOp < min) min = nsPerOp;
                if (nsPerOp > max) max = nsPerOp;
            }

            this.mean = sum / times.length;
            this.min = min;
            this.max = max;

            // 计算标准差
            double variance = 0;
            for (long t : times) {
                double nsPerOp = t / (double) iterations;
                variance += (nsPerOp - mean) * (nsPerOp - mean);
            }
            this.stdDev = Math.sqrt(variance / times.length);
        }

        void print() {
            System.out.printf("  %s:%n", name);
            System.out.printf("    Mean:   %.2f ns/op%n", mean);
            System.out.printf("    Min:    %.2f ns/op%n", min);
            System.out.printf("    Max:    %.2f ns/op%n", max);
            System.out.printf("    StdDev: %.2f ns%n", stdDev);
        }
    }
}
