#!/bin/bash

# LightScript 完整测试脚本
# 启动所有服务并运行自动化测试

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=========================================="
echo "LightScript 完整测试流程"
echo -e "==========================================${NC}"
echo ""

# 检查是否已经有服务在运行
check_port() {
    local port=$1
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

# 停止所有服务
cleanup() {
    echo -e "${YELLOW}清理环境...${NC}"
    ./scripts/mac/stop-all.sh 2>/dev/null || true
    sleep 2
}

# 设置清理陷阱
trap cleanup EXIT

# 1. 清理现有服务
echo -e "${YELLOW}步骤 1: 清理现有服务${NC}"
cleanup

# 2. 启动服务器
echo -e "${YELLOW}步骤 2: 启动服务器${NC}"
./scripts/mac/start-server.sh

# 等待服务器启动
echo "等待服务器启动..."
for i in {1..30}; do
    if curl -s -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo -e "${GREEN}✅ 服务器已启动${NC}"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "${RED}❌ 服务器启动超时${NC}"
        exit 1
    fi
    sleep 2
done

# 3. 启动前端（可选）
echo -e "${YELLOW}步骤 3: 启动前端（可选）${NC}"
if command -v npm &> /dev/null; then
    ./scripts/mac/start-modern-web.sh &
    FRONTEND_PID=$!
    
    echo "等待前端启动..."
    sleep 10
    
    if check_port 3000; then
        echo -e "${GREEN}✅ 前端已启动${NC}"
    else
        echo -e "${YELLOW}⚠️  前端启动失败，继续测试${NC}"
    fi
else
    echo -e "${YELLOW}⚠️  npm 未安装，跳过前端启动${NC}"
fi

# 4. 运行测试
echo ""
echo -e "${YELLOW}步骤 4: 运行自动化测试${NC}"
echo ""
./scripts/mac/test-multi-target.sh

TEST_EXIT_CODE=$?

# 5. 显示结果
echo ""
if [ $TEST_EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}=========================================="
    echo "✅ 所有测试通过！"
    echo -e "==========================================${NC}"
else
    echo -e "${RED}=========================================="
    echo "❌ 测试失败"
    echo -e "==========================================${NC}"
fi

exit $TEST_EXIT_CODE
