import React, { useState, useEffect } from 'react'
import { Card, Row, Col, Statistic, Progress, Typography, Space, Button, Table, Tag, Avatar, message } from 'antd'
import {
  DesktopOutlined,
  FileTextOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  ExclamationCircleOutlined,
  ReloadOutlined,
  TrophyOutlined,
  RocketOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, AreaChart, Area } from 'recharts'
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
  })
  const [recentTasks, setRecentTasks] = useState([])
  const [systemHealth, setSystemHealth] = useState({
    avgCpu: 0,
    avgMemory: 0,
    avgDisk: 0,
    networkLoad: 0,
  })

  // 加载仪表盘数据
  const loadDashboardData = async () => {
    setLoading(true)
    try {
      // 并行加载所有数据
      const [statsResponse, tasksResponse, agentsResponse] = await Promise.all([
        api.get('/web/dashboard/stats'),
        api.get('/web/tasks?page=0&size=5&sort=createdAt,desc'),
        api.get('/web/agents')
      ])

      // 设置统计数据
      setStats(statsResponse)

      // 处理最近任务数据
      const taskList = tasksResponse.content.map(task => ({
        key: task.taskId,
        id: task.taskId.substring(0, 8),
        name: task.taskName,
        status: task.taskStatus.toLowerCase(),
        createdAt: new Date(task.createdAt).toLocaleString('zh-CN'),
        createdBy: task.createdBy,
        targetAgentCount: task.targetAgentCount || 1,
      }))
      setRecentTasks(taskList)

      // 计算系统健康度
      const agents = agentsResponse.content
      if (agents.length > 0) {
        const onlineAgents = agents.filter(agent => agent.status === 'ONLINE')
        if (onlineAgents.length > 0) {
          const avgCpu = onlineAgents.reduce((sum, agent) => sum + (agent.cpuLoad ? agent.cpuLoad * 100 : 0), 0) / onlineAgents.length
          const avgMemory = onlineAgents.reduce((sum, agent) => {
            if (agent.totalMemMb && agent.freeMemMb) {
              return sum + ((agent.totalMemMb - agent.freeMemMb) / agent.totalMemMb * 100)
            }
            return sum
          }, 0) / onlineAgents.length
          const avgDisk = onlineAgents.reduce((sum, agent) => {
            if (agent.diskSpaceGb && agent.freeSpaceGb) {
              return sum + ((agent.diskSpaceGb - agent.freeSpaceGb) / agent.diskSpaceGb * 100)
            }
            return sum
          }, 0) / onlineAgents.length

          setSystemHealth({
            avgCpu: Math.round(avgCpu),
            avgMemory: Math.round(avgMemory),
            avgDisk: Math.round(avgDisk),
            networkLoad: Math.round(Math.random() * 30 + 10), // 模拟网络负载
          })
        }
      }

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
          completed: { color: 'success', text: '已完成', icon: <CheckCircleOutlined /> },
          running: { color: 'processing', text: '运行中', icon: <ClockCircleOutlined /> },
          failed: { color: 'error', text: '失败', icon: <ExclamationCircleOutlined /> },
          pending: { color: 'default', text: '待执行', icon: <ClockCircleOutlined /> },
          draft: { color: 'default', text: '草稿', icon: <FileTextOutlined /> },
        }
        const config = statusConfig[status] || statusConfig.draft
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

      {/* 统计卡片 */}
      <Row gutter={[24, 24]}>
        <Col xs={24} sm={12} lg={6}>
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
              valueStyle={{ color: '#3b82f6', fontSize: '2rem', fontWeight: 'bold' }}
            />
            <Progress
              percent={stats.totalAgents > 0 ? (stats.onlineAgents / stats.totalAgents) * 100 : 0}
              showInfo={false}
              strokeColor="#3b82f6"
              className="mt-2"
            />
          </Card>
        </Col>
        
        <Col xs={24} sm={12} lg={6}>
          <Card className="hover:shadow-lg transition-all duration-300 border-l-4 border-l-green-500">
            <Statistic
              title={
                <Space>
                  <CheckCircleOutlined className="text-green-500" />
                  <span>成功任务</span>
                </Space>
              }
              value={stats.completedTasks}
              valueStyle={{ color: '#10b981', fontSize: '2rem', fontWeight: 'bold' }}
            />
            <Text type="secondary" className="text-sm">
              成功率 {stats.totalTasks > 0 ? ((stats.completedTasks / stats.totalTasks) * 100).toFixed(1) : 0}%
            </Text>
          </Card>
        </Col>
        
        <Col xs={24} sm={12} lg={6}>
          <Card className="hover:shadow-lg transition-all duration-300 border-l-4 border-l-orange-500">
            <Statistic
              title={
                <Space>
                  <ClockCircleOutlined className="text-orange-500" />
                  <span>运行中</span>
                </Space>
              }
              value={stats.runningTasks}
              valueStyle={{ color: '#f59e0b', fontSize: '2rem', fontWeight: 'bold' }}
            />
            <Text type="secondary" className="text-sm">
              正在执行的任务
            </Text>
          </Card>
        </Col>
        
        <Col xs={24} sm={12} lg={6}>
          <Card className="hover:shadow-lg transition-all duration-300 border-l-4 border-l-red-500">
            <Statistic
              title={
                <Space>
                  <ExclamationCircleOutlined className="text-red-500" />
                  <span>失败任务</span>
                </Space>
              }
              value={stats.failedTasks}
              valueStyle={{ color: '#ef4444', fontSize: '2rem', fontWeight: 'bold' }}
            />
            <Text type="secondary" className="text-sm">
              失败率 {stats.totalTasks > 0 ? ((stats.failedTasks / stats.totalTasks) * 100).toFixed(1) : 0}%
            </Text>
          </Card>
        </Col>
      </Row>

      {/* 图表和系统健康度 */}
      <Row gutter={[24, 24]}>
        <Col xs={24} lg={16}>
          <Card 
            title={
              <Space>
                <RocketOutlined className="text-blue-500" />
                <span>任务统计概览</span>
              </Space>
            }
            className="shadow-lg"
          >
            <Row gutter={[16, 16]}>
              <Col span={6}>
                <Statistic
                  title="总任务数"
                  value={stats.totalTasks}
                  valueStyle={{ color: '#1890ff' }}
                />
              </Col>
              <Col span={6}>
                <Statistic
                  title="待执行"
                  value={stats.pendingTasks}
                  valueStyle={{ color: '#faad14' }}
                />
              </Col>
              <Col span={6}>
                <Statistic
                  title="已完成"
                  value={stats.completedTasks}
                  valueStyle={{ color: '#52c41a' }}
                />
              </Col>
              <Col span={6}>
                <Statistic
                  title="失败"
                  value={stats.failedTasks}
                  valueStyle={{ color: '#ff4d4f' }}
                />
              </Col>
            </Row>
            <div className="mt-6">
              <Progress
                percent={stats.totalTasks > 0 ? (stats.completedTasks / stats.totalTasks) * 100 : 0}
                success={{ percent: stats.totalTasks > 0 ? (stats.completedTasks / stats.totalTasks) * 100 : 0 }}
                strokeColor="#52c41a"
                className="mb-2"
              />
              <Text type="secondary">任务完成进度</Text>
            </div>
          </Card>
        </Col>
        
        <Col xs={24} lg={8}>
          <Card 
            title="系统健康度"
            className="shadow-lg h-full"
          >
            <div className="space-y-6">
              <div>
                <div className="flex justify-between items-center mb-2">
                  <Text>平均 CPU 使用率</Text>
                  <Text strong>{systemHealth.avgCpu}%</Text>
                </div>
                <Progress 
                  percent={systemHealth.avgCpu} 
                  strokeColor={systemHealth.avgCpu > 80 ? '#ff4d4f' : systemHealth.avgCpu > 60 ? '#faad14' : '#52c41a'} 
                />
              </div>
              
              <div>
                <div className="flex justify-between items-center mb-2">
                  <Text>平均内存使用率</Text>
                  <Text strong>{systemHealth.avgMemory}%</Text>
                </div>
                <Progress 
                  percent={systemHealth.avgMemory} 
                  strokeColor={systemHealth.avgMemory > 80 ? '#ff4d4f' : systemHealth.avgMemory > 60 ? '#faad14' : '#52c41a'} 
                />
              </div>
              
              <div>
                <div className="flex justify-between items-center mb-2">
                  <Text>平均磁盘使用率</Text>
                  <Text strong>{systemHealth.avgDisk}%</Text>
                </div>
                <Progress 
                  percent={systemHealth.avgDisk} 
                  strokeColor={systemHealth.avgDisk > 80 ? '#ff4d4f' : systemHealth.avgDisk > 60 ? '#faad14' : '#52c41a'} 
                />
              </div>
              
              <div>
                <div className="flex justify-between items-center mb-2">
                  <Text>网络负载</Text>
                  <Text strong>{systemHealth.networkLoad}%</Text>
                </div>
                <Progress 
                  percent={systemHealth.networkLoad} 
                  strokeColor="#8b5cf6" 
                />
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