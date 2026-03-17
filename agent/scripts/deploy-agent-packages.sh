#!/bin/bash

# LightScript Agent 安装包部署脚本
# 专门用于上传Agent安装包到阿里云服务器

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SERVER_IP="8.138.114.34"
SERVER_USER="root"
REMOTE_RELEASES_DIR="/var/www/html/agent/release"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=========================================="
echo "LightScript Agent 安装包部署脚本"
echo -e "==========================================${NC}"
echo -e "服务器: ${GREEN}${SERVER_IP}${NC}"
echo -e "目标目录: ${GREEN}${REMOTE_RELEASES_DIR}${NC}"
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

# 检查安装包文件
PACKAGES=(
    "lightscript-agent-0.4.0-windows-x64.zip"
    "lightscript-agent-0.4.0-linux-x64.tar.gz"
    "lightscript-agent-0.4.0-macos-x64.tar.gz"
    "lightscript-agent-0.4.0-macos-arm64.tar.gz"
)

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
echo -e "${GREEN}✅ 所有安装包文件就绪，总大小: ${TOTAL_SIZE_MB}MB${NC}"
echo ""

# 询问是否继续
echo -e "${YELLOW}⚠️  即将上传 ${TOTAL_SIZE_MB}MB 的安装包文件到阿里云${NC}"
read -p "是否继续？(y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}取消上传${NC}"
    exit 0
fi

# 创建远程目录
echo -e "${YELLOW}📁 创建远程目录...${NC}"
ssh ${SERVER_USER}@${SERVER_IP} "mkdir -p ${REMOTE_RELEASES_DIR}"

# 备份现有安装包（如果存在）
echo -e "${YELLOW}💾 备份现有安装包...${NC}"
ssh ${SERVER_USER}@${SERVER_IP} "
    if [ -d ${REMOTE_RELEASES_DIR} ] && [ \"\$(ls -A ${REMOTE_RELEASES_DIR} 2>/dev/null)\" ]; then
        backup_dir=\"${REMOTE_RELEASES_DIR}.backup.\$(date +%Y%m%d-%H%M%S)\"
        echo \"备份到: \$backup_dir\"
        cp -r ${REMOTE_RELEASES_DIR} \$backup_dir
        
        # 只保留最近3个备份
        cd \$(dirname ${REMOTE_RELEASES_DIR})
        ls -dt *.backup.* 2>/dev/null | tail -n +4 | xargs rm -rf 2>/dev/null || true
    else
        echo \"无现有安装包需要备份\"
    fi
"

# 上传安装包
echo -e "${YELLOW}📤 开始上传安装包...${NC}"
echo -e "${BLUE}上传进度:${NC}"

upload_start_time=$(date +%s)

for i, package in "${!PACKAGES[@]}"; do
    package_path="$RELEASE_DIR/$package"
    progress=$((i + 1))
    total=${#PACKAGES[@]}
    
    echo -e "${BLUE}[$progress/$total] 上传 $package...${NC}"
    
    # 使用rsync进行断点续传和进度显示
    if command -v rsync >/dev/null 2>&1; then
        rsync -avz --progress "$package_path" "${SERVER_USER}@${SERVER_IP}:${REMOTE_RELEASES_DIR}/"
    else
        # 备用方案：使用scp
        scp "$package_path" "${SERVER_USER}@${SERVER_IP}:${REMOTE_RELEASES_DIR}/"
    fi
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}  ✅ $package 上传成功${NC}"
    else
        echo -e "${RED}  ❌ $package 上传失败${NC}"
        exit 1
    fi
done

upload_end_time=$(date +%s)
upload_duration=$((upload_end_time - upload_start_time))
upload_speed=$((TOTAL_SIZE_MB / upload_duration))

echo -e "${GREEN}✅ 所有安装包上传完成${NC}"
echo -e "${BLUE}上传统计: ${TOTAL_SIZE_MB}MB 用时 ${upload_duration}秒，平均速度 ${upload_speed}MB/s${NC}"
echo ""

# 验证上传结果
echo -e "${YELLOW}🔍 验证上传结果...${NC}"
ssh ${SERVER_USER}@${SERVER_IP} "
    echo '远程安装包列表:'
    ls -lh ${REMOTE_RELEASES_DIR}/*.{zip,tar.gz} 2>/dev/null | while read line; do
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
echo -e "${BLUE}下载链接：${NC}"
for package in "${PACKAGES[@]}"; do
    echo -e "  • http://${SERVER_IP}/agent/release/${package}"
done
echo ""
echo -e "${BLUE}管理命令：${NC}"
echo -e "  查看远程文件: ${YELLOW}ssh ${SERVER_USER}@${SERVER_IP} 'ls -la ${REMOTE_RELEASES_DIR}'${NC}"
echo -e "  清理备份: ${YELLOW}ssh ${SERVER_USER}@${SERVER_IP} 'rm -rf ${REMOTE_RELEASES_DIR}.backup.*'${NC}"
echo ""
echo -e "${BLUE}一键安装命令：${NC}"
echo -e "  Linux:   ${GREEN}curl -fsSL http://${SERVER_IP}/scripts/install-linux.sh | sudo bash -s -- --server=http://${SERVER_IP}:8080${NC}"
echo -e "  macOS:   ${GREEN}curl -fsSL http://${SERVER_IP}/scripts/install-macos.sh | bash -s -- --server=http://${SERVER_IP}:8080${NC}"
echo -e "  Windows: ${GREEN}PowerShell 执行安装脚本${NC}"
echo ""