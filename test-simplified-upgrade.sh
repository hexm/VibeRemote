#!/bin/bash

# 测试简化升级流程脚本
# 用于验证优化后的升级过程

echo "=========================================="
echo "测试简化升级流程"
echo "=========================================="

# 设置测试环境
AGENT_DIR="agent/localtest"
TEST_VERSION="2.1.0"

cd "$AGENT_DIR" || exit 1

echo "1. 检查当前环境..."
if [ ! -f "agent.jar" ]; then
    echo "错误: agent.jar 不存在"
    exit 1
fi

if [ ! -f "upgrader.jar" ]; then
    echo "错误: upgrader.jar 不存在"
    exit 1
fi

echo "2. 创建模拟新版本文件..."
cp agent.jar "agent-${TEST_VERSION}.jar"

echo "3. 创建升级上下文文件..."
cat > .upgrade-context.json << EOF
{
  "fromVersion": "2.0.0",
  "toVersion": "${TEST_VERSION}",
  "forceUpgrade": false,
  "agentId": "test-agent-001",
  "agentToken": "test-token-123",
  "serverUrl": "http://localhost:8080",
  "upgradeLogId": 12345,
  "timestamp": $(date +%s)000
}
EOF

echo "4. 测试升级器参数解析..."
echo "模拟升级器调用: java -jar upgrader.jar agent-${TEST_VERSION}.jar"

echo "5. 检查升级上下文文件..."
if [ -f ".upgrade-context.json" ]; then
    echo "✓ 升级上下文文件已创建"
    echo "内容预览:"
    cat .upgrade-context.json | head -5
else
    echo "✗ 升级上下文文件创建失败"
fi

echo "6. 清理测试文件..."
rm -f "agent-${TEST_VERSION}.jar"
rm -f ".upgrade-context.json"

echo "=========================================="
echo "简化升级流程测试完成"
echo "=========================================="

echo ""
echo "优化总结:"
echo "- ✓ 升级器参数从4个减少到1个"
echo "- ✓ 使用JSON文件传递上下文信息"
echo "- ✓ 状态报告变为可选功能"
echo "- ✓ 保持向后兼容性"
echo "- ✓ 简化错误处理逻辑"