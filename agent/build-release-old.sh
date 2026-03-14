#!/bin/bash

# LightScript Agent 发布包构建脚本
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
    
    # JRE文件列表 - 使用BellSoft Liberica JRE (体积更小)
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
        echo "📋 请下载以下JRE文件到 $JRE_CACHE_DIR 目录："
        echo "  Windows x64: bellsoft-jre8u482+10-windows-amd64.zip (~35MB)"
        echo "    https://download.bell-sw.com/java/8u482+10/bellsoft-jre8u482+10-windows-amd64.zip"
        echo ""
        echo "  Linux x64: bellsoft-jre8u482+10-linux-amd64.tar.gz (~40MB)"
        echo "    https://download.bell-sw.com/java/8u482+10/bellsoft-jre8u482+10-linux-amd64.tar.gz"
        echo ""
        echo "  macOS Intel: bellsoft-jre8u482+10-macos-amd64.tar.gz (~40MB)"
        echo "    https://download.bell-sw.com/java/8u482+10/bellsoft-jre8u482+10-macos-amd64.tar.gz"
        echo ""
        echo "  macOS ARM64: bellsoft-jre8u482+10-macos-aarch64.tar.gz (~40MB)"
        echo "    https://download.bell-sw.com/java/8u482+10/bellsoft-jre8u482+10-macos-aarch64.tar.gz"
        echo ""
        echo "💡 或者使用手动下载工具: ./download-jre-manual.sh all"
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

echo "📋 JRE缓存目录: $JRE_CACHE_DIR"

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

