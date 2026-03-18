#!/bin/bash

# LightScript Agent Linux 一键安装脚本
# 支持 Ubuntu/Debian/CentOS/RHEL/Rocky Linux

set -e

# 默认配置
SERVER_URL="http://8.138.114.34:8080"
INSTALL_DIR="/opt/lightscript-agent"
SERVICE_NAME="lightscript-agent"
MANUAL_MODE=""
REGISTER_TOKEN="917ab328ac48ff6aeb01f38b3a3a554a07a9b623f60a9bdde9ac73a9353acc83"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=========================================="
echo "  LightScript Agent Linux 安装程序"
echo -e "==========================================${NC}"
echo ""

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --server=*)
            SERVER_URL="${1#*=}"
            shift
            ;;
        --server)
            SERVER_URL="$2"
            shift 2
            ;;
        --install-dir=*)
            INSTALL_DIR="${1#*=}"
            shift
            ;;
        --install-dir)
            INSTALL_DIR="$2"
            shift 2
            ;;
        --manual)
            MANUAL_MODE="1"
            shift
            ;;
        --token=*)
            REGISTER_TOKEN="${1#*=}"
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
            echo "  --install-dir DIR   指定安装目录 (默认: /opt/lightscript-agent)"
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
if [[ $EUID -ne 0 ]]; then
   echo -e "${RED}❌ 此脚本需要root权限运行${NC}"
   echo -e "${YELLOW}请使用: sudo $0${NC}"
   exit 1
fi

# 检测系统类型
detect_os() {
    if [[ -f /etc/os-release ]]; then
        . /etc/os-release
        OS=$ID
        VER=$VERSION_ID
    elif type lsb_release >/dev/null 2>&1; then
        OS=$(lsb_release -si | tr '[:upper:]' '[:lower:]')
        VER=$(lsb_release -sr)
    else
        OS=$(uname -s | tr '[:upper:]' '[:lower:]')
        VER=$(uname -r)
    fi
    
    echo -e "${BLUE}检测到系统: ${GREEN}${OS} ${VER}${NC}"
}

# 安装依赖
install_dependencies() {
    echo -e "${YELLOW}🔧 安装依赖包...${NC}"
    
    case $OS in
        ubuntu|debian)
            apt-get update
            apt-get install -y curl wget tar openjdk-11-jre-headless
            ;;
        centos|rhel|rocky|almalinux)
            if command -v dnf &> /dev/null; then
                dnf install -y curl wget tar java-11-openjdk-headless
            else
                yum install -y curl wget tar java-11-openjdk-headless
            fi
            ;;
        *)
            echo -e "${YELLOW}⚠️  未识别的系统，尝试通用安装...${NC}"
            # 尝试检测包管理器
            if command -v apt-get &> /dev/null; then
                apt-get update
                apt-get install -y curl wget tar openjdk-11-jre-headless
            elif command -v yum &> /dev/null; then
                yum install -y curl wget tar java-11-openjdk-headless
            elif command -v dnf &> /dev/null; then
                dnf install -y curl wget tar java-11-openjdk-headless
            else
                echo -e "${RED}❌ 无法检测包管理器，请手动安装: curl wget tar java-11-openjdk${NC}"
                exit 1
            fi
            ;;
    esac
    
    echo -e "${GREEN}✅ 依赖安装完成${NC}"
}

# 检查Java
check_java() {
    echo -e "${YELLOW}☕ 检查Java环境...${NC}"
    
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2)
        echo -e "${GREEN}✅ Java已安装: ${JAVA_VERSION}${NC}"
    else
        echo -e "${RED}❌ Java未安装${NC}"
        install_dependencies
    fi
}

# 停止现有服务
stop_existing_service() {
    echo -e "${YELLOW}🛑 检查现有服务...${NC}"
    
    if systemctl is-active --quiet $SERVICE_NAME 2>/dev/null; then
        echo -e "${BLUE}停止现有服务...${NC}"
        systemctl stop $SERVICE_NAME
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
    
    # 检测架构
    ARCH=$(uname -m)
    case $ARCH in
        x86_64)
            PACKAGE_NAME="lightscript-agent-0.4.0-linux-x64.tar.gz"
            ;;
        aarch64|arm64)
            # 如果有ARM版本的话
            PACKAGE_NAME="lightscript-agent-0.4.0-linux-arm64.tar.gz"
            # 如果没有ARM版本，使用x64版本
            if ! curl -s --head "${SERVER_URL%:*}:80/agent/release/$PACKAGE_NAME" | head -n1 | grep -q "200 OK"; then
                echo -e "${YELLOW}⚠️  ARM64版本不可用，使用x64版本${NC}"
                PACKAGE_NAME="lightscript-agent-0.4.0-linux-x64.tar.gz"
            fi
            ;;
        *)
            echo -e "${YELLOW}⚠️  未知架构 $ARCH，使用x64版本${NC}"
            PACKAGE_NAME="lightscript-agent-0.4.0-linux-x64.tar.gz"
            ;;
    esac
    
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
    mkdir -p "$INSTALL_DIR"
    
    # 备份现有安装
    if [[ -d "$INSTALL_DIR" && "$(ls -A $INSTALL_DIR)" ]]; then
        BACKUP_DIR="${INSTALL_DIR}.backup.$(date +%Y%m%d-%H%M%S)"
        echo -e "${BLUE}备份现有安装到: $BACKUP_DIR${NC}"
        cp -r "$INSTALL_DIR" "$BACKUP_DIR"
    fi
    
    # 解压安装包
    echo -e "${YELLOW}📦 解压安装包...${NC}"
    tar -xzf "$PACKAGE_NAME" -C "$INSTALL_DIR" --strip-components=1
    
    echo -e "${GREEN}✅ 安装完成${NC}"
    
    # 清理临时文件
    cd /
    rm -rf "$TEMP_DIR"
}

