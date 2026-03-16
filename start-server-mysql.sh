#!/bin/bash

echo "=== 启动LightScript服务器 (MySQL数据库) ==="
echo "时间: $(date)"
echo ""

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

# 进入服务器目录
cd server

echo "启动服务器..."
echo "数据库: MySQL (8.138.114.34:3306/lightscript)"
echo "端口: 8080"
echo "批量日志: 启用"
echo ""

# 启动服务器
mvn spring-boot:run \
    -Dspring-boot.run.jvmArguments="-Xmx1g -Xms512m" \
    2>&1 | tee ../server-startup.log &

# 等待服务器启动
echo "等待服务器启动..."
for i in {1..30}; do
    if curl -s http://localhost:8080/ >/dev/null 2>&1; then
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
echo "❌ 服务器启动超时，请检查日志: server-startup.log"
exit 1