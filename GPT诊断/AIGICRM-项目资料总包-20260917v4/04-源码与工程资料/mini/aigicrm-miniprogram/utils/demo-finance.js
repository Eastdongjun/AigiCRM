const STORAGE_KEY = 'demoFinance'

function readDemoFinance(customerId) {
  const value = wx.getStorageSync(STORAGE_KEY) || {}
  return value[customerId] || { invoices: [], payments: [] }
}

function saveDemoFinance(customerId, mode, form) {
  const value = wx.getStorageSync(STORAGE_KEY) || {}
  const existing = readDemoFinance(customerId)
  const record = { ...form, id: `demo-${mode}-${Date.now()}` }
  const key = mode === 'payment' ? 'payments' : 'invoices'
  value[customerId] = { ...existing, [key]: [record, ...existing[key]] }
  wx.setStorageSync(STORAGE_KEY, value)
  return record
}

function deleteDemoFinance(customerId, mode, recordId) {
  const value = wx.getStorageSync(STORAGE_KEY) || {}
  const existing = readDemoFinance(customerId)
  const key = mode === 'payment' ? 'payments' : 'invoices'
  value[customerId] = {
    ...existing,
    [key]: existing[key].filter((item) => String(item.id) !== String(recordId))
  }
  wx.setStorageSync(STORAGE_KEY, value)
}

module.exports = { readDemoFinance, saveDemoFinance, deleteDemoFinance }
