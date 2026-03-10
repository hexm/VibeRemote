# 会话和认证问题修复总结

## 修复时间
2026-03-09

## 修复的问题

### 1. 会话过期自动跳转 ✅
**问题**: 会话过期后用户仍可操作，只是报错，没有自动跳转到登录页

**解决方案**:
- 修改`auth.js`响应拦截器，401时清除token并重新加载页面
- 添加自定义事件通知App组件
- App.jsx添加未授权事件监听器

**文件**: 
- `web-modern/src/services/auth.js`
- `web-modern/src/App.jsx`

### 2. 系统参数功能404错误 ✅
**问题**: 访问系统参数页面返回404错误

**根本原因**:
- SystemSettingController路径错误：`/web/system-settings` 应为 `/api/web/system-settings`
- 数据库表为空（Hibernate update模式不执行INSERT语句）

**解决方案**:
- 修复Controller的@RequestMapping路径
- 手动插入初始系统参数数据

**文件**:
- `server/src/main/java/com/example/lightscript/server/controller/SystemSettingController.java`
- 数据库：插入8个默认系统参数

### 3. 用户管理页面数据为空 ✅
**问题**: 用户管理页面无法加载数据

**根本原因**: Users.jsx直接使用`axios`而不是`api`实例，导致请求没有携带认证token

**解决方案**:
- 将`import axios from 'axios'`改为`import api from '../services/auth'`
- 替换所有`axios.get/post/put/delete`为`api.get/post/put/delete`
- 移除URL中的`/api`前缀
- 修改响应数据访问：`response.data.content` → `response.content`

**文件**:
- `web-modern/src/pages/Users.jsx`

### 4. 客户端分组页面数据为空 ✅
**问题**: 客户端分组页面无法加载数据（与用户管理相同的问题）

**解决方案**: 与Users.jsx相同的修复方法

**文件**:
- `web-modern/src/pages/AgentGroups.jsx`

## 技术细节

### API实例配置 (auth.js)
```javascript
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

const api = axios.create({
  baseURL: API_BASE_URL,  // 开发: http://localhost:8080/api
  timeout: 10000,
})

// 请求拦截器：自动添加Authorization header
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：自动处理401和提取data
api.interceptors.response.use(
  (response) => response.data,  // 自动提取data
  (error) => {
    if (error.response?.status === 401) {
      // 清除token并重新加载
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      localStorage.removeItem('userInfo')
      window.dispatchEvent(new CustomEvent('auth:unauthorized'))
      window.location.reload()
    }
    return Promise.reject(error.response?.data || error)
  }
)
```

### 正确的API调用方式
```javascript
// ✅ 正确
import api from '../services/auth'
const response = await api.get('/web/users')
const users = response.content

// ❌ 错误
import axios from 'axios'
const response = await axios.get('/api/web/users')
const users = response.data.content
```

## 修改文件清单
1. `web-modern/src/services/auth.js` - 会话过期处理
2. `web-modern/src/App.jsx` - 未授权事件监听
3. `server/src/main/java/com/example/lightscript/server/controller/SystemSettingController.java` - API路径修复
4. `web-modern/src/pages/Users.jsx` - 认证修复
5. `web-modern/src/pages/AgentGroups.jsx` - 认证修复

## 部署状态
- ✅ 本地开发环境已全部修复并验证
- ⏳ 阿里云生产环境待部署

## 测试验证

### 会话过期测试
1. 登录系统
2. 打开开发者工具，删除localStorage中的token
3. 执行任何操作
4. 验证：页面自动刷新并显示登录页

### 系统参数测试
1. 访问"系统参数"菜单
2. 验证：显示4个类别的8个系统参数
3. 测试编辑、搜索功能

### 用户管理测试
1. 访问"用户管理"菜单
2. 验证：显示admin用户
3. 测试创建、编辑、删除用户功能

### 客户端分组测试
1. 访问"客户端分组"菜单
2. 验证：能正常加载分组列表
3. 测试创建、编辑、删除分组功能

## 最佳实践建议

### 1. API调用规范
- 所有需要认证的API调用必须使用`api`实例
- 不要直接使用`axios`
- URL不需要包含`/api`前缀（baseURL已配置）

### 2. 响应数据访问
- 使用`api`实例时：`response.content`（响应拦截器已提取data）
- 直接使用`axios`时：`response.data.content`

### 3. 错误处理
- `api`实例会自动处理401错误
- 其他错误需要在catch块中处理
- 使用`message.error()`显示用户友好的错误信息

### 4. 数据库迁移
- 使用Hibernate update模式时，需要手动执行数据初始化
- 建议在生产环境使用Flyway管理数据库迁移
- 确保所有迁移脚本都能正确执行

## 待检查页面
以下页面可能也存在类似问题，建议检查：
- [ ] Tasks.jsx
- [ ] Scripts.jsx
- [ ] Agents.jsx
- [ ] Dashboard.jsx

## 相关文档
- `docs/SESSION_EXPIRY_FIX_2026-03-09.md` - 会话过期修复详情
- `docs/BUGFIX_SYSTEM_SETTINGS_2026-03-09.md` - 系统参数修复详情
- `docs/BUGFIX_USER_MANAGEMENT_AUTH_2026-03-09.md` - 用户管理认证修复详情
