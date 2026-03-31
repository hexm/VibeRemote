import React, { useState, useEffect, useCallback, useRef } from 'react'
import { Card, Table, Tag, Button, Space, Typography, Input, Select, Avatar, Tooltip, Modal, message, Row, Col, Descriptions, Statistic, Divider, Empty, Spin } from 'antd'
import {
  DesktopOutlined,
  SearchOutlined,
  ReloadOutlined,
  DeleteOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  ClockCircleOutlined,
  SyncOutlined,
  EyeOutlined,
  HddOutlined,
  UserOutlined,
  FolderOutlined,
  DownloadOutlined,
  CodeOutlined,
  PlusOutlined,
  VideoCameraOutlined,
  FileTextOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import api from '../services/auth'
import ScreenMonitorModal from '../components/ScreenMonitorModal'

const { Title, Text } = Typography
const { Search } = Input
const { Option } = Select
const PORTAL_BASE_URL = import.meta.env.VITE_PORTAL_BASE_URL || `${window.location.protocol}//${window.location.hostname}:8002`
const LOG_FILE_TERMINAL_STATUSES = new Set(['SUCCESS', 'FAILED'])

const joinUrl = (baseUrl, path) => {
  if (!baseUrl) return path
  return `${String(baseUrl).replace(/\/+$/, '')}/${String(path).replace(/^\/+/, '')}`
}

const extractFileNameFromDisposition = (contentDisposition, fallbackName) => {
  if (!contentDisposition) {
    return fallbackName
  }

  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1])
    } catch (error) {
      console.warn('解析下载文件名失败，回退默认文件名', error)
    }
  }

  const asciiMatch = contentDisposition.match(/filename="?([^"]+)"?/i)
  if (asciiMatch?.[1]) {
    return asciiMatch[1]
  }

  return fallbackName
}

const sortLogFiles = (files) => [...files].sort((left, right) => {
  const leftTime = left?.modifiedAt ? new Date(left.modifiedAt).getTime() : 0
  const rightTime = right?.modifiedAt ? new Date(right.modifiedAt).getTime() : 0
  if (leftTime !== rightTime) {
    return rightTime - leftTime
  }
  return (left?.fileName || '').localeCompare(right?.fileName || '')
})

const summarizeLogCollection = (collection, files) => {
  if (!collection) return null

  const totalFiles = files.length
  const successFiles = files.filter((file) => file.uploadStatus === 'SUCCESS').length
  const failedFiles = files.filter((file) => file.uploadStatus === 'FAILED').length
  const pendingFiles = totalFiles - successFiles - failedFiles

  let status = collection.status
  if (totalFiles > 0) {
    if (pendingFiles > 0 && successFiles === 0 && failedFiles === 0) {
      status = 'COLLECTING'
    } else if (pendingFiles > 0) {
      status = 'PARTIAL_READY'
    } else if (failedFiles > 0 && successFiles === 0) {
      status = 'FAILED'
    } else if (failedFiles > 0) {
      status = 'PARTIAL_READY'
    } else {
      status = 'READY'
    }
  }

  return {
    ...collection,
    status,
    totalFiles,
    successFiles,
    failedFiles,
    pendingFiles,
    files,
  }
}

const isLogFilePending = (file) => !LOG_FILE_TERMINAL_STATUSES.has(file?.uploadStatus)

