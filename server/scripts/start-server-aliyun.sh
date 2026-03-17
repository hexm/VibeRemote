#!/bin/bash

# LightScript 服务器阿里云启动脚本
# 支持后台启动、状态检查、日志管理

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="/opt/lightscript/backend"
JAR_FILE="server-0.4.0.jar"
PID_FILE="$SERVER_DIR/backend.pid"
LOG_FILE="$SERVER_DIR/backend.log"
PROFILE="prod"

echo "🚀 LightScript 服务器启动脚本 (阿里云)"
echo "📁 服务器目录: $SERVER_DIR"
echo "📦 JAR文件: $JAR_FILE"
echo "📋 配置: $PROFILE"
echo ""

# 检查是否已经在运行
check_running() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p $PID > /dev/null 2>&1; then
            echo "✅ 服务器已在运行 (PID: $PID)"
            echo "   端口: http://8.138.114.34:8080"
            echo "   日志: tail -f $LOG_FILE"
            return 0
        else
            echo "🧹 清理过期的PID文件..."
            rm -f "$PID_FILE"
        fi
    fi
    return 1
}

# 停止服务器
stop_server() {
    echo "🛑 停止服务器..."
    
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p $PID > /dev/null 2>&1; then
            echo "   停止进程 $PID..."
            kill $PID
            
            # 等待优雅退出
            for i in {1..15}; do
                if ! ps -p $PID > /dev/null 2>&1; then
                    echo "   ✅ 进程已停止"
                    break
                fi
                sleep 1
            done
            
            # 强制停止
            if ps -p $PID > /dev/null 2>&1; then
                echo "   🔨 强制停止进程..."
                kill -9 $PID
            fi
        fi
        rm -f "$PID_FILE"
    fi
    
    # 通过进程名查找剩余进程
    PIDS=$(ps aux | grep "java.*$JAR_FILE" | grep -v grep | awk '{print $2}')
    if [ ! -z "$PIDS" ]; then
        echo "   🧹 清理剩余进程: $PIDS"
        for PID in $PIDS; do
            kill -9 $PID 2>/dev/null || true
        done
    fi
    
    echo "   ✅ 服务器已停止"
}

# 启动服务器
start_server() {
    echo "🚀 启动服务器..."
    
    # 切换到服务器目录
    cd "$SERVER_DIR" || {
        echo "❌ 无法进入服务器目录: $SERVER_DIR"
        exit 1
    }
    
    # 检查JAR文件
    if [ ! -f "$JAR_FILE" ]; then
        echo "❌ JAR文件不存在: $JAR_FILE"
        exit 1
    fi
    
    # 检查Java
    if ! command -v java >/dev/null 2>&1; then
        echo "❌ 未找到Java运行时"
        exit 1
    fi
    
    echo "   Java版本: $(java -version 2>&1 | head -1)"
    echo "   JAR文件: $(ls -lh $JAR_FILE | awk '{print $5}')"
    
    # 后台启动服务器
    echo "   🔄 后台启动中..."
    nohup java -jar "$JAR_FILE" \
        --spring.profiles.active="$PROFILE" \
        > "$LOG_FILE" 2>&1 &
    
    # 保存PID
    SERVER_PID=$!
    echo $SERVER_PID > "$PID_FILE"
    
    echo "   📝 PID: $SERVER_PID"
    echo "   📄 日志: $LOG_FILE"
    
    # 等待启动
    echo "   ⏳ 等待启动..."
    sleep 3
    
    # 检查启动状态
    if ps -p $SERVER_PID > /dev/null 2>&1; then
        echo "   ✅ 服务器启动成功!"
        echo ""
        echo "🌐 服务地址:"
        echo "   后端API: http://8.138.114.34:8080"
        echo "   管理界面: http://8.138.114.34:3000"
        echo "   门户网站: http://8.138.114.34"
        echo ""
        echo "📋 管理命令:"
        echo "   查看日志: tail -f $LOG_FILE"
        echo "   查看状态: ps -p $SERVER_PID"
        echo "   停止服务: $0 stop"
        echo "   重启服务: $0 restart"
        
        # 等待几秒钟检查启动日志
        echo ""
        echo "📄 启动日志 (最近10行):"
        sleep 2
        tail -10 "$LOG_FILE" 2>/dev/null || echo "   (日志文件尚未生成)"
        
    else
        echo "   ❌ 服务器启动失败"
        echo "   📄 错误日志:"
        tail -20 "$LOG_FILE" 2>/dev/null || echo "   (无日志文件)"
        rm -f "$PID_FILE"
        exit 1
    fi
}

# 重启服务器
restart_server() {
    echo "🔄 重启服务器..."
    stop_server
    sleep 2
    start_server
}

# 显示状态
show_status() {
    echo "📊 服务器状态:"
    
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p $PID > /dev/null 2>&1; then
            echo "   ✅ 运行中 (PID: $PID)"
            echo "   📈 内存使用: $(ps -p $PID -o rss= | awk '{printf "%.1fMB", $1/1024}')"
            echo "   ⏰ 启动时间: $(ps -p $PID -o lstart= | sed 's/^ *//')"
            echo "   🌐 端口: http://8.138.114.34:8080"
        else
            echo "   ❌ 未运行 (PID文件存在但进程不存在)"
            rm -f "$PID_FILE"
        fi
    else
        echo "   ❌ 未运行"
    fi
    
    echo ""
    echo "📄 日志文件:"
    if [ -f "$LOG_FILE" ]; then
        echo "   文件: $LOG_FILE"
        echo "   大小: $(ls -lh "$LOG_FILE" | awk '{print $5}')"
        echo "   修改: $(ls -l "$LOG_FILE" | awk '{print $6, $7, $8}')"
    else
        echo "   (无日志文件)"
    fi
}

# 显示日志
show_logs() {
    local lines=${1:-50}
    echo "📄 服务器日志 (最近 $lines 行):"
    echo "----------------------------------------"
    
    if [ -f "$LOG_FILE" ]; then
        tail -$lines "$LOG_FILE"
    else
        echo "(无日志文件)"
    fi
}

# 主逻辑
case "${1:-start}" in
    "start")
        if check_running; then
            exit 0
        fi
        start_server
        ;;
    "stop")
        stop_server
        ;;
    "restart")
        restart_server
        ;;
    "status")
        show_status
        ;;
    "logs")
        show_logs ${2:-50}
        ;;
    "follow")
        echo "📄 实时日志 (Ctrl+C 退出):"
        echo "----------------------------------------"
        tail -f "$LOG_FILE" 2>/dev/null || echo "日志文件不存在"
        ;;
    *)
        echo "用法: $0 {start|stop|restart|status|logs [行数]|follow}"
        echo ""
        echo "命令说明:"
        echo "  start   - 启动服务器 (默认)"
        echo "  stop    - 停止服务器"
        echo "  restart - 重启服务器"
        echo "  status  - 显示状态"
        echo "  logs    - 显示日志 (默认50行)"
        echo "  follow  - 实时跟踪日志"
        exit 1
        ;;
esac