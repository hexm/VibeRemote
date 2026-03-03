#!/bin/bash

# LightScript E2E 测试运行脚本

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=========================================="
echo "LightScript E2E 自动化测试"
echo -e "==========================================${NC}"
echo ""

# 检查是否在正确的目录
if [ ! -f "package.json" ]; then
    echo -e "${RED}错误: 请在 e2e-tests 目录下运行此脚本${NC}"
    exit 1
fi

# 检查 node_modules 是否存在
if [ ! -d "node_modules" ]; then
    echo -e "${YELLOW}首次运行，正在安装依赖...${NC}"
    npm install
    echo -e "${GREEN}✅ 依赖安装完成${NC}"
    echo ""
fi

# 检查 Playwright 浏览器是否安装
if [ ! -d "node_modules/@playwright" ]; then
    echo -e "${YELLOW}正在安装 Playwright 浏览器...${NC}"
    npx playwright install chromium
    echo -e "${GREEN}✅ 浏览器安装完成${NC}"
    echo ""
fi

# 检查服务器是否可访问
echo -e "${YELLOW}检查服务器连接...${NC}"
if curl -s -f http://8.138.114.34 > /dev/null; then
    echo -e "${GREEN}✅ 服务器连接正常${NC}"
else
    echo -e "${RED}❌ 无法连接到服务器 http://8.138.114.34${NC}"
    exit 1
fi
echo ""

# 运行测试
echo -e "${BLUE}开始运行测试...${NC}"
echo ""

# 根据参数选择运行模式
case "${1:-}" in
    "headed")
        echo -e "${YELLOW}运行模式: 有头模式（可见浏览器）${NC}"
        npm run test:headed
        ;;
    "debug")
        echo -e "${YELLOW}运行模式: 调试模式${NC}"
        npm run test:debug
        ;;
    "ui")
        echo -e "${YELLOW}运行模式: UI交互模式${NC}"
        npm run test:ui
        ;;
    *)
        echo -e "${YELLOW}运行模式: 无头模式${NC}"
        npm test
        ;;
esac

# 检查测试结果
if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}=========================================="
    echo "✅ 所有测试通过！"
    echo -e "==========================================${NC}"
    echo ""
    echo "查看测试报告:"
    echo "  npm run report"
    echo ""
    echo "查看截图:"
    echo "  ls -la test-results/*.png"
    echo ""
else
    echo ""
    echo -e "${RED}=========================================="
    echo "❌ 测试失败"
    echo -e "==========================================${NC}"
    echo ""
    echo "查看详细报告:"
    echo "  npm run report"
    echo ""
    echo "查看失败截图:"
    echo "  ls -la test-results/*.png"
    echo ""
    exit 1
fi
