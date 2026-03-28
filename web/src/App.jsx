import React, { useState, useEffect } from 'react'
import { Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { Layout, App as AntApp } from 'antd'
import Sidebar from './components/Layout/Sidebar'
import Header from './components/Layout/Header'
import Dashboard from './pages/Dashboard'
import Agents from './pages/Agents'
import AgentGroups from './pages/AgentGroups'
import Tasks from './pages/Tasks'
import Scripts from './pages/Scripts'
import Files from './pages/Files'
import Users from './pages/Users'
import SystemSettings from './pages/SystemSettings'
import AgentVersions from './pages/AgentVersions'
import Login from './pages/Login'
import { authService } from './services/auth'
import { getSessionKey } from './utils/crypto'
import { cleanupOverlayArtifacts } from './utils/overlayCleanup'

const { Content } = Layout

function App() {
  const location = useLocation()
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

    const handleForbidden = (event) => {
      const errorMessage = event.detail?.message || '您没有权限执行该操作'
      message.error(errorMessage)
    }
    
    window.addEventListener('storage', handleStorageChange)
    window.addEventListener('auth:unauthorized', handleUnauthorized)
    window.addEventListener('auth:forbidden', handleForbidden)
    
    return () => {
      window.removeEventListener('storage', handleStorageChange)
      window.removeEventListener('auth:unauthorized', handleUnauthorized)
      window.removeEventListener('auth:forbidden', handleForbidden)
    }
  }, [message])

  useEffect(() => {
    const timer = window.setTimeout(() => {
      cleanupOverlayArtifacts()
    }, 0)

    return () => window.clearTimeout(timer)
  }, [location.pathname, isAuthenticated])

  const checkAuth = async () => {
    try {
      const token = localStorage.getItem('token')
      const userInfo = localStorage.getItem('userInfo')
      
      if (token && userInfo) {
        // 如果 sessionStorage 里没有加密密钥（页面刷新后丢失），自动重新获取
        // 必须在 setIsAuthenticated(true) 之前完成，否则组件渲染时密钥还没就绪
        if (!getSessionKey()) {
          await authService.refreshEncryptionKey()
        }

        // 密钥就绪后再渲染页面组件
        setUserInfo(JSON.parse(userInfo))
        setIsAuthenticated(true)
      }
    } catch (error) {
      console.error('Auth check failed:', error)
      localStorage.removeItem('token')
      localStorage.removeItem('user')
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
      
      window.setTimeout(() => {
        message.success('登录成功')
      }, 100)

      window.setTimeout(() => {
        cleanupOverlayArtifacts({ force: true })
      }, 150)
      
      return true
    } catch (error) {
      console.error('Login error:', error)
      message.error(error.message || '登录失败')
      cleanupOverlayArtifacts({ force: true })
      
      return false
    }
  }

  const handleLogout = () => {
    authService.logout()
    localStorage.removeItem('user') // Also remove 'user' key
    setIsAuthenticated(false)
    setUserInfo(null)
    cleanupOverlayArtifacts({ force: true })
    
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
      <Layout style={{ marginLeft: collapsed ? 64 : 200, transition: 'margin-left 0.3s' }}>
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
            <Route path="/files" element={<Files />} />
            <Route path="/users" element={<Users />} />
            <Route path="/system-settings" element={<SystemSettings />} />
            <Route path="/agent-versions" element={<AgentVersions />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  )
}

export default App
