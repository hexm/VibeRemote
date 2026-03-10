import React, { useState, useEffect } from 'react'
import { Card, Table, Tag, Button, Space, Typography, Input, Modal, Form, message, Tooltip, Collapse, Select } from 'antd'
import {
  SettingOutlined,
  SearchOutlined,
  ReloadOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  SaveOutlined,
  EyeInvisibleOutlined,
  EyeOutlined,
} from '@ant-design/icons'
import api from '../services/auth'

const { Title, Text } = Typography
const { Search, TextArea } = Input
const { Panel } = Collapse
const { Option } = Select

const SystemSettings = () => {
  const [loading, setLoading] = useState(false)
  const [settings, setSettings] = useState([])
  const [settingsByCategory, setSettingsByCategory] = useState({})
  const [editModalVisible, setEditModalVisible] = useState(false)
  const [createModalVisible, setCreateModalVisible] = useState(false)
  const [selectedSetting, setSelectedSetting] = useState(null)
  const [searchKeyword, setSearchKeyword] = useState('')
  const [form] = Form.useForm()

  useEffect(() => {
    fetchSettings()
  }, [])

  const fetchSettings = async () => {
    setLoading(true)
    try {
      const response = await api.get('/web/system-settings/by-category')
      setSettingsByCategory(response)
      
      // 展平为列表用于搜索
      const allSettings = Object.values(response).flat()
      setSettings(allSettings)
    } catch (error) {
      console.error('获取系统参数失败:', error)
      message.error('获取系统参数失败')
    } finally {
      setLoading(false)
    }
  }

  const handleEdit = (setting) => {
    setSelectedSetting(setting)
    form.setFieldsValue({
      settingValue: setting.settingValue
    })
    setEditModalVisible(true)
  }

  const handleUpdate = async (values) => {
    try {
      await api.put(`/web/system-settings/${selectedSetting.id}`, {
        value: values.settingValue
      })
      message.success('参数更新成功')
      setEditModalVisible(false)
      form.resetFields()
      fetchSettings()
    } catch (error) {
      console.error('更新参数失败:', error)
      message.error('更新参数失败: ' + (error.response?.data?.message || error.message))
    }
  }

  const handleCreate = async (values) => {
    try {
      await api.post('/web/system-settings', values)
      message.success('参数创建成功')
      setCreateModalVisible(false)
      form.resetFields()
      fetchSettings()
    } catch (error) {
      console.error('创建参数失败:', error)
      message.error('创建参数失败: ' + (error.response?.data?.message || error.message))
    }
  }

  const handleDelete = (setting) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除参数"${setting.settingKey}"吗？`,
      okText: '确定',
      cancelText: '取消',
      okType: 'danger',
      async onOk() {
        try {
          await api.delete(`/web/system-settings/${setting.id}`)
          message.success('参数已删除')
          fetchSettings()
        } catch (error) {
          console.error('删除参数失败:', error)
          message.error('删除参数失败')
        }
      }
    })
  }

  const getTypeColor = (type) => {
    const colors = {
      'STRING': 'blue',
      'NUMBER': 'green',
      'BOOLEAN': 'orange',
      'JSON': 'purple',
    }
    return colors[type] || 'default'
  }

  const renderValue = (setting) => {
    if (setting.isEncrypted) {
      return (
        <Space>
          <EyeInvisibleOutlined />
          <Text type="secondary">******</Text>
        </Space>
      )
    }
    
    if (setting.settingType === 'BOOLEAN') {
      return <Tag color={setting.settingValue === 'true' ? 'success' : 'default'}>
        {setting.settingValue}
      </Tag>
    }
    
    if (setting.settingType === 'JSON') {
      return <Text code className="text-xs">{setting.settingValue?.substring(0, 50)}...</Text>
    }
    
    return <Text>{setting.settingValue}</Text>
  }

  const columns = [
    {
      title: '参数键',
      dataIndex: 'settingKey',
      key: 'settingKey',
      width: 200,
      render: (key) => <Text code strong>{key}</Text>,
    },
    {
      title: '参数值',
      key: 'value',
      width: 200,
      render: (_, record) => renderValue(record),
    },
    {
      title: '类型',
      dataIndex: 'settingType',
      key: 'settingType',
      width: 100,
      render: (type) => <Tag color={getTypeColor(type)}>{type}</Tag>,
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      render: (text) => <Text type="secondary">{text}</Text>,
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 160,
      render: (time) => <Text className="text-xs">{time ? new Date(time).toLocaleString('zh-CN') : '-'}</Text>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Tooltip title="编辑">
            <Button 
              type="text" 
              icon={<EditOutlined />} 
              size="small"
              onClick={() => handleEdit(record)}
              className="text-blue-500 hover:bg-blue-50"
            />
          </Tooltip>
          <Tooltip title="删除">
            <Button 
              type="text" 
              icon={<DeleteOutlined />} 
              size="small"
              danger
              onClick={() => handleDelete(record)}
            />
          </Tooltip>
        </Space>
      ),
    },
  ]

  const filteredSettings = searchKeyword
    ? settings.filter(s => 
        s.settingKey?.toLowerCase().includes(searchKeyword.toLowerCase()) ||
        s.description?.toLowerCase().includes(searchKeyword.toLowerCase())
      )
    : null

  return (
    <div className="space-y-6 animate-fade-in">
      {/* 页面标题 */}
      <div className="flex items-center justify-between">
        <div>
          <Title level={2} className="mb-2 flex items-center">
            <SettingOutlined className="mr-3 text-blue-500" />
            系统参数
          </Title>
          <Text type="secondary" className="text-base">
            配置和管理系统级参数
          </Text>
        </div>
        <Space>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setCreateModalVisible(true)}
            className="shadow-lg"
          >
            新增参数
          </Button>
          <Button
            icon={<ReloadOutlined />}
            loading={loading}
            onClick={fetchSettings}
          >
            刷新
          </Button>
        </Space>
      </div>

      {/* 搜索栏 */}
      <Card className="shadow-lg">
        <Search
          placeholder="搜索参数键或描述"
          allowClear
          value={searchKeyword}
          onChange={(e) => setSearchKeyword(e.target.value)}
          style={{ width: 400 }}
          prefix={<SearchOutlined className="text-gray-400" />}
        />
      </Card>

      {/* 参数列表 */}
      {searchKeyword ? (
        // 搜索模式：显示表格
        <Card className="shadow-lg">
          <Table
            columns={columns}
            dataSource={filteredSettings}
            loading={loading}
            rowKey="id"
            pagination={{
              pageSize: 20,
              showSizeChanger: true,
              showQuickJumper: true,
              showTotal: (total) => `共 ${total} 条记录`,
            }}
            scroll={{ x: 1200 }}
          />
        </Card>
      ) : (
        // 正常模式：按类别分组显示
        <Card className="shadow-lg">
          <Collapse defaultActiveKey={Object.keys(settingsByCategory)}>
            {Object.entries(settingsByCategory).map(([category, categorySettings]) => (
              <Panel 
                header={
                  <Space>
                    <Text strong>{category}</Text>
                    <Tag color="blue">{categorySettings.length} 个参数</Tag>
                  </Space>
                }
                key={category}
              >
                <Table
                  columns={columns}
                  dataSource={categorySettings}
                  loading={loading}
                  rowKey="id"
                  pagination={false}
                  size="small"
                />
              </Panel>
            ))}
          </Collapse>
        </Card>
      )}

      {/* 编辑参数模态框 */}
      <Modal
        title={`编辑参数 - ${selectedSetting?.settingKey}`}
        open={editModalVisible}
        onCancel={() => {
          setEditModalVisible(false)
          form.resetFields()
        }}
        footer={[
          <Button key="cancel" onClick={() => {
            setEditModalVisible(false)
            form.resetFields()
          }}>
            取消
          </Button>,
          <Button key="submit" type="primary" icon={<SaveOutlined />} onClick={() => form.submit()}>
            保存
          </Button>
        ]}
        width={600}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleUpdate}
          className="mt-4"
        >
          <div className="mb-4 p-3 bg-gray-50 rounded">
            <Space direction="vertical" size="small">
              <div><Text type="secondary">参数键：</Text><Text code>{selectedSetting?.settingKey}</Text></div>
              <div><Text type="secondary">类型：</Text><Tag color={getTypeColor(selectedSetting?.settingType)}>{selectedSetting?.settingType}</Tag></div>
              <div><Text type="secondary">描述：</Text><Text>{selectedSetting?.description}</Text></div>
            </Space>
          </div>

          <Form.Item
            name="settingValue"
            label="参数值"
            rules={[{ required: true, message: '请输入参数值' }]}
          >
            {selectedSetting?.settingType === 'JSON' ? (
              <TextArea rows={6} placeholder="输入JSON格式的参数值" />
            ) : selectedSetting?.settingType === 'BOOLEAN' ? (
              <Select>
                <Option value="true">true</Option>
                <Option value="false">false</Option>
              </Select>
            ) : selectedSetting?.settingType === 'NUMBER' ? (
              <Input type="number" placeholder="输入数字" />
            ) : (
              <Input placeholder="输入参数值" />
            )}
          </Form.Item>
        </Form>
      </Modal>

      {/* 创建参数模态框 */}
      <Modal
        title="新增系统参数"
        open={createModalVisible}
        onCancel={() => {
          setCreateModalVisible(false)
          form.resetFields()
        }}
        footer={[
          <Button key="cancel" onClick={() => {
            setCreateModalVisible(false)
            form.resetFields()
          }}>
            取消
          </Button>,
          <Button key="submit" type="primary" icon={<PlusOutlined />} onClick={() => form.submit()}>
            创建
          </Button>
        ]}
        width={600}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreate}
          className="mt-4"
          initialValues={{
            settingType: 'STRING',
            isEncrypted: false
          }}
        >
          <Form.Item
            name="settingKey"
            label="参数键"
            rules={[
              { required: true, message: '请输入参数键' },
              { pattern: /^[a-z0-9._]+$/, message: '只能包含小写字母、数字、点和下划线' }
            ]}
          >
            <Input placeholder="例如: system.name" />
          </Form.Item>

          <Form.Item
            name="category"
            label="参数类别"
            rules={[{ required: true, message: '请输入参数类别' }]}
          >
            <Select placeholder="选择或输入类别">
              <Option value="系统配置">系统配置</Option>
              <Option value="任务配置">任务配置</Option>
              <Option value="Agent配置">Agent配置</Option>
              <Option value="安全配置">安全配置</Option>
              <Option value="通知配置">通知配置</Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="settingType"
            label="参数类型"
            rules={[{ required: true, message: '请选择参数类型' }]}
          >
            <Select>
              <Option value="STRING">STRING</Option>
              <Option value="NUMBER">NUMBER</Option>
              <Option value="BOOLEAN">BOOLEAN</Option>
              <Option value="JSON">JSON</Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="settingValue"
            label="参数值"
            rules={[{ required: true, message: '请输入参数值' }]}
          >
            <Input placeholder="输入参数值" />
          </Form.Item>

          <Form.Item
            name="description"
            label="参数描述"
            rules={[{ required: true, message: '请输入参数描述' }]}
          >
            <Input placeholder="输入参数描述" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default SystemSettings
