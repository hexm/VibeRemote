#!/bin/bash

# LightScript Agent 安装包部署脚本
# 专门用于上传Agent安装包到阿里云服务器

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SERVER_IP="8.138.114.34"
SERVER_USER="root"
REMOTE_RELEASES_DIR="/var/www/html/agent/release"
REMOTE_SCRIPTS_DIR="/var/www/html/scripts"
VERSION_HELPER="$PROJECT_ROOT/agent/scripts/get-agent-version.sh"
AGENT_VERSION="$(bash "$VERSION_HELPER")"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 显示使用说明
show_usage() {
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  --platform=PLATFORM    只上传指定平台的安装包"
    echo "                         可选值: windows, windows-x64, windows-x86, linux, macos-x64, macos-arm64, all"
    echo "  --help                 显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  $0                     # 上传所有平台安装包"
    echo "  $0 --platform=windows # 只上传Windows安装包"
    echo "  $0 --platform=linux   # 只上传Linux安装包"
    echo "  $0 --platform=macos-x64 # 只上传macOS x64安装包"
    echo "  $0 --platform=all     # 上传所有平台安装包"
}

# 解析命令行参数
PLATFORM="all"
AUTO_CONFIRM=false

for arg in "$@"; do
    case $arg in
        --platform=*)
            PLATFORM="${arg#*=}"
            shift
            ;;
        --yes|-y)
            AUTO_CONFIRM=true
            shift
            ;;
        --help)
            show_usage
            exit 0
            ;;
        *)
            echo -e "${RED}未知参数: $arg${NC}"
            show_usage
            exit 1
            ;;
    esac
done

echo -e "${BLUE}=========================================="
echo "LightScript Agent 安装包部署脚本"
echo -e "==========================================${NC}"
echo -e "服务器: ${GREEN}${SERVER_IP}${NC}"
echo -e "目标目录: ${GREEN}${REMOTE_RELEASES_DIR}${NC}"
echo -e "部署平台: ${GREEN}${PLATFORM}${NC}"
echo ""

# 检查SSH连接
echo -e "${YELLOW}🔍 检查SSH连接...${NC}"
if ! ssh -o BatchMode=yes -o ConnectTimeout=5 ${SERVER_USER}@${SERVER_IP} "echo '连接成功'" 2>/dev/null; then
    echo -e "${RED}❌ SSH连接失败！${NC}"
    echo -e "${YELLOW}请确保SSH密钥已配置${NC}"
    exit 1
fi
echo -e "${GREEN}✅ SSH连接正常${NC}"
echo ""

# 检查本地安装包
echo -e "${YELLOW}🔍 检查本地安装包...${NC}"
RELEASE_DIR="$PROJECT_ROOT/agent/release"

if [ ! -d "$RELEASE_DIR" ]; then
    echo -e "${RED}❌ 安装包目录不存在: $RELEASE_DIR${NC}"
    echo -e "${YELLOW}请先运行构建脚本: cd agent && ./build-release.sh${NC}"
    exit 1
fi

MANIFEST_FILE="$RELEASE_DIR/version.json"
if [ ! -f "$MANIFEST_FILE" ]; then
    echo -e "${RED}❌ 版本清单不存在: $MANIFEST_FILE${NC}"
    echo -e "${YELLOW}请先运行构建脚本生成安装包和版本清单${NC}"
    exit 1
fi

# 根据平台选择要上传的安装包
ALL_PACKAGES=(
    "viberemote-agent-${AGENT_VERSION}-windows-x64.zip"
    "viberemote-agent-${AGENT_VERSION}-windows-x86.zip"
    "viberemote-agent-${AGENT_VERSION}-linux-x64.tar.gz"
    "viberemote-agent-${AGENT_VERSION}-macos-x64.tar.gz"
    "viberemote-agent-${AGENT_VERSION}-macos-arm64.tar.gz"
)

case "$PLATFORM" in
    "windows")
        PACKAGES=("viberemote-agent-${AGENT_VERSION}-windows-x64.zip" "viberemote-agent-${AGENT_VERSION}-windows-x86.zip")
        ;;
    "windows-x64")
        PACKAGES=("viberemote-agent-${AGENT_VERSION}-windows-x64.zip")
        ;;
    "windows-x86")
        PACKAGES=("viberemote-agent-${AGENT_VERSION}-windows-x86.zip")
        ;;
    "linux")
        PACKAGES=("viberemote-agent-${AGENT_VERSION}-linux-x64.tar.gz")
        ;;
    "macos-x64")
        PACKAGES=("viberemote-agent-${AGENT_VERSION}-macos-x64.tar.gz")
        ;;
    "macos-arm64")
        PACKAGES=("viberemote-agent-${AGENT_VERSION}-macos-arm64.tar.gz")
        ;;
    "all")
        PACKAGES=("${ALL_PACKAGES[@]}")
        ;;
    *)
        echo -e "${RED}❌ 不支持的平台: $PLATFORM${NC}"
        echo -e "${YELLOW}支持的平台: windows, windows-x64, windows-x86, linux, macos-x64, macos-arm64, all${NC}"
        exit 1
        ;;
