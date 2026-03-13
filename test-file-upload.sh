#!/bin/bash

echo "Testing file upload API..."

# 创建一个测试文件
echo "Creating test file..."
echo "This is a test file for upload" > test-upload.txt

# 测试文件上传API
echo "Testing file upload..."
curl -X POST \
  -H "Content-Type: multipart/form-data" \
  -F "file=@test-upload.txt" \
  -F "name=test-upload.txt" \
  -F "category=test" \
  -F "description=Test upload" \
  http://localhost:8080/api/web/files/upload

echo ""
echo "Upload test completed"

# 清理测试文件
rm -f test-upload.txt