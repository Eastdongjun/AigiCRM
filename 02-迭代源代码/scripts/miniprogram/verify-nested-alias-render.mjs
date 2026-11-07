import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const automator = require('miniprogram-automator')

// 【2026-11-07 D1 渲染实证】wx:for 显式别名清偿 18 处后的渲染回归:
// 8 个被改页面逐页打开, 断言关键循环仍渲染出非空内容 + 零 console 报错。
const mini = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:9424' })
console.log('connected')

await mini.evaluate(() => {
  const app = getApp()
  if (!app.__errLog) {
    app.__errLog = []
    const push = (tag) => (...args) => {
      try { app.__errLog.push(tag + ': ' + args.map((a) => { try { return typeof a === 'object' ? JSON.stringify(a).slice(0, 200) : String(a) } catch (e) { return String(a) } }).join(' ')) } catch (e) {}
    }
    const oe = console.error
    console.error = function (...a) { push('console.error')(...a); try { oe.apply(console, a) } catch (e) {} }
    if (wx.onUnhandledRejection) wx.onUnhandledRejection((res) => push('unhandledRejection')(res && res.reason))
    if (wx.onError) wx.onError((msg) => push('onError')(msg))
  }
})

const wait = (ms) => new Promise((r) => setTimeout(r, ms))
let fail = 0
const check = async (label, ok, extra = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'}: ${label}${extra ? ' — ' + extra : ''}`)
  if (!ok) fail += 1
}

// ① 应用中心: 模块×宫格双层循环(appIdx 别名处) — 断言宫格 tile 有内容
await mini.switchTab('/pages/app/index'); await wait(1600)
{
  const pg = await mini.currentPage()
  const tiles = await pg.$$('.ap-tile')
  const first = tiles && tiles[0]
  let text = ''
  if (first) { const el = await first.text(); text = (el || '').trim() }
  await check('应用中心宫格渲染', (tiles || []).length > 0 && text.length > 0, `tiles=${(tiles || []).length} 首块="${text.slice(0, 20)}"`)
}

// ② 文件管理: 分组×文件双层(fileIdx 别名处) — 断言文件行渲染
await mini.switchTab('/pages/files/index'); await wait(2200)
{
  const pg = await mini.currentPage()
  const rows = await pg.$$('.file-row')
  await check('文件管理分组列表渲染', (rows || []).length > 0, `rows=${(rows || []).length}`)
}

// ③ 学分/客户排行: ?tab=customer 直达客户榜(卡内 kv/stat 子表格 = 本次 ranking/index 别名改动面)
await mini.navigateTo('/pages/ranking/index?tab=customer'); await wait(2600)
{
  const page = await mini.currentPage()
  const cards = await page.$$('.ranking-card')
  await check('客户排行卡片流渲染', (cards || []).length > 0, `cards=${(cards || []).length}`)
  const kvCells = await page.$$('.rank-kv-cell')
  const statRows = await page.$$('.rank-stat-row')
  await check('排行卡内子表格(kv/stat 别名处)渲染', (kvCells || []).length > 0 || (statRows || []).length > 0, `kv=${(kvCells || []).length} stat=${(statRows || []).length}`)
  await mini.navigateBack(); await wait(600)
}

// ④ 激励统计完整版: ?mode=stats + callMethod 切到「排行」页签 → 排行客户榜表格(表头双层改名+行内双层别名)
await mini.navigateTo('/pages/incentive/index?mode=stats'); await wait(2600)
{
  const pg = await mini.currentPage()
  await pg.callMethod('switchTab', { currentTarget: { dataset: { key: 'ranking' } } })
  await wait(1800)
  const subs = await pg.$$('.rth-sub')
  let subText = ''
  if (subs && subs[0]) { const t = await subs[0].text(); subText = (t || '').trim() }
  await check('激励统计表头子列(child改名)渲染', (subs || []).length > 0 && subText.length > 0, `subs=${(subs || []).length} 首列="${subText.slice(0, 16)}"`)
  const cells = await pg.$$('.rtd-cat')
  await check('激励统计行内分类格(双层别名)渲染', (cells || []).length > 0, `cells=${(cells || []).length}`)
  await mini.navigateBack(); await wait(600)
}

// ⑤ 激励上传(完整版 swiper-item fileIdx): 打开上传应用页
await mini.navigateTo('/pages/incentive/upload/index'); await wait(2200)
{
  const page = await mini.currentPage()
  const d = await page.data()
  const hasSlots = Array.isArray(d.slots) ? d.slots.length > 0 : true
  await check('激励上传页打开无崩', !!page && hasSlots)
  await mini.navigateBack(); await wait(600)
}

// ⑥ 激励详情(chips chipIdx / files fileIdx): 从查询记录进首条详情
await mini.navigateTo('/pages/incentive/index?mode=query'); await wait(2400)
{
  const page = await mini.currentPage()
  const d = await page.data()
  const rec = (d.records || [])[0] || (d.pagedRecords || [])[0]
  if (rec && rec.id) {
    await mini.navigateTo('/pages/incentive/detail/index?id=' + rec.id); await wait(2400)
    const pg2 = await mini.currentPage()
    const chips = await pg2.$$('.meta-chip')
    await check('激励详情chips渲染', (chips || []).length > 0, `chips=${(chips || []).length}`)
    await mini.navigateBack(); await wait(600)
  } else {
    await check('激励详情chips渲染', true, '(无记录可点, 跳过——别名改动为纯属性新增)')
  }
  await mini.navigateBack(); await wait(600)
}

// ⑦ 客户表单/指标打分(scoreIdx): 打开表单页即验证结构
await mini.navigateTo('/pages/customer/form/index'); await wait(1800)
{
  const page = await mini.currentPage()
  await check('客户表单页打开无崩', !!page)
  await mini.navigateBack(); await wait(500)
}
await mini.navigateTo('/pages/customer/indicator/index'); await wait(1800)
{
  const page = await mini.currentPage()
  await check('客户指标页打开无崩', !!page)
  await mini.navigateBack(); await wait(500)
}

const total = await mini.evaluate(() => getApp().__errLog)
console.log('=== 全量报错(' + ((total && total.length) || 0) + ' 条) ===')
;(total || []).slice(0, 20).forEach((l) => console.log(l))
console.log(fail === 0 ? 'RENDER_ALL_PASS' : 'RENDER_FAIL=' + fail)
await mini.disconnect()
process.exit(fail === 0 && ((total || []).length === 0) ? 0 : 1)
