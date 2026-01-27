import React, { useState } from 'react'
import { Card, Table, Tag, Button, Space, Typography, Input, Select, Modal, Form, message, Tooltip } from 'antd'
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
} from '@ant-design/icons'

const { Title, Text } = Typography
const { Search, TextArea } = Input
const { Option } = Select

const Tasks = () => {
  const [loading, setLoading] = useState(false)
  const [createModalVisible, setCreateModalVisible] = useState(false)
  const [logModalVisible, setLogModalVisible] = useState(false)
  const [selectedTask, setSelectedTask] = useState(null)
  const [form] = Form.useForm()

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

  const handleCreateTask = async (values) => {
    try {
      // 模拟API调用
      await new Promise(resolve => setTimeout(resolve, 1000))
      
      const newTask = {
        key: Date.now().toString(),
        id: `T${String(tasks.length + 1).padStart(3, '0')}`,
        name: values.name,
        script: values.script,
        agent: values.agent,
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

  const handleViewLog = (task) => {
    setSelectedTask(task)
    setLogModalVisible(true)
  }

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
            创建、管理和监控脚本执行任务
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
            icon={<ReloadOutlined />}
            loading={loading}
            onClick={() => setLoading(true)}
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
      </Card>

      {/* 任务列表 */}
      <Card className="shadow-lg">
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
            name="name"
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
              <Option value="web-server-01">web-server-01</Option>
              <Option value="db-server-01">db-server-01</Option>
              <Option value="app-server-01">app-server-01</Option>
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
    </div>
  )
}

export default Tasks