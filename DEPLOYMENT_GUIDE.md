# LightScript 部署指南

## 系统要求

### 服务器端
- **操作系统**: Windows/Linux/macOS
- **Java**: JDK 1.8 或更高版本
- **数据库**: MySQL 8.0+
- **内存**: 建议 2GB 以上
- **磁盘**: 建议 10GB 以上可用空间

### 客户端
- **操作系统**: Windows/Linux
- **Java**: JDK 1.8 或更高版本
- **内存**: 建议 512MB 以上
- **网络**: 能够访问服务器端口

## 详细部署步骤

### 1. 环境准备

#### 安装 JDK 1.8
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install openjdk-8-jdk

# CentOS/RHEL
sudo yum install java-1.8.0-openjdk-devel

# Windows
# 下载并安装 Oracle JDK 8 或 OpenJDK 8
```

#### 安装 MySQL
```bash
# Ubuntu/Debian
sudo apt install mysql-server

# CentOS/RHEL
sudo yum install mysql-server

# 启动 MySQL 服务
sudo systemctl start mysql
sudo systemctl enable mysql
```

### 2. 数据库配置

#### 创建数据库和用户
```sql
-- 登录 MySQL
mysql -u root -p

-- 创建数据库
CREATE DATABASE lightscript CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建用户（可选，也可以使用 root）
CREATE USER 'lightscript'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON lightscript.* TO 'lightscript'@'localhost';
FLUSH PRIVILEGES;

-- 退出
EXIT;
```

### 3. 项目构建

#### 下载源码
```bash
git clone <your-repository-url>
cd lightScript
```

#### 配置数据库连接
编辑 `server/src/main/resources/application.yml`：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/lightscript?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
    username: lightscript  # 或 root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
```

#### 构建项目
```bash
# 安装 Maven（如果未安装）
# Ubuntu/Debian: sudo apt install maven
# CentOS/RHEL: sudo yum install maven
# Windows: 下载并安装 Apache Maven

# 构建项目
mvn clean package -DskipTests
```

### 4. 服务器端部署

#### 方式一：直接运行
```bash
cd server/target
java -jar server-*.jar
```

#### 方式二：后台运行
```bash
# Linux
nohup java -jar server/target/server-*.jar > server.log 2>&1 &

# 或使用 systemd 服务
sudo tee /etc/systemd/system/lightscript.service > /dev/null <<EOF
[Unit]
Description=LightScript Server
After=network.target mysql.service

[Service]
Type=simple
User=lightscript
WorkingDirectory=/opt/lightscript
ExecStart=/usr/bin/java -jar /opt/lightscript/server.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable lightscript
sudo systemctl start lightscript
```

#### 方式三：Docker 部署
创建 `Dockerfile`：
```dockerfile
FROM openjdk:8-jre-alpine
VOLUME /tmp
COPY server/target/server-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
```

创建 `docker-compose.yml`：
```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: rootpassword
      MYSQL_DATABASE: lightscript
      MYSQL_USER: lightscript
      MYSQL_PASSWORD: password
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql

  lightscript:
    build: .
    ports:
      - "8080:8080"
    depends_on:
      - mysql
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/lightscript?useSSL=false&serverTimezone=UTC
      SPRING_DATASOURCE_USERNAME: lightscript
      SPRING_DATASOURCE_PASSWORD: password

volumes:
  mysql_data:
```

运行：
```bash
docker-compose up -d
```

### 5. 客户端部署

#### 准备客户端文件
```bash
# 复制客户端 jar 文件到目标机器
scp agent/target/agent-*-jar-with-dependencies.jar user@target-host:/opt/lightscript/
```

#### 创建启动脚本

**Windows (start-agent.bat):**
```batch
@echo off
set LS_SERVER=http://your-server:8080
set LS_REGISTER_TOKEN=dev-register-token
java -jar agent-*-jar-with-dependencies.jar
pause
```

