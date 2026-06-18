# FastBigDecimal - 鲲鹏数学库 Java 对接

通过 Java 21 Panama FFI 对接鲲鹏数学库 (libm)，加速 BigDecimal 的 `divide`、`multiply`、`setScale` 操作。

## 架构

```
Java 应用 → FastBigDecimal → Panama FFI → libm (Kunpeng ARM64/NEON)
```

## JDK 版本要求

**JDK 21+** (Panama Foreign Function API 正式版)

## 使用方式

```java
import static com.kunpeng.math.FastBigDecimal.*;

// 替换原有 BigDecimal 操作
BigDecimal result = divide(a, b, 2, RoundingMode.HALF_UP);
BigDecimal product = multiply(x, y);
BigDecimal scaled = setScale(value, 4);
```

## 编译

```bash
# Java 代码
javac --enable-preview -source 21 src/main/java/com/kunpeng/math/*.java

# 运行时需要：
# 1. JDK 21+
# 2. libm.so 在 java.library.path 中
java -Djava.library.path=/usr/local/lib -cp src/main/java com.kunpeng.math.FastBigDecimal
```

## C 库接口规范

见 [km_math.h](src/main/resources/native/include/km_math.h)

核心函数：
- `km_divide` - 除法
- `km_multiply` - 乘法  
- `km_setscale` - 设置 scale

## Fast Path 条件

- 数值精度 ≤ 18 位十进制数（可放入 long）
- 自动 fallback 到标准 BigDecimal 实现

## 状态

- [ ] Java 接口完成
- [ ] C 库实现（libm）
- [ ] 性能验证
