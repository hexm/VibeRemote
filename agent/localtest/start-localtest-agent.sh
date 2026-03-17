#!/bin/bash

# LightScript Agent 本地测试启动脚本

echo "========================================"
echo "LightScript Agent 本地测试启动"
echo "========================================"
echo

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 检查必要文件
if [ ! -f "agent.jar" ]; then
    echo "[ERROR] agent.jar not found"
    exit 1
fi

if [ ! -f "agent.properties" ]; then
    echo "[ERROR] agent.properties not found"
    exit 1
fi

# 创建日志目录
mkdir -p logs

# 检查Java环境
if ! command -v java >/dev/null 2>&1; then
    echo "[ERROR] Java not found in PATH"
    exit 1
fi

echo "[INFO] 启动Agent..."
echo "       工作目录: $SCRIPT_DIR"
echo "       配置文件: agent.properties"
echo "       日志目录: logs/"
echo "       按 Ctrl+C 停止Agent"
echo "========================================"

# 启动Agent
java -Xms32m -Xmx128m -XX:MaxMetaspaceSize=64m \
     -Dfile.encoding=UTF-8 \
     -Djava.awt.headless=true \
     -jar agent.jar