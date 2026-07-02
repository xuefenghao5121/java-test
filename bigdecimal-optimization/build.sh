#!/bin/bash
# Build script for BigDecimal Optimization Project

set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_MAIN_DIR="$PROJECT_DIR/src/main/java"
SRC_TEST_DIR="$PROJECT_DIR/src/test/java"
TARGET_DIR="$PROJECT_DIR/target/classes"

echo "Building BigDecimal Optimization Project..."
echo "Main Source: $SRC_MAIN_DIR"
echo "Test Source: $SRC_TEST_DIR"
echo "Target: $TARGET_DIR"

# Create target directory
mkdir -p "$TARGET_DIR"

# Compile main sources first
if [ -d "$SRC_MAIN_DIR" ]; then
    javac -d "$TARGET_DIR" "$SRC_MAIN_DIR"/**/*.java 2>/dev/null || \
    javac -d "$TARGET_DIR" $(find "$SRC_MAIN_DIR" -name "*.java")
fi

# Compile test sources
if [ -d "$SRC_TEST_DIR" ]; then
    javac -cp "$TARGET_DIR" -d "$TARGET_DIR" "$SRC_TEST_DIR"/*.java
fi

echo "Build complete!"
echo ""
echo "Available benchmarks:"
echo "  java -cp $TARGET_DIR TaxRateBenchmark"
echo "  java -cp $TARGET_DIR SimpleBenchmark"
echo "  java -cp $TARGET_DIR DivideBenchmark"
echo "  java -cp $TARGET_DIR TaxRateAnalysis"
