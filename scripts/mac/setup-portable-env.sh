#!/bin/bash

echo "========================================"
echo "LightScript 便携式环境安装脚本"
echo "========================================"
echo

# 获取脚本所在目录的父目录（项目根目录）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

# 创建本地工具目录
TOOLS_DIR="$PROJECT_ROOT/tools"
mkdir -p "$TOOLS_DIR"

echo "正在创建便携式开发环境..."
echo "安装目录: $TOOLS_DIR"
echo

# 1. 下载并安装 OpenJDK 8
echo "1. 安装 OpenJDK 8..."
JAVA_DIR="$TOOLS_DIR/java"
if [ ! -d "$JAVA_DIR" ]; then
    echo "请手动下载 OpenJDK 8:"
    echo "1. 访问: https://adoptium.net/temurin/releases/"
    echo "2. 选择 Version: 8, Operating System: macOS"
    echo "3. 下载 .tar.gz 文件"
    echo "4. 解压到: $JAVA_DIR"
    echo
    read -p "下载完成后按 Enter 继续..."
    
    if [ ! -d "$JAVA_DIR" ]; then
        echo "❌ Java 目录不存在，请确保已解压到正确位置"
        exit 1
    fi
else
    echo "✅ Java 已安装"
fi

# 2. 下载并安装 Maven
echo "2. 安装 Maven..."
MAVEN_DIR="$TOOLS_DIR/maven"
if [ ! -d "$MAVEN_DIR" ]; then
    echo "正在下载 Maven..."
    MAVEN_VERSION="3.9.6"
    MAVEN_URL="https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz"
    
    curl -L "$MAVEN_URL" -o "$TOOLS_DIR/maven.tar.gz"
    if [ $? -eq 0 ]; then
        cd "$TOOLS_DIR"
        tar -xzf maven.tar.gz
        mv "apache-maven-$MAVEN_VERSION" maven
        rm maven.tar.gz
        echo "✅ Maven 安装成功"
    else
        echo "❌ Maven 下载失败，请手动下载:"
        echo "1. 访问: https://maven.apache.org/download.cgi"
        echo "2. 下载 apache-maven-x.x.x-bin.tar.gz"
        echo "3. 解压到: $MAVEN_DIR"
        read -p "下载完成后按 Enter 继续..."
    fi
else
    echo "✅ Maven 已安装"
fi

# 3. 创建环境设置脚本
echo "3. 创建环境设置脚本..."
ENV_SCRIPT="$PROJECT_ROOT/set-env.sh"
cat > "$ENV_SCRIPT" << 'EOF'
#!/bin/bash

# LightScript 便携式环境变量设置
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS_DIR="$SCRIPT_DIR/tools"

# 设置 Java 环境
if [ -d "$TOOLS_DIR/java" ]; then
    export JAVA_HOME="$TOOLS_DIR/java"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "✅ Java 环境已设置: $JAVA_HOME"
else
    echo "❌ Java 未找到，请运行 setup-portable-env.sh"
fi

# 设置 Maven 环境
if [ -d "$TOOLS_DIR/maven" ]; then
    export MAVEN_HOME="$TOOLS_DIR/maven"
    export PATH="$MAVEN_HOME/bin:$PATH"
    echo "✅ Maven 环境已设置: $MAVEN_HOME"
else
    echo "❌ Maven 未找到，请运行 setup-portable-env.sh"
fi

# 验证环境
echo
echo "环境验证:"
if command -v java &> /dev/null; then
    echo "Java: $(java -version 2>&1 | head -n 1)"
else
    echo "❌ Java 命令不可用"
fi

if command -v mvn &> /dev/null; then
    echo "Maven: $(mvn -version 2>/dev/null | head -n 1)"
else
    echo "❌ Maven 命令不可用"
fi
EOF

chmod +x "$ENV_SCRIPT"

# 4. 创建便携式启动脚本
echo "4. 创建便携式启动脚本..."
PORTABLE_START="$PROJECT_ROOT/scripts/mac/start-portable.sh"
cat > "$PORTABLE_START" << 'EOF'
#!/bin/bash

echo "========================================"
echo "LightScript 便携式启动"
echo "========================================"

# 获取脚本所在目录的父目录（项目根目录）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

# 设置环境变量
source "$PROJECT_ROOT/set-env.sh"

echo
echo "正在构建项目..."
mvn clean package -DskipTests -q

if [ $? -ne 0 ]; then
    echo "❌ 构建失败"
    exit 1
fi

echo "✅ 构建成功"
echo
echo "正在启动服务器..."
echo "访问地址: http://localhost:8080"
echo "默认账号: admin / admin123"
echo "按 Ctrl+C 停止服务器"
echo

cd server
java -jar target/server-*.jar --spring.profiles.active=dev
EOF

chmod +x "$PORTABLE_START"

echo
echo "========================================"
echo "🎉 便携式环境设置完成！"
echo "========================================"
echo
echo "接下来的步骤:"
echo "1. 如果 Java 需要手动安装，请按提示下载"
echo "2. 运行环境设置: source ./set-env.sh"
echo "3. 启动项目: ./scripts/mac/start-portable.sh"
echo
echo "文件说明:"
echo "- tools/: 本地工具目录"
echo "- set-env.sh: 环境变量设置脚本"
echo "- scripts/mac/start-portable.sh: 便携式启动脚本"
echo