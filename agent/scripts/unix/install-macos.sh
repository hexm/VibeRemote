#!/bin/bash

# VibeRemote Agent macOS 一键安装脚本
# 用户级安装，无需 sudo，安装到用户目录

set -e

# 默认配置
SERVER_URL="__SERVER_URL__"
PACKAGE_BASE_URL="__PACKAGE_BASE_URL__"
INSTALL_DIR="$HOME/.viberemote-agent"
SERVICE_NAME="com.viberemote.agent"
MANUAL_MODE=""
REGISTER_TOKEN="__REGISTER_TOKEN__"
VERSION="__AGENT_VERSION__"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=========================================="
echo "  VibeRemote Agent macOS 安装程序"
echo -e "==========================================${NC}"
echo ""

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --install-dir=*) INSTALL_DIR="${1#*=}"; shift ;;
        --install-dir) INSTALL_DIR="$2"; shift 2 ;;
        --manual) MANUAL_MODE="1"; shift ;;
        --help)
            echo "用法: $0 [选项]"
            echo "  --install-dir DIR   安装目录 (默认: ~/.viberemote-agent)"
            echo "  --manual            手动模式，不自动启动"
            echo ""
            echo "发布后的安装脚本会自动带入当前环境地址和注册令牌"
            exit 0 ;;
        *) echo -e "${RED}未知参数: $1${NC}"; exit 1 ;;
    esac
done

if [[ -z "$SERVER_URL" || "$SERVER_URL" == __SERVER_URL__ ]]; then
    echo -e "${RED}❌ 安装脚本缺少服务器地址，请重新按当前环境发布安装脚本${NC}"
    exit 1
fi

if [[ -z "$REGISTER_TOKEN" || "$REGISTER_TOKEN" == __REGISTER_TOKEN__ ]]; then
    echo -e "${RED}❌ 安装脚本缺少注册令牌，请重新按当前环境发布安装脚本${NC}"
    exit 1
fi

if [[ -z "$PACKAGE_BASE_URL" || "$PACKAGE_BASE_URL" == __PACKAGE_BASE_URL__ ]]; then
    echo -e "${RED}❌ 安装脚本缺少安装包下载地址，请重新按当前环境发布安装脚本${NC}"
    exit 1
fi

echo -e "  服务器地址: ${GREEN}${SERVER_URL}${NC}"
echo -e "  安装目录:   ${GREEN}${INSTALL_DIR}${NC}"
echo ""

# 检测架构
ARCH=$(uname -m)
case $ARCH in
    x86_64)  PACKAGE_NAME="viberemote-agent-${VERSION}-macos-x64.tar.gz"; echo -e "  架构: ${GREEN}Intel x64${NC}" ;;
    arm64)   PACKAGE_NAME="viberemote-agent-${VERSION}-macos-arm64.tar.gz"; echo -e "  架构: ${GREEN}Apple Silicon${NC}" ;;
    *)       PACKAGE_NAME="viberemote-agent-${VERSION}-macos-x64.tar.gz"; echo -e "  架构: ${YELLOW}未知，使用x64${NC}" ;;
esac
echo ""

# 停止现有服务（不需要 sudo）
echo -e "${YELLOW}🛑 检查现有服务...${NC}"
PLIST="$HOME/Library/LaunchAgents/${SERVICE_NAME}.plist"
if [ -f "$PLIST" ]; then
    launchctl unload "$PLIST" 2>/dev/null || true
    echo -e "${GREEN}✅ 已停止旧服务${NC}"
fi
pkill -f "java.*agent.jar" 2>/dev/null || true

# 下载安装包
echo -e "${YELLOW}📦 下载安装包...${NC}"
DOWNLOAD_URL="${PACKAGE_BASE_URL}/$PACKAGE_NAME"
echo -e "  ${BLUE}${DOWNLOAD_URL}${NC}"

TEMP_DIR=$(mktemp -d)
if curl -fL --progress-bar "$DOWNLOAD_URL" -o "$TEMP_DIR/$PACKAGE_NAME"; then
    echo -e "${GREEN}✅ 下载完成${NC}"
else
    echo -e "${RED}❌ 下载失败${NC}"
    rm -rf "$TEMP_DIR"
    exit 1
fi

# 安装
echo -e "${YELLOW}📁 安装到 ${INSTALL_DIR}...${NC}"
mkdir -p "$INSTALL_DIR"
tar -xzf "$TEMP_DIR/$PACKAGE_NAME" -C "$INSTALL_DIR" --strip-components=1
rm -rf "$TEMP_DIR"
chmod +x "$INSTALL_DIR"/*.sh 2>/dev/null || true
echo -e "${GREEN}✅ 解压完成${NC}"

# 写配置
cat > "$INSTALL_DIR/agent.properties" << EOF
server.url=$SERVER_URL
register.token=$REGISTER_TOKEN
agent.labels=os=macos,arch=$ARCH
log.level=INFO
encryption.enabled=true
EOF

# 创建日志目录
mkdir -p "$INSTALL_DIR/logs"

# 创建 LaunchAgent（用户级，无需 sudo）
echo -e "${YELLOW}🔧 配置开机自启 (LaunchAgent)...${NC}"
mkdir -p "$HOME/Library/LaunchAgents"
cat > "$PLIST" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>${SERVICE_NAME}</string>
    <key>ProgramArguments</key>
    <array>
        <string>${INSTALL_DIR}/start-agent.sh</string>
        <string>--launchd</string>
    </array>
    <key>WorkingDirectory</key>
    <string>${INSTALL_DIR}</string>
    <key>RunAtLoad</key>
    <true/>
    <key>StandardOutPath</key>
    <string>${INSTALL_DIR}/logs/agent-stdout.log</string>
    <key>StandardErrorPath</key>
    <string>${INSTALL_DIR}/logs/agent-stderr.log</string>
    <key>EnvironmentVariables</key>
    <dict>
        <key>PATH</key>
        <string>/usr/local/bin:/usr/bin:/bin</string>
    </dict>
</dict>
</plist>
EOF
chmod 644 "$PLIST"
echo -e "${GREEN}✅ LaunchAgent 配置完成${NC}"

# 启动
if [ -z "$MANUAL_MODE" ]; then
    echo -e "${YELLOW}🚀 启动 Agent...${NC}"
    launchctl load "$PLIST"
    sleep 2
    if launchctl list | grep -q "$SERVICE_NAME"; then
        echo -e "${GREEN}✅ Agent 启动成功${NC}"
    else
        echo -e "${YELLOW}⚠️  Agent 可能正在启动中，请稍后用 check-status.sh 确认${NC}"
    fi
fi

echo ""
echo -e "${GREEN}=========================================="
echo "✅ VibeRemote Agent 安装完成！"
echo -e "==========================================${NC}"
echo ""
echo -e "  安装目录: ${GREEN}$INSTALL_DIR${NC}"
echo -e "  服务器:   ${GREEN}$SERVER_URL${NC}"
echo ""
echo -e "${BLUE}常用命令：${NC}"
echo -e "  查看状态: ${YELLOW}$INSTALL_DIR/check-status.sh${NC}"
echo -e "  停止:     ${YELLOW}$INSTALL_DIR/stop-agent.sh${NC}"
echo -e "  启动:     ${YELLOW}$INSTALL_DIR/start-agent.sh${NC}"
echo -e "  查看日志: ${YELLOW}tail -f $INSTALL_DIR/logs/agent.log${NC}"
echo ""
