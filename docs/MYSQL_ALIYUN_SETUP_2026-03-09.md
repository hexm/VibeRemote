# 阿里云MySQL安装配置完成 - 2026-03-09

## 概述

已在阿里云服务器上成功安装和配置MySQL 8.0.44，供本地和云服务器共享使用。

---

## 安装信息

### 服务器信息
- **主机**: 8.138.114.34
- **操作系统**: Alibaba Cloud Linux 3.2104 U12 (OpenAnolis Edition)
- **MySQL版本**: 8.0.44

### 数据库连接信息
```
主机: 8.138.114.34
端口: 3306
数据库: lightscript
用户名: lightscript
密码: lightscript123
```

### Root账户
```
用户名: root
密码: Root@123456
密码文件: /root/mysql_root_password.txt
```

---

## 已完成配置

### 1. MySQL安装
- ✅ 安装MySQL Server 8.0.44
- ✅ 启动并设置开机自启动
- ✅ 服务状态: active (running)

### 2. 数据库和用户
- ✅ 创建数据库: `lightscript` (utf8mb4)
- ✅ 创建用户: `lightscript@%` (允许远程连接)
- ✅ 授予权限: ALL PRIVILEGES on lightscript.*

### 3. 远程访问配置
- ✅ 配置bind-address = 0.0.0.0
- ✅ MySQL监听在 0.0.0.0:3306
- ✅ 重启MySQL服务

---

## 下一步操作

### 1. 配置阿里云安全组（必须）

**重要**: 必须在阿里云控制台配置安全组规则，否则无法远程连接！

步骤：
1. 登录阿里云控制台: https://ecs.console.aliyun.com
2. 进入「云服务器 ECS」-> 「实例与镜像」-> 「实例」
3. 找到实例 8.138.114.34
4. 点击「更多」-> 「网络和安全组」-> 「安全组配置」
5. 点击「配置规则」
6. 点击「添加安全组规则」（入方向）
7. 填写规则：
   - **端口范围**: 3306/3306
   - **授权对象**: 0.0.0.0/0 (允许所有IP) 或指定你的本地IP
   - **协议类型**: TCP
   - **优先级**: 1
   - **描述**: MySQL远程访问

### 2. 测试本地连接

配置安全组后，在本地测试连接：

```bash
# 使用MySQL客户端测试
mysql -h 8.138.114.34 -P 3306 -u lightscript -plightscript123 lightscript

# 或使用telnet测试端口
telnet 8.138.114.34 3306
```

### 3. 更新应用配置

更新 `server/src/main/resources/application.properties`:

```properties
# 数据库配置
spring.datasource.url=jdbc:mysql://8.138.114.34:3306/lightscript?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
spring.datasource.username=lightscript
spring.datasource.password=lightscript123
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA配置
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect

# Flyway配置
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
```

### 4. 运行数据库迁移

本地和云服务器都需要运行Flyway迁移：

```bash
# 本地开发环境
mvn clean install
mvn spring-boot:run

# 云服务器（如果需要）
cd /root/LightScript
mvn flyway:migrate
```

---

## 验证步骤

### 1. 验证MySQL服务状态
```bash
ssh root@8.138.114.34 "systemctl status mysqld"
```

### 2. 验证监听端口
```bash
ssh root@8.138.114.34 "ss -tlnp | grep 3306"
```

### 3. 验证数据库
```bash
ssh root@8.138.114.34 "mysql -uroot -pRoot@123456 -e 'SHOW DATABASES;'"
```

### 4. 验证用户权限
```bash
ssh root@8.138.114.34 "mysql -uroot -pRoot@123456 -e \"SELECT user, host FROM mysql.user WHERE user='lightscript';\""
```

---

## 安全建议

### 1. 限制远程访问IP
建议在安全组规则中只允许特定IP访问，而不是0.0.0.0/0：
- 本地开发机IP
- 云服务器内网IP

### 2. 定期备份数据库
```bash
# 备份脚本示例
mysqldump -uroot -pRoot@123456 lightscript > /backup/lightscript_$(date +%Y%m%d).sql
```

### 3. 修改默认密码
生产环境建议修改为更复杂的密码：
```sql
ALTER USER 'lightscript'@'%' IDENTIFIED BY '你的复杂密码';
FLUSH PRIVILEGES;
```

### 4. 启用SSL连接（可选）
生产环境建议启用SSL加密连接。

---

## 故障排查

### 无法连接MySQL

1. **检查安全组规则**
   - 确认3306端口已开放
   - 确认授权对象包含你的IP

2. **检查MySQL服务**
   ```bash
   ssh root@8.138.114.34 "systemctl status mysqld"
   ```

3. **检查监听地址**
   ```bash
   ssh root@8.138.114.34 "ss -tlnp | grep 3306"
   ```
   应该显示 `0.0.0.0:3306`

4. **检查防火墙**
   ```bash
   ssh root@8.138.114.34 "firewall-cmd --list-ports"
   ```

### 权限问题

如果遇到权限错误，重新授权：
```sql
GRANT ALL PRIVILEGES ON lightscript.* TO 'lightscript'@'%';
FLUSH PRIVILEGES;
```

---

## 相关脚本

- `scripts/mac/install-mysql-aliyun.sh` - MySQL安装脚本
- `scripts/mac/configure-mysql-aliyun.sh` - MySQL配置脚本

---

## 总结

✅ MySQL 8.0.44 已成功安装在阿里云服务器
✅ 数据库 `lightscript` 已创建
✅ 用户 `lightscript` 已创建并授权
✅ 远程访问已配置（bind-address = 0.0.0.0）
⚠️ 需要手动配置阿里云安全组开放3306端口

配置安全组后，本地和云服务器都可以连接到这个共享的MySQL数据库。

---

**文档创建时间**: 2026-03-09  
**MySQL版本**: 8.0.44  
**服务器**: 8.138.114.34

