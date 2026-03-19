#!/bin/bash

# LightScript Agent 发布包构建脚本 (简化版)
# 构建包含完整 JRE 的跨平台 Agent 安装包

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RELEASE_DIR="$SCRIPT_DIR/release"
JRE_CACHE_DIR="$RELEASE_DIR/jre8"

# 从 pom.xml 获取版本号
VERSION=$(grep -A 1 "<parent>" "$SCRIPT_DIR/pom.xml" | grep "<version>" | sed 's/.*<version>\(.*\)<\/version>.*/\1/' | head -1)
if [ -z "$VERSION" ]; then
    VERSION="0.4.0"  # 默认版本
fi

echo "🚀 开始构建 LightScript Agent 发布包..."
echo "📁 项目根目录: $PROJECT_ROOT"
echo "📦 发布目录: $RELEASE_DIR"
echo "🏷️  版本: $VERSION"
echo ""

# 检查和准备JRE文件
prepare_jre_files() {
    echo "🔍 检查JRE缓存..."
    
    # JRE文件列表
    local jre_files=(
        "windows-x64:bellsoft-jre8u482+10-windows-amd64.zip"
        "linux-x64:bellsoft-jre8u482+10-linux-amd64.tar.gz"
        "macos-x64:bellsoft-jre8u482+10-macos-amd64.tar.gz"
        "macos-arm64:bellsoft-jre8u482+10-macos-aarch64.tar.gz"
    )
    
    local missing_count=0
    local total_count=${#jre_files[@]}
    
    for entry in "${jre_files[@]}"; do
        local platform="${entry%%:*}"
        local filename="${entry##*:}"
        local jre_file="$JRE_CACHE_DIR/$filename"
        
        if [ -f "$jre_file" ]; then
            local file_size=$(stat -f%z "$jre_file" 2>/dev/null || stat -c%s "$jre_file" 2>/dev/null || echo "0")
            if [ "$file_size" -gt 1000000 ]; then
                echo "  ✅ $platform: $filename ($(($file_size / 1024 / 1024))MB)"
            else
                echo "  ⚠️  $platform: $filename (文件损坏)"
                missing_count=$((missing_count + 1))
            fi
        else
            echo "  ❌ $platform: $filename (缺失)"
            missing_count=$((missing_count + 1))
        fi
    done
    
    if [ $missing_count -gt 0 ]; then
        echo ""
        echo "❌ 缺少 $missing_count/$total_count 个JRE文件，无法继续构建"
        echo ""
        echo "📋 请确保以下JRE文件存在于 $JRE_CACHE_DIR 目录："
        for entry in "${jre_files[@]}"; do
            local platform="${entry%%:*}"
            local filename="${entry##*:}"
            echo "  $platform: $filename"
        done
        exit 1
    else
        echo "✅ 所有JRE文件已准备就绪"
    fi
    
    echo ""
}

prepare_jre_files

# 清理发布目录（保留JRE文件）
echo "🧹 清理旧的安装包..."
rm -f "$RELEASE_DIR"/viberemote-agent-*.tar.gz
rm -f "$RELEASE_DIR"/viberemote-agent-*.zip
rm -f "$RELEASE_DIR"/lightscript-agent-*.tar.gz
rm -f "$RELEASE_DIR"/lightscript-agent-*.zip
mkdir -p "$RELEASE_DIR"
mkdir -p "$JRE_CACHE_DIR"

# 检查必要文件
echo "🔍 检查必要文件..."
AGENT_JAR="$SCRIPT_DIR/target/agent-0.4.0-jar-with-dependencies.jar"
UPGRADER_JAR="$PROJECT_ROOT/upgrader/upgrader.jar"

if [ ! -f "$AGENT_JAR" ]; then
    echo "❌ Agent JAR 文件不存在: $AGENT_JAR"
    echo "请先运行: mvn clean package"
    exit 1
fi

if [ ! -f "$UPGRADER_JAR" ]; then
    echo "❌ Upgrader JAR 文件不存在: $UPGRADER_JAR"
    exit 1
fi

echo "✅ 必要文件检查完成"
echo ""

# 使用完整JRE函数
use_full_jre() {
    local target_dir=$1
    local jre_filename=$2
    local os=$3
    
    echo "  📥 使用完整 JRE: $jre_filename"
    
    local jre_dir="$target_dir/jre"
    local local_jre_file="$JRE_CACHE_DIR/$jre_filename"
    
    # 检查JRE文件
    if [ ! -f "$local_jre_file" ]; then
        echo "    ❌ JRE文件不存在: $jre_filename"
        return 1
    fi
    
    local file_size=$(stat -f%z "$local_jre_file" 2>/dev/null || stat -c%s "$local_jre_file" 2>/dev/null || echo "0")
    echo "    📂 解压JRE (文件大小: $(($file_size / 1024 / 1024))MB)..."
    
    # 创建临时解压目录
    local temp_dir=$(mktemp -d)
    local extract_dir="$temp_dir/extracted"
    mkdir -p "$extract_dir"
    
    # 根据文件类型解压JRE
    if [[ "$jre_filename" == *.zip ]]; then
        if command -v unzip >/dev/null 2>&1; then
            if unzip -q "$local_jre_file" -d "$extract_dir" 2>/dev/null; then
                echo "    ✅ JRE解压成功"
            else
                echo "    ❌ JRE解压失败"
                rm -rf "$temp_dir"
                return 1
            fi
        else
            echo "    ❌ 未找到unzip命令"
            rm -rf "$temp_dir"
            return 1
        fi
    elif [[ "$jre_filename" == *.tar.gz ]]; then
        if tar -xzf "$local_jre_file" -C "$extract_dir" 2>/dev/null; then
            echo "    ✅ JRE解压成功"
        else
            echo "    ❌ JRE解压失败"
            rm -rf "$temp_dir"
            return 1
        fi
    else
        echo "    ❌ 不支持的JRE文件格式: $jre_filename"
        rm -rf "$temp_dir"
        return 1
    fi
    
    # 查找JRE目录
    local jre_source=$(find "$extract_dir" -name "bin" -type d | head -1 | xargs dirname 2>/dev/null)
    
    if [ -z "$jre_source" ] || [ ! -d "$jre_source" ]; then
        echo "    ❌ 未找到有效的JRE目录"
        rm -rf "$temp_dir"
        return 1
    fi
    
    # 验证关键文件
    if [ "$os" = "windows" ]; then
        if [ ! -f "$jre_source/bin/java.exe" ]; then
            echo "    ❌ 未找到java.exe"
            rm -rf "$temp_dir"
            return 1
        fi
    else
        if [ ! -f "$jre_source/bin/java" ]; then
            echo "    ❌ 未找到java"
            rm -rf "$temp_dir"
            return 1
        fi
    fi
    
    # 直接复制完整JRE
    echo "    📋 复制完整JRE..."
    mkdir -p "$jre_dir"
    cp -r "$jre_source"/* "$jre_dir/" 2>/dev/null || {
        echo "    ❌ JRE复制失败"
        rm -rf "$temp_dir"
        return 1
    }
    
    # 设置执行权限
    if [ "$os" != "windows" ]; then
        chmod +x "$jre_dir/bin/java" 2>/dev/null || true
        find "$jre_dir/bin" -type f -exec chmod +x {} \; 2>/dev/null || true
    fi
    
    # 清理临时目录
    rm -rf "$temp_dir"
    
    # 验证创建的JRE
    if [ -f "$jre_dir/bin/java" ] || [ -f "$jre_dir/bin/java.exe" ]; then
        local final_size=$(du -sm "$jre_dir" 2>/dev/null | cut -f1 || echo "0")
        echo "    ✅ 完整JRE复制成功 (体积: ${final_size}MB)"
        return 0
    else
        echo "    ❌ 完整JRE复制失败"
        return 1
    fi
}

# 创建启动脚本函数
create_scripts() {
    local target_dir=$1
    local os=$2
    
    if [ "$os" = "windows" ]; then
        # Windows 启动脚本 - 后台启动，所有输出重定向到日志文件
        cat > "$target_dir/start-agent.bat" << 'EOF'
@echo off
chcp 65001 >nul
cd /d "%~dp0"

if not exist "logs" mkdir "logs"

if not exist "agent.jar" (
    echo [错误] 未找到 agent.jar
    pause
    exit /b 1
)

REM 优先使用内置JRE，其次系统 Java
if exist "jre\bin\java.exe" (
    set "JAVA_CMD=%~dp0jre\bin\java.exe"
) else if defined JAVA_HOME (
    set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA_CMD=java"
)

REM 后台启动，stdout 和 stderr 全部写入日志文件
echo 启动 VibeRemote Agent...
echo 日志文件: %~dp0logs\agent.log
start /b "" "%JAVA_CMD%" -Xmx512m -Xms128m -Dfile.encoding=UTF-8 -jar "%~dp0agent.jar" >>"%~dp0logs\agent.log" 2>&1
echo Agent 已在后台启动，窗口将自动关闭
timeout /t 2 /nobreak >nul
EOF

        # Windows 停止脚本
        cat > "$target_dir/stop-agent.bat" << 'EOF'
@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set ID_FILE=%USERPROFILE%\.viberemote\.agent_id
set PROPS_FILE=%SCRIPT_DIR%agent.properties

echo 停止 VibeRemote Agent...

REM 读取 server.url 和凭证，主动通知服务器离线
set SERVER_URL=
set AGENT_ID=
set AGENT_TOKEN=

if exist "%PROPS_FILE%" (
    for /f "tokens=1,* delims==" %%a in ('findstr /b "server.url" "%PROPS_FILE%"') do set SERVER_URL=%%b
)
if exist "%ID_FILE%" (
    for /f "tokens=1,* delims==" %%a in ('findstr /b "agentId" "%ID_FILE%"') do set AGENT_ID=%%b
    for /f "tokens=1,* delims==" %%a in ('findstr /b "agentToken" "%ID_FILE%"') do set AGENT_TOKEN=%%b
)

if defined SERVER_URL if defined AGENT_ID if defined AGENT_TOKEN (
    echo 通知服务器离线...
    curl -s -X POST "%SERVER_URL%/api/agent/offline" -d "agentId=!AGENT_ID!&agentToken=!AGENT_TOKEN!" >nul 2>&1
)

REM 通过 wmic 精确查找并停止 agent.jar 进程
for /f "skip=1 tokens=1" %%i in ('wmic process where "name='java.exe' and commandline like '%%agent.jar%%'" get processid 2^>nul') do (
    if "%%i" neq "" (
        echo 停止进程 (PID: %%i)...
        taskkill /PID %%i /F >nul 2>&1
    )
)

echo Agent 已停止
pause
EOF

        # Windows 状态检查脚本
        cat > "$target_dir/check-status.bat" << 'EOF'
@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo   VibeRemote Agent 状态检查
echo ========================================
echo.

REM 检查进程是否运行
set RUNNING=false
for /f "skip=1 tokens=1,2" %%i in ('wmic process where "name='java.exe' and commandline like '%%agent.jar%%'" get processid,commandline 2^>nul') do (
    if "%%i" neq "" (
        set RUNNING=true
        echo [状态] 运行中 (PID: %%i)
    )
)

if "!RUNNING!"=="false" (
    echo [状态] 未运行
)

echo.

REM 检查开机启动
reg query "HKCU\Software\Microsoft\Windows\CurrentVersion\Run" /v "VibeRemoteAgent" >nul 2>&1
if !errorlevel! equ 0 (
    echo [开机启动] 已启用
) else (
    echo [开机启动] 未启用
)

echo.

REM 显示最近日志
set LOG_FILE=%~dp0logs\agent.log
if exist "%LOG_FILE%" (
    echo [最近日志] (最后10行)
    echo ----------------------------------------
    powershell -Command "Get-Content '%LOG_FILE%' -Tail 10" 2>nul
) else (
    echo [日志] 暂无日志文件
)

echo.
pause
EOF

        # Windows 开机启动注册脚本（注册表方式，无需管理员权限）
        cat > "$target_dir/install-autostart.bat" << 'EOF'
@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set REG_KEY=HKCU\Software\Microsoft\Windows\CurrentVersion\Run
set REG_NAME=VibeRemoteAgent

echo 设置 VibeRemote Agent 开机自启...

REM 写入注册表（用户级，无需管理员权限）
reg add "%REG_KEY%" /v "%REG_NAME%" /t REG_SZ /d "\"%SCRIPT_DIR%start-agent.bat\"" /f >nul 2>&1
if !errorlevel! equ 0 (
    echo [成功] 已设置开机自启
    echo 路径: %SCRIPT_DIR%start-agent.bat
) else (
    echo [失败] 设置开机自启失败
)

echo.
pause
EOF

        # Windows 取消开机启动脚本
        cat > "$target_dir/uninstall-autostart.bat" << 'EOF'
@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

set REG_KEY=HKCU\Software\Microsoft\Windows\CurrentVersion\Run
set REG_NAME=VibeRemoteAgent

echo 取消 VibeRemote Agent 开机自启...

reg delete "%REG_KEY%" /v "%REG_NAME%" /f >nul 2>&1
if !errorlevel! equ 0 (
    echo [成功] 已取消开机自启
) else (
    echo [提示] 未找到开机自启项，可能未设置
)

echo.
pause
EOF

        # Windows 卸载脚本
        cat > "$target_dir/uninstall.bat" << 'EOF'
@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set REG_KEY=HKCU\Software\Microsoft\Windows\CurrentVersion\Run
set REG_NAME=VibeRemoteAgent

echo 卸载 VibeRemote Agent...
echo.

REM 停止进程
echo 停止 Agent 进程...
set ID_FILE=%USERPROFILE%\.viberemote\.agent_id
set PROPS_FILE=%SCRIPT_DIR%agent.properties
set SERVER_URL=
set AGENT_ID=
set AGENT_TOKEN=
if exist "%PROPS_FILE%" (
    for /f "tokens=1,* delims==" %%a in ('findstr /b "server.url" "%PROPS_FILE%"') do set SERVER_URL=%%b
)
if exist "%ID_FILE%" (
    for /f "tokens=1,* delims==" %%a in ('findstr /b "agentId" "%ID_FILE%"') do set AGENT_ID=%%b
    for /f "tokens=1,* delims==" %%a in ('findstr /b "agentToken" "%ID_FILE%"') do set AGENT_TOKEN=%%b
)
if defined SERVER_URL if defined AGENT_ID if defined AGENT_TOKEN (
    curl -s -X POST "!SERVER_URL!/api/agent/offline" -d "agentId=!AGENT_ID!&agentToken=!AGENT_TOKEN!" >nul 2>&1
)
for /f "skip=1 tokens=1" %%i in ('wmic process where "name='java.exe' and commandline like '%%agent.jar%%'" get processid 2^>nul') do (
    if "%%i" neq "" (
        taskkill /PID %%i /F >nul 2>&1
    )
)

REM 取消开机自启
reg delete "%REG_KEY%" /v "%REG_NAME%" /f >nul 2>&1
echo 已取消开机自启

REM 询问是否删除安装目录
echo.
set /p DELETE_DIR="是否删除安装目录 %SCRIPT_DIR%? (y/N): "
if /i "!DELETE_DIR!"=="y" (
    cd /d "%TEMP%"
    rmdir /s /q "%SCRIPT_DIR%" 2>nul
    echo VibeRemote Agent 已完全卸载
) else (
    echo VibeRemote Agent 已卸载，文件保留
)

pause
EOF

        # 转换Windows批处理文件的换行符为CRLF
        echo "    🔄 转换批处理文件换行符为CRLF..."
        
        # 方法1: 使用unix2dos（如果可用）
        if command -v unix2dos >/dev/null 2>&1; then
            echo "    使用unix2dos转换换行符"
            unix2dos "$target_dir"/*.bat 2>/dev/null && echo "    ✅ unix2dos转换成功" || echo "    ⚠️  unix2dos转换失败"
        # 方法2: 使用sed添加回车符
        elif command -v sed >/dev/null 2>&1; then
            echo "    使用sed转换换行符"
            for bat_file in "$target_dir"/*.bat; do
                if [ -f "$bat_file" ]; then
                    # 先检查是否已经有CRLF，避免重复转换
                    if ! grep -q $'\r' "$bat_file" 2>/dev/null; then
                        sed -i.bak 's/$/\r/' "$bat_file" 2>/dev/null && rm -f "${bat_file}.bak"
                        echo "    ✅ $(basename "$bat_file") 换行符转换完成"
                    else
                        echo "    ℹ️  $(basename "$bat_file") 已经是CRLF格式"
                    fi
                fi
            done
        # 方法3: 使用perl（通常在macOS上可用）
        elif command -v perl >/dev/null 2>&1; then
            echo "    使用perl转换换行符"
            for bat_file in "$target_dir"/*.bat; do
                if [ -f "$bat_file" ]; then
                    perl -i -pe 's/\n/\r\n/g unless /\r\n/' "$bat_file" 2>/dev/null
                    echo "    ✅ $(basename "$bat_file") 换行符转换完成"
                fi
            done
        # 方法4: 使用python（最后的备选方案）
        elif command -v python3 >/dev/null 2>&1; then
            echo "    使用python转换换行符"
            for bat_file in "$target_dir"/*.bat; do
                if [ -f "$bat_file" ]; then
                    python3 -c "
import sys
with open('$bat_file', 'rb') as f:
    content = f.read()
if b'\r\n' not in content:
    content = content.replace(b'\n', b'\r\n')
    with open('$bat_file', 'wb') as f:
        f.write(content)
    print('    ✅ $(basename "$bat_file") 换行符转换完成')
else:
    print('    ℹ️  $(basename "$bat_file") 已经是CRLF格式')
" 2>/dev/null || echo "    ⚠️  python转换失败"
                fi
            done
        else
            echo "    ⚠️  警告: 未找到换行符转换工具，Windows批处理文件可能无法正常运行"
            echo "    请在Windows系统上手动转换换行符或安装dos2unix工具"
        fi
        
        # 验证转换结果
        for bat_file in "$target_dir"/*.bat; do
            if [ -f "$bat_file" ]; then
                if grep -q $'\r' "$bat_file" 2>/dev/null; then
                    echo "    ✅ $(basename "$bat_file") 使用CRLF换行符"
                else
                    echo "    ❌ $(basename "$bat_file") 仍使用LF换行符"
                fi
            fi
        done

    else
        # Unix 启动脚本
        cat > "$target_dir/start-agent.sh" << 'EOF'
#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JRE_HOME="$SCRIPT_DIR/jre"
JAVA_EXE="$JRE_HOME/bin/java"
PID_FILE="$SCRIPT_DIR/agent.pid"
LOG_FILE="$SCRIPT_DIR/logs/agent.log"

# 创建日志目录
mkdir -p "$SCRIPT_DIR/logs"

echo "LightScript Agent 启动中..."
echo "工作目录: $SCRIPT_DIR"

# 检查是否已经在运行
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p $PID > /dev/null 2>&1; then
        echo "Agent 已经在运行 (PID: $PID)"
        echo "如需重启，请先运行: ./stop-agent.sh"
        exit 1
    else
        echo "清理过期的PID文件..."
        rm -f "$PID_FILE"
    fi
fi

# 优先使用内置JRE
if [ -f "$JAVA_EXE" ] && [ -x "$JAVA_EXE" ]; then
    echo "使用内置JRE: $JAVA_EXE"
    
    # 测试JRE是否可用
    if "$JAVA_EXE" -version >/dev/null 2>&1; then
        JAVA_CMD="$JAVA_EXE"
    else
        echo "⚠️  内置JRE不可用，尝试系统Java..."
        JAVA_CMD=""
    fi
else
    echo "⚠️  未找到内置JRE，尝试系统Java..."
    JAVA_CMD=""
fi

# 如果内置JRE不可用，尝试系统Java
if [ -z "$JAVA_CMD" ]; then
    if command -v java >/dev/null 2>&1; then
        echo "使用系统Java: $(which java)"
        JAVA_CMD="java"
    elif [ -n "$JAVA_HOME" ] && [ -f "$JAVA_HOME/bin/java" ]; then
        echo "使用JAVA_HOME: $JAVA_HOME/bin/java"
        JAVA_CMD="$JAVA_HOME/bin/java"
    else
        echo "❌ 错误: 未找到可用的Java运行时!"
        echo ""
        echo "请安装Java 8或更高版本:"
        echo "  macOS:   brew install openjdk@8"
        echo "  Ubuntu:  sudo apt install openjdk-8-jre"
        echo "  CentOS:  sudo yum install java-1.8.0-openjdk"
        echo ""
        echo "或者从以下地址下载: https://adoptium.net/temurin/releases/"
        exit 1
    fi
fi

# 显示Java版本信息
echo "Java版本信息:"
"$JAVA_CMD" -version

echo ""
echo "启动LightScript Agent..."
echo "Agent JAR: $SCRIPT_DIR/agent.jar"
echo "日志文件: $LOG_FILE"

# 检测是否在LaunchAgent环境下运行
if [ -n "$LAUNCHED_BY_LAUNCHD" ] || [ "$1" = "--launchd" ]; then
    echo "检测到LaunchAgent环境，前台启动..."
    # LaunchAgent环境下，前台启动，不使用nohup
    exec "$JAVA_CMD" \
        -Xms32m \
        -Xmx128m \
        -XX:MaxMetaspaceSize=64m \
        -Dfile.encoding=UTF-8 \
        -Djava.awt.headless=true \
        -jar "$SCRIPT_DIR/agent.jar"
else
    echo "手动启动模式，后台启动..."
    # 手动启动时，后台启动
    nohup "$JAVA_CMD" \
        -Xms32m \
        -Xmx128m \
        -XX:MaxMetaspaceSize=64m \
        -Dfile.encoding=UTF-8 \
        -Djava.awt.headless=true \
        -jar "$SCRIPT_DIR/agent.jar" \
        > /dev/null 2>&1 &

    # 保存PID
    AGENT_PID=$!
    echo $AGENT_PID > "$PID_FILE"

    # 等待一下确保启动成功
    sleep 2

    # 检查进程是否还在运行
    if ps -p $AGENT_PID > /dev/null 2>&1; then
        echo "✅ Agent 启动成功 (PID: $AGENT_PID)"
        echo ""
        echo "使用以下命令:"
        echo "  查看日志: tail -f $LOG_FILE"
        echo "  停止服务: ./stop-agent.sh"
        echo "  查看状态: ps -p $AGENT_PID"
    else
        echo "❌ Agent 启动失败"
        echo "请查看日志文件: $LOG_FILE"
        rm -f "$PID_FILE"
        exit 1
    fi
fi
EOF

        # Unix 停止脚本
        cat > "$target_dir/stop-agent.sh" << 'EOF'
#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ID_FILE="$HOME/.viberemote/.agent_id"
PROPS_FILE="$SCRIPT_DIR/agent.properties"

echo "停止 VibeRemote Agent..."

# 读取 server.url 和凭证，主动通知服务器离线
SERVER_URL=$(grep -m1 "^server\.url=" "$PROPS_FILE" 2>/dev/null | cut -d= -f2-)
AGENT_ID=$(grep -m1 "^agentId=" "$ID_FILE" 2>/dev/null | cut -d= -f2-)
AGENT_TOKEN=$(grep -m1 "^agentToken=" "$ID_FILE" 2>/dev/null | cut -d= -f2-)

if [ -n "$SERVER_URL" ] && [ -n "$AGENT_ID" ] && [ -n "$AGENT_TOKEN" ]; then
    echo "通知服务器离线..."
    curl -s -X POST "$SERVER_URL/api/agent/offline" \
        -d "agentId=$AGENT_ID&agentToken=$AGENT_TOKEN" >/dev/null 2>&1 \
        && echo "服务器已收到离线通知" \
        || echo "警告: 离线通知发送失败，继续停止进程"
else
    echo "未找到凭证，跳过离线通知"
fi

# 查找并停止 agent 进程
PIDS=$(ps aux | grep "java.*agent.jar" | grep -v grep | awk '{print $2}')
if [ -n "$PIDS" ]; then
    for PID in $PIDS; do
        echo "停止进程 $PID..."
        kill $PID 2>/dev/null || true
        for i in {1..10}; do
            ps -p $PID > /dev/null 2>&1 || break
            sleep 1
        done
        ps -p $PID > /dev/null 2>&1 && kill -9 $PID 2>/dev/null || true
    done
else
    echo "未找到运行中的 Agent 进程"
fi

echo "Agent 已停止"
EOF

        # Unix 状态检查脚本
        cat > "$target_dir/check-status.sh" << 'EOF'
#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="$SCRIPT_DIR/logs/agent.log"

echo "========================================"
echo "  VibeRemote Agent 状态检查"
echo "========================================"
echo ""

# 检查进程
PIDS=$(ps aux | grep "java.*agent.jar" | grep -v grep | awk '{print $2}')
if [ -n "$PIDS" ]; then
    echo "[状态] 运行中 (PID: $PIDS)"
else
    echo "[状态] 未运行"
fi

echo ""

# 检查开机启动（macOS）
if [[ "$(uname)" == "Darwin" ]]; then
    PLIST="$HOME/Library/LaunchAgents/com.viberemote.agent.plist"
    if [ -f "$PLIST" ]; then
        echo "[开机启动] 已启用 (LaunchAgent)"
    else
        echo "[开机启动] 未启用"
    fi
fi

# 检查开机启动（Linux）
if [[ "$(uname)" == "Linux" ]]; then
    if systemctl is-enabled viberemote-agent >/dev/null 2>&1; then
        echo "[开机启动] 已启用 (systemd)"
    elif [ -f "$HOME/.config/autostart/viberemote-agent.desktop" ]; then
        echo "[开机启动] 已启用 (autostart)"
    else
        echo "[开机启动] 未启用"
    fi
fi

echo ""

# 显示最近日志
if [ -f "$LOG_FILE" ]; then
    echo "[最近日志] (最后10行)"
    echo "----------------------------------------"
    tail -10 "$LOG_FILE"
else
    echo "[日志] 暂无日志文件"
fi

echo ""
EOF
        chmod +x "$target_dir/check-status.sh"

        # 设置执行权限
        chmod +x "$target_dir"/*.sh
        
        # 创建卸载脚本
        cat > "$target_dir/uninstall-agent.sh" << 'EOF'
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
echo ""
read -p "是否删除安装目录 $(pwd)? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    cd ..
    rm -rf "$(basename "$OLDPWD")"
    echo "✅ LightScript Agent 已完全卸载"
else
    echo "✅ LightScript Agent 服务已卸载，文件保留"
fi
EOF

        chmod +x "$target_dir/uninstall-agent.sh"
    fi
}

# 创建安装包函数
create_package() {
    local temp_dir=$1
    local package_name=$2
    local os=$3
    
    echo "  📦 打包 $package_name..."
    
    # 创建 README
    local build_time
    build_time=$(date '+%Y-%m-%d %H:%M:%S %Z')
    cat > "$temp_dir/README.txt" << EOF
LightScript Agent v$VERSION
Build Time: $build_time

安装说明:
1. 解压此文件到目标目录
2. 编辑 agent.properties 配置服务器地址
3. 运行启动脚本:
   - Windows: start-agent.bat
   - Linux/macOS: ./start-agent.sh

配置文件:
- agent.properties: 主要配置文件
- jre/: 内置 Java 运行时环境

更多信息请访问: https://lightscript.com
EOF
    
    # 根据操作系统创建不同格式的安装包
    if [ "$os" = "windows" ]; then
        # Windows: 创建 ZIP 包
        (cd "$temp_dir" && zip -r -q "$RELEASE_DIR/${package_name}.zip" .)
    else
        # Unix: 创建 tar.gz 包
        (cd "$temp_dir" && tar -czf "$RELEASE_DIR/${package_name}.tar.gz" .)
    fi
}

# 构建函数
build_package() {
    local os=$1
    local arch=$2
    local jre_filename=$3
    local package_name="viberemote-agent-${VERSION}-${os}-${arch}"
    local temp_dir="$RELEASE_DIR/temp_${os}_${arch}"
    
    echo "📦 构建 ${os}-${arch} 安装包..."
    
    # 创建临时目录
    mkdir -p "$temp_dir"
    
    # 复制基础文件
    cp "$AGENT_JAR" "$temp_dir/agent.jar"
    cp "$UPGRADER_JAR" "$temp_dir/upgrader.jar"
    
    # 创建配置文件，根据平台设置默认服务器地址
    if [ "$os" = "windows" ]; then
        # Windows版本默认连接阿里云
        cat > "$temp_dir/agent.properties" << 'EOF'
# LightScript Agent 配置文件

# 服务器配置 (默认连接阿里云)
server.url=http://8.138.114.34:8080
register.token=917ab328ac48ff6aeb01f38b3a3a554a07a9b623f60a9bdde9ac73a9353acc83

# Agent配置
agent.name=${hostname}
agent.labels=

# 心跳配置
heartbeat.interval=30000
heartbeat.system.info.interval=600000
heartbeat.max.failures=3

# 任务配置
task.pull.max=10
task.pull.interval=5000

# 升级配置
upgrade.backup.keep=1
upgrade.verify.timeout=15000

# 日志配置
log.level=INFO
log.file.max.size=10MB
log.file.max.count=5

# 批量日志配置 (第一阶段性能优化)
log.batch.enabled=true
log.batch.size=1000
log.batch.timeout=5000
log.compression.enabled=false
log.async.enabled=true
log.retry.max=3

# 加密配置 (第二阶段通信加密)
encryption.enabled=true
encryption.key.rotation.days=30
encryption.algorithm=AES-256-GCM
EOF
    else
        # Unix版本使用原配置文件
        cp "$SCRIPT_DIR/src/main/resources/agent.properties" "$temp_dir/"
    fi
    
    # 创建启动脚本
    create_scripts "$temp_dir" "$os"
    
    # 使用完整JRE
    use_full_jre "$temp_dir" "$jre_filename" "$os"
    
    # 创建安装包
    create_package "$temp_dir" "$package_name" "$os"
    
    # 清理临时目录
    rm -rf "$temp_dir"
    
    echo "✅ ${os}-${arch} 安装包构建完成"
}

# 主构建流程
echo "🔨 开始构建各平台安装包..."
echo ""

# 构建各平台包
build_package "windows" "x64" "bellsoft-jre8u482+10-windows-amd64.zip"
build_package "linux" "x64" "bellsoft-jre8u482+10-linux-amd64.tar.gz"
build_package "macos" "x64" "bellsoft-jre8u482+10-macos-amd64.tar.gz"
build_package "macos" "arm64" "bellsoft-jre8u482+10-macos-aarch64.tar.gz"

echo ""
echo "🎉 所有安装包构建完成!"
echo ""
echo "📦 生成的安装包:"
ls -la "$RELEASE_DIR"/*.{zip,tar.gz} 2>/dev/null || echo "  (无安装包文件)"

echo ""
echo "📋 安装包说明:"
echo "  • Windows: viberemote-agent-${VERSION}-windows-x64.zip"
echo "  • Linux:   viberemote-agent-${VERSION}-linux-x64.tar.gz" 
echo "  • macOS:   viberemote-agent-${VERSION}-macos-x64.tar.gz"
echo "  • macOS:   viberemote-agent-${VERSION}-macos-arm64.tar.gz"
echo ""
echo "✅ 构建完成! 安装包已保存到: $RELEASE_DIR"