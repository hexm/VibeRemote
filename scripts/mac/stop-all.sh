#!/bin/bash

echo "========================================"
echo "LightScript 停止所有服务"
echo "========================================"
echo

# 获取脚本所在目录的父目录（项目根目录）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

# 停止服务器
if [ -f ".server.pid" ]; then
    SERVER_PID=$(cat .server.pid)
    if ps -p $SERVER_PID > /dev/null 2>&1; then
        echo "停止服务器 (PID: $SERVER_PID)..."
        kill $SERVER_PID
        echo "✅ 服务器已停止"
    else
        echo "ℹ 服务器进程不存在"
    fi
    rm -f .server.pid
else
    echo "ℹ 未找到服务器 PID 文件"
fi

# 停止前端
if [ -f ".web.pid" ]; then
    WEB_PID=$(cat .web.pid)
    if ps -p $WEB_PID > /dev/null 2>&1; then
        echo "停止前端服务 (PID: $WEB_PID)..."
        kill $WEB_PID
        echo "✅ 前端服务已停止"
    else
        echo "ℹ 前端进程不存在"
    fi
    rm -f .web.pid
else
    echo "ℹ 未找到前端 PID 文件"
fi

# 清理日志文件（可选）
read -p "是否删除日志文件? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    rm -f server.log web.log
    echo "✅ 日志文件已删除"
fi

echo
echo "========================================"
echo "🛑 所有服务已停止"
echo "========================================"