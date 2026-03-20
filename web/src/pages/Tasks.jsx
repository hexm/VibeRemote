import React, { useState, useEffect, useRef } from 'react'
import { Card, Table, Tag, Button, Space, Typography, Input, Select, Modal, Form, message, Tooltip, Progress, Statistic, Row, Col, Switch, Badge, Radio, Tabs } from 'antd'
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
  PlayCircleOutlined,
  CodeOutlined,
  CloudDownloadOutlined,
  DesktopOutlined,
} from '@ant-design/icons'
import AgentSelectorModal from '../components/AgentSelectorModal'
import api from '../services/auth'
import scriptService from '../services/scriptService'
import { encryptText, decryptText, getSessionKey } from '../utils/crypto'
import { hasPermission } from '../utils/permission'

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
  const [statusFilter, setStatusFilter] = useState('all')
  const [taskTypeFilter, setTaskTypeFilter] = useState('all')
  const [createModalVisible, setCreateModalVisible] = useState(false)
  const [detailModalVisible, setDetailModalVisible] = useState(false)
  const [logModalVisible, setLogModalVisible] = useState(false)
  const [selectedTask, setSelectedTask] = useState(null)
  const [form] = Form.useForm()
  const [fileTransferForm] = Form.useForm()
  
  // 任务详情相关状态
  const [taskExecutions, setTaskExecutions] = useState([])
  const [detailLoading, setDetailLoading] = useState(false)
  
  // 日志相关状态
  const [selectedExecution, setSelectedExecution] = useState(null)
  const [logContent, setLogContent] = useState('')
  const [logTotalLines, setLogTotalLines] = useState(0)
  const [autoRefreshLogs, setAutoRefreshLogs] = useState(false)
  const logIntervalRef = useRef(null)
  
  // 脚本内容查看状态
  const [scriptModalVisible, setScriptModalVisible] = useState(false)
  const [selectedScript, setSelectedScript] = useState(null)
  
  // 在线代理数据
  const [onlineAgents, setOnlineAgents] = useState([])
  const [agentGroups, setAgentGroups] = useState([])
  const [selectionMode, setSelectionMode] = useState('manual') // manual or group

  // Agent 选择器弹窗
  const [agentSelectorOpen, setAgentSelectorOpen] = useState(false)
  const [selectedAgentIds, setSelectedAgentIds] = useState([])
  
  // 脚本相关状态
  const [scriptSource, setScriptSource] = useState('custom') // custom or existing
  const canCustomScript = hasPermission('task:custom-script')
  const [availableScripts, setAvailableScripts] = useState([])
  const [selectedScriptId, setSelectedScriptId] = useState(null)
  
  // 任务类型和文件传输相关状态
  const [taskType, setTaskType] = useState('SCRIPT')
  const [availableFiles, setAvailableFiles] = useState([])
  const [activeTaskTab, setActiveTaskTab] = useState('script') // 任务创建TAB状态

  // 获取可用脚本列表
  const fetchAvailableScripts = async () => {
    try {
      const scripts = await scriptService.getScriptsForTask()
      setAvailableScripts(scripts)
    } catch (error) {
      console.error('获取脚本列表失败:', error)
    }
  }

  // 获取可用文件列表
  const fetchAvailableFiles = async () => {
    try {
      const response = await api.get('/web/files/for-task')
      setAvailableFiles(response || [])
    } catch (error) {
      console.error('获取文件列表失败:', error)
    }
  }

  // 处理脚本选择（for-task 返回的 content 可能是加密的）
  const handleScriptSelect = async (scriptId) => {
    const script = availableScripts.find(s => s.scriptId === scriptId)
    if (script) {
      let content = script.content || ''
      const encKey = getSessionKey()
      if (encKey && content) {
        try { content = await decryptText(content, encKey) } catch (e) { console.warn('[crypto] 脚本内容解密失败:', e) }
      }
      setSelectedScriptId(scriptId)
      form.setFieldsValue({
        scriptLang: script.type,
        scriptContent: content,
      })
    }
  }

  // 获取在线代理列表
  const fetchOnlineAgents = async () => {
    try {
      const response = await api.get('/web/agents')
      const agents = response.content || response || []
      setOnlineAgents(agents)
    } catch (error) {
      console.error('获取代理列表失败:', error)
      message.error('获取代理列表失败: ' + (error.response?.data?.message || error.message))
    }
  }

  // 获取Agent分组列表
  const fetchAgentGroups = async () => {
    try {
      const response = await api.get('/web/agent-groups')
      setAgentGroups(response.content || [])
    } catch (error) {
      console.error('获取分组列表失败:', error)
    }
  }

  // 当选择分组时，自动填充Agent列表（保留兼容，AgentSelectorModal 内部处理）
  const handleGroupChange = async (groupId) => {
    if (!groupId) {
      form.setFieldsValue({ selectedAgents: [] })
      fileTransferForm.setFieldsValue({ selectedAgents: [] })
      return
    }
    
    try {
      const response = await api.get(`/web/agent-groups/${groupId}`)
      const agentIds = response.agents?.map(a => a.agentId) || []
      form.setFieldsValue({ selectedAgents: agentIds })
      fileTransferForm.setFieldsValue({ selectedAgents: agentIds })
    } catch (error) {
      message.error('获取分组成员失败')
    }
  }

  // 确认 agent 选择
  const handleAgentSelectorConfirm = (agentIds) => {
    setSelectedAgentIds(agentIds)
    form.setFieldsValue({ selectedAgents: agentIds })
    fileTransferForm.setFieldsValue({ selectedAgents: agentIds })
    setAgentSelectorOpen(false)
  }

  // 获取已选 agent 的预览文本
  const getSelectedAgentsPreview = () => {
    if (selectedAgentIds.length === 0) return null
    const names = selectedAgentIds
      .slice(0, 3)
      .map(id => onlineAgents.find(a => a.agentId === id)?.hostname || id.substring(0, 8))
    const suffix = selectedAgentIds.length > 3 ? ` 等 ${selectedAgentIds.length} 台` : ` 共 ${selectedAgentIds.length} 台`
    return names.join('、') + suffix
  }

  // 获取任务列表
  const fetchTasks = async () => {
    setLoading(true)
    try {
      const params = {
        page: currentPage - 1,
        size: pageSize
      }
      
      if (statusFilter && statusFilter !== 'all') {
        params.status = statusFilter
      }
      
      if (taskTypeFilter && taskTypeFilter !== 'all') {
        params.taskType = taskTypeFilter
      }
      
      const response = await api.get('/web/tasks', { params })
      
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
    fetchAgentGroups()
    fetchAvailableScripts()
    fetchAvailableFiles()
    
    const handleScriptsChange = () => {
      fetchAvailableScripts()
    }
    
    scriptService.addListener(handleScriptsChange)
    
    return () => {
      scriptService.removeListener(handleScriptsChange)
    }
  }, [currentPage, pageSize, statusFilter, taskTypeFilter])

  // 处理URL参数，自动打开任务详情
  useEffect(() => {
    if (tasks.length === 0) return // 等待任务列表加载完成
    
    // 从标准 URL 查询参数获取
    const urlParams = new URLSearchParams(window.location.search)
    const autoOpen = urlParams.get('autoOpen')
    const taskName = urlParams.get('taskName')
    const taskId = urlParams.get('taskId')
    
    if (autoOpen === 'true' && taskName) {
      // 根据任务名查找任务
      const task = tasks.find(t => t.taskName === taskName)
      if (task) {
        handleViewDetail(task)
        // 清除URL参数，避免重复打开
        window.history.replaceState({}, '', window.location.pathname)
      } else {
        message.info(`正在查找任务: ${taskName}，请稍候...`)
      }
    }
  }, [tasks]) // 依赖tasks数组，当任务列表更新时执行

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
  const getTaskStatusColor = (status) => {
    const map = {
      'DRAFT': 'default',
      'PENDING': 'blue',
      'RUNNING': 'processing',
      'SUCCESS': 'success',
      'FAILED': 'error',
      'PARTIAL_SUCCESS': 'warning',
      'STOPPED': 'default',
      'CANCELLED': 'default',
    }
    return map[status] || 'default'
  }

  const getTaskStatusText = (status) => {
    const map = {
      'DRAFT': '草稿',
      'PENDING': '待执行',
      'RUNNING': '执行中',
      'SUCCESS': '成功',
      'FAILED': '失败',
      'PARTIAL_SUCCESS': '部分成功',
      'STOPPED': '已停止',
      'CANCELLED': '已取消',
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
  // 创建任务
  const handleCreateTask = async (values) => {
    try {
      // 根据当前TAB获取正确的表单值
      let formValues = values
      if (!formValues || Object.keys(formValues).length === 0) {
        if (activeTaskTab === 'script') {
          formValues = await form.validateFields()
        } else {
          formValues = await fileTransferForm.validateFields()
        }
      }
      
      if (!formValues.selectedAgents || formValues.selectedAgents.length === 0) {
        if (selectedAgentIds.length === 0) {
          message.error('请选择至少一个客户端')
          return
        }
      }
      
      // 优先使用 selectedAgentIds（来自 AgentSelectorModal），兼容 form 字段
      const agentIds = selectedAgentIds.length > 0 ? selectedAgentIds : (formValues.selectedAgents || [])
      if (agentIds.length === 0) {
        message.error('请选择至少一个执行节点')
        return
      }
      
      const currentTaskType = activeTaskTab === 'file-transfer' ? 'FILE_TRANSFER' : 'SCRIPT'
      
      if (currentTaskType === 'FILE_TRANSFER') {
        const fileTransferRequest = {
          agentIds,
          taskName: formValues.taskName,
          fileId: formValues.fileId,
          targetPath: formValues.targetPath,
          timeoutSec: formValues.timeoutSec || 300,
          overwriteExisting: formValues.overwriteExisting || false,
          verifyChecksum: formValues.verifyChecksum !== false
        }
        
        await api.post('/web/tasks/file-transfer/create', fileTransferRequest)
        message.success('文件传输任务创建成功')
      } else {
        const taskSpec = {
          scriptLang: formValues.scriptLang || 'shell',
          scriptContent: formValues.scriptContent,
          scriptId: scriptSource === 'existing' ? (selectedScriptId || null) : null,
          timeoutSec: formValues.timeoutSec || 300
        }
        
        // 如果有会话密钥，加密脚本内容
        const encKey = getSessionKey()
        if (encKey && taskSpec.scriptContent) {
          try {
            taskSpec.scriptContent = await encryptText(taskSpec.scriptContent, encKey)
          } catch (e) {
            console.warn('[crypto] 脚本加密失败，使用明文:', e)
          }
        }
        
        const params = new URLSearchParams()
        agentIds.forEach(id => params.append('agentIds', id))
        params.append('taskName', formValues.taskName)
        const autoStart = formValues.autoStart === undefined ? true : formValues.autoStart
        params.append('autoStart', autoStart)
        
        await api.post(`/web/tasks/create?${params.toString()}`, taskSpec)
        message.success(autoStart ? '任务创建成功' : '任务创建成功（草稿状态）')
      }
      
      setCreateModalVisible(false)
      form.resetFields()
      fileTransferForm.resetFields()
      setSelectedAgentIds([])
      setSelectedScriptId(null)
      setActiveTaskTab('script')
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

  // 查看脚本内容
  const handleViewScript = (task) => {
    setSelectedScript(task)
    setScriptModalVisible(true)
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
      
      let content = response.content || ''
      // 如果服务器返回加密内容，解密
      if (response.encrypted && content) {
        const encKey = getSessionKey()
        if (encKey) {
          try {
            content = await decryptText(content, encKey)
          } catch (e) {
            console.warn('[crypto] 日志解密失败，显示原始内容:', e)
            content = '[日志解密失败，请重新登录后查看]'
          }
        } else {
          // 没有 session key，提示用户重新登录
          content = '[日志已加密，session 密钥已过期，请重新登录后查看]'
        }
      }
      
      setLogContent(content)
      setLogTotalLines(response.totalLines || 0)
    } catch (error) {
      console.error('获取日志失败:', error)
      const errorMsg = error.response?.data?.message || error.message || '未知错误'
      message.error(`获取日志失败: ${errorMsg}`)
    }
  }

  // 清空日志显示
  const clearLogs = () => {
    setLogContent('')
    setLogTotalLines(0)
  }

  // 下载执行日志
  const downloadExecutionLog = async (execution) => {
    try {
      const token = localStorage.getItem('token')
      const response = await fetch(
        `${api.defaults.baseURL}/web/tasks/executions/${execution.id}/download`,
        {
          headers: {
            'Authorization': `Bearer ${token}`
          }
        }
      )
      
      if (!response.ok) {
        const errorText = await response.text()
        throw new Error(errorText || `HTTP ${response.status}`)
      }
      
      const blob = await response.blob()
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `execution_${execution.id}_${execution.executionNumber}.log`
      document.body.appendChild(a)
      a.click()
      window.URL.revokeObjectURL(url)
      document.body.removeChild(a)
      
      message.success('日志下载成功')
    } catch (error) {
      console.error('下载日志失败:', error)
      message.error(`下载日志失败: ${error.message}`)
    }
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

  // 启动草稿任务
  const handleStartTask = async (task) => {
    Modal.confirm({
      title: '启动任务',
      content: `确定要启动任务"${task.taskName || task.taskId}"吗？任务将在创建时选择的所有代理节点上执行。`,
      okText: '启动',
      cancelText: '取消',
      async onOk() {
        try {
          const response = await api.post(`/web/tasks/${task.taskId}/start`)
          message.success(`任务已启动，创建了${response.executionCount}个执行实例`)
          fetchTasks()
          if (detailModalVisible && selectedTask?.taskId === task.taskId) {
            handleViewDetail(task)
          }
        } catch (error) {
          console.error('启动任务失败:', error)
          message.error('启动任务失败: ' + (error.response?.data?.message || error.message))
          return Promise.reject()
        }
      }
    })
  }
  // 停止任务
  const handleStopTask = async (task) => {
    Modal.confirm({
      title: '确认停止',
      content: `确定要停止任务"${task.taskName || task.taskId}"吗？这将取消所有未完成的执行实例。`,
      okText: '确定',
      cancelText: '取消',
      async onOk() {
        try {
          const response = await api.post(`/web/tasks/${task.taskId}/stop`)
          message.success(`任务已停止，取消了${response.cancelledCount}个执行实例`)
          fetchTasks()
          if (detailModalVisible && selectedTask?.taskId === task.taskId) {
            handleViewDetail(task)
          }
        } catch (error) {
          console.error('停止任务失败:', error)
          message.error('停止任务失败: ' + (error.response?.data?.message || error.message))
        }
      }
    })
  }

  // 任务表格列定义
  const columns = [
    {
      title: '任务名称',
      key: 'taskName',
      width: 160,
      render: (_, record) => (
        <Text strong>{record.taskName || '未命名任务'}</Text>
      ),
    },
    {
      title: '类型',
      dataIndex: 'taskType',
      key: 'taskType',
      width: 80,
      render: (taskType) => (
        <Tag color={taskType === 'FILE_TRANSFER' ? 'blue' : 'purple'}>
          {taskType === 'FILE_TRANSFER' ? '文件传输' : '脚本执行'}
        </Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'taskStatus',
      key: 'taskStatus',
      width: 90,
      render: (status) => {
        const statusIcons = {
          'DRAFT': <FileTextOutlined />,
          'PENDING': <ClockCircleOutlined />,
          'RUNNING': <SyncOutlined spin />,
          'SUCCESS': <CheckCircleOutlined />,
          'FAILED': <ExclamationCircleOutlined />,
          'PARTIAL_SUCCESS': <ExclamationCircleOutlined />,
          'STOPPED': <StopOutlined />,
          'CANCELLED': <StopOutlined />,
        }
        return (
          <Tag color={getTaskStatusColor(status)} icon={statusIcons[status]}>
            {getTaskStatusText(status)}
          </Tag>
        )
      },
    },
    {
      title: '节点数',
      dataIndex: 'targetAgentCount',
      key: 'targetAgentCount',
      width: 80,
      render: (count) => (
        <Tag color="blue" icon={<TeamOutlined />}>
          {count || 0} 个
        </Tag>
      ),
    },
    {
      title: '进度',
      key: 'progress',
      width: 120,
      render: (_, record) => (
        <div>
          <Text className="text-sm">
            {record.completedExecutions || 0}/{record.targetAgentCount || 0}
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
      title: '统计',
      key: 'stats',
      width: 140,
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
      title: '脚本',
      dataIndex: 'scriptLang',
      key: 'scriptLang',
      width: 80,
      render: (lang, record) => {
        // 文件传输任务不显示脚本类型
        if (record.taskType === 'FILE_TRANSFER') {
          return '-'
        }
        return <Tag color="purple">{lang || 'shell'}</Tag>
      },
    },
    {
      title: '创建者',
      dataIndex: 'createdBy',
      key: 'createdBy',
      width: 80,
      render: (createdBy) => <Text>{createdBy || 'admin'}</Text>,
    },
    {
      title: '次数',
      dataIndex: 'executionCount',
      key: 'executionCount',
      width: 80,
      render: (count) => <Tag color="cyan">第 {count || 1} 次</Tag>,
    },
    {
      title: '开始时间',
      dataIndex: 'startedAt',
      key: 'startedAt',
      width: 120,
      render: (time) => <Text className="text-xs">{time ? formatDateTime(time) : '-'}</Text>,
    },
    {
      title: '结束时间',
      dataIndex: 'finishedAt',
      key: 'finishedAt',
      width: 120,
      render: (time) => <Text className="text-xs">{time ? formatDateTime(time) : '-'}</Text>,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 120,
      render: (time) => <Text className="text-xs">{formatDateTime(time)}</Text>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 180,
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
          
          {record.taskStatus === 'DRAFT' && (
            <Button 
              type="link" 
              icon={<PlayCircleOutlined />} 
              size="small"
              onClick={() => handleStartTask(record)}
            >
              启动
            </Button>
          )}
          
          {(record.taskStatus === 'PENDING' || record.taskStatus === 'RUNNING') && (
            <Button 
              type="link" 
              icon={<StopOutlined />} 
              size="small"
              danger
              onClick={() => handleStopTask(record)}
            >
              停止
            </Button>
          )}
          
          {(record.taskStatus === 'FAILED' || 
            record.taskStatus === 'PARTIAL_SUCCESS' || record.taskStatus === 'STOPPED' ||
            record.taskStatus === 'CANCELLED') && (
            <Button 
              type="link" 
              icon={<RedoOutlined />} 
              size="small"
              onClick={() => handleRestartTask(record)}
            >
              重启
            </Button>
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
              setScriptSource(canCustomScript ? 'custom' : 'existing')
              setActiveTaskTab('script')
              setSelectedAgentIds([])
      setSelectedScriptId(null)
              form.resetFields()
              fileTransferForm.resetFields()
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
        <div className="flex flex-col sm:flex-row gap-4 items-start sm:items-center justify-between mb-4">
          <Space wrap>
            <Search
              placeholder="搜索任务名称或ID"
              allowClear
              style={{ width: 250 }}
              prefix={<SearchOutlined className="text-gray-400" />}
            />
            <Select
              value={statusFilter}
              onChange={(value) => {
                setStatusFilter(value)
                setCurrentPage(1)
              }}
              style={{ width: 120 }}
            >
              <Option value="all">全部状态</Option>
              <Option value="DRAFT">草稿</Option>
              <Option value="PENDING">待执行</Option>
              <Option value="RUNNING">执行中</Option>
              <Option value="SUCCESS">成功</Option>
              <Option value="FAILED">失败</Option>
              <Option value="PARTIAL_SUCCESS">部分成功</Option>
              <Option value="STOPPED">已停止</Option>
              <Option value="CANCELLED">已取消</Option>
            </Select>
            <Select
              value={taskTypeFilter}
              onChange={(value) => {
                setTaskTypeFilter(value)
                setCurrentPage(1)
              }}
              style={{ width: 120 }}
            >
              <Option value="all">全部类型</Option>
              <Option value="SCRIPT">脚本执行</Option>
              <Option value="FILE_TRANSFER">文件传输</Option>
            </Select>
          </Space>
          
          <div className="flex items-center space-x-4 text-sm">
            <Space>
              <div className="w-3 h-3 bg-gray-400 rounded-full"></div>
              <Text>草稿: {tasks.filter(t => t.taskStatus === 'DRAFT').length}</Text>
            </Space>
            <Space>
              <div className="w-3 h-3 bg-blue-500 rounded-full"></div>
              <Text>执行中: {tasks.filter(t => t.taskStatus === 'RUNNING').length}</Text>
            </Space>
            <Space>
              <div className="w-3 h-3 bg-green-500 rounded-full"></div>
              <Text>成功: {tasks.filter(t => t.taskStatus === 'SUCCESS').length}</Text>
            </Space>
            <Space>
              <div className="w-3 h-3 bg-red-500 rounded-full"></div>
              <Text>失败: {tasks.filter(t => t.taskStatus === 'FAILED').length}</Text>
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
          scroll={{ x: 1420 }}
        />
      </Card>
      {/* 创建任务模态框 */}
      <Modal
        title="创建新任务"
        open={createModalVisible}
        onCancel={() => {
          setCreateModalVisible(false)
          form.resetFields()
          fileTransferForm.resetFields()
          setSelectedAgentIds([])
      setSelectedScriptId(null)
          setActiveTaskTab('script')
        }}
        footer={[
          <Button key="cancel" onClick={() => {
            setCreateModalVisible(false)
            form.resetFields()
            fileTransferForm.resetFields()
            setSelectedAgentIds([])
      setSelectedScriptId(null)
            setActiveTaskTab('script')
          }}>
            取消
          </Button>,
          <Button key="submit" type="primary" onClick={() => {
            if (activeTaskTab === 'script') {
              form.submit()
            } else {
              fileTransferForm.submit()
            }
          }}>
            {activeTaskTab === 'script' ? '创建脚本任务' : '创建文件传输任务'}
          </Button>
        ]}
        width={800}
      >
        <Tabs 
          activeKey={activeTaskTab} 
          onChange={setActiveTaskTab} 
          className="mt-4"
          items={[
            {
              key: 'script',
              label: <span><CodeOutlined />脚本执行</span>,
              children: (
                <Form
                  form={form}
                  layout="vertical"
                  onFinish={handleCreateTask}
                  initialValues={{
                    timeoutSec: 300,
                    scriptLang: 'shell',
                    autoStart: true,
                    taskName: `脚本任务_${new Date().toLocaleString('zh-CN', { 
                      year: 'numeric', 
                      month: '2-digit', 
                      day: '2-digit', 
                      hour: '2-digit', 
                      minute: '2-digit',
                      second: '2-digit',
                      hour12: false 
                    }).replace(/\//g, '').replace(/:/g, '').replace(/\s/g, '_')}`
                  }}
                >
              <div style={{ display: 'flex', gap: 12, alignItems: 'flex-end', marginBottom: 16 }}>
                <Form.Item
                  name="taskName"
                  label="任务名称"
                  rules={[{ required: true, message: '请输入任务名称' }]}
                  style={{ flex: 1, marginBottom: 0 }}
                >
                  <Input placeholder="输入任务名称" />
                </Form.Item>

                <Form.Item
                  name="timeoutSec"
                  label="超时（秒）"
                  style={{ width: 110, marginBottom: 0 }}
                >
                  <Input type="number" min={1} placeholder="300" />
                </Form.Item>

                <Form.Item
                  name="autoStart"
                  label="启动"
                  valuePropName="checked"
                  style={{ marginBottom: 0 }}
                >
                  <Switch checkedChildren="立即" unCheckedChildren="草稿" />
                </Form.Item>
              </div>

              {/* 目标节点选择入口 */}
              <Form.Item label="目标节点" required>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <Button
                    icon={<DesktopOutlined />}
                    onClick={() => setAgentSelectorOpen(true)}
                  >
                    选择节点
                  </Button>
                  {selectedAgentIds.length > 0 ? (
                    <span style={{ color: '#52c41a', fontSize: 13 }}>
                      已选 <strong>{selectedAgentIds.length}</strong> 台：{getSelectedAgentsPreview()}
                    </span>
                  ) : (
                    <span style={{ color: '#999', fontSize: 13 }}>未选择节点</span>
                  )}
                </div>
              </Form.Item>

              {/* 脚本来源 + 类型/脚本名称同行 */}
              <Form.Item label="脚本来源" style={{ marginBottom: 8 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
                  <Radio.Group
                    value={scriptSource}
                    onChange={(e) => setScriptSource(e.target.value)}
                  >
                    <Tooltip title={!canCustomScript ? '当前账号无自定义脚本权限' : ''}>
                      <Radio value="custom" disabled={!canCustomScript}>自定义</Radio>
                    </Tooltip>
                    <Radio value="existing">已有脚本</Radio>
                  </Radio.Group>

                  {scriptSource === 'existing' ? (
                    <Select
                      placeholder="选择脚本"
                      onChange={handleScriptSelect}
                      allowClear
                      style={{ flex: 1, minWidth: 200 }}
                      onClear={() => form.setFieldsValue({ scriptLang: 'shell', scriptContent: '' })}
                    >
                      {availableScripts.map(script => (
                        <Option key={script.scriptId} value={script.scriptId} disabled={script.isUploaded && script.fileExists === false}>
                          <Space size={4}>
                            <Tag color="purple" style={{ fontSize: 11 }}>{script.type}</Tag>
                            {script.name}
                            {script.filename && <Text type="secondary" style={{ fontSize: 11 }}>{script.filename}</Text>}
                            {script.isUploaded && script.fileExists === false && (
                              <Tag color="red" style={{ fontSize: 11 }}>文件缺失</Tag>
                            )}
                          </Space>
                        </Option>
                      ))}
                    </Select>
                  ) : (
                    <Form.Item
                      name="scriptLang"
                      rules={[{ required: true, message: '请选择脚本类型' }]}
                      style={{ marginBottom: 0 }}
                    >
                      <Select placeholder="脚本类型" style={{ width: 140 }}>
                        <Option value="shell">Shell</Option>
                        <Option value="python">Python</Option>
                        <Option value="javascript">JavaScript</Option>
                      </Select>
                    </Form.Item>
                  )}
                </div>
              </Form.Item>

              <Form.Item
                name="scriptContent"
                label="脚本内容"
                rules={[{ required: true, message: '请输入脚本内容' }]}
              >
                <TextArea
                  rows={6}
                  placeholder="输入要执行的脚本内容..."
                  className="font-mono"
                  disabled={scriptSource === 'existing'}
                  style={{ resize: 'vertical' }}
                />
              </Form.Item>

                </Form>
              )
            },
            {
              key: 'file-transfer',
              label: <span><CloudDownloadOutlined />文件传输</span>,
              children: (
                <Form
                  form={fileTransferForm}
                  layout="vertical"
                  onFinish={handleCreateTask}
                  initialValues={{
                    timeoutSec: 300,
                    overwriteExisting: false,
                    verifyChecksum: true,
                    taskName: `文件传输任务_${new Date().toLocaleString('zh-CN', { 
                      year: 'numeric', 
                      month: '2-digit', 
                      day: '2-digit', 
                      hour: '2-digit', 
                      minute: '2-digit',
                      second: '2-digit',
                      hour12: false 
                    }).replace(/\//g, '').replace(/:/g, '').replace(/\s/g, '_')}`
                  }}
                >
              <div className="grid grid-cols-2 gap-4">
                <Form.Item
                  name="taskName"
                  label="任务名称"
                  rules={[{ required: true, message: '请输入任务名称' }]}
                >
                  <Input placeholder="输入任务名称" />
                </Form.Item>
                
                <Form.Item
                  name="timeoutSec"
                  label="超时时间（秒）"
                >
                  <Input type="number" min={1} placeholder="默认300秒" />
                </Form.Item>
              </div>

              {/* 目标节点选择入口 */}
              <Form.Item label="目标节点" required>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <Button
                    icon={<DesktopOutlined />}
                    onClick={() => setAgentSelectorOpen(true)}
                  >
                    选择节点
                  </Button>
                  {selectedAgentIds.length > 0 ? (
                    <span style={{ color: '#52c41a', fontSize: 13 }}>
                      已选 <strong>{selectedAgentIds.length}</strong> 台：{getSelectedAgentsPreview()}
                    </span>
                  ) : (
                    <span style={{ color: '#999', fontSize: 13 }}>未选择节点</span>
                  )}
                </div>
              </Form.Item>
              <div className="grid grid-cols-2 gap-4">
                <Form.Item
                  name="fileId"
                  label="选择文件"
                  rules={[{ required: true, message: '请选择要传输的文件' }]}
                >
                  <Select placeholder="选择要传输的文件">
                    {availableFiles.map(file => (
                      <Option key={file.fileId} value={file.fileId}>
                        <Space>
                          <Tag color="blue">{file.category}</Tag>
                          {file.name}
                          <Text type="secondary">({file.sizeDisplay})</Text>
                        </Space>
                      </Option>
                    ))}
                  </Select>
                </Form.Item>
                
                <Form.Item
                  name="targetPath"
                  label="目标路径"
                  rules={[{ required: true, message: '请输入目标路径' }]}
                >
                  <Input placeholder="例如: /tmp/myfile.txt" />
                </Form.Item>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <Form.Item
                  name="overwriteExisting"
                  label="覆盖选项"
                  valuePropName="checked"
                >
                  <Switch checkedChildren="覆盖已存在文件" unCheckedChildren="跳过已存在文件" />
                </Form.Item>
                
                <Form.Item
                  name="verifyChecksum"
                  label="校验选项"
                  valuePropName="checked"
                >
                  <Switch checkedChildren="验证文件完整性" unCheckedChildren="跳过完整性验证" />
                </Form.Item>
              </div>
                </Form>
              )
            }
          ]}
        />
      </Modal>

      {/* Agent 选择器弹窗 */}
      <AgentSelectorModal
        open={agentSelectorOpen}
        onConfirm={handleAgentSelectorConfirm}
        onCancel={() => setAgentSelectorOpen(false)}
        agents={onlineAgents}
        groups={agentGroups}
        initialSelected={selectedAgentIds}
      />

      {/* 任务详情模态框 */}
      <Modal
        title={`任务详情 - ${selectedTask?.taskName || selectedTask?.taskId}`}
        open={detailModalVisible}
        onCancel={() => setDetailModalVisible(false)}
        footer={[
          <Button 
            key="refresh" 
            icon={<ReloadOutlined />} 
            onClick={() => handleViewDetail(selectedTask)}
            loading={detailLoading}
          >
            刷新
          </Button>,
          <Button key="close" onClick={() => setDetailModalVisible(false)}>
            关闭
          </Button>
        ]}
        width={1200}
      >
        {selectedTask && (
          <div className="space-y-4">
            {/* 任务信息卡片 */}
            <Card title="任务信息">
              <Row gutter={16}>
                <Col span={12}>
                  <div className="space-y-2">
                    <div><Text strong>任务名称:</Text> {selectedTask.taskName || '未命名任务'}</div>
                    <div><Text strong>任务ID:</Text> <Text code>{selectedTask.taskId}</Text></div>
                    <div><Text strong>任务类型:</Text> 
                      <Tag color={selectedTask.taskType === 'FILE_TRANSFER' ? 'blue' : 'purple'} className="ml-2">
                        {selectedTask.taskType === 'FILE_TRANSFER' ? '文件传输' : '脚本执行'}
                      </Tag>
                    </div>
                    <div><Text strong>创建者:</Text> {selectedTask.createdBy || 'admin'}</div>
                  </div>
                </Col>
                <Col span={12}>
                  <div className="space-y-2">
                    <div><Text strong>创建时间:</Text> {formatDateTime(selectedTask.createdAt)}</div>
                    {selectedTask.taskType === 'FILE_TRANSFER' ? (
                      <>
                        <div><Text strong>源文件:</Text> {selectedTask.fileName || selectedTask.fileId || '-'}</div>
                        <div><Text strong>目标路径:</Text> <Text code>{selectedTask.targetPath || '-'}</Text></div>
                        <div><Text strong>超时时间:</Text> {selectedTask.timeoutSec || 300}秒</div>
                        <div><Text strong>覆盖模式:</Text> {selectedTask.overwriteExisting ? '覆盖已存在文件' : '跳过已存在文件'}</div>
                        <div><Text strong>校验模式:</Text> {selectedTask.verifyChecksum !== false ? '验证文件完整性' : '跳过完整性验证'}</div>
                      </>
                    ) : (
                      <>
                        <div><Text strong>脚本类型:</Text> 
                          <Tag color="purple" className="ml-2">{selectedTask.scriptLang || 'shell'}</Tag>
                        </div>
                        <div><Text strong>超时时间:</Text> {selectedTask.timeoutSec || 300}秒</div>
                      </>
                    )}
                  </div>
                </Col>
              </Row>
            </Card>
            
            {/* 执行统计卡片 */}
            <Card title="执行统计">
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
                  status={selectedTask.taskStatus === 'FAILED' ? 'exception' : 'normal'}
                  strokeColor={{
                    '0%': '#108ee9',
                    '100%': '#87d068',
                  }}
                />
              </div>
            </Card>
            <Card title="执行实例">
              <Table
                dataSource={taskExecutions}
                loading={detailLoading}
                rowKey="id"
                size="small"
                pagination={false}
                scroll={{ y: 400 }}
                columns={[
                  {
                    title: '代理节点',
                    dataIndex: 'agentId',
                    key: 'agentId',
                    width: 140,
                    render: (agentId) => (
                      <div>
                        <Text strong>{getAgentName(agentId)}</Text>
                        <br />
                        <Text code className="text-xs">{agentId?.substring(0, 8)}...</Text>
                      </div>
                    ),
                  },
                  {
                    title: '执行状态',
                    dataIndex: 'status',
                    key: 'status',
                    width: 80,
                    render: (status) => (
                      <Tag color={getExecutionStatusColor(status)}>
                        {getExecutionStatusText(status)}
                      </Tag>
                    ),
                  },
                  {
                    title: '执行次数',
                    dataIndex: 'executionNumber',
                    key: 'executionNumber',
                    width: 70,
                    render: (num) => <Tag color="cyan">第 {num} 次</Tag>,
                  },
                  {
                    title: '开始时间',
                    dataIndex: 'startedAt',
                    key: 'startedAt',
                    width: 120,
                    render: (time) => <Text className="text-xs">{formatDateTime(time)}</Text>,
                  },
                  {
                    title: '结束时间',
                    dataIndex: 'finishedAt',
                    key: 'finishedAt',
                    width: 120,
                    render: (time) => <Text className="text-xs">{formatDateTime(time)}</Text>,
                  },
                  {
                    title: '耗时',
                    key: 'duration',
                    width: 80,
                    render: (_, record) => {
                      if (record.startedAt && record.finishedAt) {
                        const duration = new Date(record.finishedAt) - new Date(record.startedAt)
                        return <Text className="text-xs">{formatDuration(duration)}</Text>
                      }
                      return '-'
                    },
                  },
                  {
                    title: '退出码',
                    dataIndex: 'exitCode',
                    key: 'exitCode',
                    width: 60,
                    render: (code) => {
                      if (code === null || code === undefined) return '-'
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
                    width: 140,
                    render: (_, record) => (
                      <Space size="small">
                        <Button 
                          type="link" 
                          icon={<EyeOutlined />} 
                          size="small"
                          onClick={() => handleViewLog(record)}
                          disabled={!record.logFilePath}
                        >
                          日志
                        </Button>
                        {selectedTask?.taskType === 'SCRIPT' && (
                          <Button 
                            type="link" 
                            icon={<CodeOutlined />} 
                            size="small"
                            onClick={() => handleViewScript(selectedTask)}
                          >
                            脚本
                          </Button>
                        )}
                        {(record.status === 'PENDING' || record.status === 'PULLED' || record.status === 'RUNNING') && (
                          <Button 
                            type="link" 
                            icon={<StopOutlined />} 
                            size="small"
                            danger
                            onClick={() => handleCancelExecution(record)}
                          >
                            取消
                          </Button>
                        )}
                      </Space>
                    ),
                  },
                ]}
              />
            </Card>
          </div>
        )}
      </Modal>
      {/* 日志查看模态框 */}
      <Modal
        title={`执行日志 - ${selectedExecution?.agentId ? getAgentName(selectedExecution.agentId) : ''}`}
        open={logModalVisible}
        onCancel={() => {
          setLogModalVisible(false)
          setAutoRefreshLogs(false)
        }}
        footer={[
          <div key="controls" className="flex items-center justify-between w-full">
            <div className="flex items-center space-x-2">
              <Switch
                checked={autoRefreshLogs}
                onChange={setAutoRefreshLogs}
                checkedChildren="自动刷新"
                unCheckedChildren="手动刷新"
                size="small"
              />
              <Text type="secondary" className="text-xs">
                {logTotalLines > 0 && `共 ${logTotalLines} 行`}
              </Text>
            </div>
            <Space>
              <Button 
                icon={<DownloadOutlined />} 
                size="small"
                onClick={() => selectedExecution && downloadExecutionLog(selectedExecution)}
                disabled={!selectedExecution?.logFilePath}
              >
                下载日志
              </Button>
              <Button 
                icon={<ClearOutlined />} 
                size="small"
                onClick={clearLogs}
              >
                清空显示
              </Button>
              <Button 
                icon={<ReloadOutlined />} 
                size="small"
                onClick={() => refreshLogs()}
              >
                刷新
              </Button>
              <Button onClick={() => {
                setLogModalVisible(false)
                setAutoRefreshLogs(false)
              }}>
                关闭
              </Button>
            </Space>
          </div>
        ]}
        width={1000}
      >
        <div className="bg-black text-green-400 p-4 rounded font-mono text-sm max-h-96 overflow-y-auto">
          <pre className="whitespace-pre-wrap">
            {logContent || '暂无日志内容'}
          </pre>
        </div>
      </Modal>
      
      {/* 脚本内容查看模态框 */}
      <Modal
        title={`脚本内容 - ${selectedScript?.taskName || selectedScript?.taskId}`}
        open={scriptModalVisible}
        onCancel={() => setScriptModalVisible(false)}
        footer={[
          <Button key="close" onClick={() => setScriptModalVisible(false)}>
            关闭
          </Button>
        ]}
        width={800}
      >
        {selectedScript && (
          <div className="space-y-4">
            <div>
              <Text strong>脚本类型:</Text> 
              <Tag color="purple" className="ml-2">{selectedScript.scriptLang || 'shell'}</Tag>
            </div>
            <div>
              <Text strong>脚本内容:</Text>
            </div>
            <div className="bg-gray-900 text-green-400 p-4 rounded font-mono text-sm max-h-96 overflow-y-auto">
              <pre className="whitespace-pre-wrap">
                {selectedScript.scriptContent || '无脚本内容'}
              </pre>
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}

export default Tasks