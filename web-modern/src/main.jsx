import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import App from './App.jsx'
import './index.css'

// 安全的Ant Design主题配置
const theme = {
  token: {
    colorPrimary: '#3b82f6',
    borderRadius: 8,
    colorBgContainer: '#ffffff',
  },
  components: {
    Layout: {
      siderBg: '#001529',
      triggerBg: '#002140',
    },
    Menu: {
      darkItemBg: '#001529',
      darkSubMenuItemBg: '#000c17',
      darkItemSelectedBg: '#1890ff',
    },
    // 确保Modal和Message组件不会导致输入阻塞
    Modal: {
      zIndexPopup: 1000,
    },
    Message: {
      zIndexPopup: 1010,
    }
  }
}

// 添加全局防护措施
document.addEventListener('DOMContentLoaded', function() {
  // 确保body始终可交互
  document.body.style.pointerEvents = 'auto'
  document.body.style.overflow = 'auto'
  
  // 移除可能的Ant Design遮罩类
  document.body.classList.remove('ant-scrolling-effect')
  
  // 定期清理可能的遮罩元素
  setInterval(() => {
    // 只清理没有父容器的孤立遮罩
    const masks = document.querySelectorAll('.ant-modal-mask, .ant-drawer-mask')
    masks.forEach(mask => {
      if (!mask.closest('.ant-modal-wrap, .ant-drawer-wrap')) {
        console.log('清理孤立遮罩元素:', mask)
        mask.remove()
      }
    })
    
    // 确保body状态正常
    if (document.body.style.overflow === 'hidden' && !document.querySelector('.ant-modal-wrap, .ant-drawer-wrap')) {
      document.body.style.overflow = 'auto'
      document.body.style.pointerEvents = 'auto'
    }
  }, 2000)
})

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <ConfigProvider locale={zhCN} theme={theme}>
        <App />
      </ConfigProvider>
    </BrowserRouter>
  </React.StrictMode>,
)