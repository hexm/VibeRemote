#!/bin/bash

# Agent配置测试脚本

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "========================================="
echo "Agent配置测试"
echo "========================================="

echo "1. 测试默认配置（无配置文件）"
if [ -f "agent.properties" ]; then
    mv agent.properties agent.properties.bak
    echo "   暂时移除配置文件"
fi

echo "   启动Agent（5秒后自动停止）..."
timeout 5s ./start-agent.sh > test-default.log 2>&1 &
sleep 6
pkill -f "java -jar agent.jar" 2>/dev/null

echo "   检查默认配置日志..."
if grep -q "server.url" test-default.log; then
    echo "   ✅ 使用了内置默认配置"
else
    echo "   ⚠️  未找到配置信息"
fi

echo ""
echo "2. 测试配置文件"
if [ -f "agent.properties.bak" ]; then
    mv agent.properties.bak agent.properties
    echo "   恢复配置文件"
fi

echo "   启动Agent（5秒后自动停止）..."
timeout 5s ./start-agent.sh > test-config.log 2>&1 &
sleep 6
pkill -f "java -jar agent.jar" 2>/dev/null

echo "   检查配置文件加载..."
if grep -q "Using configuration file" test-config.log; then
    echo "   ✅ 成功加载配置文件"
else
    echo "   ⚠️  未找到配置文件加载信息"
fi

echo ""
echo "3. 测试环境变量覆盖"
export LIGHTSCRIPT_SERVER_URL="http://test-server:9999"
export LIGHTSCRIPT_REGISTER_TOKEN="test-token-123"

echo "   设置环境变量:"
echo "   LIGHTSCRIPT_SERVER_URL=$LIGHTSCRIPT_SERVER_URL"
echo "   LIGHTSCRIPT_REGISTER_TOKEN=$LIGHTSCRIPT_REGISTER_TOKEN"

echo "   启动Agent（5秒后自动停止）..."
timeout 5s ./start-agent.sh > test-env.log 2>&1 &
sleep 6
pkill -f "java -jar agent.jar" 2>/dev/null

echo "   检查环境变量覆盖..."
if grep -q "test-server:9999" test-env.log; then
    echo "   ✅ 环境变量覆盖成功"
else
    echo "   ⚠️  环境变量覆盖失败"
fi

# 清理环境变量
unset LIGHTSCRIPT_SERVER_URL
unset LIGHTSCRIPT_REGISTER_TOKEN

echo ""
echo "4. 测试命令行参数覆盖（向后兼容）"
echo "   启动Agent（5秒后自动停止）..."
timeout 5s bash -c './start-agent.sh & sleep 1; java -jar agent.jar "http://cmdline-server:7777" "cmdline-token"' > test-cmdline.log 2>&1 &
sleep 6
pkill -f "java -jar agent.jar" 2>/dev/null

echo "   检查命令行参数覆盖..."
if grep -q "cmdline-server:7777" test-cmdline.log; then
    echo "   ✅ 命令行参数覆盖成功"
else
    echo "   ⚠️  命令行参数覆盖失败（启动脚本不再接收参数）"
fi

echo ""
echo "========================================="
echo "配置测试完成"
echo "========================================="

echo "配置优先级验证:"
echo "1. 环境变量 > 配置文件 > 默认值"
echo "2. 启动脚本不再接收参数，配置通过配置文件管理"
echo ""

echo "日志文件:"
echo "- test-default.log  (默认配置测试)"
echo "- test-config.log   (配置文件测试)"
echo "- test-env.log      (环境变量测试)"
echo "- test-cmdline.log  (命令行参数测试)"
echo ""

echo "清理命令:"
echo "rm -f test-*.log"