# 修复客户端分组页面白屏问题

## Bug日期
2026-03-09

## 问题描述
打开客户端分组页面后，退出该页面时整个应用会出现白屏。

## 问题原因
React组件在卸载后仍然尝试更新状态（setState），导致内存泄漏和错误。

具体场景：
1. 用户打开客户端分组页面
2. 页面发起异步API请求（fetchGroups, fetchAgents）
3. 用户在请求完成前离开页面（组件卸载）
4. 异步请求完成后尝试调用setState
5. React检测到在已卸载的组件上调用setState，抛出错误
6. 导致整个应用崩溃，出现白屏

## 解决方案

### 使用useRef跟踪组件挂载状态

添加`isMountedRef`来跟踪组件是否已挂载：

```javascript
import React, { useState, useEffect, useRef } from 'react'

const AgentGroups = () => {
  // ... 其他状态
  
  // 使用useRef跟踪组件挂载状态
  const isMountedRef = useRef(true)

  useEffect(() => {
    fetchGroups()
    fetchAgents()
    
    // 清理函数：组件卸载时设置标记
    return () => {
      isMountedRef.current = false
    }
  }, [])
  
  // ...
}
```

### 在异步函数中检查挂载状态

在所有异步操作中，在调用setState前检查组件是否仍然挂载：

```javascript
const fetchGroups = async () => {
  if (!isMountedRef.current) return  // 组件已卸载，直接返回
  
  setLoading(true)
  try {
    const response = await api.get('/web/agent-groups')
    if (isMountedRef.current) {  // 检查组件是否仍然挂载
      setGroups(response.content || [])
    }
  } catch (error) {
    if (isMountedRef.current) {
      message.error('获取分组列表失败')
    }
  } finally {
    if (isMountedRef.current) {
      setLoading(false)
    }
  }
}

const fetchAgents = async () => {
  if (!isMountedRef.current) return
  
  try {
    const response = await api.get('/web/agents')
    if (isMountedRef.current) {
      setAgents(response.content || [])
    }
  } catch (error) {
    console.error('获取Agent列表失败', error)
  }
}
```

## 技术细节

### 为什么使用useRef而不是useState？

1. **性能**：useRef不会触发重新渲染
2. **安全**：在组件卸载后修改useRef是安全的
3. **即时性**：useRef的值立即更新，不需要等待下一次渲染

### 为什么不能在卸载后调用setState？

React的设计原则：
- 组件卸载后，其状态不再存在
- 在已卸载的组件上调用setState会导致内存泄漏
- React会在开发模式下抛出警告或错误

### 常见的内存泄漏场景

1. **异步请求**：组件卸载后API请求完成
2. **定时器**：setTimeout/setInterval未清理
3. **事件监听**：addEventListener未移除
4. **WebSocket**：连接未关闭

## 修改文件

- `web-modern/src/pages/AgentGroups.jsx` - 添加挂载状态跟踪和清理逻辑

## 测试步骤

1. 打开客户端分组页面
2. 立即点击其他菜单项（如任务管理）
3. 验证页面是否正常切换，没有白屏
4. 检查浏览器控制台是否有错误信息

## 预防措施

### 最佳实践

1. **所有异步操作都应该检查组件挂载状态**
2. **useEffect必须返回清理函数**
3. **使用useRef跟踪挂载状态**
4. **避免在组件卸载后调用setState**

### 代码模板

```javascript
const MyComponent = () => {
  const isMountedRef = useRef(true)
  
  useEffect(() => {
    // 初始化逻辑
    
    return () => {
      isMountedRef.current = false
    }
  }, [])
  
  const fetchData = async () => {
    if (!isMountedRef.current) return
    
    try {
      const data = await api.get('/endpoint')
      if (isMountedRef.current) {
        setData(data)
      }
    } catch (error) {
      if (isMountedRef.current) {
        handleError(error)
      }
    }
  }
}
```

## 相关问题

类似的问题可能存在于其他页面：
- Users.jsx
- Agents.jsx
- Tasks.jsx
- Scripts.jsx
- SystemSettings.jsx

建议对所有页面进行类似的检查和修复。

## 参考资料

- [React官方文档 - Cleanup Function](https://react.dev/learn/synchronizing-with-effects#step-3-add-cleanup-if-needed)
- [React官方文档 - Memory Leaks](https://react.dev/learn/you-might-not-need-an-effect#fetching-data)
- [useRef Hook](https://react.dev/reference/react/useRef)
