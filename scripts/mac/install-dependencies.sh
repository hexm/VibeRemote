#!/bin/bash

echo "========================================"
echo "LightScript 依赖安装脚本"
echo "========================================"
echo

# 检查是否安装了 Homebrew
if ! command -v brew &> /dev/null; then
    echo "❌ Homebrew 未安装"
    echo "正在安装 Homebrew..."
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    
    if [ $? -ne 0 ]; then
        echo "❌ Homebrew 安装失败"
        echo "请手动安装 Homebrew: https://brew.sh"
        exit 1
    fi
    
    echo "✅ Homebrew 安装成功"
else
    echo "✅ Homebrew 已安装"
fi

echo

# 检查并安装 Java
if ! command -v java &> /dev/null; then
    echo "❌ Java 未安装"
    echo "正在安装 OpenJDK 8..."
    brew install openjdk@8
    
    if [ $? -ne 0 ]; then
        echo "❌ Java 安装失败"
        exit 1
    fi
    
    # 设置 Java 环境变量
    echo 'export PATH="/usr/local/opt/openjdk@8/bin:$PATH"' >> ~/.zshrc
    echo 'export JAVA_HOME="/usr/local/opt/openjdk@8"' >> ~/.zshrc
    
    # 为当前会话设置环境变量
    export PATH="/usr/local/opt/openjdk@8/bin:$PATH"
    export JAVA_HOME="/usr/local/opt/openjdk@8"
    
    echo "✅ Java 安装成功"
else
    echo "✅ Java 已安装: $(java -version 2>&1 | head -n 1)"
fi

echo

# 检查并安装 Maven
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven 未安装"
    echo "正在安装 Maven..."
    brew install maven
    
    if [ $? -ne 0 ]; then
        echo "❌ Maven 安装失败"
        exit 1
    fi
    
    echo "✅ Maven 安装成功"
else
    echo "✅ Maven 已安装: $(mvn -version | head -n 1)"
fi

echo

# 检查并安装 Node.js (可选，用于前端服务)
if ! command -v node &> /dev/null; then
    echo "❌ Node.js 未安装"
    echo "正在安装 Node.js..."
    brew install node
    
    if [ $? -ne 0 ]; then
        echo "⚠️ Node.js 安装失败，将使用 Python 作为前端服务器"
    else
        echo "✅ Node.js 安装成功"
        
        # 安装 http-server
        echo "正在安装 http-server..."
        npm install -g http-server
        echo "✅ http-server 安装成功"
    fi
else
    echo "✅ Node.js 已安装: $(node -version)"
    
    # 检查 http-server
    if ! command -v http-server &> /dev/null; then
        echo "正在安装 http-server..."
        npm install -g http-server
        echo "✅ http-server 安装成功"
    else
        echo "✅ http-server 已安装"
    fi
fi

echo

# 检查 Python (备选前端服务器)
if command -v python3 &> /dev/null; then
    echo "✅ Python3 已安装: $(python3 --version)"
elif command -v python &> /dev/null; then
    echo "✅ Python 已安装: $(python --version)"
else
    echo "⚠️ Python 未安装，正在安装..."
    brew install python
    echo "✅ Python 安装成功"
fi

echo
echo "========================================"
echo "🎉 依赖安装完成！"
echo "========================================"
echo
echo "已安装的软件:"
echo "- Java: $(java -version 2>&1 | head -n 1 | cut -d'"' -f2)"
echo "- Maven: $(mvn -version 2>/dev/null | head -n 1 | cut -d' ' -f3)"
if command -v node &> /dev/null; then
    echo "- Node.js: $(node -version)"
fi
if command -v python3 &> /dev/null; then
    echo "- Python: $(python3 --version)"
fi

echo
echo "⚠️ 重要提示:"
echo "请重新打开终端或运行以下命令使环境变量生效:"
echo "source ~/.zshrc"
echo
echo "然后你就可以运行 LightScript 项目了:"
echo "./scripts/mac/start-all.sh"
echo