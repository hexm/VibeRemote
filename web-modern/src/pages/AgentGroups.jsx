import React, { useState, useEffect } from 'react'
import { Table, Button, Modal, Form, Input, Select, Tag, Space, message, Popconfirm, Card, Drawer } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, TeamOutlined, EyeOutlined } from '@ant-design/icons'
import axios from 'axios'

const { Option } = Select
const { TextArea } = Input

const GROUP_TYPES = {
  BUSINESS: { label: '业务分组', color: 'blue' },
  ENVIRONMENT: { label: '环境分组', color: 'green' },
  REGION: { label: '地域分组', color: 'orange' },
  CUSTOM: { label: '自定义分组', color: 'purple' }
}

const AgentGroups = () => {
  const [groups, setGroups] = useState([])
  const [agents, setAgents] = useState([])
  const [loading, setLoading] = useState(false)
  const [modalVisible, setModalVisible] = useState(false)
  const [drawerVisible, setDrawerVisible] = useState(false)
  const [modalType, setModalType] = useState('create') // create, edit
  const [currentGroup, setCurrentGroup] = useState(null)
  const [selectedAgents, setSelectedAgents] = useState([])
  const [form] = Form.useForm()

  useEffect(() => {
    fetchGroups()
    fetchAgents()
  }, [])

  const fetchGroups = async () => {
    setLoading(true)
    try {
      const response = await axios.get('/api/web/agent-groups')
      setGroups(response.data.content || [])
    } catch (error) {
      message.error('获取分组列表失败')
    } finally {
      setLoading(false)
    }
  }

  const fetchAgents = async () => {
    try {
      const response = await axios.get('/api/web/agents')
      setAgents(response.data.content || [])
    } catch (error) {
      console.error('获取Agent列表失败', error)
    }
  }

  const fetchGroupDetail = async (groupId) => {
    try {
      const response = await axios.get(`/api/web/agent-groups/${groupId}`)
      setCurrentGroup(response.data)
      setSelectedAgents(response.data.agents?.map(a => a.agentId) || [])
      setDrawerVisible(true)
    } catch (error) {
      message.error('获取分组详情失败')
    }
  }

  const handleCreate = () => {
    setModalType('create')
    setCurrentGroup(null)
    form.resetFields()
    setModalVisible(true)
  }

  const handleEdit = (record) => {
    setModalType('edit')
    setCurrentGroup(record)
    form.setFieldsValue({
      name: record.name,
      description: record.description,
      type: record.type
    })
    setModalVisible(true)
  }

  const handleDelete = async (groupId) => {
    try {
      await axios.delete(`/api/web/agent-groups/${groupId}`)
      message.success('删除成功')
      fetchGroups()
    } catch (error) {
      message.error('删除失败')
    }
  }

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields()
      
      if (modalType === 'create') {
        await axios.post('/api/web/agent-groups', values)
        message.success('创建成功')
      } else if (modalType === 'edit') {
        await axios.put(`/api/web/agent-groups/${currentGroup.id}`, values)
        message.success('更新成功')
      }
      
      setModalVisible(false)
      form.resetFields()
      fetchGroups()
    } catch (error) {
      message.error(error.response?.data?.message || '操作失败')
    }
  }

  const handleAddAgents = async () => {
    if (!currentGroup) return
    
    try {
      // 获取当前分组中的Agent
      const currentAgentIds = currentGroup.agents?.map(a => a.agentId) || []
      // 找出新增的Agent
      const newAgentIds = selectedAgents.filter(id => !currentAgentIds.includes(id))
      
      if (newAgentIds.length > 0) {
        await axios.post(`/api/web/agent-groups/${currentGroup.id}/agents`, {
          agentIds: newAgentIds
        })
        message.success(`成功添加 ${newAgentIds.length} 个Agent`)
        fetchGroupDetail(currentGroup.id)
        fetchGroups()
      }
    } catch (error) {
      message.error('添加Agent失败')
    }
  }

  const handleRemoveAgent = async (agentId) => {
    if (!currentGroup) return
    
    try {
      await axios.delete(`/api/web/agent-groups/${currentGroup.id}/agents`, {
        data: { agentIds: [agentId] }
      })
      message.success('移除成功')
      fetchGroupDetail(currentGroup.id)
      fetchGroups()
    } catch (error) {
      message.error('移除失败')
    }
  }

  const columns = [
    {
      title: '分组名称',
      dataIndex: 'name',
      key: 'name',
      render: (text) => (
        <Space>
          <TeamOutlined />
          <span className="font-medium">{text}</span>
        </Space>
      )
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      render: (type) => {
        const typeInfo = GROUP_TYPES[type] || GROUP_TYPES.CUSTOM
        return <Tag color={typeInfo.color}>{typeInfo.label}</Tag>
      }
    },
    {
      title: 'Agent数量',
      dataIndex: 'agentCount',
      key: 'agentCount',
      render: (count) => <Tag color="blue">{count} 个</Tag>
    },
    {
      title: '创建者',
      dataIndex: 'createdBy',
      key: 'createdBy',
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
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            icon={<EyeOutlined />}
            onClick={() => fetchGroupDetail(record.id)}
          >
            查看
          </Button>
          <Button
            type="link"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          >
            编辑
          </Button>
          <Popconfirm
            title="确定要删除这个分组吗？"
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
      ),
    },
  ]

  const agentColumns = [
    {
      title: 'Agent ID',
      dataIndex: 'agentId',
      key: 'agentId',
    },
    {
      title: '主机名',
      dataIndex: 'hostname',
      key: 'hostname',
    },
    {
      title: '加入时间',
      dataIndex: 'addedAt',
      key: 'addedAt',
      render: (text) => text ? new Date(text).toLocaleString() : '-'
    },
    {
      title: '操作',
      key: 'action',
      render: (_, record) => (
        <Popconfirm
          title="确定要从分组中移除这个Agent吗？"
          onConfirm={() => handleRemoveAgent(record.agentId)}
          okText="确定"
          cancelText="取消"
        >
          <Button type="link" danger size="small">
            移除
          </Button>
        </Popconfirm>
      ),
    },
  ]

  return (
    <div className="bg-white rounded-lg shadow p-6">
      <div className="flex justify-between items-center mb-6">
        <h2 className="text-2xl font-bold">Agent分组管理</h2>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={handleCreate}
        >
          创建分组
        </Button>
      </div>

      <Table
        columns={columns}
        dataSource={groups}
        rowKey="id"
        loading={loading}
        pagination={{
          pageSize: 10,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 个分组`
        }}
      />

      <Modal
        title={modalType === 'create' ? '创建分组' : '编辑分组'}
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={() => {
          setModalVisible(false)
          form.resetFields()
        }}
        okText="确定"
        cancelText="取消"
      >
        <Form
          form={form}
          layout="vertical"
          className="mt-4"
        >
          <Form.Item
            name="name"
            label="分组名称"
            rules={[{ required: true, message: '请输入分组名称' }]}
          >
            <Input placeholder="请输入分组名称" />
          </Form.Item>
          
          <Form.Item
            name="type"
            label="分组类型"
            rules={[{ required: true, message: '请选择分组类型' }]}
          >
            <Select placeholder="请选择分组类型">
              {Object.entries(GROUP_TYPES).map(([key, value]) => (
                <Option key={key} value={key}>{value.label}</Option>
              ))}
            </Select>
          </Form.Item>
          
          <Form.Item
            name="description"
            label="描述"
          >
            <TextArea rows={4} placeholder="请输入分组描述" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={`分组详情 - ${currentGroup?.name || ''}`}
        placement="right"
        width={800}
        onClose={() => setDrawerVisible(false)}
        open={drawerVisible}
      >
        {currentGroup && (
          <div>
            <Card title="基本信息" className="mb-4">
              <p><strong>分组名称：</strong>{currentGroup.name}</p>
              <p><strong>分组类型：</strong>
                <Tag color={GROUP_TYPES[currentGroup.type]?.color}>
                  {GROUP_TYPES[currentGroup.type]?.label}
                </Tag>
              </p>
              <p><strong>描述：</strong>{currentGroup.description || '无'}</p>
              <p><strong>创建者：</strong>{currentGroup.createdBy}</p>
              <p><strong>创建时间：</strong>{new Date(currentGroup.createdAt).toLocaleString()}</p>
            </Card>

            <Card 
              title="分组成员" 
              extra={
                <Select
                  mode="multiple"
                  style={{ width: 300 }}
                  placeholder="选择要添加的Agent"
                  value={selectedAgents}
                  onChange={setSelectedAgents}
                  onBlur={handleAddAgents}
                >
                  {agents.map(agent => (
                    <Option key={agent.agentId} value={agent.agentId}>
                      {agent.hostname} ({agent.agentId})
                    </Option>
                  ))}
                </Select>
              }
            >
              <Table
                columns={agentColumns}
                dataSource={currentGroup.agents || []}
                rowKey="agentId"
                pagination={false}
                size="small"
              />
            </Card>
          </div>
        )}
      </Drawer>
    </div>
  )
}

export default AgentGroups
