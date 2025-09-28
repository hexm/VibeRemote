#!/bin/bash

echo "========================================"
echo "LightScript 快速启动脚本"
echo "========================================"
echo

echo "正在构建服务器端项目..."
cd server
mvn clean package -DskipTests -q

if [ $? -ne 0 ]; then
    echo "构建失败！请检查错误信息。"
    exit 1
fi

echo
echo "构建成功！正在启动服务器..."
echo
echo "服务器信息:"
echo "- 访问地址: http://localhost:8080"
echo "- 数据库控制台: http://localhost:8080/h2-console"
echo "- 使用内存数据库 (H2)，无需配置MySQL"
echo
echo "按 Ctrl+C 停止服务器"
echo "========================================"

java -jar target/server-*.jar --spring.profiles.active=dev
