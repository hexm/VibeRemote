#!/bin/bash

# 获取脚本所在目录的父目录（项目根目录）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
WEB_MODERN_DIR="$PROJECT_ROOT/web-modern"

echo "========================================"
echo "LightScript 现代化前端启动脚本"
echo "========================================"
echo

if [ ! -d "$WEB_MODERN_DIR" ]; then
    echo "[ERROR] web-modern directory not found: $WEB_MODERN_DIR"
    exit 1
fi

cd "$WEB_MODERN_DIR"

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
            echo "[WARN] 依赖安装失败，尝试备选方案..."
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

# 方案4: 创建并启动静态展示页面
echo "[WARN] 未找到 Node.js 环境和预构建版本"
echo "       正在创建静态展示页面..."
echo

# 创建静态目录
mkdir -p static

# 创建现代化的展示页面
cat > static/index.html << 'EOF'
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>LightScript - 现代化管理平台</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <style>
        @import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap');
        body { font-family: 'Inter', sans-serif; }
        .hero-gradient { background: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 50%, #7c3aed 100%); }
        .glass-card { background: rgba(255, 255, 255, 0.95); backdrop-filter: blur(20px); }
        .animate-float { animation: float 6s ease-in-out infinite; }
        @keyframes float {
            0%, 100% { transform: translateY(0px); }
            50% { transform: translateY(-20px); }
        }
        .animate-pulse-slow { animation: pulse 4s cubic-bezier(0.4, 0, 0.6, 1) infinite; }
        .feature-card:hover { transform: translateY(-5px); transition: all 0.3s ease; }
    </style>
</head>
<body class="overflow-x-hidden">
    <!-- 背景装饰 -->
    <div class="fixed inset-0 overflow-hidden pointer-events-none">
        <div class="absolute -top-40 -right-40 w-80 h-80 bg-blue-300 rounded-full mix-blend-multiply filter blur-xl opacity-70 animate-pulse-slow"></div>
        <div class="absolute -bottom-40 -left-40 w-80 h-80 bg-purple-300 rounded-full mix-blend-multiply filter blur-xl opacity-70 animate-pulse-slow" style="animation-delay: 2s;"></div>
        <div class="absolute top-40 left-40 w-80 h-80 bg-indigo-300 rounded-full mix-blend-multiply filter blur-xl opacity-70 animate-pulse-slow" style="animation-delay: 4s;"></div>
    </div>

    <div class="min-h-screen hero-gradient flex items-center justify-center relative z-10">
        <div class="glass-card p-8 rounded-3xl shadow-2xl max-w-4xl w-full mx-4">
            <!-- 头部 -->
            <div class="text-center mb-12">
                <div class="w-20 h-20 bg-gradient-to-r from-blue-500 to-purple-600 rounded-full flex items-center justify-center mx-auto mb-6 animate-float">
                    <span class="text-3xl">🚀</span>
                </div>
                <h1 class="text-4xl font-bold bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-transparent mb-4">
                    LightScript
                </h1>
                <p class="text-xl text-gray-600 mb-2">现代化分布式脚本管理平台</p>
                <p class="text-sm text-gray-500">Modern Distributed Script Management Platform</p>
            </div>
            
            <!-- 功能卡片 -->
            <div class="grid md:grid-cols-3 gap-6 mb-8">
                <div class="feature-card p-6 bg-gradient-to-br from-blue-50 to-blue-100 rounded-2xl border border-blue-200">
                    <div class="w-12 h-12 bg-blue-500 rounded-xl flex items-center justify-center mb-4">
                        <span class="text-white text-xl">💻</span>
                    </div>
                    <h3 class="font-semibold text-blue-800 mb-2">完整体验</h3>
                    <p class="text-sm text-blue-700 mb-3">安装 Node.js 获得最佳体验</p>
                    <code class="text-xs bg-blue-200 text-blue-800 px-2 py-1 rounded block">brew install node</code>
                </div>
                
                <div class="feature-card p-6 bg-gradient-to-br from-green-50 to-green-100 rounded-2xl border border-green-200">
                    <div class="w-12 h-12 bg-green-500 rounded-xl flex items-center justify-center mb-4">
                        <span class="text-white text-xl">🌐</span>
                    </div>
                    <h3 class="font-semibold text-green-800 mb-2">后端服务</h3>
                    <p class="text-sm text-green-700 mb-3">API服务正在运行中</p>
                    <a href="http://localhost:8080" target="_blank" 
                       class="inline-block text-xs bg-green-500 text-white px-3 py-1 rounded hover:bg-green-600 transition-colors">
                        访问后端 API
                    </a>
                </div>
                
                <div class="feature-card p-6 bg-gradient-to-br from-yellow-50 to-yellow-100 rounded-2xl border border-yellow-200">
                    <div class="w-12 h-12 bg-yellow-500 rounded-xl flex items-center justify-center mb-4">
                        <span class="text-white text-xl">📱</span>
                    </div>
                    <h3 class="font-semibold text-yellow-800 mb-2">旧版界面</h3>
                    <p class="text-sm text-yellow-700 mb-3">Vue版本仍可使用</p>
                    <a href="http://localhost:3000" target="_blank" 
                       class="inline-block text-xs bg-yellow-500 text-white px-3 py-1 rounded hover:bg-yellow-600 transition-colors">
                        访问旧版前端
                    </a>
                </div>
            </div>
            
            <!-- 特性展示 -->
            <div class="bg-gradient-to-r from-gray-50 to-gray-100 rounded-2xl p-6 mb-8">
                <h3 class="text-lg font-semibold text-gray-800 mb-4 text-center">🎨 新版本特色</h3>
                <div class="grid md:grid-cols-2 gap-4 text-sm">
                    <div class="flex items-center space-x-2">
                        <span class="w-2 h-2 bg-blue-500 rounded-full"></span>
                        <span class="text-gray-700">React 18 + Ant Design 5</span>
                    </div>
                    <div class="flex items-center space-x-2">
                        <span class="w-2 h-2 bg-green-500 rounded-full"></span>
                        <span class="text-gray-700">Tailwind CSS 样式框架</span>
                    </div>
                    <div class="flex items-center space-x-2">
                        <span class="w-2 h-2 bg-purple-500 rounded-full"></span>
                        <span class="text-gray-700">Vite 超快构建工具</span>
                    </div>
                    <div class="flex items-center space-x-2">
                        <span class="w-2 h-2 bg-orange-500 rounded-full"></span>
                        <span class="text-gray-700">交互式图表和动画</span>
                    </div>
                </div>
            </div>
            
            <!-- 安装指引 -->
            <div class="bg-white rounded-2xl p-6 border border-gray-200">
                <h3 class="text-lg font-semibold text-gray-800 mb-4">🛠️ 快速安装</h3>
                <div class="space-y-3 text-sm">
                    <div class="flex items-start space-x-3">
                        <span class="flex-shrink-0 w-6 h-6 bg-blue-500 text-white rounded-full flex items-center justify-center text-xs font-bold">1</span>
                        <div>
                            <p class="font-medium text-gray-800">安装 Node.js</p>
                            <code class="text-xs bg-gray-100 px-2 py-1 rounded mt-1 block">brew install node</code>
                        </div>
                    </div>
                    <div class="flex items-start space-x-3">
                        <span class="flex-shrink-0 w-6 h-6 bg-green-500 text-white rounded-full flex items-center justify-center text-xs font-bold">2</span>
                        <div>
                            <p class="font-medium text-gray-800">启动现代化前端</p>
                            <code class="text-xs bg-gray-100 px-2 py-1 rounded mt-1 block">./scripts/mac/start-modern-web.sh</code>
                        </div>
                    </div>
                    <div class="flex items-start space-x-3">
                        <span class="flex-shrink-0 w-6 h-6 bg-purple-500 text-white rounded-full flex items-center justify-center text-xs font-bold">3</span>
                        <div>
                            <p class="font-medium text-gray-800">访问新界面</p>
                            <code class="text-xs bg-gray-100 px-2 py-1 rounded mt-1 block">http://localhost:3001</code>
                        </div>
                    </div>
                </div>
            </div>
            
            <!-- 底部信息 -->
            <div class="mt-8 text-center border-t border-gray-200 pt-6">
                <p class="text-sm text-gray-600 mb-2">
                    <strong>默认账号:</strong> admin / admin123 | user / user123
                </p>
                <p class="text-xs text-gray-500">
                    © 2024 LightScript. 现代化分布式脚本管理平台
                </p>
            </div>
        </div>
    </div>
</body>
</html>
EOF

# 使用Python启动静态版本
if command -v python3 &> /dev/null; then
    echo "[INFO] 使用 Python3 启动静态展示页面"
    echo "       访问地址: http://localhost:3001"
    echo "       特性: 安装指引、功能介绍"
    echo "       按 Ctrl+C 停止服务"
    echo "========================================"
    cd static
    python3 -m http.server 3001
    exit 0
elif command -v python &> /dev/null; then
    echo "[INFO] 使用 Python 启动静态展示页面"
    echo "       访问地址: http://localhost:3001"
    echo "       特性: 安装指引、功能介绍"
    echo "       按 Ctrl+C 停止服务"
    echo "========================================"
    cd static
    python -m http.server 3001
    exit 0
fi

# 最后的备选方案
echo "[ERROR] 无法启动现代化前端服务"
echo "        需要以下任一环境："
echo "        1. Node.js + npm (推荐，完整功能)"
echo "        2. Python 3 (基础展示)"
echo "        3. Python 2 (基础展示)"
echo
echo "安装建议:"
echo "- 安装 Node.js: brew install node"
echo "- 安装 Python: brew install python"
echo "- 或直接在浏览器打开: file://$WEB_MODERN_DIR/static/index.html"
echo
echo "========================================"
exit 1