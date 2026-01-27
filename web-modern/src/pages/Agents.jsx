import React, { useState, useEffect } from 'react'
import { Card, Table, Tag, Button, Space, Typography, Input, Select, Avatar, Tooltip, Modal, message } from 'antd'
import {
  DesktopOutlined,
  SearchOutlined,
  ReloadOutlined,
  PlusOutlined,
  DeleteOutlined,
  SettingOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons'

const { Title, Text } = Typography
const { Search } = Input
const { Option } = Select

const Agents = () => {
  const [loading, setLoading] = useState(false)
  const [agents, setAgents] = useState([
    {
      key: '1',
      id: 'agent-001',
      hostname: 'web-server-01',
      ip: '192.168.1.101',
      os: 'Ubuntu 20.04',
      status: 'online',
      lastHeartbeat: '2024-01-15 10:30:25',
      tasks: 15,
      cpu: 45,
      memory: 68,
      uptime: '15天 8小时',
    },
    {
      key: '2',
      id: 'agent-002',
      hostname: 'db-server-01',
      ip: '192.168.1.102',
      os: 'CentOS 8',
      status: 'online',
      lastHeartbeat: '2024-01-15 10:30:20',
      tasks: 8,
      cpu: 32,
      memory: 55,
      uptime: '22天 3小时',
    },
    {
      key: '3',
      id: 'agent-003',
      hostname: 'app-server-01',
      ip: '192.168.1.103',
      os: 'Windows Server 2019',
      status: 'offline',
      lastHeartbeat: '2024-01-15 09:45:12',
      tasks: 3,
      cpu: 0,
      memory: 0,
      uptime: '0天 0小时',
    },
  ])

  const [filteredAgents, setFilteredAgents] = useState(agents)
  const [searchText, setSearchText] = useState('')
  const [statusFilter, setStatusFilter] = useState('all')

  useEffect(() => {
    filterAgents()
  }, [searchText, statusFilter, agents])

  const filterAgents = () => {
    let filtered = agents

    if (searchText) {
      filtered = filtered.filter(agent =>
        agent.hostname.toLowerCase().includes(searchText.toLowerCase()) ||
        agent.ip.includes(searchText) ||
        agent.id.toLowerCase().includes(searchText.toLowerCase())
      )
    }

    if (statusFilter !== 'all') {
      filtered = filtered.filter(agent => agent.status === statusFilter)
    }

    setFilteredAgents(filtered)
  }

  const handleRefresh = () => {
    setLoading(true)
    setTimeout(() => {
      setLoading(false)
      message.success('数据已刷新')
    }, 1000)
  }

  const handleDeleteAgent = (agent) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除客户端 ${agent.hostname} 吗？`,
      okText: '确定',
      cancelText: '取消',
      onOk() {
        setAgents(agents.filter(a => a.key !== agent.key))
        message.success('客户端已删除')
      },
    })
  }

  const columns = [
    {
      title: '客户端信息',
      key: 'info',
      render: (_, record) => (
        <Space>
          <Avatar 
            icon={<DesktopOutlined />} 
            className={record.status === 'online' ? 'bg-green-500' : 'bg-gray-400'}
          />
          <div>
            <Text strong className="block">{record.hostname}</Text>
            <Text type="secondary" className="text-sm">{record.id}</Text>
          </div>
        </Space>
      ),
    },
    {
      title: 'IP地址',
      dataIndex: 'ip',
      key: 'ip',
      render: (text) => <Text code>{text}</Text>,
    },
    {
      title: '操作系统',
      dataIndex: 'os',
      key: 'os',
      render: (text) => (
        <Tag color={text.includes('Windows') ? 'blue' : 'green'}>
          {text}
        </Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status) => {
        const statusConfig = {
          online: { 
            color: 'success', 
            text: '在线', 
            icon: <CheckCircleOutlined /> 
          },
          offline: { 
            color: 'error', 
            text: '离线', 
            icon: <ExclamationCircleOutlined /> 
          },
          busy: { 
            color: 'warning', 
            text: '忙碌', 
            icon: <ClockCircleOutlined /> 
          },
        }
        const config = statusConfig[status]
        return (
          <Tag color={config.color} icon={config.icon}>
            {config.text}
          </Tag>
        )
      },
    },
    {
      title: '资源使用',
      key: 'resources',
      render: (_, record) => (
        <div className="space-y-1">
          <div className="flex items-center space-x-2">
            <Text className="text-xs text-gray-500 w-8">CPU</Text>
            <div className="flex-1 bg-gray-200 rounded-full h-2">
              <div 
                className="bg-blue-500 h-2 rounded-full transition-all duration-300"
                style={{ width: `${record.cpu}%` }}
              />
            </div>
            <Text className="text-xs w-8">{record.cpu}%</Text>
          </div>
          <div className="flex items-center space-x-2">
            <Text className="text-xs text-gray-500 w-8">MEM</Text>
            <div className="flex-1 bg-gray-200 rounded-full h-2">
              <div 
                className="bg-green-500 h-2 rounded-full transition-all duration-300"
                style={{ width: `${record.memory}%` }}
              />
            </div>
            <Text className="text-xs w-8">{record.memory}%</Text>
          </div>
        </div>
      ),
    },
    {
      title: '任务数',
      dataIndex: 'tasks',
      key: 'tasks',
      render: (text) => (
        <Tag color="blue" className="font-mono">
          {text}
        </Tag>
      ),
    },
    {
      title: '最后心跳',
      dataIndex: 'lastHeartbeat',
      key: 'lastHeartbeat',
      render: (text) => (
        <Tooltip title={text}>
          <Text type="secondary" className="text-sm">
            {text.split(' ')[1]}
          </Text>
        </Tooltip>
      ),
    },
    {
      title: '运行时间',
      dataIndex: 'uptime',
      key: 'uptime',
      render: (text) => <Text className="text-sm">{text}</Text>,
    },
    {
      title: '操作',
      key: 'actions',
      render: (_, record) => (
        <Space>
          <Tooltip title="配置">
            <Button 
              type="text" 
              icon={<SettingOutlined />} 
              size="small"
              className="text-blue-500 hover:bg-blue-50"
            />
          </Tooltip>
          <Tooltip title="删除">
            <Button 
              type="text" 
              icon={<DeleteOutlined />} 
              size="small"
              danger
              onClick={() => handleDeleteAgent(record)}
            />
          </Tooltip>
        </Space>
      ),
    },
  ]

  return (
    <div className="space-y-6 animate-fade-in">
      {/* 页面标题 */}
      <div className="flex items-center justify-between">
        <div>
          <Title level={2} className="mb-2 flex items-center">
            <DesktopOutlined className="mr-3 text-blue-500" />
            客户端管理
          </Title>
          <Text type="secondary" className="text-base">
            管理和监控所有连接的客户端节点
          </Text>
        </div>
        <Space>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            className="shadow-lg"
          >
            添加客户端
          </Button>
          <Button
            icon={<ReloadOutlined />}
            loading={loading}
            onClick={handleRefresh}
          >
            刷新
          </Button>
        </Space>
      </div>

      {/* 工具栏 */}
      <Card className="shadow-lg">
        <div className="flex flex-col sm:flex-row gap-4 items-start sm:items-center justify-between">
          <Space wrap>
            <Search
              placeholder="搜索主机名、IP或ID"
              allowClear
              style={{ width: 250 }}
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              prefix={<SearchOutlined className="text-gray-400" />}
            />
            <Select
              value={statusFilter}
              onChange={setStatusFilter}
              style={{ width: 120 }}
            >
              <Option value="all">全部状态</Option>
              <Option value="online">在线</Option>
              <Option value="offline">离线</Option>
              <Option value="busy">忙碌</Option>
            </Select>
          </Space>
          
          <div className="flex items-center space-x-4 text-sm">
            <Space>
              <div className="w-3 h-3 bg-green-500 rounded-full"></div>
              <Text>在线: {agents.filter(a => a.status === 'online').length}</Text>
            </Space>
            <Space>
              <div className="w-3 h-3 bg-red-500 rounded-full"></div>
              <Text>离线: {agents.filter(a => a.status === 'offline').length}</Text>
            </Space>
            <Space>
              <div className="w-3 h-3 bg-yellow-500 rounded-full"></div>
              <Text>忙碌: {agents.filter(a => a.status === 'busy').length}</Text>
            </Space>
          </div>
        </div>
      </Card>

      {/* 客户端列表 */}
      <Card className="shadow-lg">
        <Table
          columns={columns}
          dataSource={filteredAgents}
          loading={loading}
          pagination={{
            total: filteredAgents.length,
            pageSize: 10,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total, range) => 
              `第 ${range[0]}-${range[1]} 条，共 ${total} 条记录`,
          }}
          className="rounded-lg overflow-hidden"
          scroll={{ x: 1200 }}
        />
      </Card>
    </div>
  )
}

export default Agents