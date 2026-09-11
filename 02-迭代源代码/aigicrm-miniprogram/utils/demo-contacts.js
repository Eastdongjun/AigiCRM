const STORAGE_KEY = 'demoContacts'

function readDemoContacts(customerId) {
  const value = wx.getStorageSync(STORAGE_KEY) || {}
  return Array.isArray(value[customerId]) ? value[customerId] : []
}

function saveDemoContact(customerId, form) {
  const value = wx.getStorageSync(STORAGE_KEY) || {}
  const contacts = readDemoContacts(customerId)
  const contact = { ...form, id: `demo-contact-${Date.now()}` }
  value[customerId] = [contact, ...contacts]
  wx.setStorageSync(STORAGE_KEY, value)
  return contact
}

module.exports = { readDemoContacts, saveDemoContact }
