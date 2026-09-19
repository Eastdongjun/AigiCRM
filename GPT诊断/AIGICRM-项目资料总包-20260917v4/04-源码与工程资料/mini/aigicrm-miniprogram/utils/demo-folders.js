const STORAGE_KEY = 'demoFolders'

function readDemoFolders() {
  const value = wx.getStorageSync(STORAGE_KEY)
  return Array.isArray(value) ? value : []
}

function saveDemoFolder(name, id) {
  const folders = readDemoFolders()
  if (id) {
    const updated = folders.map((item) => String(item.id) === String(id) ? { ...item, name } : item)
    wx.setStorageSync(STORAGE_KEY, updated)
    return updated.find((item) => String(item.id) === String(id))
  }
  const folder = { id: `demo-folder-${Date.now()}`, name }
  wx.setStorageSync(STORAGE_KEY, [folder, ...folders])
  return folder
}

function removeDemoFolder(id) {
  const folders = readDemoFolders().filter((item) => String(item.id) !== String(id))
  wx.setStorageSync(STORAGE_KEY, folders)
}

module.exports = { readDemoFolders, saveDemoFolder, removeDemoFolder }