esac

MISSING_PACKAGES=()
TOTAL_SIZE=0

for package in "${PACKAGES[@]}"; do
    package_path="$RELEASE_DIR/$package"
    if [ -f "$package_path" ]; then
        size=$(stat -f%z "$package_path" 2>/dev/null || stat -c%s "$package_path" 2>/dev/null || echo "0")
        size_mb=$((size / 1024 / 1024))
        TOTAL_SIZE=$((TOTAL_SIZE + size))
        echo -e "  ✅ $package (${size_mb}MB)"
    else
        echo -e "  ❌ $package (缺失)"
        MISSING_PACKAGES+=("$package")
    fi
done

if [ ${#MISSING_PACKAGES[@]} -gt 0 ]; then
    echo -e "${RED}❌ 缺少 ${#MISSING_PACKAGES[@]} 个安装包文件${NC}"
    echo -e "${YELLOW}请先运行构建脚本生成所有安装包${NC}"
    exit 1
fi

TOTAL_SIZE_MB=$((TOTAL_SIZE / 1024 / 1024))
echo -e "${GREEN}✅ 选定的安装包文件就绪，总大小: ${TOTAL_SIZE_MB}MB${NC}"
echo ""

# 显示将要上传的文件
echo -e "${BLUE}将要上传的文件:${NC}"
for package in "${PACKAGES[@]}"; do
    package_path="$RELEASE_DIR/$package"
    if [ -f "$package_path" ]; then
        size=$(stat -f%z "$package_path" 2>/dev/null || stat -c%s "$package_path" 2>/dev/null || echo "0")
        size_mb=$((size / 1024 / 1024))
        echo -e "  📦 $package (${size_mb}MB)"
    fi
done
echo ""

# 询问是否继续
echo -e "${YELLOW}⚠️  即将上传 ${TOTAL_SIZE_MB}MB 的安装包文件到阿里云${NC}"

# 检查是否有自动确认参数
if [ "$AUTO_CONFIRM" = true ]; then
    echo "自动确认上传..."
else
    read -p "是否继续？(y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${YELLOW}取消上传${NC}"
        exit 0
    fi
fi

# 创建远程目录
echo -e "${YELLOW}📁 创建远程目录...${NC}"
ssh ${SERVER_USER}@${SERVER_IP} "mkdir -p ${REMOTE_RELEASES_DIR} ${REMOTE_SCRIPTS_DIR}"

echo -e "${YELLOW}🧹 清理远程旧安装包...${NC}"
ssh ${SERVER_USER}@${SERVER_IP} "
    find ${REMOTE_RELEASES_DIR} -maxdepth 1 -type f \
        \\( -name 'viberemote-agent-*.zip' -o -name 'viberemote-agent-*.tar.gz' \\) \
        ! -name 'viberemote-agent-${AGENT_VERSION}-*' -delete
    find ${REMOTE_SCRIPTS_DIR} -maxdepth 1 -type f -name 'viberemote-agent-*-install-*' -delete
"

# 上传安装包
echo -e "${YELLOW}📤 开始上传安装包...${NC}"
echo -e "${BLUE}上传进度:${NC}"

upload_start_time=$(date +%s)

for i in "${!PACKAGES[@]}"; do
    package="${PACKAGES[i]}"
    package_path="$RELEASE_DIR/$package"
    progress=$((i + 1))
    total=${#PACKAGES[@]}
    
    echo -e "${BLUE}[$progress/$total] 上传 $package...${NC}"
    
    # 使用scp上传（rsync在某些系统上不可用）
    echo -e "${BLUE}使用scp上传...${NC}"
    scp "$package_path" "${SERVER_USER}@${SERVER_IP}:${REMOTE_RELEASES_DIR}/"
    upload_result=$?
    
    if [ $upload_result -eq 0 ]; then
        echo -e "${GREEN}  ✅ $package 上传成功${NC}"
    else
        echo -e "${RED}  ❌ $package 上传失败${NC}"
        exit 1
    fi
done

echo -e "${BLUE}上传版本清单...${NC}"
scp "$MANIFEST_FILE" "${SERVER_USER}@${SERVER_IP}:${REMOTE_RELEASES_DIR}/version.json"

echo -e "${BLUE}上传安装脚本...${NC}"
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

sed "s/__AGENT_VERSION__/${AGENT_VERSION}/g" "$PROJECT_ROOT/agent/scripts/unix/install-linux.sh" > "$TMP_DIR/install-linux.sh"
sed "s/__AGENT_VERSION__/${AGENT_VERSION}/g" "$PROJECT_ROOT/agent/scripts/unix/install-macos.sh" > "$TMP_DIR/install-macos.sh"
sed "s/__AGENT_VERSION__/${AGENT_VERSION}/g" "$PROJECT_ROOT/agent/scripts/windows/install-agent.bat" > "$TMP_DIR/install-agent.bat"

scp "$TMP_DIR/install-linux.sh" "${SERVER_USER}@${SERVER_IP}:${REMOTE_SCRIPTS_DIR}/viberemote-agent-${AGENT_VERSION}-install-linux.sh"
scp "$TMP_DIR/install-macos.sh" "${SERVER_USER}@${SERVER_IP}:${REMOTE_SCRIPTS_DIR}/viberemote-agent-${AGENT_VERSION}-install-macos.sh"
scp "$TMP_DIR/install-agent.bat" "${SERVER_USER}@${SERVER_IP}:${REMOTE_SCRIPTS_DIR}/viberemote-agent-${AGENT_VERSION}-install-windows.bat"
ssh ${SERVER_USER}@${SERVER_IP} "chmod +x ${REMOTE_SCRIPTS_DIR}/viberemote-agent-${AGENT_VERSION}-install-linux.sh ${REMOTE_SCRIPTS_DIR}/viberemote-agent-${AGENT_VERSION}-install-macos.sh"
ssh ${SERVER_USER}@${SERVER_IP} "sed -i 's/\r//' ${REMOTE_SCRIPTS_DIR}/viberemote-agent-${AGENT_VERSION}-install-windows.bat && sed -i 's/\$/\r/' ${REMOTE_SCRIPTS_DIR}/viberemote-agent-${AGENT_VERSION}-install-windows.bat"

upload_end_time=$(date +%s)
upload_duration=$((upload_end_time - upload_start_time))
upload_speed=$((TOTAL_SIZE_MB / upload_duration))

echo -e "${GREEN}✅ 选定的安装包上传完成${NC}"
echo -e "${BLUE}上传统计: ${TOTAL_SIZE_MB}MB 用时 ${upload_duration}秒，平均速度 ${upload_speed}MB/s${NC}"
echo ""

# 验证上传结果
echo -e "${YELLOW}🔍 验证上传结果...${NC}"
ssh ${SERVER_USER}@${SERVER_IP} "
    echo '远程安装包列表:'
    ls -lh ${REMOTE_RELEASES_DIR}/*.{zip,tar.gz,json} 2>/dev/null | while read line; do
        echo \"  \$line\"
    done
    
    echo ''
    echo '磁盘使用情况:'
    du -sh ${REMOTE_RELEASES_DIR}
"

# 测试下载链接
echo -e "${YELLOW}🌐 测试下载链接...${NC}"
for package in "${PACKAGES[@]}"; do
    download_url="http://${SERVER_IP}/agent/release/${package}"
    if curl -I "$download_url" 2>/dev/null | head -1 | grep -q "200 OK"; then
        echo -e "  ✅ $download_url"
    else
        echo -e "  ❌ $download_url"
    fi
done

echo ""
echo -e "${GREEN}=========================================="
echo "✅ Agent 安装包部署完成！"
echo -e "==========================================${NC}"
echo ""
echo -e "${BLUE}已上传的安装包：${NC}"
for package in "${PACKAGES[@]}"; do
    echo -e "  • http://${SERVER_IP}/agent/release/${package}"
done
echo ""
echo -e "${BLUE}管理命令：${NC}"
echo -e "  查看远程文件: ${YELLOW}ssh ${SERVER_USER}@${SERVER_IP} 'ls -la ${REMOTE_RELEASES_DIR}'${NC}"
echo ""
echo -e "${BLUE}一键安装命令：${NC}"
echo -e "  Linux:   ${GREEN}curl -fsSL http://${SERVER_IP}/scripts/viberemote-agent-${AGENT_VERSION}-install-linux.sh | sudo bash -s -- --server=http://${SERVER_IP}:8080${NC}"
echo -e "  macOS:   ${GREEN}curl -fsSL http://${SERVER_IP}/scripts/viberemote-agent-${AGENT_VERSION}-install-macos.sh | bash -s -- --server=http://${SERVER_IP}:8080${NC}"
echo -e "  Windows: ${GREEN}PowerShell 执行安装脚本${NC}"
echo ""
