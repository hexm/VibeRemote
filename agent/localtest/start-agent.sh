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

# 创建logs目录
mkdir -p logs

# 检查是否已有Agent在运行
if [ -f ~/.lightscript/.agent.lock ]; then
    echo "WARNING: Another Agent instance may be running (lock file exists)"
    echo "If you're sure no other Agent is running, delete ~/.lightscript/.agent.lock and try again"
    exit 1
fi

# 启动Agent（后台运行）
echo "Starting LightScript Agent..."
echo "Java Command: $JAVA_CMD"
echo "JVM Options: $JVM_OPTS"
echo "Working Directory: $SCRIPT_DIR"
echo "Logs Directory: $SCRIPT_DIR/logs"

nohup "$JAVA_CMD" $JVM_OPTS -jar "$AGENT_JAR" > logs/agent-startup.log 2>&1 &
AGENT_PID=$!

# 等待几秒检查启动状态
sleep 3

# 检查进程是否还在运行
if kill -0 $AGENT_PID 2>/dev/null; then
    echo "✅ Agent started successfully (PID: $AGENT_PID)"
    echo "   Log files: $SCRIPT_DIR/logs/"
    echo "   Startup log: tail -f logs/agent-startup.log"
    echo "   Main log: tail -f logs/agent.log"
    echo "   To stop: ./stop-agent.sh"
else
    echo "❌ Agent failed to start"
    echo "Check startup log: cat logs/agent-startup.log"
    exit 1
fi