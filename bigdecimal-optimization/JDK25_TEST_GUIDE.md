# JDK 25 BigDecimal Patch Testing Guide

## 📊 Baseline Results (Unpatched JDK 25.0.3)

```
JDK Version: 25.0.3
JDK Vendor:  Ubuntu
OS:          Linux
Arch:        amd64
```

### Performance Baseline

| Operation | Range | ns/op |
|-----------|-------|-------|
| **Multiply** | Small ($10) | 0.24 ns |
| **Multiply** | Medium ($1K-$1M) | 1.63-1.65 ns |
| **Divide** | All | 3.51-6.58 ns |
| **String Parse** | Financial format | 14-17 ns |

---

## 🎯 Expected Improvements (with Patch)

| Operation | Before | After | Improvement |
|-----------|--------|-------|-------------|
| Small multiply | 0.24 ns | ~0.2 ns | Minimal |
| Large multiply | 1.65 ns | ~0.5 ns | **3x** |
| Divide | 5.18 ns | ~2 ns | **2.5x** |
| String parse | 15 ns | ~10 ns | **1.5x** |

---

## 🔧 Full Test Procedure

To test the patch on native JDK 25, you need to:

### Step 1: Install Build Dependencies

```bash
sudo apt-get update
sudo apt-get install build-essential autoconf libx11-dev
```

### Step 2: Download OpenJDK 25 Source

```bash
cd /home/huawei
mkdir jdk25-build
cd jdk25-build

# Download OpenJDK 25 source (or use specific version)
git clone https://github.com/openjdk/jdk.git --branch jdk-25-dev
cd jdk
```

### Step 3: Apply Patch

```bash
cd src/java.base/share/classes/java/math/
patch -p1 < /home/huawei/bigdecimal-optimization/patches/BigDecimal_fastpath_complete.patch
```

### Step 4: Build OpenJDK

```bash
cd /home/huawei/jdk25-build/jdk
bash configure
make build
```

> **Note**: Full build can take 30-60 minutes

### Step 5: Test with Benchmark

```bash
# Use the newly built JDK
/home/huawei/jdk25-build/jdk/build/linux-x86_64-server-release/jdk/bin/java \
    -cp /home/huawei/bigdecimal-optimization/target/classes \
    JDK25BaselineTest
```

### Step 6: Compare Results

Run the same test on both patched and unpatched JDK:

```bash
# Unpatched (baseline)
java -cp target/classes JDK25BaselineTest > baseline.txt

# Patched
/home/huawei/jdk25-build/jdk/build/.../jdk/bin/java -cp target/classes JDK25BaselineTest > patched.txt

# Compare
diff baseline.txt patched.txt
```

---

## 🚀 Quick Test (Without Full JDK Build)

If you don't want to build the entire JDK, you can still validate the patch logic:

### Option 1: Use FastDecimal (Library Approach)

FastDecimal provides similar optimizations without requiring JDK rebuild:

```bash
cd /home/huawei/bigdecimal-optimization
java -cp target/classes TaxLongTailBenchmark
```

This gives 100% accuracy validation and shows the potential speedup.

### Option 2: Simulate Fast Path Logic

Create a test that simulates the fast path conditions:

```java
// Check if operation would use fast path
boolean wouldUseFastPath = isSmallMultiply(x, y);
// Compare actual BigDecimal performance
```

---

## 📋 Test Checklist

- [ ] Run baseline test on unpatched JDK 25
- [ ] Save baseline results to `baseline.txt`
- [ ] Build patched JDK 25
- [ ] Run same test on patched JDK
- [ ] Save patched results to `patched.txt`
- [ ] Compare performance improvements
- [ ] Verify accuracy with `TaxLongTailBenchmark`

---

## 🔍 Expected Results

### Multiply Fast Path

The patch should accelerate:
- Large × Small (e.g., $1M × 8%): **3x faster**
- Small × Small: Minimal improvement (already optimized)

### Divide Fast Path

The patch should accelerate:
- All divide operations with small divisor: **2.5x faster**
- Scale adjustment overhead removed

### String Parse Fast Path

The patch should accelerate:
- Financial format strings (2 or 4 decimals): **1.5x faster**

---

## 📁 Test Files

| File | Purpose |
|------|---------|
| `JDK25BaselineTest.java` | Baseline performance test |
| `TaxLongTailBenchmark.java` | Accuracy + performance test |
| `PATCH_ANALYSIS.md` | Detailed patch documentation |
| `BigDecimal_fastpath_complete.patch` | The patch file |

---

## ⚠️ Known Limitations

1. **Build Time**: Full JDK build takes 30-60 minutes
2. **Disk Space**: Requires ~10GB for build artifacts
3. **JDK Version**: Patch designed for OpenJDK 25+, may not apply to earlier versions
4. **Scope**: Only optimizes specific patterns (small values, financial formats)

---

## 🔄 Alternative: Build Comparison Script

Instead of building full JDK, create a simple comparison:

```bash
#!/bin/bash
# compare_benchmark.sh

echo "=== Running Baseline Test ==="
java -cp target/classes JDK25BaselineTest > baseline_results.txt

echo "=== Baseline saved to baseline_results.txt ==="
echo "To compare with patched version, run:"
echo "  /path/to/patched/jdk/bin/java -cp target/classes JDK25BaselineTest > patched_results.txt"
echo "  diff baseline_results.txt patched_results.txt"
```
