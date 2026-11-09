import automator from 'miniprogram-automator'
const mini = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:9423' })
try {
  // 当前栈状态(举证 🏠 成因)
  let stack = await mini.pageStack()
  console.log('修复前栈:', stack.map(p => p.path).join(' <- '), '(长度', stack.length + ')')
  // 按真实用户路径重建: 首页 → 应用中心 → 激励统计
  await mini.reLaunch('/pages/home/index')
  await new Promise(r => setTimeout(r, 900))
  await mini.switchTab('/pages/app/index')
  await new Promise(r => setTimeout(r, 1500))
  const appPage = await mini.currentPage()
  // 点真实入口卡片(激励统计) — openApp 走 navigateTo
  await appPage.callMethod('openApp', { currentTarget: { dataset: { key: 'incentiveStats' } } })
  await new Promise(r => setTimeout(r, 4000))
  stack = await mini.pageStack()
  console.log('修复后栈:', stack.map(p => p.path).join(' <- '), '(长度', stack.length + ')')
  console.log('栈深>1 = 微信原生「<」返回键在(回应用中心)')
  await mini.screenshot({ path: '/tmp/mp-back-proof.png' })
  console.log('shot saved')
} finally { await mini.disconnect() }
