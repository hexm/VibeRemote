import React from 'react'
import { Layout, Menu } from 'antd'
import { useNavigate, useLocation } from 'react-router-dom'
import {
  DashboardOutlined,
  DesktopOutlined,
  FileTextOutlined,
  CodeOutlined,
  RocketOutlined
} from '@ant-design/icons'

const { Sider } = Layout

const menuItems = [
  {
    key: '/dashboard',
    icon: <DashboardOutlined />,
    label: '仪表盘',
  },
  {
    key: '/agents',
    icon: <DesktopOutlined />,
    label: '客户端管理',
  },
  {
    key: '/tasks',
    icon: <FileTextOutlined />,
    label: '任务管理',
  },
  {
    key: '/scripts',
    icon: <CodeOutlined />,
    label: '脚本管理',
  },
]

const Sidebar = ({ collapsed }) => {
  const navigate = useNavigate()
  const location = useLocation()

  const handleMenuClick = ({ key }) => {
    navigate(key)
  }

  return (
    <Sider
      trigger={null}
      collapsible
      collapsed={collapsed}
      className="fixed left-0 top-0 h-screen shadow-2xl"
      theme="dark"
      width={256}
      collapsedWidth={80}
      style={{ zIndex: 1000 }}
    >
      <div className="flex items-center justify-center h-16 bg-gray-900 border-b border-gray-700">
        <div className="flex items-center space-x-2">
          <RocketOutlined className="text-2xl text-blue-400" />
          {!collapsed && (
            <span className="text-xl font-bold text-white">LightScript</span>
          )}
        </div>
      </div>
      
      <Menu
        theme="dark"
        mode="inline"
        selectedKeys={[location.pathname]}
        items={menuItems}
        onClick={handleMenuClick}
        className="border-r-0 bg-gray-800"
        style={{
          height: 'calc(100vh - 64px)',
          borderRight: 0,
        }}
      />
    </Sider>
  )
}

export default Sidebar