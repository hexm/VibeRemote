import React, { useState } from 'react'
import { Form, Input, Button, Card, Typography, Space, Divider } from 'antd'
import { UserOutlined, LockOutlined, RocketOutlined } from '@ant-design/icons'

const { Title, Text } = Typography

const Login = ({ onLogin }) => {
  const [loading, setLoading] = useState(false)
  const [form] = Form.useForm()

  const handleSubmit = async (values) => {
    setLoading(true)
    try {
      const success = await onLogin(values)
      if (!success) {
        // 登录失败时重置表单状态
        form.setFieldsValue({
          username: values.username,
          password: ''
        })
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-50 via-indigo-50 to-purple-50">
      {/* 背景装饰 */}
      <div className="absolute inset-0 overflow-hidden">
        <div className="absolute -top-40 -right-40 w-80 h-80 bg-blue-300 rounded-full mix-blend-multiply filter blur-xl opacity-70 animate-blob"></div>
        <div className="absolute -bottom-40 -left-40 w-80 h-80 bg-purple-300 rounded-full mix-blend-multiply filter blur-xl opacity-70 animate-blob animation-delay-2000"></div>
        <div className="absolute top-40 left-40 w-80 h-80 bg-indigo-300 rounded-full mix-blend-multiply filter blur-xl opacity-70 animate-blob animation-delay-4000"></div>
      </div>

      <div className="relative z-10 w-full max-w-md px-6">
        <Card className="shadow-2xl border-0 backdrop-blur-sm bg-white/90">
          <div className="text-center mb-8">
            <div className="inline-flex items-center justify-center w-16 h-16 bg-gradient-to-r from-blue-500 to-purple-600 rounded-full mb-4">
              <RocketOutlined className="text-2xl text-white" />
            </div>
            <Title level={2} className="mb-2 bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-transparent">
              VibeRemote
            </Title>
            <Text type="secondary" className="text-base">
              分布式脚本管理平台
            </Text>
          </div>

          <Form
            name="login"
            form={form}
            onFinish={handleSubmit}
            autoComplete="off"
            size="large"
            className="space-y-4"
            initialValues={{
              username: '',
              password: ''
            }}
          >
            <Form.Item
              name="username"
              rules={[
                { required: true, message: '请输入用户名' },
                { min: 3, message: '用户名至少3个字符' }
              ]}
            >
              <Input
                prefix={<UserOutlined className="text-gray-400" />}
                placeholder="用户名"
                disabled={loading}
                autoComplete="username"
                className="rounded-lg border-gray-300 hover:border-blue-400 focus:border-blue-500"
                style={{ backgroundColor: 'white' }}
              />
            </Form.Item>

            <Form.Item
              name="password"
              rules={[
                { required: true, message: '请输入密码' },
                { min: 6, message: '密码至少6个字符' }
              ]}
            >
              <Input.Password
                prefix={<LockOutlined className="text-gray-400" />}
                placeholder="密码"
                disabled={loading}
                autoComplete="current-password"
                className="rounded-lg border-gray-300 hover:border-blue-400 focus:border-blue-500"
                style={{ backgroundColor: 'white' }}
              />
            </Form.Item>

            <Form.Item className="mb-6">
              <Button
                type="primary"
                htmlType="submit"
                loading={loading}
                className="w-full h-12 bg-gradient-to-r from-blue-500 to-purple-600 border-0 rounded-lg font-semibold text-base shadow-lg hover:shadow-xl transform hover:-translate-y-0.5 transition-all duration-200"
              >
                {loading ? '登录中...' : '登录'}
              </Button>
            </Form.Item>
          </Form>

          <Divider className="my-6">
            <Text type="secondary" className="text-sm">
              快捷登录
            </Text>
          </Divider>

          <div className="space-y-3">
            <Button
              block
              onClick={() => {
                form.setFieldsValue({ username: 'demo', password: 'demo123456' })
                form.submit()
              }}
              disabled={loading}
              style={{ height: 44, borderRadius: 8 }}
            >
              <Space>
                <Text strong style={{ color: '#52c41a' }}>体验账号</Text>
                <Text type="secondary">demo / demo123456</Text>
              </Space>
            </Button>
          </div>
        </Card>

        <div className="text-center mt-8">
          <Text type="secondary" className="text-sm">
            © 2024 VibeRemote. 现代化分布式脚本管理平台
          </Text>
        </div>
      </div>
    </div>
  )
}

export default Login