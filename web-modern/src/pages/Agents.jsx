import React, { useState, useEffect } from 'react'
import { Card, Table, Tag, Button, Space, Typography, Input, Select, Avatar, Tooltip, Modal, message } from 'antd'
import {
  DesktopOutlined,
  SearchOutlined,
  ReloadOutlined,
  DeleteOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  ClockCircleOutlined,
  SyncOutlined,
} from '@ant-design/icons'
import api from '../services/auth'

const { Title, Text } = Typography
const { Search } = Input
const { Option } = Select

const Agents = () => {
  const [loading, setLoading] = useState(false)
  const [agents, setAgents] = useState([])
  const [filteredAgents, setFilteredAgents] = useState([])
  const [searchText, setSearchText] = useState('')
  const [statusFilter, setStatusFilter] = useState('all')

  // 加载Agent列表
  const loadAgents = async () => {
    setLoading(true)
    try {
      const response = await api.get('/web/agents')
      const agentList = await Promise.all(response.content.map(async agent => {
        // CPU负载转换为百分比（0.0-1.0 -> 0-100）
        const cpuPercent = agent.cpuLoad ? Math.round(agent.cpuLoad * 100) : 0
        
        // 内存使用率计算
        let memoryPercent = 0
        if (agent.totalMemMb && agent.freeMemMb) {
          const usedMemMb = agent.totalMemMb - agent.freeMemMb
          memoryPercent = Math.round((usedMemMb / agent.totalMemMb) * 100)
        }
        
        // 获取Agent所属分组
        let groups = []
        try {
          const groupsResp = await api.get(`/web/agents/${agent.agentId}/groups`)
          groups = groupsResp.groups || []
        } catch (error) {
          console.error('获取分组失败', error)
        }
        
        return {
          key: agent.agentId,
          id: agent.agentId,
          hostname: agent.hostname,
          ip: agent.ip || 'N/A',
          os: agent.osType,
          status: agent.status === 'ONLINE' ? 'online' : 'offline',
          lastHeartbeat: agent.lastHeartbeat ? new Date(agent.lastHeartbeat).toLocaleString('zh-CN') : 'N/A',
          tasks: agent.taskCount || 0, // 直接使用Agent实体中的任务计数
          cpu: cpuPercent,
          memory: memoryPercent,
          uptime: calculateUptime(agent.createdAt),
          groups: groups,
        }
      }))
      setAgents(agentList)
    } catch (error) {
      console.error('Failed to load agents:', error)
      message.error('加载客户端列表失败')
    } finally {
      setLoading(false)
    }
  }

  // 计算运行时间
  const calculateUptime = (createdAt) => {
    if (!createdAt) return '0天 0小时'
    const now = new Date()
    const created = new Date(createdAt)
    const diff = now - created
    const days = Math.floor(diff / (1000 * 60 * 60 * 24))
    const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60))
    return `${days}天 ${hours}小时`
  }

  useEffect(() => {
    loadAgents()
    // 每30秒自动刷新
    const interval = setInterval(loadAgents, 30000)
    return () => clearInterval(interval)
  }, [])

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
    loadAgents()
    message.success('数据已刷新')
  }

  const handleDeleteAgent = async (agent) => {
    // 检查是否在线 - 使用大写状态进行比较
    if (agent.status === 'online' || agent.status === 'ONLINE') {
      message.warning('不能删除在线的客户端，请先停止客户端')
      return
    }
    
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除客户端 ${agent.hostname} 吗？此操作不可恢复。`,
      okText: '确定',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        try {
          await api.delete(`/web/agents/${agent.id}`)
          message.success('客户端已删除')
          loadAgents() // 重新加载列表
        } catch (error) {
          console.error('删除客户端失败:', error)
          message.error('删除失败: ' + (error.response?.data?.message || error.message))
        }
      },
    })
  }

  // 状态收集功能 - 创建一个任务来收集Agent状态
  const handleCollectStatus = async (agent) => {
    try {
      // 根据操作系统选择不同的状态收集脚本
      const isWindows = agent.os && agent.os.toLowerCase().includes('windows')
      
      const statusScript = isWindows 
        ? `@echo off
echo ===== System Information =====
systeminfo | findstr /C:"OS Name" /C:"OS Version" /C:"System Type"
echo.
echo ===== CPU Usage =====
wmic cpu get loadpercentage
echo.
echo ===== Memory Usage =====
wmic OS get FreePhysicalMemory,TotalVisibleMemorySize /Value
echo.
echo ===== Disk Usage =====
wmic logicaldisk get name,size,freespace
echo.
echo ===== Network Interfaces =====
ipconfig /all
echo.
echo ===== Running Processes (Top 10) =====
tasklist /FO TABLE | findstr /V "Image"
`
        : `#!/bin/bash
echo "===== System Information ====="
uname -a
cat /etc/*release 2>/dev/null | head -5
echo ""
echo "===== CPU Usage ====="
top -bn1 | grep "Cpu(s)" | awk '{print "CPU Usage: " $2}'
echo ""
echo "===== Memory Usage ====="
free -h
echo ""
echo "===== Disk Usage ====="
df -h
echo ""
echo "===== Network Interfaces ====="
ip addr show 2>/dev/null || ifconfig
echo ""
echo "===== Running Processes (Top 10) ====="
ps aux --sort=-%mem | head -11
`

      const taskSpec = {
        scriptLang: isWindows ? 'cmd' : 'shell',
        scriptContent: statusScript,
        timeoutSec: 60
      }
      
      const params = new URLSearchParams()
      params.append('agentIds', agent.id)
      params.append('taskName', `状态收集 - ${agent.hostname}`)
      params.append('autoStart', true)
      
      await api.post(`/web/tasks/create?${params.toString()}`, taskSpec)
      message.success(`已为客户端 ${agent.hostname} 创建状态收集任务`)
    } catch (error) {
      console.error('创建状态收集任务失败:', error)
      message.error('创建状态收集任务失败: ' + (error.response?.data?.message || error.message))
    }
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
      title: '所属分组',
      key: 'groups',
      render: (_, record) => (
        <Space size="small" wrap>
          {record.groups && record.groups.length > 0 ? (
            record.groups.map(group => (
              <Tag color="purple" key={group.id}>
                {group.name}
              </Tag>
            ))
          ) : (
            <Text type="secondary" className="text-xs">未分组</Text>
          )}
        </Space>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      render: (_, record) => (
        <Space>
          <Tooltip title="状态收集">
            <Button 
              type="text" 
              icon={<SyncOutlined />} 
              size="small"
              className="text-green-500 hover:bg-green-50"
              onClick={() => handleCollectStatus(record)}
            />
          </Tooltip>
          <Tooltip title={record.status === 'online' ? '在线客户端不能删除' : '删除'}>
            <Button 
              type="text" 
              icon={<DeleteOutlined />} 
              size="small"
              danger
              disabled={record.status === 'online'}
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
        <Button
          icon={<ReloadOutlined />}
          loading={loading}
          onClick={handleRefresh}
        >
          刷新
        </Button>
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