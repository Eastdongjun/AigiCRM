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

function updateDemoContact(customerId, contactId, form) {
  const value = wx.getStorageSync(STORAGE_KEY) || {}
  const contacts = readDemoContacts(customerId)
  const nextContacts = contacts.map((item) => String(item.id) === String(contactId) ? { ...item, ...form, id: item.id } : item)
  value[customerId] = nextContacts
  wx.setStorageSync(STORAGE_KEY, value)
  return nextContacts.find((item) => String(item.id) === String(contactId)) || null
}

function deleteDemoContact(customerId, contactId) {
  const value = wx.getStorageSync(STORAGE_KEY) || {}
  value[customerId] = readDemoContacts(customerId).filter((item) => String(item.id) !== String(contactId))
  wx.setStorageSync(STORAGE_KEY, value)
}

module.exports = { readDemoContacts, saveDemoContact, updateDemoContact, deleteDemoContact }
