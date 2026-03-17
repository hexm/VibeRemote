import React, { useState, useEffect } from 'react'
import { Table, Button, Modal, Form, Input, Select, Tag, Space, message, Popconfirm, Checkbox } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, KeyOutlined, StopOutlined, CheckCircleOutlined, UserOutlined } from '@ant-design/icons'
import api from '../services/auth'

const { Option } = Select

// 权限模板
const PERMISSION_TEMPLATES = {
  ADMIN: {
    name: '管理员',
    permissions: [
      'user:create', 'user:edit', 'user:delete', 'user:view',
      'task:create', 'task:execute', 'task:delete', 'task:view',
      'script:create', 'script:edit', 'script:delete', 'script:view',
      'agent:view', 'agent:group',
      'log:view', 'system:settings'
    ]
  },
  OPERATOR: {
    name: '操作员',
    permissions: [
      'task:create', 'task:execute', 'task:delete', 'task:view',
      'script:create', 'script:edit', 'script:delete', 'script:view',
      'agent:view', 'agent:group',
      'log:view'
    ]
  },
  READONLY: {
    name: '只读',
    permissions: ['task:view', 'script:view', 'agent:view', 'log:view']
  }
}

const Users = () => {
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(false)
  const [modalVisible, setModalVisible] = useState(false)
  const [modalType, setModalType] = useState('create') // create, edit, resetPassword
  const [currentUser, setCurrentUser] = useState(null)
  const [permissions, setPermissions] = useState([])
  const [form] = Form.useForm()

  useEffect(() => {
    fetchUsers()
    fetchPermissions()
  }, [])

  const fetchUsers = async () => {
    setLoading(true)
    try {
      const response = await api.get('/web/users')
      setUsers(response.content || [])
    } catch (error) {
      console.error('获取用户列表失败:', error)
      if (error.status === 403) {
        message.warning({
          content: '您没有权限查看用户列表，请联系管理员',
          key: 'user-list-permission',
          duration: 3
        })
      } else {
        message.error({
          content: '获取用户列表失败',
          key: 'user-list-error',
          duration: 3
        })
      }
    } finally {
      setLoading(false)
    }
  }

  const fetchPermissions = async () => {
    try {
      const response = await api.get('/web/permissions')
      setPermissions(response.permissions || [])
    } catch (error) {
      console.error('获取权限列表失败:', error)
      if (error.status === 403) {
        // 权限不足时静默失败，不显示错误消息
        setPermissions([])
      }
    }
  }

  const handleCreate = () => {
    setModalType('create')
    setCurrentUser(null)
    form.resetFields()
    setModalVisible(true)
  }

  const handleEdit = async (record) => {
    setModalType('edit')
    setModalVisible(true)
    
    // 从API获取完整的用户信息(包括权限列表)
    try {
      const response = await api.get(`/web/users/${record.id}`)
      const fullUserData = response
      console.log('[DEBUG] 获取到完整用户数据:', fullUserData)
      setCurrentUser(fullUserData)
    } catch (error) {
      console.error('获取用户详情失败', error)
      message.error(error.message || error.error || '获取用户详情失败')
      setCurrentUser(record) // 降级使用表格数据
    }
  }

  const handleResetPassword = (record) => {
    setModalType('resetPassword')
    setCurrentUser(record)
    form.resetFields()
    setModalVisible(true)
  }

  const handleDelete = async (userId) => {
    try {
      await api.delete(`/web/users/${userId}`)
      message.success('删除成功')
      fetchUsers()
    } catch (error) {
      console.error('删除失败:', error)
      message.error(error.message || error.error || '删除失败')
    }
  }

  const handleToggleStatus = async (userId) => {
    try {
      await api.post(`/web/users/${userId}/toggle-status`)
      message.success('状态更新成功')
      fetchUsers()
    } catch (error) {
      console.error('状态更新失败:', error)
      message.error(error.message || error.error || '状态更新失败')
    }
  }

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields()

      if (modalType === 'create') {
        await api.post('/web/users', values)
        message.success('创建成功')
      } else if (modalType === 'edit') {
        await api.put(`/web/users/${currentUser.id}`, values)
        message.success('更新成功')
      } else if (modalType === 'resetPassword') {
        await api.post(`/web/users/${currentUser.id}/reset-password`, values)
        message.success('密码重置成功')
      }

      setModalVisible(false)
      form.resetFields()
      fetchUsers()
    } catch (error) {
      console.error('操作失败:', error)
      // 提取有意义的错误信息
      let errorMessage = '操作失败'
      
      if (error.message) {
        errorMessage = error.message
      } else if (error.error) {
        errorMessage = error.error
      } else if (typeof error === 'string') {
        errorMessage = error
      }
      
      // 针对常见错误提供友好提示
      if (error.status === 403) {
        errorMessage = '您没有权限执行此操作'
      } else if (error.status === 409) {
        errorMessage = '用户名已存在'
      } else if (error.status === 400) {
        errorMessage = error.message || '请求参数错误，请检查输入'
      }
      
      message.error(errorMessage)
      // 不关闭Modal，让用户可以修改后重试
    }
  }

  const applyTemplate = (templateKey) => {
    const template = PERMISSION_TEMPLATES[templateKey]
    form.setFieldsValue({ permissions: template.permissions })
  }

  // 按类别分组权限
  const groupedPermissions = permissions.reduce((acc, perm) => {
    if (!acc[perm.category]) {
      acc[perm.category] = []
    }
    acc[perm.category].push(perm)
    return acc
  }, {})

  // 切换类别的全选/全不选
  const toggleCategoryPermissions = (category, perms) => {
    const currentPermissions = form.getFieldValue('permissions') || []
    const categoryPermCodes = perms.map(p => p.code)
    const allSelected = categoryPermCodes.every(code => currentPermissions.includes(code))

    let newPermissions
    if (allSelected) {
      // 全不选：移除该类别的所有权限
      newPermissions = currentPermissions.filter(code => !categoryPermCodes.includes(code))
    } else {
      // 全选：添加该类别的所有权限
      const permissionsSet = new Set([...currentPermissions, ...categoryPermCodes])
      newPermissions = Array.from(permissionsSet)
    }

    form.setFieldsValue({ permissions: newPermissions })
  }

  // 检查类别是否全选
  const isCategoryAllSelected = (category, perms) => {
    const currentPermissions = form.getFieldValue('permissions') || []
    const categoryPermCodes = perms.map(p => p.code)
    return categoryPermCodes.length > 0 && categoryPermCodes.every(code => currentPermissions.includes(code))
  }

  const columns = [
    {
      title: '用户名',
      dataIndex: 'username',
      key: 'username',
      render: (text) => (
        <Space>
          <UserOutlined />
          <span className="font-medium">{text}</span>
        </Space>
      )
    },
    {
      title: '真实姓名',
      dataIndex: 'realName',
      key: 'realName',
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      key: 'email',
    },
    {
      title: '权限数量',
      dataIndex: 'permissionCount',
      key: 'permissionCount',
      render: (count) => <Tag color="blue">{count} 个权限</Tag>
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status) => (
        <Tag color={status === 'ACTIVE' ? 'green' : 'red'}>
          {status === 'ACTIVE' ? '启用' : '禁用'}
        </Tag>
      )
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (text) => text ? new Date(text).toLocaleString() : '-'
    },
    {
      title: '操作',
      key: 'action',
      render: (_, record) => {
        // 获取当前登录用户
        const currentUser = JSON.parse(localStorage.getItem('userInfo') || '{}')
        const isCurrentUser = currentUser.username === record.username
        
        return (
          <Space>
            <Button
              type="link"
              icon={<EditOutlined />}
              onClick={() => handleEdit(record)}
            >
              编辑
            </Button>
            <Button
              type="link"
              icon={<KeyOutlined />}
              onClick={() => handleResetPassword(record)}
            >
              重置密码
            </Button>
            <Button
              type="link"
              icon={record.status === 'ACTIVE' ? <StopOutlined /> : <CheckCircleOutlined />}
              onClick={() => handleToggleStatus(record.id)}
              disabled={isCurrentUser && record.status === 'ACTIVE'}
              title={isCurrentUser && record.status === 'ACTIVE' ? '不能禁用自己的账号' : ''}
            >
              {record.status === 'ACTIVE' ? '禁用' : '启用'}
            </Button>
            <Popconfirm
              title="确定要删除这个用户吗？"
              onConfirm={() => handleDelete(record.id)}
              okText="确定"
              cancelText="取消"
            >
              <Button
                type="link"
                danger
                icon={<DeleteOutlined />}
              >
                删除
              </Button>
            </Popconfirm>
          </Space>
        )
      },
    },
  ]

  return (
    <div className="bg-white rounded-lg shadow p-6">
      <div className="flex justify-between items-center mb-6">
        <h2 className="text-2xl font-bold">用户管理</h2>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={handleCreate}
        >
          创建用户
        </Button>
      </div>

      <Table
        columns={columns}
        dataSource={users}
        rowKey="id"
        loading={loading}
        pagination={{
          pageSize: 10,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 个用户`
        }}
      />

      <Modal
        title={
          modalType === 'create' ? '创建用户' :
          modalType === 'edit' ? '编辑用户' :
          '重置密码'
        }
        open={modalVisible}
        onCancel={() => {
          setModalVisible(false)
          form.resetFields()
        }}
        afterOpenChange={(open) => {
          // Modal打开后，如果是编辑模式，设置表单值
          if (open && modalType === 'edit' && currentUser) {
            console.log('[DEBUG] afterOpenChange - 编辑模式', {
              username: currentUser.username,
              permissions: currentUser.permissions,
              permissionCount: currentUser.permissions?.length
            })
            
            // 使用setTimeout确保DOM完全渲染后再设置值
            setTimeout(() => {
              const values = {
                email: currentUser.email,
                realName: currentUser.realName,
                permissions: currentUser.permissions || []
              }
              console.log('[DEBUG] 设置表单值:', values)
              form.setFieldsValue(values)
              
              // 验证设置是否成功
              setTimeout(() => {
                const currentValues = form.getFieldsValue()
                console.log('[DEBUG] 当前表单值:', currentValues)
              }, 100)
            }, 100)
          }
        }}
        width={modalType === 'resetPassword' ? 500 : 800}
        footer={[
          <Button key="cancel" onClick={() => {
            setModalVisible(false)
            form.resetFields()
          }}>
            取消
          </Button>,
          <Button key="submit" type="primary" onClick={handleModalOk}>
            确定
          </Button>
        ]}
      >
        <Form
          form={form}
          layout="vertical"
          className="mt-4"
        >
          {modalType === 'create' && (
            <>
              <Form.Item
                name="username"
                label="用户名"
                rules={[{ required: true, message: '请输入用户名' }]}
              >
                <Input placeholder="请输入用户名" />
              </Form.Item>
              <Form.Item
                name="password"
                label="密码"
                rules={[
                  { required: true, message: '请输入密码' },
                  { min: 8, message: '密码至少8位' },
                  { pattern: /^(?=.*[A-Za-z])(?=.*\d)/, message: '密码必须包含字母和数字' }
                ]}
              >
                <Input.Password placeholder="至少8位，包含字母和数字" />
              </Form.Item>
            </>
          )}

          {modalType === 'resetPassword' && (
            <Form.Item
              name="newPassword"
              label="新密码"
              rules={[
                { required: true, message: '请输入新密码' },
                { min: 8, message: '密码至少8位' },
                { pattern: /^(?=.*[A-Za-z])(?=.*\d)/, message: '密码必须包含字母和数字' }
              ]}
            >
              <Input.Password placeholder="至少8位，包含字母和数字" />
            </Form.Item>
          )}

          {(modalType === 'create' || modalType === 'edit') && (
            <>
              <Form.Item
                name="email"
                label="邮箱"
                rules={[{ type: 'email', message: '请输入有效的邮箱地址' }]}
              >
                <Input placeholder="请输入邮箱" />
              </Form.Item>
              <Form.Item
                name="realName"
                label="真实姓名"
              >
                <Input placeholder="请输入真实姓名" />
              </Form.Item>

              <div className="mb-2">
                <span className="mr-2 font-medium text-gray-700">快捷模板：</span>
                <Space>
                  <Button size="small" type="default" onClick={() => applyTemplate('ADMIN')}>
                    管理员（16个）
                  </Button>
                  <Button size="small" type="default" onClick={() => applyTemplate('OPERATOR')}>
                    操作员（11个）
                  </Button>
                  <Button size="small" type="default" onClick={() => applyTemplate('READONLY')}>
                    只读（4个）
                  </Button>
                </Space>
              </div>

              <Form.Item
                name="permissions"
                label="权限设置"
                rules={[{ required: true, message: '请选择至少一个权限' }]}
              >
                <Checkbox.Group style={{ width: '100%' }}>
                  {Object.entries(groupedPermissions).map(([category, perms]) => (
                    <div key={category} className="mb-3 p-3 border border-gray-200 rounded bg-gray-50">
                      <div className="flex justify-between items-center mb-2">
                        <span className="font-semibold text-gray-800">{category}</span>
                        <Button
                          size="small"
                          type="link"
                          onClick={() => toggleCategoryPermissions(category, perms)}
                        >
                          {isCategoryAllSelected(category, perms) ? '全不选' : '全选'}
                        </Button>
                      </div>
                      <div className="grid grid-cols-2 gap-2">
                        {perms.map(perm => (
                          <Checkbox key={perm.code} value={perm.code}>
                            <span className="text-sm">{perm.name}</span>
                          </Checkbox>
                        ))}
                      </div>
                    </div>
                  ))}
                </Checkbox.Group>
              </Form.Item>
            </>
          )}
        </Form>
      </Modal>
    </div>
  )
}

export default Users
