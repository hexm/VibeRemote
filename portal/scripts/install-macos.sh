#!/bin/bash

# LightScript Agent macOS 一键安装脚本
# 支持 Intel 和 Apple Silicon Mac

set -e

# 默认配置
SERVER_URL="http://8.138.114.34:8080"
INSTALL_DIR="/usr/local/lightscript-agent"
SERVICE_NAME="com.lightscript.agent"
MANUAL_MODE=""
REGISTER_TOKEN="917ab328ac48ff6aeb01f38b3a3a554a07a9b623f60a9bdde9ac73a9353acc83"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=========================================="
echo "  LightScript Agent macOS 安装程序"
echo -e "==========================================${NC}"
echo ""

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --server)
            SERVER_URL="$2"
            shift 2
            ;;
        --install-dir)
            INSTALL_DIR="$2"
            shift 2
            ;;
        --manual)
            MANUAL_MODE="1"
            shift
            ;;
        --token)
            REGISTER_TOKEN="$2"
            shift 2
            ;;
        --help)
            echo "用法: $0 [选项]"
            echo ""
            echo "选项:"
            echo "  --server URL        指定服务器地址 (默认: http://8.138.114.34:8080)"
            echo "  --install-dir DIR   指定安装目录 (默认: /usr/local/lightscript-agent)"
            echo "  --token TOKEN       指定注册令牌 (默认: dev-register-token-2024)"
            echo "  --manual           手动模式，不自动启动服务"
            echo "  --help             显示此帮助信息"
            exit 0
            ;;
        *)
            echo -e "${RED}未知参数: $1${NC}"
            exit 1
            ;;
    esac
done

echo -e "${BLUE}配置信息：${NC}"
echo -e "  服务器地址: ${GREEN}${SERVER_URL}${NC}"
echo -e "  安装目录: ${GREEN}${INSTALL_DIR}${NC}"
echo -e "  注册令牌: ${GREEN}${REGISTER_TOKEN}${NC}"
echo ""

# 检查权限
check_permissions() {
    if [[ $EUID -eq 0 ]]; then
        echo -e "${YELLOW}⚠️  检测到root权限，建议使用普通用户运行${NC}"
        echo -e "${YELLOW}如需继续，请确认 (y/N): ${NC}"
        read -r confirm
        if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
}

# 检测系统架构
detect_arch() {
    ARCH=$(uname -m)
    case $ARCH in
        x86_64)
            PACKAGE_NAME="lightscript-agent-0.4.0-macos-x64.tar.gz"
            echo -e "${BLUE}检测到架构: ${GREEN}Intel x64${NC}"
            ;;
        arm64)
            PACKAGE_NAME="lightscript-agent-0.4.0-macos-arm64.tar.gz"
            echo -e "${BLUE}检测到架构: ${GREEN}Apple Silicon (ARM64)${NC}"
            ;;
        *)
            echo -e "${YELLOW}⚠️  未知架构 $ARCH，使用x64版本${NC}"
            PACKAGE_NAME="lightscript-agent-0.4.0-macos-x64.tar.gz"
            ;;
    esac
}

# 检查依赖
check_dependencies() {
    echo -e "${YELLOW}🔧 检查依赖...${NC}"
    
    # 检查curl
    if ! command -v curl &> /dev/null; then
        echo -e "${RED}❌ curl未安装，请先安装curl${NC}"
        exit 1
    fi
    
    # 检查Java
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2)
        echo -e "${GREEN}✅ Java已安装: ${JAVA_VERSION}${NC}"
    else
        echo -e "${YELLOW}⚠️  Java未安装${NC}"
        echo -e "${BLUE}请安装Java 11或更高版本：${NC}"
        echo -e "  方式1: brew install openjdk@11"
        echo -e "  方式2: 从Oracle官网下载安装"
        echo -e "  方式3: 使用系统自带的Java安装提示"
        echo ""
        echo -e "${YELLOW}是否继续安装？Agent需要Java运行 (y/N): ${NC}"
        read -r confirm
        if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
}

