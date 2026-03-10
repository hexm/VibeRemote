# 系统参数功能修复报告

## 修复时间
2026-03-09 10:00

## 问题描述
用户访问系统参数页面时报错：
```
{"timestamp": "2026-03-09T01:57:38.992+00:00","status": 404,"error": "Not Found","path": "/api/web/system-settings/by-category"}
```

## 问题分析

### 根本原因
1. **API路径错误**：SystemSettingController的@RequestMapping路径为`/web/system-settings`，缺少`/api`前缀
2. **数据库无初始数据**：使用Hibernate的`ddl-auto=update`模式只创建表结构，不执行SQL脚本中的INSERT语句

### 问题详情
- 其他Controller都使用`/api/web/*`路径模式
- SystemSettingController使用了`/web/system-settings`，导致404错误
- system_setting表已创建但为空，即使API正常也无数据返回

## 解决方案

### 1. 修复API路径
**文件**: `server/src/main/java/com/example/lightscript/server/controller/SystemSettingController.java`

**修改前**:
```java
@RequestMapping("/web/system-settings")
```

**修改后**:
```java
@RequestMapping("/api/web/system-settings")
```

### 2. 插入初始数据
由于使用Hibernate update模式，需要手动插入V9迁移脚本中的初始数据：

```sql
INSERT INTO system_setting (setting_key, setting_value, setting_type, description, category, is_encrypted) VALUES
('system.name', 'LightScript', 'STRING', '系统名称', '系统配置', FALSE),
('system.timezone', 'Asia/Shanghai', 'STRING', '系统时区', '系统配置', FALSE),
('task.default_timeout', '300', 'NUMBER', '任务默认超时时间（秒）', '任务配置', FALSE),
('task.max_concurrent', '10', 'NUMBER', '最大并发任务数', '任务配置', FALSE),
('agent.heartbeat_interval', '30', 'NUMBER', 'Agent心跳间隔（秒）', 'Agent配置', FALSE),
('agent.offline_threshold', '90', 'NUMBER', 'Agent离线阈值（秒）', 'Agent配置', FALSE),
('security.session_timeout', '3600', 'NUMBER', '会话超时时间（秒）', '安全配置', FALSE),
('security.password_min_length', '6', 'NUMBER', '密码最小长度', '安全配置', FALSE);
```

## 验证结果

### API测试
```bash
# 登录获取token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 测试系统参数API
curl http://localhost:8080/api/web/system-settings/by-category \
  -H "Authorization: Bearer <token>"
```

### 返回结果
```json
{
  "任务配置": [
    {
      "id": 3,
      "settingKey": "task.default_timeout",
      "settingValue": "300",
      "settingType": "NUMBER",
      "description": "任务默认超时时间（秒）",
      "category": "任务配置"
    },
    ...
  ],
  "Agent配置": [...],
  "系统配置": [...],
  "安全配置": [...]
}
```

## 修改文件
- `server/src/main/java/com/example/lightscript/server/controller/SystemSettingController.java` - 修复API路径
- 数据库 `lightscript.system_setting` - 插入初始数据

## 部署状态
- ✅ 本地开发环境已修复并验证
- ⏳ 阿里云生产环境待部署

## 测试步骤
1. 登录系统（admin/admin123）
2. 访问"系统参数"菜单
3. 验证能看到4个类别的系统参数：
   - 系统配置（2个参数）
   - 任务配置（2个参数）
   - Agent配置（2个参数）
   - 安全配置（2个参数）
4. 测试编辑、搜索功能

## 注意事项
1. 使用Hibernate update模式时，需要手动执行数据初始化
2. 建议在生产环境使用Flyway管理数据库迁移
3. 所有Web API应统一使用`/api/web/*`路径前缀
