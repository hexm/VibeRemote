#!/bin/bash

# 阿里云MySQL快速安装脚本（跳过系统更新）

set -e

ALIYUN_HOST="8.138.114.34"
ALIYUN_USER="root"

echo "=========================================="
echo "阿里云MySQL快速安装"
echo "=========================================="
echo ""

ssh ${ALIYUN_USER}@${ALIYUN_HOST} << 'ENDSSH'

set -e

echo "安装MySQL Server..."
dnf install -y mysql-server

echo ""
echo "启动MySQL服务..."
systemctl start mysqld
systemctl enable mysqld

echo ""
echo "检查MySQL服务状态..."
systemctl status mysqld --no-pager | head -20

echo ""
echo "MySQL版本信息:"
mysql --version

echo ""
echo "生成MySQL root密码..."
MYSQL_ROOT_PASSWORD=$(openssl rand -base64 12)
echo "MySQL root密码: $MYSQL_ROOT_PASSWORD"
echo "$MYSQL_ROOT_PASSWORD" > /root/mysql_root_password.txt
chmod 600 /root/mysql_root_password.txt

echo ""
echo "配置MySQL root用户..."
mysql << EOF
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '$MYSQL_ROOT_PASSWORD';
FLUSH PRIVILEGES;
EOF

echo ""
echo "创建LightScript数据库和用户..."
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" << EOF
CREATE DATABASE IF NOT EXISTS lightscript CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'lightscript'@'%' IDENTIFIED WITH mysql_native_password BY 'lightscript123';
GRANT ALL PRIVILEGES ON lightscript.* TO 'lightscript'@'%';
FLUSH PRIVILEGES;
SHOW DATABASES;
SELECT user, host FROM mysql.user WHERE user IN ('root', 'lightscript');
EOF

echo ""
echo "配置MySQL允许远程连接..."
cp /etc/my.cnf /etc/my.cnf.backup 2>/dev/null || true

# 添加bind-address配置
if ! grep -q "bind-address" /etc/my.cnf; then
    echo "" >> /etc/my.cnf
    echo "[mysqld]" >> /etc/my.cnf
    echo "bind-address = 0.0.0.0" >> /etc/my.cnf
fi

echo ""
echo "重启MySQL服务..."
systemctl restart mysqld

echo ""
echo "验证MySQL监听端口..."
ss -tlnp | grep 3306 || netstat -tlnp | grep 3306

echo ""
echo "=========================================="
echo "MySQL安装完成！"
echo "=========================================="
echo ""
echo "数据库连接信息："
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
echo "=========================================="
echo "下一步操作："
echo "=========================================="
echo ""
echo "1. 配置阿里云安全组开放3306端口"
echo "   - 登录阿里云控制台"
echo "   - ECS实例 -> 安全组配置"
echo "   - 添加入方向规则："
echo "     端口: 3306/3306"
echo "     授权对象: 0.0.0.0/0"
echo "     协议: TCP"
echo ""
echo "2. 测试本地连接："
echo "   mysql -h 8.138.114.34 -P 3306 -u lightscript -plightscript123 lightscript"
echo ""
echo "3. 更新应用配置："
echo "   spring.datasource.url=jdbc:mysql://8.138.114.34:3306/lightscript?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
echo "   spring.datasource.username=lightscript"
echo "   spring.datasource.password=lightscript123"
echo ""

