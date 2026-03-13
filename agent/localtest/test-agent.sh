#!/bin/bash

# LightScript Agent 测试脚本

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "========================================="
echo "LightScript Agent 本地测试环境"
echo "========================================="

# 检查必要文件
echo "1. 检查文件完整性..."
required_files=("agent.jar" "upgrader.jar" "start-agent.sh")
missing_files=()

for file in "${required_files[@]}"; do
    if [ ! -f "$file" ]; then
        missing_files+=("$file")
    fi
done

if [ ${#missing_files[@]} -gt 0 ]; then
    echo "❌ 缺少必要文件: ${missing_files[*]}"
    exit 1
fi

echo "✅ 所有必要文件都存在"

# 检查Java环境
echo ""
echo "2. 检查Java环境..."
if command -v java >/dev/null 2>&1; then
    java_version=$(java -version 2>&1 | head -n 1)
    echo "✅ Java环境: $java_version"
else
    echo "❌ 未找到Java环境，请安装Java"
    exit 1
fi

# 检查当前版本
echo ""
echo "3. 检查Agent版本..."
if [ -f "agent.jar" ]; then
    # 从MANIFEST.MF读取版本
    version=$(jar xf agent.jar META-INF/MANIFEST.MF && grep "Implementation-Version" META-INF/MANIFEST.MF | cut -d' ' -f2 2>/dev/null)
    if [ -n "$version" ]; then
        echo "✅ Agent版本: $version"
    else
        echo "⚠️  无法读取版本信息，使用默认版本"
    fi
    # 清理临时文件
    rm -f META-INF/MANIFEST.MF
    rm -rf META-INF
fi

# 检查服务器连接
echo ""
echo "4. 检查服务器连接..."
server_url="${1:-http://localhost:8080}"
if curl -s --connect-timeout 5 "$server_url/actuator/health" >/dev/null 2>&1; then
    echo "✅ 服务器连接正常: $server_url"
else
    echo "⚠️  无法连接到服务器: $server_url"
    echo "   请确保服务器正在运行"
fi

# 清理环境
echo ""
echo "5. 清理测试环境..."
# 清理可能存在的锁文件
rm -f ~/.lightscript/.agent.lock
# 清理旧的日志文件（现在在logs目录下）
rm -rf logs/
echo "✅ 环境清理完成"

echo ""
echo "========================================="
echo "测试环境准备完成！"
echo "========================================="
echo ""
echo "启动命令:"
echo "  ./start-agent.sh                    # 使用默认配置"
echo ""
echo "监控命令:"
echo "  tail -f logs/agent.log              # 监控主要日志"
echo "  tail -f logs/agent-startup.log      # 监控启动日志"
echo "  tail -f logs/upgrade.log            # 监控升级日志"
echo ""
echo "停止命令:"
echo "  pkill -f 'java -jar agent.jar'      # 停止Agent"
echo ""

# 如果指定了 --start 参数，自动启动Agent
if [ "$2" = "--start" ]; then
    echo "自动启动Agent..."
    ./start-agent.sh
fi