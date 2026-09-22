// 沙箱自清(2026-09-22): verify 脚本会向 storage 写入假 token/演示缓存,若不清会污染
// 后续人工会话(实测: 真实模式+假 token → 全站 401 风暴, 调试器 Console 刷屏)。
// 挂在 verify:mobile-parity 聚合链末尾, 成功跑完即自动清理。
import automator from 'miniprogram-automator'

const mini = await automator.connect({ wsEndpoint: process.env.WECHAT_AUTOMATOR_WS || 'ws://127.0.0.1:9421' })
try {
  await mini.evaluate(() => { wx.clearStorageSync() })
  const page = await mini.reLaunch('/pages/login/index')
  console.log('PASS: automator 沙箱已自清(storage 清空, 回落登录页', await page.path, ')')
} finally {
  await mini.disconnect()
}
