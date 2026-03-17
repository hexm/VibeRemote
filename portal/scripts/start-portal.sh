#!/bin/bash

# LightScript 门户网站启动脚本

echo "========================================"
echo "LightScript 门户网站启动脚本"
echo "========================================"
echo

# 获取脚本所在目录的父目录（portal项目根目录）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORTAL_DIR="$(dirname "$SCRIPT_DIR")"

if [ ! -d "$PORTAL_DIR" ]; then
    echo "[ERROR] portal directory not found: $PORTAL_DIR"
    exit 1
fi

cd "$PORTAL_DIR"

# 默认端口
PORT=${LIGHTSCRIPT_PORTAL_PORT:-8002}

echo "[INFO] 启动门户网站服务器"
echo "       访问地址: http://localhost:$PORT"
echo "       工作目录: $PORTAL_DIR"
echo "       按 Ctrl+C 停止服务"
echo "========================================"

# 使用Python启动HTTP服务器
if command -v python3 >/dev/null 2>&1; then
    echo "[INFO] 使用 Python3 启动服务器"
    python3 -m http.server $PORT
elif command -v python >/dev/null 2>&1; then
    echo "[INFO] 使用 Python 启动服务器"
    python -m http.server $PORT
else
    echo "[ERROR] 需要 Python 来启动服务器"
    echo "安装建议: brew install python"
    exit 1
fi