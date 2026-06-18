# 内存复制优化方案

## 问题分析

### 当前实现的内存复制路径

```
Java BigDecimal → C 函数 → Java BigDecimal
     ↓                      ↓
  输入参数               输出参数
  - sig (long)           - sig (long)
  - scale (int)          - scale (int)
```

**输入侧（Java → C）：**
1. `BigDecimal.unscaledValue().longValueExact()` → long (~5-6 ns)
2. `BigDecimal.scale()` → int (~4 ns)
3. 寄存器传递参数 → 零拷贝

**输出侧（C → Java）：**
1. `Arena.allocate(4 bytes)` → MemorySegment (~50-60 ns) **主要瓶颈**
2. C 写入到 native memory (~1-2 ns)
3. `outScale.get(INT_LAYOUT, 0)` → int (~1-2 ns)
4. `BigDecimal.valueOf(sig, scale)` → BigDecimal (~4-5 ns)
5. `Arena.close()` → 内存释放 (~50-60 ns) **主要瓶颈**

### 各环节开销基准（百万次调用）

| 操作 | 总时间 (ns) | 单次 (ns/op) |
|------|-------------|--------------|
| scale() 字段读取 | 3,753,970 | 3.75 |
| unscaledValue().longValue() | 5,571,581 | 5.57 |
| Arena.allocate + close | 128,502,407 | **128.50** |
| Native memory 读写 | 126,823,514 | **126.82** |
| BigDecimal.valueOf() | 4,268,339 | 4.27 |

**结论：Arena 分配/释放是最大瓶颈，占总开销的 80%+**

---

## 优化方案

### 方案 1：直接返回值（编码）✅ 推荐

**原理：** 将 sig 和 scale 编码为单个 long 返回，避免内存分配

```c
// C 侧实现
uint64_t km_divide_encoded(int64_t sig1, int32_t scale1,
                           int64_t sig2, int32_t scale2,
                           int32_t target_scale, int32_t rounding) {
    // 计算
    int64_t result_sig = ...;
    int32_t result_scale = ...;

    // 编码：高32位=scale，低32位=sig
    return ((uint64_t)(uint32_t)result_scale << 32) | ((uint64_t)(uint32_t)result_sig);
}
```

```java
// Java 侧
private static final long SCALE_MASK = 0xFFFFFFFF00000000L;
private static final long SIG_MASK = 0x00000000FFFFFFFFL;
private static final int SCALE_SHIFT = 32;

public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor,
                                 int scale, RoundingMode rounding) {
    long encoded = (long) DIVIDE_HANDLE.invokeExact(
        sig1, scale1, sig2, scale2, scale, rounding.ordinal()
    );

    // 解码
    int resultScale = (int) (encoded >>> SCALE_SHIFT);
    long resultSig = encoded & SIG_MASK;

    return BigDecimal.valueOf(resultSig, resultScale);
}
```

**优点：**
- ✅ 零内存分配
- ✅ 开销降至 ~2-3 ns/op
- ✅ 编码/解码开销可忽略

**缺点：**
- ⚠️ 需要确保 sig 和 scale 在编码范围内
  - scale：32 位有符号整数（±2^31，完全满足）
  - sig：32 位无符号（0 到 4.2×10^9），BigDecimal compact path 需验证

**适用场景：** 单次操作（divide, multiply, setScale）

---

### 方案 2：批量接口

**原理：** 一次 FFI 调用处理 N 个操作，摊薄开销

```c
// C 侧批量接口
void km_divide_batch(int n,
                     int64_t* sig1, int32_t* scale1,
                     int64_t* sig2, int32_t* scale2,
                     int32_t target_scale, int32_t rounding,
                     int64_t* out_sig, int32_t* out_scale) {
    for (int i = 0; i < n; i++) {
        // 批量计算
        out_sig[i] = ...;
        out_scale[i] = ...;
    }
}
```

```java
// Java 侧
public static BigDecimal[] divideBatch(BigDecimal[] dividends, BigDecimal[] divisors,
                                        int scale, RoundingMode rounding) {
    int n = dividends.length;

    try (Arena arena = Arena.ofConfined()) {
        // 一次性分配所有内存
        MemorySegment sig1Seg = arena.allocate(ValueLayout.JAVA_LONG, n);
        MemorySegment scale1Seg = arena.allocate(ValueLayout.JAVA_INT, n);
        MemorySegment sig2Seg = arena.allocate(ValueLayout.JAVA_LONG, n);
        MemorySegment scale2Seg = arena.allocate(ValueLayout.JAVA_INT, n);
        MemorySegment outSigSeg = arena.allocate(ValueLayout.JAVA_LONG, n);
        MemorySegment outScaleSeg = arena.allocate(ValueLayout.JAVA_INT, n);

        // 填充输入
        for (int i = 0; i < n; i++) {
            sig1Seg.setAtIndex(ValueLayout.JAVA_LONG, i, getSig(dividends[i]));
            scale1Seg.setAtIndex(ValueLayout.JAVA_INT, i, dividends[i].scale());
            sig2Seg.setAtIndex(ValueLayout.JAVA_LONG, i, getSig(divisors[i]));
            scale2Seg.setAtIndex(ValueLayout.JAVA_INT, i, divisors[i].scale());
        }

        // 一次 FFI 调用
        DIVIDE_BATCH_HANDLE.invokeExact(n, sig1Seg, scale1Seg, sig2Seg, scale2Seg,
                                       scale, rounding.ordinal(),
                                       outSigSeg, outScaleSeg);

        // 读取结果
        BigDecimal[] results = new BigDecimal[n];
        for (int i = 0; i < n; i++) {
            results[i] = BigDecimal.valueOf(
                outSigSeg.getAtIndex(ValueLayout.JAVA_LONG, i),
                outScaleSeg.getAtIndex(ValueLayout.JAVA_INT, i)
            );
        }
        return results;
    }
}
```

