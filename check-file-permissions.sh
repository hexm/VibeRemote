#!/bin/bash

echo "Checking file storage permissions..."

# 检查当前目录
echo "Current directory: $(pwd)"

# 检查files目录
if [ -d "files" ]; then
    echo "Files directory exists"
    echo "Directory permissions: $(ls -ld files)"
    echo "Directory is writable: $(test -w files && echo 'YES' || echo 'NO')"
else
    echo "Files directory does not exist, will be created"
fi

# 检查磁盘空间
echo "Disk space:"
df -h .

# 测试创建文件
echo "Testing file creation..."
touch files/test-file.txt 2>/dev/null && echo "File creation: SUCCESS" || echo "File creation: FAILED"

# 清理测试文件
rm -f files/test-file.txt 2>/dev/null