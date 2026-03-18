import React, { useState, useEffect } from 'react'
import { Card, Table, Tag, Button, Space, Typography, Input, Select, Modal, Form, message, Tooltip, Upload, Tabs } from 'antd'
import {
  CodeOutlined,
  SearchOutlined,
  ReloadOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  EyeOutlined,
  FileTextOutlined,
  UploadOutlined,
  InboxOutlined,
  DownloadOutlined,
} from '@ant-design/icons'
import scriptService from '../services/scriptService'
import { encryptText, decryptText, getSessionKey } from '../utils/crypto'

const { Title, Text } = Typography
const { Search, TextArea } = Input
const { Option } = Select
const { TabPane } = Tabs
const { Dragger } = Upload

const Scripts = () => {
  const [loading, setLoading] = useState(false)
  const [createModalVisible, setCreateModalVisible] = useState(false)
  const [editModalVisible, setEditModalVisible] = useState(false)
  const [reuploadModalVisible, setReuploadModalVisible] = useState(false)
  const [viewModalVisible, setViewModalVisible] = useState(false)
  const [selectedScript, setSelectedScript] = useState(null)
  const [form] = Form.useForm()
  const [editForm] = Form.useForm()
  const [uploadForm] = Form.useForm()
  const [reuploadForm] = Form.useForm()
  const [fileList, setFileList] = useState([])
  const [reuploadFileList, setReuploadFileList] = useState([])
  const [activeTab, setActiveTab] = useState('manual')
  const [scripts, setScripts] = useState([])
  const [filteredScripts, setFilteredScripts] = useState([])
  const [searchKeyword, setSearchKeyword] = useState('')
  const [selectedType, setSelectedType] = useState('all')
  const [scriptStats, setScriptStats] = useState({
    total: 0,
    bash: 0,
    python: 0,
    powershell: 0,
    javascript: 0,
    typescript: 0,
    cmd: 0
  })

  // 初始化和监听脚本数据变化
  useEffect(() => {
    loadScripts()
    
    const handleScriptsChange = (newScripts) => {
      setScripts([...newScripts])
      setFilteredScripts([...newScripts])
    }
    
    scriptService.addListener(handleScriptsChange)
    
    return () => {
      scriptService.removeListener(handleScriptsChange)
    }
  }, [])

  // 加载脚本统计信息
  const loadScriptStats = async () => {
    try {
      const allScripts = await scriptService.getAllScripts()
      const stats = {
        total: allScripts.length,
        bash: allScripts.filter(s => s.type === 'bash').length,
        python: allScripts.filter(s => s.type === 'python').length,
        powershell: allScripts.filter(s => s.type === 'powershell').length,
        javascript: allScripts.filter(s => s.type === 'javascript').length,
        typescript: allScripts.filter(s => s.type === 'typescript').length,
        cmd: allScripts.filter(s => s.type === 'cmd').length
      }
      setScriptStats(stats)
    } catch (error) {
      console.error('加载统计信息失败:', error)
    }
  }

  // 加载脚本数据
  const loadScripts = async (filters = {}) => {
    try {
      setLoading(true)
      const scripts = await scriptService.getAllScripts(filters)
      setScripts(scripts)
      setFilteredScripts(scripts)
      
      // 如果没有过滤条件，更新统计信息
      if (Object.keys(filters).length === 0) {
        const stats = {
          total: scripts.length,
          bash: scripts.filter(s => s.type === 'bash').length,
          python: scripts.filter(s => s.type === 'python').length,
          powershell: scripts.filter(s => s.type === 'powershell').length,
          javascript: scripts.filter(s => s.type === 'javascript').length,
          typescript: scripts.filter(s => s.type === 'typescript').length,
          cmd: scripts.filter(s => s.type === 'cmd').length
        }
        setScriptStats(stats)
      }
    } catch (error) {
      console.error('加载脚本失败:', error)
      message.error('加载脚本失败')
    } finally {
      setLoading(false)
    }
  }

  // 应用过滤器
  const applyFilters = () => {
    const filters = {}
    
    if (searchKeyword.trim()) {
      filters.keyword = searchKeyword.trim()
    }
    
    if (selectedType !== 'all') {
      filters.type = selectedType
    }
    
    loadScripts(filters)
  }

  // 过滤器变化时应用过滤
  useEffect(() => {
    const timeoutId = setTimeout(() => {
      applyFilters()
    }, 300) // 防抖，300ms后执行搜索
    
    return () => clearTimeout(timeoutId)
  }, [searchKeyword, selectedType])

  const handleRefresh = async () => {
    scriptService.clearCache()
    await loadScripts()
    message.success('数据已刷新')
  }

  const handleSearch = (value) => {
    setSearchKeyword(value)
  }

  const handleTypeChange = (value) => {
    setSelectedType(value)
  }

  const handleResetFilters = () => {
    setSearchKeyword('')
    setSelectedType('all')
    // 重置后会触发useEffect自动加载数据
  }

  const handleUploadScript = async (values) => {
    try {
      if (fileList.length === 0) {
        message.error('请选择要上传的脚本文件')
        return
      }

      const file = fileList[0]
      const scriptData = {
        name: values.name,
        type: values.type,
        description: values.description,
      }
      
      await scriptService.uploadScript(scriptData, file)
      setCreateModalVisible(false)
      uploadForm.resetFields()
      setFileList([])
      setActiveTab('manual')
      message.success('脚本上传成功')
    } catch (error) {
      message.error('脚本上传失败: ' + (error.response?.data?.message || error.message))
    }
  }

  const uploadProps = {
    name: 'file',
    multiple: false,
    fileList: fileList,
    beforeUpload: (file) => {
      // 检查文件类型
      const allowedTypes = ['.sh', '.ps1', '.bat', '.cmd', '.py', '.js', '.ts']
      const fileExtension = file.name.toLowerCase().substring(file.name.lastIndexOf('.'))
      
      if (!allowedTypes.includes(fileExtension)) {
        message.error('只支持脚本文件格式: .sh, .ps1, .bat, .cmd, .py, .js, .ts')
        return false
      }

      // 检查文件大小 (限制为1MB)
      if (file.size > 1024 * 1024) {
        message.error('文件大小不能超过1MB')
        return false
      }

      setFileList([file])
      
      // 根据文件扩展名自动设置脚本类型
      const typeMap = {
        '.sh': 'bash',
        '.ps1': 'powershell', 
        '.bat': 'cmd',
        '.cmd': 'cmd',
        '.py': 'python',
        '.js': 'javascript',
        '.ts': 'typescript'
      }
      
      const scriptType = typeMap[fileExtension] || 'bash'
      uploadForm.setFieldsValue({ 
        type: scriptType,
        filename: file.name 
      })
      
      return false // 阻止自动上传
    },
    onRemove: () => {
      setFileList([])
      uploadForm.resetFields(['type', 'filename'])
    },
  }

  const handleCreateScript = async (values) => {
    try {
      let content = values.content
      const encKey = getSessionKey()
      if (encKey && content) {
        try { content = await encryptText(content, encKey) } catch (e) { console.warn('[crypto] 加密失败:', e) }
      }
      const scriptData = {
        name: values.name,
        filename: values.filename,
        type: values.type,
        description: values.description,
        encoding: values.encoding || 'UTF-8',
        content,
      }
      
      await scriptService.addScript(scriptData)
      setCreateModalVisible(false)
      form.resetFields()
      message.success('脚本创建成功')
    } catch (error) {
      message.error('脚本创建失败: ' + (error.response?.data?.message || error.message))
    }
  }

  const handleViewScript = async (script) => {
    setSelectedScript(script)
    
    try {
      const response = await scriptService.getScriptContent(script.scriptId)
      let content = response.content || ''
      if (response.encrypted && content) {
        const encKey = getSessionKey()
        if (encKey) {
          try { content = await decryptText(content, encKey) } catch (e) { console.warn('[crypto] 解密失败:', e) }
        }
      }
      script.content = content
      setViewModalVisible(true)
    } catch (error) {
      message.error('获取脚本内容失败')
    }
  }

  const handleDownloadScript = async (script) => {
    try {
      const response = await scriptService.downloadScript(script.scriptId)
      const blob = await response.blob()
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = script.filename
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
      message.success(`脚本 ${script.filename} 下载成功`)
    } catch (error) {
      message.error('下载失败: ' + (error.response?.data?.message || error.message))
    }
  }

  const handleEditScript = (script) => {
    setSelectedScript(script)
    
    if (script.isUploaded) {
      // 上传的脚本：打开重新上传Modal
      reuploadForm.setFieldsValue({
        name: script.name,
        type: script.type,
        description: script.description,
      })
      setReuploadModalVisible(true)
    } else {
      // 手动录入的脚本：打开编辑Modal
      editForm.setFieldsValue({
        name: script.name,
        filename: script.filename,
        type: script.type,
        description: script.description,
        encoding: script.encoding,
        content: script.content,
      })
      setEditModalVisible(true)
    }
  }

  const handleUpdateScript = async (values) => {
    try {
      let content = values.content
      const encKey = getSessionKey()
      if (encKey && content) {
        try { content = await encryptText(content, encKey) } catch (e) { console.warn('[crypto] 加密失败:', e) }
      }
      const updates = {
        name: values.name,
        filename: values.filename,
        type: values.type,
        description: values.description,
        encoding: values.encoding,
        content,
      }
      
      await scriptService.updateScript(selectedScript.scriptId, updates)
      setEditModalVisible(false)
      editForm.resetFields()
      setSelectedScript(null)
      message.success('脚本更新成功')
    } catch (error) {
      message.error('脚本更新失败: ' + (error.response?.data?.message || error.message))
    }
  }

  const handleReuploadScript = async (values) => {
    try {
      if (reuploadFileList.length === 0) {
        message.error('请选择要重新上传的脚本文件')
        return
      }

      const file = reuploadFileList[0]
      const scriptData = {
        name: values.name,
        type: values.type,
        description: values.description,
      }
      
      await scriptService.reuploadScript(selectedScript.scriptId, scriptData, file)
      setReuploadModalVisible(false)
      reuploadForm.resetFields()
      setReuploadFileList([])
      setSelectedScript(null)
      message.success('脚本重新上传成功')
    } catch (error) {
      message.error('脚本重新上传失败: ' + (error.response?.data?.message || error.message))
    }
  }

  const reuploadProps = {
    name: 'file',
    multiple: false,
    fileList: reuploadFileList,
    beforeUpload: (file) => {
      // 检查文件类型
      const allowedTypes = ['.sh', '.ps1', '.bat', '.cmd', '.py', '.js', '.ts']
      const fileExtension = file.name.toLowerCase().substring(file.name.lastIndexOf('.'))
      
      if (!allowedTypes.includes(fileExtension)) {
        message.error('只支持脚本文件格式: .sh, .ps1, .bat, .cmd, .py, .js, .ts')
        return false
      }

      // 检查文件大小 (限制为1MB)
      if (file.size > 1024 * 1024) {
        message.error('文件大小不能超过1MB')
        return false
      }

      setReuploadFileList([file])
      
      // 根据文件扩展名自动设置脚本类型
      const typeMap = {
        '.sh': 'bash',
        '.ps1': 'powershell', 
        '.bat': 'cmd',
        '.cmd': 'cmd',
        '.py': 'python',
        '.js': 'javascript',
        '.ts': 'typescript'
      }
      
      const scriptType = typeMap[fileExtension] || 'bash'
      reuploadForm.setFieldsValue({ type: scriptType })
      
      return false // 阻止自动上传
    },
    onRemove: () => {
      setReuploadFileList([])
      reuploadForm.resetFields(['type'])
    },
  }

  const handleDeleteScript = (script) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除脚本 ${script.name} 吗？`,
      okText: '确定',
      cancelText: '取消',
      async onOk() {
        try {
          await scriptService.deleteScript(script.scriptId)
          message.success('脚本已删除')
        } catch (error) {
          message.error('删除失败: ' + (error.response?.data?.message || error.message))
        }
      },
    })
  }

  const getTypeColor = (type) => {
    const colors = {
      bash: 'green',
      powershell: 'blue',
      cmd: 'orange',
      python: 'purple',
      javascript: 'yellow',
      typescript: 'cyan',
    }
    return colors[type] || 'default'
  }

  const getTypeIcon = (type) => {
    const icons = {
      bash: '🐧',
      powershell: '💻',
      cmd: '⚡',
      python: '🐍',
      javascript: '🟨',
      typescript: '🔷',
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
            {record.isUploaded && (
              <Tag color="blue" size="small">上传</Tag>
            )}
          </div>
          <Text code className="text-sm">{record.filename}</Text>
          {record.encoding && !record.isUploaded && (
            <div>
              <Text type="secondary" className="text-xs">编码: {record.encoding}</Text>
            </div>
          )}
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
          {text || '-'}
        </Text>
      ),
    },
    {
      title: '大小',
      dataIndex: 'sizeDisplay',
      key: 'sizeDisplay',
      render: (text) => <Text className="font-mono text-sm">{text}</Text>,
    },
    {
      title: '使用次数',
      dataIndex: 'usageCount',
      key: 'usageCount',
      render: (count) => (
        <Tag color="blue" className="font-mono">
          {count}
        </Tag>
      ),
    },
    {
      title: '创建者',
      dataIndex: 'createdBy',
      key: 'createdBy',
      render: (text) => <Text>{text}</Text>,
    },
    {
      title: '最后修改',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      render: (text) => (
        <Text type="secondary" className="text-sm">
          {text ? text.split('T')[0] : '-'}
        </Text>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 240,
      render: (_, record) => (
        <Space wrap>
          <Button 
            type="link" 
            icon={<EyeOutlined />} 
            size="small"
            onClick={() => handleViewScript(record)}
          >
            查看
          </Button>
          
          {record.isUploaded ? (
            <>
              <Button 
                type="link" 
                icon={<DownloadOutlined />} 
                size="small"
                onClick={() => handleDownloadScript(record)}
              >
                下载
              </Button>
              <Button 
                type="link" 
                icon={<UploadOutlined />} 
                size="small"
                onClick={() => handleEditScript(record)}
              >
                重传
              </Button>
            </>
          ) : (
            <Button 
              type="link" 
              icon={<EditOutlined />} 
              size="small"
              onClick={() => handleEditScript(record)}
            >
              编辑
            </Button>
          )}
          
          <Button 
            type="link" 
            icon={<DeleteOutlined />} 
            size="small"
            danger
            onClick={() => handleDeleteScript(record)}
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
            onClick={handleRefresh}
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
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
              onSearch={handleSearch}
            />
            <Select
              value={selectedType}
              style={{ width: 120 }}
              onChange={handleTypeChange}
            >
              <Option value="all">全部类型</Option>
              <Option value="bash">Bash</Option>
              <Option value="powershell">PowerShell</Option>
              <Option value="cmd">CMD</Option>
              <Option value="python">Python</Option>
              <Option value="javascript">JavaScript</Option>
              <Option value="typescript">TypeScript</Option>
            </Select>
            {(searchKeyword || selectedType !== 'all') && (
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
                显示: {filteredScripts.length} / {scriptStats.total}
              </Text>
            </Space>
            <Space>
              <span>🐧</span>
              <Text>Bash: {scriptStats.bash}</Text>
            </Space>
            <Space>
              <span>💻</span>
              <Text>PowerShell: {scriptStats.powershell}</Text>
            </Space>
            <Space>
              <span>🐍</span>
              <Text>Python: {scriptStats.python}</Text>
            </Space>
          </div>
        </div>
      </Card>

      {/* 脚本列表 */}
      <Card className="shadow-lg">
        <Table
          columns={columns}
          dataSource={filteredScripts}
          rowKey="scriptId"
          loading={loading}
          pagination={{
            total: filteredScripts.length,
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
        onCancel={() => {
          setCreateModalVisible(false)
          form.resetFields()
          uploadForm.resetFields()
          setFileList([])
          setActiveTab('manual')
        }}
        footer={[
          <Button key="cancel" onClick={() => {
            setCreateModalVisible(false)
            form.resetFields()
            uploadForm.resetFields()
            setFileList([])
            setActiveTab('manual')
          }}>
            取消
          </Button>,
          <Button key="submit" type="primary" onClick={() => {
            if (activeTab === 'manual') {
              form.submit()
            } else {
              uploadForm.submit()
            }
          }}>
            {activeTab === 'manual' ? '创建脚本' : '上传脚本'}
          </Button>
        ]}
        width={800}
      >
        <Tabs activeKey={activeTab} onChange={setActiveTab} className="mt-4">
          <TabPane tab={<span><CodeOutlined />手动录入</span>} key="manual">
            <Form
              form={form}
              layout="vertical"
              onFinish={handleCreateScript}
            >
              <div className="grid grid-cols-2 gap-4">
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
                    <Option value="javascript">JavaScript</Option>
                    <Option value="typescript">TypeScript</Option>
                  </Select>
                </Form.Item>
                
                <Form.Item
                  name="encoding"
                  label="编码格式"
                  initialValue="UTF-8"
                >
                  <Select placeholder="选择编码格式">
                    <Option value="UTF-8">UTF-8</Option>
                    <Option value="GBK">GBK</Option>
                    <Option value="GB2312">GB2312</Option>
                    <Option value="ASCII">ASCII</Option>
                    <Option value="ISO-8859-1">ISO-8859-1</Option>
                  </Select>
                </Form.Item>
              </div>
              
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
          </TabPane>
          
          <TabPane tab={<span><UploadOutlined />文件上传</span>} key="upload">
            <Form
              form={uploadForm}
              layout="vertical"
              onFinish={handleUploadScript}
            >
              <Form.Item
                name="file"
                label="选择脚本文件"
                rules={[{ required: true, message: '请选择脚本文件' }]}
              >
                <Dragger {...uploadProps}>
                  <p className="ant-upload-drag-icon">
                    <InboxOutlined />
                  </p>
                  <p className="ant-upload-text">点击或拖拽文件到此区域上传</p>
                  <p className="ant-upload-hint">
                    支持 .sh, .ps1, .bat, .cmd, .py, .js, .ts 格式，文件大小不超过1MB
                  </p>
                </Dragger>
              </Form.Item>
              
              <div className="grid grid-cols-2 gap-4">
                <Form.Item
                  name="name"
                  label="脚本名称"
                  rules={[{ required: true, message: '请输入脚本名称' }]}
                >
                  <Input placeholder="输入脚本名称" />
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
                    <Option value="javascript">JavaScript</Option>
                    <Option value="typescript">TypeScript</Option>
                  </Select>
                </Form.Item>
                
                <Form.Item
                  name="filename"
                  label="文件名"
                  rules={[{ required: true, message: '文件名不能为空' }]}
                >
                  <Input placeholder="自动填充" disabled />
                </Form.Item>
                
                <Form.Item
                  name="description"
                  label="脚本描述"
                  rules={[{ required: true, message: '请输入脚本描述' }]}
                >
                  <Input placeholder="输入脚本描述" />
                </Form.Item>
              </div>
            </Form>
          </TabPane>
        </Tabs>
      </Modal>

      {/* 编辑脚本模态框 */}
      <Modal
        title="编辑脚本"
        open={editModalVisible}
        onCancel={() => {
          setEditModalVisible(false)
          editForm.resetFields()
          setSelectedScript(null)
        }}
        footer={[
          <Button key="cancel" onClick={() => {
            setEditModalVisible(false)
            editForm.resetFields()
            setSelectedScript(null)
          }}>
            取消
          </Button>,
          <Button key="submit" type="primary" onClick={() => editForm.submit()}>
            保存修改
          </Button>
        ]}
        width={800}
      >
        <Form
          form={editForm}
          layout="vertical"
          onFinish={handleUpdateScript}
          className="mt-4"
        >
          <div className="grid grid-cols-2 gap-4">
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
          </div>
          
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

      {/* 重新上传脚本模态框 */}
      <Modal
        title="重新上传脚本"
        open={reuploadModalVisible}
        onCancel={() => {
          setReuploadModalVisible(false)
          reuploadForm.resetFields()
          setReuploadFileList([])
          setSelectedScript(null)
        }}
        footer={[
          <Button key="cancel" onClick={() => {
            setReuploadModalVisible(false)
            reuploadForm.resetFields()
            setReuploadFileList([])
            setSelectedScript(null)
          }}>
            取消
          </Button>,
          <Button key="submit" type="primary" onClick={() => reuploadForm.submit()}>
            重新上传
          </Button>
        ]}
        width={800}
      >
        <Form
          form={reuploadForm}
          layout="vertical"
          onFinish={handleReuploadScript}
          className="mt-4"
        >
          <Form.Item
            name="file"
            label="选择新的脚本文件"
            rules={[{ required: true, message: '请选择脚本文件' }]}
          >
            <Dragger {...reuploadProps}>
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p className="ant-upload-text">点击或拖拽文件到此区域重新上传</p>
              <p className="ant-upload-hint">
                支持 .sh, .ps1, .bat, .cmd, .py, .js, .ts 格式，文件大小不超过1MB
              </p>
            </Dragger>
          </Form.Item>
          
          <div className="grid grid-cols-2 gap-4">
            <Form.Item
              name="name"
              label="脚本名称"
              rules={[{ required: true, message: '请输入脚本名称' }]}
            >
              <Input placeholder="输入脚本名称" />
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
                <Option value="javascript">JavaScript</Option>
                <Option value="typescript">TypeScript</Option>
              </Select>
            </Form.Item>
          </div>
          
          <Form.Item
            name="description"
            label="脚本描述"
            rules={[{ required: true, message: '请输入脚本描述' }]}
          >
            <Input placeholder="输入脚本描述" />
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