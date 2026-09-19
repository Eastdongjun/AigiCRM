const STORAGE_KEY = 'demoProcessedReminders'

function readProcessedReminders() {
  const value = wx.getStorageSync(STORAGE_KEY)
  return Array.isArray(value) ? value : []
}

function isProcessed(id) { return readProcessedReminders().some((item) => String(item) === String(id)) }

function markProcessed(id) {
  if (!isProcessed(id)) wx.setStorageSync(STORAGE_KEY, [...readProcessedReminders(), id])
}

module.exports = { isProcessed, markProcessed }
