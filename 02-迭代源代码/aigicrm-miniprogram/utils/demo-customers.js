const STORAGE_KEY = 'demoCustomers'

function readDemoCustomers() {
  const value = wx.getStorageSync(STORAGE_KEY)
  return Array.isArray(value) ? value : []
}

function saveDemoCustomer(form, id) {
  const customers = readDemoCustomers()
  if (id) {
    const updated = customers.map((item) => String(item.id) === String(id) ? { ...item, ...form, id } : item)
    wx.setStorageSync(STORAGE_KEY, updated)
    return updated.find((item) => String(item.id) === String(id))
  }
  const stamp = Date.now()
  const next = { ...form, id: `demo-${stamp}`, customerCode: `DEMO-${stamp}`, status: '正常', ownerName: '张三', officeName: '晋蒙业务部' }
  wx.setStorageSync(STORAGE_KEY, [next, ...customers])
  return next
}

module.exports = { readDemoCustomers, saveDemoCustomer }