# 停止现有服务
stop_existing_service() {
    echo -e "${YELLOW}🛑 检查现有服务...${NC}"
    
    # 检查launchd服务
    if launchctl list | grep -q "$SERVICE_NAME" 2>/dev/null; then
        echo -e "${BLUE}停止现有launchd服务...${NC}"
        launchctl unload ~/Library/LaunchAgents/${SERVICE_NAME}.plist 2>/dev/null || true
        launchctl remove $SERVICE_NAME 2>/dev/null || true
        echo -e "${GREEN}✅ 服务已停止${NC}"
    fi
    
    # 检查进程
    if pgrep -f "lightscript.*agent" > /dev/null; then
        echo -e "${BLUE}终止现有Agent进程...${NC}"
        pkill -f "lightscript.*agent" || true
        sleep 2
    fi
}

# 下载和安装
download_and_install() {
    echo -e "${YELLOW}📦 下载Agent安装包...${NC}"
    
    DOWNLOAD_URL="${SERVER_URL%:*}:80/agent/release/$PACKAGE_NAME"
    echo -e "${BLUE}下载地址: ${DOWNLOAD_URL}${NC}"
    
    # 创建临时目录
    TEMP_DIR=$(mktemp -d)
    cd "$TEMP_DIR"
    
    # 下载文件
    if curl -fL "$DOWNLOAD_URL" -o "$PACKAGE_NAME"; then
        echo -e "${GREEN}✅ 下载完成${NC}"
    else
        echo -e "${RED}❌ 下载失败${NC}"
        exit 1
    fi
    
    # 创建安装目录
    echo -e "${YELLOW}📁 创建安装目录...${NC}"
    sudo mkdir -p "$INSTALL_DIR"
    
    # 备份现有安装
    if [[ -d "$INSTALL_DIR" && "$(ls -A $INSTALL_DIR)" ]]; then
        BACKUP_DIR="${INSTALL_DIR}.backup.$(date +%Y%m%d-%H%M%S)"
        echo -e "${BLUE}备份现有安装到: $BACKUP_DIR${NC}"
        sudo cp -r "$INSTALL_DIR" "$BACKUP_DIR"
    fi
    
    # 解压安装包
    echo -e "${YELLOW}📦 解压安装包...${NC}"
    sudo tar -xzf "$PACKAGE_NAME" -C "$INSTALL_DIR" --strip-components=1
    
    echo -e "${GREEN}✅ 安装完成${NC}"
    
    # 清理临时文件
    cd /
    rm -rf "$TEMP_DIR"
}

