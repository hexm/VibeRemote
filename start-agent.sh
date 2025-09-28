#!/bin/bash

echo "========================================"
echo "LightScript 客户端启动脚本"
echo "========================================"
echo

# 检查 Java 环境
if ! command -v java &> /dev/null; then
    echo "错误: 未找到 Java 环境，请安装 JDK 1.8 或更高版本"
    exit 1
fi

# 显示 Java 版本
echo "Java 版本信息:"
java -version
echo

# 检查客户端 jar 文件
if [ ! -f agent/target/agent-*-jar-with-dependencies.jar ]; then
    echo "错误: 未找到客户端 jar 文件，请先运行 mvn clean package"
    exit 1
fi

# 设置环境变量
read -p "请输入服务器地址 (默认: http://localhost:8080): " SERVER_URL
SERVER_URL=${SERVER_URL:-http://localhost:8080}

read -p "请输入注册令牌 (默认: dev-register-token): " REGISTER_TOKEN
REGISTER_TOKEN=${REGISTER_TOKEN:-dev-register-token}

export LS_SERVER=$SERVER_URL
export LS_REGISTER_TOKEN=$REGISTER_TOKEN

echo
echo "配置信息:"
echo "服务器地址: $LS_SERVER"
echo "注册令牌: $LS_REGISTER_TOKEN"
echo
echo "正在启动 LightScript 客户端..."
echo "按 Ctrl+C 停止客户端"
echo "========================================"

cd agent/target
java -jar agent-*-jar-with-dependencies.jar
