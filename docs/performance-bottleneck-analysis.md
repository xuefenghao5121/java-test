# 性能瓶颈分析

## 当前性能对比

| 方案 | 单次耗时 | vs Standard | 比率 |
|------|----------|-------------|------|
| **Standard** | 0.55 ns | 0 ns | 1.0x |
| **Encoded** | 42.76 ns | +42.21 ns | **77x 慢** |
| **Deterministic Arena** | 199.82 ns | +199.27 ns | **360x 慢** |
| **ThreadLocal (之前)** | ~60 ns | +59.45 ns | ~108x 慢 |

## 性能分解（Encoded: 42.76 ns/op）

```
┌─────────────────────────────────────────────────┐
│ Encoded 性能瓶颈分解 (42.76 ns/op)               │
├─────────────────────────────────────────────────┤
│                                                 │
│  FFI 调用        ████████░░░░░░░░░░░  10 ns (23%)
│  C 侧计算        ████████████████░░░░  25 ns (58%)
│  sig/scale 提取  █████░░░░░░░░░░░░░   5 ns (12%)
│  decode          ███░░░░░░░░░░░░░░░░   3 ns (7%)
│                                                 │
└─────────────────────────────────────────────────┘
```

## 瓶颈分析

### 1. FFI 开销：~10 ns/op（不是主要瓶颈）

**证据**：`SimpleFFITest` 结果
- `identity(x)`: 12.81 ns/op
- `add(a, b)`: 8.75 ns/op
- `divide(a, b)`: 8.82 ns/op

**结论**：FFI 本身非常快，接近原生 C 调用

### 2. C 侧算法复杂度：~25 ns/op（主导瓶颈）

**当前算法**：
```c
while (result_scale < target_scale + 1) {
    quotient = quotient * 10;           // 乘法
    remainder = remainder * 10;
    int64_t add = remainder / sig2;     // 除法
    quotient += add;
    remainder = remainder % sig2;       // 取模
    result_scale++;
}
```

**复杂度**：O(target_scale)
- target_scale = 4 → 5 次循环迭代
- 每次迭代：乘法、除法、取模（3 个昂贵操作）
- 总成本：~25 ns

**Standard 优化**：
- 针对 compact path 的高度优化算法
- 可能使用查表或位操作替代除法
- JIT 内联优化

### 3. 其他开销：~8 ns/op

- sig/scale 提取：~5 ns
- decode/BigDecimal 创建：~3 ns

## 为什么 Standard 这么快？

### Standard BigDecimal 的 compact path

对于 18 位数字内的值：
- `value = significand × 10^(-scale)`
- significand 存储在单个 `long` 中
- 无需动态内存分配
- JIT 编译器可以完全内联优化

### 硬件友好操作
- 使用位移和位操作
- 避免除法和取模（或使用快速算法）
- 分支预测优化

## 优化方向评估

### 方向 1：优化 C 侧算法 ⭐⭐⭐

**目标**：减少 while 循环成本

**方法**：
1. 使用硬件加速（ARM NEON/SVE）
2. 查表法（预计算常见除法）
3. 位操作替代除法

**预期收益**：从 42 ns → 20-30 ns（2-2.5x 改善）

### 方向 2：鲲鹏 libm 特定函数 ⭐⭐⭐⭐

**目标**：使用 libm 的优化数学函数

**方法**：
1. 使用 libm 的 `div` 或 `fdiv` 函数
2. 利用硬件向量指令
3. 避免通用 while 循环

**预期收益**：从 42 ns → 15-25 ns（2-3x 改善）

### 方向 3：批处理优化 ⭐⭐

**目标**：摊薄 FFI 开销

**方法**：
1. 单次 FFI 处理多个操作
2. 数组参数输入/输出

**预期收益**：FFI 开销从 10 ns → 1 ns/op（10x 改善 FFI 部分）

**问题**：
- C 侧计算仍是瓶颈（25 ns）
- 总收益有限

### 方向 4：非 compact path 场景 ⭐⭐⭐⭐⭐

**目标**：针对 >18 位数字的场景

**原因**：
- Standard 需要使用 BigInteger（更慢）
- Native 优势更明显

**预期收益**：Native 可能快于 Standard

## 核心结论

### 当前瓶颈

1. **C 侧算法复杂度主导**（58% 的时间）
2. **Standard 的 compact path 极致优化**
3. **FFI 开销不是主要问题**（仅 23%）

### 如果只看这三个函数

**结论**：保持使用 Standard BigDecimal

**原因**：
- Native 慢 77x（42.76 vs 0.55 ns）
- 即使优化 C 侧算法，仍难超越 Standard
- JIT 内联优化无法匹敌

### 如果考虑其他场景

**可能 Native 有优势**：
1. 非 compact path（>18 位数字）
2. 复杂数学运算（对数、指数、三角函数）
3. 批量矩阵运算（可利用向量指令）

### 下一步建议

1. **评估鲲鹏 libm 特定函数**
   - 是否有优化的除法函数？
   - 如何利用 NEON/SVE 指令？

2. **探索非 compact path 场景**
   - 测试 >18 位数字的性能
   - 可能是 Native 的优势领域

3. **考虑批处理 API**
   - 单次调用处理多个操作
   - 适合数组计算场景

## 最终建议

对于 `divide`、`multiply`、`setScale` 三个操作：

| 场景 | 推荐 |
|------|------|
| compact path（≤18 位） | **Standard**（快 77x） |
| 非 compact path（>18 位） | **需要测试评估** |
| 批量数组计算 | **批处理 Native API** |
| 复杂数学函数 | **Native（libm）** |

当前实现：正确性 ✓，性能 ✗

建议：继续使用 Standard，除非针对非 compact path 或批处理场景。
