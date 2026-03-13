#!/bin/bash

# Agent升级测试包创建脚本

echo "========================================="
echo "创建Agent升级测试包"
echo "========================================="

# 创建测试目录
mkdir -p test-upgrade
cd test-upgrade

# 1. 编译当前Agent
echo "1. 编译当前Agent..."
cd ../agent
mvn clean package -q
if [ $? -ne 0 ]; then
    echo "❌ Agent编译失败"
    exit 1
fi

# 复制编译好的JAR作为基础版本
cp target/agent-*.jar ../test-upgrade/agent-1.0.0.jar
echo "✅ 基础版本 agent-1.0.0.jar 创建完成"

# 2. 创建模拟的新版本（修改版本号）
cd ../test-upgrade
cp agent-1.0.0.jar agent-1.1.0.jar

# 修改JAR中的版本信息（简单方式：在文件末尾添加版本标识）
echo "# Version 1.1.0 - Test Upgrade" >> agent-1.1.0.jar
echo "✅ 新版本 agent-1.1.0.jar 创建完成"

# 3. 计算文件信息
echo ""
echo "========================================="
echo "文件信息:"
echo "========================================="

for file in agent-*.jar; do
    if [ -f "$file" ]; then
        size=$(stat -f%z "$file" 2>/dev/null || stat -c%s "$file" 2>/dev/null)
        hash=$(shasum -a 256 "$file" | cut -d' ' -f1)
        echo "文件: $file"
        echo "  大小: $size bytes"
        echo "  SHA256: $hash"
        echo ""
    fi
done

# 4. 创建版本信息JSON
cat > version-info.json << EOF
{
  "versions": [
    {
      "version": "1.0.0",
      "file": "agent-1.0.0.jar",
      "size": $(stat -f%z "agent-1.0.0.jar" 2>/dev/null || stat -c%s "agent-1.0.0.jar" 2>/dev/null),
      "hash": "$(shasum -a 256 agent-1.0.0.jar | cut -d' ' -f1)",
      "isCurrent": true,
      "isLatest": false
    },
    {
      "version": "1.1.0", 
      "file": "agent-1.1.0.jar",
      "size": $(stat -f%z "agent-1.1.0.jar" 2>/dev/null || stat -c%s "agent-1.1.0.jar" 2>/dev/null),
      "hash": "$(shasum -a 256 agent-1.1.0.jar | cut -d' ' -f1)",
      "isCurrent": false,
      "isLatest": true,
      "releaseNotes": "测试升级版本 - 包含升级功能改进"
    }
  ]
}
EOF

echo "✅ 版本信息文件 version-info.json 创建完成"

# 5. 创建简单的HTTP服务器脚本
cat > start-file-server.sh << 'EOF'
#!/bin/bash
echo "启动文件服务器..."
echo "访问地址: http://localhost:8000"
echo "按 Ctrl+C 停止服务器"
echo ""

# 使用Python启动简单HTTP服务器
if command -v python3 &> /dev/null; then
    python3 -m http.server 8000
elif command -v python &> /dev/null; then
    python -m SimpleHTTPServer 8000
else
    echo "❌ 需要安装Python来启动文件服务器"
    exit 1
fi
EOF

chmod +x start-file-server.sh

echo ""
echo "========================================="
echo "测试包创建完成！"
echo "========================================="
echo "文件位置: $(pwd)"
echo ""
echo "下一步操作:"
echo "1. 启动文件服务器: ./start-file-server.sh"
echo "2. 访问 http://localhost:8000 查看文件"
echo "3. 使用以下URL进行测试:"
echo "   - agent-1.0.0.jar: http://localhost:8000/agent-1.0.0.jar"
echo "   - agent-1.1.0.jar: http://localhost:8000/agent-1.1.0.jar"
echo ""