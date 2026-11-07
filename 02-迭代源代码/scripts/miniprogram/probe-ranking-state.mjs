import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const automator = require('miniprogram-automator')

const mini = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:9424' })
const wait = (ms) => new Promise((r) => setTimeout(r, ms))

await mini.navigateTo('/pages/incentive/index?mode=stats'); await wait(3000)
const page = await mini.currentPage()
const d = await page.data()
console.log('mode =', d.mode)
console.log('rankTab =', d.rankTab)
const iv = d.incentiveView || {}
console.log('incentiveView.showRanking =', iv.showRanking)
console.log('incentiveView.showRankCustomer =', iv.showRankCustomer)
console.log('customerRankPaged =', Array.isArray(d.customerRankPaged) ? d.customerRankPaged.length : typeof d.customerRankPaged)
console.log('rankParents =', Array.isArray(d.rankParents) ? d.rankParents.map((p) => p.categoryName).join('/') : typeof d.rankParents)
const tabs = await page.$$('.rank-subtabs .tab')
console.log('.rank-subtabs .tab =', (tabs || []).length)
const panel = await page.$$('.ranking-panel')
console.log('.ranking-panel =', (panel || []).length)
await mini.disconnect()
console.log('PROBE2_DONE')
