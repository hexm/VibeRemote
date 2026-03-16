#!/bin/bash

# 批量日志功能测试脚本
# 生成大量日志输出来测试批量传输性能

echo "=== 批量日志功能测试开始 ==="
echo "测试时间: $(date)"

# 测试1: 生成1000行日志
echo "测试1: 生成1000行标准输出日志"
for i in {1..1000}; do
    echo "这是第 $i 行日志输出，用于测试批量传输功能"
done

echo ""
echo "测试2: 生成500行错误输出日志"
for i in {1..500}; do
    echo "这是第 $i 行错误日志，测试stderr批量收集" >&2
done

echo ""
echo "测试3: 混合输出测试"
for i in {1..200}; do
    if [ $((i % 3)) -eq 0 ]; then
        echo "标准输出: 第 $i 行"
    else
        echo "错误输出: 第 $i 行" >&2
    fi
done

echo ""
echo "测试4: 长文本行测试"
long_text="这是一个很长的日志行，包含大量文本内容，用于测试批量传输对长文本的处理能力。"
for i in {1..100}; do
    echo "$long_text 行号: $i, 时间戳: $(date '+%H:%M:%S.%3N')"
done

echo ""
echo "=== 批量日志功能测试完成 ==="
echo "总计生成约1800行日志，测试批量传输性能"