#!/bin/bash

# 快速验证测试环境

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "========================================="
echo "LightScript Agent 快速验证测试"
echo "========================================="

# 1. 检查文件完整性
echo "1. 检查文件完整性..."
files=("agent.jar" "upgrader.jar" "start-agent.sh" "start-agent.bat" "agent.properties")
all_good=true

for file in "${files[@]}"; do
    if [ -f "$file" ]; then
        echo "   ✅ $file"
    else
        echo "   ❌ $file (缺失)"
        all_good=false
    fi
done

if [ "$all_good" = false ]; then
    echo "❌ 文件检查失败，请重新准备测试环境"
    exit 1
fi

# 2. 检查Java环境
echo ""
echo "2. 检查Java环境..."
if command -v java >/dev/null 2>&1; then
    java_version=$(java -version 2>&1 | head -n 1)
    echo "   ✅ $java_version"
else
    echo "   ❌ Java环境未找到"
    exit 1
fi

# 3. 检查Agent版本
echo ""
echo "3. 检查Agent版本..."
if [ -f "agent.jar" ]; then
    # 尝试从JAR中读取版本信息
    version=$(java -cp agent.jar com.example.lightscript.agent.VersionUtil 2>/dev/null || echo "unknown")
    echo "   ✅ Agent版本: $version"
fi

# 4. 检查配置文件
echo ""
echo "4. 检查配置文件..."
if [ -f "agent.properties" ]; then
    server_url=$(grep "^server.url=" agent.properties | cut -d'=' -f2)
    register_token=$(grep "^server.register.token=" agent.properties | cut -d'=' -f2)
    echo "   ✅ 服务器地址: $server_url"
    echo "   ✅ 注册令牌: ${register_token:0:10}..."
fi

# 5. 测试启动脚本语法
echo ""
echo "5. 检查启动脚本..."
if bash -n start-agent.sh; then
    echo "   ✅ start-agent.sh 语法正确"
else
    echo "   ❌ start-agent.sh 语法错误"
fi

# 6. 清理环境
echo ""
echo "6. 清理测试环境..."
# 清理全局锁文件
rm -f ~/.lightscript/.agent.lock
rm -rf logs/
rm -rf backup/
echo "   ✅ 环境清理完成"

echo ""
echo "========================================="
echo "✅ 测试环境验证完成！"
echo "========================================="
echo ""
echo "可用命令："
echo "  ./start-agent.sh                    # 启动Agent"
echo "  ./test-agent.sh                     # 完整环境测试"
echo "  ./test-config.sh                    # 配置管理测试"
echo "  ./create-upgrade-test.sh            # 创建升级测试包"
echo ""
echo "监控命令："
echo "  tail -f logs/agent.log              # 主要日志"
echo "  tail -f logs/tasks.log              # 任务日志"
echo "  tail -f logs/upgrade.log            # 升级日志"
echo ""
echo "停止命令："
echo "  pkill -f 'java -jar agent.jar'      # 停止Agent"
echo ""