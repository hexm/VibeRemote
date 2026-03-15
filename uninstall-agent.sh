#!/bin/bash

echo "卸载 LightScript Agent..."

# 停止Agent
echo "正在停止Agent..."
LAUNCH_AGENT_PLIST="$HOME/Library/LaunchAgents/com.lightscript.agent.plist"
if [ -f "$LAUNCH_AGENT_PLIST" ]; then
    echo "停止LaunchAgent服务..."
    launchctl unload "$LAUNCH_AGENT_PLIST" 2>/dev/null || true
fi

# 停止所有Agent进程
PIDS=$(ps aux | grep "java.*agent.jar" | grep -v grep | awk '{print $2}')
if [ ! -z "$PIDS" ]; then
    echo "停止Agent进程: $PIDS"
    for PID in $PIDS; do
        kill $PID 2>/dev/null || true
    done
    sleep 2
    # 强制停止
    for PID in $PIDS; do
        kill -9 $PID 2>/dev/null || true
    done
fi

# 卸载LaunchAgent服务
if [ -f "$LAUNCH_AGENT_PLIST" ]; then
    echo "删除LaunchAgent配置文件..."
    rm -f "$LAUNCH_AGENT_PLIST"
    echo "LaunchAgent服务已卸载"
fi

# 询问是否删除安装目录
INSTALL_DIR="$HOME/.lightscript-agent"
if [ -d "$INSTALL_DIR" ]; then
    echo ""
    read -p "是否删除安装目录 $INSTALL_DIR? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        rm -rf "$INSTALL_DIR"
        echo "✅ LightScript Agent 已完全卸载"
    else
        echo "✅ LightScript Agent 服务已卸载，文件保留在 $INSTALL_DIR"
    fi
else
    echo "✅ LightScript Agent 服务已卸载"
fi

echo ""
echo "卸载完成！"