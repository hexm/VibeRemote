# 下一个对话的上下文信息

## 任务目标
优化 web-modern 的任务管理功能，使其与旧版 web 的功能保持一致。

## 当前状态

### 系统运行状态
- ✅ 后端服务：运行中（阿里云 8.138.114.34:8080）
- ✅ 前端服务：运行中（Nginx，端口 80 和 3000）
- ✅ 本地Agent：在线，正常上报资源使用
- ✅ 数据库：H2，正常运行

### 访问信息
- 前端：http://8.138.114.34:3000
- 后端：http://8.138.114.34:8080
- 账号：admin / admin123

## 需要优化的功能

### 参考文件
- **旧版实现**：`web/tasks.html` 和 `web/js/tasks.js`
- **新版实现**：`web-modern/src/pages/Tasks.jsx`
- **优化文档**：`docs/TASK_MANAGEMENT_IMPROVEMENTS.md`

### 主要缺失功能

1. **任务名称字段**
   - 创建任务时需要输入任务名称（taskName）
   - 任务列表显示任务名称

2. **执行次数显示**
   - 显示任务执行了多少次（executionCount）
   - 显示"第X次"标签

3. **任务历史记录**
   - 查看任务的历史执行记录
   - API：`GET /api/web/tasks/{taskId}/executions`

4. **日志功能增强**
   - 自动刷新开关
   - 下载日志按钮
   - 显示日志行数
   - API：`GET /api/web/tasks/{taskId}/logs/download`

5. **任务重启功能**
   - 失败或超时的任务可以重启
   - API：`POST /api/web/tasks/{taskId}/restart`

6. **批量任务详情优化**
   - 显示详细的统计信息
   - 进度条和状态卡片
   - 子任务列表

### 表格列调整

**当前列：**
```
任务信息 | 脚本 | 执行节点 | 状态 | 执行时间 | 退出码 | 操作
```

**目标列：**
```
任务信息(含名称) | Agent | 执行次数 | 脚本类型 | 状态 | 创建者 | 创建时间 | 完成时间 | 操作
```

### 操作按钮逻辑

根据任务状态显示不同按钮：
- **详情** - 所有任务
- **日志** - executionCount > 0
- **重启** - status === 'FAILED' || status === 'TIMEOUT'
- **历史** - executionCount > 1
- **取消** - status === 'PENDING' || status === 'RUNNING'

## 后端API状态

### 已有接口
- ✅ `GET /api/web/tasks` - 获取任务列表
- ✅ `POST /api/web/tasks/create` - 创建任务
- ✅ `GET /api/web/tasks/{taskId}` - 获取任务详情
- ✅ `GET /api/web/tasks/{taskId}/logs` - 获取任务日志
- ✅ `POST /api/web/tasks/{taskId}/cancel` - 取消任务
- ✅ `GET /api/web/batch-tasks` - 获取批量任务列表
- ✅ `POST /api/web/batch-tasks/create` - 创建批量任务
- ✅ `GET /api/web/batch-tasks/{batchId}` - 获取批量任务详情
- ✅ `GET /api/web/batch-tasks/{batchId}/tasks` - 获取批量任务的子任务

### 需要确认的接口
- ❓ `GET /api/web/tasks/{taskId}/executions` - 获取任务执行历史
- ❓ `GET /api/web/tasks/executions/{executionId}/logs` - 获取历史日志
- ❓ `GET /api/web/tasks/{taskId}/logs/download` - 下载日志
- ❓ `POST /api/web/tasks/{taskId}/restart` - 重启任务

## 技术栈

### 前端
- React 18
- Ant Design 5
- Tailwind CSS
- Vite
- Axios

### 后端
- Spring Boot 2.7.18
- Spring Data JPA
- H2 Database
- JWT认证

## 开发流程

### 1. 修改前端
```bash
# 编辑文件
vim web-modern/src/pages/Tasks.jsx

# 构建
cd web-modern && npm run build

# 部署
scp -r dist/* root@8.138.114.34:/opt/lightscript/frontend/
```

### 2. 修改后端（如果需要）
```bash
# 编辑文件
vim server/src/main/java/com/example/lightscript/server/web/WebController.java

# 构建
mvn clean package -DskipTests

# 部署
scp server/target/server-*.jar root@8.138.114.34:/opt/lightscript/backend/server.jar
ssh root@8.138.114.34 '/opt/lightscript/scripts/restart-all.sh'
```

### 3. 测试
1. 访问 http://8.138.114.34:3000
2. 登录：admin / admin123
3. 进入"任务管理"
4. 测试各项功能

## 注意事项

1. **保持现代化设计** - 使用Ant Design 5组件
2. **API路径** - 使用 `/api/web/...` 前缀
3. **错误处理** - 添加适当的加载状态和错误提示
4. **代码简洁** - 避免过度复杂
5. **浏览器缓存** - 部署后需要强制刷新（Cmd+Shift+R）

## 相关文档

- [任务管理优化说明](./TASK_MANAGEMENT_IMPROVEMENTS.md) - 详细的功能说明
- [快速参考指南](./QUICK_REFERENCE.md) - 常用命令和操作
- [部署指南](./DEPLOYMENT_ALIYUN.md) - 部署相关信息

## Git状态

最新提交：
```
commit 9d13589
docs: 添加快速参考指南
```

所有代码已提交，工作目录干净。

## 下一步

1. 查看旧版 `web/tasks.html` 和 `web/js/tasks.js` 了解完整功能
2. 检查后端是否已有所需API接口
3. 修改 `web-modern/src/pages/Tasks.jsx` 添加缺失功能
4. 测试并验证所有功能
5. 部署到生产环境

---

**准备就绪！可以开始优化任务管理功能了。**
