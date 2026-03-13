#!/bin/bash

# LightScript Agent 启动脚本 (Unix/Linux/macOS)

# 脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 配置Java环境
if [ -n "$JAVA_HOME" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
    JAVA_CMD="java"
else
    echo "ERROR: Java not found. Please install Java or set JAVA_HOME environment variable."
    exit 1
fi

# Agent配置
AGENT_JAR="agent.jar"

# JVM参数配置
JVM_OPTS="${LIGHTSCRIPT_JVM_OPTS:--Xmx512m -Xms128m}"

# 检查Agent JAR文件
if [ ! -f "$AGENT_JAR" ]; then
    echo "ERROR: Agent JAR file not found: $AGENT_JAR"
    exit 1
fi

# 启动Agent
echo "Starting LightScript Agent..."
echo "Java Command: $JAVA_CMD"
echo "JVM Options: $JVM_OPTS"
echo "Working Directory: $SCRIPT_DIR"

exec "$JAVA_CMD" $JVM_OPTS -jar "$AGENT_JAR"