# 构建函数
build_package() {
    local os=$1
    local arch=$2
    local jre_url=$3
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
    use_full_jre "$temp_dir" "$jre_url" "$os" "$arch"
    
    # 创建安装包
    create_package "$temp_dir" "$package_name" "$os"
    
    # 清理临时目录
    rm -rf "$temp_dir"
    
    echo "✅ ${os}-${arch} 安装包构建完成"
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

REM 启动Agent
"%JAVA_CMD%" -Xms64m -Xmx256m -Dfile.encoding=UTF-8 -Djava.awt.headless=true -jar "%SCRIPT_DIR%agent.jar"
EOF

        # Windows 停止脚本
        cat > "$target_dir/stop-agent.bat" << 'EOF'
@echo off
echo Stopping LightScript Agent...
taskkill /f /im java.exe /fi "WINDOWTITLE eq LightScript Agent*" 2>nul
echo Agent stopped.
pause
EOF

        # Windows 安装服务脚本
        cat > "$target_dir/install-service.bat" << 'EOF'
@echo off
echo Installing LightScript Agent as Windows Service...
echo This feature will be available in future versions.
pause
EOF

    else
        # Unix 启动脚本
        cat > "$target_dir/start-agent.sh" << 'EOF'
#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JRE_HOME="$SCRIPT_DIR/jre"
JAVA_EXE="$JRE_HOME/bin/java"

echo "LightScript Agent 启动中..."
echo "工作目录: $SCRIPT_DIR"

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

# 启动Agent，添加一些有用的JVM参数
"$JAVA_CMD" \
    -Xms64m \
    -Xmx256m \
    -Dfile.encoding=UTF-8 \
    -Djava.awt.headless=true \
    -jar "$SCRIPT_DIR/agent.jar"
EOF

        # Unix 停止脚本
        cat > "$target_dir/stop-agent.sh" << 'EOF'
#!/bin/bash

echo "Stopping LightScript Agent..."
pkill -f "java.*agent.jar" || echo "No agent process found."
echo "Agent stopped."
EOF

        # Unix 安装服务脚本
        cat > "$target_dir/install-service.sh" << 'EOF'
#!/bin/bash

echo "Installing LightScript Agent as system service..."
echo "This feature will be available in future versions."
echo "For now, you can run the agent manually using ./start-agent.sh"
EOF

        # 设置执行权限
        chmod +x "$target_dir"/*.sh
    fi
}
# 下载并使用完整 JRE 函数
use_full_jre() {
    local target_dir=$1
    local jre_url=$2
    local os=$3
    local arch=$4
    
    echo "  📥 使用完整 JRE for $os-$arch..."
    
    local jre_dir="$target_dir/jre"
    
    # 尝试使用本地JRE文件
    if use_local_jre_full "$jre_dir" "$jre_url" "$os"; then
        echo "    ✅ 使用本地JRE文件成功"
        return
    fi
    
    # 如果本地JRE不可用，创建占位符
    echo "    ⚠️  本地JRE不可用，创建占位符JRE..."
    create_placeholder_jre "$jre_dir" "$os"
}

# 使用本地完整JRE文件
use_local_jre_full() {
    local target_dir=$1
    local jre_url=$2
    local os=$3
    
    echo "    📦 检查JRE缓存..."
    
    # 从URL中提取文件名
    local jre_filename=$(basename "$jre_url")
    local local_jre_file="$JRE_CACHE_DIR/$jre_filename"
    
    # 检查缓存的JRE文件是否存在
    if [ ! -f "$local_jre_file" ]; then
        echo "    ❌ JRE缓存文件不存在: $jre_filename"
        return 1
    fi
    
    echo "    📁 使用缓存的JRE文件: $jre_filename"
    
    # 检查文件大小
    local file_size=$(stat -f%z "$local_jre_file" 2>/dev/null || stat -c%s "$local_jre_file" 2>/dev/null || echo "0")
    if [ "$file_size" -lt 1000000 ]; then  # 小于1MB可能是错误文件
        echo "    ❌ JRE文件太小 ($file_size bytes)，可能已损坏"
        return 1
    fi
    
    echo "    📂 解压完整JRE (文件大小: $(($file_size / 1024 / 1024))MB)..."
    
    # 创建临时解压目录
    local temp_dir=$(mktemp -d)
    local extract_dir="$temp_dir/extracted"
    mkdir -p "$extract_dir"
    
    # 根据文件类型解压JRE
    if [[ "$jre_filename" == *.zip ]]; then
        # Windows ZIP文件
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
        # Unix TAR.GZ文件
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
    
    echo "    📁 JRE源目录: $jre_source"
    
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
    mkdir -p "$target_dir"
    cp -r "$jre_source"/* "$target_dir/" 2>/dev/null || {
        echo "    ❌ JRE复制失败"
        rm -rf "$temp_dir"
        return 1
    }
    
    # 设置执行权限
    if [ "$os" != "windows" ]; then
        chmod +x "$target_dir/bin/java" 2>/dev/null || true
        find "$target_dir/bin" -type f -exec chmod +x {} \; 2>/dev/null || true
    fi
    
    # 创建版本信息文件
    cat > "$target_dir/release" << EOF
JAVA_VERSION="1.8.0_full"
OS_NAME="$os"
OS_ARCH="x64"
SOURCE="Cached JRE File (Full Version)"
BUILD_TYPE="full"
CREATED_BY="LightScript Agent Builder"
JRE_FILE="$jre_filename"
CACHE_DIR="$JRE_CACHE_DIR"
MINIMIZED="false"
OPTIMIZATION_LEVEL="none"
FEATURES="complete_jre_with_all_components"
TARGET_SIZE="35-40MB"
COMPATIBLE_WITH="all_java_applications"
EOF
    
    # 清理临时目录
    rm -rf "$temp_dir"
    
    # 验证创建的JRE
    if [ -f "$target_dir/bin/java" ] || [ -f "$target_dir/bin/java.exe" ]; then
        local final_size=$(du -sm "$target_dir" 2>/dev/null | cut -f1 || echo "0")
        echo "    ✅ 完整JRE复制成功 (体积: ${final_size}MB)"
        return 0
    else
        echo "    ❌ 完整JRE复制失败"
        return 1
    fi
}

# 使用本地JRE文件（支持所有平台）
use_local_jre() {
    local target_dir=$1
    local jre_url=$2
    local os=$3
    
    echo "    📦 检查JRE缓存..."
    
    # 从URL中提取文件名
    local jre_filename=$(basename "$jre_url")
    local local_jre_file="$JRE_CACHE_DIR/$jre_filename"
    
    # 检查缓存的JRE文件是否存在
    if [ ! -f "$local_jre_file" ]; then
        echo "    ❌ JRE缓存文件不存在: $jre_filename"
        echo "    📥 尝试下载JRE文件..."
        
        # 尝试下载JRE文件
        if download_jre_file "$jre_filename" "$jre_url" "$local_jre_file"; then
            echo "    ✅ JRE文件下载成功"
        else
            echo "    ❌ JRE文件下载失败"
            return 1
        fi
    fi
    
    echo "    📁 使用缓存的JRE文件: $jre_filename"
    
    # 检查文件大小
    local file_size=$(stat -f%z "$local_jre_file" 2>/dev/null || stat -c%s "$local_jre_file" 2>/dev/null || echo "0")
    if [ "$file_size" -lt 1000000 ]; then  # 小于1MB可能是错误文件
        echo "    ❌ JRE文件太小 ($file_size bytes)，可能已损坏"
        rm -f "$local_jre_file"
        return 1
    fi
    
    echo "    📂 解压JRE (文件大小: $(($file_size / 1024 / 1024))MB)..."
    
    # 创建临时解压目录
    local temp_dir=$(mktemp -d)
    local extract_dir="$temp_dir/extracted"
    mkdir -p "$extract_dir"
    
    # 根据文件类型解压JRE
    if [[ "$jre_filename" == *.zip ]]; then
        # Windows ZIP文件
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
        # Unix TAR.GZ文件
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
    
    echo "    📁 JRE源目录: $jre_source"
    
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
    
    # 创建超精简JRE（目标：8-11MB）
    echo "    🔧 创建超精简JRE（目标体积：8-11MB）..."
    mkdir -p "$target_dir"
    
    # 1. 复制JVM核心可执行文件
    mkdir -p "$target_dir/bin"
    if [ "$os" = "windows" ]; then
        copy_if_exists "$jre_source/bin/java.exe" "$target_dir/bin/"
        # 只复制最核心的DLL
        copy_if_exists "$jre_source/bin/msvcr100.dll" "$target_dir/bin/"
        copy_if_exists "$jre_source/bin/server" "$target_dir/bin/"
    else
        copy_if_exists "$jre_source/bin/java" "$target_dir/bin/"
        chmod +x "$target_dir/bin/java" 2>/dev/null
    fi
    
    # 2. 创建超精简lib目录
    mkdir -p "$target_dir/lib"
    
    # 只保留绝对必需的核心JAR文件
    echo "    📦 提取核心运行时库..."
    
    # rt.jar是最重要的，包含所有核心类
    if [ -f "$jre_source/lib/rt.jar" ]; then
        # 暂时不精简 rt.jar，直接复制以确保兼容性
        echo "    📦 复制完整 rt.jar (确保兼容性)..."
        copy_if_exists "$jre_source/lib/rt.jar" "$target_dir/lib/rt.jar"
    fi
    
    # 网络和安全相关（Agent需要HTTP通信）
    copy_if_exists "$jre_source/lib/jsse.jar" "$target_dir/lib/"
    copy_if_exists "$jre_source/lib/jce.jar" "$target_dir/lib/"
    
    # 字符集支持（最小化）
    create_minimal_charsets "$jre_source/lib/charsets.jar" "$target_dir/lib/charsets.jar"
    
    # JVM配置文件（必需）
    copy_if_exists "$jre_source/lib/jvm.cfg" "$target_dir/lib/"
    
    # 最小化安全配置
    create_minimal_security_config "$jre_source/lib/security" "$target_dir/lib/security"
    
    # 3. JVM本地库（只保留server模式）
    if [ "$os" = "macos" ]; then
        # macOS 特殊处理 - 动态库直接在 lib/ 目录下
        mkdir -p "$target_dir/lib/server"
        
        # JVM 核心库
        copy_if_exists "$jre_source/lib/server/libjvm.dylib" "$target_dir/lib/server/"
        copy_if_exists "$jre_source/lib/server/libjsig.dylib" "$target_dir/lib/server/"
        copy_if_exists "$jre_source/lib/server/Xusage.txt" "$target_dir/lib/server/"
        
        # 必需的系统库
        copy_if_exists "$jre_source/lib/libjava.dylib" "$target_dir/lib/"
        copy_if_exists "$jre_source/lib/libnet.dylib" "$target_dir/lib/"
        copy_if_exists "$jre_source/lib/libnio.dylib" "$target_dir/lib/"
        copy_if_exists "$jre_source/lib/libzip.dylib" "$target_dir/lib/"
        copy_if_exists "$jre_source/lib/libverify.dylib" "$target_dir/lib/"
        
    elif [ "$os" = "linux" ]; then
        # Linux 处理 - 使用 amd64 子目录
        mkdir -p "$target_dir/lib/amd64/server"
        
        # JVM 核心库
        copy_if_exists "$jre_source/lib/amd64/server/libjvm.so" "$target_dir/lib/amd64/server/"
        
        # 必需的系统库
        copy_if_exists "$jre_source/lib/amd64/libjava.so" "$target_dir/lib/amd64/"
        copy_if_exists "$jre_source/lib/amd64/libnet.so" "$target_dir/lib/amd64/"
        copy_if_exists "$jre_source/lib/amd64/libnio.so" "$target_dir/lib/amd64/"
        copy_if_exists "$jre_source/lib/amd64/libzip.so" "$target_dir/lib/amd64/"
        copy_if_exists "$jre_source/lib/amd64/libverify.so" "$target_dir/lib/amd64/"
    fi
    
    # 创建超精简版release文件
    cat > "$target_dir/release" << EOF
JAVA_VERSION="1.8.0_ultra_minimal"
OS_NAME="$os"
OS_ARCH="x64"
SOURCE="Cached JRE File (Ultra Minimized)"
BUILD_TYPE="ultra-minimal-server"
CREATED_BY="LightScript Agent Builder"
JRE_FILE="$jre_filename"
CACHE_DIR="$JRE_CACHE_DIR"
MINIMIZED="true"
OPTIMIZATION_LEVEL="ultra"
FEATURES_REMOVED="desktop,swing,awt,audio,print,management,applet,rmi,corba,jaxb,jaxws"
FEATURES_KEPT="jvm,network,io,collections,strings,threading,security"
TARGET_SIZE="8-11MB"
COMPATIBLE_WITH="server_applications,command_line_tools,network_services"
EOF
    
    # 清理临时目录
    rm -rf "$temp_dir"
    
    # 优化JRE体积
    optimize_jre_size "$target_dir" "$os"
    
    # 验证创建的JRE
    if [ -f "$target_dir/bin/java" ] || [ -f "$target_dir/bin/java.exe" ]; then
        local final_size=$(du -sm "$target_dir" 2>/dev/null | cut -f1 || echo "0")
        echo "    ✅ 最小化JRE创建成功 (体积: ${final_size}MB)"
        return 0
    else
        echo "    ❌ 最小化JRE创建失败"
        return 1
    fi
}

# JRE体积优化函数
optimize_jre_size() {
    local jre_dir=$1
    local os=$2
    
    echo "    📉 优化JRE体积..."
    
    # 移除调试信息和符号表
    if [ "$os" != "windows" ]; then
        find "$jre_dir" -name "*.debuginfo" -delete 2>/dev/null || true
        find "$jre_dir" -name "*.diz" -delete 2>/dev/null || true
    fi
    
    # 移除示例和文档
    rm -rf "$jre_dir/demo" 2>/dev/null || true
    rm -rf "$jre_dir/sample" 2>/dev/null || true
    rm -rf "$jre_dir/man" 2>/dev/null || true
    rm -rf "$jre_dir/docs" 2>/dev/null || true
    
    # 移除源码
    find "$jre_dir" -name "src.zip" -delete 2>/dev/null || true
    
    # 压缩JAR文件（如果pack200可用）
    if command -v pack200 >/dev/null 2>&1; then
        echo "    🗜️  压缩JAR文件..."
        find "$jre_dir/lib" -name "*.jar" -type f | while read jar_file; do
            local packed_file="${jar_file}.pack.gz"
            if pack200 "$packed_file" "$jar_file" 2>/dev/null; then
                if unpack200 "$packed_file" "${jar_file}.tmp" 2>/dev/null; then
                    mv "${jar_file}.tmp" "$jar_file"
                    rm -f "$packed_file"
                fi
            fi
        done
    fi
    
    # 移除空目录
    find "$jre_dir" -type d -empty -delete 2>/dev/null || true
    
    local optimized_size=$(du -sm "$jre_dir" 2>/dev/null | cut -f1 || echo "0")
    echo "    ✅ JRE优化完成，当前体积: ${optimized_size}MB"
}

# 创建超精简rt.jar（只保留核心包）
# 创建超精简rt.jar（只保留核心包）
create_minimal_rt_jar() {
    local source_jar=$1
    local target_jar=$2
    
    echo "    🔧 创建精简rt.jar..."
    
    # 检查是否启用激进精简模式
    local AGGRESSIVE_MINIMIZE=${AGGRESSIVE_MINIMIZE:-false}
    
    if [ "$AGGRESSIVE_MINIMIZE" = "true" ]; then
        # 激进精简模式：只保留最核心的包
        create_aggressive_minimal_rt_jar "$source_jar" "$target_jar"
    else
        # 保守精简模式：保留更多包以确保兼容性
        create_conservative_minimal_rt_jar "$source_jar" "$target_jar"
    fi
}

# 保守精简模式 - 确保兼容性
create_conservative_minimal_rt_jar() {
    local source_jar=$1
    local target_jar=$2
    
    echo "    📦 使用保守精简模式..."
    
    # 创建临时目录
    local temp_dir=$(mktemp -d)
    local extract_dir="$temp_dir/rt_extracted"
    local minimal_dir="$temp_dir/rt_minimal"
    
    mkdir -p "$extract_dir" "$minimal_dir"
    
    # 解压原始rt.jar
    if command -v unzip >/dev/null 2>&1; then
        unzip -q "$source_jar" -d "$extract_dir" 2>/dev/null || {
            echo "    ⚠️  rt.jar解压失败，使用原文件"
            cp "$source_jar" "$target_jar"
            rm -rf "$temp_dir"
            return
        }
    else
        echo "    ⚠️  未找到unzip，使用原rt.jar"
        cp "$source_jar" "$target_jar"
        rm -rf "$temp_dir"
        return
    fi
    
    # 保守精简：移除明确不需要的包，保留其他所有包
    local exclude_packages=(
        "java/awt"           # 桌面GUI
        "javax/swing"        # Swing GUI
        "javax/sound"        # 音频支持
        "javax/print"        # 打印支持
        "javax/imageio"      # 图像处理
        "sun/awt"            # AWT实现
        "sun/swing"          # Swing实现
        "sun/audio"          # 音频实现
        "sun/print"          # 打印实现
        "com/sun/imageio"    # 图像处理实现
        "com/sun/media"      # 媒体支持
        "org/w3c"            # W3C标准
        "org/xml/sax"        # XML SAX (保留基础XML)
        "org/omg"            # CORBA
    )
    
    # 复制所有内容
    cp -r "$extract_dir"/* "$minimal_dir/" 2>/dev/null || true
    
    # 删除不需要的包
    for package in "${exclude_packages[@]}"; do
        if [ -d "$minimal_dir/$package" ]; then
            echo "    🗑️  移除包: $package"
            rm -rf "$minimal_dir/$package"
        fi
    done
    
    # 重新打包
    if command -v zip >/dev/null 2>&1; then
        (cd "$minimal_dir" && zip -r -q "$target_jar" . 2>/dev/null) || {
            echo "    ⚠️  重新打包失败，使用原文件"
            cp "$source_jar" "$target_jar"
        }
    else
        echo "    ⚠️  未找到zip，使用原rt.jar"
        cp "$source_jar" "$target_jar"
    fi
    
    # 清理
    rm -rf "$temp_dir"
    
    local original_size=$(stat -f%z "$source_jar" 2>/dev/null || stat -c%s "$source_jar" 2>/dev/null || echo "0")
    local minimal_size=$(stat -f%z "$target_jar" 2>/dev/null || stat -c%s "$target_jar" 2>/dev/null || echo "0")
    
    if [ "$minimal_size" -gt 0 ] && [ "$minimal_size" -lt "$original_size" ]; then
        echo "    ✅ rt.jar保守精简成功: $(($original_size / 1024 / 1024))MB → $(($minimal_size / 1024 / 1024))MB"
    else
        echo "    ℹ️  rt.jar保持原始大小"
    fi
}

# 激进精简模式 - 最小体积
create_aggressive_minimal_rt_jar() {
    local source_jar=$1
    local target_jar=$2
    
    echo "    ⚡ 使用激进精简模式..."
    
    # 创建临时目录
    local temp_dir=$(mktemp -d)
    local extract_dir="$temp_dir/rt_extracted"
    local minimal_dir="$temp_dir/rt_minimal"
    
    mkdir -p "$extract_dir" "$minimal_dir"
    
    # 解压原始rt.jar
    if command -v unzip >/dev/null 2>&1; then
        unzip -q "$source_jar" -d "$extract_dir" 2>/dev/null || {
            echo "    ⚠️  rt.jar解压失败，使用原文件"
            cp "$source_jar" "$target_jar"
            rm -rf "$temp_dir"
            return
        }
    else
        echo "    ⚠️  未找到unzip，使用原rt.jar"
        cp "$source_jar" "$target_jar"
        rm -rf "$temp_dir"
        return
    fi
    
    # 只保留核心包（LightScript Agent需要的）
    local core_packages=(
        "java/lang"
        "java/util"
        "java/io"
        "java/net"
        "java/nio"
        "java/security"
        "java/text"
        "java/math"
        "java/sql"
        "javax/net"
        "javax/security"
        "sun/net"
        "sun/nio"
        "sun/security"
        "sun/util"
        "sun/misc"           # 系统关键类 (Unsafe等)
        "sun/reflect"        # 反射支持
        "sun/launcher"       # 启动器支持
        "sun/management"     # 管理接口 (可能需要)
        "sun/font"           # 字体支持 (基础)
        "sun/text"           # 文本处理
        "com/sun/net"
        "com/sun/security"   # 安全实现
    )
    
    # 复制核心包
    for package in "${core_packages[@]}"; do
        if [ -d "$extract_dir/$package" ]; then
            mkdir -p "$minimal_dir/$(dirname "$package")"
            cp -r "$extract_dir/$package" "$minimal_dir/$package" 2>/dev/null || true
        fi
    done
    
    # 复制META-INF
    copy_if_exists "$extract_dir/META-INF" "$minimal_dir/"
    
    # 重新打包
    if command -v zip >/dev/null 2>&1; then
        (cd "$minimal_dir" && zip -r -q "$target_jar" . 2>/dev/null) || {
            echo "    ⚠️  重新打包失败，使用原文件"
            cp "$source_jar" "$target_jar"
        }
    else
        echo "    ⚠️  未找到zip，使用原rt.jar"
        cp "$source_jar" "$target_jar"
    fi
    
    # 清理
    rm -rf "$temp_dir"
    
    local original_size=$(stat -f%z "$source_jar" 2>/dev/null || stat -c%s "$source_jar" 2>/dev/null || echo "0")
    local minimal_size=$(stat -f%z "$target_jar" 2>/dev/null || stat -c%s "$target_jar" 2>/dev/null || echo "0")
    
    if [ "$minimal_size" -gt 0 ] && [ "$minimal_size" -lt "$original_size" ]; then
        echo "    ✅ rt.jar激进精简成功: $(($original_size / 1024 / 1024))MB → $(($minimal_size / 1024 / 1024))MB"
    else
        echo "    ℹ️  rt.jar保持原始大小"
    fi
}
    
    # 创建临时目录
    local temp_dir=$(mktemp -d)
    local extract_dir="$temp_dir/rt_extracted"
    local minimal_dir="$temp_dir/rt_minimal"
    
    mkdir -p "$extract_dir" "$minimal_dir"
    
    # 解压原始rt.jar
    if command -v unzip >/dev/null 2>&1; then
        unzip -q "$source_jar" -d "$extract_dir" 2>/dev/null || {
            echo "    ⚠️  rt.jar解压失败，使用原文件"
            cp "$source_jar" "$target_jar"
            rm -rf "$temp_dir"
            return
        }
    else
        echo "    ⚠️  未找到unzip，使用原rt.jar"
        cp "$source_jar" "$target_jar"
        rm -rf "$temp_dir"
        return
    fi
    
    # 只保留核心包（LightScript Agent需要的）
    local core_packages=(
        "java/lang"
        "java/util"
        "java/io"
        "java/net"
        "java/nio"
        "java/security"
        "java/text"
        "java/math"
        "java/sql"
        "javax/net"
        "javax/security"
        "sun/net"
        "sun/nio"
        "sun/security"
        "sun/util"
        "sun/misc"           # 系统关键类 (Unsafe等)
        "sun/reflect"        # 反射支持
        "sun/launcher"       # 启动器支持
        "sun/management"     # 管理接口 (可能需要)
        "sun/font"           # 字体支持 (基础)
        "sun/text"           # 文本处理
        "com/sun/net"
        "com/sun/security"   # 安全实现
    )
    
    # 复制核心包
    for package in "${core_packages[@]}"; do
        if [ -d "$extract_dir/$package" ]; then
            mkdir -p "$minimal_dir/$(dirname "$package")"
            cp -r "$extract_dir/$package" "$minimal_dir/$package" 2>/dev/null || true
        fi
    done
    
    # 复制META-INF
    copy_if_exists "$extract_dir/META-INF" "$minimal_dir/"
    
    # 重新打包
    if command -v zip >/dev/null 2>&1; then
        (cd "$minimal_dir" && zip -r -q "$target_jar" . 2>/dev/null) || {
            echo "    ⚠️  重新打包失败，使用原文件"
            cp "$source_jar" "$target_jar"
        }
    else
        echo "    ⚠️  未找到zip，使用原rt.jar"
        cp "$source_jar" "$target_jar"
    fi
    
    # 清理
    rm -rf "$temp_dir"
    
    local original_size=$(stat -f%z "$source_jar" 2>/dev/null || stat -c%s "$source_jar" 2>/dev/null || echo "0")
    local minimal_size=$(stat -f%z "$target_jar" 2>/dev/null || stat -c%s "$target_jar" 2>/dev/null || echo "0")
    
    if [ "$minimal_size" -gt 0 ] && [ "$minimal_size" -lt "$original_size" ]; then
        echo "    ✅ rt.jar精简成功: $(($original_size / 1024 / 1024))MB → $(($minimal_size / 1024 / 1024))MB"
    else
        echo "    ℹ️  rt.jar保持原始大小"
    fi
}

# 创建最小化字符集文件
create_minimal_charsets() {
    local source_jar=$1
    local target_jar=$2
    
    # 只保留UTF-8和基本字符集
    if [ -f "$source_jar" ]; then
        local temp_dir=$(mktemp -d)
        local extract_dir="$temp_dir/charsets_extracted"
        local minimal_dir="$temp_dir/charsets_minimal"
        
        mkdir -p "$extract_dir" "$minimal_dir"
        
        if command -v unzip >/dev/null 2>&1 && unzip -q "$source_jar" -d "$extract_dir" 2>/dev/null; then
            # 只保留UTF-8相关字符集
            find "$extract_dir" -name "*UTF*" -o -name "*utf*" -o -name "*US_ASCII*" -o -name "*ISO_8859_1*" | while read file; do
                local rel_path="${file#$extract_dir/}"
                mkdir -p "$minimal_dir/$(dirname "$rel_path")"
                cp "$file" "$minimal_dir/$rel_path" 2>/dev/null || true
            done
            
            copy_if_exists "$extract_dir/META-INF" "$minimal_dir/"
            
            if command -v zip >/dev/null 2>&1; then
                (cd "$minimal_dir" && zip -r -q "$target_jar" . 2>/dev/null) || cp "$source_jar" "$target_jar"
            else
                cp "$source_jar" "$target_jar"
            fi
        else
            cp "$source_jar" "$target_jar"
        fi
        
        rm -rf "$temp_dir"
    fi
}

# 创建最小化安全配置
create_minimal_security_config() {
    local source_dir=$1
    local target_dir=$2
    
    mkdir -p "$target_dir"
    
    # 只复制必需的安全配置文件
    copy_if_exists "$source_dir/java.security" "$target_dir/"
    copy_if_exists "$source_dir/java.policy" "$target_dir/"
    copy_if_exists "$source_dir/cacerts" "$target_dir/"
    
    # 移除不必要的策略文件
    rm -f "$target_dir/US_export_policy.jar" 2>/dev/null || true
    rm -f "$target_dir/local_policy.jar" 2>/dev/null || true
}

# 辅助函数：安全复制文件
copy_if_exists() {
    local source=$1
    local target_dir=$2
    
    if [ -e "$source" ]; then
        mkdir -p "$target_dir"
        cp -r "$source" "$target_dir/" 2>/dev/null || true
    fi
}

# 从系统JRE创建最小化JRE
create_jre_from_system() {
    local target_dir=$1
    local target_os=$2
    
    echo "    🔧 从系统JRE创建最小化版本..."
    
    # 获取当前构建机器的操作系统
    local build_os="unknown"
    if [[ "$OSTYPE" == "darwin"* ]]; then
        build_os="macos"
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        build_os="linux"
    elif [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "cygwin" ]]; then
        build_os="windows"
    fi
    
    # 只有当构建平台和目标平台匹配时才使用系统JRE
    if [ "$build_os" != "$target_os" ]; then
        echo "    ⚠️  跨平台构建 ($build_os -> $target_os)，无法使用系统JRE"
        return 1
    fi
    
    # 查找系统Java
    local system_java_home=""
    
    if [ "$target_os" = "macos" ]; then
        # macOS特殊处理
        if command -v /usr/libexec/java_home >/dev/null 2>&1; then
            system_java_home=$(/usr/libexec/java_home 2>/dev/null)
        fi
    fi
    
    # 通用方法查找JAVA_HOME
    if [ -z "$system_java_home" ] && [ -n "$JAVA_HOME" ]; then
        system_java_home="$JAVA_HOME"
    fi
    
    # 从java命令推断JAVA_HOME
    if [ -z "$system_java_home" ] && command -v java >/dev/null 2>&1; then
        local java_path=$(which java)
        if [ -L "$java_path" ]; then
            java_path=$(readlink -f "$java_path" 2>/dev/null || readlink "$java_path")
        fi
        # 尝试从java路径推断JAVA_HOME
        system_java_home=$(dirname $(dirname "$java_path") 2>/dev/null)
    fi
    
    if [ -z "$system_java_home" ] || [ ! -d "$system_java_home" ]; then
        echo "    ❌ 未找到系统JAVA_HOME"
        return 1
    fi
    
    echo "    📁 系统JAVA_HOME: $system_java_home"
    
    # 验证是否为有效的JRE/JDK
    local java_exe="$system_java_home/bin/java"
    if [ ! -f "$java_exe" ]; then
        echo "    ❌ 无效的JAVA_HOME: $java_exe 不存在"
        return 1
    fi
    
    # 创建目标目录
    mkdir -p "$target_dir"
    
    echo "    📋 复制必要的JRE文件..."
    
    # 复制bin目录（只复制必要文件）
    mkdir -p "$target_dir/bin"
    if [ "$target_os" = "windows" ]; then
        copy_if_exists "$system_java_home/bin/java.exe" "$target_dir/bin/"
        copy_if_exists "$system_java_home/bin/javaw.exe" "$target_dir/bin/"
        # 复制必要的DLL
        find "$system_java_home/bin" -name "*.dll" -exec cp {} "$target_dir/bin/" \; 2>/dev/null || true
    else
        copy_if_exists "$system_java_home/bin/java" "$target_dir/bin/"
        chmod +x "$target_dir/bin/java" 2>/dev/null
    fi
    
    # 复制lib目录（选择性复制）
    mkdir -p "$target_dir/lib"
    
    # JDK 8 的核心库文件
    copy_if_exists "$system_java_home/lib/rt.jar" "$target_dir/lib/"
    copy_if_exists "$system_java_home/jre/lib/rt.jar" "$target_dir/lib/"
    
    # 其他重要库
    copy_if_exists "$system_java_home/lib/jce.jar" "$target_dir/lib/"
    copy_if_exists "$system_java_home/lib/jsse.jar" "$target_dir/lib/"
    copy_if_exists "$system_java_home/lib/charsets.jar" "$target_dir/lib/"
    copy_if_exists "$system_java_home/lib/resources.jar" "$target_dir/lib/"
    
    copy_if_exists "$system_java_home/jre/lib/jce.jar" "$target_dir/lib/"
    copy_if_exists "$system_java_home/jre/lib/jsse.jar" "$target_dir/lib/"
    copy_if_exists "$system_java_home/jre/lib/charsets.jar" "$target_dir/lib/"
    copy_if_exists "$system_java_home/jre/lib/resources.jar" "$target_dir/lib/"
    
    # 配置文件
    copy_if_exists "$system_java_home/lib/jvm.cfg" "$target_dir/lib/"
    copy_if_exists "$system_java_home/jre/lib/jvm.cfg" "$target_dir/lib/"
    
    # 安全配置
    copy_if_exists "$system_java_home/lib/security" "$target_dir/lib/"
    copy_if_exists "$system_java_home/jre/lib/security" "$target_dir/lib/"
    
    # 本地库（根据操作系统）
    if [ "$target_os" != "windows" ]; then
        # Unix系统的本地库
        copy_if_exists "$system_java_home/lib/amd64" "$target_dir/lib/"
        copy_if_exists "$system_java_home/lib/server" "$target_dir/lib/"
        copy_if_exists "$system_java_home/jre/lib/amd64" "$target_dir/lib/"
        copy_if_exists "$system_java_home/jre/lib/server" "$target_dir/lib/"
        
        # macOS特殊处理
        if [ "$target_os" = "macos" ]; then
            copy_if_exists "$system_java_home/lib/libserver.dylib" "$target_dir/lib/"
            copy_if_exists "$system_java_home/jre/lib/libserver.dylib" "$target_dir/lib/"
        fi
    fi
    
    # 创建release文件
    create_release_file "$target_dir" "$target_os" "$system_java_home"
    
    # 验证创建的JRE
    if [ -f "$target_dir/bin/java" ] || [ -f "$target_dir/bin/java.exe" ]; then
        echo "    ✅ 最小化JRE创建成功"
        return 0
    else
        echo "    ❌ 最小化JRE创建失败"
        return 1
    fi
}

# 创建release文件
create_release_file() {
    local target_dir=$1
    local os=$2
    local java_home=$3
    
    # 尝试获取Java版本信息
    local java_version="1.8.0_unknown"
    if [ -f "$java_home/bin/java" ]; then
        java_version=$("$java_home/bin/java" -version 2>&1 | head -1 | sed 's/.*"\(.*\)".*/\1/' 2>/dev/null || echo "1.8.0_unknown")
    fi
    
    cat > "$target_dir/release" << EOF
JAVA_VERSION="$java_version"
OS_NAME="$os"
OS_ARCH="x64"
SOURCE="System JRE"
BUILD_TYPE="minimal"
CREATED_BY="LightScript Agent Builder"
EOF
}

