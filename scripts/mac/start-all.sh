#!/bin/bash

echo "========================================"
echo "LightScript 一键启动所有服务"
echo "========================================"
echo

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 检查依赖
echo "检查系统依赖..."

if ! command -v java &> /dev/null; then
    echo "❌ Java 未安装，请先安装 JDK 1.8+"
    echo "   安装命令: brew install openjdk@8"
    exit 1
fi

if ! command -v mvn &> /dev/null; then
    echo "❌ Maven 未安装，请先安装 Maven"
    echo "   安装命令: brew install maven"
    exit 1
fi

echo "✅ 系统依赖检查通过"
echo

# 构建项目
echo "1. 构建项目..."
"$SCRIPT_DIR/build.sh"
if [ $? -ne 0 ]; then
    echo "❌ 项目构建失败"
    exit 1
fi

echo
echo "2. 启动服务器..."
echo "   服务器将在后台启动，访问地址: http://localhost:8080"

# 在后台启动服务器
nohup "$SCRIPT_DIR/start-server.sh" > server.log 2>&1 &
SERVER_PID=$!

# 等待服务器启动
echo "   等待服务器启动..."
sleep 10

# 检查服务器是否启动成功
if curl -s http://localhost:8080 > /dev/null; then
    echo "✅ 服务器启动成功 (PID: $SERVER_PID)"
else
    echo "❌ 服务器启动失败，请检查 server.log"
    exit 1
fi

echo
echo "3. 启动前端服务..."
echo "   前端将在后台启动，访问地址: http://localhost:3000"

# 在后台启动前端
nohup "$SCRIPT_DIR/start-web.sh" > web.log 2>&1 &
WEB_PID=$!

sleep 3
echo "✅ 前端服务启动成功 (PID: $WEB_PID)"

echo
echo "========================================"
echo "🎉 所有服务启动完成！"
echo "========================================"
echo
echo "访问地址:"
echo "- 前端界面: http://localhost:3000"
echo "- 后端 API: http://localhost:8080"
echo "- H2 数据库控制台: http://localhost:8080/h2-console"
echo
echo "默认登录账号:"
echo "- 管理员: admin / admin123"
echo "- 普通用户: user / user123"
echo
echo "日志文件:"
echo "- 服务器日志: server.log"
echo "- 前端日志: web.log"
echo
echo "停止服务:"
echo "- 停止服务器: kill $SERVER_PID"
echo "- 停止前端: kill $WEB_PID"
echo "- 或运行: ./scripts/mac/stop-all.sh"
echo
echo "启动 Agent:"
echo "- 运行: ./scripts/mac/start-agent.sh"
echo

# 保存 PID 到文件，方便后续停止
echo "$SERVER_PID" > .server.pid
echo "$WEB_PID" > .web.pid