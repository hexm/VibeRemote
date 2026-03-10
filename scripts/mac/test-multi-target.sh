#!/bin/bash

# LightScript 多目标任务功能自动化测试脚本
# 测试任务多目标支持的所有关键功能

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
SERVER_URL="http://localhost:8080"
WEB_URL="http://localhost:3000"
TEST_RESULTS_DIR="test-results"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

# 测试统计
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# 创建测试结果目录
mkdir -p ${TEST_RESULTS_DIR}
TEST_LOG="${TEST_RESULTS_DIR}/test-${TIMESTAMP}.log"

echo -e "${BLUE}=========================================="
echo "LightScript 多目标任务功能测试"
echo "测试时间: $(date)"
echo -e "==========================================${NC}"
echo ""

# 日志函数
log() {
    echo -e "$1" | tee -a ${TEST_LOG}
}

# 测试函数
test_start() {
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    log "${BLUE}[TEST $TOTAL_TESTS] $1${NC}"
}

test_pass() {
    PASSED_TESTS=$((PASSED_TESTS + 1))
    log "${GREEN}✅ PASS${NC}"
    echo ""
}

test_fail() {
    FAILED_TESTS=$((FAILED_TESTS + 1))
    log "${RED}❌ FAIL: $1${NC}"
    echo ""
}

# 检查服务是否运行
check_service() {
    local url=$1
    local name=$2
    
    if curl -s -f -o /dev/null ${url}; then
        log "${GREEN}✅ ${name} 运行正常${NC}"
        return 0
    else
        log "${RED}❌ ${name} 未运行${NC}"
        return 1
    fi
}

# API 测试函数
api_test() {
    local method=$1
    local endpoint=$2
    local data=$3
    local expected_status=$4
    
    local response=$(curl -s -w "\n%{http_code}" -X ${method} \
        -H "Content-Type: application/json" \
        ${data:+-d "$data"} \
        "${SERVER_URL}${endpoint}")
    
    local body=$(echo "$response" | sed '$d')
    local status=$(echo "$response" | tail -n 1)
    
    if [ "$status" = "$expected_status" ]; then
        echo "$body"
        return 0
    else
        log "${RED}Expected status $expected_status, got $status${NC}"
        log "${RED}Response: $body${NC}"
        return 1
    fi
}

# ============================================
# 阶段 1: 环境检查
# ============================================
log "${YELLOW}阶段 1: 环境检查${NC}"
echo ""

test_start "检查服务器是否运行"
if check_service "${SERVER_URL}/actuator/health" "服务器"; then
    test_pass
else
    test_fail "服务器未运行，请先启动: ./scripts/mac/start-server.sh"
    exit 1
fi

test_start "检查前端是否运行"
if check_service "${WEB_URL}" "前端"; then
    test_pass
else
    log "${YELLOW}⚠️  前端未运行，跳过前端测试${NC}"
    echo ""
fi

# ============================================
# 阶段 2: 数据库迁移验证
# ============================================
log "${YELLOW}阶段 2: 数据库迁移验证${NC}"
echo ""

test_start "检查 TaskExecution 表是否存在"
# 通过创建一个测试任务来验证表结构
AGENT_IDS='["test-agent-1"]'
TASK_SPEC='{"scriptLang":"bash","scriptContent":"echo test","timeoutSec":60}'

response=$(api_test POST "/api/web/tasks/create?agentIds=test-agent-1&taskName=migration-test" "$TASK_SPEC" "200")
if [ $? -eq 0 ]; then
    MIGRATION_TEST_TASK_ID=$(echo $response | grep -o '"taskId":"[^"]*"' | cut -d'"' -f4)
    log "创建的测试任务ID: $MIGRATION_TEST_TASK_ID"
    test_pass
else
    test_fail "无法创建任务，可能是数据库迁移失败"
fi

# ============================================
# 阶段 3: API 端点测试
# ============================================
log "${YELLOW}阶段 3: API 端点测试${NC}"
echo ""

