function errorMessage(error, fallback) {
  const message = error && typeof error.message === 'string' ? error.message.trim() : ''
  return message || fallback || '操作失败'
}

function showError(error, fallback) {
  wx.showToast({ title: errorMessage(error, fallback), icon: 'none' })
}

// 【PR-03/PR-04 重试入口】加载失败弹窗提供"重试"按钮，直接重发失败的加载动作。
// 页面并发多路加载时只弹一个重试窗（后续失败退化为 toast），避免弹窗堆叠；
// 未传可重试动作时同样退化为 toast。
let retryModalOpen = false

function showErrorWithRetry(error, fallback, retry) {
  if (typeof retry !== 'function' || retryModalOpen) {
    showError(error, fallback)
    return
  }
  retryModalOpen = true
  wx.showModal({
    title: fallback || '加载失败',
    content: errorMessage(error, fallback),
    confirmText: '重试',
    cancelText: '取消',
    success: (res) => {
      if (res.confirm) retry()
    },
    complete: () => {
      retryModalOpen = false
    }
  })
}

module.exports = { errorMessage, showError, showErrorWithRetry }
