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

echo "✅ 必要文件检查完成"
echo ""

# 创建临时部署目录
TEMP_DIR=$(mktemp -d)
echo "📁 创建临时部署目录: $TEMP_DIR"

# 复制门户文件
echo "📋 准备门户文件..."
cp -r portal/* "$TEMP_DIR/"

# 注意：Agent安装包不在此脚本中上传
# 如需上传Agent安装包，请使用: ./agent/scripts/deploy-agent-packages.sh

# 创建安装脚本
echo "📝 创建安装脚本..."
mkdir -p "$TEMP_DIR/scripts"

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
INSTALL_DIR="$HOME/.lightscript-agent"
SERVICE_NAME="com.lightscript.agent"
USE_SYSTEM_INSTALL=false

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
        --system)
            USE_SYSTEM_INSTALL=true
            INSTALL_DIR="/usr/local/lightscript-agent"
            shift
            ;;
        *)
            echo "未知参数: $1"
            echo "用法: $0 [--server=URL] [--install-dir=DIR] [--system]"
            echo "  --system: 安装到系统目录（需要sudo权限）"
            exit 1
            ;;
    esac
done

echo "🚀 开始安装 LightScript Agent..."
echo "📡 服务器地址: $SERVER_URL"
echo "📁 安装目录: $INSTALL_DIR"
echo ""

# 检查权限（仅在系统安装时需要）
if [ "$USE_SYSTEM_INSTALL" = true ] && [ "$EUID" -ne 0 ]; then
    echo "❌ 系统安装需要 sudo 权限"
    echo "请使用: curl -fsSL http://8.138.114.34/scripts/install-macos.sh | sudo bash -s -- --system --server=$SERVER_URL"
    echo "或者使用用户安装（推荐）: curl -fsSL http://8.138.114.34/scripts/install-macos.sh | bash -s -- --server=$SERVER_URL"
    exit 1
fi

# 检测架构
ARCH="x64"
if [[ $(uname -m) == "arm64" ]]; then
    ARCH="arm64"
fi

echo "🔍 检测到架构: $ARCH"
if [ "$USE_SYSTEM_INSTALL" = true ]; then
    echo "📦 安装模式: 系统安装（所有用户可用）"
else
    echo "📦 安装模式: 用户安装（仅当前用户）"
fi

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

# 创建服务配置
echo "🔧 创建系统服务..."
if [ "$USE_SYSTEM_INSTALL" = true ]; then
    # 系统级服务
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
        <string>--launchd</string>
    </array>
    <key>WorkingDirectory</key>
    <string>$INSTALL_DIR</string>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
</dict>
</plist>
EOL
    
    # 启动系统服务
    launchctl load /Library/LaunchDaemons/$SERVICE_NAME.plist
    launchctl start $SERVICE_NAME
    
else
    # 用户级服务
    USER_AGENTS_DIR="$HOME/Library/LaunchAgents"
    mkdir -p "$USER_AGENTS_DIR"
    
    cat > "$USER_AGENTS_DIR/$SERVICE_NAME.plist" << EOL
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>$SERVICE_NAME</string>
    <key>ProgramArguments</key>
    <array>
        <string>$INSTALL_DIR/start-agent.sh</string>
        <string>--launchd</string>
    </array>
    <key>WorkingDirectory</key>
    <string>$INSTALL_DIR</string>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>EnvironmentVariables</key>
    <dict>
        <key>PATH</key>
        <string>/usr/local/bin:/usr/bin:/bin</string>
    </dict>
</dict>
</plist>
EOL
    
    # 启动用户服务
    launchctl load "$USER_AGENTS_DIR/$SERVICE_NAME.plist"
    launchctl start $SERVICE_NAME
fi

# 创建日志目录
mkdir -p logs

echo ""
echo "✅ LightScript Agent 安装完成!"
if [ "$USE_SYSTEM_INSTALL" = true ]; then
    echo "📊 查看状态: sudo launchctl list | grep lightscript"
    echo "📋 查看日志: tail -f $INSTALL_DIR/logs/agent.log"
    echo "🛑 停止服务: sudo launchctl stop $SERVICE_NAME"
else
    echo "📊 查看状态: launchctl list | grep lightscript"
    echo "📋 查看日志: tail -f $INSTALL_DIR/logs/agent.log"
    echo "🛑 停止服务: launchctl stop $SERVICE_NAME"
fi
echo "🌐 管理后台: $SERVER_URL/admin"
EOF

# Windows PowerShell 安装脚本
cat > "$TEMP_DIR/scripts/install-windows.ps1" << 'EOF'
# LightScript Agent Windows 安装脚本

param(
    [string]$ServerUrl = "http://8.138.114.34:8080",
    [string]$InstallDir = "C:\Program Files\LightScript",
    [switch]$Manual = $false
)

Write-Host "🚀 开始安装 LightScript Agent..." -ForegroundColor Green
Write-Host "📡 服务器地址: $ServerUrl" -ForegroundColor Cyan
Write-Host "📁 安装目录: $InstallDir" -ForegroundColor Cyan

if ($Manual) {
    Write-Host "📦 安装模式: 手动模式（不安装Windows服务）" -ForegroundColor Yellow
} else {
    Write-Host "📦 安装模式: 服务模式（自动安装Windows服务）" -ForegroundColor Cyan
}
Write-Host ""

# 检查管理员权限（仅在服务模式需要）
if (-NOT $Manual -AND -NOT ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole] "Administrator")) {
    Write-Host "❌ 服务模式需要管理员权限运行此脚本" -ForegroundColor Red
    Write-Host "请以管理员身份运行 PowerShell，或使用手动模式:" -ForegroundColor Yellow
    Write-Host "  手动模式: iex ((New-Object System.Net.WebClient).DownloadString('http://8.138.114.34/scripts/install-windows.ps1')) -Manual" -ForegroundColor Cyan
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
    Invoke-WebRequest -Uri $DownloadUrl -OutFile $ZipFile -UseBasicParsing
    Write-Host "✅ 下载完成" -ForegroundColor Green
} catch {
    Write-Host "❌ 下载失败: $_" -ForegroundColor Red
    exit 1
}

# 解压安装包
Write-Host "📂 解压安装包..." -ForegroundColor Yellow
try {
    Expand-Archive -Path $ZipFile -DestinationPath $InstallDir -Force
    Remove-Item $ZipFile
    Write-Host "✅ 解压完成" -ForegroundColor Green
} catch {
    Write-Host "❌ 解压失败: $_" -ForegroundColor Red
    exit 1
}

# 配置服务器地址
Write-Host "⚙️  配置服务器地址..." -ForegroundColor Yellow
"server.url=$ServerUrl" | Out-File -FilePath "$InstallDir\agent.properties" -Encoding UTF8

if ($Manual) {
    # 手动模式
    Write-Host "🔧 配置手动启动模式..." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "✅ LightScript Agent 安装完成!" -ForegroundColor Green
    Write-Host "📊 手动启动: .\start-agent.bat" -ForegroundColor Cyan
    Write-Host "🛑 手动停止: .\stop-agent.bat" -ForegroundColor Cyan
    Write-Host "📋 查看日志: type logs\agent.log" -ForegroundColor Cyan
    Write-Host "🌐 管理后台: $ServerUrl/admin" -ForegroundColor Cyan
} else {
    # 服务模式
    Write-Host "🔧 安装 Windows 服务..." -ForegroundColor Yellow
    $ServiceName = "LightScriptAgent"
    $ServiceDisplayName = "LightScript Agent"
    $ServiceDescription = "LightScript 分布式脚本执行代理"
    
    # 停止并删除现有服务
    try {
        $existingService = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
        if ($existingService) {
            Write-Host "发现现有服务，正在更新..." -ForegroundColor Yellow
            Stop-Service -Name $ServiceName -Force -ErrorAction SilentlyContinue
            & sc.exe delete $ServiceName | Out-Null
            Start-Sleep -Seconds 2
        }
    } catch {
        # 忽略错误
    }
    
    # 创建新服务
    try {
        $servicePath = "cmd.exe /c `"$InstallDir\start-agent.bat`" --service"
        & sc.exe create $ServiceName binPath= $servicePath DisplayName= $ServiceDisplayName start= auto depend= Tcpip | Out-Null
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✅ 服务创建成功" -ForegroundColor Green
            
            # 设置服务描述
            & sc.exe description $ServiceName $ServiceDescription | Out-Null
            
            # 设置服务恢复选项（失败时自动重启）
            & sc.exe failure $ServiceName reset= 86400 actions= restart/30000/restart/60000/restart/120000 | Out-Null
            
            # 启动服务
            Write-Host "🚀 启动服务..." -ForegroundColor Yellow
            Start-Service -Name $ServiceName
            
            # 检查服务状态
            $service = Get-Service -Name $ServiceName
            if ($service.Status -eq "Running") {
                Write-Host "✅ 服务启动成功" -ForegroundColor Green
            } else {
                Write-Host "⚠️  服务状态: $($service.Status)" -ForegroundColor Yellow
            }
        } else {
            Write-Host "❌ 服务创建失败" -ForegroundColor Red
            exit 1
        }
    } catch {
        Write-Host "❌ 服务安装失败: $_" -ForegroundColor Red
        exit 1
    }
    
    Write-Host ""
    Write-Host "✅ LightScript Agent 安装完成!" -ForegroundColor Green
    Write-Host "📊 查看状态: Get-Service -Name $ServiceName" -ForegroundColor Cyan
    Write-Host "🛑 停止服务: Stop-Service -Name $ServiceName" -ForegroundColor Cyan
    Write-Host "🚀 启动服务: Start-Service -Name $ServiceName" -ForegroundColor Cyan
    Write-Host "📋 查看日志: type `"$InstallDir\logs\agent.log`"" -ForegroundColor Cyan
    Write-Host "🗑️  卸载服务: .\uninstall-service.bat" -ForegroundColor Cyan
    Write-Host "🌐 管理后台: $ServerUrl/admin" -ForegroundColor Cyan
}
EOF

# Windows 批处理安装脚本（备用方案）
cat > "$TEMP_DIR/scripts/install-windows.bat" << 'EOF'
@echo off
setlocal enabledelayedexpansion

REM LightScript Agent Windows 安装脚本 (批处理版本)

set SERVER_URL=http://8.138.114.34:8080
set INSTALL_DIR=C:\Program Files\LightScript
set SERVICE_NAME=LightScriptAgent

REM 解析命令行参数
:parse_args
if "%1"=="" goto :start_install
if "%1"=="--server" (
    set SERVER_URL=%2
    shift
    shift
    goto :parse_args
)
if "%1"=="--install-dir" (
    set INSTALL_DIR=%2
    shift
    shift
    goto :parse_args
)
if "%1"=="--manual" (
    set MANUAL_MODE=1
    shift
    goto :parse_args
)
shift
goto :parse_args

:start_install
echo 🚀 开始安装 LightScript Agent...
echo 📡 服务器地址: %SERVER_URL%
echo 📁 安装目录: %INSTALL_DIR%

if defined MANUAL_MODE (
    echo 📦 安装模式: 手动模式（不安装Windows服务）
) else (
    echo 📦 安装模式: 服务模式（自动安装Windows服务）
)
echo.

REM 检查管理员权限（仅在服务模式需要）
if not defined MANUAL_MODE (
    net session >nul 2>&1
    if !errorlevel! neq 0 (
        echo ❌ 服务模式需要管理员权限
        echo 请以管理员身份运行此脚本，或使用手动模式:
        echo   手动模式: %0 --manual
        pause
        exit /b 1
    )
)

REM 创建安装目录
echo 📁 创建安装目录...
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"
cd /d "%INSTALL_DIR%"

REM 下载安装包
echo 📥 下载安装包...
set DOWNLOAD_URL=http://8.138.114.34/agent/release/lightscript-agent-0.4.0-windows-x64.zip
set ZIP_FILE=%INSTALL_DIR%\lightscript-agent.zip

REM 使用PowerShell下载文件
powershell -Command "try { Invoke-WebRequest -Uri '%DOWNLOAD_URL%' -OutFile '%ZIP_FILE%' -UseBasicParsing; Write-Host '✅ 下载完成' } catch { Write-Host '❌ 下载失败:' $_.Exception.Message; exit 1 }"
if !errorlevel! neq 0 (
    echo 下载失败，请检查网络连接
    pause
    exit /b 1
)

REM 解压安装包
echo 📂 解压安装包...
powershell -Command "try { Expand-Archive -Path '%ZIP_FILE%' -DestinationPath '%INSTALL_DIR%' -Force; Remove-Item '%ZIP_FILE%'; Write-Host '✅ 解压完成' } catch { Write-Host '❌ 解压失败:' $_.Exception.Message; exit 1 }"
if !errorlevel! neq 0 (
    echo 解压失败
    pause
    exit /b 1
)

REM 配置服务器地址
echo ⚙️  配置服务器地址...
echo server.url=%SERVER_URL% > "%INSTALL_DIR%\agent.properties"

if defined MANUAL_MODE (
    REM 手动模式
    echo 🔧 配置手动启动模式...
    echo.
    echo ✅ LightScript Agent 安装完成!
    echo 📊 手动启动: start-agent.bat
    echo 🛑 手动停止: stop-agent.bat
    echo 📋 查看日志: type logs\agent.log
    echo 🌐 管理后台: %SERVER_URL%/admin
) else (
    REM 服务模式
    echo 🔧 安装 Windows 服务...
    
    REM 停止并删除现有服务
    sc query "%SERVICE_NAME%" >nul 2>&1
    if !errorlevel! equ 0 (
        echo 发现现有服务，正在更新...
        net stop "%SERVICE_NAME%" >nul 2>&1
        sc delete "%SERVICE_NAME%" >nul 2>&1
        timeout /t 2 /nobreak >nul
    )
    
    REM 创建新服务
    sc create "%SERVICE_NAME%" binPath= "cmd.exe /c \"%INSTALL_DIR%\start-agent.bat\" --service" DisplayName= "LightScript Agent" start= auto depend= Tcpip >nul 2>&1
    if !errorlevel! equ 0 (
        echo ✅ 服务创建成功
        
        REM 设置服务描述和恢复选项
        sc description "%SERVICE_NAME%" "LightScript 分布式脚本执行代理" >nul 2>&1
        sc failure "%SERVICE_NAME%" reset= 86400 actions= restart/30000/restart/60000/restart/120000 >nul 2>&1
        
        REM 启动服务
        echo 🚀 启动服务...
        net start "%SERVICE_NAME%" >nul 2>&1
        if !errorlevel! equ 0 (
            echo ✅ 服务启动成功
        ) else (
            echo ⚠️  服务启动失败，请检查日志
        )
    ) else (
        echo ❌ 服务创建失败
        pause
        exit /b 1
    )
    
    echo.
    echo ✅ LightScript Agent 安装完成!
    echo 📊 查看状态: sc query "%SERVICE_NAME%"
    echo 🛑 停止服务: net stop "%SERVICE_NAME%"
    echo 🚀 启动服务: net start "%SERVICE_NAME%"
    echo 📋 查看日志: type "%INSTALL_DIR%\logs\agent.log"
    echo 🗑️  卸载服务: uninstall-service.bat
    echo 🌐 管理后台: %SERVER_URL%/admin
)

pause
EOF

# 设置脚本执行权限
chmod +x "$TEMP_DIR/scripts"/*.sh

echo "📤 开始上传文件到服务器..."

# 创建远程目录
echo "📁 创建远程目录..."
ssh "$SERVER_USER@$SERVER_IP" "mkdir -p $REMOTE_PORTAL_DIR $REMOTE_SCRIPTS_DIR"

# 上传门户文件（不包含Agent安装包）
echo "🌐 上传门户网站..."
scp -r "$TEMP_DIR"/* "$SERVER_USER@$SERVER_IP:$REMOTE_PORTAL_DIR/"

echo ""
echo "🎉 门户网站部署完成!"
echo ""
echo "📋 部署信息:"
echo "  🌐 门户网站: http://$SERVER_IP/"
echo "  � 安装脚本: http://$SERVER_IP/scripts/"
echo ""
echo "📦 Agent安装包部署:"
echo "  如需上传Agent安装包，请运行: ./agent/scripts/deploy-agent-packages.sh"
echo "  当前安装包目录: http://$SERVER_IP/agent/release/"
echo ""
echo "🔗 可用链接:"
echo "  • 首页: http://$SERVER_IP/"
echo "  • 客户端安装: http://$SERVER_IP/client-install.html"
echo "  • 服务端部署: http://$SERVER_IP/server-deploy.html"
echo "  • 文档: http://$SERVER_IP/docs.html"
echo "  • 管理后台: http://$SERVER_IP:8080/admin"
echo ""
echo "📥 一键安装命令:"
echo "  Linux:   curl -fsSL http://$SERVER_IP/scripts/install-linux.sh | sudo bash -s -- --server=http://$SERVER_IP:8080"
echo "  macOS:   curl -fsSL http://$SERVER_IP/scripts/install-macos.sh | bash -s -- --server=http://$SERVER_IP:8080 (用户安装)"
echo "           curl -fsSL http://$SERVER_IP/scripts/install-macos.sh | sudo bash -s -- --system --server=http://$SERVER_IP:8080 (系统安装)"
echo "  Windows: PowerShell (推荐):"
echo "           Set-ExecutionPolicy Bypass -Scope Process -Force; iex ((New-Object System.Net.WebClient).DownloadString('http://$SERVER_IP/scripts/install-windows.ps1'))"
echo "           手动模式: iex ((New-Object System.Net.WebClient).DownloadString('http://$SERVER_IP/scripts/install-windows.ps1')) -Manual"
echo "           批处理备用: 下载并运行 http://$SERVER_IP/scripts/install-windows.bat"

# 清理临时目录
rm -rf "$TEMP_DIR"

echo ""
echo "✅ 部署脚本执行完成!"