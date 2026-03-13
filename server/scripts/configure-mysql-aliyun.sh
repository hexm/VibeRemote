#!/bin/bash

# 配置已安装的MySQL

set -e

ALIYUN_HOST="8.138.114.34"
ALIYUN_USER="root"

echo "=========================================="
echo "配置阿里云MySQL"
echo "=========================================="
echo ""

ssh ${ALIYUN_USER}@${ALIYUN_HOST} << 'ENDSSH'

set -e

echo "获取MySQL临时密码..."
TEMP_PASSWORD=$(grep 'temporary password' /var/log/mysqld.log | awk '{print $NF}' | tail -1)

if [ -z "$TEMP_PASSWORD" ]; then
    echo "未找到临时密码，尝试无密码登录..."
    # 如果没有临时密码，可能是全新安装，尝试无密码登录
    mysql -uroot --skip-password << EOF
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'Root@123456';
FLUSH PRIVILEGES;
EOF
    NEW_ROOT_PASSWORD="Root@123456"
else
    echo "找到临时密码，正在重置..."
    # 使用临时密码登录并重置
    NEW_ROOT_PASSWORD="Root@123456"
    mysql -uroot -p"$TEMP_PASSWORD" --connect-expired-password << EOF
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '$NEW_ROOT_PASSWORD';
FLUSH PRIVILEGES;
EOF
fi

echo "Root密码: $NEW_ROOT_PASSWORD"
echo "$NEW_ROOT_PASSWORD" > /root/mysql_root_password.txt
chmod 600 /root/mysql_root_password.txt

echo ""
echo "创建LightScript数据库和用户..."
mysql -uroot -p"$NEW_ROOT_PASSWORD" << EOF
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

# 检查并添加bind-address配置
if grep -q "^\[mysqld\]" /etc/my.cnf; then
    # 如果已有[mysqld]段，检查是否有bind-address
    if ! grep -q "bind-address" /etc/my.cnf; then
        sed -i '/^\[mysqld\]/a bind-address = 0.0.0.0' /etc/my.cnf
    fi
else
    # 如果没有[mysqld]段，添加整个段
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
echo "MySQL配置完成！"
echo "=========================================="
echo ""
echo "数据库连接信息："
echo "主机: 8.138.114.34"
echo "端口: 3306"
echo "数据库: lightscript"
echo "用户名: lightscript"
echo "密码: lightscript123"
echo ""
echo "Root密码: $NEW_ROOT_PASSWORD"
echo "Root密码已保存到: /root/mysql_root_password.txt"
echo "=========================================="

ENDSSH

echo ""
echo "=========================================="
echo "下一步操作："
echo "=========================================="
echo ""
echo "1. 配置阿里云安全组开放3306端口"
echo "   - 登录阿里云控制台: https://ecs.console.aliyun.com"
echo "   - 找到实例 8.138.114.34"
echo "   - 点击「安全组配置」"
echo "   - 添加入方向规则："
echo "     端口范围: 3306/3306"
echo "     授权对象: 0.0.0.0/0 (或你的本地IP)"
echo "     协议类型: TCP"
echo "     优先级: 1"
echo ""
echo "2. 测试本地连接："
echo "   mysql -h 8.138.114.34 -P 3306 -u lightscript -plightscript123 lightscript"
echo ""
echo "3. 更新应用配置文件："
echo "   server/src/main/resources/application.properties"
echo ""
echo "   spring.datasource.url=jdbc:mysql://8.138.114.34:3306/lightscript?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
echo "   spring.datasource.username=lightscript"
echo "   spring.datasource.password=lightscript123"
echo ""

