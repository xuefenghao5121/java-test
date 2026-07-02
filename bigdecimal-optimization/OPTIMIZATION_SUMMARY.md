# BigDecimal 优化汇总 - 金融场景完整方案

## 优化概览

针对 JDK 25 BigDecimal 的性能优化，专门面向金融交易场景的三个核心优化：

| 优化类型 | 目标操作 | 预期提升 | 状态 |
|---------|---------|---------|------|
| 乘法快速路径 | price × taxRate | 25-30% | ✅ 已完成 |
| 除法快速路径 | price ÷ (1+taxRate) | 18-31% | ✅ 已完成 |
| 字符串解析快速路径 | "123.45" → BigDecimal | 40-60% | ✅ 已完成 |

---

## 1. 乘法快速路径优化（长尾数支持）

### 补丁文件
`bigdecimal_multiply_fastpath.patch`

### 优化内容
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

### 覆盖范围
| 金额范围 | 优化前 | 优化后 |
|---------|--------|--------|
| <$10M | ✓ | ✓ |
| $10M - $100M | ✗ | ✓ |

### 性能预期
| 场景 | 基线 ns/op | 优化后 ns/op | 提升 |
|------|------------|-------------|------|
| 小额 × 税率 | 4.91 | ~3.5 | 30% |
| 大额 × 税率 | 38.21 | ~4.5 | 75% |

---

## 2. 除法快速路径优化（Scale 差支持）

### 补丁文件
`bigdecimal_divide_fastpath.patch`（已合并到 `bigDecimal_optimized_tax_rate.patch`）

### 优化内容
```java
private static boolean canUseFastDivideWithScale(long dividend, long divisor, int scaleDiff) {
    if (divisor == 0) return false;
    long absDivisor = Math.abs(divisor);
    long absDividend = Math.abs(dividend);
    int absScaleDiff = Math.abs(scaleDiff);

    // 小除数 + 合理的被除数 + 小的 scale 差
    return absDivisor < 100_000L &&
           absDividend < 10_000_000_000_000_000L &&
           absScaleDiff <= 4;
}
```

### 覆盖范围
| 场景 | 优化前 | 优化后 |
|------|--------|--------|
| 同 scale 除法 | ✓ | ✓ |
| scale 差 ≤ 4 | ✗ | ✓ |

### 性能预期
| 场景 | 基线 ns/op | 优化后 ns/op | 提升 |
|------|------------|-------------|------|
| 同 scale | 3.64 | ~3.0 | 18% |
| scale 差 ≤ 4 | 6.55 | ~4.5 | 31% |

---

## 3. 字符串解析快速路径优化（新增）

### 补丁文件
`bigdecimal_string_fastpath.patch`

### 优化内容
针对金融格式字符串的快速识别和解析：

```java
private static int checkFinancialFastPath(char[] in, int offset, int len) {
    // 快速检查是否匹配金融格式：
    // - 可选符号 (+/-)
    // - 1-12 位数字（整数部分）
    // - 可选小数点
    // - 如果有小数点，恰好 2 位（货币）或 4 位（百分比）小数
    // ...
}

private static BigDecimal parseFinancialFastPath(char[] in, int offset, int len, int scale, MathContext mc) {
    // 简化的解析逻辑，直接处理数字
    // ...
}
```

### 覆盖格式
| 格式类型 | 示例 | 状态 |
|---------|------|------|
| 货币格式 | "123.45", "-1234.56" | ✓ |
| 百分比格式 | "0.0625", "0.1300" | ✓ |
| 整数格式 | "100", "-1000" | ✓ |
| 大额货币 | "999999999999.99" | ✓ |

### 不覆盖格式
| 格式类型 | 示例 | 状态 |
|---------|------|------|
| 科学计数法 | "1.23E5" | ✗ |
| 超长小数 | "0.123456789" | ✗ |
| 多个小数点 | "123.45.67" | ✗ |

### 基线性能（JDK 25.0.3）
| 格式 | ns/op |
|------|-------|
| "123.45" | 21.58 |
| "1000.00" | 15.27 |
| "0.13" | 11.86 |

### 预期提升
| 格式类型 | 预期提升 |
|---------|---------|
| 货币格式 | 40-60% |
| 百分比格式 | 40-60% |
| 整数格式 | 30-50% |

---

## 4. 完整优化补丁

### 文件
`bigDecimal_optimized_tax_rate.patch` - 包含乘法和除法优化的完整补丁

### 应用方法
```bash
cd /path/to/jdk/src/java.base/share/classes/java/math/
patch -p1 < bigDecimal_optimized_tax_rate.patch
patch -p1 < bigdecimal_string_fastpath.patch
```

### 编译验证
```bash
cd /path/to/jdk
bash configure
make build
```

---

## 5. 性能测试

### 可用测试
| 测试文件 | 测试内容 |
|---------|---------|
| `TaxRateBenchmark.java` | 税率计算场景 |
| `StringParseBenchmark.java` | 字符串解析性能 |
| `DivideBenchmark.java` | 除法操作 |
| `SimpleBenchmark.java` | 基础乘法 |

### 运行测试
```bash
# 税率计算场景
javac TaxRateBenchmark.java && java TaxRateBenchmark

# 字符串解析
javac StringParseBenchmark.java && java StringParseBenchmark

# 除法测试
javac DivideBenchmark.java && java DivideBenchmark
```

---

## 6. 实际应用场景

### 税率计算
```java
BigDecimal price = new BigDecimal("12345.67");  // $123.4567
BigDecimal taxRate = new BigDecimal("0.13");    // 13%

// 计算税额 - 使用乘法快速路径
BigDecimal taxAmount = price.multiply(taxRate);

// 计算不含税价 - 使用除法快速路径
BigDecimal priceWithoutTax = price.divide(
    BigDecimal.ONE.add(taxRate),
    2,
    RoundingMode.HALF_UP
);
```

### 电商订单处理
```java
// 从数据库或 API 读取的价格字符串 - 使用字符串快速路径
BigDecimal itemPrice = new BigDecimal("999.99");
BigDecimal quantity = new BigDecimal("10");

BigDecimal lineTotal = itemPrice.multiply(quantity);
```

---

## 7. 下一步

### 立即可做
- [ ] 编译修改后的 JDK
- [ ] 运行完整性能测试验证预期
- [ ] 创建 JEP 提案提交给 OpenJDK

### 未来优化方向
- 加法/减法快速路径（相同 scale 优化）
- compareTo 快速路径优化
- valueOf(double) 精确转换优化

---

## 8. 许可证

遵循 GPL v2 with Classpath Exception（与 OpenJDK 一致）

---

## 9. 参考资源

- [OpenJDK BigDecimal Source](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/math/BigDecimal.java)
- [JDK 25 文档](https://docs.oracle.com/en/java/javase/25/docs/api/)
- [项目仓库](https://github.com/xuefenghao5121/java-test/tree/main/bigdecimal-optimization)
