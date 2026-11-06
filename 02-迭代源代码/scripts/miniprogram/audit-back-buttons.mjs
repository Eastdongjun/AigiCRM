import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const automator = require('miniprogram-automator')

// 【2026-11-07 用户令"缺少返回上一级的按钮 你来寻找"】按真实入口路径逐页测栈深:
// 栈深=1 → 微信导航栏必显示 🏠 而非 ← → 无返回键。入口一律走应用中心同款跳转代码。
const mini = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:9424' })
console.log('connected')

const CASES = [
  ['客户列表', '/pages/customer/index'],
  ['学分排行(单榜)', '/pages/ranking/index?tab=score'],
  ['客户排行(单榜)', '/pages/ranking/index?tab=customer'],
  ['激励查询', '/pages/incentive/index?mode=query'],
  ['激励统计', '/pages/incentive/index?mode=stats'],
  ['核心六项指标', '/pages/indicator/index'],
  ['统计分析', '/pages/statistics/index'],
  ['审批管理', '/pages/approval/index'],
  ['提醒管理', '/pages/reminder/index'],
  ['公海池', '/pages/sea/index'],
  ['新增客户', '/pages/customer/form/index']
]

for (const [name, url] of CASES) {
  // 先回应用中心(tabBar, 模拟真实入口), 再以 navigateTo 进入(与 openApp 同款)
  await mini.switchTab('/pages/app/index')
  await new Promise((r) => setTimeout(r, 600))
  await mini.navigateTo(url)
  await new Promise((r) => setTimeout(r, 1600))
  const stack = await mini.pageStack()
  const page = await mini.currentPage()
  console.log(`${name}: 栈深=${stack.length} 页面=${page && page.path} ${stack.length === 1 ? '🚨无返回键' : '←有返回'}`)
  await mini.navigateBack()
  await new Promise((r) => setTimeout(r, 500))
}
await mini.disconnect()
console.log('AUDIT_DONE')
