#!/bin/bash

# LightScript 阿里云服务器测试脚本
set -e

SERVER_URL="http://8.138.114.34:8080"
WEB_URL="http://8.138.114.34:3000"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

log() { echo -e "$1"; }
test_start() { TOTAL_TESTS=$((TOTAL_TESTS + 1)); echo -e "\n${BLUE}[TEST $TOTAL_TESTS]${NC} $1"; }
test_pass() { PASSED_TESTS=$((PASSED_TESTS + 1)); echo -e "${GREEN}✅ PASS${NC}"; }
test_fail() { FAILED_TESTS=$((FAILED_TESTS + 1)); echo -e "${RED}❌ FAIL${NC}: $1"; }

api_call() {
    local method=$1 endpoint=$2 data=$3 expected_status=$4
    local response=$(curl -s -w "\n%{http_code}" -X ${method} -H "Content-Type: application/json" ${data:+-d "$data"} "${SERVER_URL}${endpoint}")
    local body=$(echo "$response" | sed '$d')
    local status=$(echo "$response" | tail -n 1)
    [ "$status" = "$expected_status" ] && echo "$body" && return 0 || return 1
}

check_service() { curl -s -f -o /dev/null --connect-timeout 5 "$1"; }

log "${BLUE}=========================================="
log "LightScript 阿里云服务器测试"
log "测试时间: $(date)"
log "==========================================${NC}\n"

log "${YELLOW}阶段 1: 环境检查${NC}\n"
test_start "检查后端服务器"
check_service "${SERVER_URL}/actuator/health" && test_pass || { test_fail "后端未运行"; exit 1; }

test_start "检查前端服务器"
check_service "${WEB_URL}" && test_pass || log "${YELLOW}⚠️  前端未运行${NC}"

log "${YELLOW}阶段 2: 多代理任务创建${NC}\n"
test_start "创建单代理任务"
TASK_SPEC='{"scriptLang":"bash","scriptContent":"echo test","timeoutSec":60}'
response=$(api_call POST "/api/web/tasks/create?agentIds=aliyun-agent-1&taskName=single-$(date +%s)" "$TASK_SPEC" "200")
[ $? -eq 0 ] && SINGLE_TASK_ID=$(echo $response | grep -o '"taskId":"[^"]*"' | cut -d'"' -f4) && test_pass || test_fail "创建失败"

test_start "创建多代理任务"
response=$(api_call POST "/api/web/tasks/create?agentIds=aliyun-agent-1&agentIds=aliyun-agent-2&agentIds=aliyun-agent-3&taskName=multi-$(date +%s)" "$TASK_SPEC" "200")
[ $? -eq 0 ] && MULTI_TASK_ID=$(echo $response | grep -o '"taskId":"[^"]*"' | cut -d'"' -f4) && test_pass || test_fail "创建失败"

log "${YELLOW}阶段 3: 任务查询${NC}\n"
test_start "获取任务执行实例"
[ -n "$MULTI_TASK_ID" ] && api_call GET "/api/web/tasks/${MULTI_TASK_ID}/executions" "" "200" >/dev/null && test_pass || test_fail "查询失败"

test_start "获取任务摘要"
[ -n "$MULTI_TASK_ID" ] && api_call GET "/api/web/tasks/${MULTI_TASK_ID}/summary" "" "200" >/dev/null && test_pass || test_fail "查询失败"

log "${YELLOW}阶段 4: Agent API${NC}\n"
test_start "Agent注册"
REGISTER_DATA='{"agentId":"test-aliyun","hostname":"test","osType":"Linux","osVersion":"Ubuntu","registerToken":"dev-register-token"}'
response=$(api_call POST "/api/agent/register" "$REGISTER_DATA" "200")
[ $? -eq 0 ] && TEST_AGENT_ID=$(echo $response | grep -o '"agentId":"[^"]*"' | cut -d'"' -f4) && TEST_AGENT_TOKEN=$(echo $response | grep -o '"agentToken":"[^"]*"' | cut -d'"' -f4) && test_pass || test_fail "注册失败"

test_start "为Agent创建任务"
[ -n "$TEST_AGENT_ID" ] && response=$(api_call POST "/api/web/tasks/create?agentIds=${TEST_AGENT_ID}&taskName=agent-test-$(date +%s)" "$TASK_SPEC" "200") && TEST_TASK_ID=$(echo $response | grep -o '"taskId":"[^"]*"' | cut -d'"' -f4) && test_pass || test_fail "创建失败"

test_start "Agent拉取任务"
[ -n "$TEST_AGENT_ID" ] && response=$(api_call GET "/api/agent/tasks/pull?agentId=${TEST_AGENT_ID}&agentToken=${TEST_AGENT_TOKEN}&max=5" "" "200") && EXECUTION_ID=$(echo $response | grep -o '"executionId":[0-9]*' | head -1 | cut -d':' -f2) && test_pass || test_fail "拉取失败"

test_start "Agent确认任务"
[ -n "$EXECUTION_ID" ] && curl -s -X POST "${SERVER_URL}/api/agent/tasks/executions/${EXECUTION_ID}/ack?agentId=${TEST_AGENT_ID}&agentToken=${TEST_AGENT_TOKEN}" >/dev/null && test_pass || test_fail "确认失败"

test_start "Agent提交日志"
[ -n "$EXECUTION_ID" ] && LOG_DATA="{\"agentId\":\"${TEST_AGENT_ID}\",\"agentToken\":\"${TEST_AGENT_TOKEN}\",\"executionId\":${EXECUTION_ID},\"seq\":1,\"stream\":\"stdout\",\"data\":\"test\\n\"}" && api_call POST "/api/agent/tasks/executions/${EXECUTION_ID}/log" "$LOG_DATA" "200" >/dev/null && test_pass || test_fail "提交失败"

test_start "Agent完成任务"
[ -n "$EXECUTION_ID" ] && FINISH_DATA="{\"agentId\":\"${TEST_AGENT_ID}\",\"agentToken\":\"${TEST_AGENT_TOKEN}\",\"executionId\":${EXECUTION_ID},\"exitCode\":0,\"status\":\"SUCCESS\",\"summary\":\"OK\"}" && api_call POST "/api/agent/tasks/executions/${EXECUTION_ID}/finish" "$FINISH_DATA" "200" >/dev/null && test_pass || test_fail "完成失败"

test_start "验证任务完成状态"
[ -n "$TEST_TASK_ID" ] && response=$(api_call GET "/api/web/tasks/${TEST_TASK_ID}/summary" "" "200") && PROGRESS=$(echo $response | grep -o '"executionProgress":"[^"]*"' | cut -d'"' -f4) && [ -n "$PROGRESS" ] && test_pass || test_fail "执行进度获取失败: $PROGRESS"

log "\n${BLUE}=========================================="
log "测试完成"
log "==========================================${NC}"
log "总计: ${TOTAL_TESTS} | ${GREEN}通过: ${PASSED_TESTS}${NC} | ${RED}失败: ${FAILED_TESTS}${NC}\n"
[ $FAILED_TESTS -eq 0 ] && exit 0 || exit 1
