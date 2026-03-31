#!/bin/bash

# LightScript Agent 发布包构建脚本 (简化版)
# 构建包含完整 JRE 的跨平台 Agent 安装包

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RELEASE_DIR="$SCRIPT_DIR/release"
JRE_CACHE_DIR="$RELEASE_DIR/jre8"
VERSION_HELPER="$SCRIPT_DIR/scripts/get-agent-version.sh"

# 从 pom.xml 获取 Agent 模块版本号
VERSION=$(bash "$VERSION_HELPER")
if [ -z "$VERSION" ]; then
    VERSION="0.4.3"  # 默认版本
fi

echo "🚀 开始构建 LightScript Agent 发布包..."
echo "📁 项目根目录: $PROJECT_ROOT"
echo "📦 发布目录: $RELEASE_DIR"
echo "🏷️  版本: $VERSION"
echo ""

build_upgrader() {
    echo "🔧 构建最新 Upgrader..."
    (cd "$PROJECT_ROOT/upgrader" && mvn -DskipTests package -q)
    cp "$PROJECT_ROOT/upgrader/target/upgrader.jar" "$PROJECT_ROOT/upgrader/upgrader.jar"
    echo "✅ Upgrader 构建完成"
    echo ""
}

# 检查和准备JRE文件
prepare_jre_files() {
    echo "🔍 检查JRE缓存..."
    
    # JRE文件列表
    local jre_files=(
        "windows-x64:bellsoft-jre8u482+10-windows-amd64.zip"
        "windows-x86:zulu8.92.0.21-ca-jre8.0.482-win_i686.zip"
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
build_upgrader

# 清理发布目录（保留JRE文件）
echo "🧹 清理旧的安装包..."
rm -f "$RELEASE_DIR"/viberemote-agent-*.tar.gz
rm -f "$RELEASE_DIR"/viberemote-agent-*.zip
rm -f "$RELEASE_DIR"/lightscript-agent-*.tar.gz
rm -f "$RELEASE_DIR"/lightscript-agent-*.zip
mkdir -p "$RELEASE_DIR"
mkdir -p "$JRE_CACHE_DIR"

generate_release_manifest() {
    local release_date
    release_date=$(date '+%Y-%m-%d')
    cat > "$RELEASE_DIR/version.json" <<EOF
{
  "version": "$VERSION",
  "releaseDate": "$release_date",
  "packages": {
    "windows-x64": "viberemote-agent-$VERSION-windows-x64.zip",
    "windows-x86": "viberemote-agent-$VERSION-windows-x86.zip",
    "linux-x64": "viberemote-agent-$VERSION-linux-x64.tar.gz",
    "macos-x64": "viberemote-agent-$VERSION-macos-x64.tar.gz",
    "macos-arm64": "viberemote-agent-$VERSION-macos-arm64.tar.gz"
  }
}
EOF
    echo "📝 版本清单已生成: $RELEASE_DIR/version.json"
}

generate_release_manifest

# 检查必要文件
echo "🔍 检查必要文件..."
AGENT_JAR="$SCRIPT_DIR/target/agent-${VERSION}-jar-with-dependencies.jar"
UPGRADER_JAR="$PROJECT_ROOT/upgrader/target/upgrader.jar"

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
    local SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/scripts"

    if [ "$os" = "windows" ]; then
        cp "$SCRIPTS_DIR/windows/start-agent.bat" "$target_dir/"
        cp "$SCRIPTS_DIR/windows/stop-agent.bat" "$target_dir/"
        cp "$SCRIPTS_DIR/windows/check-status.bat" "$target_dir/"
        cp "$SCRIPTS_DIR/windows/install-autostart.bat" "$target_dir/"
        cp "$SCRIPTS_DIR/windows/uninstall-autostart.bat" "$target_dir/"
        cp "$SCRIPTS_DIR/windows/uninstall.bat" "$target_dir/"

        # Convert to CRLF
        echo "    🔄 Converting bat files to CRLF..."
        for bat_file in "$target_dir"/*.bat; do
            if [ -f "$bat_file" ]; then
                if ! grep -q $'\r' "$bat_file" 2>/dev/null; then
                    sed -i.bak 's/$/\r/' "$bat_file" 2>/dev/null && rm -f "${bat_file}.bak"
                fi
                grep -q $'\r' "$bat_file" 2>/dev/null                     && echo "    ✅ $(basename "$bat_file") CRLF ok"                     || echo "    ❌ $(basename "$bat_file") still LF"
            fi
        done
    else
        cp "$SCRIPTS_DIR/unix/start-agent.sh" "$target_dir/"
        cp "$SCRIPTS_DIR/unix/stop-agent.sh" "$target_dir/"
        cp "$SCRIPTS_DIR/unix/check-status.sh" "$target_dir/"
        cp "$SCRIPTS_DIR/unix/uninstall.sh" "$target_dir/"
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
        # 安装包内不再写死服务器地址，实际安装时由安装脚本或部署工具写入
        cat > "$temp_dir/agent.properties" << 'EOF'
# LightScript Agent 配置文件

# 服务器配置（由安装脚本或部署工具写入）
server.url=
register.token=

# Agent配置
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
build_package "windows" "x86" "zulu8.92.0.21-ca-jre8.0.482-win_i686.zip"
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
echo "  • Windows: viberemote-agent-${VERSION}-windows-x86.zip"
echo "  • Linux:   viberemote-agent-${VERSION}-linux-x64.tar.gz" 
echo "  • macOS:   viberemote-agent-${VERSION}-macos-x64.tar.gz"
echo "  • macOS:   viberemote-agent-${VERSION}-macos-arm64.tar.gz"
echo ""
echo "✅ 构建完成! 安装包已保存到: $RELEASE_DIR"
