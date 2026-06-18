# kunpeng-math - BigDecimal Native 加速

通过 Panama FFI 对接 Kunpeng libm / Intel MKL，优化 BigDecimal Non-Compact Path 性能。

## 背景

### BigDecimal 两种存储路径

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Compact Path (≤18 位)        │ Non-Compact Path (>18 位)                   │
├────────────────────────────────────┼────────────────────────────────────────┤
│ 存储：long intCompact              │ 存储：BigInteger intVal                   │
│ 性能：9-27 ns/op（JIT 优化）       │ 性能：260-400 ns/op（大整数运算）       │
│ 建议：使用 Standard 实现           │ 建议：使用 Native 向量化                  │
└────────────────────────────────────┴────────────────────────────────────────┘
```

### 为什么只优化 Non-Compact Path

1. **Compact Path 已极致优化** — Standard 使用 long 存储，JIT 完全内联优化
2. **Native FFI 有固定开销** (~8-13 ns) — 无法超越 Compact Path
3. **Non-Compact Path 是真正的瓶颈** — BigInteger 运算慢 10-40 倍

### 优化策略

使用 **double 向量化运算**（接受精度权衡）：
- Intel MKL VML (AVX/AVX2/AVX-512)
- 标准 libm (备选)
- 鲲鹏 libm NEON/SVE (同样适用)

## 项目结构

```
kunpeng-math/
├── src/main/java/com/kunpeng/math/
│   ├── FastBigDecimalMKL.java          # 对接实现（Java 层）
│   ├── MKLNonCompactBenchmark.java     # Non-Compact Path 验证
│   └── MicroserviceStressTest.java     # 微服务场景验证
├── src/main/c/
│   └── km_math_mkl.c                   # 对接实现（C 层）
├── src/main/resources/native/
│   └── linux-x86_64/libm_mkl.so       # 编译后的 native 库
└── docs/
    └── bigdecimal-implementation-analysis.md  # BigDecimal 实现分析
```

## 编译

### 使用标准 libm

```bash
gcc -shared -fPIC -O3 -o src/main/resources/native/linux-x86_64/libm_mkl.so \
    src/main/c/km_math_mkl.c -lm
```

### 使用 Intel MKL

```bash
gcc -shared -fPIC -O3 -DUSE_MKL -I/usr/include/mkl \
    -o src/main/resources/native/linux-x86_64/libm_mkl.so \
    src/main/c/km_math_mkl.c -lmkl_rt -lpthread -lm -ldl
```

### 鲲鹏平台（使用 NEON/SVE）

```bash
# 类似标准 libm，鲲鹏 libm 会自动使用 NEON/SVE
gcc -shared -fPIC -O3 -o libm_mkl.so km_math_mkl.c -lm
```

## 使用

### Java API

```java
import com.kunpeng.math.FastBigDecimalMKL;
import java.math.BigDecimal;

// 批量除法
BigDecimal[] dividends = { /* ... */ };
BigDecimal[] divisors = { /* ... */ };
BigDecimal[] results = FastBigDecimalMKL.divideBatch(
    dividends, divisors, 10, RoundingMode.HALF_UP
);

// 批量乘法
BigDecimal[] aArray = { /* ... */ };
BigDecimal[] bArray = { /* ... */ };
BigDecimal[] products = FastBigDecimalMKL.multiplyBatch(aArray, bArray);

// 批量 setScale
BigDecimal[] values = { /* ... */ };
BigDecimal[] scaled = FastBigDecimalMKL.setScaleBatch(
    values, 2, RoundingMode.HALF_UP
);
```

## 性能验证

### Non-Compact Path 验证

```bash
java --enable-native-access=ALL-UNNAMED --enable-preview \
    -cp . -Djava.library.path=src/main/resources/native/linux-x86_64 \
    com.kunpeng.math.MKLNonCompactBenchmark
```

### 微服务场景验证

```bash
java --enable-native-access=ALL-UNNAMED --enable-preview \
    -cp . -Djava.library.path=src/main/resources/native/linux-x86_64 \
    com.kunpeng.math.MicroserviceStressTest
```

## 性能总结

### 单线程批量（离线计算）

| 函数 | Standard (ns) | MKL (ns) | 提升 |
|------|--------------|----------|------|
| Multiply (100k) | 153.98 | 25.80 | **6x** |
| SetScale (100k) | 77.91 | 42.52 | **2x** |
| Divide (10k) | 118.64 | 82.48 | **1.4x** |

### 微服务高并发（24 线程）

| 场景 | Standard QPS | MKL QPS | 差异 |
|------|--------------|---------|------|
| Multiply | 3,788 | 3,746 | -1.1% |
| SetScale | 6,853 | 6,828 | -0.4% |
| Divide | 3,232 | 3,197 | -1.1% |

## 适用场景

| 场景 | MKL 适用性 | 说明 |
|------|-----------|------|
| 离线批处理 | ✓✓✓ 强烈推荐 | 单线程、大规模、无并发竞争 |
| 科学计算 | ✓✓ 推荐 | 可接受浮点精度 |
| 数据分析 | ✓✓ 推荐 | 批量聚合计算 |
| 微服务高并发 | ✗ 不推荐 | 多线程竞争、MKL 劣势 |

## 精度说明

- double 有 53 位尾数，可精确表示约 **15-17 位十进制数字**
- 对于 >18 位的 BigDecimal，**会有精度损失**
- 适用于可接受浮点近似的场景

## 鲲鹏平台

- 鲲鹏 920/930 的 NEON/SVE 指令集同样适用
- 预期性能与 Intel MKL 类似或更好（SVE 优势）
- 使用标准 libm 编译即可，鲲鹏 libm 会自动使用 NEON/SVE

## License

MIT License
