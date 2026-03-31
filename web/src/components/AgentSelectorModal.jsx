import React, { useState, useEffect, useMemo, useRef } from 'react'
import { Modal, Button, Input, Select, Tag, Checkbox, Space, Typography, Badge, Empty } from 'antd'
import { SearchOutlined, TeamOutlined, DesktopOutlined, CloseOutlined, CheckOutlined } from '@ant-design/icons'

const { Text } = Typography
const { Option } = Select

/**
 * AgentSelectorModal
 * Props:
 *   open: boolean
 *   onConfirm: (agentIds: string[]) => void
 *   onCancel: () => void
 *   agents: Agent[]        — 全量 agent 列表
 *   groups: Group[]        — 分组列表
 *   initialSelected: string[]  — 打开时的初始已选
 *   selectedAgentDetails: Agent[] — 当前分组已选 agent 详情，兜底用于回显
 */
const AgentSelectorModal = ({
  open,
  onConfirm,
  onCancel,
  agents = [],
  groups = [],
  initialSelected = [],
  selectedAgentDetails = [],
  confirmLoading = false
}) => {
  // 左侧筛选条件
  const [groupFilter, setGroupFilter] = useState('all')
  const [statusFilter, setStatusFilter] = useState('all')
  const [leftSearch, setLeftSearch] = useState('')

  // 右侧搜索
  const [rightSearch, setRightSearch] = useState('')

  // 当前已选（Modal 内部临时状态，确认后才回传）
  const [selected, setSelected] = useState([])
  const wasOpenRef = useRef(false)

  // 合并当前列表和已选详情，确保弹窗始终能回显已选节点
  const mergedAgents = useMemo(() => {
    const merged = new Map()

    agents.forEach(agent => {
      if (agent?.agentId) {
        merged.set(agent.agentId, agent)
      }
    })

    selectedAgentDetails.forEach(agent => {
      if (agent?.agentId && !merged.has(agent.agentId)) {
        merged.set(agent.agentId, agent)
      }
    })

    return Array.from(merged.values())
  }, [agents, selectedAgentDetails])

  // 仅在弹窗从关闭切到打开时同步初始值，避免父组件重渲染把用户当前选择覆盖掉
  useEffect(() => {
    if (open && !wasOpenRef.current) {
      setSelected([...initialSelected])
      setGroupFilter('all')
      setStatusFilter('all')
      setLeftSearch('')
      setRightSearch('')
    }

    wasOpenRef.current = open
  }, [open, initialSelected])

  // 构建 groupId -> agentId[] 映射
  const groupAgentMap = useMemo(() => {
    const map = {}
    groups.forEach(g => {
      map[g.id] = (g.agents || []).map(a => a.agentId)
    })
    return map
  }, [groups])

  // 左侧候选列表（应用所有筛选）
  const leftCandidates = useMemo(() => {
    return mergedAgents.filter(agent => {
      // 分组过滤
      if (groupFilter !== 'all') {
        if (groupFilter === 'ungrouped') {
          const inAnyGroup = groups.some(g =>
            (g.agents || []).some(a => a.agentId === agent.agentId)
          )
          if (inAnyGroup) return false
        } else {
          const groupAgents = groupAgentMap[groupFilter] || []
          if (!groupAgents.includes(agent.agentId)) return false
        }
      }
      // 状态过滤
      if (statusFilter !== 'all') {
        if (statusFilter === 'online' && agent.status !== 'ONLINE') return false
        if (statusFilter === 'offline' && agent.status === 'ONLINE') return false
      }
      // 搜索过滤
      if (leftSearch) {
        const q = leftSearch.toLowerCase()
        const matchHost = agent.hostname?.toLowerCase().includes(q)
        const matchIp = agent.ip?.toLowerCase().includes(q)
        const matchUser = agent.startUser?.toLowerCase().includes(q)
        const matchId = agent.agentId?.toLowerCase().includes(q)
        if (!matchHost && !matchIp && !matchUser && !matchId) return false
      }
      return true
    })
  }, [mergedAgents, groups, groupFilter, statusFilter, leftSearch, groupAgentMap])

  // 右侧已选列表（应用右侧搜索）
  const rightAgents = useMemo(() => {
    return mergedAgents.filter(a => selected.includes(a.agentId)).filter(agent => {
      if (!rightSearch) return true
      const q = rightSearch.toLowerCase()
      return (
        agent.hostname?.toLowerCase().includes(q) ||
        agent.ip?.toLowerCase().includes(q) ||
        agent.startUser?.toLowerCase().includes(q) ||
        agent.agentId?.toLowerCase().includes(q)
      )
    })
  }, [mergedAgents, selected, rightSearch])

  // 左侧全选状态
  const leftFilteredIds = leftCandidates.map(a => a.agentId)
  const leftSelectedCount = leftFilteredIds.filter(id => selected.includes(id)).length
  const leftAllChecked = leftFilteredIds.length > 0 && leftSelectedCount === leftFilteredIds.length
  const leftIndeterminate = leftSelectedCount > 0 && !leftAllChecked

  const toggleLeftAll = () => {
    if (leftAllChecked) {
      setSelected(prev => prev.filter(id => !leftFilteredIds.includes(id)))
    } else {
      setSelected(prev => {
        const merged = new Set([...prev, ...leftFilteredIds])
        return Array.from(merged)
      })
    }
  }

  const toggleAgent = (agentId) => {
    setSelected(prev =>
      prev.includes(agentId) ? prev.filter(id => id !== agentId) : [...prev, agentId]
    )
  }

  const removeAgent = (agentId) => {
    setSelected(prev => prev.filter(id => id !== agentId))
  }

  const clearAll = () => setSelected([])

  const handleConfirm = () => onConfirm(selected)

  return (
    <Modal
      title={
        <Space>
          <DesktopOutlined />
          选择执行节点
        </Space>
      }
      open={open}
      onCancel={onCancel}
      width={860}
      footer={[
        <Button key="clear" danger onClick={clearAll} disabled={selected.length === 0}>
          清空已选
        </Button>,
        <Button key="cancel" onClick={onCancel}>取消</Button>,
        <Button
          key="confirm"
          type="primary"
          icon={<CheckOutlined />}
          onClick={handleConfirm}
          loading={confirmLoading}
        >
          确认选择（{selected.length} 台）
        </Button>
      ]}
      styles={{ body: { padding: '12px 24px' } }}
    >
      <div style={{ display: 'flex', gap: 12, height: 460 }}>
        {/* 左侧：候选列表 */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', border: '1px solid #f0f0f0', borderRadius: 6, overflow: 'hidden' }}>
          {/* 筛选栏 */}
          <div style={{ padding: '10px 12px', background: '#fafafa', borderBottom: '1px solid #f0f0f0' }}>
            <Space wrap size={6}>
              <Select
                value={groupFilter}
                onChange={setGroupFilter}
                size="small"
                style={{ width: 130 }}
                prefix={<TeamOutlined />}
              >
                <Option value="all">全部分组</Option>
                <Option value="ungrouped">未分组</Option>
                {groups.map(g => (
                  <Option key={g.id} value={g.id}>
                    {g.name}
                    <Text type="secondary" style={{ fontSize: 11, marginLeft: 4 }}>({g.agentCount || 0})</Text>
                  </Option>
                ))}
              </Select>
              <Select
                value={statusFilter}
                onChange={setStatusFilter}
                size="small"
                style={{ width: 90 }}
              >
                <Option value="all">全部</Option>
                <Option value="online">在线</Option>
                <Option value="offline">离线</Option>
              </Select>
            </Space>
            <Input
              size="small"
              placeholder="搜索主机名 / 启动用户 / IP / ID"
              prefix={<SearchOutlined />}
              value={leftSearch}
              onChange={e => setLeftSearch(e.target.value)}
              allowClear
              style={{ marginTop: 8 }}
            />
          </div>

          {/* 全选行 */}
          <div style={{ padding: '8px 12px', borderBottom: '1px solid #f0f0f0', background: '#fff' }}>
            <Checkbox
              checked={leftAllChecked}
              indeterminate={leftIndeterminate}
              onChange={toggleLeftAll}
            >
              <Text style={{ fontSize: 13 }}>
                全选当前筛选结果
                <Text type="secondary" style={{ marginLeft: 6 }}>({leftCandidates.length} 台)</Text>
              </Text>
            </Checkbox>
          </div>

          {/* agent 列表 */}
          <div style={{ flex: 1, overflowY: 'auto' }}>
            {leftCandidates.length === 0 ? (
              <Empty description="无匹配节点" style={{ marginTop: 60 }} image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
              leftCandidates.map(agent => (
                <div
                  key={agent.agentId}
                  style={{
                    padding: '7px 12px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    borderBottom: '1px solid #f9f9f9',
                    background: selected.includes(agent.agentId) ? '#e6f4ff' : '#fff',
                    transition: 'background 0.15s',
                  }}
                >
                  <div style={{ flex: 0 }}>
                    <Checkbox 
                      checked={selected.includes(agent.agentId)} 
                      onChange={() => toggleAgent(agent.agentId)}
                      style={{ cursor: 'pointer' }}
                    />
                  </div>
                  <div 
                    style={{ flex: 1, cursor: 'pointer' }}
                    onClick={() => toggleAgent(agent.agentId)}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, width: '100%' }}>
                      <Badge status={agent.status === 'ONLINE' ? 'success' : 'default'} />
                      <Text
                        style={{
                          flex: 1,
                          fontSize: 13,
                          minWidth: 0
                        }}
                        ellipsis
                      >
                        {agent.hostname || agent.agentId}
                      </Text>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 2 }}>
                      <Text type="secondary" style={{ fontSize: 11 }}>
                        启动用户: {agent.startUser || '-'}
                      </Text>
                      <Text type="secondary" style={{ fontSize: 11 }}>
                        IP: {agent.ip || '-'}
                      </Text>
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* 右侧：已选列表 */}
        <div style={{ width: 280, display: 'flex', flexDirection: 'column', border: '1px solid #f0f0f0', borderRadius: 6, overflow: 'hidden' }}>
          <div style={{ padding: '10px 12px', background: '#fafafa', borderBottom: '1px solid #f0f0f0' }}>
            <Text strong style={{ fontSize: 13 }}>
              已选节点
              <Tag color="blue" style={{ marginLeft: 8 }}>{selected.length} 台</Tag>
            </Text>
            <Input
              size="small"
              placeholder="搜索已选主机 / 用户 / IP / ID"
              prefix={<SearchOutlined />}
              value={rightSearch}
              onChange={e => setRightSearch(e.target.value)}
              allowClear
              style={{ marginTop: 8 }}
            />
          </div>

          <div style={{ flex: 1, overflowY: 'auto' }}>
            {rightAgents.length === 0 ? (
              <Empty description={selected.length === 0 ? '尚未选择节点' : '无匹配'} style={{ marginTop: 60 }} image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
              rightAgents.map(agent => (
                <div
                  key={agent.agentId}
                  style={{
                    padding: '7px 12px',
                    borderBottom: '1px solid #f9f9f9',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <Badge status={agent.status === 'ONLINE' ? 'success' : 'default'} />
                    <Text style={{ flex: 1, fontSize: 13, minWidth: 0 }} ellipsis>
                      {agent.hostname || agent.agentId}
                    </Text>
                    <CloseOutlined
                      style={{ fontSize: 11, color: '#999', cursor: 'pointer' }}
                      onClick={() => removeAgent(agent.agentId)}
                    />
                  </div>
                  <div style={{ marginTop: 2 }}>
                    <Text type="secondary" style={{ fontSize: 11, display: 'block' }}>
                      启动用户: {agent.startUser || '-'}
                    </Text>
                    <Text type="secondary" style={{ fontSize: 11, display: 'block' }}>
                      IP: {agent.ip || '-'}
                    </Text>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </Modal>
  )
}

export default AgentSelectorModal
