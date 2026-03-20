#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JRE_HOME="$SCRIPT_DIR/jre"
JAVA_EXE="$JRE_HOME/bin/java"
PID_FILE="$SCRIPT_DIR/agent.pid"
LOG_FILE="$SCRIPT_DIR/logs/agent.log"

mkdir -p "$SCRIPT_DIR/logs"

echo "VibeRemote Agent starting..."
echo "Dir: $SCRIPT_DIR"

if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p $PID > /dev/null 2>&1; then
        echo "Agent already running (PID: $PID). Run ./stop-agent.sh first."
        exit 1
    else
        rm -f "$PID_FILE"
    fi
fi

if [ -f "$JAVA_EXE" ] && [ -x "$JAVA_EXE" ] && "$JAVA_EXE" -version >/dev/null 2>&1; then
    JAVA_CMD="$JAVA_EXE"
elif command -v java >/dev/null 2>&1; then
    JAVA_CMD="java"
elif [ -n "$JAVA_HOME" ] && [ -f "$JAVA_HOME/bin/java" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
else
    echo "ERROR: Java not found. Install Java 8+."
    exit 1
fi

echo "Java: $JAVA_CMD"
echo "Log: $LOG_FILE"

if [ -n "$LAUNCHED_BY_LAUNCHD" ] || [ "$1" = "--launchd" ]; then
    exec "$JAVA_CMD" -Xms32m -Xmx128m -XX:MaxMetaspaceSize=64m \
        -Dfile.encoding=UTF-8 \
        -jar "$SCRIPT_DIR/agent.jar"
else
    nohup "$JAVA_CMD" -Xms32m -Xmx128m -XX:MaxMetaspaceSize=64m \
        -Dfile.encoding=UTF-8 \
        -jar "$SCRIPT_DIR/agent.jar" > /dev/null 2>&1 &
    AGENT_PID=$!
    echo $AGENT_PID > "$PID_FILE"
    sleep 2
    if ps -p $AGENT_PID > /dev/null 2>&1; then
        echo "Agent started (PID: $AGENT_PID)"
    else
        echo "Agent failed to start. Check: $LOG_FILE"
        rm -f "$PID_FILE"
        exit 1
    fi
fi
