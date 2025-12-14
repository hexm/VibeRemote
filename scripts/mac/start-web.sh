#!/bin/bash

# 获取脚本所在目录的父目录（项目根目录）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
WEB_DIR="$PROJECT_ROOT/web"

echo "========================================"
echo "LightScript Web - Simple Start"
echo "========================================"
echo

if [ ! -d "$WEB_DIR" ]; then
    echo "[ERROR] web directory not found: $WEB_DIR"
    exit 1
fi

# 优先使用 Node http-server
if command -v http-server &> /dev/null; then
    echo "[INFO] Starting with Node http-server on http://localhost:3000"
    cd "$WEB_DIR"
    http-server . -p 3000 -c-1 --cors
    exit 0
fi

# 备选使用 Python
if command -v python3 &> /dev/null; then
    echo "[INFO] Starting with Python http.server on http://localhost:3000"
    cd "$WEB_DIR"
    python3 -m http.server 3000
    exit 0
elif command -v python &> /dev/null; then
    echo "[INFO] Starting with Python http.server on http://localhost:3000"
    cd "$WEB_DIR"
    python -m http.server 3000
    exit 0
fi

echo "[ERROR] Neither http-server nor python found in PATH."
echo "        Install Node (and http-server) or Python, or open $WEB_DIR/index.html directly in browser."
echo
echo "安装建议:"
echo "- 安装 Node.js: brew install node"
echo "- 安装 http-server: npm install -g http-server"
echo "- 或安装 Python: brew install python"

echo
echo "========================================"