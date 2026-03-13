#!/bin/bash

echo "========================================="
echo "测试新的锁文件机制"
echo "========================================="

# 测试1：在当前目录启动Agent
echo "1. 测试当前目录锁文件..."
CURRENT_DIR=$(pwd)
echo "   当前目录: $CURRENT_DIR"

# 计算预期的锁文件名（使用Java的hashCode算法）
EXPECTED_HASH=$(echo -n "$CURRENT_DIR" | python3 -c "import sys; print(hex(hash(sys.stdin.read().strip()) & 0xffffffff)[2:])")
EXPECTED_LOCK_FILE="$HOME/.lightscript/.agent-${EXPECTED_HASH}.lock"
echo "   预期锁文件: $EXPECTED_LOCK_FILE"

# 清理可能存在的锁文件
rm -f "$EXPECTED_LOCK_FILE"

# 启动Agent（后台运行5秒后自动停止）
echo "   启动Agent测试..."
timeout 5s ./agent/localtest/start-agent.sh > /dev/null 2>&1 &
AGENT_PID=$!

# 等待Agent启动
sleep 2

# 检查锁文件是否创建
if [ -f "$EXPECTED_LOCK_FILE" ]; then
    echo "   ✅ 锁文件已创建"
    echo "   锁文件内容:"
    cat "$EXPECTED_LOCK_FILE" | sed 's/^/      /'
else
    echo "   ❌ 锁文件未创建"
fi

# 尝试启动第二个Agent实例
echo "   尝试启动第二个Agent实例..."
timeout 3s ./agent/localtest/start-agent.sh > test-second-agent.log 2>&1 &
SECOND_PID=$!
sleep 1

# 检查第二个实例是否被阻止
if grep -q "Another Agent instance is already running" test-second-agent.log; then
    echo "   ✅ 第二个实例被正确阻止"
else
    echo "   ❌ 第二个实例没有被阻止"
    echo "   日志内容:"
    cat test-second-agent.log | sed 's/^/      /'
fi

# 清理
kill $AGENT_PID $SECOND_PID 2>/dev/null
wait $AGENT_PID $SECOND_PID 2>/dev/null
rm -f test-second-agent.log
rm -f "$EXPECTED_LOCK_FILE"

echo ""
echo "2. 测试不同目录的Agent实例..."

# 创建临时目录
TEMP_DIR=$(mktemp -d)
cp -r agent/localtest/* "$TEMP_DIR/"
echo "   临时目录: $TEMP_DIR"

# 在临时目录中启动Agent
echo "   在临时目录启动Agent..."
(cd "$TEMP_DIR" && timeout 3s ./start-agent.sh > /dev/null 2>&1) &
TEMP_AGENT_PID=$!

# 在当前目录启动Agent
echo "   在当前目录启动Agent..."
timeout 3s ./agent/localtest/start-agent.sh > test-current-agent.log 2>&1 &
CURRENT_AGENT_PID=$!

sleep 2

# 检查两个Agent是否都能启动（因为在不同目录）
TEMP_HASH=$(echo -n "$TEMP_DIR" | python3 -c "import sys; print(hex(hash(sys.stdin.read().strip()) & 0xffffffff)[2:])")
CURRENT_HASH=$(echo -n "$CURRENT_DIR" | python3 -c "import sys; print(hex(hash(sys.stdin.read().strip()) & 0xffffffff)[2:])")

TEMP_LOCK="$HOME/.lightscript/.agent-${TEMP_HASH}.lock"
CURRENT_LOCK="$HOME/.lightscript/.agent-${CURRENT_HASH}.lock"

if [ -f "$TEMP_LOCK" ] && [ -f "$CURRENT_LOCK" ]; then
    echo "   ✅ 两个不同目录的Agent都能正常启动"
    echo "   临时目录锁文件: $TEMP_LOCK"
    echo "   当前目录锁文件: $CURRENT_LOCK"
else
    echo "   ❌ 不同目录的Agent启动有问题"
    echo "   临时锁文件存在: $([ -f "$TEMP_LOCK" ] && echo "是" || echo "否")"
    echo "   当前锁文件存在: $([ -f "$CURRENT_LOCK" ] && echo "是" || echo "否")"
fi

# 清理
kill $TEMP_AGENT_PID $CURRENT_AGENT_PID 2>/dev/null
wait $TEMP_AGENT_PID $CURRENT_AGENT_PID 2>/dev/null
rm -rf "$TEMP_DIR"
rm -f "$TEMP_LOCK" "$CURRENT_LOCK"
rm -f test-current-agent.log

echo ""
echo "========================================="
echo "锁文件机制测试完成"
echo "========================================="