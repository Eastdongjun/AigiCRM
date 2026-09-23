import automator from 'miniprogram-automator'
const mini = await automator.connect({ wsEndpoint: process.env.WECHAT_AUTOMATOR_WS || 'ws://127.0.0.1:9421' })
try {
  const errs = []
  mini.on('console', (msg) => { if (msg.type === 'error' || msg.type === 'warn') errs.push({ type: msg.type, text: (msg.args||[]).map(a => typeof a === 'string' ? a : JSON.stringify(a)).join(' ').slice(0, 300) }) })
  // 冷启动完整走一遍 tab 链,复现用户看到的报错
  await mini.reLaunch('/pages/login/index'); await new Promise(r => setTimeout(r, 1500))
  await mini.evaluate(() => { const app = getApp(); app.globalData.demoMode = true; app.globalData.permissions = ['customer:view','incentive:view','approval:view','reminder:view','statistics:view','sea:view','file:view'] })
  for (const p of ['/pages/home/index','/pages/customer/index','/pages/incentive/index','/pages/sea/index','/pages/statistics/index']) {
    try { await mini.reLaunch(p); await new Promise(r => setTimeout(r, 2000)) } catch (e) { errs.push({ type: 'nav', text: p + ' :: ' + e.message }) }
  }
  await new Promise(r => setTimeout(r, 1000))
  console.log('=== 捕获 error/warn 共', errs.length, '条 ===')
  const seen = new Set()
  for (const e of errs) { const k = e.text.slice(0, 80); if (seen.has(k)) continue; seen.add(k); console.log(`[${e.type}]`, e.text) }
} finally { await mini.disconnect() }
