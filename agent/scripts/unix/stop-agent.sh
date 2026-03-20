#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="$SCRIPT_DIR/agent.pid"

echo "Stopping VibeRemote Agent..."

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
    echo "Agent is not running."
fi
