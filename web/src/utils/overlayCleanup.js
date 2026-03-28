const OVERLAY_ROOT_SELECTORS = [
  '.ant-modal-root',
  '.ant-drawer-root',
]

const OVERLAY_MASK_SELECTORS = [
  '.ant-modal-mask',
  '.ant-drawer-mask',
]

function isVisible(element) {
  if (!element) {
    return false
  }

  const style = window.getComputedStyle(element)
  return style.display !== 'none' && style.visibility !== 'hidden' && style.opacity !== '0'
}

function hasActiveOverlay() {
  const wrappers = document.querySelectorAll('.ant-modal-wrap, .ant-drawer, .ant-drawer-content-wrapper')
  return Array.from(wrappers).some(isVisible)
}

export function cleanupOverlayArtifacts({ force = false } = {}) {
  if (typeof document === 'undefined') {
    return
  }

  const activeOverlayExists = hasActiveOverlay()
  if (!force && activeOverlayExists) {
    return
  }

  OVERLAY_ROOT_SELECTORS.forEach((selector) => {
    document.querySelectorAll(selector).forEach((root) => {
      const hasVisibleChild = root.querySelector('.ant-modal-wrap, .ant-drawer, .ant-drawer-content-wrapper')
      if (force || !hasVisibleChild || !isVisible(hasVisibleChild)) {
        root.remove()
      }
    })
  })

  OVERLAY_MASK_SELECTORS.forEach((selector) => {
    document.querySelectorAll(selector).forEach((mask) => {
      const root = mask.closest('.ant-modal-root, .ant-drawer-root')
      if (force || !root) {
        mask.remove()
      }
    })
  })

  document.body.classList.remove('ant-scrolling-effect')
  document.body.style.removeProperty('overflow')
  document.body.style.removeProperty('overflow-y')
  document.body.style.removeProperty('pointer-events')
}
