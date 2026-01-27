import React from 'react'
import { Layout, Button, Dropdown, Avatar, Space, Typography } from 'antd'
import {
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
  LogoutOutlined,
  SettingOutlined,
} from '@ant-design/icons'

const { Header: AntHeader } = Layout
const { Text } = Typography

const Header = ({ collapsed, onToggle, userInfo, onLogout }) => {
  const userMenuItems = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: '个人资料',
    },
    {
      key: 'settings',
      icon: <SettingOutlined />,
      label: '设置',
    },
    {
      type: 'divider',
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: onLogout,
    },
  ]

  return (
    <AntHeader className="bg-white shadow-sm border-b border-gray-200 px-6 flex items-center justify-between sticky top-0" style={{ zIndex: 999 }}>
      <div className="flex items-center space-x-4">
        <Button
          type="text"
          icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
          onClick={onToggle}
          className="text-gray-600 hover:text-gray-900 hover:bg-gray-100"
          size="large"
        />
        
        <div className="hidden md:block">
          <Text className="text-gray-600">
            欢迎使用 LightScript 分布式脚本管理平台
          </Text>
        </div>
      </div>

      <div className="flex items-center space-x-4">
        <Dropdown
          menu={{ items: userMenuItems }}
          placement="bottomRight"
          arrow
        >
          <Space className="cursor-pointer hover:bg-gray-50 px-3 py-2 rounded-lg transition-colors">
            <Avatar 
              size="small" 
              icon={<UserOutlined />} 
              className="bg-blue-500"
            />
            <Text className="hidden sm:inline text-gray-700 font-medium">
              {userInfo?.username || 'admin'}
            </Text>
          </Space>
        </Dropdown>
      </div>
    </AntHeader>
  )
}

export default Header