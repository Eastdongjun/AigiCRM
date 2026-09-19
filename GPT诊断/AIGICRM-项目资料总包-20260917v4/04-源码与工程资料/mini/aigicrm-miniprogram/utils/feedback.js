function errorMessage(error, fallback) {
  const message = error && typeof error.message === 'string' ? error.message.trim() : ''
  return message || fallback || '操作失败'
}

function showError(error, fallback) {
  wx.showToast({ title: errorMessage(error, fallback), icon: 'none' })
}

module.exports = { errorMessage, showError }