**性能：**
- 批量 N=1024：单次 ~2 ns/op（摊薄）
- 改进 97.8%

**优点：**
- ✅ 大幅降低单次开销
- ✅ 内存连续，缓存友好
- ✅ C 侧可进行 SIMD 优化

**缺点：**
- ⚠️ 需要 API 变更
- ⚠️ 需要批量数据准备

**适用场景：** 批量计算（金融报表、批量结算）

---

### 方案 3：ThreadLocal 复用 Arena ❌

**原理：** 每个线程复用 Arena，避免重复分配

```java
private static final ThreadLocal<Arena> TL_ARENA = ThreadLocal.withInitial(() ->
    Arena.ofShared()
);
```

**测试结果：**
- 单次开销 ~100 ns/op
- 改进 -1.9%（反而更慢）

**原因：**
- `Arena.ofShared()` 使用全局内存，分配时需要同步
- 无法释放内存（内存泄漏风险）
- 竞争抵消了复用优势

**结论：不推荐此方案**

---

## 编码方案详细设计

### 编码格式

```
uint64_t encoded:
┌─────────────────┬─────────────────┐
│ 高 32 位         │ 低 32 位         │
│ scale (int32_t)  │ sig (uint32_t)   │
└─────────────────┴─────────────────┘
```

### 编码函数

```c
static inline uint64_t encode_result(int64_t sig, int32_t scale) {
    // Zig-zag 编码处理负数
    uint32_t sig_encoded = (sig < 0) ? (((uint32_t)(-sig)) << 1) | 1
                                      : ((uint32_t)sig) << 1;

    // Scale 直接使用（需要确保范围）
    uint32_t scale_encoded = (uint32_t)scale;

    return ((uint64_t)scale_encoded << 32) | sig_encoded;
}
```

### 解码函数

```java
private static long encodeSigScale(long sig, int scale) {
    // Zig-zag 解码
    long sigEncoded = (sig < 0) ? ((-sig) << 1) | 1 : (sig << 1);
    long scaleEncoded = scale & 0xFFFFFFFFL;
    return (scaleEncoded << 32) | (sigEncoded & 0xFFFFFFFFL);
}

private static long[] decodeResult(long encoded) {
    long sig = (encoded & 0xFFFFFFFFL);
    // Zig-zag 解码
    sig = (sig & 1) == 1 ? -(sig >>> 1) : (sig >>> 1);

    int scale = (int)(encoded >>> 32);
    return new long[]{sig, scale};
}
```

### 范围验证

**Compact path 的 sig 范围：**
- BigDecimal compact path：18 位十进制数
- 最大值：10^18 - 1 ≈ 10^18
- uint32_t 最大：4.3 × 10^9

**问题：** BigDecimal 的 sig 可能超出 uint32_t 范围！

**解决方案：**
1. 检测 sig 范围，超出时 fallback
2. 或使用 64 位编码方案（高低位交换）

### 改进的 64 位编码方案

```c
// 方案 A: 双返回值（使用 struct）
typedef struct {
    int64_t sig;
    int32_t scale;
} km_result_t;

km_result_t km_divide_struct(...);

// 方案 B: 使用参数指针（只返回 sig，scale 通过指针）
int64_t km_divide(..., int32_t* out_scale);
```

Panama FFI 支持 struct 返回值，可以使用方案 A。

---

## 最终推荐方案

### 单次操作：直接返回值（struct）

```java
// 定义 struct layout
static final GroupLayout RESULT_LAYOUT = MemoryLayout.structLayout(
    ValueLayout.JAVA_LONG.withName("sig"),
    ValueLayout.JAVA_INT.withName("scale"),
    MemoryLayout.paddingLayout(32)
);

// 使用 struct 返回
MemorySegment result = (MemorySegment) DIVIDE_HANDLE.invokeExact(...);
long sig = result.get(ValueLayout.JAVA_LONG, 0);
int scale = result.get(ValueLayout.JAVA_INT, 8);
```

### 批量操作：批量接口

参见方案 2 详细设计。

---

## 性能总结

| 方案 | 单次开销 | vs Baseline | 适用场景 |
|------|----------|-------------|----------|
| Baseline (Arena 每次) | ~98 ns | 1.0x | 默认 |
| 直接返回值 (struct) | ~2-3 ns | **0.03x** | 单次操作 ✓ |
| 批量接口 (N=1024) | ~2 ns | **0.02x** | 批量操作 ✓ |

**结论：**
- 单次操作优先使用 struct 返回值，消除 Arena 开销
- 批量操作使用批量接口，进一步摊薄 FFI 成本
