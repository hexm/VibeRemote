import React, { useState, useEffect } from 'react'
import { Card, Table, Tag, Button, Space, Typography, Input, Select, Avatar, Tooltip, Modal, message, Row, Col, Descriptions, Statistic, Divider } from 'antd'
import {
  DesktopOutlined,
  SearchOutlined,
  ReloadOutlined,
  DeleteOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  ClockCircleOutlined,
  SyncOutlined,
  EyeOutlined,
  HddOutlined,
  UserOutlined,
  FolderOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import api from '../services/auth'

const { Title, Text } = Typography
const { Search } = Input
const { Option } = Select

const Agents = () => {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [agents, setAgents] = useState([])
  const [filteredAgents, setFilteredAgents] = useState([])
  const [searchText, setSearchText] = useState('')
  const [statusFilter, setStatusFilter] = useState('all')
  const [detailModalVisible, setDetailModalVisible] = useState(false)
  const [selectedAgent, setSelectedAgent] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)

  // 处理单个 Agent 数据的辅助函数
  const processAgentData = async (agent) => {
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
      agentId: agent.agentId, // 添加 agentId 字段
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
      // 添加深度检查任务信息
      lastDiagnosticTaskId: agent.lastDiagnosticTaskId,
      lastDiagnosticTaskName: agent.lastDiagnosticTaskName,
      lastDiagnosticTime: agent.lastDiagnosticTime ? new Date(agent.lastDiagnosticTime).toLocaleString('zh-CN') : null,
      // 添加扩展系统信息
      startUser: agent.startUser,
      workingDir: agent.workingDir,
      diskSpaceGb: agent.diskSpaceGb,
      freeSpaceGb: agent.freeSpaceGb,
      osVersion: agent.osVersion,
      javaVersion: agent.javaVersion,
      agentVersion: agent.agentVersion,
      // 计算磁盘使用率
      diskUsagePercent: agent.diskSpaceGb && agent.freeSpaceGb ? 
        Math.round(((agent.diskSpaceGb - agent.freeSpaceGb) / agent.diskSpaceGb) * 100) : 0,
      // 格式化内存信息
      totalMemInfo: agent.totalMemMb ? `${(agent.totalMemMb / 1024).toFixed(1)} GB` : 'N/A',
      freeMemInfo: agent.freeMemMb ? `${(agent.freeMemMb / 1024).toFixed(1)} GB` : 'N/A',
      // 格式化磁盘信息
      diskInfo: agent.diskSpaceGb ? `${agent.diskSpaceGb} GB` : 'N/A',
      freeSpaceInfo: agent.freeSpaceGb ? `${agent.freeSpaceGb} GB` : 'N/A',
      memoryUsagePercent: memoryPercent,
      taskCount: agent.taskCount || 0
    }
  }

  // 加载Agent列表
  const loadAgents = async () => {
    setLoading(true)
    try {
      const response = await api.get('/web/agents')
      const agentList = await Promise.all(response.content.map(processAgentData))
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

  // 查看客户端详情
  const handleViewDetail = async (agent) => {
    setSelectedAgent(agent)
    setDetailModalVisible(true)
    setDetailLoading(true)
    
    try {
      // 获取更详细的客户端信息
      const response = await api.get(`/web/agents/${agent.id}`)
      setSelectedAgent({
        ...agent,
        ...response,
        // 格式化磁盘空间信息
        diskInfo: response.diskSpaceGb ? `${response.diskSpaceGb}GB` : 'N/A',
        freeSpaceInfo: response.freeSpaceGb ? `${response.freeSpaceGb}GB` : 'N/A',
        diskUsagePercent: response.diskSpaceGb && response.freeSpaceGb 
          ? Math.round(((response.diskSpaceGb - response.freeSpaceGb) / response.diskSpaceGb) * 100)
          : 0,
        // 格式化内存信息
        totalMemInfo: response.totalMemMb ? `${Math.round(response.totalMemMb / 1024)}GB` : 'N/A',
        freeMemInfo: response.freeMemMb ? `${Math.round(response.freeMemMb / 1024)}GB` : 'N/A',
        memoryUsagePercent: response.totalMemMb && response.freeMemMb 
          ? Math.round(((response.totalMemMb - response.freeMemMb) / response.totalMemMb) * 100)
          : 0
      })
    } catch (error) {
      console.error('获取客户端详情失败:', error)
      message.error('获取客户端详情失败')
    } finally {
      setDetailLoading(false)
    }
  }

  // 打开任务详情的辅助函数
  const openTaskDetail = (taskInfo) => {
    // 构造一个特殊的URL，包含任务信息
    const params = new URLSearchParams()
    if (taskInfo.taskId) {
      params.set('taskId', taskInfo.taskId)
    }
    params.set('taskName', taskInfo.taskName)
    params.set('autoOpen', 'true') // 标记需要自动打开详情
    
    // 使用 navigate 进行页面跳转
    navigate(`/tasks?${params.toString()}`)
  }

  const handleRefresh = async () => {
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
      const isWindows = agent.osType && agent.osType.toLowerCase().includes('windows')
      
      const statusScript = isWindows 
        ? `@echo off
echo ===== System Deep Check Report =====
echo Generated at: %date% %time%
echo.
echo ===== Detailed System Information =====
systeminfo
echo.
echo ===== CPU Information =====
wmic cpu get Name,NumberOfCores,NumberOfLogicalProcessors,MaxClockSpeed,LoadPercentage /format:list
echo.
echo ===== Memory Details =====
wmic OS get TotalVisibleMemorySize,FreePhysicalMemory,TotalVirtualMemorySize,FreeVirtualMemory /format:list
wmic computersystem get TotalPhysicalMemory /format:list
echo.
echo ===== Disk Information =====
wmic logicaldisk get DeviceID,Size,FreeSpace,FileSystem,VolumeName /format:list
echo.
echo ===== Network Configuration =====
ipconfig /all
echo.
echo ===== Network Connections =====
netstat -an | findstr LISTENING
echo.
echo ===== Running Processes (Top 15 by Memory) =====
tasklist /FO CSV | sort /R /+5 | head -16
echo.
echo ===== Services Status =====
sc query state= all | findstr "SERVICE_NAME STATE"
echo.
echo ===== Environment Variables =====
set
`
        : `#!/bin/bash
echo "===== System Deep Check Report ====="
echo "Generated at: $(date)"
echo ""
echo "===== Detailed System Information ====="
uname -a
cat /etc/*release 2>/dev/null
echo ""
echo "===== CPU Information ====="
cat /proc/cpuinfo | grep -E "(processor|model name|cpu MHz|cache size)" 2>/dev/null || echo "CPU info not available"
echo ""
echo "===== Memory Details ====="
cat /proc/meminfo 2>/dev/null || free -h
echo ""
echo "===== Disk Information ====="
df -h
lsblk 2>/dev/null || echo "Block device info not available"
echo ""
echo "===== Network Configuration ====="
ip addr show 2>/dev/null || ifconfig
echo ""
echo "===== Network Connections ====="
ss -tuln 2>/dev/null || netstat -tuln 2>/dev/null || echo "Network connections info not available"
echo ""
echo "===== Running Processes (Top 15 by Memory) ====="
ps aux --sort=-%mem | head -16
echo ""
echo "===== System Load ====="
uptime
cat /proc/loadavg 2>/dev/null || echo "Load average not available"
echo ""
echo "===== Disk I/O Statistics ====="
iostat 2>/dev/null || echo "I/O statistics not available"
echo ""
echo "===== Environment Variables ====="
env | sort
`

      const taskSpec = {
        scriptLang: isWindows ? 'cmd' : 'shell',
        scriptContent: statusScript,
        timeoutSec: 120
      }
      
      // 生成带时间戳的任务名称
      const timestamp = new Date().toLocaleString('zh-CN', { 
        year: 'numeric', 
        month: '2-digit', 
        day: '2-digit', 
        hour: '2-digit', 
        minute: '2-digit',
        second: '2-digit',
        hour12: false 
      }).replace(/\//g, '-').replace(/:/g, '').replace(/\s/g, '_')
      
      const taskName = `深度检查_${agent.hostname}_${timestamp}`
      
      const params = new URLSearchParams()
      params.append('agentIds', agent.agentId || agent.id)
      params.append('taskName', taskName)
      params.append('autoStart', true)
      
      const response = await api.post(`/web/tasks/create?${params.toString()}`, taskSpec)
      
      message.success(
        <div>
          <div>深度检查任务已创建：<strong>{taskName}</strong></div>
          <div style={{ marginTop: 4, fontSize: '12px', color: '#666' }}>
            请在任务管理页面查看执行结果
          </div>
        </div>,
        6
      )
      
      // 刷新 Agent 列表以获取最新的深度检查任务信息
      await loadAgents()
      
      // 如果详情弹窗是打开的，需要重新获取当前 agent 的最新数据
      if (detailModalVisible && selectedAgent) {
        try {
          const agentResponse = await api.get('/web/agents')
          const updatedAgent = agentResponse.content.find(a => a.agentId === selectedAgent.agentId)
          if (updatedAgent) {
            const processedAgent = await processAgentData(updatedAgent)
            setSelectedAgent(processedAgent)
          }
        } catch (error) {
          console.error('更新选中 Agent 数据失败:', error)
        }
      }
    } catch (error) {
      console.error('创建深度检查任务失败:', error)
      message.error('创建深度检查任务失败: ' + (error.response?.data?.message || error.message))
    }
  }

  const columns = [
    {
      title: '客户端',
      key: 'info',
      width: 160,
      render: (_, record) => (
        <Space>
          <Avatar 
            icon={<DesktopOutlined />} 
            className={record.status === 'online' ? 'bg-green-500' : 'bg-gray-400'}
          />
          <div>
            <Text strong className="block">{record.hostname}</Text>
          </div>
        </Space>
      ),
    },
    {
      title: 'IP',
      dataIndex: 'ip',
      key: 'ip',
      width: 120,
      render: (text) => <Text code>{text}</Text>,
    },
    {
      title: '系统',
      dataIndex: 'os',
      key: 'os',
      width: 100,
      render: (text) => (
        <Tag color={text.includes('Windows') ? 'blue' : 'green'}>
          {text}
        </Tag>
      ),
    },
    {
      title: '版本',
      dataIndex: 'agentVersion',
      key: 'agentVersion',
      width: 80,
      render: (version) => (
        <Tag color="purple">
          {version || 'Unknown'}
        </Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 80,
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
      title: '任务',
      dataIndex: 'tasks',
      key: 'tasks',
      width: 60,
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
      width: 140,
      render: (text) => (
        <Text type="secondary" className="text-sm">
          {text}
        </Text>
      ),
    },
    {
      title: '运行',
      dataIndex: 'uptime',
      key: 'uptime',
      width: 80,
      render: (text) => <Text className="text-sm">{text}</Text>,
    },
    {
      title: '分组',
      key: 'groups',
      width: 100,
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
      width: 140,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button 
            type="link" 
            icon={<EyeOutlined />} 
            size="small"
            onClick={() => handleViewDetail(record)}
          >
            详情
          </Button>
          <Button 
            type="link" 
            icon={<DeleteOutlined />} 
            size="small"
            danger
            disabled={record.status === 'online'}
            onClick={() => handleDeleteAgent(record)}
          >
            删除
          </Button>
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
          scroll={{ x: 900 }}
        />
      </Card>

      {/* 客户端详情弹窗 */}
      <Modal
        title={`客户端详情 - ${selectedAgent?.hostname || ''}`}
        open={detailModalVisible}
        onCancel={() => setDetailModalVisible(false)}
        footer={[
          <Button 
            key="collect" 
            type="primary"
            icon={<SyncOutlined />}
            onClick={() => {
              if (selectedAgent) {
                handleCollectStatus(selectedAgent)
              }
            }}
            disabled={selectedAgent?.status !== 'online' && selectedAgent?.status !== 'ONLINE'}
          >
            深度检查
          </Button>,
          <Button key="close" onClick={() => setDetailModalVisible(false)}>
            关闭
          </Button>
        ]}
        width={800}
        style={{ top: 20 }}
      >
        {selectedAgent && (
          <div className="space-y-6">
            {/* 基本信息 */}
            <Card title="基本信息" size="small">
              <Descriptions column={2} size="small">
                <Descriptions.Item label="客户端ID">
                  <Text code>{selectedAgent.agentId || selectedAgent.id}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="主机名">
                  <Text strong>{selectedAgent.hostname}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="IP地址">
                  <Text code>{selectedAgent.ip || 'N/A'}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="状态">
                  <Tag 
                    color={selectedAgent.status === 'online' || selectedAgent.status === 'ONLINE' ? 'success' : 'error'}
                    icon={selectedAgent.status === 'online' || selectedAgent.status === 'ONLINE' ? <CheckCircleOutlined /> : <ExclamationCircleOutlined />}
                  >
                    {selectedAgent.status === 'online' || selectedAgent.status === 'ONLINE' ? '在线' : '离线'}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="操作系统">
                  <Tag color={selectedAgent.osType?.includes('WINDOWS') ? 'blue' : 'green'}>
                    {selectedAgent.osType || selectedAgent.os}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="系统版本">
                  <Text>{selectedAgent.osVersion || 'N/A'}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="Java版本">
                  <Text>{selectedAgent.javaVersion || 'N/A'}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="Agent版本">
                  <Text>{selectedAgent.agentVersion || 'N/A'}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="启动用户">
                  <Space>
                    <UserOutlined />
                    <Text>{selectedAgent.startUser || 'N/A'}</Text>
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="工作目录">
                  <Space>
                    <FolderOutlined />
                    <Text code>{selectedAgent.workingDir || 'N/A'}</Text>
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="运行时间">
                  <Text>{selectedAgent.uptime}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="最后心跳">
                  <Text type="secondary">{selectedAgent.lastHeartbeat}</Text>
                </Descriptions.Item>
              </Descriptions>
            </Card>

            {/* 资源使用情况 */}
            <Card title="资源使用情况" size="small">
              <Row gutter={16}>
                <Col span={6}>
                  <Statistic
                    title="CPU使用率"
                    value={selectedAgent.cpu || 0}
                    suffix="%"
                    valueStyle={{ color: (selectedAgent.cpu || 0) > 80 ? '#cf1322' : '#3f8600' }}
                  />
                </Col>
                <Col span={6}>
                  <Statistic
                    title="内存使用率"
                    value={selectedAgent.memoryUsagePercent || selectedAgent.memory || 0}
                    suffix="%"
                    valueStyle={{ color: (selectedAgent.memoryUsagePercent || selectedAgent.memory || 0) > 80 ? '#cf1322' : '#3f8600' }}
                  />
                </Col>
                <Col span={6}>
                  <Statistic
                    title="磁盘使用率"
                    value={selectedAgent.diskUsagePercent || 0}
                    suffix="%"
                    valueStyle={{ color: (selectedAgent.diskUsagePercent || 0) > 80 ? '#cf1322' : '#3f8600' }}
                    prefix={<HddOutlined />}
                  />
                </Col>
                <Col span={6}>
                  <Statistic
                    title="活跃任务"
                    value={selectedAgent.taskCount || selectedAgent.tasks || 0}
                    valueStyle={{ color: '#1890ff' }}
                  />
                </Col>
              </Row>
              
              <Row gutter={16} className="mt-4">
                <Col span={8}>
                  <Descriptions size="small" column={1}>
                    <Descriptions.Item label="总内存">
                      <Text>{selectedAgent.totalMemInfo || 'N/A'}</Text>
                    </Descriptions.Item>
                    <Descriptions.Item label="可用内存">
                      <Text>{selectedAgent.freeMemInfo || 'N/A'}</Text>
                    </Descriptions.Item>
                  </Descriptions>
                </Col>
                <Col span={8}>
                  <Descriptions size="small" column={1}>
                    <Descriptions.Item label="总磁盘空间">
                      <Text>{selectedAgent.diskInfo}</Text>
                    </Descriptions.Item>
                    <Descriptions.Item label="可用磁盘空间">
                      <Text>{selectedAgent.freeSpaceInfo}</Text>
                    </Descriptions.Item>
                  </Descriptions>
                </Col>
                <Col span={8}>
                  {selectedAgent.lastDiagnosticTaskId && selectedAgent.lastDiagnosticTaskName && (
                    <div className="text-center">
                      <Text type="secondary" className="text-xs block mb-2">最近检查</Text>
                      <Button 
                        type="link" 
                        size="small"
                        onClick={() => openTaskDetail({
                          taskId: selectedAgent.lastDiagnosticTaskId,
                          taskName: selectedAgent.lastDiagnosticTaskName
                        })}
                        title={`任务名称: ${selectedAgent.lastDiagnosticTaskName}`}
                      >
                        查看检查结果
                      </Button>
                      <div className="text-xs text-gray-500 mt-1">
                        {selectedAgent.lastDiagnosticTime}
                      </div>
                    </div>
                  )}
                  {!selectedAgent.lastDiagnosticTaskId && (
                    <div className="text-center">
                      <Text type="secondary" className="text-xs">暂无检查记录</Text>
                    </div>
                  )}
                </Col>
              </Row>
            </Card>

            {/* 升级历史 */}
            <Card title="升级历史" size="small">
              <UpgradeHistory agentId={selectedAgent.agentId || selectedAgent.id} />
            </Card>

            {/* 所属分组 */}
            <Card title="所属分组" size="small">
              <Space size="small" wrap>
                {selectedAgent.groups && selectedAgent.groups.length > 0 ? (
                  selectedAgent.groups.map(group => (
                    <Tag color="purple" key={group.id}>
                      {group.name}
                    </Tag>
                  ))
                ) : (
                  <Text type="secondary">该客户端未加入任何分组</Text>
                )}
              </Space>
            </Card>
          </div>
        )}
      </Modal>
    </div>
  )
}

// 升级历史组件
const UpgradeHistory = ({ agentId }) => {
  const [upgradeHistory, setUpgradeHistory] = useState([])
  const [loading, setLoading] = useState(false)
  
  useEffect(() => {
    if (agentId) {
      loadUpgradeHistory()
    }
  }, [agentId])
  
  const loadUpgradeHistory = async () => {
    setLoading(true)
    try {
      const response = await api.get(`/web/upgrade/agents/${agentId}/history`)
      setUpgradeHistory(response || [])
    } catch (error) {
      console.error('加载升级历史失败:', error)
      message.error('加载升级历史失败')
    } finally {
      setLoading(false)
    }
  }
  
  const columns = [
    {
      title: '升级时间',
      dataIndex: 'startTime',
      render: (text) => text ? new Date(text).toLocaleString('zh-CN') : '-',
    },
    {
      title: '版本变更',
      render: (_, record) => (
        <span>{record.fromVersion || 'Unknown'} → {record.toVersion || 'Unknown'}</span>
      ),
    },
    {
      title: '升级状态',
      dataIndex: 'upgradeStatus',
      render: (status) => {
        const statusConfig = {
          SUCCESS: { color: 'success', text: '成功' },
          FAILED: { color: 'error', text: '失败' },
          ROLLBACK: { color: 'warning', text: '回滚' },
          STARTED: { color: 'processing', text: '开始' },
          DOWNLOADING: { color: 'processing', text: '下载中' },
          INSTALLING: { color: 'processing', text: '安装中' },
        }
        const config = statusConfig[status] || { color: 'default', text: status }
        return <Tag color={config.color}>{config.text}</Tag>
      },
    },
    {
      title: '耗时',
      render: (_, record) => {
        if (record.endTime && record.startTime) {
          const duration = Math.floor((new Date(record.endTime) - new Date(record.startTime)) / 1000)
          return `${duration}秒`
        }
        return '-'
      },
    },
    {
      title: '强制升级',
      dataIndex: 'forceUpgrade',
      render: (force) => force ? <Tag color="red">是</Tag> : <Tag>否</Tag>,
    },
  ]
  
  return (
    <Table
      columns={columns}
      dataSource={upgradeHistory}
      rowKey="id"
      loading={loading}
      size="small"
      pagination={{ pageSize: 5 }}
      locale={{ emptyText: '暂无升级记录' }}
    />
  )
}

export default Agents