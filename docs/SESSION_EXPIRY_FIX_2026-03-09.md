# 会话过期自动跳转修复报告

## 修复时间
2026-03-09

## 问题描述
前端页面在会话过期后，用户仍然可以继续操作，只是API调用会报错。正常应该在检测到会话过期时立即跳转到登录页。

## 根本原因
之前的实现中，401响应拦截器尝试使用 `window.location.href = '/login'` 跳转，但由于应用使用React Router且登录页不是一个路由而是根据认证状态渲染的组件，导致跳转失败。

## 解决方案

### 1. 更新响应拦截器 (auth.js)
```javascript
// 响应拦截器
api.interceptors.response.use(
  (response) => {
    return response.data
  },
  (error) => {
    console.error('API Error:', error)
    if (error.response?.status === 401) {
      // 清除认证信息
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      localStorage.removeItem('userInfo')
      
      // 触发自定义事件通知App组件
      window.dispatchEvent(new CustomEvent('auth:unauthorized'))
      
      // 重新加载页面，让App.jsx重新检查认证状态
      window.location.reload()
      
      return Promise.reject(error.response?.data || error)
    }
    return Promise.reject(error.response?.data || error)
  }
)
```

### 2. 添加未授权事件监听器 (App.jsx)
```javascript
useEffect(() => {
  checkAuth()
  
  // 监听storage变化（用于多标签页同步）
  const handleStorageChange = (e) => {
    if (e.key === 'token' && !e.newValue) {
      setIsAuthenticated(false)
      setUserInfo(null)
    }
  }
  
  // 监听401未授权事件
  const handleUnauthorized = () => {
    setIsAuthenticated(false)
    setUserInfo(null)
    message.warning('会话已过期，请重新登录')
  }
  
  window.addEventListener('storage', handleStorageChange)
  window.addEventListener('auth:unauthorized', handleUnauthorized)
  
  return () => {
    window.removeEventListener('storage', handleStorageChange)
    window.removeEventListener('auth:unauthorized', handleUnauthorized)
  }
}, [])
```

## 修复效果

### 会话过期处理流程
1. API请求返回401状态码
2. 响应拦截器捕获401错误
3. 清除localStorage中的认证信息（token、user、userInfo）
4. 触发自定义事件 `auth:unauthorized`
5. 重新加载页面
6. App.jsx检测到无token，自动显示登录页
7. 显示提示消息："会话已过期，请重新登录"

### 多标签页同步
- 当一个标签页的token被清除时，其他标签页会通过storage事件监听器同步登出状态
- 确保所有标签页的认证状态保持一致

## 测试方法

### 方法1：手动清除Token
1. 打开浏览器开发者工具 (F12)
2. 进入 Application/Storage -> Local Storage
3. 删除 `token` 项
4. 在页面中执行任何API操作（如刷新列表）
5. 验证：应该立即跳转到登录页并显示提示消息

### 方法2：模拟Token过期
1. 打开浏览器开发者工具 (F12)
2. 进入 Application/Storage -> Local Storage
3. 将 `token` 修改为无效值（如 "invalid-token"）
4. 在页面中执行任何API操作
5. 验证：应该立即跳转到登录页并显示提示消息

### 方法3：等待真实过期
1. 登录系统
2. 等待24小时（JWT token过期时间）
3. 执行任何操作
4. 验证：应该自动跳转到登录页

## 修改文件
- `web-modern/src/services/auth.js` - 更新401响应处理逻辑
- `web-modern/src/App.jsx` - 添加未授权事件监听器

## 部署状态
- ✅ 本地开发环境已更新（Vite HMR自动应用）
- ⏳ 阿里云生产环境待部署

## 注意事项
1. 使用 `window.location.reload()` 会导致页面完全刷新，用户当前的操作状态会丢失
2. 这是预期行为，因为会话已过期，需要重新登录
3. 多标签页会同步登出状态，确保安全性
