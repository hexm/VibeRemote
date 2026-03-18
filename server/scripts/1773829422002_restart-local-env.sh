#!/bin/bash

# LightScript 本地环境完整重启脚本
# 功能：停止所有服务 → 重新编译打包 → 启动所有服务

echo "========================================"
echo "LightScript 本地环境完整重启"
echo "========================================"
echo "时间: $(date)"
echo

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 等待 URL 可访问的辅助函数，避免 set -e 干扰
wait_for_url() {
    local url=$1
    local max=$2
    local label=$3
    for i in $(seq 1 "$max"); do
        STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || echo "000")
        if [ "$STATUS" = "200" ]; then
            echo "✅ $label 启动成功"
            return 0
        fi
        echo -n "."
        sleep 2
    done
    echo ""
    echo "❌ $label 启动超时"
    return 1
}

# ========================================
# 第一步：停止所有服务
# ========================================
echo "🛑 第一步：停止所有服务"
echo "----------------------------------------"

echo "停止Agent进程..."
if [ -f "agent/stop-agent.sh" ]; then
    bash agent/stop-agent.sh 2>/dev/null || true
else
    pkill -f "agent.jar" 2>/dev/null || true
fi

echo "停止服务器进程..."
pkill -f "spring-boot:run" 2>/dev/null || true
pkill -f "ServerApplication" 2>/dev/null || true

echo "停止前端进程..."
pkill -f "vite" 2>/dev/null || true

echo "停止门户进程..."
pkill -f "http.server.*8002" 2>/dev/null || true

rm -f ~/.lightscript/.agent.lock

echo "✅ 所有服务已停止"
echo
sleep 3

# ========================================
# 第二步：重新编译打包
# ========================================
echo "🔨 第二步：重新编译打包"
echo "----------------------------------------"

echo "编译服务器端..."
cd server && mvn clean compile -q || { echo "❌ 服务器端编译失败"; exit 1; }
cd "$SCRIPT_DIR"
echo "✅ 服务器端编译完成"

echo "编译Agent..."
cd agent && mvn clean package -DskipTests -q || { echo "❌ Agent编译失败"; exit 1; }
cd "$SCRIPT_DIR"
echo "✅ Agent编译完成"

echo "检查前端依赖..."
cd web
if [ ! -d "node_modules" ] || [ "package.json" -nt "node_modules" ]; then
    npm install || { echo "❌ 前端依赖安装失败"; exit 1; }
fi
cd "$SCRIPT_DIR"
echo "✅ 前端依赖检查完成"

echo "✅ 所有组件编译完成"
echo

# ========================================
# 第三步：准备 Agent localtest 目录
# ========================================
echo "📁 第三步：准备 Agent localtest 目录"
echo "----------------------------------------"

LOCALTEST_DIR="$SCRIPT_DIR/agent/localtest"
AGENT_JAR=$(ls "$SCRIPT_DIR/agent/target/agent-"*"-jar-with-dependencies.jar" 2>/dev/null | head -1)

if [ -z "$AGENT_JAR" ]; then
    echo "❌ 未找到编译好的 agent jar"
    exit 1
fi

echo "清空 localtest 目录（保留 agent.properties）..."
cp "$LOCALTEST_DIR/agent.properties" /tmp/agent.properties.bak 2>/dev/null || true
rm -rf "$LOCALTEST_DIR"/*
cp /tmp/agent.properties.bak "$LOCALTEST_DIR/agent.properties" 2>/dev/null || true

echo "复制 agent.jar → localtest/agent.jar"
cp "$AGENT_JAR" "$LOCALTEST_DIR/agent.jar"

echo "复制启动脚本..."
cp "$SCRIPT_DIR/agent/start-agent.sh" "$LOCALTEST_DIR/"
cp "$SCRIPT_DIR/agent/stop-agent.sh" "$LOCALTEST_DIR/"
chmod +x "$LOCALTEST_DIR/start-agent.sh" "$LOCALTEST_DIR/stop-agent.sh"

mkdir -p "$LOCALTEST_DIR/logs"

echo "✅ localtest 目录准备完成"
echo "   JAR: $(basename "$AGENT_JAR")"
echo "   配置: agent.properties (server.url=$(grep 'server.url' "$LOCALTEST_DIR/agent.properties" | cut -d= -f2))"
echo

# ========================================
# 第四步：启动所有服务
# ========================================
echo "🚀 第四步：启动所有服务"
echo "----------------------------------------"

# 启动服务器（直接 nohup mvn，不嵌套调用 start-with-mysql-enhanced.sh）
echo "启动服务器（MySQL dev数据库）..."
cd server
nohup mvn spring-boot:run \
    -Dspring-boot.run.profiles=dev \
    -Dspring-boot.run.jvmArguments="-Xmx1g -Xms512m" \
    > "$SCRIPT_DIR/server-restart.log" 2>&1 &
cd "$SCRIPT_DIR"

echo "等待服务器启动..."
wait_for_url "http://localhost:8080/actuator/health" 30 "服务器" || exit 1

# 启动前端
echo "启动前端管理界面..."
cd web
nohup bash scripts/start-web.sh > "$SCRIPT_DIR/web-restart.log" 2>&1 &
cd "$SCRIPT_DIR"

echo "等待前端启动..."
wait_for_url "http://localhost:3001/" 15 "前端" || exit 1

# 启动门户
echo "启动门户网站..."
cd portal
nohup bash scripts/start-portal.sh > "$SCRIPT_DIR/portal-restart.log" 2>&1 &
cd "$SCRIPT_DIR"

echo "等待门户启动..."
wait_for_url "http://localhost:8002/" 10 "门户网站" || exit 1

# 启动 Agent（直接在 localtest 目录下执行，start-agent.sh 内部已是后台启动）
echo "启动本地测试Agent..."
cd "$LOCALTEST_DIR"
bash start-agent.sh > "$SCRIPT_DIR/agent-restart.log" 2>&1
cd "$SCRIPT_DIR"

sleep 2
if pgrep -f "agent.jar" >/dev/null 2>&1; then
    echo "✅ 本地测试Agent启动成功"
else
    echo "❌ Agent启动失败，查看日志: tail -f agent-restart.log"
    exit 1
fi

echo

# ========================================
# 第五步：验证服务状态
# ========================================
echo "🔍 第五步：验证服务状态"
echo "----------------------------------------"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health 2>/dev/null || echo "000")
[ "$STATUS" = "200" ] && echo "✅ 服务器:  http://localhost:8080 (正常)" || echo "❌ 服务器:  http://localhost:8080 (异常)"

curl -s http://localhost:3001/ >/dev/null 2>&1 && echo "✅ 前端:    http://localhost:3001 (正常)" || echo "❌ 前端:    http://localhost:3001 (异常)"
curl -s http://localhost:8002/ >/dev/null 2>&1 && echo "✅ 门户:    http://localhost:8002 (正常)" || echo "❌ 门户:    http://localhost:8002 (异常)"

AGENT_PID=$(pgrep -f "agent.jar" | head -1)
[ -n "$AGENT_PID" ] && echo "✅ Agent:   PID $AGENT_PID (正常)" || echo "❌ Agent:   未运行"

echo
echo "🎉 本地环境重启完成！"
echo "========================================"
echo "  管理后台: http://localhost:3001"
echo "  门户网站: http://localhost:8002"
echo "  后端API:  http://localhost:8080"
echo ""
echo "  日志: tail -f server-restart.log"
echo "========================================"
