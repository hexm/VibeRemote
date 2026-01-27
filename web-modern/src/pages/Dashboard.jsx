import React, { useState, useEffect } from 'react'
import { Card, Row, Col, Statistic, Progress, Typography, Space, Button, Table, Tag, Avatar } from 'antd'
import {
  DesktopOutlined,
  FileTextOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  ExclamationCircleOutlined,
  ReloadOutlined,
  TrophyOutlined,
  RocketOutlined,
} from '@ant-design/icons'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, AreaChart, Area } from 'recharts'

const { Title, Text } = Typography

const Dashboard = () => {
  const [loading, setLoading] = useState(false)
  const [stats, setStats] = useState({
    totalAgents: 12,
    onlineAgents: 8,
    totalTasks: 156,
    runningTasks: 3,
    completedTasks: 142,
    failedTasks: 11,
  })

  // 模拟图表数据
  const chartData = [
    { name: '00:00', tasks: 4, success: 3 },
    { name: '04:00', tasks: 8, success: 7 },
    { name: '08:00', tasks: 15, success: 13 },
    { name: '12:00', tasks: 23, success: 21 },
    { name: '16:00', tasks: 18, success: 16 },
    { name: '20:00', tasks: 12, success: 11 },
  ]

  // 最近任务数据
  const recentTasks = [
    {
      key: '1',
      id: 'T001',
      name: '系统更新脚本',
      agent: 'Server-01',
      status: 'success',
      duration: '2m 30s',
      time: '2分钟前',
    },
    {
      key: '2',
      id: 'T002',
      name: '日志清理任务',
      agent: 'Server-02',
      status: 'running',
      duration: '1m 15s',
      time: '5分钟前',
    },
    {
      key: '3',
      id: 'T003',
      name: '数据备份脚本',
      agent: 'Server-03',
      status: 'failed',
      duration: '45s',
      time: '10分钟前',
    },
  ]

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
      title: '执行节点',
      dataIndex: 'agent',
      key: 'agent',
      render: (text) => (
        <Space>
          <Avatar size="small" icon={<DesktopOutlined />} />
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
          success: { color: 'success', text: '成功', icon: <CheckCircleOutlined /> },
          running: { color: 'processing', text: '运行中', icon: <ClockCircleOutlined /> },
          failed: { color: 'error', text: '失败', icon: <ExclamationCircleOutlined /> },
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
      title: '执行时间',
      dataIndex: 'duration',
      key: 'duration',
    },
    {
      title: '时间',
      dataIndex: 'time',
      key: 'time',
      render: (text) => <Text type="secondary">{text}</Text>,
    },
  ]

  const refreshData = () => {
    setLoading(true)
    // 模拟API调用
    setTimeout(() => {
      setLoading(false)
    }, 1000)
  }

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
          onClick={refreshData}
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
              percent={(stats.onlineAgents / stats.totalAgents) * 100}
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
              成功率 {((stats.completedTasks / stats.totalTasks) * 100).toFixed(1)}%
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
              失败率 {((stats.failedTasks / stats.totalTasks) * 100).toFixed(1)}%
            </Text>
          </Card>
        </Col>
      </Row>

      {/* 图表区域 */}
      <Row gutter={[24, 24]}>
        <Col xs={24} lg={16}>
          <Card 
            title={
              <Space>
                <RocketOutlined className="text-blue-500" />
                <span>任务执行趋势</span>
              </Space>
            }
            className="shadow-lg"
          >
            <ResponsiveContainer width="100%" height={300}>
              <AreaChart data={chartData}>
                <defs>
                  <linearGradient id="colorTasks" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.8}/>
                    <stop offset="95%" stopColor="#3b82f6" stopOpacity={0.1}/>
                  </linearGradient>
                  <linearGradient id="colorSuccess" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#10b981" stopOpacity={0.8}/>
                    <stop offset="95%" stopColor="#10b981" stopOpacity={0.1}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                <XAxis dataKey="name" stroke="#666" />
                <YAxis stroke="#666" />
                <Tooltip 
                  contentStyle={{ 
                    backgroundColor: 'white', 
                    border: '1px solid #e5e7eb',
                    borderRadius: '8px',
                    boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)'
                  }} 
                />
                <Area
                  type="monotone"
                  dataKey="tasks"
                  stroke="#3b82f6"
                  fillOpacity={1}
                  fill="url(#colorTasks)"
                  strokeWidth={2}
                />
                <Area
                  type="monotone"
                  dataKey="success"
                  stroke="#10b981"
                  fillOpacity={1}
                  fill="url(#colorSuccess)"
                  strokeWidth={2}
                />
              </AreaChart>
            </ResponsiveContainer>
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
                  <Text>CPU 使用率</Text>
                  <Text strong>45%</Text>
                </div>
                <Progress percent={45} strokeColor="#3b82f6" />
              </div>
              
              <div>
                <div className="flex justify-between items-center mb-2">
                  <Text>内存使用率</Text>
                  <Text strong>68%</Text>
                </div>
                <Progress percent={68} strokeColor="#10b981" />
              </div>
              
              <div>
                <div className="flex justify-between items-center mb-2">
                  <Text>磁盘使用率</Text>
                  <Text strong>32%</Text>
                </div>
                <Progress percent={32} strokeColor="#f59e0b" />
              </div>
              
              <div>
                <div className="flex justify-between items-center mb-2">
                  <Text>网络负载</Text>
                  <Text strong>23%</Text>
                </div>
                <Progress percent={23} strokeColor="#8b5cf6" />
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
        />
      </Card>
    </div>
  )
}

export default Dashboard