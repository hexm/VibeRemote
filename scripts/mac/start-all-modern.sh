#!/bin/bash

echo "========================================"
echo "LightScript 现代化版本一键启动"
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
if curl -s http://localhost:8080/actuator/health > /dev/null; then
    echo "✅ 服务器启动成功 (PID: $SERVER_PID)"
else
    echo "❌ 服务器启动失败，请检查 server.log"
    exit 1
fi

echo
echo "3. 启动现代化前端..."
echo "   前端将在后台启动，访问地址: http://localhost:3001"

# 在后台启动现代化前端
nohup "$SCRIPT_DIR/start-modern-web.sh" > web-modern.log 2>&1 &
WEB_PID=$!

sleep 5
echo "✅ 现代化前端启动成功 (PID: $WEB_PID)"

echo
echo "========================================"
echo "🎉 现代化版本启动完成！"
echo "========================================"
echo
echo "访问地址:"
echo "- 🚀 现代化前端: http://localhost:3001 (推荐)"
echo "- 📱 传统前端: http://localhost:3000 (备选)"
echo "- 🔧 后端 API: http://localhost:8080"
echo "- 🗄️ H2 数据库控制台: http://localhost:8080/h2-console"
echo
echo "默认登录账号:"
echo "- 管理员: admin / admin123"
echo "- 普通用户: user / user123"
echo
echo "日志文件:"
echo "- 服务器日志: server.log"
echo "- 前端日志: web-modern.log"
echo
echo "停止服务:"
echo "- 停止服务器: kill $SERVER_PID"
echo "- 停止前端: kill $WEB_PID"
echo "- 或运行: ./scripts/mac/stop-all.sh"
echo
echo "启动 Agent:"
echo "- 运行: ./scripts/mac/start-agent.sh"
echo
echo "🎨 新版本特色:"
echo "- React 18 + Ant Design 5"
echo "- 现代化渐变设计"
echo "- 流畅动画效果"
echo "- 完全响应式布局"
echo "- 交互式图表"
echo

# 保存 PID 到文件，方便后续停止
echo "$SERVER_PID" > .server.pid
echo "$WEB_PID" > .web-modern.pid

# 如果没有Node.js，显示额外提示
if ! command -v node &> /dev/null; then
    echo "💡 提示: 安装 Node.js 可获得最佳前端体验"
    echo "   安装命令: brew install node"
    echo "   然后重新运行此脚本"
    echo
fi