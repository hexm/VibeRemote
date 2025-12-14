#!/bin/bash

echo "========================================"
echo "LightScript 快速启动脚本"
echo "========================================"
echo

# 获取脚本所在目录的父目录（项目根目录）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

echo "正在构建服务器端项目..."
cd server

# 检查 Maven 是否安装
if ! command -v mvn &> /dev/null; then
    echo "错误: 未找到 Maven 环境，请安装 Apache Maven"
    echo "可以使用 Homebrew 安装: brew install maven"
    exit 1
fi

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

echo
echo "服务器已停止"