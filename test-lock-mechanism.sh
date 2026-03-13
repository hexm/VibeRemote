#!/bin/bash

# 测试全局锁机制

echo "========================================="
echo "测试Agent全局锁机制"
echo "========================================="

# 清理可能存在的锁文件
rm -f ~/.lightscript/.agent.lock

echo "1. 测试单实例启动..."

# 启动第一个Agent实例（后台运行）
cd agent/localtest
echo "启动第一个Agent实例..."
./start-agent.sh &
AGENT1_PID=$!

# 等待第一个实例启动
sleep 5

echo "第一个Agent实例PID: $AGENT1_PID"

# 检查锁文件是否存在
if [ -f ~/.lightscript/.agent.lock ]; then
    echo "✅ 锁文件已创建: ~/.lightscript/.agent.lock"
    echo "锁文件内容:"
    cat ~/.lightscript/.agent.lock
else
    echo "❌ 锁文件未创建"
fi

echo ""
echo "2. 测试重复启动防护..."

# 尝试启动第二个Agent实例
echo "尝试启动第二个Agent实例..."
./start-agent.sh &
AGENT2_PID=$!

# 等待第二个实例尝试启动
sleep 3

# 检查第二个实例是否被阻止
if kill -0 $AGENT2_PID 2>/dev/null; then
    echo "❌ 第二个实例仍在运行，锁机制失效"
    kill $AGENT2_PID 2>/dev/null
else
    echo "✅ 第二个实例被成功阻止"
fi

echo ""
echo "3. 清理测试环境..."

# 停止第一个Agent实例
if kill -0 $AGENT1_PID 2>/dev/null; then
    echo "停止第一个Agent实例..."
    kill $AGENT1_PID
    sleep 2
    
    # 强制停止如果还在运行
    if kill -0 $AGENT1_PID 2>/dev/null; then
        kill -9 $AGENT1_PID 2>/dev/null
    fi
fi

# 检查锁文件是否被清理
sleep 2
if [ -f ~/.lightscript/.agent.lock ]; then
    echo "⚠️  锁文件仍然存在，手动清理"
    rm -f ~/.lightscript/.agent.lock
else
    echo "✅ 锁文件已自动清理"
fi

echo ""
echo "========================================="
echo "全局锁机制测试完成"
echo "========================================="