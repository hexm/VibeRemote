import React, { useState, useEffect } from 'react'

const SimpleLogin = ({ onLogin }) => {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [debugInfo, setDebugInfo] = useState('')

  useEffect(() => {
    // 检查页面状态
    const checkPageState = () => {
      const info = {
        bodyOverflow: document.body.style.overflow,
        bodyClasses: document.body.className,
        documentReadyState: document.readyState,
        timestamp: new Date().toLocaleTimeString()
      }
      setDebugInfo(JSON.stringify(info, null, 2))
    }
    
    checkPageState()
    
    // 监听页面变化
    const observer = new MutationObserver(checkPageState)
    observer.observe(document.body, { 
      attributes: true, 
      attributeFilter: ['class', 'style'] 
    })
    
    return () => observer.disconnect()
  }, [])

  const handleSubmit = async (e) => {
    e.preventDefault()
    console.log('提交登录:', { username, password })
    
    if (!username || !password) {
      alert('请输入用户名和密码')
      return
    }
    
    setLoading(true)
    try {
      const result = await onLogin({ username, password })
      console.log('登录结果:', result)
    } catch (error) {
      console.error('登录错误:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleInputFocus = (field) => {
    console.log(`${field} 输入框获得焦点`)
  }

  const handleInputChange = (field, value) => {
    console.log(`${field} 输入框值变化:`, value)
    if (field === 'username') {
      setUsername(value)
    } else {
      setPassword(value)
    }
  }

  return (
    <div style={{ 
      minHeight: '100vh', 
      display: 'flex', 
      alignItems: 'center', 
      justifyContent: 'center',
      backgroundColor: '#f0f2f5'
    }}>
      <div style={{
        backgroundColor: 'white',
        padding: '40px',
        borderRadius: '8px',
        boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
        width: '400px'
      }}>
        <h2 style={{ textAlign: 'center', marginBottom: '30px' }}>LightScript 登录</h2>
        
        <form onSubmit={handleSubmit}>
          <div style={{ marginBottom: '20px' }}>
            <label style={{ display: 'block', marginBottom: '8px' }}>用户名:</label>
            <input
              type="text"
              value={username}
              onChange={(e) => handleInputChange('username', e.target.value)}
              onFocus={() => handleInputFocus('username')}
              disabled={loading}
              placeholder="请输入用户名"
              style={{
                width: '100%',
                padding: '12px',
                border: '1px solid #d9d9d9',
                borderRadius: '6px',
                fontSize: '14px',
                backgroundColor: 'white',
                pointerEvents: 'auto',
                zIndex: 9999
              }}
            />
          </div>
          
          <div style={{ marginBottom: '20px' }}>
            <label style={{ display: 'block', marginBottom: '8px' }}>密码:</label>
            <input
              type="password"
              value={password}
              onChange={(e) => handleInputChange('password', e.target.value)}
              onFocus={() => handleInputFocus('password')}
              disabled={loading}
              placeholder="请输入密码"
              style={{
                width: '100%',
                padding: '12px',
                border: '1px solid #d9d9d9',
                borderRadius: '6px',
                fontSize: '14px',
                backgroundColor: 'white',
                pointerEvents: 'auto',
                zIndex: 9999
              }}
            />
          </div>
          
          <button
            type="submit"
            disabled={loading}
            style={{
              width: '100%',
              padding: '12px',
              backgroundColor: loading ? '#ccc' : '#1890ff',
              color: 'white',
              border: 'none',
              borderRadius: '6px',
              fontSize: '16px',
              cursor: loading ? 'not-allowed' : 'pointer',
              pointerEvents: 'auto',
              zIndex: 9999
            }}
          >
            {loading ? '登录中...' : '登录'}
          </button>
        </form>
        
        <div style={{ marginTop: '20px', padding: '15px', backgroundColor: '#f6f8fa', borderRadius: '6px' }}>
          <p style={{ margin: '0 0 10px 0', fontWeight: 'bold' }}>默认账号:</p>
          <p style={{ margin: '5px 0' }}>管理员: admin / admin123</p>
          <p style={{ margin: '5px 0' }}>普通用户: user / user123</p>
        </div>
        
        {/* 调试信息 */}
        <details style={{ marginTop: '20px', fontSize: '12px' }}>
          <summary>调试信息</summary>
          <pre style={{ backgroundColor: '#f0f0f0', padding: '10px', marginTop: '10px' }}>
            {debugInfo}
          </pre>
        </details>
      </div>
    </div>
  )
}

export default SimpleLogin