test_start "测试创建单代理任务"
TASK_SPEC='{"scriptLang":"bash","scriptContent":"echo Hello Single Agent","timeoutSec":60}'
response=$(api_test POST "/api/web/tasks/create?agentIds=agent-1&taskName=single-agent-task" "$TASK_SPEC" "200")
if [ $? -eq 0 ]; then
    SINGLE_TASK_ID=$(echo $response | grep -o '"taskId":"[^"]*"' | cut -d'"' -f4)
    TARGET_COUNT=$(echo $response | grep -o '"targetAgentCount":[0-9]*' | cut -d':' -f2)
    
    if [ "$TARGET_COUNT" = "1" ]; then
        log "任务ID: $SINGLE_TASK_ID, 目标代理数: $TARGET_COUNT"
        test_pass
    else
        test_fail "目标代理数不正确，期望1，实际$TARGET_COUNT"
    fi
else
    test_fail "创建单代理任务失败"
fi

test_start "测试创建多代理任务"
TASK_SPEC='{"scriptLang":"bash","scriptContent":"echo Hello Multi Agent","timeoutSec":60}'
response=$(api_test POST "/api/web/tasks/create?agentIds=agent-1&agentIds=agent-2&agentIds=agent-3&taskName=multi-agent-task" "$TASK_SPEC" "200")
if [ $? -eq 0 ]; then
    MULTI_TASK_ID=$(echo $response | grep -o '"taskId":"[^"]*"' | cut -d'"' -f4)
    TARGET_COUNT=$(echo $response | grep -o '"targetAgentCount":[0-9]*' | cut -d':' -f2)
    
    if [ "$TARGET_COUNT" = "3" ]; then
        log "任务ID: $MULTI_TASK_ID, 目标代理数: $TARGET_COUNT"
        test_pass
    else
        test_fail "目标代理数不正确，期望3，实际$TARGET_COUNT"
    fi
else
    test_fail "创建多代理任务失败"
fi

test_start "测试获取任务详情"
if [ -n "$MULTI_TASK_ID" ]; then
    response=$(api_test GET "/api/web/tasks/${MULTI_TASK_ID}" "" "200")
    if [ $? -eq 0 ]; then
        log "成功获取任务详情"
        test_pass
    else
        test_fail "获取任务详情失败"
    fi
else
    test_fail "没有可用的任务ID"
fi