# 创建占位符JRE（当系统JRE不可用时）
create_placeholder_jre() {
    local jre_dir=$1
    local os=$2
    
    echo "    🔧 创建占位符JRE结构..."
    
    mkdir -p "$jre_dir/bin"
    mkdir -p "$jre_dir/lib"
    
    if [ "$os" = "windows" ]; then
        # Windows占位符
        cat > "$jre_dir/bin/java.exe" << 'EOF'
@echo off
echo ========================================
echo   LightScript Agent - Java 环境检查
echo ========================================
echo.
echo 错误: 未找到Java运行环境!
echo.
echo 请安装Java 8或更高版本:
echo   1. 访问: https://adoptium.net/temurin/releases/
echo   2. 下载适合您系统的Java 8 JRE
echo   3. 安装后重新运行此脚本
echo.
echo 或者使用包管理器安装:
echo   Chocolatey: choco install openjdk8
echo   Scoop:      scoop install openjdk8
echo.
pause
exit /b 1
EOF
    else
        # Unix占位符
        cat > "$jre_dir/bin/java" << 'EOF'
#!/bin/bash
echo "========================================"
echo "  LightScript Agent - Java 环境检查"
echo "========================================"
echo ""
echo "错误: 未找到Java运行环境!"
echo ""
echo "请安装Java 8或更高版本:"
echo ""
if [[ "$OSTYPE" == "darwin"* ]]; then
    echo "macOS 安装方法:"
    echo "  brew install openjdk@8"
    echo "  或访问: https://adoptium.net/temurin/releases/"
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    echo "Linux 安装方法:"
    echo "  Ubuntu/Debian: sudo apt install openjdk-8-jre"
    echo "  CentOS/RHEL:   sudo yum install java-1.8.0-openjdk"
    echo "  或访问: https://adoptium.net/temurin/releases/"
fi
echo ""
exit 1
EOF
        chmod +x "$jre_dir/bin/java"
    fi
    
    # 创建占位符库文件
    echo "# Placeholder JRE Library" > "$jre_dir/lib/rt.jar"
    
    # 创建版本信息
    cat > "$jre_dir/release" << EOF
JAVA_VERSION="placeholder"
OS_NAME="$os"
OS_ARCH="x64"
SOURCE="Placeholder"
BUILD_TYPE="placeholder"
CREATED_BY="LightScript Agent Builder"
EOF

    echo "    ⚠️  已创建占位符JRE，用户需要安装真实的Java环境"
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
        (cd "$temp_dir" && zip -r "$RELEASE_DIR/${package_name}.zip" .)
    else
        # Unix: 创建 tar.gz 包
        (cd "$temp_dir" && tar -czf "$RELEASE_DIR/${package_name}.tar.gz" .)
    fi
}
# 主构建流程
echo "🔨 开始构建各平台安装包..."
echo ""

# 构建 Windows x64 包
build_package "windows" "x64" "https://download.bell-sw.com/java/8u482+10/bellsoft-jre8u482+10-windows-amd64.zip"

# 构建 Linux x64 包  
build_package "linux" "x64" "https://download.bell-sw.com/java/8u482+10/bellsoft-jre8u482+10-linux-amd64.tar.gz"

# 构建 macOS x64 包
build_package "macos" "x64" "https://download.bell-sw.com/java/8u482+10/bellsoft-jre8u482+10-macos-amd64.tar.gz"

# 构建 macOS ARM64 包 (Apple Silicon)
build_package "macos" "arm64" "https://download.bell-sw.com/java/8u482+10/bellsoft-jre8u482+10-macos-aarch64.tar.gz"

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