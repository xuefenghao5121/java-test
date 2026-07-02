# BigDecimal Performance Optimization for JDK 25

## 概述

针对 JDK 25 BigDecimal 的性能优化，专门面向金融交易场景：

- **乘法快速路径**：支持小额和大额交易（长尾分布）
- **除法快速路径**：支持小除数和 scale 调整（税率计算）
- **税率计算优化**：支持 price × taxRate 和 price ÷ (1 + taxRate)

> **⚠️ 精度修复 (2026-07-02)**: 发现并修复了除法快速路径中的精度问题。请使用 `BigDecimal_fastpath_complete_fixed.patch` 而不是旧版本。

---

## 版本历史

### v1.1 (2026-07-02) - 精度修复版本

**修复内容**：
- 修复除法快速路径中 `createDivideResult` 的 `intermediateScale` 计算错误
- 统一 Case 2 和 Case 3 的 scale 计算公式
- 修复了 100 ÷ 0.001、100 ÷ 1.2345 等场景的精度问题

**测试验证**：
- 所有基础测试通过
- 边界情况测试通过
- 用户报告的场景（100 ÷ 0.001/0.01/1.2345/99.999/999.99）全部正确

**推荐使用**：`BigDecimal_fastpath_complete_fixed.patch`

### v1.0 (2026-07-01) - 初始版本

**已知问题**：除法 Case 3（scaleDiff < 0）有精度错误

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
| --- | --- | --- | --- |
| <$10M | ✓ | ✓ | - |
| $10M - $100M | ✗ | ✓ | **+10%** |
| >$100M | ✗ | ✗ | - |

### 2. 除法快速路径优化（已修复精度问题）

#### 修复内容

修复了 Case 3（scaleDiff < 0）中的 `intermediateScale` 计算错误：

```java
// 修复前（错误）
return createDivideResult(q * qsign, scale + adjust, scale, roundingMode);

// 修复后（正确）
return createDivideResult(q * qsign, scaleDiff, scale, roundingMode);
// 内部统一使用：intermediateScale = targetScale - scaleDiff
```

#### 问题场景对比

| 运算 | 修复前（错误） | 修复后（正确） |
|------|---------------|---------------|
| 100 ÷ 0.001 | 0.00000100 | 100000.00000000 |
| 100 ÷ 0.01 | 0.00000100 | 10000.00000000 |
| 100 ÷ 1.2345 | 0E-8 | 81.00445525 |
| 100 ÷ 99.999 | 0E-8 | 1.00001000 |
| 100 ÷ 999.99 | 0E-8 | 0.10000100 |

#### 新增辅助方法

```java
private static boolean canUseFastDivideWithScale(long dividend, long divisor, int scaleDiff) {
    return divisor != 0 &&
           Math.abs(divisor) < 100_000L &&
           Math.abs(dividend) < 10_000_000_000_000_000L &&
           Math.abs(scaleDiff) <= 4;  // 允许小的 scale 差
}
```

#### 覆盖范围

| 场景 | 支持情况 |
| --- | --- |
| 同 scale 除法 | ✓ |
| scale 差 ≤ 4 | ✓ （已修复精度） |
| scale 差 > 4 | ✗ （使用标准路径） |

---

## 文件说明

| 文件 | 说明 |
| --- | --- |
| `patches/BigDecimal_fastpath_complete_fixed.patch` | **推荐使用** - 精度修复版本 (v1.1) |
| `patches/BigDecimal_fastpath_complete.patch` | 旧版本 (v1.0) - 有精度问题 |
| `patches/bigDecimal_optimized_tax_rate.patch` | 初始版本 |
| `src/test/java/TaxRateBenchmark.java` | 税率计算性能测试 |
| `src/test/java/TaxRateAnalysis.java` | 快速路径覆盖分析 |
| `src/test/java/SimpleBenchmark.java` | 基础乘法测试 |
| `src/test/java/DivideBenchmark.java` | 基础除法测试 |
| `src/test/java/DividePrecisionTest.java` | 除法精度测试 |
| `src/test/java/DivideFastPathAnalysis.java` | 快速路径问题分析 |
| `src/test/java/DivideScaleFixValidation.java` | Scale 修复验证 |

---

## 性能预期

### 乘法性能（税率计算场景）

| 场景 | 基线 | 优化后 | 提升 |
| --- | --- | --- | --- |
| 小额 × 税率 | 0.77 ns/op | ~0.6 ns/op | **22%** |
| 大额 × 税率 | 6.42 ns/op | ~4.5 ns/op | **30%** |

### 除法性能（税率计算场景）

| 场景 | 基线 | 优化后 | 提升 |
| --- | --- | --- | --- |
| 同 scale | 3.64 ns/op | ~3.0 ns/op | **18%** |
| scale 差 ≤ 4 | 6.55 ns/op | ~4.5 ns/op | **31%** |

---

## 应用方法

### 完整补丁（推荐）

```bash
cd /path/to/jdk/src/java.base/share/classes/java/math/
patch -p1 < patches/BigDecimal_fastpath_complete_fixed.patch
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
# 编译测试
javac -d target/classes src/test/java/*.java

# 税率计算场景测试
java -cp target/classes TaxRateBenchmark

# 快速路径覆盖分析
java -cp target/classes TaxRateAnalysis

# 基础乘法测试
java -cp target/classes SimpleBenchmark

# 基础除法测试
java -cp target/classes DivideBenchmark
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

```
// 原实现：两数都 < 10^9
if (|x| < 10^9 && |y| < 10^9) { fast_path(); }

// 优化后：支持一个大数 × 一个小数（税率）
if (两数都 < 10^9) { fast_path(); }  // 原有路径
if (一数 < 1K && 另一数 < 10^13) { fast_path(); }  // 新增路径
```

### 除法优化原理（scale 差支持）

```
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

- OpenJDK BigDecimal Source
- JDK 25 文档