test_start "测试获取任务执行实例列表"
if [ -n "$MULTI_TASK_ID" ]; then
    response=$(api_test GET "/api/web/tasks/${MULTI_TASK_ID}/executions" "" "200")
    if [ $? -eq 0 ]; then
        EXECUTION_COUNT=$(echo $response | grep -o '"id":[0-9]*' | wc -l)
        log "执行实例数量: $EXECUTION_COUNT"
        
        if [ "$EXECUTION_COUNT" -ge "3" ]; then
            # 提取第一个执行实例ID
            EXECUTION_ID=$(echo $response | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
            log "第一个执行实例ID: $EXECUTION_ID"
            test_pass
        else
            test_fail "执行实例数量不正确，期望3，实际$EXECUTION_COUNT"
        fi
    else
        test_fail "获取执行实例列表失败"
    fi
else
    test_fail "没有可用的任务ID"
fi

test_start "测试获取任务摘要"
if [ -n "$MULTI_TASK_ID" ]; then
    response=$(api_test GET "/api/web/tasks/${MULTI_TASK_ID}/summary" "" "200")
    if [ $? -eq 0 ]; then
        log "成功获取任务摘要"
        test_pass
    else
        test_fail "获取任务摘要失败"
    fi
else
    test_fail "没有可用的任务ID"
fi

test_start "测试获取任务列表"
response=$(api_test GET "/api/web/tasks?page=0&size=10" "" "200")
if [ $? -eq 0 ]; then
    TASK_COUNT=$(echo $response | grep -o '"taskId":"[^"]*"' | wc -l)
    log "任务列表中的任务数: $TASK_COUNT"
    test_pass
else
    test_fail "获取任务列表失败"
fi

# ============================================
# 阶段 4: Agent API 测试
# ============================================
log "${YELLOW}阶段 4: Agent API 测试${NC}"
echo ""

test_start "测试 Agent 拉取任务"
response=$(api_test GET "/api/agent/tasks/pull?agentId=agent-1&agentToken=test-token&max=10" "" "200")
if [ $? -eq 0 ]; then
    PULLED_TASK_COUNT=$(echo $response | grep -o '"taskId":"[^"]*"' | wc -l)
    log "拉取到的任务数: $PULLED_TASK_COUNT"
    
    # 检查是否包含 executionId
    if echo $response | grep -q '"executionId"'; then
        log "✅ TaskSpec 包含 executionId 字段"
        
        # 提取第一个任务的信息
        if [ "$PULLED_TASK_COUNT" -gt "0" ]; then
            PULLED_EXECUTION_ID=$(echo $response | grep -o '"executionId":[0-9]*' | head -1 | cut -d':' -f2)
            PULLED_TASK_ID=$(echo $response | grep -o '"taskId":"[^"]*"' | head -1 | cut -d'"' -f4)
            log "拉取的任务: taskId=$PULLED_TASK_ID, executionId=$PULLED_EXECUTION_ID"
        fi
        
        test_pass
    else
        test_fail "TaskSpec 缺少 executionId 字段"
    fi
else
    test_fail "Agent 拉取任务失败"
fi

test_start "测试 Agent 确认任务（使用 executionId）"
if [ -n "$PULLED_EXECUTION_ID" ]; then
    response=$(api_test POST "/api/agent/tasks/executions/${PULLED_EXECUTION_ID}/ack?agentId=agent-1&agentToken=test-token" "" "200")
    if [ $? -eq 0 ]; then
        log "成功确认任务执行"
        test_pass
    else
        test_fail "确认任务失败"
    fi
else
    log "${YELLOW}⚠️  跳过：没有可用的执行实例ID${NC}"
    echo ""
fi

test_start "测试 Agent 上传日志（使用 executionId）"
if [ -n "$PULLED_EXECUTION_ID" ]; then
    LOG_DATA='{"agentId":"agent-1","agentToken":"test-token","executionId":'$PULLED_EXECUTION_ID',"seq":1,"stream":"stdout","data":"Test log message"}'
    response=$(api_test POST "/api/agent/tasks/executions/${PULLED_EXECUTION_ID}/log" "$LOG_DATA" "200")
    if [ $? -eq 0 ]; then
        log "成功上传日志"
        test_pass
    else
        test_fail "上传日志失败"
    fi
else
    log "${YELLOW}⚠️  跳过：没有可用的执行实例ID${NC}"
    echo ""
fi

test_start "测试 Agent 完成任务（使用 executionId）"
if [ -n "$PULLED_EXECUTION_ID" ]; then
    FINISH_DATA='{"agentId":"agent-1","agentToken":"test-token","executionId":'$PULLED_EXECUTION_ID',"exitCode":0,"status":"SUCCESS","summary":"Test completed"}'
    response=$(api_test POST "/api/agent/tasks/executions/${PULLED_EXECUTION_ID}/finish" "$FINISH_DATA" "200")
    if [ $? -eq 0 ]; then
        log "成功完成任务"
        test_pass
    else
        test_fail "完成任务失败"
    fi
else
    log "${YELLOW}⚠️  跳过：没有可用的执行实例ID${NC}"
    echo ""
fi

# ============================================
# 阶段 5: 任务管理功能测试
# ============================================
log "${YELLOW}阶段 5: 任务管理功能测试${NC}"
echo ""

test_start "测试取消单个执行实例"
if [ -n "$EXECUTION_ID" ]; then
    response=$(api_test POST "/api/web/tasks/executions/${EXECUTION_ID}/cancel" "" "200")
    if [ $? -eq 0 ]; then
        log "成功取消执行实例"
        test_pass
    else
        test_fail "取消执行实例失败"
    fi
else
    log "${YELLOW}⚠️  跳过：没有可用的执行实例ID${NC}"
    echo ""
fi

test_start "测试重启任务（ALL模式）"
if [ -n "$MULTI_TASK_ID" ]; then
    response=$(api_test POST "/api/web/tasks/${MULTI_TASK_ID}/restart?mode=ALL" "" "200")
    if [ $? -eq 0 ]; then
        NEW_EXEC_COUNT=$(echo $response | grep -o '"newExecutionCount":[0-9]*' | cut -d':' -f2)
        log "创建的新执行实例数: $NEW_EXEC_COUNT"
        test_pass
    else
        test_fail "重启任务失败"
    fi
else
    log "${YELLOW}⚠️  跳过：没有可用的任务ID${NC}"
    echo ""
fi

test_start "测试取消整个任务"
if [ -n "$SINGLE_TASK_ID" ]; then
    response=$(api_test POST "/api/web/tasks/${SINGLE_TASK_ID}/cancel" "" "200")
    if [ $? -eq 0 ]; then
        log "成功取消任务"
        test_pass
    else
        test_fail "取消任务失败"
    fi
else
    log "${YELLOW}⚠️  跳过：没有可用的任务ID${NC}"
    echo ""
fi

# ============================================
# 阶段 6: 数据一致性验证
# ============================================
log "${YELLOW}阶段 6: 数据一致性验证${NC}"
echo ""

test_start "验证任务和执行实例的关联"
if [ -n "$MULTI_TASK_ID" ]; then
    # 获取任务详情
    task_response=$(api_test GET "/api/web/tasks/${MULTI_TASK_ID}" "" "200")
    TARGET_COUNT=$(echo $task_response | grep -o '"targetAgentCount":[0-9]*' | cut -d':' -f2)
    
    # 获取执行实例列表
    exec_response=$(api_test GET "/api/web/tasks/${MULTI_TASK_ID}/executions" "" "200")
    ACTUAL_EXEC_COUNT=$(echo $exec_response | grep -o '"id":[0-9]*' | wc -l)
    
    # 验证数量是否匹配（考虑重启可能增加执行实例）
    if [ "$ACTUAL_EXEC_COUNT" -ge "$TARGET_COUNT" ]; then
        log "目标代理数: $TARGET_COUNT, 实际执行实例数: $ACTUAL_EXEC_COUNT"
        test_pass
    else
        test_fail "执行实例数量不匹配"
    fi
else
    log "${YELLOW}⚠️  跳过：没有可用的任务ID${NC}"
    echo ""
fi

test_start "验证执行进度计算"
if [ -n "$MULTI_TASK_ID" ]; then
    response=$(api_test GET "/api/web/tasks/${MULTI_TASK_ID}/summary" "" "200")
    if [ $? -eq 0 ]; then
        EXECUTION_PROGRESS=$(echo $response | grep -o '"executionProgress":"[^"]*"' | cut -d'"' -f4)
        
        log "执行进度: $EXECUTION_PROGRESS"
        
        if [ -n "$EXECUTION_PROGRESS" ]; then
            test_pass
        else
            test_fail "执行进度为空"
        fi
    else
        test_fail "获取任务摘要失败"
    fi
else
    log "${YELLOW}⚠️  跳过：没有可用的任务ID${NC}"
    echo ""
fi

# ============================================
# 测试总结
# ============================================
echo ""
log "${BLUE}=========================================="
log "测试总结"
log "==========================================${NC}"
log "总测试数: ${TOTAL_TESTS}"
log "${GREEN}通过: ${PASSED_TESTS}${NC}"
log "${RED}失败: ${FAILED_TESTS}${NC}"

if [ ${FAILED_TESTS} -eq 0 ]; then
    log "${GREEN}✅ 所有测试通过！${NC}"
    EXIT_CODE=0
else
    log "${RED}❌ 有 ${FAILED_TESTS} 个测试失败${NC}"
    EXIT_CODE=1
fi

log ""
log "详细日志: ${TEST_LOG}"
log "测试时间: $(date)"
log "${BLUE}==========================================${NC}"

exit ${EXIT_CODE}
