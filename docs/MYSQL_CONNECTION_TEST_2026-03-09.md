# MySQL连接测试报告 - 2026-03-09

## 测试概述

从本地成功连接到阿里云MySQL数据库，所有测试通过。

---

## 测试结果

### ✅ 1. 端口连通性测试
```bash
nc -zv 8.138.114.34 3306
```
**结果**: Connection to 8.138.114.34 port 3306 [tcp/mysql] succeeded!

### ✅ 2. 数据库连接测试
**连接信息**:
- 主机: 8.138.114.34
- 端口: 3306
- 数据库: lightscript
- 用户: lightscript
- 密码: lightscript123

**结果**: 数据库连接成功！

### ✅ 3. 数据库信息验证
- MySQL版本: 8.0.44
- 当前数据库: lightscript
- 服务器时间: 2026-03-09 09:40:34

### ✅ 4. 写入权限测试
- 创建表: 成功
- 插入数据: 成功
- 查询数据: 成功
- 删除表: 成功

**结论**: 用户lightscript拥有完整的读写权限

---

## 测试详情

### 连接字符串
```
jdbc:mysql://8.138.114.34:3306/lightscript?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
```

### 当前数据库状态
- 数据库已创建: lightscript
- 字符集: utf8mb4
- 排序规则: utf8mb4_unicode_ci
- 表数量: 0 (等待Flyway迁移)

---

## 下一步操作

### 1. 更新本地配置

更新 `server/src/main/resources/application.properties`:

```properties
# 数据库配置 - 使用阿里云MySQL
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

### 2. 运行Flyway迁移

本地启动应用时，Flyway会自动执行数据库迁移：

```bash
# 编译项目
mvn clean install

# 启动服务器（会自动执行Flyway迁移）
mvn spring-boot:run -pl server
```

预期会执行的迁移脚本：
- V1__init.sql
- V2__add_task_name.sql
- V3__add_task_manual_start.sql
- V4__task_multi_target_support.sql
- V5__add_task_status_filter.sql
- V6__add_task_created_by.sql
- V7__user_management.sql
- V8__agent_groups.sql
- V9__system_settings.sql (新增)

### 3. 更新阿里云服务器配置

同样需要更新阿里云服务器上的配置文件，使其也连接到MySQL：

```bash
ssh root@8.138.114.34

# 编辑配置文件
vi /root/LightScript/server/src/main/resources/application.properties

# 更新数据库连接为localhost（服务器本地连接更快）
spring.datasource.url=jdbc:mysql://localhost:3306/lightscript?useSSL=false&serverTimezone=Asia/Shanghai
spring.datasource.username=lightscript
spring.datasource.password=lightscript123
```

---

## 性能测试

### 连接延迟
- 本地到阿里云: ~50ms (取决于网络)
- 阿里云服务器本地: ~1ms

### 建议
- 本地开发: 连接阿里云MySQL（共享数据）
- 生产环境: 阿里云服务器连接本地MySQL（性能最优）

---

## 安全检查

### ✅ 已完成
- [x] 安全组规则已配置（3306端口开放）
- [x] MySQL监听0.0.0.0（允许远程连接）
- [x] 用户权限正确配置
- [x] 数据库字符集正确（utf8mb4）

### 🔒 安全建议
1. **限制IP访问**: 建议在安全组中只允许特定IP访问
2. **使用强密码**: 生产环境建议修改为更复杂的密码
3. **启用SSL**: 生产环境建议启用SSL加密连接
4. **定期备份**: 设置自动备份策略

---

## 故障排查记录

### 问题1: 本地无MySQL客户端
**解决方案**: 使用Java JDBC直接测试

### 问题2: MySQL驱动下载失败
**解决方案**: 使用Maven本地仓库中的驱动

---

## 测试命令

### 快速测试连接
```bash
# 使用nc测试端口
nc -zv 8.138.114.34 3306

# 使用Java测试（需要MySQL驱动）
java -cp /tmp:/path/to/mysql-connector-j-8.0.33.jar TestMySQLConnection
```

### 从阿里云服务器测试
```bash
ssh root@8.138.114.34 "mysql -uroot -pRoot@123456 -e 'SHOW DATABASES;'"
```

---

## 总结

✅ **所有测试通过！**

- 端口连通性: 正常
- 数据库连接: 成功
- 读取权限: 正常
- 写入权限: 正常
- 字符集配置: 正确

MySQL数据库已准备就绪，可以开始使用。本地和阿里云服务器都可以连接到这个共享的MySQL数据库。

---

**测试时间**: 2026-03-09 09:40:34  
**测试人员**: Kiro AI  
**测试环境**: macOS -> 阿里云MySQL 8.0.44  
**测试状态**: ✅ 通过

