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
        # Windows 启动脚本 - 简化版
        cat > "$target_dir/start-agent.bat" << 'EOF'
@echo off
REM LightScript Agent 启动脚本 (Windows)

REM 切换到脚本目录
cd /d "%~dp0"

REM 创建日志目录
if not exist "logs" mkdir "logs"

echo LightScript Agent 启动中...
echo 工作目录: %CD%

REM 检查Agent JAR文件
if not exist "agent.jar" (
    echo ERROR: Agent JAR file not found: agent.jar
    pause
    exit /b 1
)

REM 优先使用内置JRE
if exist "jre\bin\java.exe" (
    echo 使用内置JRE
    set "JAVA_CMD=jre\bin\java.exe"
    goto :start_agent
)

REM 尝试系统Java
java -version >nul 2>&1
if %errorlevel% equ 0 (
    echo 使用系统Java
    set "JAVA_CMD=java"
    goto :start_agent
)

REM 尝试JAVA_HOME
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        echo 使用JAVA_HOME: %JAVA_HOME%\bin\java.exe
        set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
        goto :start_agent
    )
)

REM 未找到Java
echo ERROR: 未找到可用的Java运行时!
echo 请安装Java 8或更高版本
pause
exit /b 1

:start_agent
echo Java版本信息:
"%JAVA_CMD%" -version

echo.
echo 启动LightScript Agent...
"%JAVA_CMD%" -Xmx512m -Xms128m -jar agent.jar

pause
EOF

        # Windows 停止脚本
        cat > "$target_dir/stop-agent.bat" << 'EOF'
@echo off
setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set PID_FILE=%SCRIPT_DIR%agent.pid
set SERVICE_NAME=LightScriptAgent

echo 停止 LightScript Agent...

REM 检查Windows服务是否存在并运行
sc query "%SERVICE_NAME%" >nul 2>&1
if !errorlevel! equ 0 (
    echo 检测到Windows服务，正在停止...
    net stop "%SERVICE_NAME%" >nul 2>&1
    if !errorlevel! equ 0 (
        echo Windows服务已停止
    ) else (
        echo 警告: 停止Windows服务失败，可能服务未运行
    )
)

REM 检查PID文件是否存在
if exist "%PID_FILE%" (
    set /p AGENT_PID=<"%PID_FILE%"
    
    REM 检查进程是否存在
    tasklist /fi "PID eq !AGENT_PID!" 2>nul | find "!AGENT_PID!" >nul
    if !errorlevel! equ 0 (
        echo 找到Agent进程 (PID: !AGENT_PID!)，正在停止...
        
        REM 尝试优雅停止
        taskkill /pid !AGENT_PID! >nul 2>&1
        
        REM 等待进程退出
        echo 等待进程退出...
        for /l %%i in (1,1,15) do (
            tasklist /fi "PID eq !AGENT_PID!" 2>nul | find "!AGENT_PID!" >nul
            if !errorlevel! neq 0 (
                echo 进程 !AGENT_PID! 已停止
                goto :cleanup_pid
            )
            timeout /t 1 /nobreak >nul
        )
        
        REM 如果进程仍在运行，强制停止
        tasklist /fi "PID eq !AGENT_PID!" 2>nul | find "!AGENT_PID!" >nul
        if !errorlevel! equ 0 (
            echo 强制停止进程 !AGENT_PID!...
            taskkill /f /pid !AGENT_PID! >nul 2>&1
        )
    ) else (
        echo PID文件中的进程 !AGENT_PID! 不存在，清理PID文件...
    )
    
    :cleanup_pid
    del "%PID_FILE%" 2>nul
) else (
    echo 未找到PID文件，尝试通过进程名查找...
)

REM 精确查找剩余的Agent进程（通过命令行参数匹配）
echo 检查剩余的Agent进程...
set FOUND_PROCESSES=0
for /f "tokens=1,2" %%i in ('wmic process where "CommandLine like '%%agent.jar%%' and Name='java.exe'" get ProcessId^,CommandLine /format:csv 2^>nul ^| find /v "Node" ^| find /v "ProcessId" ^| find /v "^$"') do (
    set REMAINING_PID=%%j
    if defined REMAINING_PID (
        set FOUND_PROCESSES=1
        echo 发现剩余的Agent进程: !REMAINING_PID!
        echo 停止进程 !REMAINING_PID!...
        
        REM 尝试优雅停止
        taskkill /pid !REMAINING_PID! >nul 2>&1
        
        REM 等待进程退出
        for /l %%k in (1,1,10) do (
            tasklist /fi "PID eq !REMAINING_PID!" 2>nul | find "!REMAINING_PID!" >nul
            if !errorlevel! neq 0 (
                echo 进程 !REMAINING_PID! 已停止
                goto :next_process
            )
            timeout /t 1 /nobreak >nul
        )
        
        REM 强制停止
        tasklist /fi "PID eq !REMAINING_PID!" 2>nul | find "!REMAINING_PID!" >nul
        if !errorlevel! equ 0 (
            echo 强制停止进程 !REMAINING_PID!...
            taskkill /f /pid !REMAINING_PID! >nul 2>&1
        )
        
        :next_process
    )
)

