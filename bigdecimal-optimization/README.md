# BigDecimal Performance Optimization for JDK 25

## 概述

针对 JDK 25 BigDecimal 的性能优化，专门面向金融交易场景：
- **乘法快速路径**：小规模数值（≤10 位精度）
- **除法快速路径**：小除数（2-5 位）

---

## 优化内容

### 1. 乘法快速路径优化

#### 新增辅助方法
```java
private static boolean isSmallMultiply(long x, long y) {
    return Math.abs(x) < 1_000_000_000L && Math.abs(y) < 1_000_000_000L;
}
```

#### 优化的方法
- `multiply(long x, long y, int scale)` - 跳过小值溢出检查
- `multiplyAndRound(long x, long y, int scale, MathContext mc)` - 添加快速路径

#### 性能预期
| 场景 | 基线 | 优化后 | 提升 |
|------|------|--------|------|
| 小值乘法 (9位) | 4.91 ns/op | ~3.5 ns/op | **28%** |
| 带scale乘法 | 4.43 ns/op | ~3.2 ns/op | **28%** |

---

### 2. 除法快速路径优化

#### 新增辅助方法
```java
private static boolean isSmallDivisor(long divisor) {
    return divisor != 0 && Math.abs(divisor) < 100_000L;
}

private static boolean canUseFastDivide(long dividend, long divisor) {
    return divisor != 0 && Math.abs(divisor) < 100_000L
        && Math.abs(dividend) < 10_000_000_000_000_000L;
}
```

#### 优化的方法
- `divide(long dividend, int dividendScale, long divisor, int divisorScale, ...)` 
  - 同 scale 时跳过复杂调整逻辑

#### 性能预期
| 场景 | 基线 | 优化后 | 提升 |
|------|------|--------|------|
| ÷100 (百分比) | 4.40 ns/op | ~3.5 ns/op | **20%** |
| ÷1000 (千分比) | 2.57 ns/op | ~2.0 ns/op | **22%** |
| ÷25 (佣金费率) | 4.63 ns/op | ~3.7 ns/op | **20%** |

---

## 文件说明

| 文件 | 说明 |
|------|------|
| `bigDecimal_multiply_fastpath.patch` | 乘法优化补丁 |
| `bigDecimal_divide_fastpath.patch` | 除法优化补丁 |
| `SimpleBenchmark.java` | 乘法性能测试 |
| `DivideBenchmark.java` | 除法性能测试 |
| `MockBigDecimal.java` | 优化逻辑验证 |
| `performance_comparison_report.md` | 详细性能分析 |

---

## 应用方法

### 合并补丁（推荐）

```bash
cd /path/to/jdk/src/java.base/share/classes/java/math/
patch -p1 < bigDecimal_multiply_fastpath.patch
patch -p1 < bigDecimal_divide_fastpath.patch
```

### 编译验证

```bash
sudo apt-get install build-essential autoconf libx11-dev
cd /path/to/jdk
bash configure
make build
```

---

## 运行测试

```bash
# 乘法测试
javac SimpleBenchmark.java
java SimpleBenchmark

# 除法测试
javac DivideBenchmark.java
java DivideBenchmark
```

---

## 适用场景

### ✅ 推荐使用
- 金融交易（±$10M 以内）
- 货币计算（2 位小数）
- 百分比计算（÷100）
- 佣金/费率计算（÷固定小值）

### ❌ 不适用
- 科学计算（大数值）
- 精密工程（高精度需求）

---

## 技术细节

### 乘法优化原理
```java
// 原实现：每次乘法都需要除法验证
if (product / y == x) { return product; }  // 昂贵的除法

// 优化后：小值直接返回
if (isSmallMultiply(x, y)) {
    return valueOf(x * y, scale);  // 无除法开销
}
```

### 除法优化原理
```java
// 原实现：复杂的 scale 调整和可能的 BigInteger 转换

// 优化后：同 scale 时直接使用 long 除法
if (dividendScale == divisorScale) {
    long q = absDividend / absDivisor;
    // 简单的舍入处理
    return valueOf(q * sign, scale);
}
```

---

## 目标 JDK 版本

- OpenJDK 25+
- 可能兼容 JDK 21-24（需验证）

---

## 许可证

遵循 GPL v2 with Classpath Exception（与 OpenJDK 一致）

---

## 参考

- [OpenJDK BigDecimal Source](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/math/BigDecimal.java)
- [JDK 25 文档](https://docs.oracle.com/en/java/javase/25/docs/api/)
