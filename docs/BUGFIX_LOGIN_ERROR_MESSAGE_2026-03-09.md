# 登录错误提示修复报告

## 修复时间
2026-03-09 10:19

## 问题描述
登录失败时没有显示错误提示信息，页面直接刷新。

## 根本原因
响应拦截器对所有401错误都执行`window.location.reload()`，包括登录失败的401。这导致：
1. 登录API返回401（用户名密码错误或用户被禁用）
2. 响应拦截器立即重新加载页面
3. 错误消息来不及显示就被刷新掉了

## 解决方案

### 区分登录失败和会话过期
修改响应拦截器，区分两种401场景：

**修改前**：
```javascript
if (error.response?.status === 401) {
  // 所有401都清除token并重新加载
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  localStorage.removeItem('userInfo')
  window.dispatchEvent(new CustomEvent('auth:unauthorized'))
  window.location.reload()
  return Promise.reject(error.response?.data || error)
}
```

**修改后**：
```javascript
if (error.response?.status === 401) {
  // 如果是登录接口返回401，不要重新加载页面，让登录表单显示错误
  const isLoginRequest = error.config?.url?.includes('/auth/login')
  
  if (!isLoginRequest) {
    // 非登录请求的401：会话过期，清除认证信息并重新加载
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    localStorage.removeItem('userInfo')
    window.dispatchEvent(new CustomEvent('auth:unauthorized'))
    window.location.reload()
  }
  
  return Promise.reject(error.response?.data || error)
}
```

## 修复效果

### 登录失败场景
1. 用户输入错误的用户名/密码
2. 后端返回401 + {"error": "Invalid credentials"}
3. 响应拦截器检测到是登录请求，不重新加载页面
4. 错误被传递到authService.login的catch块
5. 抛出Error对象：`new Error(error.error || error.message || '登录失败')`
6. App.jsx的handleLogin捕获错误
7. 显示错误消息：`message.error(error.message || '登录失败')`
8. 用户看到友好的错误提示

### 会话过期场景
1. 用户已登录，token过期
2. 访问任何需要认证的API（如/web/users）
3. 后端返回401
4. 响应拦截器检测到不是登录请求
5. 清除localStorage中的认证信息
6. 触发unauthorized事件
7. 重新加载页面
8. App.jsx检测到无token，显示登录页
9. 显示提示："会话已过期，请重新登录"

## 错误消息流程

### 后端错误格式
```json
{
  "error": "Invalid credentials"
}
```

### 前端处理链
1. **响应拦截器**：检测401，判断是否登录请求
2. **authService.login**：捕获错误，提取error.error或error.message
3. **App.jsx handleLogin**：捕获Error对象，显示error.message
4. **Ant Design message**：在页面顶部显示错误提示

## 测试场景

### 1. 用户名密码错误
- 输入：admin/wrongpassword
- 预期：显示"Invalid credentials"或"登录失败"

### 2. 用户被禁用
- 输入：admin/admin123（admin状态为DISABLED）
- 预期：显示"Invalid credentials"或"登录失败"

### 3. 会话过期
- 已登录，token过期
- 访问任何页面
- 预期：自动跳转登录页，显示"会话已过期，请重新登录"

### 4. 正常登录
- 输入：admin/admin123（admin状态为ACTIVE）
- 预期：成功登录，显示"登录成功"

## 修改文件
- `web-modern/src/services/auth.js` - 响应拦截器401处理逻辑

## 部署状态
- ✅ 本地开发环境已修复（Vite HMR自动应用）
- ⏳ 阿里云生产环境待部署

## 相关问题
- Admin用户状态问题已解决（数据库中已设置为ACTIVE）
- 如果admin再次被禁用，需要执行：
  ```sql
  UPDATE user SET status='ACTIVE' WHERE username='admin';
  ```

## 最佳实践
1. 登录失败的401不应触发页面刷新
2. 会话过期的401应该清除token并跳转登录页
3. 所有错误都应该有友好的用户提示
4. 错误消息应该从后端传递到前端显示