if !FOUND_PROCESSES! equ 0 (
    echo 未找到运行中的Agent进程
)

echo ✅ Agent 已停止
pause
EOF

        # Windows 服务安装脚本
        cat > "$target_dir/install-service.bat" << 'EOF'
@echo off
setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set SERVICE_NAME=LightScriptAgent
set SERVICE_DISPLAY_NAME=LightScript Agent
set SERVICE_DESCRIPTION=LightScript 分布式脚本执行代理

echo 安装 LightScript Agent Windows 服务...

REM 检查管理员权限
net session >nul 2>&1
if !errorlevel! neq 0 (
    echo ❌ 需要管理员权限安装服务
    echo 请以管理员身份运行此脚本
    pause
    exit /b 1
)

REM 检查服务是否已存在
sc query "%SERVICE_NAME%" >nul 2>&1
if !errorlevel! equ 0 (
    echo 服务已存在，正在删除旧服务...
    net stop "%SERVICE_NAME%" >nul 2>&1
    sc delete "%SERVICE_NAME%" >nul 2>&1
    timeout /t 2 /nobreak >nul
)

REM 创建服务
echo 创建Windows服务...
sc create "%SERVICE_NAME%" ^
    binPath= "cmd.exe /c \"%SCRIPT_DIR%start-agent.bat\" --service" ^
    DisplayName= "%SERVICE_DISPLAY_NAME%" ^
    start= auto ^
    depend= Tcpip

if !errorlevel! equ 0 (
    echo ✅ 服务创建成功
    
    REM 设置服务描述
    sc description "%SERVICE_NAME%" "%SERVICE_DESCRIPTION%"
    
    REM 设置服务恢复选项（失败时自动重启）
    sc failure "%SERVICE_NAME%" reset= 86400 actions= restart/30000/restart/60000/restart/120000
    
    REM 启动服务
    echo 启动服务...
    net start "%SERVICE_NAME%"
    
    if !errorlevel! equ 0 (
        echo ✅ 服务启动成功
        echo.
        echo 使用以下命令管理服务:
        echo   查看状态: sc query "%SERVICE_NAME%"
        echo   启动服务: net start "%SERVICE_NAME%"
        echo   停止服务: net stop "%SERVICE_NAME%"
        echo   卸载服务: uninstall-service.bat
    ) else (
        echo ❌ 服务启动失败
        echo 请检查日志文件: %SCRIPT_DIR%logs\agent.log
    )
) else (
    echo ❌ 服务创建失败
)

pause
EOF

        # Windows 服务卸载脚本
        cat > "$target_dir/uninstall-service.bat" << 'EOF'
@echo off
setlocal enabledelayedexpansion

set SERVICE_NAME=LightScriptAgent

echo 卸载 LightScript Agent Windows 服务...

REM 检查管理员权限
net session >nul 2>&1
if !errorlevel! neq 0 (
    echo ❌ 需要管理员权限卸载服务
    echo 请以管理员身份运行此脚本
    pause
    exit /b 1
)

REM 检查服务是否存在
sc query "%SERVICE_NAME%" >nul 2>&1
if !errorlevel! neq 0 (
    echo 服务不存在或已卸载
    pause
    exit /b 0
)

REM 停止服务
echo 停止服务...
net stop "%SERVICE_NAME%" >nul 2>&1
if !errorlevel! equ 0 (
    echo 服务已停止
) else (
    echo 警告: 停止服务失败，可能服务未运行
)

REM 删除服务
echo 删除服务...
sc delete "%SERVICE_NAME%" >nul 2>&1
if !errorlevel! equ 0 (
    echo ✅ 服务已删除
) else (
    echo ❌ 删除服务失败
)

REM 询问是否删除安装目录
echo.
set /p DELETE_DIR="是否删除安装目录 %~dp0? (y/N): "
if /i "!DELETE_DIR!"=="y" (
    echo 删除安装目录...
    cd /d "%TEMP%"
    rmdir /s /q "%~dp0" 2>nul
    if !errorlevel! equ 0 (
        echo ✅ LightScript Agent 已完全卸载
    ) else (
        echo ⚠️  部分文件可能仍在使用中，请手动删除: %~dp0
    )
) else (
    echo ✅ LightScript Agent 服务已卸载，文件保留
)

pause
EOF

        # Windows 完整卸载脚本
        cat > "$target_dir/uninstall-agent.bat" << 'EOF'
@echo off
setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set SERVICE_NAME=LightScriptAgent

echo 卸载 LightScript Agent...

REM 停止服务（如果存在）
sc query "%SERVICE_NAME%" >nul 2>&1
if !errorlevel! equ 0 (
    echo 停止Windows服务...
    net stop "%SERVICE_NAME%" >nul 2>&1
    sc delete "%SERVICE_NAME%" >nul 2>&1
    echo Windows服务已卸载
)

