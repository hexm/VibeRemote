#!/bin/bash

echo "========================================"
echo "LightScript Agent 启动脚本"
echo "========================================"
echo

SERVER_URL=${1:-"http://localhost:8080"}
REGISTER_TOKEN=${2:-"dev-register-token"}

echo "配置信息:"
echo "- 服务器地址: $SERVER_URL"
echo "- 注册令牌: $REGISTER_TOKEN"
echo

echo "正在构建Agent..."
mvn clean package -DskipTests -q

if [ $? -ne 0 ]; then
    echo "构建失败！请检查错误信息。"
    exit 1
fi

echo
echo "构建成功！正在启动Agent..."
echo
echo "日志文件位置:"
echo "- 主日志: logs/agent.log"
echo "- 任务日志: logs/tasks.log"
echo
echo "按 Ctrl+C 停止Agent"
echo "========================================"

mkdir -p logs

java -jar target/agent-*.jar "$SERVER_URL" "$REGISTER_TOKEN"
