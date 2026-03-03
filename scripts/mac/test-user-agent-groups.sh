#!/bin/bash

# 用户管理和Agent分组功能测试脚本
# 测试阿里云部署环境

set -e

# 配置
SERVER_URL="http://8.138.114.34:8080"
FRONTEND_URL="http://8.138.114.34:3000"
ADMIN_USER="admin"
ADMIN_PASS="admin123"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 测试计数
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# 测试结果
declare -a FAILED_TEST_NAMES

echo -e "${BLUE}=========================================="
echo "LightScript 用户管理和Agent分组功能测试"
echo "测试环境: 阿里云"
echo -e "==========================================${NC}"
echo ""

# 辅助函数
test_start() {
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    echo -e "${YELLOW}[测试 $TOTAL_TESTS] $1${NC}"
}

test_pass() {
    PASSED_TESTS=$((PASSED_TESTS + 1))
    echo -e "${GREEN}✅ 通过${NC}"
    echo ""
}

test_fail() {
    FAILED_TESTS=$((FAILED_TESTS + 1))
    FAILED_TEST_NAMES+=("$1")
    echo -e "${RED}❌ 失败: $2${NC}"
    echo ""
}

# 获取token
get_token() {
    local response=$(curl -s -X POST "${SERVER_URL}/api/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\"}")
    
    echo "$response" | grep -o '"token":"[^"]*"' | cut -d'"' -f4
}

# ==================== 基础测试 ====================

test_start "检查前端服务（80端口）"
if curl -s -I "${FRONTEND_URL/3000/}" | grep -q "200 OK"; then
    test_pass
else
    test_fail "前端服务（80端口）" "无法访问"
fi

test_start "检查前端服务（3000端口）"
if curl -s -I "${FRONTEND_URL}" | grep -q "200 OK"; then
    test_pass
else
    test_fail "前端服务（3000端口）" "无法访问"
fi

test_start "检查后端服务"
if curl -s "${SERVER_URL}/actuator/health" | grep -q "UP"; then
    test_pass
else
    test_fail "后端服务" "服务未启动或健康检查失败"
fi

# ==================== 认证测试 ====================

test_start "管理员登录"
TOKEN=$(get_token)
if [ -n "$TOKEN" ] && [ "$TOKEN" != "null" ]; then
    echo "Token: ${TOKEN:0:20}..."
    test_pass
else
    test_fail "管理员登录" "无法获取token"
    echo -e "${RED}后续测试需要token，退出测试${NC}"
    exit 1
fi

test_start "验证token包含用户信息"
LOGIN_RESPONSE=$(curl -s -X POST "${SERVER_URL}/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\"}")

