import React, { useState, useEffect } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { Layout, message } from 'antd'
import Sidebar from './components/Layout/Sidebar'
import Header from './components/Layout/Header'
import Dashboard from './pages/Dashboard'
import Agents from './pages/Agents'
import Tasks from './pages/Tasks'
import Scripts from './pages/Scripts'
import Login from './pages/Login'
import { authService } from './services/auth'

const { Content } = Layout

function App() {
  const [collapsed, setCollapsed] = useState(false)
  const [isAuthenticated, setIsAuthenticated] = useState(false)
  const [loading, setLoading] = useState(true)
  const [userInfo, setUserInfo] = useState(null)

  useEffect(() => {
    checkAuth()
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
      localStorage.setItem('userInfo', JSON.stringify(response.user))
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
            <Route path="/tasks" element={<Tasks />} />
            <Route path="/scripts" element={<Scripts />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  )
}

export default App