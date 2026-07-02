# BigDecimal Performance Optimization for JDK 25

## 概述

针对 JDK 25 BigDecimal 的性能优化，专门面向金融交易场景：
- **乘法快速路径**：支持小额和大额交易（长尾分布）
- **除法快速路径**：支持小除数和 scale 调整（税率计算）
- **税率计算优化**：支持 price × taxRate 和 price ÷ (1 + taxRate)

---

## 优化内容

### 1. 乘法快速路径优化（已更新支持长尾数）

#### 优化后的辅助方法
```java
private static boolean isSmallMultiply(long x, long y) {
    long absX = Math.abs(x);
    long absY = Math.abs(y);

    // 快速路径 1：两数都小（< 10^9）
    if (absX < 1_000_000_000L && absY < 1_000_000_000L) {
        return true;
    }

    // 快速路径 2：一个很小（税率 < 1000），另一个可达 10^13
    // 支持大额交易 × 税率场景
    if (absX < 1_000L && absY < 10_000_000_000_000L) {
        return true;
    }
    if (absY < 1_000L && absX < 10_000_000_000_000L) {
        return true;
    }

    return false;
}
```

#### 覆盖范围提升

| 金额范围 | 优化前 | 优化后 | 覆盖率提升 |
|----------|--------|--------|-----------|
| <$10M | ✓ | ✓ | - |
| $10M - $100M | ✗ | ✓ | **+10%** |
| >$100M | ✗ | ✗ | - |

### 2. 除法快速路径优化（已更新支持 scale 差）

#### 新增辅助方法
```java
private static boolean canUseFastDivideWithScale(long dividend, long divisor, int scaleDiff) {
    return divisor != 0 &&
           Math.abs(divisor) < 100_000L &&
           Math.abs(dividend) < 10_000_000_000_000_000L &&
           Math.abs(scaleDiff) <= 4;  // 允许小的 scale 差
}
```

#### 覆盖范围提升

| 场景 | 优化前 | 优化后 |
|------|--------|--------|
| 同 scale 除法 | ✓ | ✓ |
| scale 差 ≤ 4 | ✗ | ✓ |

---

## 文件说明

| 文件 | 说明 |
|------|------|
| `bigDecimal_optimized_tax_rate.patch` | 完整优化补丁（含长尾数支持） |
| `bigDecimal_multiply_fastpath.patch` | 乘法优化补丁（原始） |
| `bigDecimal_divide_fastpath.patch` | 除法优化补丁（原始） |
| `TaxRateBenchmark.java` | 税率计算性能测试 |
| `TaxRateAnalysis.java` | 快速路径覆盖分析 |
| `SimpleBenchmark.java` | 基础乘法测试 |
| `DivideBenchmark.java` | 基础除法测试 |

---

## 性能预期

### 乘法性能（税率计算场景）

| 场景 | 基线 | 优化后 | 提升 |
|------|------|--------|------|
| 小额 × 税率 | 0.77 ns/op | ~0.6 ns/op | **22%** |
| 大额 × 税率 | 6.42 ns/op | ~4.5 ns/op | **30%** |

### 除法性能（税率计算场景）

| 场景 | 基线 | 优化后 | 提升 |
|------|------|--------|------|
| 同 scale | 3.64 ns/op | ~3.0 ns/op | **18%** |
| scale 差 ≤ 4 | 6.55 ns/op | ~4.5 ns/op | **31%** |

---

## 应用方法

### 完整补丁（推荐）

```bash
cd /path/to/jdk/src/java.base/share/classes/java/math/
patch -p1 < bigDecimal_optimized_tax_rate.patch
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
# 税率计算场景测试
javac TaxRateBenchmark.java
java TaxRateBenchmark

# 快速路径覆盖分析
javac TaxRateAnalysis.java
java TaxRateAnalysis
```

---

## 适用场景

### ✅ 推荐使用

- **税率计算**：price × taxRate
- **含税计算**：price ÷ (1 + taxRate)
- **长尾交易**：从 $1 到 $100M 的交易
- **百分比转换**：÷100, ÷1000

### ❌ 不适用

- 科学计算（大数值 > 10^13）
- 精密工程（需要 BigInteger）

---

## 技术细节

### 乘法优化原理（长尾数支持）

```java
// 原实现：两数都 < 10^9
if (|x| < 10^9 && |y| < 10^9) { fast_path(); }

// 优化后：支持一个大数 × 一个小数（税率）
if (两数都 < 10^9) { fast_path(); }  // 原有路径
if (一数 < 1K && 另一数 < 10^13) { fast_path(); }  // 新增路径
```

### 除法优化原理（scale 差支持）

```java
// 原实现：只处理相同 scale
if (dividendScale == divisorScale) { fast_divide(); }

// 优化后：支持小的 scale 差
if (scale 差 ≤ 4) {
    调整 scale 后使用 long 除法;
} else {
    标准 BigInteger 路径;
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
