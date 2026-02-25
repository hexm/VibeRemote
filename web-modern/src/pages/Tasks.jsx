import React, { useState, useEffect } from 'react'
import { Card, Table, Tag, Button, Space, Typography, Input, Select, Modal, Form, message, Tooltip, Tabs, Progress, Statistic, Row, Col } from 'antd'
import {
  FileTextOutlined,
  SearchOutlined,
  ReloadOutlined,
  PlusOutlined,
  PlayCircleOutlined,
  StopOutlined,
  DeleteOutlined,
  EyeOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  ClockCircleOutlined,
  SyncOutlined,
  AppstoreOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import api from '../services/auth'

const { Title, Text } = Typography
const { Search, TextArea } = Input
const { Option } = Select
const { TabPane } = Tabs

const Tasks = () => {
  // 基础状态
  const [loading, setLoading] = useState(false)
  const [activeTab, setActiveTab] = useState('normal')
  
  // 普通任务状态
  const [createModalVisible, setCreateModalVisible] = useState(false)
  const [logModalVisible, setLogModalVisible] = useState(false)
  const [selectedTask, setSelectedTask] = useState(null)
  const [form] = Form.useForm()
  
  // 批量任务状态
  const [batchTasks, setBatchTasks] = useState([])
  const [batchLoading, setBatchLoading] = useState(false)
  const [batchModalVisible, setBatchModalVisible] = useState(false)
  const [batchDetailModalVisible, setBatchDetailModalVisible] = useState(false)
  const [selectedBatchTask, setSelectedBatchTask] = useState(null)
  const [batchTaskTasks, setBatchTaskTasks] = useState([])
  const [batchForm] = Form.useForm()
  
  // 模拟在线代理数据
  const [onlineAgents] = useState([
    { agentId: 'agent-001', hostname: 'web-server-01', status: 'online' },
    { agentId: 'agent-002', hostname: 'db-server-01', status: 'online' },
    { agentId: 'agent-003', hostname: 'app-server-01', status: 'online' },
    { agentId: 'agent-004', hostname: 'cache-server-01', status: 'online' },
  ])

  // 模拟任务数据
  const [tasks, setTasks] = useState([
    {
      key: '1',
      id: 'T001',
      name: '系统更新脚本',
      script: 'update-system.sh',
      agent: 'web-server-01',
      status: 'completed',
      progress: 100,
      startTime: '2024-01-15 10:25:30',
      endTime: '2024-01-15 10:28:00',
      duration: '2m 30s',
      exitCode: 0,
    },
    {
      key: '2',
      id: 'T002',
      name: '日志清理任务',
      script: 'cleanup-logs.sh',
      agent: 'db-server-01',
      status: 'running',
      progress: 65,
      startTime: '2024-01-15 10:25:15',
      endTime: null,
      duration: '4m 45s',
      exitCode: null,
    },
    {
      key: '3',
      id: 'T003',
      name: '数据备份脚本',
      script: 'backup-data.sh',
      agent: 'app-server-01',
      status: 'failed',
      progress: 45,
      startTime: '2024-01-15 10:20:10',
      endTime: '2024-01-15 10:20:55',
      duration: '45s',
      exitCode: 1,
    },
    {
      key: '4',
      id: 'T004',
      name: '服务重启脚本',
      script: 'restart-services.sh',
      agent: 'web-server-01',
      status: 'pending',
      progress: 0,
      startTime: null,
      endTime: null,
      duration: null,
      exitCode: null,
    },
  ])

  // 模拟批量任务数据
  useEffect(() => {
    if (activeTab === 'batch' && batchTasks.length === 0) {
      setBatchTasks([
        {
          batchId: 'B001',
          batchName: '系统维护批量任务',
          scriptLang: 'shell',
          targetAgentCount: 4,
          successTasks: 3,
          failedTasks: 1,
          runningTasks: 0,
          pendingTasks: 0,
          status: 'PARTIAL_FAILED',
          createdAt: '2024-01-15 09:30:00',
          finishedAt: '2024-01-15 09:45:00',
          progress: 100
        },
        {
          batchId: 'B002', 
          batchName: '日志清理批量任务',
          scriptLang: 'shell',
          targetAgentCount: 3,
          successTasks: 2,
          failedTasks: 0,
          runningTasks: 1,
          pendingTasks: 0,
          status: 'RUNNING',
          createdAt: '2024-01-15 10:00:00',
          finishedAt: null,
          progress: 67
        }
      ])
    }
  }, [activeTab, batchTasks.length])

  // 批量任务状态辅助函数
  const getBatchStatusText = (status) => {
    const map = {
      'PENDING': '等待中',
      'RUNNING': '运行中', 
      'COMPLETED': '已完成',
      'PARTIAL_FAILED': '部分失败',
      'FAILED': '失败'
    }
    return map[status] || status
  }

  const getBatchStatusType = (status) => {
    const map = {
      'PENDING': 'default',
      'RUNNING': 'processing',
      'COMPLETED': 'success', 
      'PARTIAL_FAILED': 'warning',
      'FAILED': 'error'
    }
    return map[status] || 'default'
  }

  // Tab切换处理
  const handleTabChange = (key) => {
    setActiveTab(key)
  }

  // 刷新数据
  const handleRefresh = () => {
    setLoading(true)
    
    if (activeTab === 'batch') {
      // 刷新批量任务
      setBatchLoading(true)
      // 模拟API调用
      setTimeout(() => {
        setBatchLoading(false)
        message.success('批量任务列表已刷新')
        setLoading(false)
      }, 800)
    } else {
      // 刷新普通任务
      // 模拟API调用
      setTimeout(() => {
        message.success('任务列表已刷新')
        setLoading(false)
      }, 800)
    }
  }

  // 创建批量任务
  const handleCreateBatchTask = async (values) => {
    try {
      message.success('批量任务创建成功')
      setBatchModalVisible(false)
      batchForm.resetFields()
      setActiveTab('batch')
    } catch (error) {
      message.error('创建批量任务失败')
    }
  }

  // 创建普通任务
  const handleCreateTask = async (values) => {
    try {
      // 模拟API调用
      await new Promise(resolve => setTimeout(resolve, 1000))
      
      const newTask = {
        key: Date.now().toString(),
        id: `T${String(tasks.length + 1).padStart(3, '0')}`,
        name: values.taskName,
        script: values.script,
        agent: onlineAgents.find(a => a.agentId === values.agent)?.hostname || values.agent,
        status: 'pending',
        progress: 0,
        startTime: null,
        endTime: null,
        duration: null,
        exitCode: null,
      }
      
      setTasks([newTask, ...tasks])
      setCreateModalVisible(false)
      form.resetFields()
      message.success('任务创建成功')
    } catch (error) {
      message.error('任务创建失败')
    }
  }

  // 查看任务日志
  const handleViewLog = (task) => {
    setSelectedTask(task)
    setLogModalVisible(true)
  }

  // 停止任务
  const handleStopTask = (task) => {
    Modal.confirm({
      title: '确认停止',
      content: `确定要停止任务 ${task.name} 吗？`,
      okText: '确定',
      cancelText: '取消',
      onOk() {
        setTasks(tasks.map(t => 
          t.key === task.key 
            ? { ...t, status: 'stopped', endTime: new Date().toLocaleString() }
            : t
        ))
        message.success('任务已停止')
      },
    })
  }

  // 删除任务
  const handleDeleteTask = (task) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除任务 ${task.name} 吗？`,
      okText: '确定',
      cancelText: '取消',
      onOk() {
        setTasks(tasks.filter(t => t.key !== task.key))
        message.success('任务已删除')
      },
    })
  }

  // 查看批量任务详情
  const viewBatchTaskDetail = (batchTask) => {
    setSelectedBatchTask(batchTask)
    setBatchDetailModalVisible(true)
  }

  // 取消批量任务
  const cancelBatchTask = (batchTask) => {
    Modal.confirm({
      title: '确认取消',
      content: `确定要取消批量任务"${batchTask.batchName}"吗？这将取消所有未完成的子任务。`,
      okText: '确定',
      cancelText: '取消',
      onOk() {
        // 更新批量任务状态
        setBatchTasks(batchTasks.map(task => 
          task.batchId === batchTask.batchId 
            ? { ...task, status: 'CANCELLED', runningTasks: 0 }
            : task
        ))
        message.success('批量任务已取消')
      },
    })
  }

  // 普通任务表格列定义
  const columns = [
    {
      title: '任务信息',
      key: 'info',
      render: (_, record) => (
        <div>
          <Text strong className="block">{record.name}</Text>
          <Text code className="text-sm">{record.id}</Text>
        </div>
      ),
    },
    {
      title: '脚本',
      dataIndex: 'script',
      key: 'script',
      render: (text) => <Text code>{text}</Text>,
    },
    {
      title: '执行节点',
      dataIndex: 'agent',
      key: 'agent',
      render: (text) => (
        <Tag color="blue" icon={<FileTextOutlined />}>
          {text}
        </Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status, record) => {
        const statusConfig = {
          completed: { 
            color: 'success', 
            text: '已完成', 
            icon: <CheckCircleOutlined /> 
          },
          running: { 
            color: 'processing', 
            text: '运行中', 
            icon: <SyncOutlined spin /> 
          },
          failed: { 
            color: 'error', 
            text: '失败', 
            icon: <ExclamationCircleOutlined /> 
          },
          pending: { 
            color: 'default', 
            text: '等待中', 
            icon: <ClockCircleOutlined /> 
          },
          stopped: { 
            color: 'warning', 
            text: '已停止', 
            icon: <StopOutlined /> 
          },
        }
        const config = statusConfig[status]
        return (
          <div className="space-y-1">
            <Tag color={config.color} icon={config.icon}>
              {config.text}
            </Tag>
            {status === 'running' && (
              <div className="w-full bg-gray-200 rounded-full h-1">
                <div 
                  className="bg-blue-500 h-1 rounded-full transition-all duration-300"
                  style={{ width: `${record.progress}%` }}
                />
              </div>
            )}
          </div>
        )
      },
    },
    {
      title: '执行时间',
      key: 'time',
      render: (_, record) => (
        <div className="text-sm">
          {record.startTime && (
            <div>开始: {record.startTime.split(' ')[1]}</div>
          )}
          {record.endTime && (
            <div>结束: {record.endTime.split(' ')[1]}</div>
          )}
          {record.duration && (
            <Text type="secondary">耗时: {record.duration}</Text>
          )}
        </div>
      ),
    },
    {
      title: '退出码',
      dataIndex: 'exitCode',
      key: 'exitCode',
      render: (code) => {
        if (code === null) return <Text type="secondary">-</Text>
        return (
          <Tag color={code === 0 ? 'success' : 'error'}>
            {code}
          </Tag>
        )
      },
    },
    {
      title: '操作',
      key: 'actions',
      render: (_, record) => (
        <Space>
          <Tooltip title="查看日志">
            <Button 
              type="text" 
              icon={<EyeOutlined />} 
              size="small"
              onClick={() => handleViewLog(record)}
              className="text-blue-500 hover:bg-blue-50"
            />
          </Tooltip>
          {record.status === 'running' && (
            <Tooltip title="停止任务">
              <Button 
                type="text" 
                icon={<StopOutlined />} 
                size="small"
                onClick={() => handleStopTask(record)}
                className="text-orange-500 hover:bg-orange-50"
              />
            </Tooltip>
          )}
          {record.status === 'pending' && (
            <Tooltip title="立即执行">
              <Button 
                type="text" 
                icon={<PlayCircleOutlined />} 
                size="small"
                className="text-green-500 hover:bg-green-50"
              />
            </Tooltip>
          )}
          <Tooltip title="删除">
            <Button 
              type="text" 
              icon={<DeleteOutlined />} 
              size="small"
              danger
              onClick={() => handleDeleteTask(record)}
            />
          </Tooltip>
        </Space>
      ),
    },
  ]

  // 批量任务表格列定义
  const batchColumns = [
    {
      title: '批量任务信息',
      key: 'info',
      render: (_, record) => (
        <div>
          <Text strong className="block">{record.batchName}</Text>
          <Text code className="text-sm">{record.batchId}</Text>
        </div>
      ),
    },
    {
      title: '目标节点',
      dataIndex: 'targetAgentCount',
      key: 'targetAgentCount',
      render: (count) => (
        <Tag color="blue" icon={<TeamOutlined />}>
          {count} 个节点
        </Tag>
      ),
    },
    {
      title: '执行统计',
      key: 'stats',
      render: (_, record) => (
        <div className="space-y-1">
          <div className="flex space-x-2 text-sm">
            <Tag color="success">成功: {record.successTasks}</Tag>
            <Tag color="error">失败: {record.failedTasks}</Tag>
            {record.runningTasks > 0 && <Tag color="processing">运行: {record.runningTasks}</Tag>}
          </div>
          <Progress 
            percent={record.progress} 
            size="small" 
            status={record.status === 'FAILED' ? 'exception' : 'normal'}
          />
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status) => (
        <Tag color={getBatchStatusType(status)}>
          {getBatchStatusText(status)}
        </Tag>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      render: (_, record) => (
        <Space>
          <Tooltip title="查看详情">
            <Button 
              type="text" 
              icon={<EyeOutlined />} 
              size="small"
              onClick={() => viewBatchTaskDetail(record)}
              className="text-blue-500 hover:bg-blue-50"
            />
          </Tooltip>
          {record.status === 'RUNNING' && (
            <Tooltip title="取消任务">
              <Button 
                type="text" 
                icon={<StopOutlined />} 
                size="small"
                onClick={() => cancelBatchTask(record)}
                className="text-orange-500 hover:bg-orange-50"
              />
            </Tooltip>
          )}
          <Tooltip title="刷新统计">
            <Button 
              type="text" 
              icon={<ReloadOutlined />} 
              size="small"
              onClick={() => {
                message.success(`${record.batchName} 统计信息已刷新`)
                // 这里可以调用API刷新单个批量任务的统计信息
              }}
              className="text-green-500 hover:bg-green-50"
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
            <FileTextOutlined className="mr-3 text-green-500" />
            任务管理
          </Title>
          <Text type="secondary" className="text-base">
            创建、管理和监控脚本执行任务，支持批量任务功能
          </Text>
        </div>
        <Space>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setCreateModalVisible(true)}
            className="shadow-lg"
          >
            创建任务
          </Button>
          <Button
            type="default"
            icon={<AppstoreOutlined />}
            onClick={() => setBatchModalVisible(true)}
            className="shadow-lg"
          >
            批量任务
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

      {/* Tab切换 */}
      <Card className="shadow-lg">
        <Tabs activeKey={activeTab} onChange={handleTabChange}>
          <TabPane 
            tab={
              <span>
                <FileTextOutlined />
                普通任务
              </span>
            } 
            key="normal"
          >
            {/* 普通任务工具栏 */}
            <div className="flex flex-col sm:flex-row gap-4 items-start sm:items-center justify-between mb-4">
              <Space wrap>
                <Search
                  placeholder="搜索任务名称或ID"
                  allowClear
                  style={{ width: 250 }}
                  prefix={<SearchOutlined className="text-gray-400" />}
                />
                <Select
                  defaultValue="all"
                  style={{ width: 120 }}
                >
                  <Option value="all">全部状态</Option>
                  <Option value="running">运行中</Option>
                  <Option value="completed">已完成</Option>
                  <Option value="failed">失败</Option>
                  <Option value="pending">等待中</Option>
                </Select>
              </Space>
              
              <div className="flex items-center space-x-4 text-sm">
                <Space>
                  <div className="w-3 h-3 bg-green-500 rounded-full"></div>
                  <Text>成功: {tasks.filter(t => t.status === 'completed').length}</Text>
                </Space>
                <Space>
                  <div className="w-3 h-3 bg-blue-500 rounded-full"></div>
                  <Text>运行中: {tasks.filter(t => t.status === 'running').length}</Text>
                </Space>
                <Space>
                  <div className="w-3 h-3 bg-red-500 rounded-full"></div>
                  <Text>失败: {tasks.filter(t => t.status === 'failed').length}</Text>
                </Space>
              </div>
            </div>

            <Table
              columns={columns}
              dataSource={tasks}
              loading={loading}
              pagination={{
                total: tasks.length,
                pageSize: 10,
                showSizeChanger: true,
                showQuickJumper: true,
                showTotal: (total, range) => 
                  `第 ${range[0]}-${range[1]} 条，共 ${total} 条记录`,
              }}
              className="rounded-lg overflow-hidden"
              scroll={{ x: 1200 }}
            />
          </TabPane>

          <TabPane 
            tab={
              <span>
                <AppstoreOutlined />
                批量任务
              </span>
            } 
            key="batch"
          >
            {/* 批量任务工具栏 */}
            <div className="flex flex-col sm:flex-row gap-4 items-start sm:items-center justify-between mb-4">
              <Space wrap>
                <Search
                  placeholder="搜索批量任务名称"
                  allowClear
                  style={{ width: 250 }}
                  prefix={<SearchOutlined className="text-gray-400" />}
                />
                <Select
                  defaultValue="all"
                  style={{ width: 120 }}
                >
                  <Option value="all">全部状态</Option>
                  <Option value="RUNNING">运行中</Option>
                  <Option value="COMPLETED">已完成</Option>
                  <Option value="FAILED">失败</Option>
                  <Option value="PARTIAL_FAILED">部分失败</Option>
                </Select>
              </Space>
              
              <div className="flex items-center space-x-4 text-sm">
                <Space>
                  <div className="w-3 h-3 bg-green-500 rounded-full"></div>
                  <Text>已完成: {batchTasks.filter(t => t.status === 'COMPLETED').length}</Text>
                </Space>
                <Space>
                  <div className="w-3 h-3 bg-blue-500 rounded-full"></div>
                  <Text>运行中: {batchTasks.filter(t => t.status === 'RUNNING').length}</Text>
                </Space>
                <Space>
                  <div className="w-3 h-3 bg-orange-500 rounded-full"></div>
                  <Text>部分失败: {batchTasks.filter(t => t.status === 'PARTIAL_FAILED').length}</Text>
                </Space>
              </div>
            </div>

            <Table
              columns={batchColumns}
              dataSource={batchTasks}
              loading={batchLoading}
              pagination={{
                total: batchTasks.length,
                pageSize: 10,
                showSizeChanger: true,
                showQuickJumper: true,
                showTotal: (total, range) => 
                  `第 ${range[0]}-${range[1]} 条，共 ${total} 条记录`,
              }}
              className="rounded-lg overflow-hidden"
              scroll={{ x: 1200 }}
            />
          </TabPane>
        </Tabs>
      </Card>

      {/* 创建任务模态框 */}
      <Modal
        title="创建新任务"
        open={createModalVisible}
        onCancel={() => setCreateModalVisible(false)}
        footer={null}
        width={600}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreateTask}
          className="mt-4"
        >
          <Form.Item
            name="taskName"
            label="任务名称"
            rules={[{ required: true, message: '请输入任务名称' }]}
          >
            <Input placeholder="输入任务名称" />
          </Form.Item>
          
          <Form.Item
            name="script"
            label="脚本文件"
            rules={[{ required: true, message: '请选择脚本文件' }]}
          >
            <Select placeholder="选择脚本文件">
              <Option value="update-system.sh">update-system.sh</Option>
              <Option value="cleanup-logs.sh">cleanup-logs.sh</Option>
              <Option value="backup-data.sh">backup-data.sh</Option>
              <Option value="restart-services.sh">restart-services.sh</Option>
            </Select>
          </Form.Item>
          
          <Form.Item
            name="agent"
            label="执行节点"
            rules={[{ required: true, message: '请选择执行节点' }]}
          >
            <Select placeholder="选择执行节点">
              {onlineAgents.map(agent => (
                <Option key={agent.agentId} value={agent.agentId}>
                  {agent.hostname}
                </Option>
              ))}
            </Select>
          </Form.Item>
          
          <Form.Item
            name="description"
            label="任务描述"
          >
            <TextArea rows={3} placeholder="输入任务描述（可选）" />
          </Form.Item>
          
          <Form.Item className="mb-0 text-right">
            <Space>
              <Button onClick={() => setCreateModalVisible(false)}>
                取消
              </Button>
              <Button type="primary" htmlType="submit">
                创建任务
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* 日志查看模态框 */}
      <Modal
        title={`任务日志 - ${selectedTask?.name}`}
        open={logModalVisible}
        onCancel={() => setLogModalVisible(false)}
        footer={[
          <Button key="close" onClick={() => setLogModalVisible(false)}>
            关闭
          </Button>
        ]}
        width={800}
      >
        <div className="bg-black text-green-400 p-4 rounded-lg font-mono text-sm max-h-96 overflow-y-auto">
          <div>[2024-01-15 10:25:30] 任务开始执行...</div>
          <div>[2024-01-15 10:25:31] 正在检查系统环境...</div>
          <div>[2024-01-15 10:25:32] 环境检查完成</div>
          <div>[2024-01-15 10:25:33] 开始执行脚本...</div>
          <div>[2024-01-15 10:26:15] 脚本执行中... (50%)</div>
          <div>[2024-01-15 10:27:30] 脚本执行中... (80%)</div>
          <div>[2024-01-15 10:28:00] 脚本执行完成</div>
          <div>[2024-01-15 10:28:00] 任务执行成功，退出码: 0</div>
        </div>
      </Modal>
      <Modal
        title="创建批量任务"
        open={batchModalVisible}
        onCancel={() => setBatchModalVisible(false)}
        footer={null}
        width={700}
      >
        <Form
          form={batchForm}
          layout="vertical"
          onFinish={handleCreateBatchTask}
          className="mt-4"
        >
          <Form.Item
            name="batchName"
            label="批量任务名称"
            rules={[{ required: true, message: '请输入批量任务名称' }]}
          >
            <Input placeholder="输入批量任务名称" />
          </Form.Item>
          
          <Form.Item
            name="selectedAgents"
            label="目标节点"
            rules={[{ required: true, message: '请选择至少一个执行节点' }]}
          >
            <Select
              mode="multiple"
              placeholder="选择执行节点（可多选）"
              style={{ width: '100%' }}
            >
              {onlineAgents.map(agent => (
                <Option key={agent.agentId} value={agent.agentId}>
                  <Space>
                    <Tag color="green" size="small">在线</Tag>
                    {agent.hostname}
                  </Space>
                </Option>
              ))}
            </Select>
          </Form.Item>
          
          <Form.Item
            name="scriptContent"
            label="脚本内容"
            rules={[{ required: true, message: '请输入脚本内容' }]}
          >
            <TextArea 
              rows={8} 
              placeholder="输入要执行的脚本内容..."
              style={{ fontFamily: 'monospace' }}
            />
          </Form.Item>
          
          <Form.Item className="mb-0 text-right">
            <Space>
              <Button onClick={() => setBatchModalVisible(false)}>
                取消
              </Button>
              <Button type="primary" htmlType="submit">
                创建批量任务
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* 批量任务详情模态框 */}
      <Modal
        title={`批量任务详情 - ${selectedBatchTask?.batchName}`}
        open={batchDetailModalVisible}
        onCancel={() => setBatchDetailModalVisible(false)}
        footer={[
          <Button key="close" onClick={() => setBatchDetailModalVisible(false)}>
            关闭
          </Button>
        ]}
        width={1000}
      >
        {selectedBatchTask && (
          <div className="space-y-4">
            <Row gutter={16}>
              <Col span={6}>
                <Statistic
                  title="目标节点"
                  value={selectedBatchTask.targetAgentCount}
                  prefix={<TeamOutlined />}
                />
              </Col>
              <Col span={6}>
                <Statistic
                  title="成功任务"
                  value={selectedBatchTask.successTasks}
                  valueStyle={{ color: '#3f8600' }}
                  prefix={<CheckCircleOutlined />}
                />
              </Col>
              <Col span={6}>
                <Statistic
                  title="失败任务"
                  value={selectedBatchTask.failedTasks}
                  valueStyle={{ color: '#cf1322' }}
                  prefix={<ExclamationCircleOutlined />}
                />
              </Col>
              <Col span={6}>
                <Statistic
                  title="运行中"
                  value={selectedBatchTask.runningTasks}
                  valueStyle={{ color: '#1890ff' }}
                  prefix={<SyncOutlined />}
                />
              </Col>
            </Row>

            <div>
              <Text strong>整体进度</Text>
              <Progress 
                percent={selectedBatchTask.progress} 
                status={selectedBatchTask.status === 'FAILED' ? 'exception' : 'normal'}
                strokeColor={{
                  '0%': '#108ee9',
                  '100%': '#87d068',
                }}
              />
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}

export default Tasks