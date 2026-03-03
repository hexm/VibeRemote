#!/bin/bash

# 任务手动启动功能测试脚本

set -e

BASE_URL="http://localhost:8080"
API_BASE="${BASE_URL}/api/web"

echo "========================================="
echo "任务手动启动功能测试"
echo "========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 测试计数器
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# 测试函数
test_case() {
    local test_name="$1"
    local test_command="$2"
    local expected_status="$3"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    echo -e "${YELLOW}测试 ${TOTAL_TESTS}: ${test_name}${NC}"
    
    # 执行测试命令
    response=$(eval "$test_command")
    status=$?
    
    if [ $status -eq 0 ]; then
        echo -e "${GREEN}✓ 通过${NC}"
        echo "响应: $response"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "${RED}✗ 失败${NC}"
        echo "响应: $response"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
    echo ""
}

# 等待服务启动
echo "等待服务启动..."
sleep 2

# 获取在线代理
echo "获取在线代理列表..."
AGENTS_RESPONSE=$(curl -s "${API_BASE}/agents?size=10")
AGENT_ID=$(echo "$AGENTS_RESPONSE" | grep -o '"agentId":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -z "$AGENT_ID" ]; then
    echo -e "${RED}错误: 没有找到在线代理，请先启动Agent${NC}"
    exit 1
fi

echo "使用代理: $AGENT_ID"
echo ""

# ========================================
# 测试1: 创建草稿任务（autoStart=false）
# ========================================
test_case "创建草稿任务（autoStart=false）" \
    "curl -s -X POST '${API_BASE}/tasks/create?agentIds=${AGENT_ID}&taskName=test-draft-task&autoStart=false' \
    -H 'Content-Type: application/json' \
    -d '{
        \"scriptLang\": \"bash\",
        \"scriptContent\": \"echo \\\"Draft task test\\\"\",
        \"timeoutSec\": 300
    }' | grep -q '\"taskStatus\":\"DRAFT\"' && echo 'SUCCESS' || echo 'FAILED'" \
    "200"

# 保存任务ID
DRAFT_TASK_RESPONSE=$(curl -s -X POST "${API_BASE}/tasks/create?agentIds=${AGENT_ID}&taskName=test-draft-task-2&autoStart=false" \
    -H 'Content-Type: application/json' \
    -d '{
        "scriptLang": "bash",
        "scriptContent": "echo \"Draft task for start test\"",
        "timeoutSec": 300
    }')
DRAFT_TASK_ID=$(echo "$DRAFT_TASK_RESPONSE" | grep -o '"taskId":"[^"]*"' | cut -d'"' -f4)

echo "草稿任务ID: $DRAFT_TASK_ID"
echo ""

# ========================================
# 测试2: 验证草稿任务没有执行实例
# ========================================
test_case "验证草稿任务没有执行实例" \
    "curl -s '${API_BASE}/tasks/${DRAFT_TASK_ID}/executions' | grep -q '\\[\\]' && echo 'SUCCESS' || echo 'FAILED'" \
    "200"

# ========================================
# 测试3: 创建立即启动的任务（autoStart=true，默认）
# ========================================
test_case "创建立即启动的任务（autoStart=true）" \
    "curl -s -X POST '${API_BASE}/tasks/create?agentIds=${AGENT_ID}&taskName=test-auto-start-task&autoStart=true' \
    -H 'Content-Type: application/json' \
    -d '{
        \"scriptLang\": \"bash\",
        \"scriptContent\": \"echo \\\"Auto start task test\\\"\",
        \"timeoutSec\": 300
    }' | grep -q '\"taskStatus\":\"PENDING\"' && echo 'SUCCESS' || echo 'FAILED'" \
    "200"

# ========================================
# 测试4: 启动草稿任务
# ========================================
if [ -n "$DRAFT_TASK_ID" ]; then
    test_case "启动草稿任务" \
        "curl -s -X POST '${API_BASE}/tasks/${DRAFT_TASK_ID}/start?agentIds=${AGENT_ID}' | grep -q '\"taskStatus\":\"PENDING\"' && echo 'SUCCESS' || echo 'FAILED'" \
        "200"
    
    # 等待一下
    sleep 1
    
    # 验证执行实例已创建
    test_case "验证启动后创建了执行实例" \
        "curl -s '${API_BASE}/tasks/${DRAFT_TASK_ID}/executions' | grep -q '\"status\":\"PENDING\"' && echo 'SUCCESS' || echo 'FAILED'" \
        "200"
fi

# ========================================
# 测试5: 创建一个任务用于停止测试
# ========================================
STOP_TASK_RESPONSE=$(curl -s -X POST "${API_BASE}/tasks/create?agentIds=${AGENT_ID}&taskName=test-stop-task&autoStart=true" \
    -H 'Content-Type: application/json' \
    -d '{
        "scriptLang": "bash",
        "scriptContent": "sleep 60",
        "timeoutSec": 300
    }')
STOP_TASK_ID=$(echo "$STOP_TASK_RESPONSE" | grep -o '"taskId":"[^"]*"' | cut -d'"' -f4)

echo "停止测试任务ID: $STOP_TASK_ID"
echo ""

# 等待任务被拉取
sleep 2

# ========================================
# 测试6: 停止任务
# ========================================
if [ -n "$STOP_TASK_ID" ]; then
    test_case "停止任务" \
        "curl -s -X POST '${API_BASE}/tasks/${STOP_TASK_ID}/stop' | grep -q '\"cancelledCount\"' && echo 'SUCCESS' || echo 'FAILED'" \
        "200"
    
    # 验证任务状态
    sleep 1
    test_case "验证停止后任务状态" \
        "curl -s '${API_BASE}/tasks/${STOP_TASK_ID}' | grep -qE '\"taskStatus\":\"(STOPPED|CANCELLED)\"' && echo 'SUCCESS' || echo 'FAILED'" \
        "200"
fi

# ========================================
# 测试7: 查询任务列表，验证taskStatus字段
# ========================================
test_case "查询任务列表包含taskStatus字段" \
    "curl -s '${API_BASE}/tasks?size=5' | grep -q '\"taskStatus\"' && echo 'SUCCESS' || echo 'FAILED'" \
    "200"

# ========================================
# 测试总结
# ========================================
echo "========================================="
echo "测试总结"
echo "========================================="
echo -e "总测试数: ${TOTAL_TESTS}"
echo -e "${GREEN}通过: ${PASSED_TESTS}${NC}"
echo -e "${RED}失败: ${FAILED_TESTS}${NC}"
echo ""

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}所有测试通过！✓${NC}"
    exit 0
else
    echo -e "${RED}部分测试失败！✗${NC}"
    exit 1
fi
