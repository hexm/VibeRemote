#!/bin/bash

echo "========================================"
echo "LightScript Agent - 连接阿里云服务器"
echo "========================================"
echo

# 获取脚本所在目录的父目录（项目根目录）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

# 检查 Java 环境
if ! command -v java &> /dev/null; then
    echo "❌ ERROR: Java not found. Please install JDK 1.8 or higher"
    echo "可以使用 Homebrew 安装: brew install openjdk@8"
    exit 1
fi

# 检查 agent jar 文件
if [ ! -f "agent/target/agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar" ]; then
    echo "⚠️  Agent jar file not found"
    echo "📦 Building agent module..."
    
    if ! command -v mvn &> /dev/null; then
        echo "❌ ERROR: Maven not found. Please install Maven"
        echo "可以使用 Homebrew 安装: brew install maven"
        exit 1
    fi
    
    mvn -q -f agent/pom.xml clean package -DskipTests
    if [ $? -ne 0 ]; then
        echo "❌ ERROR: Build failed"
        exit 1
    fi
    echo "✅ Build successful!"
    echo
fi

# 阿里云服务器配置
SERVER_URL="http://8.138.114.34:8080"
REGISTER_TOKEN="dev-register-token"

echo "📋 Configuration:"
echo "   Server URL: $SERVER_URL"
echo "   Register Token: $REGISTER_TOKEN"
echo
echo "🚀 Starting LightScript Agent..."
echo "   Press Ctrl+C to stop the agent"
echo "========================================"
echo

# 启动 agent
cd agent/target

java -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 \
     -jar agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar \
     "$SERVER_URL" "$REGISTER_TOKEN"

echo
echo "Agent stopped."
