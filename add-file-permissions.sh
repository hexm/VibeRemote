#!/bin/bash

# Script to add file management permissions to admin user
# This script calls the REST API to update admin user permissions

echo "Adding file management permissions to admin user..."

# Login to get JWT token
echo "Logging in as admin..."
LOGIN_RESPONSE=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')

TOKEN=$(echo $LOGIN_RESPONSE | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo "Failed to get authentication token"
  echo "Response: $LOGIN_RESPONSE"
  exit 1
fi

echo "Got authentication token"

# Get current admin user info
echo "Getting current admin user info..."
USER_RESPONSE=$(curl -s -X GET http://localhost:8080/api/web/users/1 \
  -H "Authorization: Bearer $TOKEN")

echo "Current user info: $USER_RESPONSE"

# Update admin user with file permissions
echo "Adding file management permissions..."
UPDATE_RESPONSE=$(curl -s -X PUT http://localhost:8080/api/web/users/1 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@example.com",
    "realName": "系统管理员",
    "permissions": [
      "user:create",
      "user:edit", 
      "user:delete",
      "user:view",
      "task:create",
      "task:execute",
      "task:delete", 
      "task:view",
      "script:create",
      "script:edit",
      "script:delete",
      "script:view",
      "script:list",
      "agent:view",
      "agent:group",
      "log:view",
      "system:settings",
      "file:list",
      "file:view",
      "file:upload",
      "file:download",
      "file:delete"
    ]
  }')

echo "Update response: $UPDATE_RESPONSE"

if echo "$UPDATE_RESPONSE" | grep -q "用户更新成功"; then
  echo "✅ File management permissions added successfully!"
else
  echo "❌ Failed to add permissions"
  echo "Response: $UPDATE_RESPONSE"
  exit 1
fi

echo "Done!"