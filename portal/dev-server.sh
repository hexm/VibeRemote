#!/bin/bash

# LightScript 门户网站开发服务器

PORT=${1:-8000}
HOST=${2:-localhost}

echo "🚀 启动 LightScript 门户网站开发服务器..."
echo "📍 地址: http://$HOST:$PORT"
echo "📁 目录: $(pwd)"
echo ""
echo "按 Ctrl+C 停止服务器"
echo ""

# 检查端口是否被占用
if lsof -Pi :$PORT -sTCP:LISTEN -t >/dev/null ; then
    echo "⚠️  端口 $PORT 已被占用，尝试使用其他端口..."
    PORT=$((PORT + 1))
    echo "🔄 使用端口: $PORT"
fi

# 启动 Python 服务器
if command -v python3 &> /dev/null; then
    python3 -m http.server $PORT --bind $HOST
elif command -v python &> /dev/null; then
    python -m http.server $PORT --bind $HOST
else
    echo "❌ 未找到 Python，请安装 Python 或使用其他服务器"
    echo ""
    echo "其他选项:"
    echo "  - Node.js: npx serve . -p $PORT"
    echo "  - PHP: php -S $HOST:$PORT"
    echo "  - Ruby: ruby -run -e httpd . -p $PORT"
    exit 1
fi