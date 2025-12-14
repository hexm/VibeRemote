#!/bin/bash

echo "========================================"
echo "配置 IDEA 内置 Maven 环境变量"
echo "========================================"
echo

# IDEA Maven 路径
IDEA_MAVEN_HOME="/Applications/IntelliJ IDEA CE.app/Contents/plugins/maven/lib/maven3"

# 检查 IDEA Maven 是否存在
if [ ! -d "$IDEA_MAVEN_HOME" ]; then
    echo "❌ 未找到 IDEA 内置 Maven，请检查 IDEA 是否已安装"
    echo "预期路径: $IDEA_MAVEN_HOME"
    exit 1
fi

echo "✅ 找到 IDEA 内置 Maven: $IDEA_MAVEN_HOME"

# 检查 Maven 版本
echo "Maven 版本信息:"
"$IDEA_MAVEN_HOME/bin/mvn" -version
echo

# 备份现有的 shell 配置文件
if [ -f ~/.zshrc ]; then
    cp ~/.zshrc ~/.zshrc.backup.$(date +%Y%m%d_%H%M%S)
    echo "✅ 已备份 ~/.zshrc"
fi

# 检查是否已经配置过
if grep -q "IDEA Maven" ~/.zshrc 2>/dev/null; then
    echo "⚠️  检测到已有 IDEA Maven 配置，正在更新..."
    # 移除旧的配置
    sed -i '' '/# IDEA Maven Configuration/,/# End IDEA Maven Configuration/d' ~/.zshrc
fi

# 添加 Maven 环境变量到 .zshrc
echo "正在添加环境变量到 ~/.zshrc..."
cat >> ~/.zshrc << EOF

# IDEA Maven Configuration
export MAVEN_HOME="$IDEA_MAVEN_HOME"
export PATH="\$MAVEN_HOME/bin:\$PATH"
# End IDEA Maven Configuration
EOF

echo "✅ 环境变量已添加到 ~/.zshrc"

# 为当前会话设置环境变量
export MAVEN_HOME="$IDEA_MAVEN_HOME"
export PATH="$MAVEN_HOME/bin:$PATH"

echo
echo "========================================"
echo "🎉 配置完成！"
echo "========================================"
echo
echo "Maven 信息:"
echo "- Maven Home: $MAVEN_HOME"
echo "- Maven 版本: $(mvn -version 2>/dev/null | head -n 1 | cut -d' ' -f3)"
echo
echo "使用方法:"
echo "1. 重新打开终端，或运行: source ~/.zshrc"
echo "2. 验证安装: mvn -version"
echo "3. 现在可以在任何目录使用 mvn 命令了"
echo
echo "恢复方法:"
echo "如果需要恢复原配置: mv ~/.zshrc.backup.* ~/.zshrc"
echo