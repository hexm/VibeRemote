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
        # Windows 启动脚本
        cat > "$target_dir/start-agent.bat" << 'EOF'
@echo off
setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set JRE_HOME=%SCRIPT_DIR%jre
set JAVA_EXE=%JRE_HOME%\bin\java.exe

echo LightScript Agent 启动中...
echo 工作目录: %SCRIPT_DIR%

REM 优先使用内置JRE
if exist "%JAVA_EXE%" (
    echo 使用内置JRE: %JAVA_EXE%
    
    REM 测试JRE是否可用
    "%JAVA_EXE%" -version >nul 2>&1
    if !errorlevel! equ 0 (
        set JAVA_CMD=%JAVA_EXE%
        goto :start_agent
    ) else (
        echo 警告: 内置JRE不可用，尝试系统Java...
    )
) else (
    echo 警告: 未找到内置JRE，尝试系统Java...
)

REM 尝试系统Java
java -version >nul 2>&1
if !errorlevel! equ 0 (
    echo 使用系统Java
    set JAVA_CMD=java
    goto :start_agent
)

REM 尝试JAVA_HOME
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        echo 使用JAVA_HOME: %JAVA_HOME%\bin\java.exe
        set JAVA_CMD=%JAVA_HOME%\bin\java.exe
        goto :start_agent
    )
)

REM 未找到Java
echo 错误: 未找到可用的Java运行时!
echo.
echo 请安装Java 8或更高版本:
echo   下载地址: https://adoptium.net/temurin/releases/
echo.
pause
exit /b 1

:start_agent
echo Java版本信息:
"%JAVA_CMD%" -version

echo.
echo 启动LightScript Agent...
echo Agent JAR: %SCRIPT_DIR%agent.jar

REM 启动Agent (控制内存使用)
"%JAVA_CMD%" -Xms32m -Xmx128m -XX:MaxMetaspaceSize=64m -Dfile.encoding=UTF-8 -Djava.awt.headless=true -jar "%SCRIPT_DIR%agent.jar"
EOF

        # Windows 停止脚本
        cat > "$target_dir/stop-agent.bat" << 'EOF'
@echo off
echo Stopping LightScript Agent...
taskkill /f /im java.exe /fi "WINDOWTITLE eq LightScript Agent*" 2>nul
echo Agent stopped.
pause
EOF

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
echo "启动LightScript Agent (后台模式)..."
echo "Agent JAR: $SCRIPT_DIR/agent.jar"
echo "日志文件: $LOG_FILE"

# 后台启动Agent，控制内存使用
nohup "$JAVA_CMD" \
    -Xms32m \
    -Xmx128m \
    -XX:MaxMetaspaceSize=64m \
    -Dfile.encoding=UTF-8 \
    -Djava.awt.headless=true \
    -jar "$SCRIPT_DIR/agent.jar" \
    >> "$LOG_FILE" 2>&1 &

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
EOF

        # Unix 停止脚本
        cat > "$target_dir/stop-agent.sh" << 'EOF'
#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="$SCRIPT_DIR/agent.pid"

echo "停止 LightScript Agent..."

# 检查PID文件是否存在
if [ ! -f "$PID_FILE" ]; then
    echo "未找到PID文件，尝试通过进程名查找..."
    
    # 通过进程名查找
    PIDS=$(ps aux | grep "java.*agent.jar" | grep -v grep | awk '{print $2}')
    
    if [ -z "$PIDS" ]; then
        echo "未找到运行中的Agent进程"
        exit 0
    fi
    
    echo "找到Agent进程: $PIDS"
    for PID in $PIDS; do
        echo "停止进程 $PID..."
        kill $PID
        
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
            kill -9 $PID
        fi
    done
    
    echo "Agent 已停止"
    exit 0
fi

# 读取PID
PID=$(cat "$PID_FILE")

# 检查进程是否存在
if ! ps -p $PID > /dev/null 2>&1; then
    echo "进程 $PID 不存在，清理PID文件..."
    rm -f "$PID_FILE"
    echo "Agent 未运行"
    exit 0
fi

echo "停止Agent进程 (PID: $PID)..."

# 发送TERM信号
kill $PID

# 等待进程优雅退出
echo "等待进程退出..."
for i in {1..15}; do
    if ! ps -p $PID > /dev/null 2>&1; then
        echo "✅ Agent 已停止"
        rm -f "$PID_FILE"
        exit 0
    fi
    sleep 1
done

# 如果进程仍在运行，强制杀死
if ps -p $PID > /dev/null 2>&1; then
    echo "进程未响应，强制停止..."
    kill -9 $PID
    sleep 1
    
    if ps -p $PID > /dev/null 2>&1; then
        echo "❌ 无法停止进程 $PID"
        exit 1
    else
        echo "✅ Agent 已强制停止"
        rm -f "$PID_FILE"
    fi
else
    echo "✅ Agent 已停止"
    rm -f "$PID_FILE"
fi
EOF

        # 设置执行权限
        chmod +x "$target_dir"/*.sh
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