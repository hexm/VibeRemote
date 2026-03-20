#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="$SCRIPT_DIR/agent.pid"
SERVICE_NAME="com.viberemote.agent"
PLIST="$HOME/Library/LaunchAgents/${SERVICE_NAME}.plist"

echo "Stopping VibeRemote Agent..."

# 读取配置
SERVER_URL=$(grep -m1 "^server.url=" "$SCRIPT_DIR/agent.properties" 2>/dev/null | cut -d= -f2- | tr -d ' ')
AGENT_TOKEN=$(grep -m1 "^register.token=" "$SCRIPT_DIR/agent.properties" 2>/dev/null | cut -d= -f2- | tr -d ' ')
AGENT_ID=$(grep -m1 "^agentId=" "$HOME/.viberemote/.agent_id" 2>/dev/null | cut -d= -f2- | tr -d ' ')

# 通知服务器离线
if [ -n "$SERVER_URL" ] && [ -n "$AGENT_ID" ] && [ -n "$AGENT_TOKEN" ]; then
    echo "Notifying server of offline status..."
    curl -s -X POST "$SERVER_URL/api/agent/offline" -d "agentId=$AGENT_ID&agentToken=$AGENT_TOKEN" >/dev/null 2>&1 \
        && echo "  Server notified successfully." \
        || echo "  Failed to notify server (server may be unreachable)."
else
    echo "  Skipping server notification (missing config)."
fi

# 先卸载 launchd 服务，防止 kill 后自动重启
if [ -f "$PLIST" ]; then
    echo "Unloading launchd service..."
    launchctl unload "$PLIST" 2>/dev/null || true
fi

# 再 kill 残留进程
PIDS=$(ps aux | grep "java.*agent.jar" | grep -v grep | awk '{print $2}')
if [ -n "$PIDS" ]; then
    for PID in $PIDS; do
        echo "Stopping PID $PID..."
        kill $PID 2>/dev/null || true
        for i in $(seq 1 10); do
            ps -p $PID > /dev/null 2>&1 || break
            sleep 1
        done
        ps -p $PID > /dev/null 2>&1 && kill -9 $PID 2>/dev/null || true
    done
    rm -f "$PID_FILE"
    echo "Agent stopped."
else
    rm -f "$PID_FILE"
    echo "Agent is not running."
fi
