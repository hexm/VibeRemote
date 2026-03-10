#!/bin/bash

echo "=== 测试系统参数API ==="
echo ""

# 1. 登录获取token
echo "1. 登录获取token..."
LOGIN_RESPONSE=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')

echo "登录响应: $LOGIN_RESPONSE"
echo ""

# 提取token (使用grep和cut)
TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo "❌ 获取token失败"
  exit 1
fi

echo "✅ Token获取成功: ${TOKEN:0:30}..."
echo ""

# 2. 测试获取所有系统参数
echo "2. 测试获取所有系统参数..."
curl -s http://localhost:8080/web/system-settings \
  -H "Authorization: Bearer $TOKEN" | head -50
echo ""
echo ""

# 3. 测试按类别获取系统参数
echo "3. 测试按类别获取系统参数..."
curl -s http://localhost:8080/web/system-settings/by-category \
  -H "Authorization: Bearer $TOKEN" | head -50
echo ""
echo ""

echo "=== 测试完成 ==="
