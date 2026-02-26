import React, { useState, useEffect, useRef } from 'react'
import { Card, Table, Tag, Button, Space, Typography, Input, Select, Modal, Form, message, Tooltip, Progress, Statistic, Row, Col, Switch, Badge, Radio } from 'antd'
import {
  FileTextOutlined,
  SearchOutlined,
  ReloadOutlined,
  PlusOutlined,
  StopOutlined,
  EyeOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  ClockCircleOutlined,
  SyncOutlined,
  TeamOutlined,
  DownloadOutlined,
  HistoryOutlined,
  RedoOutlined,
  ClearOutlined,
} from '@ant-design/icons'
import api from '../services/auth'

const { Title, Text } = Typography
const { Search, TextArea } = Input
const { Option } = Select

const Tasks = () => {
  // 基础状态
  const [loading, setLoading] = useState(false)
  
  // 任务状态
  const [tasks, setTasks] = useState([])
  const [totalTasks, setTotalTasks] = useState(0)
  const [currentPage, setCurrentPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [createModalVisible, setCreateModalVisible] = useState(false)
  const [detailModalVisible, setDetailModalVisible] = useState(false)
  const [logModalVisible, setLogModalVisible] = useState(false)
  const [selectedTask, setSelectedTask] = useState(null)
  const [form] = Form.useForm()
  
  // 任务详情相关状态
  const [taskExecutions, setTaskExecutions] = useState([])
  const [detailLoading, setDetailLoading] = useState(false)
  
  // 日志相关状态
  const [selectedExecution, setSelectedExecution] = useState(null)
  const [logContent, setLogContent] = useState('')
  const [logTotalLines, setLogTotalLines] = useState(0)
  const [autoRefreshLogs, setAutoRefreshLogs] = useState(false)
  const logIntervalRef = useRef(null)
  
  // 在线代理数据
  const [onlineAgents, setOnlineAgents] = useState([])

  // 获取在线代理列表
  const fetchOnlineAgents = async () => {
    try {
      console.log('开始获取代理列表...')
      const response = await api.get('/web/agents')
      console.log('代理列表API响应:', response)
      const agents = response.content || response || []
      console.log('解析后的代理列表:', agents)
      setOnlineAgents(agents)
      if (agents.length === 0) {
        console.warn('警告：代理列表为空')
      }
    } catch (error) {
      console.error('获取代理列表失败:', error)
      console.error('错误详情:', error.response)
      message.error('获取代理列表失败: ' + (error.response?.data?.message || error.message))
    }
  }

  // 获取任务列表（含聚合状态）
  const fetchTasks = async () => {
    setLoading(true)
    try {
      const response = await api.get('/web/tasks', {
        params: {
          page: currentPage - 1,
          size: pageSize
        }
      })
      
      if (response?.content) {
        setTasks(response.content)
        setTotalTasks(response.totalElements || 0)
      } else if (Array.isArray(response)) {
        setTasks(response)
        setTotalTasks(response.length)
      }
    } catch (error) {
      console.error('获取任务列表失败:', error)
      message.error('获取任务列表失败')
    } finally {
      setLoading(false)
    }
  }

  // 初始化
  useEffect(() => {
    fetchTasks()
    fetchOnlineAgents()
  }, [currentPage, pageSize])

  // 自动刷新日志
  useEffect(() => {
    if (autoRefreshLogs && logModalVisible && selectedExecution) {
      logIntervalRef.current = setInterval(() => {
        refreshLogs()
      }, 3000)
    } else {
      if (logIntervalRef.current) {
        clearInterval(logIntervalRef.current)
        logIntervalRef.current = null
      }
    }
    
    return () => {
      if (logIntervalRef.current) {
        clearInterval(logIntervalRef.current)
      }
    }
  }, [autoRefreshLogs, logModalVisible, selectedExecution])

  // 辅助函数
  const getStatusColor = (status) => {
    const map = {
      'PENDING': 'default',
      'IN_PROGRESS': 'processing',
      'ALL_SUCCESS': 'success',
      'PARTIAL_SUCCESS': 'warning',
      'ALL_FAILED': 'error',
    }
    return map[status] || 'default'
  }

  const getStatusText = (status) => {
    const map = {
      'PENDING': '等待中',
      'IN_PROGRESS': '进行中',
      'ALL_SUCCESS': '全部成功',
      'PARTIAL_SUCCESS': '部分成功',
      'ALL_FAILED': '全部失败',
    }
    return map[status] || status
  }

  const getExecutionStatusColor = (status) => {
    const map = {
      'PENDING': 'default',
      'PULLED': 'processing',
      'RUNNING': 'processing',
      'SUCCESS': 'success',
      'FAILED': 'error',
      'TIMEOUT': 'warning',
      'CANCELLED': 'default',
    }
    return map[status] || 'default'
  }

  const getExecutionStatusText = (status) => {
    const map = {
      'PENDING': '等待中',
      'PULLED': '已拉取',
      'RUNNING': '运行中',
      'SUCCESS': '成功',
      'FAILED': '失败',
      'TIMEOUT': '超时',
      'CANCELLED': '已取消',
    }
    return map[status] || status
  }

  const getAgentName = (agentId) => {
    const agent = onlineAgents.find(a => a.agentId === agentId)
    return agent?.hostname || agentId?.substring(0, 8) + '...'
  }

  const formatDateTime = (dateStr) => {
    if (!dateStr) return '-'
    return new Date(dateStr).toLocaleString('zh-CN')
  }

  const formatDuration = (ms) => {
    if (!ms) return '-'
    const seconds = Math.floor(ms / 1000)
    const minutes = Math.floor(seconds / 60)
    const hours = Math.floor(minutes / 60)
    
    if (hours > 0) {
      return `${hours}h ${minutes % 60}m ${seconds % 60}s`
    } else if (minutes > 0) {
      return `${minutes}m ${seconds % 60}s`
    } else {
      return `${seconds}s`
    }
  }

  // 创建多代理任务
  const handleCreateTask = async (values) => {
    try {
      const taskSpec = {
        scriptLang: values.scriptLang || 'shell',
        scriptContent: values.scriptContent,
        timeoutSec: values.timeoutSec || 300
      }
      
      const params = new URLSearchParams()
      values.selectedAgents.forEach(id => params.append('agentIds', id))
      params.append('taskName', values.taskName)
      
      await api.post(`/web/tasks/create?${params.toString()}`, taskSpec)
      message.success('任务创建成功')
      setCreateModalVisible(false)
      form.resetFields()
      fetchTasks()
    } catch (error) {
      console.error('创建任务失败:', error)
      message.error('创建任务失败: ' + (error.response?.data?.message || error.message))
    }
  }

  // 查看任务详情
  const handleViewDetail = async (task) => {
    setSelectedTask(task)
    setDetailModalVisible(true)
    setDetailLoading(true)
    
    try {
      const response = await api.get(`/web/tasks/${task.taskId}/executions`)
      setTaskExecutions(response || [])
    } catch (error) {
      console.error('获取执行实例失败:', error)
      message.error('获取执行实例失败')
    } finally {
      setDetailLoading(false)
    }
  }

  // 查看执行实例日志
  const handleViewLog = async (execution) => {
    setSelectedExecution(execution)
    setLogModalVisible(true)
    await refreshLogs(execution)
  }

  // 刷新日志
  const refreshLogs = async (execution = selectedExecution) => {
    if (!execution) return
    
    try {
      const response = await api.get(`/web/tasks/executions/${execution.id}/logs`, {
        params: {
          offset: 0,
          limit: 5000
        }
      })
      setLogContent(response.content || '')
      setLogTotalLines(response.totalLines || 0)
    } catch (error) {
      console.error('获取日志失败:', error)
      message.error('获取日志失败')
    }
  }

  // 清空日志显示
  const clearLogs = () => {
    setLogContent('')
    setLogTotalLines(0)
  }

  // 下载执行日志
  const downloadExecutionLog = (execution) => {
    const url = `${api.defaults.baseURL}/web/tasks/executions/${execution.id}/download`
    window.open(url, '_blank')
  }

  // 取消任务（取消所有执行实例）
  const handleCancelTask = async (task) => {
    Modal.confirm({
      title: '确认取消',
      content: `确定要取消任务"${task.taskName || task.taskId}"吗？这将取消所有未完成的执行实例。`,
      okText: '确定',
      cancelText: '取消',
      async onOk() {
        try {
          await api.post(`/web/tasks/${task.taskId}/cancel`)
          message.success('任务已取消')
          fetchTasks()
          if (detailModalVisible && selectedTask?.taskId === task.taskId) {
            handleViewDetail(task)
          }
        } catch (error) {
          console.error('取消任务失败:', error)
          message.error('取消任务失败')
        }
      }
    })
  }

  // 取消单个执行实例
  const handleCancelExecution = async (execution) => {
    Modal.confirm({
      title: '确认取消',
      content: `确定要取消该执行实例吗？`,
      okText: '确定',
      cancelText: '取消',
      async onOk() {
        try {
          await api.post(`/web/tasks/executions/${execution.id}/cancel`)
          message.success('执行实例已取消')
          if (selectedTask) {
            handleViewDetail(selectedTask)
          }
        } catch (error) {
          console.error('取消执行实例失败:', error)
          message.error('取消失败: ' + (error.response?.data?.message || error.message))
        }
      }
    })
  }

  // 重启任务
  const handleRestartTask = async (task) => {
    let restartMode = 'ALL'
    
    Modal.confirm({
      title: '选择重启模式',
      content: (
        <div className="space-y-4">
          <p>请选择重启模式：</p>
          <Radio.Group 
            defaultValue="ALL" 
            onChange={(e) => { restartMode = e.target.value }}
          >
            <Space direction="vertical">
              <Radio value="ALL">重启所有执行实例</Radio>
              <Radio value="FAILED_ONLY">仅重启失败的执行实例</Radio>
            </Space>
          </Radio.Group>
        </div>
      ),
      okText: '确定重启',
      cancelText: '取消',
      async onOk() {
        try {
          const response = await api.post(`/web/tasks/${task.taskId}/restart`, null, {
            params: { mode: restartMode }
          })
          message.success(`任务已重启，创建了${response.newExecutionCount}个新执行实例`)
          fetchTasks()
          if (detailModalVisible && selectedTask?.taskId === task.taskId) {
            handleViewDetail(task)
          }
        } catch (error) {
          console.error('重启任务失败:', error)
          message.error('重启任务失败: ' + (error.response?.data?.message || error.message))
        }
      }
    })
  }

  // 任务表格列定义
  const columns = [
    {
      title: '任务信息',
      key: 'info',
      width: 200,
      render: (_, record) => (
        <div>
          <Text strong className="block">{record.taskName || '未命名任务'}</Text>
          <Text code className="text-xs text-gray-500">{record.taskId}</Text>
        </div>
      ),
    },
    {
      title: '目标节点',
      dataIndex: 'targetAgentCount',
      key: 'targetAgentCount',
      width: 120,
      render: (count) => (
        <Tag color="blue" icon={<TeamOutlined />}>
          {count || 0} 个节点
        </Tag>
      ),
    },
    {
      title: '执行进度',
      key: 'progress',
      width: 150,
      render: (_, record) => (
        <div>
          <Text className="text-sm">
            {record.completedExecutions || 0}/{record.targetAgentCount || 0} 已完成
          </Text>
          <Progress 
            percent={record.targetAgentCount > 0 
              ? Math.round((record.completedExecutions || 0) / record.targetAgentCount * 100) 
              : 0
            } 
            size="small"
            showInfo={false}
          />
        </div>
      ),
    },
    {
      title: '聚合状态',
      dataIndex: 'aggregatedStatus',
      key: 'aggregatedStatus',
      width: 120,
      render: (status) => {
        const statusIcons = {
          'PENDING': <ClockCircleOutlined />,
          'IN_PROGRESS': <SyncOutlined spin />,
          'ALL_SUCCESS': <CheckCircleOutlined />,
          'PARTIAL_SUCCESS': <ExclamationCircleOutlined />,
          'ALL_FAILED': <ExclamationCircleOutlined />,
        }
        return (
          <Tag color={getStatusColor(status)} icon={statusIcons[status]}>
            {getStatusText(status)}
          </Tag>
        )
      },
    },
    {
      title: '执行统计',
      key: 'stats',
      width: 200,
      render: (_, record) => (
        <Space size="small" wrap>
          {record.successCount > 0 && <Tag color="success">成功: {record.successCount}</Tag>}
          {record.failedCount > 0 && <Tag color="error">失败: {record.failedCount}</Tag>}
          {record.runningCount > 0 && <Tag color="processing">运行: {record.runningCount}</Tag>}
          {record.pendingCount > 0 && <Tag color="default">等待: {record.pendingCount}</Tag>}
        </Space>
      ),
    },
    {
      title: '脚本类型',
      dataIndex: 'scriptLang',
      key: 'scriptLang',
      width: 100,
      render: (lang) => <Tag color="purple">{lang || 'shell'}</Tag>,
    },
    {
      title: '创建者',
      dataIndex: 'createdBy',
      key: 'createdBy',
      width: 100,
      render: (user) => <Text>{user || 'admin'}</Text>,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (time) => <Text className="text-xs">{formatDateTime(time)}</Text>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 180,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Tooltip title="查看详情">
            <Button 
              type="text" 
              icon={<EyeOutlined />} 
              size="small"
              onClick={() => handleViewDetail(record)}
              className="text-blue-500 hover:bg-blue-50"
            />
          </Tooltip>
          
          {(record.aggregatedStatus === 'PARTIAL_SUCCESS' || record.aggregatedStatus === 'ALL_FAILED') && (
            <Tooltip title="重启任务">
              <Button 
                type="text" 
                icon={<RedoOutlined />} 
                size="small"
                onClick={() => handleRestartTask(record)}
                className="text-orange-500 hover:bg-orange-50"
              />
            </Tooltip>
          )}
          
          {record.aggregatedStatus === 'IN_PROGRESS' && (
            <Tooltip title="取消任务">
              <Button 
                type="text" 
                icon={<StopOutlined />} 
                size="small"
                danger
                onClick={() => handleCancelTask(record)}
              />
            </Tooltip>
          )}
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
            创建、管理和监控多代理脚本执行任务
          </Text>
        </div>
        <Space>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              fetchOnlineAgents()
              setCreateModalVisible(true)
            }}
            className="shadow-lg"
          >
            创建任务
          </Button>
          <Button
            icon={<ReloadOutlined />}
            loading={loading}
            onClick={fetchTasks}
          >
            刷新
          </Button>
        </Space>
      </div>

      {/* 任务列表 */}
      <Card className="shadow-lg">
        {/* 工具栏 */}
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
              style={{ width: 150 }}
            >
              <Option value="all">全部状态</Option>
              <Option value="IN_PROGRESS">进行中</Option>
              <Option value="ALL_SUCCESS">全部成功</Option>
              <Option value="PARTIAL_SUCCESS">部分成功</Option>
              <Option value="ALL_FAILED">全部失败</Option>
              <Option value="PENDING">等待中</Option>
            </Select>
          </Space>
          
          <div className="flex items-center space-x-4 text-sm">
            <Space>
              <div className="w-3 h-3 bg-green-500 rounded-full"></div>
              <Text>全部成功: {tasks.filter(t => t.aggregatedStatus === 'ALL_SUCCESS').length}</Text>
            </Space>
            <Space>
              <div className="w-3 h-3 bg-blue-500 rounded-full"></div>
              <Text>进行中: {tasks.filter(t => t.aggregatedStatus === 'IN_PROGRESS').length}</Text>
            </Space>
            <Space>
              <div className="w-3 h-3 bg-orange-500 rounded-full"></div>
              <Text>部分成功: {tasks.filter(t => t.aggregatedStatus === 'PARTIAL_SUCCESS').length}</Text>
            </Space>
          </div>
        </div>

        <Table
          columns={columns}
          dataSource={tasks}
          loading={loading}
          rowKey="taskId"
          pagination={{
            current: currentPage,
            pageSize: pageSize,
            total: totalTasks,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total, range) => 
              `第 ${range[0]}-${range[1]} 条，共 ${total} 条记录`,
            onChange: (page, size) => {
              setCurrentPage(page)
              setPageSize(size)
            },
            onShowSizeChange: (current, size) => {
              setCurrentPage(1)
              setPageSize(size)
            }
          }}
          className="rounded-lg overflow-hidden"
          scroll={{ x: 1600 }}
        />
      </Card>

      {/* 创建任务模态框 */}
      <Modal
        title="创建多代理任务"
        open={createModalVisible}
        onCancel={() => setCreateModalVisible(false)}
        footer={null}
        width={700}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreateTask}
          className="mt-4"
          initialValues={{
            timeoutSec: 300,
            scriptLang: 'shell'
          }}
        >
          <Form.Item
            name="taskName"
            label="任务名称"
            rules={[{ required: true, message: '请输入任务名称' }]}
          >
            <Input placeholder="输入任务名称（必填）" maxLength={100} showCount />
          </Form.Item>
          
          <Form.Item
            name="selectedAgents"
            label={
              <span>
                目标节点（可多选）
                {onlineAgents.length > 0 && (
                  <Text type="secondary" className="ml-2 text-xs">
                    (共{onlineAgents.length}个节点)
                  </Text>
                )}
              </span>
            }
            rules={[{ required: true, message: '请选择至少一个执行节点' }]}
          >
            <Select
              mode="multiple"
              placeholder={onlineAgents.length === 0 ? '正在加载节点列表...' : '选择执行节点（可多选）'}
              notFoundContent={onlineAgents.length === 0 ? '暂无可用节点' : '未找到匹配节点'}
              style={{ width: '100%' }}
            >
              {onlineAgents.map(agent => (
                <Option key={agent.agentId} value={agent.agentId}>
                  <Space>
                    <Tag color={agent.status === 'ONLINE' ? 'green' : 'gray'} size="small">
                      {agent.status === 'ONLINE' ? '在线' : '离线'}
                    </Tag>
                    {agent.hostname}
                  </Space>
                </Option>
              ))}
            </Select>
          </Form.Item>
          
          <Form.Item
            name="scriptLang"
            label="脚本类型"
            rules={[{ required: true, message: '请选择脚本类型' }]}
          >
            <Select placeholder="选择脚本类型">
              <Option value="shell">Shell</Option>
              <Option value="python">Python</Option>
              <Option value="javascript">JavaScript</Option>
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
          
          <Form.Item
            name="timeoutSec"
            label="超时时间（秒）"
          >
            <Input type="number" min={1} />
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

      {/* 任务详情模态框 */}
      <Modal
        title={`任务详情 - ${selectedTask?.taskName || selectedTask?.taskId}`}
        open={detailModalVisible}
        onCancel={() => setDetailModalVisible(false)}
        footer={[
          <Button key="close" onClick={() => setDetailModalVisible(false)}>
            关闭
          </Button>
        ]}
        width={1200}
      >
        {selectedTask && (
          <div className="space-y-4">
            {/* 统计卡片 */}
            <Card>
              <Row gutter={16}>
                <Col span={6}>
                  <Statistic
                    title="目标节点"
                    value={selectedTask.targetAgentCount || 0}
                    prefix={<TeamOutlined />}
                  />
                </Col>
                <Col span={6}>
                  <Statistic
                    title="成功执行"
                    value={selectedTask.successCount || 0}
                    valueStyle={{ color: '#3f8600' }}
                    prefix={<CheckCircleOutlined />}
                  />
                </Col>
                <Col span={6}>
                  <Statistic
                    title="失败执行"
                    value={selectedTask.failedCount || 0}
                    valueStyle={{ color: '#cf1322' }}
                    prefix={<ExclamationCircleOutlined />}
                  />
                </Col>
                <Col span={6}>
                  <Statistic
                    title="运行中"
                    value={selectedTask.runningCount || 0}
                    valueStyle={{ color: '#1890ff' }}
                    prefix={<SyncOutlined />}
                  />
                </Col>
              </Row>

              <div className="mt-4">
                <Text strong>整体进度</Text>
                <Progress 
                  percent={selectedTask.targetAgentCount > 0 
                    ? Math.round((selectedTask.completedExecutions || 0) / selectedTask.targetAgentCount * 100) 
                    : 0
                  } 
                  status={selectedTask.aggregatedStatus === 'ALL_FAILED' ? 'exception' : 'normal'}
                  strokeColor={{
                    '0%': '#108ee9',
                    '100%': '#87d068',
                  }}
                />
              </div>

              <div className="mt-4 space-y-2">
                <div><Text strong>脚本类型：</Text><Tag color="purple">{selectedTask.scriptLang}</Tag></div>
                <div><Text strong>创建者：</Text>{selectedTask.createdBy}</div>
                <div><Text strong>创建时间：</Text>{formatDateTime(selectedTask.createdAt)}</div>
                <div><Text strong>聚合状态：</Text>
                  <Tag color={getStatusColor(selectedTask.aggregatedStatus)}>
                    {getStatusText(selectedTask.aggregatedStatus)}
                  </Tag>
                </div>
              </div>
            </Card>

            {/* 执行实例列表 */}
            <Card title="执行实例列表">
              <Table
                dataSource={taskExecutions}
                loading={detailLoading}
                rowKey="id"
                pagination={false}
                scroll={{ y: 400 }}
                size="small"
                columns={[
                  {
                    title: '执行ID',
                    dataIndex: 'id',
                    key: 'id',
                    width: 80,
                    render: (id) => <Text code>{id}</Text>
                  },
                  {
                    title: 'Agent',
                    dataIndex: 'agentId',
                    key: 'agentId',
                    width: 150,
                    render: (agentId) => (
                      <Tag color="blue" size="small">
                        {getAgentName(agentId)}
                      </Tag>
                    )
                  },
                  {
                    title: '执行次数',
                    dataIndex: 'executionNumber',
                    key: 'executionNumber',
                    width: 100,
                    render: (num) => <Tag color="cyan">第{num}次</Tag>
                  },
                  {
                    title: '状态',
                    dataIndex: 'status',
                    key: 'status',
                    width: 100,
                    render: (status) => (
                      <Tag color={getExecutionStatusColor(status)} size="small">
                        {getExecutionStatusText(status)}
                      </Tag>
                    )
                  },
                  {
                    title: '开始时间',
                    dataIndex: 'startedAt',
                    key: 'startedAt',
                    width: 160,
                    render: (time) => <Text className="text-xs">{formatDateTime(time)}</Text>
                  },
                  {
                    title: '完成时间',
                    dataIndex: 'finishedAt',
                    key: 'finishedAt',
                    width: 160,
                    render: (time) => <Text className="text-xs">{formatDateTime(time)}</Text>
                  },
                  {
                    title: '耗时',
                    key: 'duration',
                    width: 100,
                    render: (_, record) => {
                      if (record.startedAt && record.finishedAt) {
                        const duration = new Date(record.finishedAt) - new Date(record.startedAt)
                        return formatDuration(duration)
                      }
                      return '-'
                    }
                  },
                  {
                    title: '退出码',
                    dataIndex: 'exitCode',
                    key: 'exitCode',
                    width: 80,
                    render: (code) => {
                      if (code === null || code === undefined) return <Text type="secondary">-</Text>
                      return <Tag color={code === 0 ? 'success' : 'error'} size="small">{code}</Tag>
                    }
                  },
                  {
                    title: '操作',
                    key: 'actions',
                    width: 150,
                    render: (_, record) => (
                      <Space size="small">
                        {record.logFilePath && (
                          <Tooltip title="查看日志">
                            <Button 
                              type="text" 
                              size="small"
                              icon={<EyeOutlined />}
                              onClick={() => handleViewLog(record)}
                            />
                          </Tooltip>
                        )}
                        {record.logFilePath && (
                          <Tooltip title="下载日志">
                            <Button 
                              type="text" 
                              size="small"
                              icon={<DownloadOutlined />}
                              onClick={() => downloadExecutionLog(record)}
                            />
                          </Tooltip>
                        )}
                        {(record.status === 'PENDING' || record.status === 'RUNNING' || record.status === 'PULLED') && (
                          <Tooltip title="取消执行">
                            <Button 
                              type="text" 
                              size="small"
                              icon={<StopOutlined />}
                              danger
                              onClick={() => handleCancelExecution(record)}
                            />
                          </Tooltip>
                        )}
                      </Space>
                    )
                  }
                ]}
              />
            </Card>
          </div>
        )}
      </Modal>

      {/* 执行日志模态框 */}
      <Modal
        title={
          <div className="flex items-center justify-between">
            <span>执行日志 - 第{selectedExecution?.executionNumber}次 - {getAgentName(selectedExecution?.agentId)}</span>
            <Space>
              <Switch 
                checked={autoRefreshLogs}
                onChange={setAutoRefreshLogs}
                checkedChildren="自动刷新"
                unCheckedChildren="手动刷新"
              />
              <Text type="secondary" className="text-sm">
                共 {logTotalLines} 行
              </Text>
            </Space>
          </div>
        }
        open={logModalVisible}
        onCancel={() => {
          setLogModalVisible(false)
          setAutoRefreshLogs(false)
        }}
        footer={[
          <Button key="clear" icon={<ClearOutlined />} onClick={clearLogs}>
            清空显示
          </Button>,
          <Button key="refresh" icon={<ReloadOutlined />} onClick={() => refreshLogs()}>
            刷新日志
          </Button>,
          <Button 
            key="download" 
            type="primary" 
            icon={<DownloadOutlined />} 
            onClick={() => downloadExecutionLog(selectedExecution)}
          >
            下载日志
          </Button>,
          <Button key="close" onClick={() => {
            setLogModalVisible(false)
            setAutoRefreshLogs(false)
          }}>
            关闭
          </Button>
        ]}
        width={900}
      >
        <div className="bg-black text-green-400 p-4 rounded-lg font-mono text-sm max-h-96 overflow-y-auto whitespace-pre-wrap">
          {logContent || '暂无日志内容'}
        </div>
      </Modal>
    </div>
  )
}

export default Tasks
