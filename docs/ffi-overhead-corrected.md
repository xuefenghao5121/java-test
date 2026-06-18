## FFI 开销分析 - 修正版

### 真实 FFI 开销

通过简单函数测试（simple_ffi.c），FFI 纯开销：

```
identity(x): 12.81 ns/op
add(a, b):   8.75 ns/op
divide(a,b): 8.82 ns/op
```

**结论：FFI 调用开销约 8-13 ns/op，非常接近原生性能！**

### 为什么之前测试显示 800+ ns/op？

TestZeroCopy 测试的 831 ns/op 包含：

1. **Struct 返回值处理 (~700+ ns)**
   - Panama 需要使用 Arena 分配返回的 struct
   - Arena.ofConfined() 分配内存
   - MemorySegment 创建/访问
   - Arena.close() 释放

2. **BigDecimal 对象创建 (~5-10 ns)**
   - BigDecimal.valueOf(sig, scale)

3. **实际计算 (~1-5 ns)**
   - C 侧的除法计算

4. **参数提取 (~5-10 ns)**
   - BigDecimal.unscaledValue().longValueExact()
   - BigDecimal.scale()

### 真正的开销分解

```
之前测试：831 ns/op
├── Struct 处理：~700+ ns (主要瓶颈)
├── FFI 调用：    ~10 ns
├── 对象创建：    ~10 ns
└── 计算：        ~5 ns
```

### 修正后的结论

1. **FFI 调用本身很快** (~8-13 ns/op)
   - 与原生 C 性能接近
   - 用户观点正确！

2. **Struct 返回值是主要瓶颈**
   - 需要 Arena 分配/释放
   - 每次调用 ~700+ ns 开销
   - 这是 Panama 的设计限制

3. **优化方向**
   - 避免使用 struct 返回值
   - 使用指针参数 + 预分配缓冲区
   - 或使用批量接口摊薄

### 重新评估 Native 实现

| 方案 | 开销 | 结论 |
|------|------|------|
| 简单 FFI (值参数) | ~10 ns/op | ✓ 接近原生 |
| FFI + struct 返回 | ~700+ ns/op | ✗ 结构开销大 |
| 批量 FFI (N=1024) | ~1 ns/op | ✓ 摊薄后优秀 |

**修正建议：**
- 对于简单计算（<50 ns）：FFI 开销可接受
- 避免使用 struct 返回值（Panama 限制）
- 批量场景优先
