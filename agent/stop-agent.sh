#!/bin/bash
# LightScript Agent 停止脚本

echo "Stopping LightScript Agent..."

# 方法1：通过锁文件查找进程
STOPPED=false

if [ -f ~/.lightscript/.agent.lock ]; then
    echo "Found lock file, reading process info..."
    
    # 从锁文件读取PID信息
    LOCK_INFO=$(cat ~/.lightscript/.agent.lock)
    PID=$(echo "$LOCK_INFO" | grep "PID:" | cut -d' ' -f2 | cut -d',' -f1)
    
    if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
        echo "Stopping Agent process (PID: $PID)..."
        kill "$PID"
        
        # 等待进程结束
        for i in {1..10}; do
            if ! kill -0 "$PID" 2>/dev/null; then
                echo "✅ Agent stopped successfully"
                STOPPED=true
                break
            fi
            sleep 1
        done
        
        # 如果进程还没结束，强制终止
        if [ "$STOPPED" = false ] && kill -0 "$PID" 2>/dev/null; then
            echo "Force stopping Agent process..."
            kill -9 "$PID"
            sleep 2
            if ! kill -0 "$PID" 2>/dev/null; then
                echo "✅ Agent force stopped"
                STOPPED=true
            fi
        fi
    fi
fi

# 方法2：通过进程名查找并停止
if [ "$STOPPED" = false ]; then
    echo "Searching for Agent processes by name..."
    
    # 查找所有Agent进程
    AGENT_PIDS=$(ps aux | grep "java.*agent.jar" | grep -v grep | awk '{print $2}')
    
    if [ -n "$AGENT_PIDS" ]; then
        echo "Found Agent processes: $AGENT_PIDS"
        
        for PID in $AGENT_PIDS; do
            echo "Stopping Agent process (PID: $PID)..."
            kill "$PID"
        done
        
        # 等待进程结束
        sleep 3
        
        # 检查是否还有进程在运行
        REMAINING_PIDS=$(ps aux | grep "java.*agent.jar" | grep -v grep | awk '{print $2}')
        
        if [ -n "$REMAINING_PIDS" ]; then
            echo "Force stopping remaining processes: $REMAINING_PIDS"
            for PID in $REMAINING_PIDS; do
                kill -9 "$PID"
            done
            sleep 2
        fi
        
        echo "✅ All Agent processes stopped"
        STOPPED=true
    fi
fi

# 清理锁文件
if [ -f ~/.lightscript/.agent.lock ]; then
    echo "Cleaning up lock file..."
    rm -f ~/.lightscript/.agent.lock
fi

if [ "$STOPPED" = false ]; then
    echo "ℹ️  No Agent processes found"
else
    echo "🔄 Agent stopped. You can start it again with: ./start-agent.sh"
fi