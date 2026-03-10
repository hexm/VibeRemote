#!/bin/bash

echo "=========================================="
echo "LightScript 文件管理功能自动化测试"
echo "=========================================="

# 检查依赖
echo "检查测试环境..."

# 检查Node.js
if ! command -v node &> /dev/null; then
    echo "❌ Node.js 未安装"
    exit 1
fi

# 检查npm
if ! command -v npm &> /dev/null; then
    echo "❌ npm 未安装"
    exit 1
fi

echo "✓ Node.js 和 npm 已安装"

# 进入测试目录
cd "$(dirname "$0")"

# 检查package.json
if [ ! -f "package.json" ]; then
    echo "❌ 未找到 package.json"
    exit 1
fi

# 安装依赖（如果需要）
if [ ! -d "node_modules" ]; then
    echo "安装测试依赖..."
    npm install
fi

# 创建临时目录
mkdir -p temp
mkdir -p test-results

echo "✓ 测试环境准备完成"

# 检查服务状态
echo ""
echo "检查服务状态..."

# 检查后端服务
if curl -s http://localhost:8080/actuator/health > /dev/null; then
    echo "✓ 后端服务 (8080) 运行正常"
else
    echo "❌ 后端服务 (8080) 未运行"
    echo "请先启动后端服务: ./scripts/mac/start-server.sh"
    exit 1
fi

# 检查前端服务
if curl -s http://localhost:3001 > /dev/null; then
    echo "✓ 前端服务 (3001) 运行正常"
else
    echo "❌ 前端服务 (3001) 未运行"
    echo "请先启动前端服务: cd web-modern && npm run dev"
    exit 1
fi

echo ""
echo "=========================================="
echo "开始执行文件管理功能测试"
echo "=========================================="

# 运行简单测试
echo ""
echo "1. 运行核心功能测试..."
npx playwright test file-management-simple.spec.js --reporter=list

if [ $? -eq 0 ]; then
    echo "✅ 核心功能测试通过"
else
    echo "❌ 核心功能测试失败"
    echo "查看详细报告: npx playwright show-report"
    exit 1
fi

# 运行完整测试
echo ""
echo "2. 运行完整功能测试..."
npx playwright test file-management.spec.js --reporter=list

if [ $? -eq 0 ]; then
    echo "✅ 完整功能测试通过"
else
    echo "❌ 完整功能测试失败"
    echo "查看详细报告: npx playwright show-report"
    exit 1
fi

echo ""
echo "=========================================="
echo "✅ 文件管理功能测试全部完成！"
echo "=========================================="
echo ""
echo "测试结果:"
echo "- 核心功能测试: ✅ 通过"
echo "- 完整功能测试: ✅ 通过"
echo ""
echo "查看详细报告:"
echo "  npx playwright show-report"
echo ""
echo "查看测试截图:"
echo "  ls -la test-results/"
echo ""
echo "=========================================="