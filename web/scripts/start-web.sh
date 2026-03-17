#!/bin/bash

# 获取脚本所在目录的父目录（web项目根目录）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WEB_DIR="$(dirname "$SCRIPT_DIR")"

echo "========================================"
echo "LightScript Web 前端启动脚本"
echo "========================================"
echo

if [ ! -d "$WEB_DIR" ]; then
    echo "[ERROR] web directory not found: $WEB_DIR"
    exit 1
fi

cd "$WEB_DIR"

# 方案1: 优先使用 Node.js + Vite (最佳体验)
if command -v node &> /dev/null && command -v npm &> /dev/null; then
    echo "[INFO] 检测到 Node.js 环境"
    echo "       Node.js 版本: $(node --version)"
    echo "       npm 版本: $(npm --version)"
    echo
    
    # 检查是否已安装依赖
    if [ ! -d "node_modules" ]; then
        echo "[INFO] 安装依赖包..."
        npm install
        if [ $? -ne 0 ]; then
            echo "[ERROR] 依赖安装失败，尝试备选方案..."
        else
            echo "[INFO] 依赖安装完成"
            echo
            echo "[INFO] 启动 Vite 开发服务器 (最佳体验)"
            echo "       访问地址: http://localhost:3001"
            echo "       特性: 热重载、快速构建、完整功能"
            echo "       按 Ctrl+C 停止服务"
            echo "========================================"
            npm run dev
            exit 0
        fi
    else
        echo "[INFO] 启动 Vite 开发服务器 (最佳体验)"
        echo "       访问地址: http://localhost:3001"
        echo "       特性: 热重载、快速构建、完整功能"
        echo "       按 Ctrl+C 停止服务"
        echo "========================================"
        npm run dev
        exit 0
    fi
fi

# 方案2: 使用预构建版本 + http-server (如果存在)
if [ -d "dist" ] && command -v http-server &> /dev/null; then
    echo "[INFO] 使用预构建版本 + http-server"
    echo "       访问地址: http://localhost:3001"
    echo "       特性: 生产版本、快速加载"
    echo "       按 Ctrl+C 停止服务"
    echo "========================================"
    http-server dist -p 3001 -c-1 --cors
    exit 0
fi

# 方案3: 使用预构建版本 + Python (备选)
if [ -d "dist" ]; then
    if command -v python3 &> /dev/null; then
        echo "[INFO] 使用预构建版本 + Python3 http.server"
        echo "       访问地址: http://localhost:3001"
        echo "       特性: 生产版本、基础功能"
        echo "       按 Ctrl+C 停止服务"
        echo "========================================"
        cd dist
        python3 -m http.server 3001
        exit 0
    elif command -v python &> /dev/null; then
        echo "[INFO] 使用预构建版本 + Python http.server"
        echo "       访问地址: http://localhost:3001"
        echo "       特性: 生产版本、基础功能"
        echo "       按 Ctrl+C 停止服务"
        echo "========================================"
        cd dist
        python -m http.server 3001
        exit 0
    fi
fi

# 方案4: 创建预构建版本
echo "[WARN] 未找到 Node.js 环境和预构建版本"
echo "       正在创建静态版本..."
echo

# 创建一个简化的静态版本
mkdir -p static
cat > static/index.html << 'EOF'
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>LightScript - 管理平台</title>
    <script src="https://unpkg.com/react@18/umd/react.production.min.js"></script>
    <script src="https://unpkg.com/react-dom@18/umd/react-dom.production.min.js"></script>
    <script src="https://unpkg.com/antd@5.12.8/dist/antd.min.js"></script>
    <link rel="stylesheet" href="https://unpkg.com/antd@5.12.8/dist/reset.css">
    <script src="https://unpkg.com/axios@1.6.2/dist/axios.min.js"></script>
    <script src="https://cdn.tailwindcss.com"></script>
    <style>
        body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; }
        .hero-gradient { background: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 50%, #7c3aed 100%); }
        .glass-card { background: rgba(255, 255, 255, 0.9); backdrop-filter: blur(10px); }
    </style>
</head>
<body>
    <div id="root">
        <div class="min-h-screen hero-gradient flex items-center justify-center">
            <div class="glass-card p-8 rounded-2xl shadow-2xl max-w-md w-full mx-4">
                <div class="text-center mb-8">
                    <div class="w-16 h-16 bg-white rounded-full flex items-center justify-center mx-auto mb-4">
                        <span class="text-2xl">🚀</span>
                    </div>
                    <h1 class="text-3xl font-bold text-gray-800 mb-2">LightScript</h1>
                    <p class="text-gray-600">分布式脚本管理平台</p>
                </div>
                
                <div class="space-y-4">
                    <div class="p-4 bg-blue-50 rounded-lg border-l-4 border-blue-500">
                        <h3 class="font-semibold text-blue-800 mb-2">🎯 完整体验</h3>
                        <p class="text-sm text-blue-700">安装 Node.js 获得最佳体验：</p>
                        <code class="text-xs bg-blue-100 px-2 py-1 rounded mt-1 block">brew install node</code>
                    </div>
                    
                    <div class="p-4 bg-green-50 rounded-lg border-l-4 border-green-500">
                        <h3 class="font-semibold text-green-800 mb-2">🌐 当前可用</h3>
                        <p class="text-sm text-green-700 mb-2">后端服务正在运行：</p>
                        <a href="http://localhost:8080" target="_blank" 
                           class="text-sm bg-green-500 text-white px-3 py-1 rounded hover:bg-green-600 transition-colors">
                            访问后端 API
                        </a>
                    </div>
                    
                    <div class="p-4 bg-purple-50 rounded-lg border-l-4 border-purple-500">
                        <h3 class="font-semibold text-purple-800 mb-2">📱 门户网站</h3>
                        <p class="text-sm text-purple-700 mb-2">安装指南和文档：</p>
                        <a href="http://localhost:8002" target="_blank" 
                           class="text-sm bg-purple-500 text-white px-3 py-1 rounded hover:bg-purple-600 transition-colors">
                            访问门户网站
                        </a>
                    </div>
                </div>
                
                <div class="mt-6 text-center">
                    <p class="text-xs text-gray-500">
                        默认账号: admin / admin123
                    </p>
                </div>
            </div>
        </div>
    </div>
</body>
</html>
EOF

# 使用Python启动静态版本
if command -v python3 &> /dev/null; then
    echo "[INFO] 使用 Python3 启动静态版本"
    echo "       访问地址: http://localhost:3001"
    echo "       特性: 基础展示、安装指引"
    echo "       按 Ctrl+C 停止服务"
    echo "========================================"
    cd static
    python3 -m http.server 3001
    exit 0
elif command -v python &> /dev/null; then
    echo "[INFO] 使用 Python 启动静态版本"
    echo "       访问地址: http://localhost:3001"
    echo "       特性: 基础展示、安装指引"
    echo "       按 Ctrl+C 停止服务"
    echo "========================================"
    cd static
    python -m http.server 3001
    exit 0
fi

# 最后的备选方案
echo "[ERROR] 无法启动前端服务"
echo "        需要以下任一环境："
echo "        1. Node.js + npm (推荐)"
echo "        2. Python 3"
echo "        3. Python 2"
echo
echo "安装建议:"
echo "- 安装 Node.js: brew install node"
echo "- 安装 Python: brew install python"
echo "- 或直接在浏览器打开: file://$WEB_DIR/static/index.html"
echo
echo "========================================"
exit 1