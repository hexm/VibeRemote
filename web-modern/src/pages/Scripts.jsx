import React, { useState } from 'react'
import { Card, Table, Tag, Button, Space, Typography, Input, Select, Modal, Form, message, Tooltip } from 'antd'
import {
  CodeOutlined,
  SearchOutlined,
  ReloadOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  EyeOutlined,
  PlayCircleOutlined,
  FileTextOutlined,
} from '@ant-design/icons'

const { Title, Text } = Typography
const { Search, TextArea } = Input
const { Option } = Select

const Scripts = () => {
  const [loading, setLoading] = useState(false)
  const [createModalVisible, setCreateModalVisible] = useState(false)
  const [viewModalVisible, setViewModalVisible] = useState(false)
  const [selectedScript, setSelectedScript] = useState(null)
  const [form] = Form.useForm()

  const [scripts, setScripts] = useState([
    {
      key: '1',
      id: 'S001',
      name: '系统更新脚本',
      filename: 'update-system.sh',
      type: 'bash',
      description: '更新系统软件包和安全补丁',
      size: '2.3 KB',
      lastModified: '2024-01-15 09:30:00',
      author: 'admin',
      usage: 15,
      content: `#!/bin/bash
echo "开始系统更新..."
apt update
apt upgrade -y
echo "系统更新完成"`,
    },
    {
      key: '2',
      id: 'S002',
      name: '日志清理脚本',
      filename: 'cleanup-logs.sh',
      type: 'bash',
      description: '清理系统日志文件，释放磁盘空间',
      size: '1.8 KB',
      lastModified: '2024-01-14 16:45:00',
      author: 'admin',
      usage: 8,
      content: `#!/bin/bash
echo "开始清理日志..."
find /var/log -name "*.log" -mtime +30 -delete
echo "日志清理完成"`,
    },
    {
      key: '3',
      id: 'S003',
      name: '数据备份脚本',
      filename: 'backup-data.ps1',
      type: 'powershell',
      description: '备份重要数据到指定目录',
      size: '3.1 KB',
      lastModified: '2024-01-13 14:20:00',
      author: 'user',
      usage: 3,
      content: `# PowerShell 数据备份脚本
Write-Host "开始数据备份..."
$source = "C:\\Data"
$destination = "C:\\Backup"
Copy-Item -Path $source -Destination $destination -Recurse
Write-Host "数据备份完成"`,
    },
  ])

  const handleCreateScript = async (values) => {
    try {
      await new Promise(resolve => setTimeout(resolve, 1000))
      
      const newScript = {
        key: Date.now().toString(),
        id: `S${String(scripts.length + 1).padStart(3, '0')}`,
        name: values.name,
        filename: values.filename,
        type: values.type,
        description: values.description,
        content: values.content,
        size: `${(values.content.length / 1024).toFixed(1)} KB`,
        lastModified: new Date().toLocaleString(),
        author: 'admin',
        usage: 0,
      }
      
      setScripts([newScript, ...scripts])
      setCreateModalVisible(false)
      form.resetFields()
      message.success('脚本创建成功')
    } catch (error) {
      message.error('脚本创建失败')
    }
  }

  const handleViewScript = (script) => {
    setSelectedScript(script)
    setViewModalVisible(true)
  }

  const handleDeleteScript = (script) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除脚本 ${script.name} 吗？`,
      okText: '确定',
      cancelText: '取消',
      onOk() {
        setScripts(scripts.filter(s => s.key !== script.key))
        message.success('脚本已删除')
      },
    })
  }

  const handleRunScript = (script) => {
    Modal.confirm({
      title: '执行脚本',
      content: `确定要执行脚本 ${script.name} 吗？`,
      okText: '确定',
      cancelText: '取消',
      onOk() {
        message.success('脚本执行任务已创建')
      },
    })
  }

  const getTypeColor = (type) => {
    const colors = {
      bash: 'green',
      powershell: 'blue',
      cmd: 'orange',
      python: 'purple',
    }
    return colors[type] || 'default'
  }

  const getTypeIcon = (type) => {
    const icons = {
      bash: '🐧',
      powershell: '💻',
      cmd: '⚡',
      python: '🐍',
    }
    return icons[type] || '📄'
  }

  const columns = [
    {
      title: '脚本信息',
      key: 'info',
      render: (_, record) => (
        <div>
          <div className="flex items-center space-x-2 mb-1">
            <span className="text-lg">{getTypeIcon(record.type)}</span>
            <Text strong>{record.name}</Text>
          </div>
          <Text code className="text-sm">{record.filename}</Text>
        </div>
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      render: (type) => (
        <Tag color={getTypeColor(type)} className="uppercase">
          {type}
        </Tag>
      ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      render: (text) => (
        <Text className="text-sm" style={{ maxWidth: 200 }}>
          {text}
        </Text>
      ),
    },
    {
      title: '大小',
      dataIndex: 'size',
      key: 'size',
      render: (text) => <Text className="font-mono text-sm">{text}</Text>,
    },
    {
      title: '使用次数',
      dataIndex: 'usage',
      key: 'usage',
      render: (count) => (
        <Tag color="blue" className="font-mono">
          {count}
        </Tag>
      ),
    },
    {
      title: '作者',
      dataIndex: 'author',
      key: 'author',
      render: (text) => <Text>{text}</Text>,
    },
    {
      title: '最后修改',
      dataIndex: 'lastModified',
      key: 'lastModified',
      render: (text) => (
        <Text type="secondary" className="text-sm">
          {text.split(' ')[0]}
        </Text>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      render: (_, record) => (
        <Space>
          <Tooltip title="查看代码">
            <Button 
              type="text" 
              icon={<EyeOutlined />} 
              size="small"
              onClick={() => handleViewScript(record)}
              className="text-blue-500 hover:bg-blue-50"
            />
          </Tooltip>
          <Tooltip title="编辑">
            <Button 
              type="text" 
              icon={<EditOutlined />} 
              size="small"
              className="text-green-500 hover:bg-green-50"
            />
          </Tooltip>
          <Tooltip title="执行">
            <Button 
              type="text" 
              icon={<PlayCircleOutlined />} 
              size="small"
              onClick={() => handleRunScript(record)}
              className="text-orange-500 hover:bg-orange-50"
            />
          </Tooltip>
          <Tooltip title="删除">
            <Button 
              type="text" 
              icon={<DeleteOutlined />} 
              size="small"
              danger
              onClick={() => handleDeleteScript(record)}
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
            <CodeOutlined className="mr-3 text-purple-500" />
            脚本管理
          </Title>
          <Text type="secondary" className="text-base">
            管理和维护所有脚本文件
          </Text>
        </div>
        <Space>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setCreateModalVisible(true)}
            className="shadow-lg"
          >
            创建脚本
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
              placeholder="搜索脚本名称或文件名"
              allowClear
              style={{ width: 250 }}
              prefix={<SearchOutlined className="text-gray-400" />}
            />
            <Select
              defaultValue="all"
              style={{ width: 120 }}
            >
              <Option value="all">全部类型</Option>
              <Option value="bash">Bash</Option>
              <Option value="powershell">PowerShell</Option>
              <Option value="cmd">CMD</Option>
              <Option value="python">Python</Option>
            </Select>
          </Space>
          
          <div className="flex items-center space-x-4 text-sm">
            <Space>
              <span>🐧</span>
              <Text>Bash: {scripts.filter(s => s.type === 'bash').length}</Text>
            </Space>
            <Space>
              <span>💻</span>
              <Text>PowerShell: {scripts.filter(s => s.type === 'powershell').length}</Text>
            </Space>
            <Space>
              <span>🐍</span>
              <Text>Python: {scripts.filter(s => s.type === 'python').length}</Text>
            </Space>
          </div>
        </div>
      </Card>

      {/* 脚本列表 */}
      <Card className="shadow-lg">
        <Table
          columns={columns}
          dataSource={scripts}
          loading={loading}
          pagination={{
            total: scripts.length,
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

      {/* 创建脚本模态框 */}
      <Modal
        title="创建新脚本"
        open={createModalVisible}
        onCancel={() => setCreateModalVisible(false)}
        footer={[
          <Button key="cancel" onClick={() => setCreateModalVisible(false)}>
            取消
          </Button>,
          <Button key="submit" type="primary" onClick={() => form.submit()}>
            创建脚本
          </Button>
        ]}
        width={800}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreateScript}
          className="mt-4"
        >
          <Form.Item
            name="name"
            label="脚本名称"
            rules={[{ required: true, message: '请输入脚本名称' }]}
          >
            <Input placeholder="输入脚本名称" />
          </Form.Item>
          
          <Form.Item
            name="filename"
            label="文件名"
            rules={[{ required: true, message: '请输入文件名' }]}
          >
            <Input placeholder="例如: script.sh" />
          </Form.Item>
          
          <Form.Item
            name="type"
            label="脚本类型"
            rules={[{ required: true, message: '请选择脚本类型' }]}
          >
            <Select placeholder="选择脚本类型">
              <Option value="bash">Bash</Option>
              <Option value="powershell">PowerShell</Option>
              <Option value="cmd">CMD</Option>
              <Option value="python">Python</Option>
            </Select>
          </Form.Item>
          
          <Form.Item
            name="description"
            label="脚本描述"
            rules={[{ required: true, message: '请输入脚本描述' }]}
          >
            <Input placeholder="输入脚本描述" />
          </Form.Item>
          
          <Form.Item
            name="content"
            label="脚本内容"
            rules={[{ required: true, message: '请输入脚本内容' }]}
          >
            <TextArea 
              rows={10} 
              placeholder="输入脚本代码..."
              className="font-mono"
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 查看脚本模态框 */}
      <Modal
        title={`脚本内容 - ${selectedScript?.name}`}
        open={viewModalVisible}
        onCancel={() => setViewModalVisible(false)}
        footer={[
          <Button key="close" onClick={() => setViewModalVisible(false)}>
            关闭
          </Button>
        ]}
        width={800}
      >
        <div className="space-y-4">
          <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
            <Space>
              <FileTextOutlined />
              <Text strong>{selectedScript?.filename}</Text>
              <Tag color={getTypeColor(selectedScript?.type)}>
                {selectedScript?.type?.toUpperCase()}
              </Tag>
            </Space>
            <Text type="secondary">{selectedScript?.size}</Text>
          </div>
          
          <div className="bg-gray-900 text-green-400 p-4 rounded-lg font-mono text-sm max-h-96 overflow-y-auto">
            <pre className="whitespace-pre-wrap">
              {selectedScript?.content}
            </pre>
          </div>
        </div>
      </Modal>
    </div>
  )
}

export default Scripts