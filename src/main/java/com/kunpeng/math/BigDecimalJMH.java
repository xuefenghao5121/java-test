package com.kunpeng.math;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmark - 公平对比 Standard vs Native
 *
 * 测试三个指定的函数：
 * 1. divide(BigDecimal, BigDecimal, int, RoundingMode)
 * 2. multiply(BigDecimal, BigDecimal)
 * 3. setScale(BigDecimal, int, RoundingMode)
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@State(Scope.Thread)
public class BigDecimalJMH {

    private BigDecimal a;
    private BigDecimal b;
    private BigDecimal value;

    @Setup(Level.Trial)
    public void setup() {
        Random random = new Random();
        // 使用真实的金融数据范围
        a = new BigDecimal(100 + random.nextInt(100000))
            .divide(new BigDecimal(1 + random.nextInt(100)), 4, RoundingMode.HALF_UP);
        b = new BigDecimal(1 + random.nextInt(1000))
            .divide(new BigDecimal(100), 4, RoundingMode.HALF_UP);
        value = new BigDecimal(random.nextDouble() * 10000)
            .setScale(4, RoundingMode.HALF_UP);
    }

    // ========== Divide 测试 ==========

    @Benchmark
    public void divideStandard(Blackhole bh) {
        BigDecimal result = a.divide(b, 2, RoundingMode.HALF_UP);
        bh.consume(result);
    }

    @Benchmark
    public void divideEncoded(Blackhole bh) {
        if (FastBigDecimalOptimizedFinal.isNativeAvailable()) {
            BigDecimal result = FastBigDecimalOptimizedFinal.divideEncoded(a, b, 2, RoundingMode.HALF_UP);
            bh.consume(result);
        } else {
            bh.consume(a.divide(b, 2, RoundingMode.HALF_UP));
        }
    }

    @Benchmark
    public void dividePtr(Blackhole bh) {
        if (FastBigDecimalOptimizedFinal.isNativeAvailable()) {
            BigDecimal result = FastBigDecimalOptimizedFinal.dividePtr(a, b, 2, RoundingMode.HALF_UP);
            bh.consume(result);
        } else {
            bh.consume(a.divide(b, 2, RoundingMode.HALF_UP));
        }
    }

    // ========== Multiply 测试 ==========

    @Benchmark
    public void multiplyStandard(Blackhole bh) {
        BigDecimal result = a.multiply(b);
        bh.consume(result);
    }

    @Benchmark
    public void multiplyEncoded(Blackhole bh) {
        if (FastBigDecimalOptimizedFinal.isNativeAvailable()) {
            BigDecimal result = FastBigDecimalOptimizedFinal.multiplyEncoded(a, b);
            bh.consume(result);
        } else {
            bh.consume(a.multiply(b));
        }
    }

    // ========== SetScale 测试 ==========

    @Benchmark
    public void setScaleStandard(Blackhole bh) {
        BigDecimal result = value.setScale(2, RoundingMode.HALF_UP);
        bh.consume(result);
    }

    @Benchmark
    public void setScaleEncoded(Blackhole bh) {
        if (FastBigDecimalOptimizedFinal.isNativeAvailable()) {
            // 暂时使用 divideEncoded 测试，因为 setScale_encoded 未实现
            BigDecimal result = FastBigDecimalOptimizedFinal.divideEncoded(value, BigDecimal.ONE, 2, RoundingMode.HALF_UP);
            bh.consume(result);
        } else {
            bh.consume(value.setScale(2, RoundingMode.HALF_UP));
        }
    }

    // ========== 组合场景测试 ==========

    @Benchmark
    public void combinedStandard(Blackhole bh) {
        // 模拟真实场景：price * quantity / cost
        BigDecimal price = a;
        BigDecimal quantity = b;
        BigDecimal cost = new BigDecimal("1.05");

        BigDecimal total = price.multiply(quantity);
        BigDecimal finalPrice = total.divide(cost, 2, RoundingMode.HALF_UP);
        BigDecimal rounded = finalPrice.setScale(2, RoundingMode.HALF_UP);

        bh.consume(rounded);
    }

    @Benchmark
    public void combinedEncoded(Blackhole bh) {
        if (FastBigDecimalOptimizedFinal.isNativeAvailable()) {
            BigDecimal price = a;
            BigDecimal quantity = b;
            BigDecimal cost = new BigDecimal("1.05");

            BigDecimal total = FastBigDecimalOptimizedFinal.multiplyEncoded(price, quantity);
            BigDecimal finalPrice = FastBigDecimalOptimizedFinal.divideEncoded(total, cost, 2, RoundingMode.HALF_UP);
            // BigDecimal setScale 暂时用 Standard
            BigDecimal rounded = finalPrice.setScale(2, RoundingMode.HALF_UP);

            bh.consume(rounded);
        } else {
            bh.consume(a.multiply(b));
        }
    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
}
