import automator from 'miniprogram-automator'
const sleep = (ms) => new Promise(r => setTimeout(r, ms))
const mini = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:9423' })
try {
  await mini.reLaunch('/pages/home/index'); await sleep(1000)
  await mini.switchTab('/pages/files/index'); await sleep(3000)
  const out = await mini.evaluate(() => new Promise((resolve) => {
    const fs = wx.getFileSystemManager()
    const p = `${wx.env.USER_DATA_PATH}/e2e-probe.txt`
    fs.writeFile({ filePath: p, data: 'probe ' + Date.now(), success: () => {
      const app = getApp()
      const base = app.globalData.apiBaseUrl
      const token = wx.getStorageSync('AIGICRM_TOKEN::' + base)
      wx.uploadFile({
        url: base + '/api/file/upload', filePath: p, name: 'file',
        formData: { scope: 'public', folderId: '17' },
        header: { Authorization: 'Bearer ' + token },
        success: (res) => resolve({ status: res.statusCode, body: String(res.data).slice(0, 220) }),
        fail: (e) => resolve({ fail: e && e.errMsg })
      })
    }, fail: (e) => resolve({ writeFail: e && e.errMsg }) })
  }))
  console.log('文件夹17 直传探针:', JSON.stringify(out))
  const out2 = await mini.evaluate(() => new Promise((resolve) => {
    const app = getApp()
    const base = app.globalData.apiBaseUrl
    const token = wx.getStorageSync('AIGICRM_TOKEN::' + base)
    wx.uploadFile({
      url: base + '/api/file/upload', filePath: `${wx.env.USER_DATA_PATH}/e2e-probe.txt`, name: 'file',
      formData: { scope: 'public' },
      header: { Authorization: 'Bearer ' + token },
      success: (res) => resolve({ status: res.statusCode, body: String(res.data).slice(0, 220) }),
      fail: (e) => resolve({ fail: e && e.errMsg })
    })
  }))
  console.log('根目录 直传探针:', JSON.stringify(out2))
} finally { await mini.disconnect() }
