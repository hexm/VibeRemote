# H2数据库持久化配置指南

## 🎯 目标

让开发环境的服务器重启后，Agent无需重启即可继续工作。

## 🔧 解决方案

**将H2从内存模式改为文件模式**，数据持久化到磁盘。

---

## ✅ 配置步骤

### 1. 修改数据库配置

**文件**：`server/src/main/resources/application.yml`

**修改前（内存模式）**：
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:lightscript  # 内存模式
```

**修改后（文件模式）**：
```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/lightscript;MODE=MySQL;AUTO_SERVER=TRUE
    username: sa
    password: 
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
  h2:
    console:
      enabled: true  # 可选：Web控制台
```

### 2. 重新编译

```bash
cd d:\git\gitee\lightScript\server
mvn clean package -DskipTests
```

### 3. 重启服务器

```bash
cd d:\git\gitee\lightScript
start-server.bat
```

**首次启动时会自动创建**：
```
d:\git\gitee\lightScript\
└── data\
    └── lightscript.mv.db  ← H2数据文件
```

---

## 🧪 验证测试

### 自动测试脚本

```bash
d:\git\gitee\lightScript\test-server-restart.bat
```

### 手动测试步骤

#### 步骤1：启动系统

```bash
# 终端1：服务器
start-server.bat

# 终端2：Agent
start-agent.bat
```

**记录Agent ID**：
```
Agent ID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
```

#### 步骤2：创建测试任务

```bash
# 在Web界面创建任务，或使用curl
curl -X POST "http://localhost:8080/api/web/tasks/create?agentId=<agent-id>" ^
     -H "Content-Type: application/json" ^
     -d "{\"scriptLang\":\"cmd\",\"scriptContent\":\"echo Test\",\"timeoutSec\":60}"
```

#### 步骤3：重启服务器

```bash
# 在服务器终端按 Ctrl+C
# 然后重新启动
start-server.bat
```

#### 步骤4：验证Agent状态

**Agent控制台应该**：
- ✅ 继续发送心跳
- ✅ 无"令牌无效"错误
- ✅ 可以接收新任务

**Web界面应该**：
```
http://localhost:3000/agents.html
```
- ✅ Agent仍显示为ONLINE
- ✅ Agent ID与之前相同
- ✅ 最后心跳时间更新

**历史数据应该**：
- ✅ 之前创建的任务仍然可见
- ✅ 任务日志保留

---

## 📊 效果对比

| 特性 | 内存模式 | 文件模式 |
|-----|---------|---------|
| **服务器重启后Agent** | ❌ 需要重启 | ✅ 无需重启 |
| **历史数据** | ❌ 丢失 | ✅ 保留 |
| **开发便利性** | ❌ 差 | ✅ 好 |
| **性能** | ⭐⭐⭐⭐⭐ 极快 | ⭐⭐⭐⭐ 快 |
| **文件大小** | 0 | ~数MB |
| **生产适用** | ❌ 不适用 | ⚠️ 小型部署 |

---

## 💾 数据管理

### 数据文件位置

```
项目根目录/data/
├── lightscript.mv.db       # 主数据文件（必需）
└── lightscript.trace.db    # 跟踪日志（可选）
```

### 备份数据

```bash
# 简单备份
xcopy data\*.db backup\ /Y

# 带日期的备份
set BACKUP_DATE=%date:~0,4%%date:~5,2%%date:~8,2%
xcopy data\*.db backup\%BACKUP_DATE%\ /Y
```

### 清空数据（重新开始）

```bash
# 1. 停止服务器
# 2. 删除数据文件
del /Q data\lightscript.mv.db
# 3. 重启服务器（会自动创建新数据库）
start-server.bat
```

### 恢复数据

```bash
# 1. 停止服务器
# 2. 恢复备份文件
xcopy backup\lightscript.mv.db data\ /Y
# 3. 重启服务器
start-server.bat
```

---

## 🔍 H2 Web控制台

### 访问方式

配置已启用H2控制台，可以直接查看和管理数据库。

**URL**：http://localhost:8080/h2-console

**连接配置**：
```
Driver Class: org.h2.Driver
JDBC URL: jdbc:h2:file:./data/lightscript
User Name: sa
Password: (留空)
```

### 常用SQL查询

```sql
-- 查看所有Agent
SELECT * FROM agents ORDER BY created_at DESC;

