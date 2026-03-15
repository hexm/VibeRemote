#!/bin/bash

# LightScript 门户网站和安装包部署脚本
# 部署到阿里云服务器

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_IP="8.138.114.34"
SERVER_USER="root"
REMOTE_PORTAL_DIR="/var/www/html"
REMOTE_RELEASES_DIR="/var/www/html/agent/release"
REMOTE_SCRIPTS_DIR="/var/www/html/scripts"

echo "🚀 开始部署 LightScript 门户网站和安装包..."
echo "📡 目标服务器: $SERVER_USER@$SERVER_IP"
echo ""

# 检查必要文件
echo "🔍 检查必要文件..."
if [ ! -d "portal" ]; then
    echo "❌ 错误: portal 目录不存在"
    exit 1
fi

if [ ! -d "agent/release" ]; then
    echo "❌ 错误: agent/release 目录不存在"
    exit 1
fi

echo "✅ 必要文件检查完成"
echo ""

# 创建临时部署目录
TEMP_DIR=$(mktemp -d)
echo "📁 创建临时部署目录: $TEMP_DIR"

# 复制门户文件
echo "📋 准备门户文件..."
cp -r portal/* "$TEMP_DIR/"

# 复制安装包
echo "📦 准备安装包..."
mkdir -p "$TEMP_DIR/agent/release"
cp agent/release/*.zip "$TEMP_DIR/agent/release/" 2>/dev/null || true
cp agent/release/*.tar.gz "$TEMP_DIR/agent/release/" 2>/dev/null || true

# 创建安装脚本
echo "📝 创建安装脚本..."
mkdir -p "$TEMP_DIR/scripts"

# 通用安装脚本（自动检测操作系统）
cat > "$TEMP_DIR/scripts/install.sh" << 'EOF'
#!/bin/bash

# LightScript Agent 通用安装脚本
# 自动检测操作系统并执行相应的安装

set -e

SERVER_URL="http://8.138.114.34:8080"

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --server=*)
            SERVER_URL="${1#*=}"
            shift
            ;;
        *)
            echo "未知参数: $1"
            exit 1
            ;;
    esac
done

echo "🚀 LightScript Agent 智能安装程序"
echo "📡 服务器地址: $SERVER_URL"
echo ""

# 检测操作系统
detect_os() {
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        echo "linux"
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        echo "macos"
    elif [[ "$OSTYPE" == "cygwin" ]] || [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "win32" ]]; then
        echo "windows"
    else
        # 尝试其他检测方法
        case "$(uname -s)" in
            Linux*)     echo "linux";;
            Darwin*)    echo "macos";;
            CYGWIN*)    echo "windows";;
            MINGW*)     echo "windows";;
            *)          echo "unknown";;
        esac
    fi
}

OS=$(detect_os)
echo "🔍 检测到操作系统: $OS"

case $OS in
    "linux")
        echo "📥 下载并执行 Linux 安装脚本..."
        curl -fsSL http://8.138.114.34/scripts/install-linux.sh | bash -s -- --server="$SERVER_URL"
        ;;
    "macos")
        echo "📥 下载并执行 macOS 安装脚本..."
        curl -fsSL http://8.138.114.34/scripts/install-macos.sh | bash -s -- --server="$SERVER_URL"
        ;;
    "windows")
        echo "❌ Windows 系统请使用 PowerShell 执行以下命令："
        echo "Set-ExecutionPolicy Bypass -Scope Process -Force"
        echo "iex ((New-Object System.Net.WebClient).DownloadString('http://8.138.114.34/scripts/install-windows.ps1'))"
        exit 1
        ;;
    *)
        echo "❌ 不支持的操作系统: $OS"
        echo ""
        echo "请手动选择安装方式："
        echo "Linux:   curl -fsSL http://8.138.114.34/scripts/install-linux.sh | bash -s -- --server=$SERVER_URL"
        echo "macOS:   curl -fsSL http://8.138.114.34/scripts/install-macos.sh | bash -s -- --server=$SERVER_URL"
        echo "Windows: Set-ExecutionPolicy Bypass -Scope Process -Force; iex ((New-Object System.Net.WebClient).DownloadString('http://8.138.114.34/scripts/install-windows.ps1'))"
        exit 1
        ;;
esac
EOF

# Linux 安装脚本
cat > "$TEMP_DIR/scripts/install-linux.sh" << 'EOF'
#!/bin/bash

# LightScript Agent Linux 安装脚本
set -e

SERVER_URL="http://8.138.114.34:8080"
INSTALL_DIR="/opt/lightscript-agent"
SERVICE_NAME="lightscript-agent"

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --server=*)
            SERVER_URL="${1#*=}"
            shift
            ;;
        --install-dir=*)
            INSTALL_DIR="${1#*=}"
            shift
            ;;
        *)
            echo "未知参数: $1"
            exit 1
            ;;
    esac
done

echo "🚀 开始安装 LightScript Agent..."
echo "📡 服务器地址: $SERVER_URL"
echo "📁 安装目录: $INSTALL_DIR"
echo ""

# 检查权限
if [ "$EUID" -ne 0 ]; then
    echo "❌ 请使用 root 权限运行此脚本"
    echo "使用: sudo $0"
    exit 1
fi

# 检查系统
if ! command -v curl >/dev/null 2>&1 && ! command -v wget >/dev/null 2>&1; then
    echo "❌ 需要 curl 或 wget 来下载文件"
    echo "Ubuntu/Debian: apt update && apt install -y curl"
    echo "CentOS/RHEL: yum install -y curl"
    exit 1
fi

# 创建安装目录
echo "📁 创建安装目录..."
mkdir -p "$INSTALL_DIR"
cd "$INSTALL_DIR"

# 下载安装包
echo "📥 下载安装包..."
DOWNLOAD_URL="http://8.138.114.34/agent/release/lightscript-agent-0.4.0-linux-x64.tar.gz"

if command -v curl >/dev/null 2>&1; then
    curl -L -o lightscript-agent.tar.gz "$DOWNLOAD_URL"
else
    wget -O lightscript-agent.tar.gz "$DOWNLOAD_URL"
fi

# 解压安装包
echo "📂 解压安装包..."
tar -xzf lightscript-agent.tar.gz --strip-components=0
rm lightscript-agent.tar.gz

# 配置服务器地址
echo "⚙️  配置服务器地址..."
echo "server.url=$SERVER_URL" > agent.properties

# 设置执行权限
chmod +x *.sh

# 创建系统服务
echo "🔧 创建系统服务..."
cat > /etc/systemd/system/$SERVICE_NAME.service << EOL
[Unit]
Description=LightScript Agent
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=$INSTALL_DIR
ExecStart=$INSTALL_DIR/start-agent.sh
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOL

# 启动服务
echo "🚀 启动服务..."
systemctl daemon-reload
systemctl enable $SERVICE_NAME
systemctl start $SERVICE_NAME

echo ""
echo "✅ LightScript Agent 安装完成!"
echo "📊 查看状态: systemctl status $SERVICE_NAME"
echo "📋 查看日志: journalctl -u $SERVICE_NAME -f"
echo "🌐 管理后台: $SERVER_URL/admin"
EOF

# macOS 安装脚本
cat > "$TEMP_DIR/scripts/install-macos.sh" << 'EOF'
#!/bin/bash

# LightScript Agent macOS 安装脚本
set -e

SERVER_URL="http://8.138.114.34:8080"
INSTALL_DIR="/usr/local/lightscript-agent"
SERVICE_NAME="com.lightscript.agent"

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --server=*)
            SERVER_URL="${1#*=}"
            shift
            ;;
        --install-dir=*)
            INSTALL_DIR="${1#*=}"
            shift
            ;;
        *)
            echo "未知参数: $1"
            exit 1
            ;;
    esac
done

echo "🚀 开始安装 LightScript Agent..."
echo "📡 服务器地址: $SERVER_URL"
echo "📁 安装目录: $INSTALL_DIR"
echo ""

# 检查权限
if [ "$EUID" -ne 0 ]; then
    echo "❌ 请使用 sudo 权限运行此脚本"
    echo "使用: sudo $0"
    exit 1
fi

# 检测架构
ARCH="x64"
if [[ $(uname -m) == "arm64" ]]; then
    ARCH="arm64"
fi

echo "🔍 检测到架构: $ARCH"

# 创建安装目录
echo "📁 创建安装目录..."
mkdir -p "$INSTALL_DIR"
cd "$INSTALL_DIR"

# 下载安装包
echo "📥 下载安装包..."
DOWNLOAD_URL="http://8.138.114.34/agent/release/lightscript-agent-0.4.0-macos-$ARCH.tar.gz"

if command -v curl >/dev/null 2>&1; then
    curl -L -o lightscript-agent.tar.gz "$DOWNLOAD_URL"
else
    echo "❌ 需要 curl 来下载文件"
    exit 1
fi

# 解压安装包
echo "📂 解压安装包..."
tar -xzf lightscript-agent.tar.gz --strip-components=0
rm lightscript-agent.tar.gz

# 配置服务器地址
echo "⚙️  配置服务器地址..."
echo "server.url=$SERVER_URL" > agent.properties

# 设置执行权限
chmod +x *.sh

# 创建 LaunchDaemon
echo "🔧 创建系统服务..."
cat > /Library/LaunchDaemons/$SERVICE_NAME.plist << EOL
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>$SERVICE_NAME</string>
    <key>ProgramArguments</key>
    <array>
        <string>$INSTALL_DIR/start-agent.sh</string>
    </array>
    <key>WorkingDirectory</key>
    <string>$INSTALL_DIR</string>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>StandardOutPath</key>
    <string>$INSTALL_DIR/logs/agent.log</string>
    <key>StandardErrorPath</key>
    <string>$INSTALL_DIR/logs/agent.log</string>
</dict>
</plist>
EOL

# 创建日志目录
mkdir -p logs

# 启动服务
echo "🚀 启动服务..."
launchctl load /Library/LaunchDaemons/$SERVICE_NAME.plist
launchctl start $SERVICE_NAME

echo ""
echo "✅ LightScript Agent 安装完成!"
echo "📊 查看状态: launchctl list | grep lightscript"
echo "📋 查看日志: tail -f $INSTALL_DIR/logs/agent.log"
echo "🌐 管理后台: $SERVER_URL/admin"
EOF

# Windows PowerShell 安装脚本
cat > "$TEMP_DIR/scripts/install-windows.ps1" << 'EOF'
# LightScript Agent Windows 安装脚本

param(
    [string]$ServerUrl = "http://8.138.114.34:8080",
    [string]$InstallDir = "C:\Program Files\LightScript"
)

Write-Host "🚀 开始安装 LightScript Agent..." -ForegroundColor Green
Write-Host "📡 服务器地址: $ServerUrl" -ForegroundColor Cyan
Write-Host "📁 安装目录: $InstallDir" -ForegroundColor Cyan
Write-Host ""

# 检查管理员权限
if (-NOT ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole] "Administrator")) {
    Write-Host "❌ 需要管理员权限运行此脚本" -ForegroundColor Red
    Write-Host "请以管理员身份运行 PowerShell" -ForegroundColor Yellow
    exit 1
}

# 创建安装目录
Write-Host "📁 创建安装目录..." -ForegroundColor Yellow
New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
Set-Location $InstallDir

# 下载安装包
Write-Host "📥 下载安装包..." -ForegroundColor Yellow
$DownloadUrl = "http://8.138.114.34/agent/release/lightscript-agent-0.4.0-windows-x64.zip"
$ZipFile = "$InstallDir\lightscript-agent.zip"

try {
    Invoke-WebRequest -Uri $DownloadUrl -OutFile $ZipFile
} catch {
    Write-Host "❌ 下载失败: $_" -ForegroundColor Red
    exit 1
}

# 解压安装包
Write-Host "📂 解压安装包..." -ForegroundColor Yellow
Expand-Archive -Path $ZipFile -DestinationPath $InstallDir -Force
Remove-Item $ZipFile

# 配置服务器地址
Write-Host "⚙️  配置服务器地址..." -ForegroundColor Yellow
"server.url=$ServerUrl" | Out-File -FilePath "$InstallDir\agent.properties" -Encoding UTF8

# 安装 Windows 服务
Write-Host "🔧 安装 Windows 服务..." -ForegroundColor Yellow
$ServiceName = "LightScriptAgent"
$ServiceDisplayName = "LightScript Agent"
$ServiceDescription = "LightScript 分布式脚本执行代理"
$ServicePath = "$InstallDir\start-agent.bat"

# 创建服务
$params = @{
    Name = $ServiceName
    DisplayName = $ServiceDisplayName
    Description = $ServiceDescription
    BinaryPathName = "cmd.exe /c `"$ServicePath`""
    StartupType = "Automatic"
}

try {
    New-Service @params
    Write-Host "✅ 服务创建成功" -ForegroundColor Green
} catch {
    Write-Host "⚠️  服务可能已存在，尝试更新..." -ForegroundColor Yellow
}

# 启动服务
Write-Host "🚀 启动服务..." -ForegroundColor Yellow
Start-Service -Name $ServiceName

Write-Host ""
Write-Host "✅ LightScript Agent 安装完成!" -ForegroundColor Green
Write-Host "📊 查看状态: Get-Service -Name $ServiceName" -ForegroundColor Cyan
Write-Host "🌐 管理后台: $ServerUrl/admin" -ForegroundColor Cyan
EOF

# 设置脚本执行权限
chmod +x "$TEMP_DIR/scripts"/*.sh

echo "📤 开始上传文件到服务器..."

# 创建远程目录
echo "📁 创建远程目录..."
ssh "$SERVER_USER@$SERVER_IP" "mkdir -p $REMOTE_PORTAL_DIR $REMOTE_RELEASES_DIR $REMOTE_SCRIPTS_DIR"

# 上传门户文件
echo "🌐 上传门户网站..."
scp -r "$TEMP_DIR"/* "$SERVER_USER@$SERVER_IP:$REMOTE_PORTAL_DIR/"

echo ""
echo "🎉 部署完成!"
echo ""
echo "📋 部署信息:"
echo "  🌐 门户网站: http://$SERVER_IP/"
echo "  📦 安装包目录: http://$SERVER_IP/agent/release/"
echo "  📝 安装脚本: http://$SERVER_IP/scripts/"
echo ""
echo "🔗 可用链接:"
echo "  • 首页: http://$SERVER_IP/"
echo "  • 客户端安装: http://$SERVER_IP/client-install.html"
echo "  • 服务端部署: http://$SERVER_IP/server-deploy.html"
echo "  • 文档: http://$SERVER_IP/docs.html"
echo "  • 管理后台: http://$SERVER_IP:8080/admin"
echo ""
echo "📥 一键安装命令:"
echo "  通用:    curl -fsSL http://$SERVER_IP/scripts/install.sh | bash -s -- --server=http://$SERVER_IP:8080"
echo "  Linux:   curl -fsSL http://$SERVER_IP/scripts/install-linux.sh | sudo bash -s -- --server=http://$SERVER_IP:8080"
echo "  macOS:   curl -fsSL http://$SERVER_IP/scripts/install-macos.sh | sudo bash -s -- --server=http://$SERVER_IP:8080"
echo "  Windows: Set-ExecutionPolicy Bypass -Scope Process -Force; iex ((New-Object System.Net.WebClient).DownloadString('http://$SERVER_IP/scripts/install-windows.ps1'))"

# 清理临时目录
rm -rf "$TEMP_DIR"

echo ""
echo "✅ 部署脚本执行完成!"