import React, { useState, useEffect } from 'react'
import { Card, Row, Col, Statistic, Typography, Space, Button, Table, Tag, Avatar, message } from 'antd'
import {
  DesktopOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  ExclamationCircleOutlined,
  ReloadOutlined,
  TrophyOutlined,
  UserOutlined,
  TeamOutlined,
  CodeOutlined,
  LineChartOutlined,
  MonitorOutlined,
  FileTextOutlined,
} from '@ant-design/icons'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts'
import api from '../services/auth'

const { Title, Text } = Typography

const Dashboard = () => {
  const [loading, setLoading] = useState(false)
  const [stats, setStats] = useState({
    totalAgents: 0,
    onlineAgents: 0,
    offlineAgents: 0,
    totalTasks: 0,
    runningTasks: 0,
    completedTasks: 0,
    failedTasks: 0,
    pendingTasks: 0,
    totalUsers: 0,
    activeUsers: 0,
    totalScripts: 0,
  })
  const [recentTasks, setRecentTasks] = useState([])
  const [taskTrends, setTaskTrends] = useState([])
  const [serverHealth, setServerHealth] = useState({
    cpuUsage: 0,
    cpuCores: 0,
    jvmMemoryUsage: 0,
    jvmUsedMemoryMB: 0,
    jvmMaxMemoryMB: 0,
    systemMemoryUsage: 0,
    systemUsedMemoryGB: 0,
    systemTotalMemoryGB: 0,
    diskUsage: 0,
    usedDiskGB: 0,
    totalDiskGB: 0,
    uptimeHours: 0,
    uptimeMinutes: 0,
    threadCount: 0,
    peakThreadCount: 0,
    loadedClasses: 0,
    gcCount: 0,
    gcTime: 0,
    activeConnections: 0,
    totalConnections: 0,
    samplingTime: '',
  })

  // 加载仪表盘数据
  const loadDashboardData = async () => {
    setLoading(true)
    try {
      // 并行加载所有数据
      const [statsResponse, tasksResponse, trendsResponse, healthResponse] = await Promise.all([
        api.get('/web/dashboard/stats'),
        api.get('/web/tasks?page=0&size=5&sort=createdAt,desc'),
        api.get('/web/dashboard/task-trends'),
        api.get('/web/dashboard/server-health')
      ])

      // 设置统计数据
      setStats(statsResponse)

      // 处理最近任务数据
      const taskList = tasksResponse.content.map(task => ({
        key: task.taskId,
        id: task.taskId.substring(0, 8),
        name: task.taskName,
        status: task.taskStatus,
        createdAt: new Date(task.createdAt).toLocaleString('zh-CN'),
        createdBy: task.createdBy,
        targetAgentCount: task.targetAgentCount || 1,
      }))
      setRecentTasks(taskList)

      // 设置任务趋势数据
      const trendsData = trendsResponse.map(item => ({
        date: new Date(item.date).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' }),
        total: item.total,
        success: item.success,
        failed: item.failed,
      }))
      setTaskTrends(trendsData)

      // 设置服务器健康度数据
      setServerHealth(healthResponse)

    } catch (error) {
      console.error('加载仪表盘数据失败:', error)
      message.error('加载数据失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadDashboardData()
    // 每30秒自动刷新数据
    const interval = setInterval(loadDashboardData, 30000)
    return () => clearInterval(interval)
  }, [])

  const taskColumns = [
    {
      title: '任务ID',
      dataIndex: 'id',
      key: 'id',
      render: (text) => <Text code>{text}</Text>,
    },
    {
      title: '任务名称',
      dataIndex: 'name',
      key: 'name',
      render: (text) => <Text strong>{text}</Text>,
    },
    {
      title: '创建者',
      dataIndex: 'createdBy',
      key: 'createdBy',
      render: (text) => (
        <Space>
          <Avatar size="small" icon={<UserOutlined />} />
          {text}
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status) => {
        const statusConfig = {
          SUCCESS: { color: 'success', text: '已完成', icon: <CheckCircleOutlined /> },
          COMPLETED: { color: 'success', text: '已完成', icon: <CheckCircleOutlined /> },
          RUNNING: { color: 'processing', text: '运行中', icon: <ClockCircleOutlined /> },
          FAILED: { color: 'error', text: '失败', icon: <ExclamationCircleOutlined /> },
          PENDING: { color: 'default', text: '待执行', icon: <ClockCircleOutlined /> },
          DRAFT: { color: 'default', text: '草稿', icon: <FileTextOutlined /> },
          CANCELLED: { color: 'default', text: '已取消', icon: <ExclamationCircleOutlined /> },
          PARTIAL_SUCCESS: { color: 'warning', text: '部分成功', icon: <ExclamationCircleOutlined /> },
        }
        const config = statusConfig[status] || statusConfig.DRAFT
        return (
          <Tag color={config.color} icon={config.icon}>
            {config.text}
          </Tag>
        )
      },
    },
    {
      title: '目标节点',
      dataIndex: 'targetAgentCount',
      key: 'targetAgentCount',
      render: (count) => <Text>{count} 个节点</Text>,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (text) => <Text type="secondary">{text}</Text>,
    },
  ]

  return (
    <div className="space-y-6 animate-fade-in">
      {/* 页面标题 */}
      <div className="flex items-center justify-between">
        <div>
          <Title level={2} className="mb-2 flex items-center">
            <TrophyOutlined className="mr-3 text-yellow-500" />
            仪表盘
          </Title>
          <Text type="secondary" className="text-base">
            实时监控系统状态和任务执行情况
          </Text>
        </div>
        <Button
          type="primary"
          icon={<ReloadOutlined />}
          loading={loading}
          onClick={loadDashboardData}
          className="shadow-lg"
        >
          刷新数据
        </Button>
      </div>

      {/* 统计卡片 - 精简为一排六个 */}
      <Row gutter={[24, 24]}>
        <Col xs={24} sm={12} lg={4}>
          <Card className="hover:shadow-lg transition-all duration-300 border-l-4 border-l-blue-500">
            <Statistic
              title={
                <Space>
                  <DesktopOutlined className="text-blue-500" />
                  <span>在线节点</span>
                </Space>
              }
              value={stats.onlineAgents}
              suffix={`/ ${stats.totalAgents}`}
              valueStyle={{ color: '#3b82f6', fontSize: '1.5rem', fontWeight: 'bold' }}
            />
          </Card>
        </Col>
        
        <Col xs={24} sm={12} lg={4}>
          <Card className="hover:shadow-lg transition-all duration-300 border-l-4 border-l-green-500">
            <Statistic
              title={
                <Space>
                  <CheckCircleOutlined className="text-green-500" />
                  <span>成功任务</span>
                </Space>
              }
              value={stats.completedTasks}
              valueStyle={{ color: '#10b981', fontSize: '1.5rem', fontWeight: 'bold' }}
            />
          </Card>
        </Col>
        
        <Col xs={24} sm={12} lg={4}>
          <Card className="hover:shadow-lg transition-all duration-300 border-l-4 border-l-red-500">
            <Statistic
              title={
                <Space>
                  <ExclamationCircleOutlined className="text-red-500" />
                  <span>失败任务</span>
                </Space>
              }
              value={stats.failedTasks}
              valueStyle={{ color: '#ef4444', fontSize: '1.5rem', fontWeight: 'bold' }}
            />
          </Card>
        </Col>
        
        <Col xs={24} sm={12} lg={4}>
          <Card className="hover:shadow-lg transition-all duration-300 border-l-4 border-l-orange-500">
            <Statistic
              title={
                <Space>
                  <ClockCircleOutlined className="text-orange-500" />
                  <span>运行中</span>
                </Space>
              }
              value={stats.runningTasks}
              valueStyle={{ color: '#f59e0b', fontSize: '1.5rem', fontWeight: 'bold' }}
            />
          </Card>
        </Col>
        
        <Col xs={24} sm={12} lg={4}>
          <Card className="hover:shadow-lg transition-all duration-300 border-l-4 border-l-purple-500">
            <Statistic
              title={
                <Space>
                  <TeamOutlined className="text-purple-500" />
                  <span>用户数</span>
                </Space>
              }
              value={stats.totalUsers}
              valueStyle={{ color: '#8b5cf6', fontSize: '1.5rem', fontWeight: 'bold' }}
            />
          </Card>
        </Col>
        
        <Col xs={24} sm={12} lg={4}>
          <Card className="hover:shadow-lg transition-all duration-300 border-l-4 border-l-indigo-500">
            <Statistic
              title={
                <Space>
                  <CodeOutlined className="text-indigo-500" />
                  <span>脚本数量</span>
                </Space>
              }
              value={stats.totalScripts}
              valueStyle={{ color: '#6366f1', fontSize: '1.5rem', fontWeight: 'bold' }}
            />
          </Card>
        </Col>
      </Row>

      {/* 图表区域 */}
      <Row gutter={[24, 24]}>
        <Col xs={24} lg={16}>
          <Card 
            title={
              <Space>
                <LineChartOutlined className="text-blue-500" />
                <span>任务执行趋势</span>
              </Space>
            }
            className="shadow-lg"
          >
            <ResponsiveContainer width="100%" height={300}>
              <LineChart data={taskTrends}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="date" />
                <YAxis />
                <Tooltip />
                <Legend />
                <Line 
                  type="monotone" 
                  dataKey="total" 
                  stroke="#3b82f6" 
                  strokeWidth={2}
                  name="总任务数"
                />
                <Line 
                  type="monotone" 
                  dataKey="success" 
                  stroke="#10b981" 
                  strokeWidth={2}
                  name="成功任务"
                />
                <Line 
                  type="monotone" 
                  dataKey="failed" 
                  stroke="#ef4444" 
                  strokeWidth={2}
                  name="失败任务"
                />
              </LineChart>
            </ResponsiveContainer>
          </Card>
        </Col>
        
        <Col xs={24} lg={8}>
          <Card 
            title={
              <div className="flex justify-between items-center">
                <Space>
                  <MonitorOutlined className="text-green-500" />
                  <span>服务器健康度</span>
                </Space>
                <Text type="secondary" className="text-xs">
                  {serverHealth.samplingTime ? new Date(serverHealth.samplingTime).toLocaleTimeString('zh-CN') : ''}
                </Text>
              </div>
            }
            className="shadow-lg"
            style={{ height: '100%' }}
          >
            <div className="h-full flex flex-col justify-center">
              <div className="space-y-3">
                {/* 第一行：CPU、JVM内存、系统内存 */}
                <div className="grid grid-cols-3 gap-3">
                  <div className="text-center p-2 bg-blue-50 rounded">
                    <div className="text-lg font-bold text-blue-600">{serverHealth.cpuUsage}%</div>
                    <div className="text-xs text-gray-600">CPU使用率</div>
                    <div className="text-xs text-gray-400">{serverHealth.cpuCores}核心</div>
                  </div>
                  <div className="text-center p-2 bg-green-50 rounded">
                    <div className="text-lg font-bold text-green-600">{serverHealth.jvmMemoryUsage}%</div>
                    <div className="text-xs text-gray-600">JVM内存</div>
                    <div className="text-xs text-gray-400">{serverHealth.jvmUsedMemoryMB}MB/{serverHealth.jvmMaxMemoryMB}MB</div>
                  </div>
                  <div className="text-center p-2 bg-emerald-50 rounded">
                    <div className="text-lg font-bold text-emerald-600">{serverHealth.systemMemoryUsage}%</div>
                    <div className="text-xs text-gray-600">系统内存</div>
                    <div className="text-xs text-gray-400">{serverHealth.systemUsedMemoryGB}GB/{serverHealth.systemTotalMemoryGB}GB</div>
                  </div>
                </div>
                
                {/* 第二行：磁盘、运行时间、线程 */}
                <div className="grid grid-cols-3 gap-3">
                  <div className="text-center p-2 bg-orange-50 rounded">
                    <div className="text-lg font-bold text-orange-600">{serverHealth.diskUsage}%</div>
                    <div className="text-xs text-gray-600">磁盘使用率</div>
                    <div className="text-xs text-gray-400">{serverHealth.usedDiskGB}GB/{serverHealth.totalDiskGB}GB</div>
                  </div>
                  <div className="text-center p-2 bg-purple-50 rounded">
                    <div className="text-lg font-bold text-purple-600">
                      {serverHealth.uptimeHours}h{serverHealth.uptimeMinutes}m
                    </div>
                    <div className="text-xs text-gray-600">运行时间</div>
                    <div className="text-xs text-gray-400">JVM启动</div>
                  </div>
                  <div className="text-center p-2 bg-indigo-50 rounded">
                    <div className="text-lg font-bold text-indigo-600">{serverHealth.threadCount}</div>
                    <div className="text-xs text-gray-600">活跃线程</div>
                    <div className="text-xs text-gray-400">峰值{serverHealth.peakThreadCount}</div>
                  </div>
                </div>
                
                {/* 第三行：类加载、GC统计、数据库连接 */}
                <div className="grid grid-cols-3 gap-3">
                  <div className="text-center p-2 bg-cyan-50 rounded">
                    <div className="text-lg font-bold text-cyan-600">{serverHealth.loadedClasses}</div>
                    <div className="text-xs text-gray-600">已载类数</div>
                    <div className="text-xs text-gray-400">JVM类</div>
                  </div>
                  <div className="text-center p-2 bg-yellow-50 rounded">
                    <div className="text-lg font-bold text-yellow-600">{serverHealth.gcCount}</div>
                    <div className="text-xs text-gray-600">GC次数</div>
                    <div className="text-xs text-gray-400">{serverHealth.gcTime}ms耗时</div>
                  </div>
                  <div className="text-center p-2 bg-pink-50 rounded">
                    <div className="text-lg font-bold text-pink-600">
                      {serverHealth.activeConnections}/{serverHealth.totalConnections}
                    </div>
                    <div className="text-xs text-gray-600">数据库连接</div>
                    <div className="text-xs text-gray-400">活跃/总数</div>
                  </div>
                </div>
              </div>
            </div>
          </Card>
        </Col>
      </Row>

      {/* 最近任务 */}
      <Card 
        title={
          <Space>
            <FileTextOutlined className="text-green-500" />
            <span>最近任务</span>
          </Space>
        }
        className="shadow-lg"
      >
        <Table
          columns={taskColumns}
          dataSource={recentTasks}
          pagination={false}
          size="middle"
          className="rounded-lg overflow-hidden"
          loading={loading}
        />
      </Card>
    </div>
  )
}

export default Dashboard