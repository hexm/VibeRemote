#!/bin/bash
# LightScript Agent 本地测试启动脚本

# 脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AGENT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$SCRIPT_DIR"

echo "========================================"
echo "启动 LightScript Agent (本地测试)"
echo "========================================"
echo "工作目录: $SCRIPT_DIR"
echo "Agent目录: $AGENT_DIR"
echo

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
AGENT_JAR="$AGENT_DIR/agent.jar"
CONFIG_FILE="$SCRIPT_DIR/agent.properties"

# JVM参数配置
JVM_OPTS="${LIGHTSCRIPT_JVM_OPTS:--Xmx512m -Xms128m}"

# 检查Agent JAR文件
if [ ! -f "$AGENT_JAR" ]; then
    echo "ERROR: Agent JAR file not found: $AGENT_JAR"
    echo "请先编译Agent: cd $AGENT_DIR && mvn clean package -DskipTests"
    exit 1
fi

# 检查配置文件
if [ ! -f "$CONFIG_FILE" ]; then
    echo "ERROR: Config file not found: $CONFIG_FILE"
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
echo "Starting LightScript Agent (LocalTest)..."
echo "Java Command: $JAVA_CMD"
echo "JVM Options: $JVM_OPTS"
echo "Config File: $CONFIG_FILE"
echo "Working Directory: $SCRIPT_DIR"
echo "Logs Directory: $SCRIPT_DIR/logs"

nohup "$JAVA_CMD" $JVM_OPTS -Dspring.config.location="file:$CONFIG_FILE" -jar "$AGENT_JAR" > logs/agent-startup.log 2>&1 &
AGENT_PID=$!

# 等待几秒检查启动状态
sleep 3

# 检查进程是否还在运行
if kill -0 $AGENT_PID 2>/dev/null; then
    echo "✅ LocalTest Agent started successfully (PID: $AGENT_PID)"
    echo "   Log files: $SCRIPT_DIR/logs/"
    echo "   Startup log: tail -f logs/agent-startup.log"
    echo "   Main log: tail -f logs/agent.log"
    echo "   To stop: ../stop-agent.sh"
else
    echo "❌ LocalTest Agent failed to start"
    echo "Check startup log: cat logs/agent-startup.log"
    exit 1
fi