-- 查看在线Agent
SELECT agent_id, hostname, os_type, status, last_heartbeat 
FROM agents 
WHERE status = 'ONLINE';

-- 查看最近的任务
SELECT * FROM tasks ORDER BY created_at DESC LIMIT 10;

-- 查看任务执行统计
SELECT status, COUNT(*) as count 
FROM tasks 
GROUP BY status;

-- 查看Agent的任务执行历史
SELECT t.task_id, t.script_lang, t.status, t.created_at, t.finished_at
FROM tasks t
WHERE t.agent_id = 'your-agent-id'
ORDER BY t.created_at DESC;
```

---

## ⚠️ 注意事项

### 1. Git版本控制

数据文件已配置为忽略（`.gitignore`）：
```
data/  # 不会提交到Git
```

### 2. 磁盘空间

- 初始大小：~1-5 MB
- 随使用增长：取决于任务量
- 建议监控：定期检查data目录大小

### 3. 并发访问

`AUTO_SERVER=TRUE` 允许多个进程访问，但：
- ⚠️ 仅用于开发环境
- ⚠️ 生产环境应使用专业数据库

### 4. 性能考虑

H2文件模式性能略低于内存模式，但：
- ✅ 对开发环境影响不大
- ✅ 便利性远超性能损失
- ⚠️ 大量并发时考虑MySQL

---

## 🔄 切换到MySQL（可选）

如果后续需要更强的性能和稳定性：

### 1. 安装MySQL

```bash
# 下载并安装MySQL 8.0
# 创建数据库
mysql -u root -p
CREATE DATABASE lightscript CHARACTER SET utf8mb4;
```

### 2. 修改配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/lightscript?useSSL=false&serverTimezone=UTC
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
```

### 3. 数据迁移

可使用H2的导出功能导出SQL，然后导入MySQL。

---

## 📈 容量规划

### 预估存储需求

| 数据类型 | 单条大小 | 数量 | 总大小 |
|---------|---------|------|--------|
| Agent记录 | ~1 KB | 100个 | ~100 KB |
| 任务记录 | ~2 KB | 10000个 | ~20 MB |
| 任务日志 | ~500字节 | 100000条 | ~50 MB |
| **总计** | | | **~70 MB** |

### 清理策略

```sql
-- 清理30天前的已完成任务
DELETE FROM tasks 
WHERE status IN ('SUCCESS', 'FAILED', 'TIMEOUT') 
AND finished_at < DATEADD('DAY', -30, CURRENT_TIMESTAMP);

-- 清理旧日志
DELETE FROM task_logs 
WHERE task_id IN (
    SELECT task_id FROM tasks 
    WHERE finished_at < DATEADD('DAY', -30, CURRENT_TIMESTAMP)
);
```

---

## 🎓 总结

### 修改内容

1. ✅ 配置文件：从内存模式改为文件模式
2. ✅ .gitignore：添加data/目录
3. ✅ 测试脚本：验证重启后数据保留

### 效果

- ✅ **开发效率提升**：服务器重启后Agent无需重启
- ✅ **数据保留**：历史任务和Agent信息保留
- ✅ **零依赖**：无需安装MySQL等外部数据库
- ✅ **易于管理**：H2控制台可视化管理

### 适用场景

- ✅ **开发环境** - 推荐使用
- ✅ **小型测试** - 可以使用
- ⚠️ **生产环境** - 建议使用MySQL/PostgreSQL

---

**配置完成！现在可以愉快地开发了！** 🎉