# 配置Agent
configure_agent() {
    echo -e "${YELLOW}⚙️  配置Agent...${NC}"
    
    # 创建配置文件
    cat > "$INSTALL_DIR/agent.properties" << EOF
# LightScript Agent 配置文件
# 自动生成于 $(date)

# 服务器配置
server.url=$SERVER_URL
register.token=$REGISTER_TOKEN

# Agent配置
agent.name=$(hostname)
agent.labels=os=linux,arch=$(uname -m),auto-installed=true

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
    chown -R root:root "$INSTALL_DIR"
    chmod +x "$INSTALL_DIR"/*.sh 2>/dev/null || true
    chmod 644 "$INSTALL_DIR/agent.properties"
    
    echo -e "${GREEN}✅ 配置完成${NC}"
}

# 创建systemd服务
create_service() {
    echo -e "${YELLOW}🔧 创建系统服务...${NC}"
    
    cat > "/etc/systemd/system/$SERVICE_NAME.service" << EOF
[Unit]
Description=LightScript Agent
After=network.target
Wants=network.target

[Service]
Type=simple
User=root
WorkingDirectory=$INSTALL_DIR
ExecStart=/usr/bin/java -jar $INSTALL_DIR/agent.jar
ExecStop=/bin/kill -TERM \$MAINPID
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=lightscript-agent

# 环境变量
Environment=JAVA_OPTS="-Xmx512m -Xms256m"

[Install]
WantedBy=multi-user.target
EOF
    
    # 重新加载systemd
    systemctl daemon-reload
    systemctl enable $SERVICE_NAME
    
    echo -e "${GREEN}✅ 系统服务创建完成${NC}"
}

# 启动服务
start_service() {
    if [[ -z "$MANUAL_MODE" ]]; then
        echo -e "${YELLOW}🚀 启动Agent服务...${NC}"
        
        systemctl start $SERVICE_NAME
        sleep 3
        
        if systemctl is-active --quiet $SERVICE_NAME; then
            echo -e "${GREEN}✅ Agent服务启动成功${NC}"
        else
            echo -e "${RED}❌ Agent服务启动失败${NC}"
            echo -e "${YELLOW}查看日志: journalctl -u $SERVICE_NAME -f${NC}"
            exit 1
        fi
    else
        echo -e "${YELLOW}⚠️  手动模式，服务未自动启动${NC}"
        echo -e "${BLUE}手动启动命令: systemctl start $SERVICE_NAME${NC}"
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
    echo -e "  服务器地址: ${GREEN}$SERVER_URL${NC}"
    echo ""
    echo -e "${BLUE}管理命令：${NC}"
    echo -e "  启动服务: ${YELLOW}systemctl start $SERVICE_NAME${NC}"
    echo -e "  停止服务: ${YELLOW}systemctl stop $SERVICE_NAME${NC}"
    echo -e "  重启服务: ${YELLOW}systemctl restart $SERVICE_NAME${NC}"
    echo -e "  查看状态: ${YELLOW}systemctl status $SERVICE_NAME${NC}"
    echo -e "  查看日志: ${YELLOW}journalctl -u $SERVICE_NAME -f${NC}"
    echo ""
    echo -e "${BLUE}配置文件: ${GREEN}$INSTALL_DIR/agent.properties${NC}"
    echo -e "${BLUE}日志文件: ${GREEN}$INSTALL_DIR/logs/agent.log${NC}"
    echo ""
    
    if [[ -z "$MANUAL_MODE" ]]; then
        echo -e "${GREEN}Agent服务已自动启动并设置为开机自启${NC}"
    else
        echo -e "${YELLOW}手动模式：请使用 'systemctl start $SERVICE_NAME' 启动服务${NC}"
    fi
    echo ""
}

# 主安装流程
main() {
    detect_os
    check_java
    stop_existing_service
    download_and_install
    configure_agent
    create_service
    start_service
    show_result
}

# 执行安装
main "$@"