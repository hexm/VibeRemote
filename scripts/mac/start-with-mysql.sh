#!/bin/bash

# 使用MySQL启动服务器

echo "=========================================="
echo "启动服务器（使用MySQL）"
echo "=========================================="
echo ""

# 检查配置
echo "当前数据库配置："
grep "url:" server/src/main/resources/application.yml | head -1

echo ""
echo "启动Spring Boot应用..."
echo ""

mvn spring-boot:run -pl server

