import React, { useState, useEffect } from 'react'
import { Card, Table, Tag, Button, Space, Typography, Input, Select, Modal, Form, message, Upload, Progress, Tooltip } from 'antd'
import {
  FileOutlined,
  SearchOutlined,
  ReloadOutlined,
  PlusOutlined,
  UploadOutlined,
  DownloadOutlined,
  DeleteOutlined,
  EyeOutlined,
  InboxOutlined,
  FolderOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons'
import api from '../services/auth'

const { Title, Text } = Typography
const { Search } = Input
const { Option } = Select
const { Dragger } = Upload

const Files = () => {
  const [loading, setLoading] = useState(false)
  const [files, setFiles] = useState([])
  const [totalFiles, setTotalFiles] = useState(0)
  const [currentPage, setCurrentPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [searchKeyword, setSearchKeyword] = useState('')
  const [selectedCategory, setSelectedCategory] = useState('all')
  const [categories, setCategories] = useState([])
  const [uploadModalVisible, setUploadModalVisible] = useState(false)
  const [detailModalVisible, setDetailModalVisible] = useState(false)
  const [selectedFile, setSelectedFile] = useState(null)
  const [form] = Form.useForm()
  const [fileList, setFileList] = useState([])
  const [uploading, setUploading] = useState(false)

  // 获取文件列表
  const fetchFiles = async () => {
    setLoading(true)
    try {
      const params = {
        page: currentPage - 1,
        size: pageSize
      }
      
      if (searchKeyword.trim()) {
        params.keyword = searchKeyword.trim()
      }
      
      if (selectedCategory !== 'all') {
        params.category = selectedCategory
      }
      
      const response = await api.get('/web/files', { params })
      
      if (response?.content) {
        setFiles(response.content)
        setTotalFiles(response.totalElements || 0)
      } else if (Array.isArray(response)) {
        setFiles(response)
        setTotalFiles(response.length)
      }
    } catch (error) {
      console.error('获取文件列表失败:', error)
      message.error('获取文件列表失败')
    } finally {
      setLoading(false)
    }
  }

  // 获取文件分类
  const fetchCategories = async () => {
    try {
      const response = await api.get('/web/files/categories')
      setCategories(response || [])
    } catch (error) {
      console.error('获取分类列表失败:', error)
    }
  }

  // 初始化
  useEffect(() => {
    fetchFiles()
    fetchCategories()
  }, [currentPage, pageSize, searchKeyword, selectedCategory])

  // 处理搜索
  const handleSearch = (value) => {
    setSearchKeyword(value)
    setCurrentPage(1)
  }

  // 处理分类筛选
  const handleCategoryChange = (value) => {
    setSelectedCategory(value)
    setCurrentPage(1)
  }

  // 重置筛选
  const handleResetFilters = () => {
    setSearchKeyword('')
    setSelectedCategory('all')
    setCurrentPage(1)
  }

  // 上传文件
  const handleUpload = async (values) => {
    if (fileList.length === 0) {
      message.error('请选择要上传的文件')
      return
    }

    setUploading(true)
    try {
      const file = fileList[0]
      const formData = new FormData()
      formData.append('file', file)
      formData.append('name', values.name)
      formData.append('category', values.category)
      if (values.description) {
        formData.append('description', values.description)
      }
      if (values.tags) {
        formData.append('tags', values.tags)
      }
      if (values.version) {
        formData.append('version', values.version)
      }

      await api.post('/web/files/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      })

      message.success('文件上传成功')
      setUploadModalVisible(false)
      form.resetFields()
      setFileList([])
      fetchFiles()
      fetchCategories() // 刷新分类列表
    } catch (error) {
      console.error('文件上传失败:', error)
      message.error('文件上传失败: ' + (error.response?.data?.message || error.message))
    } finally {
      setUploading(false)
    }
  }

  // 查看文件详情
  const handleViewDetail = (file) => {
    setSelectedFile(file)
    setDetailModalVisible(true)
  }

  // 下载文件
  const handleDownload = async (file) => {
    try {
      const token = localStorage.getItem('token')
      const response = await fetch(`${api.defaults.baseURL}/web/files/${file.fileId}/download`, {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      })
      
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }
      
      const blob = await response.blob()
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = file.originalName
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
      
      message.success(`文件 ${file.originalName} 下载成功`)
    } catch (error) {
      console.error('下载文件失败:', error)
      message.error('下载失败: ' + error.message)
    }
  }

  // 删除文件
  const handleDelete = (file) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除文件 "${file.name}" 吗？此操作不可恢复。`,
      okText: '确定',
      cancelText: '取消',
      async onOk() {
        try {
          await api.delete(`/web/files/${file.fileId}`)
          message.success('文件删除成功')
          fetchFiles()
          fetchCategories()
        } catch (error) {
          console.error('删除文件失败:', error)
          message.error('删除失败: ' + (error.response?.data?.message || error.message))
        }
      }
    })
  }

  // 获取分类颜色
  const getCategoryColor = (category) => {
    const colors = {
      'config': 'blue',
      'script': 'green',
      'document': 'orange',
      'image': 'purple',
      'archive': 'cyan',
      'other': 'default'
    }
    return colors[category] || 'default'
  }

  // 获取文件类型图标
  const getFileTypeIcon = (fileType) => {
    if (!fileType) return <FileOutlined />
    
    if (fileType.startsWith('image/')) return '🖼️'
    if (fileType.startsWith('text/')) return '📄'
    if (fileType.includes('zip') || fileType.includes('tar') || fileType.includes('rar')) return '📦'
    if (fileType.includes('pdf')) return '📕'
    if (fileType.includes('video/')) return '🎥'
    if (fileType.includes('audio/')) return '🎵'
    
    return <FileOutlined />
  }

  // 上传配置
  const uploadProps = {
    name: 'file',
    multiple: false,
    fileList: fileList,
    beforeUpload: (file) => {
      // 检查文件大小 (限制为100MB)
      if (file.size > 100 * 1024 * 1024) {
        message.error('文件大小不能超过100MB')
        return false
      }

      setFileList([file])
      
      // 自动填充文件名
      form.setFieldsValue({ 
        name: file.name.substring(0, file.name.lastIndexOf('.')) || file.name
      })
      
      return false // 阻止自动上传
    },
    onRemove: () => {
      setFileList([])
      form.resetFields(['name'])
    },
  }

  // 表格列定义
  const columns = [
    {
      title: '文件信息',
      key: 'info',
      width: 300,
      render: (_, record) => (
        <div>
          <div className="flex items-center space-x-2 mb-1">
            <span className="text-lg">{getFileTypeIcon(record.fileType)}</span>
            <Text strong>{record.name}</Text>
          </div>
          <Text code className="text-sm text-gray-500">{record.originalName}</Text>
          <div className="text-xs text-gray-400 mt-1">
            ID: {record.fileId}
          </div>
        </div>
      ),
    },
    {
      title: '分类',
      dataIndex: 'category',
      key: 'category',
      width: 120,
      render: (category) => (
        <Tag color={getCategoryColor(category)} icon={<FolderOutlined />}>
          {category || '未分类'}
        </Tag>
      ),
    },
    {
      title: '文件大小',
      dataIndex: 'sizeDisplay',
      key: 'sizeDisplay',
      width: 120,
      render: (size) => <Text className="font-mono">{size}</Text>,
    },
    {
      title: '文件类型',
      dataIndex: 'fileType',
      key: 'fileType',
      width: 150,
      render: (type) => (
        <Text className="text-sm text-gray-600">
          {type || '未知'}
        </Text>
      ),
    },
    {
      title: '版本',
      dataIndex: 'version',
      key: 'version',
      width: 80,
      render: (version) => <Tag color="cyan">{version}</Tag>,
    },
    {
      title: '校验和',
      key: 'checksum',
      width: 120,
      render: (_, record) => (
        <div>
          {record.md5 && (
            <Tooltip title={`MD5: ${record.md5}`}>
              <Tag color="green" size="small" icon={<SafetyCertificateOutlined />}>
                MD5
              </Tag>
            </Tooltip>
          )}
          {record.sha256 && (
            <Tooltip title={`SHA256: ${record.sha256}`}>
              <Tag color="blue" size="small" icon={<SafetyCertificateOutlined />}>
                SHA256
              </Tag>
            </Tooltip>
          )}
        </div>
      ),
    },
    {
      title: '上传者',
      dataIndex: 'uploadBy',
      key: 'uploadBy',
      width: 100,
      render: (uploadBy) => <Text>{uploadBy}</Text>,
    },
    {
      title: '上传时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (time) => (
        <Text className="text-sm">
          {time ? new Date(time).toLocaleString('zh-CN') : '-'}
        </Text>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small" wrap>
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
            icon={<DownloadOutlined />} 
            size="small"
            onClick={() => handleDownload(record)}
          >
            下载
          </Button>
          <Button 
            type="link" 
            icon={<DeleteOutlined />} 
            size="small"
            danger
            onClick={() => handleDelete(record)}
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
            <FileOutlined className="mr-3 text-blue-500" />
            文件管理
          </Title>
          <Text type="secondary" className="text-base">
            管理和维护服务器上的文件资源
          </Text>
        </div>
        <Space>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setUploadModalVisible(true)}
            className="shadow-lg"
          >
            上传文件
          </Button>
          <Button
            icon={<ReloadOutlined />}
            loading={loading}
            onClick={fetchFiles}
          >
            刷新
          </Button>
        </Space>
      </div>

      {/* 文件列表 */}
      <Card className="shadow-lg">
        {/* 工具栏 */}
        <div className="flex flex-col sm:flex-row gap-4 items-start sm:items-center justify-between mb-4">
          <Space wrap>
            <Search
              placeholder="搜索文件名或描述"
              allowClear
              style={{ width: 250 }}
              prefix={<SearchOutlined className="text-gray-400" />}
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
              onSearch={handleSearch}
            />
            <Select
              value={selectedCategory}
              style={{ width: 120 }}
              onChange={handleCategoryChange}
            >
              <Option value="all">全部分类</Option>
              {categories.map(category => (
                <Option key={category} value={category}>
                  {category}
                </Option>
              ))}
            </Select>
            {(searchKeyword || selectedCategory !== 'all') && (
              <Button 
                onClick={handleResetFilters}
                size="small"
              >
                重置筛选
              </Button>
            )}
          </Space>
          
          <div className="flex items-center space-x-4 text-sm">
            <Space>
              <span>📊</span>
              <Text>
                显示: {files.length} / {totalFiles}
              </Text>
            </Space>
          </div>
        </div>

        <Table
          columns={columns}
          dataSource={files}
          loading={loading}
          rowKey="fileId"
          pagination={{
            current: currentPage,
            pageSize: pageSize,
            total: totalFiles,
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
          scroll={{ x: 1400 }}
        />
      </Card>

      {/* 上传文件模态框 */}
      <Modal
        title="上传文件"
        open={uploadModalVisible}
        onCancel={() => {
          setUploadModalVisible(false)
          form.resetFields()
          setFileList([])
        }}
        footer={[
          <Button key="cancel" onClick={() => {
            setUploadModalVisible(false)
            form.resetFields()
            setFileList([])
          }}>
            取消
          </Button>,
          <Button 
            key="submit" 
            type="primary" 
            loading={uploading}
            onClick={() => form.submit()}
          >
            上传文件
          </Button>
        ]}
        width={600}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleUpload}
          className="mt-4"
        >
          <Form.Item
            name="file"
            label="选择文件"
            rules={[{ required: true, message: '请选择要上传的文件' }]}
          >
            <Dragger {...uploadProps}>
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p className="ant-upload-text">点击或拖拽文件到此区域上传</p>
              <p className="ant-upload-hint">
                支持单个文件上传，文件大小不超过100MB
              </p>
            </Dragger>
          </Form.Item>
          
          <div className="grid grid-cols-2 gap-4">
            <Form.Item
              name="name"
              label="文件名称"
              rules={[{ required: true, message: '请输入文件名称' }]}
            >
              <Input placeholder="输入文件名称" />
            </Form.Item>
            
            <Form.Item
              name="category"
              label="文件分类"
              rules={[{ required: true, message: '请选择文件分类' }]}
            >
              <Select placeholder="选择文件分类">
                <Option value="config">配置文件</Option>
                <Option value="script">脚本文件</Option>
                <Option value="document">文档文件</Option>
                <Option value="image">图片文件</Option>
                <Option value="archive">压缩文件</Option>
                <Option value="other">其他文件</Option>
              </Select>
            </Form.Item>
            
            <Form.Item
              name="version"
              label="版本号"
              initialValue="1.0"
            >
              <Input placeholder="例如: 1.0" />
            </Form.Item>
            
            <Form.Item
              name="tags"
              label="标签"
            >
              <Input placeholder="多个标签用逗号分隔" />
            </Form.Item>
          </div>
          
          <Form.Item
            name="description"
            label="文件描述"
          >
            <Input.TextArea 
              rows={3} 
              placeholder="输入文件描述（可选）"
              maxLength={500}
              showCount
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 文件详情模态框 */}
      <Modal
        title={`文件详情 - ${selectedFile?.name}`}
        open={detailModalVisible}
        onCancel={() => setDetailModalVisible(false)}
        footer={[
          <Button 
            key="download" 
            type="primary" 
            icon={<DownloadOutlined />}
            onClick={() => handleDownload(selectedFile)}
          >
            下载文件
          </Button>,
          <Button key="close" onClick={() => setDetailModalVisible(false)}>
            关闭
          </Button>
        ]}
        width={700}
      >
        {selectedFile && (
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <Text strong>文件ID：</Text>
                <Text code>{selectedFile.fileId}</Text>
              </div>
              <div>
                <Text strong>原始文件名：</Text>
                <Text>{selectedFile.originalName}</Text>
              </div>
              <div>
                <Text strong>文件大小：</Text>
                <Text>{selectedFile.sizeDisplay}</Text>
              </div>
              <div>
                <Text strong>文件类型：</Text>
                <Text>{selectedFile.fileType || '未知'}</Text>
              </div>
              <div>
                <Text strong>分类：</Text>
                <Tag color={getCategoryColor(selectedFile.category)}>
                  {selectedFile.category || '未分类'}
                </Tag>
              </div>
              <div>
                <Text strong>版本：</Text>
                <Tag color="cyan">{selectedFile.version}</Tag>
              </div>
              <div>
                <Text strong>上传者：</Text>
                <Text>{selectedFile.uploadBy}</Text>
              </div>
              <div>
                <Text strong>上传时间：</Text>
                <Text>{new Date(selectedFile.createdAt).toLocaleString('zh-CN')}</Text>
              </div>
            </div>
            
            {selectedFile.description && (
              <div>
                <Text strong>文件描述：</Text>
                <div className="mt-2 p-3 bg-gray-50 rounded">
                  <Text>{selectedFile.description}</Text>
                </div>
              </div>
            )}
            
            {selectedFile.tags && (
              <div>
                <Text strong>标签：</Text>
                <div className="mt-2">
                  {selectedFile.tags.split(',').map((tag, index) => (
                    <Tag key={index} className="mb-1">
                      {tag.trim()}
                    </Tag>
                  ))}
                </div>
              </div>
            )}
            
            <div>
              <Text strong>文件校验：</Text>
              <div className="mt-2 space-y-2">
                {selectedFile.md5 && (
                  <div className="flex items-center space-x-2">
                    <Tag color="green" icon={<SafetyCertificateOutlined />}>MD5</Tag>
                    <Text code className="text-xs">{selectedFile.md5}</Text>
                  </div>
                )}
                {selectedFile.sha256 && (
                  <div className="flex items-center space-x-2">
                    <Tag color="blue" icon={<SafetyCertificateOutlined />}>SHA256</Tag>
                    <Text code className="text-xs">{selectedFile.sha256}</Text>
                  </div>
                )}
              </div>
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}

export default Files