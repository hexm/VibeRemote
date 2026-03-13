#!/bin/bash

# Agent升级测试包创建脚本

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "========================================="
echo "创建Agent升级测试包"
echo "========================================="

# 检查当前版本
current_version="0.1.0-SNAPSHOT"
if [ -f "agent.jar" ]; then
    version=$(jar xf agent.jar META-INF/MANIFEST.MF && grep "Implementation-Version" META-INF/MANIFEST.MF | cut -d' ' -f2 2>/dev/null)
    if [ -n "$version" ]; then
        current_version="$version"
    fi
    rm -f META-INF/MANIFEST.MF
    rm -rf META-INF
fi

echo "当前版本: $current_version"

# 提示用户选择新版本号
echo ""
echo "请选择要创建的测试版本:"
echo "1. 1.0.0 (正式版本)"
echo "2. 1.1.0 (小版本升级)"
echo "3. 2.0.0 (大版本升级)"
echo "4. 自定义版本"
echo ""
read -p "请选择 (1-4): " choice

case $choice in
    1)
        new_version="1.0.0"
        ;;
    2)
        new_version="1.1.0"
        ;;
    3)
        new_version="2.0.0"
        ;;
    4)
        read -p "请输入自定义版本号: " new_version
        ;;
    *)
        echo "无效选择，使用默认版本 1.0.0"
        new_version="1.0.0"
        ;;
esac

echo ""
echo "创建版本: $new_version"

# 创建测试目录
test_dir="upgrade-test-$new_version"
mkdir -p "$test_dir"

echo ""
echo "1. 复制当前Agent程序..."
cp agent.jar "$test_dir/agent-$new_version.jar"

echo "2. 修改版本信息..."
# 创建临时目录
temp_dir=$(mktemp -d)
cd "$temp_dir"

# 解压JAR
jar xf "$SCRIPT_DIR/$test_dir/agent-$new_version.jar"

# 修改MANIFEST.MF中的版本信息
if [ -f "META-INF/MANIFEST.MF" ]; then
    sed -i.bak "s/Implementation-Version: .*/Implementation-Version: $new_version/" META-INF/MANIFEST.MF
    sed -i.bak "s/Specification-Version: .*/Specification-Version: $new_version/" META-INF/MANIFEST.MF
    rm -f META-INF/MANIFEST.MF.bak
fi

# 修改version.properties中的版本信息
if [ -f "version.properties" ]; then
    sed -i.bak "s/version=.*/version=$new_version/" version.properties
    rm -f version.properties.bak
fi

# 重新打包JAR
jar cfm "$SCRIPT_DIR/$test_dir/agent-$new_version.jar" META-INF/MANIFEST.MF .

# 清理临时目录
cd "$SCRIPT_DIR"
rm -rf "$temp_dir"

echo "3. 计算文件信息..."
cd "$test_dir"
file_size=$(stat -f%z "agent-$new_version.jar" 2>/dev/null || stat -c%s "agent-$new_version.jar" 2>/dev/null)
file_hash=$(shasum -a 256 "agent-$new_version.jar" | cut -d' ' -f1)

echo "4. 创建版本信息文件..."
cat > version-info.json << EOF
{
  "version": "$new_version",
  "fileName": "agent-$new_version.jar",
  "fileSize": $file_size,
  "fileHash": "$file_hash",
  "platform": "ALL",
  "forceUpgrade": false,
  "releaseNotes": "测试升级版本 $new_version - 用于升级功能测试",
  "createdAt": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
}
EOF

echo "5. 创建使用说明..."
cat > README.md << EOF
# Agent升级测试包 - 版本 $new_version

## 文件信息
- **文件名**: agent-$new_version.jar
- **版本号**: $new_version
- **文件大小**: $file_size bytes
- **SHA256**: $file_hash

## 使用方法

### 1. 通过Web界面上传
1. 访问 http://localhost:3000/agent-versions
2. 点击"上传新版本"
3. 选择 \`agent-$new_version.jar\` 文件
4. 填写版本信息并上传

### 2. 手动测试升级
\`\`\`bash
# 在localtest目录下启动Agent
./start-agent.sh

# 上传新版本后，Agent会自动检测并升级
# 监控升级过程
tail -f logs/upgrade.log
\`\`\`

### 3. 验证升级结果
升级完成后，Agent会重新启动并报告新版本号。

## 版本变更
- 从 $current_version 升级到 $new_version
- 测试升级功能的完整流程
- 验证版本检测和自动升级机制

---
创建时间: $(date)
EOF

cd ..

echo ""
echo "========================================="
echo "升级测试包创建完成！"
echo "========================================="
echo "测试包位置: $test_dir/"
echo "文件信息:"
echo "  - agent-$new_version.jar ($file_size bytes)"
echo "  - version-info.json (版本信息)"
echo "  - README.md (使用说明)"
echo ""
echo "下一步操作:"
echo "1. 启动服务器: cd ../../server && mvn spring-boot:run"
echo "2. 启动Agent: ./start-agent.sh"
echo "3. 上传新版本: 访问 http://localhost:3000/agent-versions"
echo "4. 观察升级过程: tail -f logs/upgrade.log"
echo ""