const Agents = () => {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [agents, setAgents] = useState([])
  const [filteredAgents, setFilteredAgents] = useState([])
  const [searchText, setSearchText] = useState('')
  const [statusFilter, setStatusFilter] = useState('all')
  const [detailModalVisible, setDetailModalVisible] = useState(false)
  const [selectedAgent, setSelectedAgent] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [upgradeHistoryModalVisible, setUpgradeHistoryModalVisible] = useState(false)
  const [selectedAgentForHistory, setSelectedAgentForHistory] = useState(null)
  const [screenMonitorVisible, setScreenMonitorVisible] = useState(false)
  const [screenMonitorAgent, setScreenMonitorAgent] = useState(null)
  const [screenMonitorEnabled, setScreenMonitorEnabled] = useState(true)
  const [logModalVisible, setLogModalVisible] = useState(false)
  const [logAgent, setLogAgent] = useState(null)
  const [logCollection, setLogCollection] = useState(null)
  const [logFiles, setLogFiles] = useState([])
  const [logLoading, setLogLoading] = useState(false)
  const [logFileActionKey, setLogFileActionKey] = useState(null)
  const [logContentVisible, setLogContentVisible] = useState(false)
  const [logContentLoading, setLogContentLoading] = useState(false)
  const [logContent, setLogContent] = useState('')
  const [logContentFile, setLogContentFile] = useState(null)
  const [logRefreshLocked, setLogRefreshLocked] = useState(false)
  const agentsRef = useRef([])
  const logCollectionRef = useRef(null)
  const logFilesRef = useRef([])

  useEffect(() => {
    agentsRef.current = agents
  }, [agents])

  useEffect(() => {
    logCollectionRef.current = logCollection
  }, [logCollection])

  useEffect(() => {
    logFilesRef.current = logFiles
  }, [logFiles])

  const fetchAgentGroups = async (agentId, fallbackGroups = []) => {
    try {
      const groupsResp = await api.get(`/web/agents/${agentId}/groups`)
      return groupsResp.groups || []
    } catch (error) {
      console.error('获取分组失败', error)
      return fallbackGroups
    }
  }

  // 处理单个 Agent 数据的辅助函数
  const processAgentData = async (agent, previousAgent = null, includeGroups = true) => {
    // CPU负载转换为百分比（0.0-1.0 -> 0-100）
    const cpuPercent = agent.cpuLoad ? Math.round(agent.cpuLoad * 100) : 0
    
    // 内存使用率计算
    let memoryPercent = 0
    if (agent.totalMemMb && agent.freeMemMb) {
      const usedMemMb = agent.totalMemMb - agent.freeMemMb
      memoryPercent = Math.round((usedMemMb / agent.totalMemMb) * 100)
    }
    
    const groups = includeGroups
      ? await fetchAgentGroups(agent.agentId, previousAgent?.groups || [])
      : (previousAgent?.groups || [])
    
    return {
      key: agent.agentId,
      id: agent.agentId,
      agentId: agent.agentId, // 添加 agentId 字段
      hostname: agent.hostname,
      ip: agent.ip || 'N/A',
      os: agent.osType,
      status: agent.status === 'ONLINE' ? 'online' : 'offline',
      lastHeartbeat: agent.lastHeartbeat ? new Date(agent.lastHeartbeat).toLocaleString('zh-CN') : 'N/A',
      tasks: agent.taskCount || 0, // 直接使用Agent实体中的任务计数
      cpu: cpuPercent,
      memory: memoryPercent,
      uptime: calculateUptime(agent.createdAt),
      groups: groups,
      // 添加深度检查任务信息
      lastDiagnosticTaskId: agent.lastDiagnosticTaskId,
      lastDiagnosticTaskName: agent.lastDiagnosticTaskName,
      lastDiagnosticTime: agent.lastDiagnosticTime ? new Date(agent.lastDiagnosticTime).toLocaleString('zh-CN') : null,
      // 添加扩展系统信息
      startUser: agent.startUser,
      workingDir: agent.workingDir,
      diskSpaceGb: agent.diskSpaceGb,
      freeSpaceGb: agent.freeSpaceGb,
      osVersion: agent.osVersion,
      javaVersion: agent.javaVersion,
      agentVersion: agent.agentVersion,
      // 计算磁盘使用率
      diskUsagePercent: agent.diskSpaceGb && agent.freeSpaceGb ? 
        Math.round(((agent.diskSpaceGb - agent.freeSpaceGb) / agent.diskSpaceGb) * 100) : 0,
      // 格式化内存信息
      totalMemInfo: agent.totalMemMb ? `${(agent.totalMemMb / 1024).toFixed(1)} GB` : 'N/A',
      freeMemInfo: agent.freeMemMb ? `${(agent.freeMemMb / 1024).toFixed(1)} GB` : 'N/A',
      // 格式化磁盘信息
      diskInfo: agent.diskSpaceGb ? `${agent.diskSpaceGb} GB` : 'N/A',
      freeSpaceInfo: agent.freeSpaceGb ? `${agent.freeSpaceGb} GB` : 'N/A',
      memoryUsagePercent: memoryPercent,
      taskCount: agent.taskCount || 0
    }
  }

  // 加载Agent列表
  const loadAgents = useCallback(async ({ showLoading = true, includeGroups = true } = {}) => {
    if (showLoading) {
      setLoading(true)
    }
    try {
      const response = await api.get('/web/agents')
      const previousMap = new Map(agentsRef.current.map((agent) => [agent.agentId || agent.id, agent]))
      const agentList = await Promise.all(
        (response.content || []).map((agent) =>
          processAgentData(agent, previousMap.get(agent.agentId), includeGroups)
        )
      )
      setAgents(agentList)

      if (detailModalVisible && selectedAgent) {
        const refreshedSelectedAgent = agentList.find((agent) => agent.id === (selectedAgent.agentId || selectedAgent.id))
        if (refreshedSelectedAgent) {
          setSelectedAgent((previous) => ({ ...previous, ...refreshedSelectedAgent }))
        }
      }
    } catch (error) {
      console.error('Failed to load agents:', error)
      if (showLoading) {
        message.error('加载客户端列表失败')
      }
    } finally {
      if (showLoading) {
        setLoading(false)
      }
    }
  }, [detailModalVisible, selectedAgent])

  const loadScreenMonitorSetting = async () => {
    try {
      const response = await api.get('/web/system-settings/key/agent.screen_monitor.enabled')
      const enabled = response?.settingValue == null ? true : String(response.settingValue).toLowerCase() === 'true'
      setScreenMonitorEnabled(enabled)
    } catch (error) {
      console.warn('加载屏幕监控开关失败，默认按开启处理', error)
      setScreenMonitorEnabled(true)
    }
  }

  // 计算运行时间
  const calculateUptime = (createdAt) => {
    if (!createdAt) return '0天 0小时'
    const now = new Date()
    const created = new Date(createdAt)
    const diff = now - created
    const days = Math.floor(diff / (1000 * 60 * 60 * 24))
    const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60))
    return `${days}天 ${hours}小时`
  }

  const applyLogCollectionSnapshot = useCallback((collectionDto) => {
    const nextFiles = sortLogFiles(collectionDto?.files || [])
    const nextCollection = summarizeLogCollection(collectionDto, nextFiles)
    logFilesRef.current = nextFiles
    logCollectionRef.current = nextCollection
    setLogFiles(nextFiles)
    setLogCollection(nextCollection)
  }, [])

  const applyLogFileUpdates = useCallback((updates) => {
    if (!updates || updates.length === 0) return

    const fileMap = new Map((logFilesRef.current || []).map((file) => [file.id, file]))
    updates.forEach((file) => {
      if (file?.id) {
        fileMap.set(file.id, { ...fileMap.get(file.id), ...file })
      }
    })

    const nextFiles = sortLogFiles(Array.from(fileMap.values()))
    const nextCollection = summarizeLogCollection(logCollectionRef.current, nextFiles)

    logFilesRef.current = nextFiles
    logCollectionRef.current = nextCollection
    setLogFiles(nextFiles)
    setLogCollection(nextCollection)
  }, [])

  useEffect(() => {
    loadAgents({ showLoading: true, includeGroups: true })
    loadScreenMonitorSetting()
    // 每30秒静默刷新核心状态，避免整页 loading
    const interval = setInterval(() => {
      loadAgents({ showLoading: false, includeGroups: false })
    }, 30000)
    return () => clearInterval(interval)
  }, [loadAgents])

  useEffect(() => {
    filterAgents()
  }, [searchText, statusFilter, agents])

  const loadLatestLogCollection = useCallback(async (agentId, autoTrigger = true, options = {}) => {
    if (!agentId) return null
    const { showLoading = true } = options

    if (showLoading) {
      setLogLoading(true)
    }
    try {
      const response = await api.get(`/web/agents/${agentId}/log-collections/latest`)
      if (response && response.id) {
        return response
      }

      if (!autoTrigger) {
        return null
      }

      return await api.post(`/web/agents/${agentId}/log-collections`)
    } catch (error) {
      if (!autoTrigger) {
        throw error
      }
      return await api.post(`/web/agents/${agentId}/log-collections`)
    } finally {
      if (showLoading) {
        setLogLoading(false)
      }
    }
  }, [])

  useEffect(() => {
    if (!logModalVisible || !logAgent) return undefined

    if (logCollection && ((logCollection.totalFiles || 0) > 0 || logCollection.status === 'FAILED' || logCollection.status === 'READY' || logCollection.status === 'PARTIAL_READY')) {
      return undefined
    }

    const interval = setInterval(() => {
      loadLatestLogCollection(logAgent.agentId || logAgent.id, false, { showLoading: false })
        .then((response) => {
          if (response?.id) {
            applyLogCollectionSnapshot(response)
          }
        })
        .catch((error) => {
          console.error('轮询Agent日志清单失败:', error)
        })
    }, 3000)

    return () => clearInterval(interval)
  }, [logModalVisible, logAgent, logCollection, loadLatestLogCollection, applyLogCollectionSnapshot])

  useEffect(() => {
    if (!logModalVisible || !logAgent || !logCollection?.id || logFiles.length === 0) return undefined

    const pendingFiles = logFiles.filter(isLogFilePending)
    if (pendingFiles.length === 0) return undefined

    const interval = setInterval(async () => {
      try {
        const results = await Promise.allSettled(
          pendingFiles.map((file) =>
            api.get(`/web/agents/${logAgent.agentId || logAgent.id}/log-collections/${logCollection.id}/files/${file.id}`)
          )
        )
        const updates = results
          .filter((result) => result.status === 'fulfilled' && result.value?.id)
          .map((result) => result.value)
        applyLogFileUpdates(updates)
      } catch (error) {
        console.error('轮询Agent日志文件失败:', error)
      }
    }, 3000)

    return () => clearInterval(interval)
  }, [logModalVisible, logAgent, logCollection?.id, logFiles, applyLogFileUpdates])

  const filterAgents = () => {
    let filtered = agents

    if (searchText) {
      const keyword = searchText.toLowerCase()
      filtered = filtered.filter(agent =>
        agent.hostname?.toLowerCase().includes(keyword) ||
        agent.ip?.toLowerCase().includes(keyword) ||
        agent.id?.toLowerCase().includes(keyword) ||
        agent.startUser?.toLowerCase().includes(keyword)
      )
    }

    if (statusFilter !== 'all') {
      filtered = filtered.filter(agent => agent.status === statusFilter)
    }

    setFilteredAgents(filtered)
  }

  // 查看客户端详情
  const handleViewDetail = async (agent) => {
    setSelectedAgent(agent)
    setDetailModalVisible(true)
    setDetailLoading(true)
    
    try {
      // 获取更详细的客户端信息
      const response = await api.get(`/web/agents/${agent.id}`)
      setSelectedAgent({
        ...agent,
        ...response,
        // 格式化磁盘空间信息
        diskInfo: response.diskSpaceGb ? `${response.diskSpaceGb}GB` : 'N/A',
        freeSpaceInfo: response.freeSpaceGb ? `${response.freeSpaceGb}GB` : 'N/A',
        diskUsagePercent: response.diskSpaceGb && response.freeSpaceGb 
          ? Math.round(((response.diskSpaceGb - response.freeSpaceGb) / response.diskSpaceGb) * 100)
          : 0,
        // 格式化内存信息
        totalMemInfo: response.totalMemMb ? `${Math.round(response.totalMemMb / 1024)}GB` : 'N/A',
        freeMemInfo: response.freeMemMb ? `${Math.round(response.freeMemMb / 1024)}GB` : 'N/A',
        memoryUsagePercent: response.totalMemMb && response.freeMemMb 
          ? Math.round(((response.totalMemMb - response.freeMemMb) / response.totalMemMb) * 100)
          : 0
      })
    } catch (error) {
      console.error('获取客户端详情失败:', error)
      message.error('获取客户端详情失败')
    } finally {
      setDetailLoading(false)
    }
  }

  // 打开任务详情的辅助函数
  const openTaskDetail = (taskInfo) => {
    // 构造一个特殊的URL，包含任务信息
    const params = new URLSearchParams()
    if (taskInfo.taskId) {
      params.set('taskId', taskInfo.taskId)
    }
    params.set('taskName', taskInfo.taskName)
    params.set('autoOpen', 'true') // 标记需要自动打开详情
    
    // 使用 navigate 进行页面跳转
    navigate(`/tasks?${params.toString()}`)
  }

  const handleRefresh = async () => {
    await loadAgents({ showLoading: true, includeGroups: true })
    message.success('数据已刷新')
  }

  const handleOpenLogs = async (agent) => {
    setLogAgent(agent)
    setLogRefreshLocked(false)
    setLogCollection(null)
    setLogFiles([])
    setLogModalVisible(true)
    try {
      const response = await loadLatestLogCollection(agent.agentId || agent.id, true, { showLoading: true })
      if (response) {
        applyLogCollectionSnapshot(response)
      }
    } catch (error) {
      console.error('加载Agent日志失败:', error)
      message.error('加载Agent日志失败: ' + (error.message || '未知错误'))
    }
  }

  const handleViewLogContent = async (file) => {
    if (!logAgent || !logCollection) return
    if (file.uploadStatus !== 'SUCCESS') {
      message.info('日志还在收集中，请稍后再试')
      return
    }

    setLogContentVisible(true)
    setLogContentLoading(true)
    setLogContent('')
    setLogContentFile(file)
    try {
      const response = await api.get(
        `/web/agents/${logAgent.agentId || logAgent.id}/log-collections/${logCollection.id}/files/${file.id}/content`
      )
      setLogContent(response?.content || '')
    } catch (error) {
      console.error('读取日志内容失败:', error)
      message.error('读取日志内容失败: ' + (error.message || '未知错误'))
    } finally {
      setLogContentLoading(false)
    }
  }

  const handleRecollectLogs = async () => {
    if (!logAgent || logRefreshLocked) return
    setLogLoading(true)
    try {
      const response = await api.post(`/web/agents/${logAgent.agentId || logAgent.id}/log-collections`)
      applyLogCollectionSnapshot(response)
      setLogRefreshLocked(true)
      message.success('已重新触发全部日志收集')
    } catch (error) {
      console.error('重新收集Agent日志失败:', error)
      message.error('重新收集Agent日志失败: ' + (error.message || '未知错误'))
    } finally {
      setLogLoading(false)
    }
  }

  const handleRecollectLogFile = async (file) => {
    if (!logAgent || !logCollection || !file?.id) return

    setLogFileActionKey(`recollect-${file.id}`)
    try {
      const response = await api.post(
        `/web/agents/${logAgent.agentId || logAgent.id}/log-collections/${logCollection.id}/files/${file.id}/recollect`
      )
      applyLogCollectionSnapshot(response)
      message.success(`已重新触发日志上传: ${file.fileName}`)
    } catch (error) {
      console.error('重新上传Agent日志失败:', error)
      message.error('重新上传Agent日志失败: ' + (error.message || '未知错误'))
    } finally {
      setLogFileActionKey(null)
    }
  }

  const handleDownloadLogFile = async (file) => {
    if (!logAgent || !logCollection || !file?.id) return
    if (file.uploadStatus !== 'SUCCESS') {
      message.info('日志还在收集中，请稍后再试')
      return
    }

    setLogFileActionKey(`download-${file.id}`)
    try {
      const token = localStorage.getItem('token')
      const downloadUrl = joinUrl(
        api.defaults.baseURL || '/api',
        `/web/agents/${logAgent.agentId || logAgent.id}/log-collections/${logCollection.id}/files/${file.id}/download`
      )

      const response = await fetch(downloadUrl, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      })

      if (!response.ok) {
        let errorMessage = `HTTP ${response.status}`
        try {
          const errorBody = await response.json()
          errorMessage = errorBody?.message || errorBody?.error || errorMessage
        } catch (error) {
          console.warn('解析日志下载错误响应失败', error)
        }
        throw new Error(errorMessage)
      }

      const blob = await response.blob()
      const objectUrl = window.URL.createObjectURL(blob)
      const fileName = extractFileNameFromDisposition(
        response.headers.get('content-disposition'),
        file.fileName || `agent-log-${file.id}.log`
      )

      const link = document.createElement('a')
      link.href = objectUrl
      link.download = fileName
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(objectUrl)
      message.success(`开始下载日志: ${fileName}`)
    } catch (error) {
      console.error('下载Agent日志失败:', error)
      message.error('下载Agent日志失败: ' + (error.message || '未知错误'))
    } finally {
      setLogFileActionKey(null)
    }
  }

  const handleDeleteAgent = async (agent) => {
    // 检查是否在线 - 使用大写状态进行比较
    if (agent.status === 'online' || agent.status === 'ONLINE') {
      message.warning('不能删除在线的客户端，请先停止客户端')
      return
    }
    
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除客户端 ${agent.hostname} 吗？此操作不可恢复。`,
      okText: '确定',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        try {
          await api.delete(`/web/agents/${agent.id}`)
          message.success('客户端已删除')
          loadAgents() // 重新加载列表
        } catch (error) {
          console.error('删除客户端失败:', error)
          message.error('删除失败: ' + (error.response?.data?.message || error.message))
        }
      },
    })
  }

  // 状态收集功能 - 创建一个任务来收集Agent状态
  const handleCollectStatus = async (agent) => {
    try {
      // 根据操作系统选择不同的状态收集脚本
      const isWindows = agent.osType && agent.osType.toLowerCase().includes('windows')
      
      const statusScript = isWindows 
        ? `@echo off
echo ===== System Deep Check Report =====
echo Generated at: %date% %time%
echo.
echo ===== Detailed System Information =====
systeminfo
echo.
echo ===== CPU Information =====
wmic cpu get Name,NumberOfCores,NumberOfLogicalProcessors,MaxClockSpeed,LoadPercentage /format:list
echo.
echo ===== Memory Details =====
wmic OS get TotalVisibleMemorySize,FreePhysicalMemory,TotalVirtualMemorySize,FreeVirtualMemory /format:list
wmic computersystem get TotalPhysicalMemory /format:list
echo.
echo ===== Disk Information =====
wmic logicaldisk get DeviceID,Size,FreeSpace,FileSystem,VolumeName /format:list
echo.
echo ===== Network Configuration =====
ipconfig /all
echo.
echo ===== Network Connections =====
netstat -an | findstr LISTENING
echo.
echo ===== Running Processes (Top 15 by Memory) =====
powershell -NoProfile -Command "Get-Process | Sort-Object WS -Descending | Select-Object -First 15 ProcessName,Id,@{Name='MemoryMB';Expression={[math]::Round($_.WS/1MB,2)}} | Format-Table -AutoSize"
echo.
echo ===== Services Status =====
sc query state= all | findstr "SERVICE_NAME STATE"
echo.
echo ===== Environment Variables =====
set
`
        : `#!/bin/bash
echo "===== System Deep Check Report ====="
echo "Generated at: $(date)"
echo ""
echo "===== Detailed System Information ====="
uname -a
cat /etc/*release 2>/dev/null
echo ""
echo "===== CPU Information ====="
cat /proc/cpuinfo | grep -E "(processor|model name|cpu MHz|cache size)" 2>/dev/null || echo "CPU info not available"
echo ""
echo "===== Memory Details ====="
cat /proc/meminfo 2>/dev/null || free -h
echo ""
echo "===== Disk Information ====="
df -h
lsblk 2>/dev/null || echo "Block device info not available"
echo ""
echo "===== Network Configuration ====="
ip addr show 2>/dev/null || ifconfig
echo ""
echo "===== Network Connections ====="
ss -tuln 2>/dev/null || netstat -tuln 2>/dev/null || echo "Network connections info not available"
echo ""
echo "===== Running Processes (Top 15 by Memory) ====="
ps aux --sort=-%mem | head -16
echo ""
echo "===== System Load ====="
uptime
cat /proc/loadavg 2>/dev/null || echo "Load average not available"
echo ""
echo "===== Disk I/O Statistics ====="
iostat 2>/dev/null || echo "I/O statistics not available"
echo ""
echo "===== Environment Variables ====="
env | sort
`

      const taskSpec = {
        scriptLang: isWindows ? 'cmd' : 'shell',
        scriptContent: statusScript,
        timeoutSec: 120
      }
      
      // 生成带时间戳的任务名称
      const timestamp = new Date().toLocaleString('zh-CN', { 
        year: 'numeric', 
        month: '2-digit', 
        day: '2-digit', 
        hour: '2-digit', 
        minute: '2-digit',
        second: '2-digit',
        hour12: false 
      }).replace(/\//g, '-').replace(/:/g, '').replace(/\s/g, '_')
      
      const taskName = `深度检查_${agent.hostname}_${timestamp}`
      
      const params = new URLSearchParams()
      params.append('agentIds', agent.agentId || agent.id)
      params.append('taskName', taskName)
      params.append('autoStart', true)
      
      const response = await api.post(`/web/tasks/create?${params.toString()}`, taskSpec)
      
      message.success(
        <div>
          <div>深度检查任务已创建：<strong>{taskName}</strong></div>
          <div style={{ marginTop: 4, fontSize: '12px', color: '#666' }}>
            请在任务管理页面查看执行结果
          </div>
        </div>,
        6
      )
      
      // 刷新 Agent 列表以获取最新的深度检查任务信息
      await loadAgents()
      
      // 如果详情弹窗是打开的，需要重新获取当前 agent 的最新数据
      if (detailModalVisible && selectedAgent) {
        try {
          const agentResponse = await api.get('/web/agents')
          const updatedAgent = agentResponse.content.find(a => a.agentId === selectedAgent.agentId)
          if (updatedAgent) {
            const processedAgent = await processAgentData(updatedAgent)
            setSelectedAgent(processedAgent)
          }
        } catch (error) {
          console.error('更新选中 Agent 数据失败:', error)
        }
      }
    } catch (error) {
      console.error('创建深度检查任务失败:', error)
      message.error('创建深度检查任务失败: ' + (error.response?.data?.message || error.message))
    }
  }

  const columns = [
    {
      title: '客户端',
      key: 'info',
      width: 160,
      render: (_, record) => (
        <Space>
          <Avatar 
            icon={<DesktopOutlined />} 
            className={record.status === 'online' ? 'bg-green-500' : 'bg-gray-400'}
          />
          <div>
            <Text strong className="block">{record.hostname}</Text>
          </div>
        </Space>
      ),
    },
    {
      title: '启动用户',
      dataIndex: 'startUser',
      key: 'startUser',
      width: 130,
      render: (startUser) => startUser ? <Text>{startUser}</Text> : <Text type="secondary">-</Text>,
    },
    {
      title: 'IP',
      dataIndex: 'ip',
      key: 'ip',
      width: 120,
      render: (text) => <Text code>{text}</Text>,
    },
    {
      title: '系统',
      dataIndex: 'os',
      key: 'os',
      width: 100,
      render: (text) => (
        <Tag color={text.includes('Windows') ? 'blue' : 'green'}>
          {text}
        </Tag>
      ),
    },
    {
      title: '版本',
      dataIndex: 'agentVersion',
      key: 'agentVersion',
      width: 80,
      render: (version) => (
        <Tag color="purple">
          {version || 'Unknown'}
        </Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 80,
      render: (status) => {
        const statusConfig = {
          online: { 
            color: 'success', 
            text: '在线', 
            icon: <CheckCircleOutlined /> 
          },
          offline: { 
            color: 'error', 
            text: '离线', 
            icon: <ExclamationCircleOutlined /> 
          },
          busy: { 
            color: 'warning', 
            text: '忙碌', 
            icon: <ClockCircleOutlined /> 
          },
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
      title: '任务',
      dataIndex: 'tasks',
      key: 'tasks',
      width: 60,
      render: (text) => (
        <Tag color="blue" className="font-mono">
          {text}
        </Tag>
      ),
    },
    {
      title: '最后心跳',
      dataIndex: 'lastHeartbeat',
      key: 'lastHeartbeat',
      width: 140,
      render: (text) => (
        <Text type="secondary" className="text-sm">
          {text}
        </Text>
      ),
    },
    {
      title: '运行',
      dataIndex: 'uptime',
      key: 'uptime',
      width: 80,
      render: (text) => <Text className="text-sm">{text}</Text>,
    },
    {
      title: '分组',
      key: 'groups',
      width: 100,
      render: (_, record) => (
        <Space size="small" wrap>
          {record.groups && record.groups.length > 0 ? (
            record.groups.map(group => (
              <Tag color="purple" key={group.id}>
                {group.name}
              </Tag>
            ))
          ) : (
            <Text type="secondary" className="text-xs">未分组</Text>
          )}
        </Space>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 220,
      fixed: 'right',
      render: (_, record) => (
        <Space>
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
            icon={<FileTextOutlined />}
            size="small"
            onClick={() => handleOpenLogs(record)}
          >
            日志
          </Button>
          <Button 
            type="link" 
            icon={<DeleteOutlined />} 
            size="small"
            danger
            disabled={record.status === 'online'}
            onClick={() => handleDeleteAgent(record)}
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
            <DesktopOutlined className="mr-3 text-blue-500" />
            客户端管理
          </Title>
          <Text type="secondary" className="text-base">
            管理和监控所有连接的客户端节点
          </Text>
        </div>
        <Space>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => window.open(`${PORTAL_BASE_URL}/client-install.html`, '_blank')}
          >
            安装客户端
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
              placeholder="搜索主机名、启动用户、IP或ID"
              allowClear
              style={{ width: 300 }}
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              prefix={<SearchOutlined className="text-gray-400" />}
            />
            <Select
              value={statusFilter}
              onChange={setStatusFilter}
              style={{ width: 120 }}
            >
              <Option value="all">全部状态</Option>
              <Option value="online">在线</Option>
              <Option value="offline">离线</Option>
              <Option value="busy">忙碌</Option>
            </Select>
          </Space>
          
          <div className="flex items-center space-x-4 text-sm">
            <Space>
              <div className="w-3 h-3 bg-green-500 rounded-full"></div>
              <Text>在线: {agents.filter(a => a.status === 'online').length}</Text>
            </Space>
            <Space>
              <div className="w-3 h-3 bg-red-500 rounded-full"></div>
              <Text>离线: {agents.filter(a => a.status === 'offline').length}</Text>
            </Space>
            <Space>
              <div className="w-3 h-3 bg-yellow-500 rounded-full"></div>
              <Text>忙碌: {agents.filter(a => a.status === 'busy').length}</Text>
            </Space>
          </div>
        </div>
      </Card>

      {/* 客户端列表 */}
      <Card className="shadow-lg">
        <Table
          columns={columns}
          dataSource={filteredAgents}
          loading={loading}
          pagination={{
            total: filteredAgents.length,
            pageSize: 10,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total, range) => 
              `第 ${range[0]}-${range[1]} 条，共 ${total} 条记录`,
          }}
          className="rounded-lg overflow-hidden"
          scroll={{ x: 900 }}
        />
      </Card>

      {/* 客户端详情弹窗 */}
      <Modal
        title={`客户端详情 - ${selectedAgent?.hostname || ''}`}
        open={detailModalVisible}
        onCancel={() => setDetailModalVisible(false)}
        width={800}
        style={{ top: 20 }}
        bodyStyle={{ maxHeight: '70vh', overflowY: 'auto' }}
        footer={[
          <Button
            key="logs"
            icon={<FileTextOutlined />}
            onClick={() => {
              if (selectedAgent) {
                handleOpenLogs(selectedAgent)
              }
            }}
          >
            日志
          </Button>,
          ...(screenMonitorEnabled ? [
          <Button 
            key="screen"
            icon={<VideoCameraOutlined />}
            onClick={() => {
              if (selectedAgent) {
                setScreenMonitorAgent(selectedAgent)
                setScreenMonitorVisible(true)
              }
            }}
            disabled={
              (selectedAgent?.status !== 'online' && selectedAgent?.status !== 'ONLINE') ||
              !['WINDOWS', 'MACOS'].includes((selectedAgent?.osType || selectedAgent?.os || '').toUpperCase())
            }
          >
            屏幕监控
          </Button>
          ] : []),
          <Button 
            key="collect" 
            type="primary"
            icon={<SyncOutlined />}
            onClick={() => {
              if (selectedAgent) {
                handleCollectStatus(selectedAgent)
              }
            }}
            disabled={selectedAgent?.status !== 'online' && selectedAgent?.status !== 'ONLINE'}
          >
            深度检查
          </Button>,
          <Button key="close" onClick={() => setDetailModalVisible(false)}>
            关闭
          </Button>
        ]}
      >
        {selectedAgent && (
          <div className="space-y-6">
            {/* 基本信息 */}
            <Card title="基本信息" size="small">
              <Descriptions column={2} size="small">
                <Descriptions.Item label="客户端ID">
                  <Text code>{selectedAgent.agentId || selectedAgent.id}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="主机名">
                  <Text strong>{selectedAgent.hostname}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="IP地址">
                  <Text code>{selectedAgent.ip || 'N/A'}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="状态">
                  <Tag 
                    color={selectedAgent.status === 'online' || selectedAgent.status === 'ONLINE' ? 'success' : 'error'}
                    icon={selectedAgent.status === 'online' || selectedAgent.status === 'ONLINE' ? <CheckCircleOutlined /> : <ExclamationCircleOutlined />}
                  >
                    {selectedAgent.status === 'online' || selectedAgent.status === 'ONLINE' ? '在线' : '离线'}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="操作系统">
                  <Tag color={selectedAgent.osType?.includes('WINDOWS') ? 'blue' : 'green'}>
                    {selectedAgent.osType || selectedAgent.os}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="系统版本">
                  <Text>{selectedAgent.osVersion || 'N/A'}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="Java版本">
                  <Text>{selectedAgent.javaVersion || 'N/A'}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="Agent版本">
                  <Space>
                    <Text>{selectedAgent.agentVersion || 'N/A'}</Text>
                    <Button 
                      type="link" 
                      size="small"
                      style={{ padding: 0, height: 'auto' }}
                      onClick={() => {
                        setSelectedAgentForHistory(selectedAgent)
                        setUpgradeHistoryModalVisible(true)
                      }}
                    >
                      查看历史
                    </Button>
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="启动用户">
                  <Space>
                    <UserOutlined />
                    <Text>{selectedAgent.startUser || 'N/A'}</Text>
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="工作目录">
                  <Space>
                    <FolderOutlined />
                    <Text code>{selectedAgent.workingDir || 'N/A'}</Text>
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="运行时间">
                  <Text>{selectedAgent.uptime}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="最后心跳">
                  <Text type="secondary">{selectedAgent.lastHeartbeat}</Text>
                </Descriptions.Item>
              </Descriptions>
            </Card>

            {/* 资源使用情况 */}
            <Card title="资源使用情况" size="small">
              <Row gutter={16}>
                <Col span={6}>
                  <Statistic
                    title="CPU使用率"
                    value={selectedAgent.cpu || 0}
                    suffix="%"
                    valueStyle={{ color: (selectedAgent.cpu || 0) > 80 ? '#cf1322' : '#3f8600' }}
                  />
                </Col>
                <Col span={6}>
                  <Statistic
                    title="内存使用率"
                    value={selectedAgent.memoryUsagePercent || selectedAgent.memory || 0}
                    suffix="%"
                    valueStyle={{ color: (selectedAgent.memoryUsagePercent || selectedAgent.memory || 0) > 80 ? '#cf1322' : '#3f8600' }}
                  />
                </Col>
                <Col span={6}>
                  <Statistic
                    title="磁盘使用率"
                    value={selectedAgent.diskUsagePercent || 0}
                    suffix="%"
                    valueStyle={{ color: (selectedAgent.diskUsagePercent || 0) > 80 ? '#cf1322' : '#3f8600' }}
                    prefix={<HddOutlined />}
                  />
                </Col>
                <Col span={6}>
                  <Statistic
                    title="活跃任务"
                    value={selectedAgent.taskCount || selectedAgent.tasks || 0}
                    valueStyle={{ color: '#1890ff' }}
                  />
                </Col>
              </Row>
              
              <Row gutter={16} className="mt-4">
                <Col span={8}>
                  <Descriptions size="small" column={1}>
                    <Descriptions.Item label="总内存">
                      <Text>{selectedAgent.totalMemInfo || 'N/A'}</Text>
                    </Descriptions.Item>
                    <Descriptions.Item label="可用内存">
                      <Text>{selectedAgent.freeMemInfo || 'N/A'}</Text>
                    </Descriptions.Item>
                  </Descriptions>
                </Col>
                <Col span={8}>
                  <Descriptions size="small" column={1}>
                    <Descriptions.Item label="总磁盘空间">
                      <Text>{selectedAgent.diskInfo}</Text>
                    </Descriptions.Item>
                    <Descriptions.Item label="可用磁盘空间">
                      <Text>{selectedAgent.freeSpaceInfo}</Text>
                    </Descriptions.Item>
                  </Descriptions>
                </Col>
                <Col span={8}>
                  {selectedAgent.lastDiagnosticTaskId && selectedAgent.lastDiagnosticTaskName && (
                    <div className="text-center">
                      <Text type="secondary" className="text-xs block mb-2">最近检查</Text>
                      <Button 
                        type="link" 
                        size="small"
                        onClick={() => openTaskDetail({
                          taskId: selectedAgent.lastDiagnosticTaskId,
                          taskName: selectedAgent.lastDiagnosticTaskName
                        })}
                        title={`任务名称: ${selectedAgent.lastDiagnosticTaskName}`}
                      >
                        查看检查结果
                      </Button>
                      <div className="text-xs text-gray-500 mt-1">
                        {selectedAgent.lastDiagnosticTime}
                      </div>
                    </div>
                  )}
                  {!selectedAgent.lastDiagnosticTaskId && (
                    <div className="text-center">
                      <Text type="secondary" className="text-xs">暂无检查记录</Text>
                    </div>
                  )}
                </Col>
              </Row>
            </Card>

            {/* 所属分组 */}
            <Card title="所属分组" size="small">
              <Space size="small" wrap>
                {selectedAgent.groups && selectedAgent.groups.length > 0 ? (
                  selectedAgent.groups.map(group => (
                    <Tag color="purple" key={group.id}>
                      {group.name}
                    </Tag>
                  ))
                ) : (
                  <Text type="secondary">该客户端未加入任何分组</Text>
                )}
              </Space>
            </Card>
          </div>
        )}
      </Modal>

      {/* 升级历史弹窗 */}
      <Modal
        title={`升级历史 - ${selectedAgentForHistory?.hostname || ''}`}
        open={upgradeHistoryModalVisible}
        onCancel={() => setUpgradeHistoryModalVisible(false)}
        width={900}
        footer={[
          <Button key="close" onClick={() => setUpgradeHistoryModalVisible(false)}>
            关闭
          </Button>
        ]}
      >
        {selectedAgentForHistory && (
          <UpgradeHistory agentId={selectedAgentForHistory.agentId || selectedAgentForHistory.id} />
        )}
      </Modal>

      {/* 屏幕监控弹窗 */}
      <ScreenMonitorModal
        agentId={screenMonitorAgent?.agentId || screenMonitorAgent?.id}
        hostname={screenMonitorAgent?.hostname}
        visible={screenMonitorVisible}
        onClose={() => setScreenMonitorVisible(false)}
      />

      <AgentLogModal
        visible={logModalVisible}
        agent={logAgent}
        collection={logCollection}
        files={logFiles}
        loading={logLoading}
        refreshLocked={logRefreshLocked}
        fileActionKey={logFileActionKey}
        onClose={() => {
          setLogModalVisible(false)
          setLogAgent(null)
          setLogCollection(null)
          setLogFiles([])
          setLogRefreshLocked(false)
          setLogFileActionKey(null)
        }}
        onRefresh={handleRecollectLogs}
        onViewContent={handleViewLogContent}
        onRecollectFile={handleRecollectLogFile}
        onDownloadFile={handleDownloadLogFile}
      />

      <Modal
        title={`日志内容 - ${logContentFile?.fileName || ''}`}
        open={logContentVisible}
        onCancel={() => setLogContentVisible(false)}
        width={1000}
        footer={[
          <Button key="close" onClick={() => setLogContentVisible(false)}>
            关闭
          </Button>
        ]}
      >
        {logContentLoading ? (
          <div className="py-12 text-center">
            <Spin />
          </div>
        ) : (
          <pre
            style={{
              maxHeight: '65vh',
              overflow: 'auto',
              background: '#111827',
              color: '#e5e7eb',
              padding: 16,
              borderRadius: 8,
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-word',
              margin: 0,
            }}
          >
            {logContent || '日志内容为空'}
          </pre>
        )}
      </Modal>
    </div>
  )
}

