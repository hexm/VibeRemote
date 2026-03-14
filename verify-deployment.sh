#!/bin/bash

# 验证部署状态脚本

SERVER_IP="8.138.114.34"
SERVER_USER="root"

echo "🔍 验证 LightScript 部署状态..."
echo "📡 服务器: $SERVER_IP"
echo ""

# 检查服务器连接
echo "🌐 检查服务器连接..."
if ping -c 1 "$SERVER_IP" >/dev/null 2>&1; then
    echo "✅ 服务器连接正常"
else
    echo "❌ 服务器连接失败"
    exit 1
fi

# 检查 HTTP 服务
echo "🔧 检查 HTTP 服务..."
ssh "$SERVER_USER@$SERVER_IP" "systemctl status nginx || systemctl status apache2 || systemctl status httpd" 2>/dev/null

# 检查文件部署
echo "📁 检查文件部署..."
echo "门户文件:"
ssh "$SERVER_USER@$SERVER_IP" "ls -la /var/www/html/ | head -10"

echo ""
echo "安装包文件:"
ssh "$SERVER_USER@$SERVER_IP" "ls -la /var/www/html/agent/release/"

echo ""
echo "安装脚本:"
ssh "$SERVER_USER@$SERVER_IP" "ls -la /var/www/html/scripts/"

# 测试 HTTP 访问
echo ""
echo "🌐 测试 HTTP 访问..."
echo "测试首页:"
curl -I "http://$SERVER_IP/" 2>/dev/null | head -1 || echo "❌ 首页访问失败"

echo "测试客户端安装页面:"
curl -I "http://$SERVER_IP/client-install.html" 2>/dev/null | head -1 || echo "❌ 客户端安装页面访问失败"

echo "测试安装包下载:"
curl -I "http://$SERVER_IP/agent/release/lightscript-agent-0.5.0-macos-arm64.tar.gz" 2>/dev/null | head -1 || echo "❌ 安装包下载失败"

echo "测试安装脚本:"
curl -I "http://$SERVER_IP/scripts/install-linux.sh" 2>/dev/null | head -1 || echo "❌ 安装脚本访问失败"

echo ""
echo "📋 可用链接:"
echo "  🏠 首页: http://$SERVER_IP/"
echo "  📱 客户端安装: http://$SERVER_IP/client-install.html"
echo "  🖥️  服务端部署: http://$SERVER_IP/server-deploy.html"
echo "  📚 文档: http://$SERVER_IP/docs.html"
echo "  ⚙️  管理后台: http://$SERVER_IP:8080/admin"

echo ""
echo "🚀 一键安装命令测试:"
echo "Linux:"
echo "curl -fsSL http://$SERVER_IP/scripts/install-linux.sh | sudo bash -s -- --server=http://$SERVER_IP:8080"

echo ""
echo "macOS:"
echo "curl -fsSL http://$SERVER_IP/scripts/install-macos.sh | sudo bash -s -- --server=http://$SERVER_IP:8080"

echo ""
echo "✅ 验证完成!"