# 功能变更需求 - 2026-03-06

## 变更清单

### 1. ✅ 前端favicon图标
**状态**: 已完成
**文件**: 
- `web-modern/index.html` - 更新favicon引用
- `web-modern/public/favicon.svg` - 新增SVG图标

**变更内容**:
- 创建了带有"L"字母的渐变色SVG图标
- 更新HTML中的favicon链接

---

### 2. ✅ 移除"添加客户端"按钮
**状态**: 已完成
**文件**: `web-modern/src/pages/Agents.jsx`

**变更内容**:
- 移除了无用的"添加客户端"按钮
- Agent通过自动注册机制添加，不需要手动添加

---

### 3. ✅ 客户端状态收集功能
**状态**: 已完成
**文件**: `web-modern/src/pages/Agents.jsx`

**变更内容**:
- 添加`handleCollectStatus`函数
- 在操作列添加"状态收集"按钮（绿色同步图标）
- 根据操作系统自动选择合适的状态收集脚本
  - Windows: 使用cmd脚本（systeminfo, wmic等）
  - Linux: 使用shell脚本（uname, top, free, df等）
- 点击后自动创建任务并执行

**功能说明**:
状态收集脚本会收集以下信息：
- 系统信息（OS版本、架构）
- CPU使用率
- 内存使用情况
- 磁盘使用情况
- 网络接口信息
- 运行中的进程（Top 10）

---

### 4. ⏳ 创建任务支持选择已有脚本
**状态**: 待实现
**文件**: `web-modern/src/pages/Tasks.jsx`

**需求说明**:
- 在创建任务对话框中添加"脚本来源"选择
  - 选项1: 选择已有脚本
  - 选项2: 自定义输入
- 选择已有脚本时，从Scripts页面的脚本列表中选择
- 选择后自动填充脚本类型和内容
- 仍然允许用户修改脚本内容

**实现方案**:
1. 添加Radio选择脚本来源
2. 添加Select下拉框选择已有脚本
3. 监听脚本选择，自动填充表单
4. 保持现有的自定义输入功能

---

### 5. ⏳ 菜单名称统一
**状态**: 待实现
**文件**: 
- `web-modern/src/components/Layout/Sidebar.jsx`
- `web-modern/src/App.jsx`

**变更内容**:
- "Agent分组" → "客户端分组"
- 保持与"客户端管理"名称一致

---

### 6. ✅ 系统参数维护功能
**状态**: 已完成
**文件**: 
- 后端：
  - `server/src/main/resources/db/migration/V9__system_settings.sql` - 数据库迁移脚本
  - `server/src/main/java/com/example/lightscript/server/entity/SystemSetting.java` - 实体类
  - `server/src/main/java/com/example/lightscript/server/repository/SystemSettingRepository.java` - Repository
  - `server/src/main/java/com/example/lightscript/server/service/SystemSettingService.java` - Service
  - `server/src/main/java/com/example/lightscript/server/controller/SystemSettingController.java` - Controller
- 前端：
  - `web-modern/src/pages/SystemSettings.jsx` - 系统参数页面
  - `web-modern/src/components/Layout/Sidebar.jsx` - 添加菜单项
  - `web-modern/src/App.jsx` - 添加路由

**功能说明**:
新增"系统参数"菜单，用于配置系统级参数

**已实现功能**:
1. 按类别分组展示参数（折叠面板）
2. 参数搜索功能（搜索键和描述）
3. 参数编辑（支持STRING、NUMBER、BOOLEAN、JSON类型）
4. 新增参数
5. 删除参数
6. 敏感参数加密显示（带密码遮罩）
7. 预定义8个系统参数：
   - 系统配置：系统名称、时区
   - 任务配置：默认超时时间、最大并发数
   - Agent配置：心跳间隔、离线阈值
   - 安全配置：会话超时、密码最小长度

**API接口**:
- `GET /web/system-settings` - 获取所有参数
- `GET /web/system-settings/by-category` - 按类别分组获取
- `GET /web/system-settings/key/{key}` - 根据键获取
- `GET /web/system-settings/search?keyword=xxx` - 搜索参数
- `PUT /web/system-settings/{id}` - 更新参数值
- `POST /web/system-settings` - 创建新参数
- `DELETE /web/system-settings/{id}` - 删除参数

---

## 实施优先级

1. ✅ **P0 - 已完成**
   - Favicon图标
   - 移除添加客户端按钮
   - 状态收集功能
   - 菜单名称统一
   - 创建任务支持选择脚本
   - 系统参数维护

---

## 总结

所有6个功能变更需求已全部完成：
1. ✅ 前端favicon图标
2. ✅ 移除"添加客户端"按钮
3. ✅ 客户端状态收集功能
4. ✅ 创建任务支持选择已有脚本
5. ✅ 菜单名称统一（"客户端分组"）
6. ✅ 系统参数维护功能

**下一步**: 等待用户在阿里云环境测试完成后，可以部署最新功能。

**文档创建时间**: 2026-03-06  
**最后更新**: 2026-03-06