const formatFileSize = (size) => {
  if (!size && size !== 0) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  if (size < 1024 * 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`
  return `${(size / 1024 / 1024 / 1024).toFixed(1)} GB`
}

const renderLogStatus = (status) => {
  const statusMap = {
    COLLECTING: { color: 'processing', text: '收集中' },
    READY: { color: 'success', text: '已完成' },
    FAILED: { color: 'error', text: '失败' },
    PARTIAL_READY: { color: 'warning', text: '部分完成' },
    PENDING: { color: 'default', text: '待收集' },
    QUEUED: { color: 'processing', text: '排队中' },
    UPLOADING: { color: 'processing', text: '上传中' },
    SUCCESS: { color: 'success', text: '已上传' },
  }
  const config = statusMap[status] || { color: 'default', text: status || '未知' }
  return <Tag color={config.color}>{config.text}</Tag>
}

const AgentLogModal = ({ visible, agent, collection, files, loading, refreshLocked, fileActionKey, onClose, onRefresh, onViewContent, onRecollectFile, onDownloadFile }) => {
  const columns = [
    {
      title: '日志文件',
      dataIndex: 'fileName',
      key: 'fileName',
      render: (text) => <Text strong>{text}</Text>,
    },
    {
      title: '大小',
      dataIndex: 'fileSize',
      key: 'fileSize',
      width: 80,
      render: (value) => formatFileSize(value),
    },
    {
      title: '更新时间',
      dataIndex: 'modifiedAt',
      key: 'modifiedAt',
      width: 160,
      render: (value) => value ? new Date(value).toLocaleString('zh-CN') : '-',
    },
    {
      title: '收集状态',
      dataIndex: 'uploadStatus',
      key: 'uploadStatus',
      width: 90,
      render: (value) => renderLogStatus(value),
    },
    {
      title: '上传时间',
      dataIndex: 'uploadedAt',
      key: 'uploadedAt',
      width: 160,
      render: (value) => value ? new Date(value).toLocaleString('zh-CN') : '-',
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      render: (_, record) => (
        <Space size={0}>
          <Button
            type="link"
            size="small"
            disabled={record.uploadStatus !== 'SUCCESS'}
            onClick={() => onViewContent(record)}
          >
            查看
          </Button>
          <Button
            type="link"
            size="small"
            loading={fileActionKey === `download-${record.id}`}
            disabled={record.uploadStatus !== 'SUCCESS'}
            onClick={() => onDownloadFile(record)}
          >
            下载
          </Button>
          <Button
            type="link"
            size="small"
            loading={fileActionKey === `recollect-${record.id}`}
            disabled={record.uploadStatus === 'QUEUED' || record.uploadStatus === 'UPLOADING'}
            onClick={() => onRecollectFile(record)}
          >
            重新上传
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <Modal
      title={`Agent日志 - ${agent?.hostname || ''}`}
      open={visible}
      onCancel={onClose}
      width={980}
      footer={[
        <Button key="refresh" onClick={onRefresh} loading={loading} disabled={refreshLocked}>
          重新收集全部
        </Button>,
        <Button key="close" onClick={onClose}>
          关闭
        </Button>
      ]}
    >
      <div className="space-y-4">
        <Card size="small">
          <Space split={<Divider type="vertical" />}>
            <div>
              <Text type="secondary">收集批次</Text>
              <div>{collection?.id || '准备中'}</div>
            </div>
            <div>
              <Text type="secondary">总体状态</Text>
              <div>{renderLogStatus(collection?.status || 'COLLECTING')}</div>
            </div>
            <div>
              <Text type="secondary">文件统计</Text>
              <div>
                {collection
                  ? `共 ${collection.totalFiles || 0} 个，完成 ${collection.successFiles || 0} 个，待处理 ${collection.pendingFiles || 0} 个`
                  : '正在触发日志收集'}
              </div>
            </div>
          </Space>
        </Card>

        {loading ? (
          <div className="py-12 text-center">
            <Spin />
          </div>
        ) : files.length > 0 ? (
          <Table
            rowKey="id"
            columns={columns}
            dataSource={files}
            size="small"
            pagination={false}
            scroll={{ y: 420 }}
          />
        ) : (
          <Empty description="正在收集日志清单，请稍候刷新" />
        )}
      </div>
    </Modal>
  )
}

// 升级历史组件
const UpgradeHistory = ({ agentId }) => {
  const [upgradeHistory, setUpgradeHistory] = useState([])
  const [loading, setLoading] = useState(false)
  
  const loadUpgradeHistory = useCallback(async () => {
    if (!agentId) return
    
    setLoading(true)
    try {
      const response = await api.get(`/agent/${agentId}/upgrade-history`)
      setUpgradeHistory(response || [])
    } catch (error) {
      console.error('加载升级历史失败:', error)
      message.error('加载升级历史失败')
    } finally {
      setLoading(false)
    }
  }, [agentId])
  
  useEffect(() => {
    loadUpgradeHistory()
  }, [loadUpgradeHistory])
  
  const columns = [
    {
      title: '升级时间',
      dataIndex: 'startTime',
      render: (text) => text ? new Date(text).toLocaleString('zh-CN') : '-',
    },
    {
      title: '版本变更',
      render: (_, record) => (
        <span>{record.fromVersion || 'Unknown'} → {record.toVersion || 'Unknown'}</span>
      ),
    },
    {
      title: '升级状态',
      dataIndex: 'upgradeStatus',
      render: (status) => {
        const statusConfig = {
          SUCCESS: { color: 'success', text: '成功' },
          FAILED: { color: 'error', text: '失败' },
          ROLLBACK: { color: 'warning', text: '回滚' },
          STARTED: { color: 'processing', text: '开始' },
          DOWNLOADING: { color: 'processing', text: '下载中' },
          INSTALLING: { color: 'processing', text: '安装中' },
        }
        const config = statusConfig[status] || { color: 'default', text: status }
        return <Tag color={config.color}>{config.text}</Tag>
      },
    },
    {
      title: '耗时',
      render: (_, record) => {
        if (record.endTime && record.startTime) {
          const duration = Math.floor((new Date(record.endTime) - new Date(record.startTime)) / 1000)
          return `${duration}秒`
        }
        return '-'
      },
    },
    {
      title: '强制升级',
      dataIndex: 'forceUpgrade',
      render: (force) => force ? <Tag color="red">是</Tag> : <Tag>否</Tag>,
    },
  ]
  
  return (
    <Table
      columns={columns}
      dataSource={upgradeHistory}
      rowKey="id"
      loading={loading}
      size="small"
      scroll={{ y: 300 }}
      pagination={{ 
        pageSize: 10,
        showSizeChanger: true,
        showQuickJumper: true,
        showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条，共 ${total} 条`,
        pageSizeOptions: ['5', '10', '20', '50']
      }}
      locale={{ emptyText: '暂无升级记录' }}
    />
  )
}

export default Agents
