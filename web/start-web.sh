#!/bin/bash

echo "⚠️  警告: 此项目已废弃"
echo "📍 请使用新的现代化前端: web-modern"
echo "🌐 新版本地址: http://localhost:3001"
echo "🔄 启动脚本: cd ../web-modern && ./start-modern.sh"
echo ""
echo "是否仍要启动传统版本? (y/N)"
read -r response

if [[ ! "$response" =~ ^[Yy]$ ]]; then
    echo "已取消启动，建议使用新版本"
    exit 0
fi

echo "========================================"
echo "LightScript Web 前端启动脚本 (已废弃)"
echo "========================================"
echo

# 检查是否安装了 Node.js
if command -v node &> /dev/null; then
    echo "检测到 Node.js 版本:"
    node --version
    echo
    
    # 检查是否安装了 http-server
    if ! npm list -g http-server &> /dev/null; then
        echo "正在安装 http-server..."
        npm install -g http-server
    fi
    
    echo "使用 Node.js http-server 启动前端服务..."
    echo "访问地址: http://localhost:3000"
    echo "按 Ctrl+C 停止服务"
    echo "========================================"
    http-server . -p 3000 -c-1 --cors

elif command -v python3 &> /dev/null; then
    echo "检测到 Python3 版本:"
    python3 --version
    echo
    echo "使用 Python3 启动 HTTP 服务器..."
    echo "访问地址: http://localhost:3000"
    echo "按 Ctrl+C 停止服务"
    echo "========================================"
    python3 -m http.server 3000

elif command -v python &> /dev/null; then
    echo "检测到 Python 版本:"
    python --version
    echo
    echo "使用 Python 启动 HTTP 服务器..."
    echo "访问地址: http://localhost:3000"
    echo "按 Ctrl+C 停止服务"
    echo "========================================"
    python -m http.server 3000

else
    echo "错误: 未找到 Node.js 或 Python，无法启动 HTTP 服务器"
    echo "请安装 Node.js 或 Python，或直接用浏览器打开 index.html"
    exit 1
fi
