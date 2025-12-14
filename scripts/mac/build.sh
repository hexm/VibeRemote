#!/bin/bash

echo "========================================"
echo "LightScript 项目构建脚本"
echo "========================================"
echo

# 获取脚本所在目录的父目录（项目根目录）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

# 检查 Maven 环境
if ! command -v mvn &> /dev/null; then
    echo "错误: 未找到 Maven 环境，请安装 Apache Maven"
    echo "可以使用 Homebrew 安装: brew install maven"
    exit 1
fi

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
echo "- ./scripts/mac/start-server.sh (启动服务器)"
echo "- ./scripts/mac/start-agent.sh (启动客户端)"
echo "- ./scripts/mac/start-web.sh (启动前端)"
echo