if echo "$LOGIN_RESPONSE" | grep -q "permissions" && \
   echo "$LOGIN_RESPONSE" | grep -q "realName" && \
   echo "$LOGIN_RESPONSE" | grep -q "系统管理员"; then
    echo "用户信息: $(echo $LOGIN_RESPONSE | grep -o '"realName":"[^"]*"')"
    echo "权限数量: $(echo $LOGIN_RESPONSE | grep -o '"user:create"' | wc -l)"
    test_pass
else
    test_fail "验证token包含用户信息" "响应中缺少必要字段"
fi

# ==================== 权限API测试 ====================

test_start "获取所有可用权限"
PERMISSIONS_RESPONSE=$(curl -s -X GET "${SERVER_URL}/api/web/permissions" \
    -H "Authorization: Bearer ${TOKEN}")

if echo "$PERMISSIONS_RESPONSE" | grep -q "permissions" && \
   echo "$PERMISSIONS_RESPONSE" | grep -q "user:create"; then
    PERM_COUNT=$(echo "$PERMISSIONS_RESPONSE" | grep -o '"code":"[^"]*"' | wc -l)
    echo "权限数量: $PERM_COUNT"
    test_pass
else
    test_fail "获取所有可用权限" "响应格式错误"
fi

# ==================== 用户管理测试 ====================

test_start "获取用户列表"
USERS_RESPONSE=$(curl -s -X GET "${SERVER_URL}/api/web/users" \
    -H "Authorization: Bearer ${TOKEN}")

if echo "$USERS_RESPONSE" | grep -q "content" && \
   echo "$USERS_RESPONSE" | grep -q "admin"; then
    USER_COUNT=$(echo "$USERS_RESPONSE" | grep -o '"username":"[^"]*"' | wc -l)
    echo "用户数量: $USER_COUNT"
    test_pass
else
    test_fail "获取用户列表" "响应格式错误或无admin用户"
fi

test_start "创建测试用户"
CREATE_USER_RESPONSE=$(curl -s -X POST "${SERVER_URL}/api/web/users" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{
        "username": "testuser_'$(date +%s)'",
        "password": "Test1234",
        "email": "test@example.com",
        "realName": "测试用户",
        "permissions": ["task:view", "agent:view", "log:view"]
    }')

if echo "$CREATE_USER_RESPONSE" | grep -q "用户创建成功"; then
    TEST_USER_ID=$(echo "$CREATE_USER_RESPONSE" | grep -o '"id":[0-9]*' | cut -d':' -f2)
    echo "创建的用户ID: $TEST_USER_ID"
    test_pass
else
    test_fail "创建测试用户" "创建失败: $CREATE_USER_RESPONSE"
    TEST_USER_ID=""
fi

if [ -n "$TEST_USER_ID" ]; then
    test_start "获取用户详情"
    USER_DETAIL=$(curl -s -X GET "${SERVER_URL}/api/web/users/${TEST_USER_ID}" \
        -H "Authorization: Bearer ${TOKEN}")
    
    if echo "$USER_DETAIL" | grep -q "测试用户"; then
        test_pass
    else
        test_fail "获取用户详情" "无法获取用户详情"
    fi
    
    test_start "更新用户信息"
    UPDATE_RESPONSE=$(curl -s -X PUT "${SERVER_URL}/api/web/users/${TEST_USER_ID}" \
        -H "Authorization: Bearer ${TOKEN}" \
        -H "Content-Type: application/json" \
        -d '{
            "email": "updated@example.com",
            "realName": "更新后的用户",
            "permissions": ["task:view", "script:view"]
        }')
    
    if echo "$UPDATE_RESPONSE" | grep -q "用户更新成功"; then
        test_pass
    else
        test_fail "更新用户信息" "更新失败"
    fi
    
    test_start "重置用户密码"
    RESET_RESPONSE=$(curl -s -X POST "${SERVER_URL}/api/web/users/${TEST_USER_ID}/reset-password" \
        -H "Authorization: Bearer ${TOKEN}" \
        -H "Content-Type: application/json" \
        -d '{"newPassword": "NewPass123"}')
    
    if echo "$RESET_RESPONSE" | grep -q "密码重置成功"; then
        test_pass
    else
        test_fail "重置用户密码" "重置失败"
    fi
    
    test_start "切换用户状态"
    TOGGLE_RESPONSE=$(curl -s -X POST "${SERVER_URL}/api/web/users/${TEST_USER_ID}/toggle-status" \
        -H "Authorization: Bearer ${TOKEN}")
    
    if echo "$TOGGLE_RESPONSE" | grep -q "status"; then
        test_pass
    else
        test_fail "切换用户状态" "切换失败"
    fi
    
    test_start "删除测试用户"
    DELETE_RESPONSE=$(curl -s -X DELETE "${SERVER_URL}/api/web/users/${TEST_USER_ID}" \
        -H "Authorization: Bearer ${TOKEN}")
    
    if echo "$DELETE_RESPONSE" | grep -q "用户删除成功"; then
        test_pass
    else
        test_fail "删除测试用户" "删除失败"
    fi
fi

# ==================== Agent分组测试 ====================

test_start "获取分组列表"
GROUPS_RESPONSE=$(curl -s -X GET "${SERVER_URL}/api/web/agent-groups" \
    -H "Authorization: Bearer ${TOKEN}")

if echo "$GROUPS_RESPONSE" | grep -q "content"; then
    GROUP_COUNT=$(echo "$GROUPS_RESPONSE" | grep -o '"name":"[^"]*"' | wc -l)
    echo "分组数量: $GROUP_COUNT"
    test_pass
else
    test_fail "获取分组列表" "响应格式错误"
fi

test_start "创建测试分组"
CREATE_GROUP_RESPONSE=$(curl -s -X POST "${SERVER_URL}/api/web/agent-groups" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{
        "name": "测试分组_'$(date +%s)'",
        "type": "CUSTOM",
        "description": "自动化测试创建的分组"
    }')

if echo "$CREATE_GROUP_RESPONSE" | grep -q "分组创建成功"; then
    TEST_GROUP_ID=$(echo "$CREATE_GROUP_RESPONSE" | grep -o '"id":[0-9]*' | cut -d':' -f2)
    echo "创建的分组ID: $TEST_GROUP_ID"
    test_pass
else
    test_fail "创建测试分组" "创建失败: $CREATE_GROUP_RESPONSE"
    TEST_GROUP_ID=""
fi

if [ -n "$TEST_GROUP_ID" ]; then
    test_start "获取分组详情"
    GROUP_DETAIL=$(curl -s -X GET "${SERVER_URL}/api/web/agent-groups/${TEST_GROUP_ID}" \
        -H "Authorization: Bearer ${TOKEN}")
    
    if echo "$GROUP_DETAIL" | grep -q "自动化测试创建的分组"; then
        test_pass
    else
        test_fail "获取分组详情" "无法获取分组详情"
    fi
    
    test_start "更新分组信息"
    UPDATE_GROUP_RESPONSE=$(curl -s -X PUT "${SERVER_URL}/api/web/agent-groups/${TEST_GROUP_ID}" \
        -H "Authorization: Bearer ${TOKEN}" \
        -H "Content-Type: application/json" \
        -d '{
            "name": "更新后的测试分组_'$(date +%s)'",
            "description": "更新后的描述"
        }')
    
    if echo "$UPDATE_GROUP_RESPONSE" | grep -q "分组更新成功"; then
        test_pass
    else
        test_fail "更新分组信息" "更新失败"
    fi
    
    test_start "删除测试分组"
    DELETE_GROUP_RESPONSE=$(curl -s -X DELETE "${SERVER_URL}/api/web/agent-groups/${TEST_GROUP_ID}" \
        -H "Authorization: Bearer ${TOKEN}")
    
    if echo "$DELETE_GROUP_RESPONSE" | grep -q "分组删除成功"; then
        test_pass
    else
        test_fail "删除测试分组" "删除失败"
    fi
fi

# ==================== Agent列表测试 ====================

test_start "获取Agent列表"
AGENTS_RESPONSE=$(curl -s -X GET "${SERVER_URL}/api/web/agents" \
    -H "Authorization: Bearer ${TOKEN}")

if echo "$AGENTS_RESPONSE" | grep -q "content"; then
    AGENT_COUNT=$(echo "$AGENTS_RESPONSE" | grep -o '"agentId":"[^"]*"' | wc -l)
    echo "Agent数量: $AGENT_COUNT"
    test_pass
else
    test_fail "获取Agent列表" "响应格式错误"
fi

# ==================== 任务管理测试 ====================

test_start "获取任务列表"
TASKS_RESPONSE=$(curl -s -X GET "${SERVER_URL}/api/web/tasks" \
    -H "Authorization: Bearer ${TOKEN}")

if echo "$TASKS_RESPONSE" | grep -q "content"; then
    TASK_COUNT=$(echo "$TASKS_RESPONSE" | grep -o '"taskId":"[^"]*"' | wc -l)
    echo "任务数量: $TASK_COUNT"
    test_pass
else
    test_fail "获取任务列表" "响应格式错误"
fi

# ==================== 测试总结 ====================

echo ""
echo -e "${BLUE}=========================================="
echo "测试总结"
echo -e "==========================================${NC}"
echo -e "总测试数: ${BLUE}${TOTAL_TESTS}${NC}"
echo -e "通过: ${GREEN}${PASSED_TESTS}${NC}"
echo -e "失败: ${RED}${FAILED_TESTS}${NC}"
echo -e "通过率: ${BLUE}$(awk "BEGIN {printf \"%.1f\", ${PASSED_TESTS}/${TOTAL_TESTS}*100}")%${NC}"
echo ""

if [ ${FAILED_TESTS} -gt 0 ]; then
    echo -e "${RED}失败的测试:${NC}"
    for test_name in "${FAILED_TEST_NAMES[@]}"; do
        echo -e "  ${RED}✗${NC} $test_name"
    done
    echo ""
    exit 1
else
    echo -e "${GREEN}🎉 所有测试通过！${NC}"
    echo ""
    exit 0
fi