**Linux (start-agent.sh):**
```bash
#!/bin/bash
export LS_SERVER=http://your-server:8080
export LS_REGISTER_TOKEN=dev-register-token
java -jar agent-*-jar-with-dependencies.jar
```

#### 创建系统服务（Linux）
```bash
sudo tee /etc/systemd/system/lightscript-agent.service > /dev/null <<EOF
[Unit]
Description=LightScript Agent
After=network.target

[Service]
Type=simple
User=lightscript
WorkingDirectory=/opt/lightscript
Environment=LS_SERVER=http://your-server:8080
Environment=LS_REGISTER_TOKEN=dev-register-token
ExecStart=/usr/bin/java -jar /opt/lightscript/agent.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable lightscript-agent
sudo systemctl start lightscript-agent
```

### 6. 反向代理配置（推荐）

#### Nginx 配置
```nginx
server {
    listen 80;
    server_name your-domain.com;
    
    # 重定向到 HTTPS
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name your-domain.com;
    
    ssl_certificate /path/to/your/cert.pem;
    ssl_certificate_key /path/to/your/key.pem;
    
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 7. 防火墙配置

#### Linux (iptables)
```bash
# 允许 HTTP 和 HTTPS
sudo iptables -A INPUT -p tcp --dport 80 -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 443 -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 8080 -j ACCEPT

# 保存规则
sudo iptables-save > /etc/iptables/rules.v4
```

#### Linux (firewalld)
```bash
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload
```

## 验证部署

### 1. 检查服务状态
```bash
# 检查服务器是否启动
curl http://localhost:8080/actuator/health

# 检查数据库连接
mysql -u lightscript -p lightscript -e "SHOW TABLES;"
```

### 2. 访问 Web 界面
打开浏览器访问：`http://your-server:8080`

使用默认账号登录：
- 管理员：admin / admin123
- 普通用户：user / user123

### 3. 验证客户端连接
启动客户端后，在 Web 界面的"客户端管理"页面应该能看到新注册的客户端。

## 故障排除

### 常见问题

#### 1. 数据库连接失败
```bash
# 检查 MySQL 服务状态
sudo systemctl status mysql

# 检查端口是否开放
netstat -tlnp | grep 3306

# 测试连接
mysql -h localhost -u lightscript -p
```

#### 2. 服务器启动失败
```bash
# 查看日志
tail -f server.log

# 检查端口占用
netstat -tlnp | grep 8080
```

#### 3. 客户端无法连接
```bash
# 检查网络连通性
telnet your-server 8080

# 检查防火墙
sudo iptables -L
```

### 日志文件位置
- 服务器日志：`server.log` 或 `/var/log/lightscript/`
- 客户端日志：控制台输出或指定的日志文件

## 性能优化

### JVM 参数调优
```bash
# 服务器端
java -Xms1g -Xmx2g -XX:+UseG1GC -jar server.jar

# 客户端
java -Xms128m -Xmx512m -jar agent.jar
```

### 数据库优化
```sql
-- 创建索引
CREATE INDEX idx_agent_status ON agents(status);
CREATE INDEX idx_task_status ON tasks(status);
CREATE INDEX idx_task_agent ON tasks(agent_id);
```

## 安全建议

1. **修改默认密码**：首次登录后立即修改默认用户密码
2. **使用 HTTPS**：在生产环境中配置 SSL 证书
3. **限制网络访问**：使用防火墙限制不必要的网络访问
4. **定期备份**：设置数据库自动备份
5. **更新注册令牌**：修改默认的注册令牌

## 监控和维护

### 系统监控
```bash
# 监控系统资源
top
htop
iostat

# 监控数据库
mysqladmin processlist
mysqladmin status
```

### 日志轮转
```bash
# 配置 logrotate
sudo tee /etc/logrotate.d/lightscript > /dev/null <<EOF
/opt/lightscript/logs/*.log {
    daily
    missingok
    rotate 30
    compress
    notifempty
    create 644 lightscript lightscript
}
EOF
```

这个部署指南提供了完整的部署流程，从环境准备到生产环境配置，帮助用户成功部署 LightScript 系统。
