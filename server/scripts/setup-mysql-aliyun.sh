#!/bin/bash

# 阿里云MySQL安装和配置脚本
# 用途：在阿里云服务器上安装MySQL，供本地和云服务器共享使用

set -e

ALIYUN_HOST="8.138.114.34"
ALIYUN_USER="root"

echo "=========================================="
echo "阿里云MySQL安装和配置"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${YELLOW}步骤1: 连接到阿里云服务器并安装MySQL${NC}"
ssh ${ALIYUN_USER}@${ALIYUN_HOST} << 'ENDSSH'

set -e

echo "检查操作系统版本..."
cat /etc/os-release

echo ""
echo "更新软件包列表..."
dnf update -y

echo ""
echo "安装MySQL Server..."
dnf install -y mysql-server

echo ""
echo "启动MySQL服务..."
systemctl start mysqld
systemctl enable mysqld

echo ""
echo "检查MySQL服务状态..."
systemctl status mysqld --no-pager

echo ""
echo "MySQL版本信息:"
mysql --version

ENDSSH

echo ""
echo -e "${GREEN}✓ MySQL安装完成${NC}"
echo ""

echo -e "${YELLOW}步骤2: 配置MySQL安全设置和远程访问${NC}"
ssh ${ALIYUN_USER}@${ALIYUN_HOST} << 'ENDSSH'

set -e

# 生成随机密码
MYSQL_ROOT_PASSWORD=$(openssl rand -base64 12)
echo "生成的MySQL root密码: $MYSQL_ROOT_PASSWORD"
echo "$MYSQL_ROOT_PASSWORD" > /root/mysql_root_password.txt
chmod 600 /root/mysql_root_password.txt

echo ""
echo "配置MySQL root用户密码..."
mysql << EOF
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '$MYSQL_ROOT_PASSWORD';
FLUSH PRIVILEGES;
EOF

echo ""
echo "创建LightScript数据库和用户..."
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" << EOF
-- 创建数据库
CREATE DATABASE IF NOT EXISTS lightscript CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建专用用户（允许远程访问）
CREATE USER IF NOT EXISTS 'lightscript'@'%' IDENTIFIED WITH mysql_native_password BY 'lightscript123';

-- 授予权限
GRANT ALL PRIVILEGES ON lightscript.* TO 'lightscript'@'%';
FLUSH PRIVILEGES;

-- 显示数据库
SHOW DATABASES;

-- 显示用户
SELECT user, host FROM mysql.user WHERE user IN ('root', 'lightscript');
EOF

echo ""
echo "配置MySQL允许远程连接..."
# 备份原配置
cp /etc/my.cnf /etc/my.cnf.backup 2>/dev/null || true

# 修改或添加bind-address配置
if grep -q "^\[mysqld\]" /etc/my.cnf; then
    # 如果已有[mysqld]段，在其后添加bind-address
    sed -i '/^\[mysqld\]/a bind-address = 0.0.0.0' /etc/my.cnf
else
    # 如果没有[mysqld]段，添加整个段
    echo -e "\n[mysqld]\nbind-address = 0.0.0.0" >> /etc/my.cnf
fi

echo ""
echo "重启MySQL服务..."
systemctl restart mysqld

echo ""
echo "验证MySQL配置..."
netstat -tlnp | grep 3306 || ss -tlnp | grep 3306

echo ""
echo "MySQL配置完成！"
echo ""
echo "=========================================="
echo "数据库连接信息："
echo "=========================================="
echo "主机: 8.138.114.34"
echo "端口: 3306"
echo "数据库: lightscript"
echo "用户名: lightscript"
echo "密码: lightscript123"
echo ""
echo "Root密码已保存到: /root/mysql_root_password.txt"
echo "Root密码: $MYSQL_ROOT_PASSWORD"
echo "=========================================="

ENDSSH

echo ""
echo -e "${YELLOW}步骤3: 配置阿里云安全组（开放3306端口）${NC}"
echo ""
echo -e "${RED}重要：请手动在阿里云控制台配置安全组规则${NC}"
echo "1. 登录阿里云控制台"
echo "2. 进入 ECS 实例管理"
echo "3. 找到实例 8.138.114.34"
echo "4. 点击「安全组配置」"
echo "5. 添加入方向规则："
echo "   - 端口范围: 3306/3306"
echo "   - 授权对象: 0.0.0.0/0 (或指定你的本地IP)"
echo "   - 协议类型: TCP"
echo "   - 优先级: 1"
echo ""

echo -e "${YELLOW}步骤4: 测试本地连接${NC}"
echo ""
echo "请先配置阿里云安全组，然后运行以下命令测试连接："
echo ""
echo "mysql -h 8.138.114.34 -P 3306 -u lightscript -plightscript123 lightscript"
echo ""

echo -e "${YELLOW}步骤5: 更新本地配置${NC}"
echo ""
echo "更新 server/src/main/resources/application.properties:"
echo ""
echo "spring.datasource.url=jdbc:mysql://8.138.114.34:3306/lightscript?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
echo "spring.datasource.username=lightscript"
echo "spring.datasource.password=lightscript123"
echo ""

echo -e "${GREEN}=========================================="
echo "MySQL安装和配置完成！"
echo "==========================================${NC}"
echo ""
echo "下一步："
echo "1. 配置阿里云安全组开放3306端口"
echo "2. 测试本地连接"
echo "3. 更新应用配置文件"
echo "4. 重启应用"
echo ""

