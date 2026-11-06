import { mkdirSync } from 'node:fs'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const automator = require('miniprogram-automator')

mkdirSync('/tmp/mp-debug-shots', { recursive: true })
const mini = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:9424' })
console.log('connected')
const shot = async (name) => { await mini.screenshot({ path: `/tmp/mp-debug-shots/${name}.png` }); console.log('shot:', name) }

// —— ④ 审批队列卡(无描边)
try {
  await mini.switchTab('/pages/approval/index')
  await new Promise((r) => setTimeout(r, 2500))
  await shot('4-approval-queue')
} catch (e) { console.log('approval skip:', String(e).slice(0, 120)) }

// —— ⑤ 登录页记住勾选: 暂存 token→清→进登录页→勾选→截图→恢复
try {
  const KEYS = ['AIGICRM_TOKEN::http://111.229.37.220', 'AIGICRM_TOKEN::https://www.aigicrm.com']
  const saved = {}
  for (const k of KEYS) {
    const v = await mini.callWxMethod('getStorageSync', k)
    if (v) saved[k] = v
  }
  console.log('saved token keys:', Object.keys(saved).length)
  for (const k of Object.keys(saved)) await mini.callWxMethod('removeStorageSync', k)
  await mini.reLaunch('/pages/login/index')
  await new Promise((r) => setTimeout(r, 2200))
  const page = await mini.currentPage()
  console.log('login page:', page && page.path)
  await page.callMethod('toggleRememberAccount')
  await page.callMethod('toggleRememberPassword')
  const ld = await page.data()
  console.log('rememberAccount:', ld.rememberAccount, 'rememberPassword:', ld.rememberPassword, 'username:', JSON.stringify(ld.username || ''))
  await shot('5-login-remember')
  for (const k of Object.keys(saved)) await mini.callWxMethod('setStorageSync', k, saved[k])
  console.log('token restored')
} catch (e) { console.log('login skip:', String(e).slice(0, 160)) }

await mini.disconnect()
console.log('DONE-PART2')
