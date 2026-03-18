import React, { useState, useEffect, useRef } from 'react'
import { Modal, Button, Alert, Space, Typography, Progress } from 'antd'
import { VideoCameraOutlined } from '@ant-design/icons'

const { Text } = Typography

/**
 * 屏幕监控弹窗
 * 建立 WebSocket 连接，被动接收截图帧，关闭时自动断开
 */
const ScreenMonitorModal = ({ agentId, hostname, visible, onClose }) => {
  const [imageData, setImageData] = useState(null)
  const [lastUpdate, setLastUpdate] = useState(null)
  const [status, setStatus] = useState('connecting') // connecting | waiting | live | error | timeout
  const [errorMsg, setErrorMsg] = useState('')
  const [countdown, setCountdown] = useState(null)   // 倒计时秒数
  const wsRef = useRef(null)
  const countdownRef = useRef(null)

  const clearCountdown = () => {
    if (countdownRef.current) {
      clearInterval(countdownRef.current)
      countdownRef.current = null
    }
    setCountdown(null)
  }

  const startCountdown = (seconds) => {
    clearCountdown()
    setCountdown(seconds)
    countdownRef.current = setInterval(() => {
      setCountdown(prev => {
        if (prev <= 1) {
          clearInterval(countdownRef.current)
          countdownRef.current = null
          return 0
        }
        return prev - 1
      })
    }, 1000)
  }

  const connectWs = () => {
    if (wsRef.current) {
      wsRef.current.close()
    }
    setStatus('connecting')
    setImageData(null)
    setLastUpdate(null)
    clearCountdown()

    const token = localStorage.getItem('token') || ''
    const wsUrl = `ws://${window.location.host}/ws/screen/${agentId}?token=${encodeURIComponent(token)}`
    const ws = new WebSocket(wsUrl)
    wsRef.current = ws

    ws.onopen = () => {
      // 等待服务器推送 waiting 消息
      setStatus('connecting')
    }

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)

        if (data.type === 'waiting') {
          // 服务器告知心跳间隔，开始倒计时
          setStatus('waiting')
          startCountdown(data.heartbeatInterval || 30)
          return
        }

        // 收到截图帧
        if (data.imageData) {
          clearCountdown()
          setStatus('live')
          setImageData(data.imageData)
          setLastUpdate(
            data.timestamp
              ? new Date(data.timestamp).toLocaleTimeString('zh-CN')
              : new Date().toLocaleTimeString('zh-CN')
          )
        }
      } catch (e) {
        console.error('[Screen] Failed to parse message:', e)
      }
    }

    ws.onclose = (event) => {
      clearCountdown()
      if (event.code === 1011) {
        setStatus('timeout')
        setErrorMsg('监控时间已达上限，连接已断开')
      } else if (event.code !== 1000) {
        setStatus('error')
        setErrorMsg('连接已断开')
      }
    }

    ws.onerror = () => {
      clearCountdown()
      setStatus('error')
      setErrorMsg('WebSocket 连接失败，请检查网络或权限')
    }
  }

  useEffect(() => {
    if (!visible || !agentId) return
    connectWs()
    return () => {
      clearCountdown()
      if (wsRef.current) {
        wsRef.current.close(1000, 'user closed')
      }
    }
  }, [visible, agentId])

  const handleClose = () => {
    clearCountdown()
    if (wsRef.current) {
      wsRef.current.close(1000, 'user closed')
    }
    setImageData(null)
    setStatus('connecting')
    onClose()
  }

  const renderStatusBar = () => {
    if (status === 'connecting') {
      return <Alert type="info" message="正在建立连接..." showIcon style={{ marginBottom: 8 }} />
    }
    if (status === 'waiting') {
      return (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 8 }}
          message={
            <Space>
              <span>
                将在 <Text strong style={{ color: '#1890ff' }}>{countdown ?? '...'}</Text> 秒后回传监控画面
              </span>
              <Progress
                percent={countdown != null ? Math.round((countdown / 30) * 100) : 100}
                showInfo={false}
                size="small"
                style={{ width: 120, marginBottom: 0 }}
                strokeColor="#1890ff"
              />
            </Space>
          }
        />
      )
    }
    if (status === 'error') {
      return (
        <Alert type="error" message={errorMsg} showIcon style={{ marginBottom: 8 }}
          action={<Button size="small" onClick={connectWs}>重连</Button>} />
      )
    }
    if (status === 'timeout') {
      return (
        <Alert type="warning" message={errorMsg} showIcon style={{ marginBottom: 8 }}
          action={<Button size="small" onClick={connectWs}>重新开始</Button>} />
      )
    }
    return null
  }

  return (
    <Modal
      title={
        <Space>
          <VideoCameraOutlined style={{ color: status === 'live' ? '#52c41a' : '#999' }} />
          <span>屏幕监控 - {hostname || agentId}</span>
          {status === 'live' && (
            <span style={{ fontSize: 12, color: '#52c41a', fontWeight: 'normal' }}>● 实时</span>
          )}
        </Space>
      }
      open={visible}
      onCancel={handleClose}
      width={1280}
      style={{ top: 20 }}
      bodyStyle={{ padding: '12px', background: '#000', minHeight: 400 }}
      footer={[
        <Text key="time" type="secondary" style={{ fontSize: 12, marginRight: 'auto', color: '#999' }}>
          {lastUpdate ? `最后更新: ${lastUpdate}` : (status === 'live' ? '等待截图...' : '')}
        </Text>,
        <Button key="close" onClick={handleClose}>关闭</Button>
      ]}
      destroyOnClose
    >
      {renderStatusBar()}
      {imageData ? (
        <img
          src={imageData}
          alt="screen"
          style={{ width: '100%', display: 'block', borderRadius: 4 }}
        />
      ) : (
        <div style={{ color: '#555', textAlign: 'center', padding: '80px 0', fontSize: 14 }}>
          {status === 'live' && '等待截图...'}
        </div>
      )}
    </Modal>
  )
}

export default ScreenMonitorModal
