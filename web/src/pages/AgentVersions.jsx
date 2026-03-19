import React, { useState, useEffect } from 'react'
import { Card, Table, Tag, Button, Space, Typography, Modal, Form, Input, message, Popconfirm, Switch, Upload } from 'antd'
import {
  CloudDownloadOutlined,
  PlusOutlined,
  DeleteOutlined,
  UploadOutlined,
  FileOutlined,
  RocketOutlined,
  DownloadOutlined,
} from '@ant-design/icons'
import api from '../services/auth'

const { Title, Text } = Typography
const { TextArea } = Input

const AgentVersions = () => {
  const [loading, setLoading] = useState(false)
  const [versions, setVersions] = useState([])
  const [modalVisible, setModalVisible] = useState(false)
  const [form] = Form.useForm()
  const [uploading, setUploading] = useState(false)

  // 版本号比较函数
  const compareVersions = (version1, version2) => {
    if (!version1 && !version2) return 0;
    if (!version1) return -1;
    if (!version2) return 1;
    
    try {
      const parts1 = version1.split('.').map(Number);
      const parts2 = version2.split('.').map(Number);
      const maxLength = Math.max(parts1.length, parts2.length);
      
      for (let i = 0; i < maxLength; i++) {
        const part1 = i < parts1.length ? parts1[i] : 0;
        const part2 = i < parts2.length ? parts2[i] : 0;
        
        if (part1 !== part2) {
          return part1 - part2;
        }
      }
      return 0;
    } catch (error) {
      return version1.localeCompare(version2);
    }
  };

  // 加载版本列表
  const loadVersions = async () => {
    setLoading(true)
    try {
      const response = await api.get('/web/agent-versions')
      setVersions(response)
    } catch (error) {
      console.error('加载版本列表失败:', error)
      message.error('加载版本列表失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadVersions()
  }, [])

  // 上传文件并创建版本
  const handleUpload = async (values) => {
    const { file, releaseNotes } = values
    
    if (!file || !file.fileList || file.fileList.length === 0) {
      message.error('请选择要上传的文件')
      return
    }

    const uploadFile = file.fileList[0]
    if (!uploadFile.originFileObj) {
      message.error('文件上传失败')
      return
    }

    console.log('Uploading file:', uploadFile.name)
    setUploading(true)
    
    try {
      // 1. 先上传文件到文件管理系统
      const formData = new FormData()
      formData.append('file', uploadFile.originFileObj)
      formData.append('name', uploadFile.name)
      formData.append('category', 'agent-version')
      formData.append('description', `Agent版本文件: ${uploadFile.name}`)
      
      console.log('Uploading to file system...')
      const fileResponse = await api.post('/web/files/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      })
      
      console.log('File uploaded, response:', fileResponse)

      // 2. 使用文件ID创建版本
      console.log('Creating version from file ID:', fileResponse.fileId)
      const versionResponse = await api.post('/web/agent-versions/from-file', null, {
        params: {
          fileId: fileResponse.fileId,
          releaseNotes: releaseNotes
        }
      })
      
      console.log('Version created:', versionResponse)
      message.success('版本创建成功')
      setModalVisible(false)
      form.resetFields()
      loadVersions()
      
    } catch (error) {
      console.error('创建版本失败:', error)
      
      // 显示详细错误信息
      let errorMessage = '创建版本失败'
      if (error.response?.data?.message) {
        errorMessage += ': ' + error.response.data.message
      } else if (error.message) {
        errorMessage += ': ' + error.message
      }
      
      message.error(errorMessage)
    } finally {
      setUploading(false)
    }
  }

  // 更新强制升级状态
  const handleForceUpgradeChange = async (id, forceUpgrade) => {
    try {
      await api.put(`/web/agent-versions/${id}/force-upgrade`, null, {
        params: { forceUpgrade }
      })
      message.success('强制升级状态已更新')
      loadVersions()
    } catch (error) {
      console.error('更新失败:', error)
      message.error('更新失败')
    }
  }

  // 下载版本文件
  const handleDownload = async (record) => {
    try {
      // 使用文件ID构建下载URL
      const downloadUrl = `/api/web/files/${record.fileId}/download`
      
      // 添加认证头
      const token = localStorage.getItem('token')
      if (token) {
        // 对于需要认证的下载，我们需要通过fetch来处理
        const response = await fetch(downloadUrl, {
          headers: {
            'Authorization': `Bearer ${token}`
          }
        })
        
        if (!response.ok) {
          throw new Error('下载失败')
        }
        
        const blob = await response.blob()
        const url = window.URL.createObjectURL(blob)
        
        // 创建一个临时的a标签来触发下载
        const link = document.createElement('a')
        link.href = url
        link.download = record.originalFilename || `agent-${record.version}.jar`
        
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        
        // 清理URL对象
        window.URL.revokeObjectURL(url)
        
        message.success('文件下载已开始')
      }
    } catch (error) {
      console.error('下载失败:', error)
      message.error('下载失败')
    }
  }

  // 删除版本
  const handleDelete = async (id) => {
    try {
      await api.delete(`/web/agent-versions/${id}`)
      message.success('版本删除成功')
      loadVersions()
    } catch (error) {
      console.error('删除版本失败:', error)
      message.error('删除版本失败')
    }
  }

  const columns = [
    {
      title: '版本号',
      dataIndex: 'version',
      key: 'version',
      render: (text, record) => {
        // 基于版本号自动判断是否为最新版本
        const isLatestVersion = versions.length > 0 && 
          versions.reduce((latest, current) => 
            compareVersions(current.version, latest.version) > 0 ? current : latest
          ).id === record.id;
        
        return (
          <Space>
            <Text strong>{text}</Text>
            {isLatestVersion && <Tag color="green">最新</Tag>}
          </Space>
        );
      },
    },
    {
      title: '文件信息',
      key: 'fileInfo',
      render: (_, record) => (
        <Space direction="vertical" size="small">
          <Space>
            <FileOutlined />
            <Text>{record.originalFilename}</Text>
          </Space>
          <Text type="secondary" style={{ fontSize: '12px' }}>
            {record.fileSize ? `${(record.fileSize / 1024 / 1024).toFixed(2)} MB` : 'N/A'}
          </Text>
        </Space>
      ),
    },
    {
      title: '版本说明',
      dataIndex: 'releaseNotes',
      key: 'releaseNotes',
      ellipsis: true,
      render: (text) => (
        <Text style={{ maxWidth: 200 }} ellipsis={{ tooltip: text }}>
          {text || '无说明'}
        </Text>
      ),
    },
    {
      title: '强制升级',
      dataIndex: 'forceUpgrade',
      key: 'forceUpgrade',
      render: (force, record) => (
        <Switch
          checked={force}
          onChange={(checked) => handleForceUpgradeChange(record.id, checked)}
          checkedChildren="是"
          unCheckedChildren="否"
        />
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (text) => new Date(text).toLocaleString('zh-CN'),
    },
    {
      title: '创建者',
      dataIndex: 'createdBy',
      key: 'createdBy',
    },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            icon={<DownloadOutlined />}
            size="small"
            onClick={() => handleDownload(record)}
            title="下载版本文件"
          >
            下载
          </Button>
          <Popconfirm
            title="确定要删除这个版本吗？"
            onConfirm={() => handleDelete(record.id)}
            okText="确定"
            cancelText="取消"
          >
            <Button
              type="link"
              icon={<DeleteOutlined />}
              size="small"
              danger
            >
              删除
            </Button>
          </Popconfirm>
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
            <CloudDownloadOutlined className="mr-3 text-blue-500" />
            Agent 版本管理
          </Title>
          <Text type="secondary" className="text-base">
            上传Agent版本文件，系统自动解析版本号并管理升级
          </Text>
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => setModalVisible(true)}
          className="shadow-lg"
        >
          上传新版本
        </Button>
      </div>

      {/* 版本列表 */}
      <Card className="shadow-lg">
        <Table
          columns={columns}
          dataSource={versions}
          rowKey="id"
          loading={loading}
          pagination={{
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => `共 ${total} 个版本`,
          }}
        />
      </Card>

      {/* 上传版本弹窗 */}
      <Modal
        title={
          <Space>
            <RocketOutlined className="text-blue-500" />
            <span>上传新版本</span>
          </Space>
        }
        open={modalVisible}
        onCancel={() => {
          setModalVisible(false)
          form.resetFields()
        }}
        footer={null}
        width={600}
        className="top-8"
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleUpload}
          className="mt-6"
        >
          <Form.Item
            name="file"
            label="Agent文件"
            rules={[{ required: true, message: '请选择Agent文件' }]}
          >
            <Upload
              beforeUpload={() => false} // 阻止自动上传
              maxCount={1}
              accept=".jar"
            >
              <Button icon={<UploadOutlined />}>选择JAR文件</Button>
            </Upload>
          </Form.Item>

          <Form.Item
            name="releaseNotes"
            label="版本说明"
            rules={[{ required: true, message: '请输入版本说明' }]}
          >
            <TextArea
              rows={4}
              placeholder="描述此版本的新功能、改进和修复..."
            />
          </Form.Item>

          <div className="bg-blue-50 p-4 rounded-lg mb-4">
            <Text type="secondary" className="text-sm">
              💡 <strong>文件命名规范：</strong>
              <br />
              • 文件名应包含版本号，如：<code>agent-1.2.3.jar</code>、<code>viberemote-agent-2.0.0.jar</code>
              <br />
              • 系统会自动从文件名解析版本号
              <br />
              • 版本号最高的版本将作为最新版本供Agent自动升级
            </Text>
          </div>

          <div className="flex justify-end space-x-2 pt-4 border-t">
            <Button
              onClick={() => {
                setModalVisible(false)
                form.resetFields()
              }}
            >
              取消
            </Button>
            <Button 
              type="primary" 
              htmlType="submit"
              loading={uploading}
            >
              上传并创建版本
            </Button>
          </div>
        </Form>
      </Modal>
    </div>
  )
}

export default AgentVersions