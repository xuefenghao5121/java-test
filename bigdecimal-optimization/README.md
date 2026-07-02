# BigDecimal Multiply Fast Path Optimization

## 概述

针对 JDK 25 BigDecimal 的乘法运算快速路径优化，专门面向金融交易场景（小规模数值 ≤10 位精度）。

## 优化内容

### 新增辅助方法

```java
private static boolean isSmallMultiply(long x, long y) {
    return Math.abs(x) < 1_000_000_000L && Math.abs(y) < 1_000_000_000L;
}
```

### 优化的方法

1. `multiply(long x, long y, int scale)` - 跳过小值溢出检查
2. `multiplyAndRound(long x, long y, int scale, MathContext mc)` - 添加快速路径

## 性能预期

| 场景 | 基线 | 优化后 | 提升 |
|------|------|--------|------|
| 小值乘法 (9位) | 4.91 ns/op | ~3.5 ns/op | **28%** |
| 带scale乘法 | 4.43 ns/op | ~3.2 ns/op | **28%** |
| UNLIMITED精度 | 5.52 ns/op | ~4.0 ns/op | **28%** |

## 文件说明

| 文件 | 说明 |
|------|------|
| `bigDecimal_multiply_fastpath.patch` | JDK 源码补丁 |
| `performance_comparison_report.md` | 详细性能分析报告 |
| `SimpleBenchmark.java` | 简单性能测试程序 |
| `MockBigDecimal.java` | 优化逻辑验证程序 |

## 应用方法

### 方法1: 应用到 OpenJDK 源码

```bash
cd /path/to/jdk/src/java.base/share/classes/java/math/
patch -p1 < bigDecimal_multiply_fastpath.patch
```

### 方法2: 手动修改

在 `BigDecimal.java` 中：
1. 添加 `isSmallMultiply` 方法（约 5820 行）
2. 修改 `multiply(long x, long y, int scale)` 方法（约 5890 行）
3. 修改 `multiplyAndRound` 方法（约 5918 行）

## 编译验证

```bash
# 需要安装构建依赖
sudo apt-get install build-essential autoconf libx11-dev

# 编译 JDK
cd /path/to/jdk
bash configure
make build

# 运行测试
java -jar SimpleBenchmark.jar
```

## 运行测试

```bash
javac SimpleBenchmark.java
java SimpleBenchmark

javac MockBigDecimal.java
java MockBigDecimal
```

## 技术细节

### 优化原理

对于金融场景中的小值（< 10^9），乘积必然小于 10^18，可以安全地跳过溢出检查：

```java
// 原实现：每次乘法都需要除法验证
if (product / y == x) { return product; }  // 昂贵的除法

// 优化后：小值直接返回
if (isSmallMultiply(x, y)) {
    return valueOf(x * y, scale);  // 无除法开销
}
```

### 适用场景

- ✅ 金融交易（±$10M 以内）
- ✅ 货币计算（2 位小数）
- ✅ 订单金额、税费计算
- ❌ 科学计算（大数值）
- ❌ 精密工程（高精度需求）

## 目标 JDK 版本

- OpenJDK 25+
- 可能兼容 JDK 21-24（需验证）

## 许可证

遵循 GPL v2 with Classpath Exception（与 OpenJDK 一致）

## 参考

- [OpenJDK BigDecimal Source](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/math/BigDecimal.java)
- [JDK 25 文档](https://docs.oracle.com/en/java/javase/25/docs/api/)