REM 停止所有Agent进程
echo 停止Agent进程...
for /f "tokens=1,2" %%i in ('wmic process where "CommandLine like '%%agent.jar%%' and Name='java.exe'" get ProcessId^,CommandLine /format:csv 2^>nul ^| find /v "Node" ^| find /v "ProcessId" ^| find /v "^$"') do (
    set AGENT_PID=%%j
    if defined AGENT_PID (
        echo 停止进程: !AGENT_PID!
        taskkill /f /pid !AGENT_PID! >nul 2>&1
    )
)

REM 清理PID文件
if exist "%SCRIPT_DIR%agent.pid" (
    del "%SCRIPT_DIR%agent.pid" 2>nul
)

REM 询问是否删除安装目录
echo.
set /p DELETE_DIR="是否删除安装目录 %SCRIPT_DIR%? (y/N): "
if /i "!DELETE_DIR!"=="y" (
    echo 删除安装目录...
    cd /d "%TEMP%"
    rmdir /s /q "%SCRIPT_DIR%" 2>nul
    if !errorlevel! equ 0 (
        echo ✅ LightScript Agent 已完全卸载
    ) else (
        echo ⚠️  部分文件可能仍在使用中，请手动删除: %SCRIPT_DIR%
    )
) else (
    echo ✅ LightScript Agent 已卸载，文件保留
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
PID_FILE="$SCRIPT_DIR/agent.pid"

echo "停止 LightScript Agent..."

# 检查是否有LaunchAgent服务运行
LAUNCH_AGENT_PLIST="$HOME/Library/LaunchAgents/com.lightscript.agent.plist"
LAUNCH_DAEMON_PLIST="/Library/LaunchDaemons/com.lightscript.agent.plist"

if [ -f "$LAUNCH_AGENT_PLIST" ]; then
    echo "检测到用户级LaunchAgent服务，正在停止..."
    launchctl unload "$LAUNCH_AGENT_PLIST" 2>/dev/null || true
    echo "用户级LaunchAgent服务已停止"
elif [ -f "$LAUNCH_DAEMON_PLIST" ]; then
    echo "检测到系统级LaunchDaemon服务，正在停止..."
    sudo launchctl unload "$LAUNCH_DAEMON_PLIST" 2>/dev/null || true
    echo "系统级LaunchDaemon服务已停止"
fi

# 检查PID文件是否存在
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    
    # 检查进程是否存在
    if ps -p $PID > /dev/null 2>&1; then
        echo "找到Agent进程 (PID: $PID)，正在停止..."
        
        # 发送TERM信号
        kill $PID
        
        # 等待进程优雅退出
        echo "等待进程退出..."
        for i in {1..15}; do
            if ! ps -p $PID > /dev/null 2>&1; then
                echo "进程 $PID 已停止"
                break
            fi
            sleep 1
        done
        
        # 如果进程仍在运行，强制杀死
        if ps -p $PID > /dev/null 2>&1; then
            echo "强制停止进程 $PID..."
            kill -9 $PID
        fi
    else
        echo "PID文件中的进程 $PID 不存在，清理PID文件..."
    fi
    
    # 清理PID文件
    rm -f "$PID_FILE"
fi

# 通过进程名查找剩余的Agent进程
PIDS=$(ps aux | grep "java.*agent.jar" | grep -v grep | awk '{print $2}')

if [ ! -z "$PIDS" ]; then
    echo "发现剩余的Agent进程: $PIDS"
    for PID in $PIDS; do
        echo "停止进程 $PID..."
        kill $PID 2>/dev/null || true
        
        # 等待进程结束
        for i in {1..10}; do
            if ! ps -p $PID > /dev/null 2>&1; then
                echo "进程 $PID 已停止"
                break
            fi
            sleep 1
        done
        
        # 如果进程仍在运行，强制杀死
        if ps -p $PID > /dev/null 2>&1; then
            echo "强制停止进程 $PID..."
            kill -9 $PID 2>/dev/null || true
        fi
    done
fi

echo "✅ Agent 已停止"
EOF

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
    cat > "$temp_dir/README.txt" << EOF
LightScript Agent v$VERSION

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
    local package_name="lightscript-agent-${VERSION}-${os}-${arch}"
    local temp_dir="$RELEASE_DIR/temp_${os}_${arch}"
    
    echo "📦 构建 ${os}-${arch} 安装包..."
    
    # 创建临时目录
    mkdir -p "$temp_dir"
    
    # 复制基础文件
    cp "$AGENT_JAR" "$temp_dir/agent.jar"
    cp "$UPGRADER_JAR" "$temp_dir/upgrader.jar"
    cp "$SCRIPT_DIR/src/main/resources/agent.properties" "$temp_dir/"
    
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
echo "  • Windows: lightscript-agent-${VERSION}-windows-x64.zip"
echo "  • Linux:   lightscript-agent-${VERSION}-linux-x64.tar.gz" 
echo "  • macOS:   lightscript-agent-${VERSION}-macos-x64.tar.gz"
echo "  • macOS:   lightscript-agent-${VERSION}-macos-arm64.tar.gz"
echo ""
echo "✅ 构建完成! 安装包已保存到: $RELEASE_DIR"