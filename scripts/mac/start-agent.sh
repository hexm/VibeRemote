#!/bin/bash

echo "========================================"
echo "LightScript Agent Startup Script"
echo "========================================"
echo

# 获取脚本所在目录的父目录（项目根目录）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

# 检查 Java 环境
if ! command -v java &> /dev/null; then
    echo "ERROR: Java not found. Please install JDK 1.8 or higher"
    echo "可以使用 Homebrew 安装: brew install openjdk@8"
    exit 1
fi

# 检查 agent jar 文件
if [ ! -f "agent/target/agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar" ]; then
    echo "ERROR: Agent jar file not found"
    echo "Building agent module..."
    
    if ! command -v mvn &> /dev/null; then
        echo "ERROR: Maven not found. Please install Maven"
        echo "可以使用 Homebrew 安装: brew install maven"
        exit 1
    fi
    
    mvn -q -f agent/pom.xml clean package -DskipTests
    if [ $? -ne 0 ]; then
        echo "ERROR: Build failed"
        exit 1
    fi
    echo "Build successful!"
    echo
fi

# 设置默认值
SERVER_URL="http://localhost:8080"

# 从环境变量读取注册令牌或使用默认值
if [ -z "$LIGHTSCRIPT_REGISTER_TOKEN" ]; then
    REGISTER_TOKEN="dev-register-token"
else
    REGISTER_TOKEN="$LIGHTSCRIPT_REGISTER_TOKEN"
    echo "Using token from environment"
fi

# 获取用户输入的服务器 URL
echo -n "Enter server URL (default: $SERVER_URL): "
read INPUT_SERVER
if [ -n "$INPUT_SERVER" ]; then
    SERVER_URL="$INPUT_SERVER"
fi

# 获取用户输入的注册令牌
echo -n "Enter register token (default: $REGISTER_TOKEN): "
read INPUT_TOKEN
if [ -n "$INPUT_TOKEN" ]; then
    REGISTER_TOKEN="$INPUT_TOKEN"
fi

echo
echo "Configuration:"
echo "Server URL: $SERVER_URL"
echo "Register Token: $REGISTER_TOKEN"
echo
echo "Starting LightScript Agent..."
echo "Press Ctrl+C to stop the agent"
echo "========================================"

# 启动 agent
cd agent/target

echo "Starting agent..."
java -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 \
     -jar agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar \
     "$SERVER_URL" "$REGISTER_TOKEN"

echo
echo "Agent stopped."