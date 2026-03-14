import React, { useState } from 'react'
import { Layout, Button, Dropdown, Avatar, Space, Typography, Modal, Tag } from 'antd'
import {
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
  LogoutOutlined,
  SettingOutlined,
  HomeOutlined,
} from '@ant-design/icons'

const { Header: AntHeader } = Layout
const { Text } = Typography

const Header = ({ collapsed, onToggle, userInfo, onLogout }) => {
  const [profileModalVisible, setProfileModalVisible] = useState(false)

  const handleMenuClick = ({ key }) => {
    if (key === 'profile') {
      setProfileModalVisible(true)
    } else if (key === 'logout') {
      onLogout()
    }
  }

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
    },
  ]

  return (
    <>
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
          <Button
            type="text"
            icon={<HomeOutlined />}
            onClick={() => window.open('http://8.138.114.34', '_blank')}
            className="text-gray-600 hover:text-blue-600 hover:bg-blue-50"
            size="middle"
            title="回到门户网站"
            style={{ fontSize: '14px', height: '32px' }}
          >
            <span className="hidden sm:inline ml-1" style={{ fontSize: '14px' }}>门户</span>
          </Button>
          
          <Dropdown
            menu={{ items: userMenuItems, onClick: handleMenuClick }}
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

      <Modal
        title="个人资料"
        open={profileModalVisible}
        onCancel={() => setProfileModalVisible(false)}
        footer={[
          <Button key="close" type="primary" onClick={() => setProfileModalVisible(false)}>
            关闭
          </Button>
        ]}
        width={600}
      >
        <div className="py-4">
          <div className="grid grid-cols-2 gap-4 mb-6">
            <div>
              <span className="text-gray-600">用户名：</span>
              <span className="font-medium">{userInfo?.username || '-'}</span>
            </div>
            <div>
              <span className="text-gray-600">真实姓名：</span>
              <span className="font-medium">{userInfo?.realName || '-'}</span>
            </div>
            <div className="col-span-2">
              <span className="text-gray-600">邮箱：</span>
              <span className="font-medium">{userInfo?.email || '-'}</span>
            </div>
            <div>
              <span className="text-gray-600">状态：</span>
              <Tag color="green">启用</Tag>
            </div>
          </div>
          <div>
            <div className="text-gray-600 mb-2">我的权限：</div>
            <div className="flex flex-wrap gap-2">
              {userInfo?.permissions && userInfo.permissions.length > 0 ? (
                userInfo.permissions.map(perm => (
                  <Tag key={perm} color="blue">{perm}</Tag>
                ))
              ) : (
                <span className="text-gray-400">无权限信息</span>
              )}
            </div>
          </div>
        </div>
      </Modal>
    </>
  )
}

export default Header