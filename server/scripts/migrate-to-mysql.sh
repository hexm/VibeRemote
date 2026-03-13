#!/bin/bash

# 数据迁移脚本：从H2迁移到MySQL

set -e

echo "=========================================="
echo "数据迁移：H2 -> MySQL"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# 配置
MYSQL_HOST="8.138.114.34"
MYSQL_PORT="3306"
MYSQL_DB="lightscript"
MYSQL_USER="lightscript"
MYSQL_PASS="lightscript123"
H2_DB_FILE="./data/lightscript.mv.db"

echo -e "${YELLOW}步骤1: 检查H2数据库${NC}"
if [ -f "$H2_DB_FILE" ]; then
    echo "✅ 找到H2数据库文件: $H2_DB_FILE"
    ls -lh "$H2_DB_FILE"
else
    echo "⚠️  未找到H2数据库文件，可能是全新安装"
    echo "将直接使用MySQL创建新数据库"
fi

echo ""
echo -e "${YELLOW}步骤2: 备份当前配置${NC}"
cp server/src/main/resources/application.yml server/src/main/resources/application.yml.h2.backup
echo "✅ 配置文件已备份到: application.yml.h2.backup"

echo ""
echo -e "${YELLOW}步骤3: 更新配置文件使用MySQL${NC}"
cat > server/src/main/resources/application.yml << 'EOF'
server:
  port: 8080
  servlet:
    encoding:
      charset: UTF-8
      enabled: true
      force: true
spring:
  application:
    name: lightscript-server
  datasource:
    # MySQL数据库配置
    url: jdbc:mysql://8.138.114.34:3306/lightscript?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=utf8mb4
    username: lightscript
    password: lightscript123
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
        format_sql: false
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
  security:
    user:
      name: admin
      password: admin123
      roles: ADMIN
logging:
  level:
    root: INFO
    com.example.lightscript.server: INFO
    org.hibernate.SQL: WARN
    org.hibernate.type.descriptor.sql.BasicBinder: WARN
    org.springframework.web: WARN
  file:
    name: server/logs/server.log
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

lightscript:
  jwt:
    secret: lightscript-jwt-secret-key-2024
    expiration: 86400000  # 24 hours
  register:
    token: dev-register-token
  log:
    storage:
      path: logs/tasks  # 日志文件存储根目录
    retention:
      days: 90  # 日志保留天数
EOF

echo "✅ 配置文件已更新为MySQL"

echo ""
echo -e "${YELLOW}步骤4: 测试MySQL连接${NC}"
if nc -zv $MYSQL_HOST $MYSQL_PORT 2>&1 | grep -q "succeeded"; then
    echo "✅ MySQL连接正常"
else
    echo -e "${RED}❌ MySQL连接失败${NC}"
    exit 1
fi

echo ""
echo -e "${YELLOW}步骤5: 编译项目${NC}"
mvn clean install -DskipTests -f server/pom.xml
if [ $? -eq 0 ]; then
    echo "✅ 项目编译成功"
else
    echo -e "${RED}❌ 项目编译失败${NC}"
    exit 1
fi

echo ""
echo -e "${YELLOW}步骤6: 运行Flyway迁移${NC}"
echo "Flyway将自动创建所有表结构..."
mvn flyway:migrate -f server/pom.xml
if [ $? -eq 0 ]; then
    echo "✅ Flyway迁移成功"
else
    echo -e "${RED}❌ Flyway迁移失败${NC}"
    exit 1
fi

echo ""
echo -e "${YELLOW}步骤7: 验证数据库表${NC}"
echo "查询MySQL数据库表..."
ssh root@$MYSQL_HOST "mysql -u$MYSQL_USER -p$MYSQL_PASS $MYSQL_DB -e 'SHOW TABLES;'"

echo ""
echo -e "${GREEN}=========================================="
echo "✅ 数据迁移完成！"
echo "==========================================${NC}"
echo ""
echo "数据库信息："
echo "  主机: $MYSQL_HOST"
echo "  端口: $MYSQL_PORT"
echo "  数据库: $MYSQL_DB"
echo "  用户: $MYSQL_USER"
echo ""
echo "下一步："
echo "1. 启动服务器: mvn spring-boot:run -pl server"
echo "2. 访问应用: http://localhost:8080"
echo "3. 如需恢复H2配置: cp server/src/main/resources/application.yml.h2.backup server/src/main/resources/application.yml"
echo ""

