#!/bin/bash

# LightScript 本地环境完整重启脚本
# 功能：停止所有服务 → 重新编译打包 → 启动所有服务

set -e  # 遇到错误立即退出

echo "========================================"
echo "LightScript 本地环境完整重启"
echo "========================================"
echo "时间: $(date)"
echo

# 获取脚本所在目录（项目根目录）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ========================================
# 第一步：停止所有服务
# ========================================
echo "🛑 第一步：停止所有服务"
echo "----------------------------------------"

# 停止Agent进程
echo "停止Agent进程..."
pkill -f "agent.jar" 2>/dev/null || true
pkill -f "AgentMain" 2>/dev/null || true

# 停止服务器进程
echo "停止服务器进程..."
pkill -f "spring-boot:run" 2>/dev/null || true
pkill -f "ServerApplication" 2>/dev/null || true

# 停止前端进程
echo "停止前端进程..."
pkill -f "vite" 2>/dev/null || true
pkill -f "start-web.sh" 2>/dev/null || true

# 停止门户进程
echo "停止门户进程..."
pkill -f "http.server.*8002" 2>/dev/null || true
pkill -f "start-portal.sh" 2>/dev/null || true

# 清理Agent锁文件
echo "清理Agent锁文件..."
rm -f ~/.lightscript/.agent.lock

echo "✅ 所有服务已停止"
echo

# 等待进程完全停止
echo "等待进程完全停止..."
sleep 3

# ========================================
# 第二步：重新编译打包
# ========================================
echo "🔨 第二步：重新编译打包"
echo "----------------------------------------"

# 编译服务器端
echo "编译服务器端..."
cd server
mvn clean compile -q
if [ $? -ne 0 ]; then
    echo "❌ 服务器端编译失败"
    exit 1
fi
echo "✅ 服务器端编译完成"
cd ..

# 编译Agent
echo "编译Agent..."
cd agent
mvn clean package -DskipTests -q
if [ $? -ne 0 ]; then
    echo "❌ Agent编译失败"
    exit 1
fi
# 复制JAR文件到根目录并构建安装包
cp target/agent-*-jar-with-dependencies.jar agent.jar
echo "✅ Agent编译完成"
cd ..

# 安装前端依赖（如果需要）
echo "检查前端依赖..."
cd web
if [ ! -d "node_modules" ] || [ "package.json" -nt "node_modules" ]; then
    echo "安装前端依赖..."
    npm install
    if [ $? -ne 0 ]; then
        echo "❌ 前端依赖安装失败"
        exit 1
    fi
fi
echo "✅ 前端依赖检查完成"
cd ..

echo "✅ 所有组件编译打包完成"
echo

# ========================================
# 第三步：启动所有服务
# ========================================
echo "🚀 第三步：启动所有服务"
echo "----------------------------------------"

# 启动服务器（后台）
echo "启动服务器（MySQL数据库）..."
cd server
nohup ./scripts/start-with-mysql-enhanced.sh > ../server-restart.log 2>&1 &
SERVER_PID=$!
cd ..

# 等待服务器启动
echo "等待服务器启动..."
for i in {1..30}; do
    if curl -s http://localhost:8080/ >/dev/null 2>&1; then
        echo "✅ 服务器启动成功"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "❌ 服务器启动超时"
        echo "查看日志: tail -f server-restart.log"
        exit 1
    fi
    echo -n "."
    sleep 2
done

# 启动前端（后台）
echo "启动前端管理界面..."
cd web
nohup ./scripts/start-web.sh > ../web-restart.log 2>&1 &
WEB_PID=$!
cd ..

# 等待前端启动
echo "等待前端启动..."
for i in {1..15}; do
    if curl -s http://localhost:3001/ >/dev/null 2>&1; then
        echo "✅ 前端启动成功"
        break
    fi
    if [ $i -eq 15 ]; then
        echo "❌ 前端启动超时"
        echo "查看日志: tail -f web-restart.log"
        exit 1
    fi
    echo -n "."
    sleep 2
done

# 启动门户网站（后台）
echo "启动门户网站..."
cd portal
nohup ./scripts/start-portal.sh > ../portal-restart.log 2>&1 &
PORTAL_PID=$!
cd ..

# 等待门户启动
echo "等待门户启动..."
for i in {1..10}; do
    if curl -s http://localhost:8002/ >/dev/null 2>&1; then
        echo "✅ 门户网站启动成功"
        break
    fi
    if [ $i -eq 10 ]; then
        echo "❌ 门户网站启动超时"
        echo "查看日志: tail -f portal-restart.log"
        exit 1
    fi
    echo -n "."
    sleep 2
done

# 启动本地测试Agent
echo "启动本地测试Agent..."
cd agent/localtest
nohup ./start-localtest-agent.sh > ../../agent-restart.log 2>&1 &
AGENT_PID=$!
cd ../..

# 等待Agent启动
echo "等待Agent启动..."
sleep 3

# 检查Agent是否启动成功
if pgrep -f "agent.jar" >/dev/null 2>&1; then
    echo "✅ 本地测试Agent启动成功"
else
    echo "❌ 本地测试Agent启动失败"
    echo "查看日志: tail -f agent-restart.log"
    exit 1
fi

echo "✅ 所有服务启动完成"
echo

# ========================================
# 第四步：验证服务状态
# ========================================
echo "🔍 第四步：验证服务状态"
echo "----------------------------------------"

# 验证各服务状态
echo "验证服务状态..."

# 检查服务器
if curl -s http://localhost:8080/ >/dev/null 2>&1; then
    echo "✅ 服务器: http://localhost:8080 (正常)"
else
    echo "❌ 服务器: http://localhost:8080 (异常)"
fi

# 检查前端
if curl -s http://localhost:3001/ >/dev/null 2>&1; then
    echo "✅ 前端: http://localhost:3001 (正常)"
else
    echo "❌ 前端: http://localhost:3001 (异常)"
fi

# 检查门户
if curl -s http://localhost:8002/ >/dev/null 2>&1; then
    echo "✅ 门户: http://localhost:8002 (正常)"
else
    echo "❌ 门户: http://localhost:8002 (异常)"
fi

# 检查Agent进程
if pgrep -f "agent.jar" >/dev/null 2>&1; then
    AGENT_PID=$(pgrep -f "agent.jar")
    echo "✅ Agent: PID $AGENT_PID (正常)"
else
    echo "❌ Agent: 未运行"
fi

echo

# ========================================
# 完成
# ========================================
echo "🎉 本地环境重启完成！"
echo "========================================"
echo "📊 服务访问地址:"
echo "  • 管理后台: http://localhost:3001"
echo "  • 门户网站: http://localhost:8002"  
echo "  • 后端API: http://localhost:8080"
echo ""
echo "📋 进程信息:"
echo "  • 服务器PID: $SERVER_PID"
echo "  • 前端PID: $WEB_PID"
echo "  • 门户PID: $PORTAL_PID"
echo "  • Agent: 本地测试模式"
echo ""
echo "📝 日志文件:"
echo "  • 服务器: server-restart.log"
echo "  • 前端: web-restart.log"
echo "  • 门户: portal-restart.log"
echo "  • Agent: agent-restart.log"
echo ""
echo "🔧 管理命令:"
echo "  • 查看所有日志: tail -f *-restart.log"
echo "  • 停止所有服务: pkill -f 'spring-boot\\|vite\\|http.server\\|agent.jar'"
echo "  • 重新启动: ./restart-local-env.sh"
echo ""
echo "✅ 环境就绪，可以开始开发和测试！"