# 配置Agent
configure_agent() {
    echo -e "${YELLOW}⚙️  配置Agent...${NC}"
    
    # 创建配置文件
    sudo tee "$INSTALL_DIR/agent.properties" > /dev/null << EOF
# LightScript Agent 配置文件
# 自动生成于 $(date)

# 服务器配置
server.url=$SERVER_URL
register.token=$REGISTER_TOKEN

# Agent配置
agent.name=$(hostname)
agent.labels=os=macos,arch=$(uname -m),auto-installed=true

# 日志配置
log.level=INFO
log.file.enabled=true
log.file.path=logs/agent.log

# 心跳配置
heartbeat.interval=30000
heartbeat.timeout=10000

# 任务配置
task.pull.interval=5000
task.timeout.default=300000

# 加密配置
encryption.enabled=true
EOF
    
    # 设置权限
    sudo chown -R $(whoami):staff "$INSTALL_DIR"
    sudo chmod +x "$INSTALL_DIR"/*.sh 2>/dev/null || true
    sudo chmod 644 "$INSTALL_DIR/agent.properties"
    
    echo -e "${GREEN}✅ 配置完成${NC}"
}

# 创建launchd服务
create_service() {
    echo -e "${YELLOW}🔧 创建系统服务...${NC}"
    
    # 创建LaunchAgents目录
    mkdir -p ~/Library/LaunchAgents
    
    # 创建plist文件
    cat > ~/Library/LaunchAgents/${SERVICE_NAME}.plist << EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>$SERVICE_NAME</string>
    <key>ProgramArguments</key>
    <array>
        <string>/usr/bin/java</string>
        <string>-Xmx512m</string>
        <string>-Xms256m</string>
        <string>-jar</string>
        <string>$INSTALL_DIR/agent.jar</string>
    </array>
    <key>WorkingDirectory</key>
    <string>$INSTALL_DIR</string>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>StandardOutPath</key>
    <string>$INSTALL_DIR/logs/agent-stdout.log</string>
    <key>StandardErrorPath</key>
    <string>$INSTALL_DIR/logs/agent-stderr.log</string>
    <key>EnvironmentVariables</key>
    <dict>
        <key>PATH</key>
        <string>/usr/local/bin:/usr/bin:/bin</string>
    </dict>
</dict>
</plist>
EOF
    
    # 设置权限
    chmod 644 ~/Library/LaunchAgents/${SERVICE_NAME}.plist
    
    echo -e "${GREEN}✅ 系统服务创建完成${NC}"
}

# 启动服务
start_service() {
    if [[ -z "$MANUAL_MODE" ]]; then
        echo -e "${YELLOW}🚀 启动Agent服务...${NC}"
        
        # 创建日志目录
        sudo mkdir -p "$INSTALL_DIR/logs"
        sudo chown -R $(whoami):staff "$INSTALL_DIR/logs"
        
        # 加载并启动服务
        launchctl load ~/Library/LaunchAgents/${SERVICE_NAME}.plist
        sleep 3
        
        if launchctl list | grep -q "$SERVICE_NAME"; then
            echo -e "${GREEN}✅ Agent服务启动成功${NC}"
        else
            echo -e "${RED}❌ Agent服务启动失败${NC}"
            echo -e "${YELLOW}查看日志: tail -f $INSTALL_DIR/logs/agent-stdout.log${NC}"
            exit 1
        fi
    else
        echo -e "${YELLOW}⚠️  手动模式，服务未自动启动${NC}"
        echo -e "${BLUE}手动启动命令: launchctl load ~/Library/LaunchAgents/${SERVICE_NAME}.plist${NC}"
    fi
}

# 显示安装结果
show_result() {
    echo ""
    echo -e "${GREEN}=========================================="
    echo "✅ LightScript Agent 安装完成！"
    echo -e "==========================================${NC}"
    echo ""
    echo -e "${BLUE}安装信息：${NC}"
    echo -e "  安装目录: ${GREEN}$INSTALL_DIR${NC}"
    echo -e "  配置文件: ${GREEN}$INSTALL_DIR/agent.properties${NC}"
    echo -e "  服务名称: ${GREEN}$SERVICE_NAME${NC}"
    echo -e "  服务文件: ${GREEN}~/Library/LaunchAgents/${SERVICE_NAME}.plist${NC}"
    echo -e "  服务器地址: ${GREEN}$SERVER_URL${NC}"
    echo ""
    echo -e "${BLUE}管理命令：${NC}"
    echo -e "  启动服务: ${YELLOW}launchctl load ~/Library/LaunchAgents/${SERVICE_NAME}.plist${NC}"
    echo -e "  停止服务: ${YELLOW}launchctl unload ~/Library/LaunchAgents/${SERVICE_NAME}.plist${NC}"
    echo -e "  查看状态: ${YELLOW}launchctl list | grep $SERVICE_NAME${NC}"
    echo -e "  查看日志: ${YELLOW}tail -f $INSTALL_DIR/logs/agent-stdout.log${NC}"
    echo ""
    echo -e "${BLUE}配置文件: ${GREEN}$INSTALL_DIR/agent.properties${NC}"
    echo -e "${BLUE}日志目录: ${GREEN}$INSTALL_DIR/logs/${NC}"
    echo ""
    
    if [[ -z "$MANUAL_MODE" ]]; then
        echo -e "${GREEN}Agent服务已自动启动并设置为开机自启${NC}"
    else
        echo -e "${YELLOW}手动模式：请使用 'launchctl load ~/Library/LaunchAgents/${SERVICE_NAME}.plist' 启动服务${NC}"
    fi
    echo ""
}

# 主安装流程
main() {
    check_permissions
    detect_arch
    check_dependencies
    stop_existing_service
    download_and_install
    configure_agent
    create_service
    start_service
    show_result
}

# 执行安装
main "$@"