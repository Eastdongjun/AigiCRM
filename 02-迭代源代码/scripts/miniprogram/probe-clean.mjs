import automator from 'miniprogram-automator'
const mini = await automator.connect({ wsEndpoint: process.env.WECHAT_AUTOMATOR_WS || 'ws://127.0.0.1:9421' })
try {
  await mini.evaluate(() => { wx.clearStorageSync() })
  console.log('storage cleared:', JSON.stringify((await mini.evaluate(() => wx.getStorageInfoSync().keys)).keys))
  const page = await mini.reLaunch('/pages/login/index')
  await new Promise(r => setTimeout(r, 1200))
  console.log('landed:', await page.path)
} finally { await mini.disconnect() }
