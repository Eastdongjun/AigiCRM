import automator from 'miniprogram-automator'
const mini = await automator.connect({ wsEndpoint: process.env.WECHAT_AUTOMATOR_WS || 'ws://127.0.0.1:9421' })
const errs = []
try {
  mini.on('console', (msg) => { try { if (msg.type === 'error') errs.push((msg.args||[]).map(a => typeof a === 'string' ? a : JSON.stringify(a)).join(' ').slice(0,200)) } catch (e) {} })
  await mini.reLaunch('/pages/login/index'); await new Promise(r => setTimeout(r, 2500))
  console.log('login 页 error 数:', errs.length)
  errs.slice(0, 6).forEach((e, i) => console.log(`  ${i+1}.`, e))
} finally { await mini.disconnect() }
