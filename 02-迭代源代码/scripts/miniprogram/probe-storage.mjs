import automator from 'miniprogram-automator'
const mini = await automator.connect({ wsEndpoint: process.env.WECHAT_AUTOMATOR_WS || 'ws://127.0.0.1:9421' })
try {
  const info = await mini.systemInfo()
  const keys = await mini.evaluate(() => ({ keys: wx.getStorageInfoSync().keys, demo: getApp().globalData.demoMode, base: getApp().globalData.apiBaseUrl }))
  console.log('storage keys:', JSON.stringify(keys.keys))
  console.log('globalData: demoMode=', keys.demo, 'apiBaseUrl=', keys.base)
} finally { await mini.disconnect() }
