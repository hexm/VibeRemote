import React, { useState, useEffect } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { Layout, App as AntApp } from 'antd'
import Sidebar from './components/Layout/Sidebar'
import Header from './components/Layout/Header'
import Dashboard from './pages/Dashboard'
import Agents from './pages/Agents'
import AgentGroups from './pages/AgentGroups'
import Tasks from './pages/Tasks'
import Scripts from './pages/Scripts'
import Users from './pages/Users'
import SystemSettings from './pages/SystemSettings'
import Login from './pages/Login'
import { authService } from './services/auth'

const { Content } = Layout

function App() {
  const [collapsed, setCollapsed] = useState(false)
  const [isAuthenticated, setIsAuthenticated] = useState(false)
  const [loading, setLoading] = useState(true)
  const [userInfo, setUserInfo] = useState(null)
  
  // 使用Ant Design的message hook
  const { message } = AntApp.useApp()

  useEffect(() => {
    checkAuth()
    
    // 监听storage变化（用于多标签页同步）
    const handleStorageChange = (e) => {
      if (e.key === 'token' && !e.newValue) {
        // token被删除，说明在其他标签页登出或会话过期
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

  const checkAuth = async () => {
    try {
      const token = localStorage.getItem('token')
      const userInfo = localStorage.getItem('userInfo')
      
      if (token && userInfo) {
        // 直接使用本地存储的用户信息，不调用API
        setUserInfo(JSON.parse(userInfo))
        setIsAuthenticated(true)
      }
    } catch (error) {
      console.error('Auth check failed:', error)
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      setIsAuthenticated(false)
    } finally {
      setLoading(false)
    }
  }

  const handleLogin = async (credentials) => {
    try {
      const response = await authService.login(credentials)
      localStorage.setItem('token', response.token)
      localStorage.setItem('user', JSON.stringify(response.user)) // Changed from 'userInfo' to 'user'
      localStorage.setItem('userInfo', JSON.stringify(response.user)) // Keep for backward compatibility
      setUserInfo(response.user)
      setIsAuthenticated(true)
      
      // 延迟显示成功消息，避免立即触发可能的遮罩问题
      setTimeout(() => {
        message.success('登录成功')
      }, 100)
      
      // 确保没有任何遮罩残留
      document.body.style.overflow = 'auto'
      document.body.style.pointerEvents = 'auto'
      document.body.classList.remove('ant-scrolling-effect')
      
      // 强制移除所有可能的遮罩
      setTimeout(() => {
        const masks = document.querySelectorAll('.ant-modal-mask, .ant-drawer-mask, .ant-message')
        masks.forEach(mask => {
          if (!mask.closest('.ant-modal-wrap, .ant-drawer-wrap')) {
            mask.remove()
          }
        })
      }, 500)
      
      return true
    } catch (error) {
      console.error('Login error:', error)
      message.error(error.message || '登录失败')
      
      // 清理可能的遮罩状态
      document.body.style.overflow = 'auto'
      document.body.style.pointerEvents = 'auto'
      document.body.classList.remove('ant-scrolling-effect')
      
      return false
    }
  }

  const handleLogout = () => {
    authService.logout()
    localStorage.removeItem('user') // Also remove 'user' key
    setIsAuthenticated(false)
    setUserInfo(null)
    
    // 清理可能的遮罩状态
    document.body.style.overflow = 'auto'
    document.body.style.pointerEvents = 'auto'
    document.body.classList.remove('ant-scrolling-effect')
    
    message.success('已退出登录')
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-gray-50">
        <div className="text-center">
          <div className="animate-spin rounded-full h-32 w-32 border-b-2 border-blue-500 mx-auto"></div>
          <p className="mt-4 text-gray-600">正在加载...</p>
        </div>
      </div>
    )
  }

  if (!isAuthenticated) {
    return <Login onLogin={handleLogin} />
  }

  // 恢复正常的主界面，但加入防护措施
  return (
    <Layout className="min-h-screen">
      <Sidebar collapsed={collapsed} />
      <Layout style={{ marginLeft: collapsed ? 80 : 256, transition: 'margin-left 0.3s' }}>
        <Header 
          collapsed={collapsed}
          onToggle={() => setCollapsed(!collapsed)}
          userInfo={userInfo}
          onLogout={handleLogout}
        />
        <Content className="p-6 bg-gray-50" style={{ minHeight: 'calc(100vh - 64px)', pointerEvents: 'auto' }}>
          <Routes>
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/agents" element={<Agents />} />
            <Route path="/agent-groups" element={<AgentGroups />} />
            <Route path="/tasks" element={<Tasks />} />
            <Route path="/scripts" element={<Scripts />} />
            <Route path="/users" element={<Users />} />
            <Route path="/system-settings" element={<SystemSettings />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  )
}

export default App