#!/bin/bash

# SSH免密登录设置脚本
# 用途：配置到阿里云服务器的SSH免密登录

set -e

SERVER_IP="8.138.114.34"
SERVER_USER="root"  # 根据实际情况修改用户名

echo "=========================================="
echo "SSH免密登录设置"
echo "=========================================="
echo "服务器地址: ${SERVER_IP}"
echo "用户名: ${SERVER_USER}"
echo ""

# 检查是否已有SSH密钥
if [ ! -f ~/.ssh/id_rsa ]; then
    echo "📝 生成SSH密钥对..."
    ssh-keygen -t rsa -b 4096 -C "lightscript-deploy" -f ~/.ssh/id_rsa -N ""
    echo "✅ SSH密钥生成完成"
else
    echo "✅ SSH密钥已存在"
fi

echo ""
echo "📤 复制公钥到服务器..."
echo "⚠️  这一步需要输入服务器密码（仅此一次）"
echo ""

# 复制公钥到服务器
ssh-copy-id -i ~/.ssh/id_rsa.pub ${SERVER_USER}@${SERVER_IP}

echo ""
echo "🧪 测试SSH连接..."
if ssh -o BatchMode=yes -o ConnectTimeout=5 ${SERVER_USER}@${SERVER_IP} "echo '连接成功'" 2>/dev/null; then
    echo "✅ SSH免密登录配置成功！"
    echo ""
    echo "现在可以运行部署脚本了："
    echo "  ./scripts/mac/deploy-to-aliyun.sh"
else
    echo "❌ SSH连接测试失败，请检查配置"
    exit 1
fi
