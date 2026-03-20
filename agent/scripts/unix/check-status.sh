#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="$SCRIPT_DIR/logs/agent.log"

echo "========================================"
echo "  VibeRemote Agent Status"
echo "========================================"
echo ""

PIDS=$(ps aux | grep "java.*agent.jar" | grep -v grep | awk '{print $2}')
if [ -n "$PIDS" ]; then
    echo "[Status] Running (PID: $PIDS)"
else
    echo "[Status] Not running"
fi

echo ""

if [[ "$(uname)" == "Darwin" ]]; then
    PLIST="$HOME/Library/LaunchAgents/com.viberemote.agent.plist"
    [ -f "$PLIST" ] && echo "[Autostart] Enabled (LaunchAgent)" || echo "[Autostart] Disabled"
elif [[ "$(uname)" == "Linux" ]]; then
    if systemctl is-enabled viberemote-agent >/dev/null 2>&1; then
        echo "[Autostart] Enabled (systemd)"
    elif [ -f "$HOME/.config/autostart/viberemote-agent.desktop" ]; then
        echo "[Autostart] Enabled (autostart)"
    else
        echo "[Autostart] Disabled"
    fi
fi

echo ""

if [ -f "$LOG_FILE" ]; then
    echo "[Last 10 log lines]"
    echo "----------------------------------------"
    tail -10 "$LOG_FILE"
else
    echo "[Log] No log file found"
fi

echo ""
