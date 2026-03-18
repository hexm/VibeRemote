#!/bin/bash

echo "=== 启动LightScript服务器 (MySQL数据库) ==="
echo "时间: $(date)"
echo ""

# 获取脚本所在目录的父目录（server目录）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$(dirname "$SCRIPT_DIR")"
cd "$SERVER_DIR"

# 检查MySQL连接
echo "检查MySQL数据库连接..."
if ! nc -z 8.138.114.34 3306; then
    echo "❌ 无法连接到MySQL服务器 8.138.114.34:3306"
    echo "请确保MySQL服务器可访问"
    exit 1
fi
echo "✅ MySQL服务器连接正常"

# 停止现有服务器进程
echo "停止现有服务器进程..."
pkill -f "spring-boot:run" 2>/dev/null || true
pkill -f "ServerApplication" 2>/dev/null || true
sleep 2

echo "启动服务器..."
echo "数据库: MySQL (${DB_HOST:-8.138.114.34}:${DB_PORT:-3306}/${DB_NAME:-lightscript_dev})"
echo "端口: 8080"
echo "批量日志: 启用"
echo "工作目录: $SERVER_DIR"
echo ""
echo "提示: 可通过环境变量覆盖数据库配置，例如:"
echo "  export DB_HOST=xxx DB_NAME=xxx DB_USERNAME=xxx DB_PASSWORD=xxx"
echo ""

# 启动服务器
mvn spring-boot:run \
    -Dspring-boot.run.profiles=dev \
    -Dspring-boot.run.jvmArguments="-Xmx1g -Xms512m" \
    2>&1 | tee logs/server-startup.log &

# 等待服务器启动（使用 /actuator/health 更可靠）
echo "等待服务器启动..."
for i in {1..30}; do
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health 2>/dev/null)
    if [ "$STATUS" = "200" ]; then
        echo ""
        echo "✅ 服务器启动成功!"
        echo "访问地址: http://localhost:8080"
        echo "管理员账号: admin / admin123"
        echo ""
        echo "批量日志功能已启用，可以开始测试"
        exit 0
    fi
    echo -n "."
    sleep 2
done

echo ""
echo "❌ 服务器启动超时，请检查日志: logs/server-startup.log"
exit 1