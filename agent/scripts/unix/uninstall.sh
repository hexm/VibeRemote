#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Uninstalling VibeRemote Agent..."

# Step 1: Remove autostart
echo "[1/3] Removing autostart..."
if [[ "$(uname)" == "Darwin" ]]; then
    PLIST="$HOME/Library/LaunchAgents/com.viberemote.agent.plist"
    if [ -f "$PLIST" ]; then
        launchctl unload "$PLIST" 2>/dev/null || true
        rm -f "$PLIST"
        echo "LaunchAgent removed."
    else
        echo "LaunchAgent not set."
    fi
elif [[ "$(uname)" == "Linux" ]]; then
    if systemctl is-enabled viberemote-agent >/dev/null 2>&1; then
        systemctl disable viberemote-agent 2>/dev/null || true
        echo "systemd service disabled."
    else
        echo "systemd service not set."
    fi
fi

# Step 2: Stop process
echo "[2/3] Stopping agent..."
PIDS=$(ps aux | grep "java.*agent.jar" | grep -v grep | awk '{print $2}')
if [ -n "$PIDS" ]; then
    for PID in $PIDS; do
        kill $PID 2>/dev/null || true
    done
    sleep 2
    for PID in $PIDS; do
        kill -9 $PID 2>/dev/null || true
    done
    echo "Agent stopped."
else
    echo "Agent was not running."
fi

# Step 3: Delete files
echo "[3/3] Deleting files..."
rm -f "$HOME/.viberemote/.agent_id"
cd /tmp
rm -rf "$SCRIPT_DIR"

echo ""
echo "VibeRemote Agent uninstalled."
