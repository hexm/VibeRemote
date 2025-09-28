#!/bin/bash

echo "========================================"
echo "LightScript 服务器启动脚本"
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

# 检查服务器 jar 文件
if [ ! -f server/target/server-*.jar ]; then
    echo "错误: 未找到服务器 jar 文件，请先运行 mvn clean package"
    exit 1
fi

echo "正在启动 LightScript 服务器..."
echo "访问地址: http://localhost:8080"
echo "默认账号: admin / admin123"
echo
echo "按 Ctrl+C 停止服务器"
echo "========================================"

cd server/target
java -jar server-*.jar
