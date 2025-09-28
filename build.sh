#!/bin/bash

echo "========================================"
echo "LightScript 项目构建脚本"
echo "========================================"
echo

# 检查 Maven 环境
if ! command -v mvn &> /dev/null; then
    echo "错误: 未找到 Maven 环境，请安装 Apache Maven"
    exit 1
fi

echo "Maven 版本信息:"
mvn -version
echo

echo "正在清理项目..."
mvn clean

echo
echo "正在编译和打包项目..."
mvn package -DskipTests

if [ $? -ne 0 ]; then
    echo
    echo "构建失败！请检查错误信息。"
    exit 1
fi

echo
echo "========================================"
echo "构建完成！"
echo "========================================"
echo
echo "生成的文件:"
echo "- 服务器: server/target/server-*.jar"
echo "- 客户端: agent/target/agent-*-jar-with-dependencies.jar"
echo
echo "接下来可以运行:"
echo "- ./start-server.sh (启动服务器)"
echo "- ./start-agent.sh (启动客户端)"
echo

# 给脚本添加执行权限
chmod +x start-server.sh
chmod +x start-agent.sh

echo "已为启动脚本添加执行权限"
