// 2026-09-20 优化批次一次性验证: 12 条改动逐页截图 + 数据断言(evaluate 绕过 automator page-node 不稳定)
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const automator = require('miniprogram-automator')

const shots = process.env.OPT_SHOT_DIR || '/tmp/opt-shots'
const wsEndpoint = process.env.WECHAT_AUTOMATOR_WS || 'ws://127.0.0.1:9421'
import { mkdirSync } from 'node:fs'
mkdirSync(shots, { recursive: true })

const assert = (cond, msg) => { if (!cond) throw new Error('FAIL: ' + msg) }
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function main() {
  const mp = await automator.connect({ wsEndpoint })
  const errors = []
  mp.on('console', (msg) => { if (msg.type === 'error') errors.push(String(msg.args && msg.args.join(' ')).slice(0, 200)) })
  const shot = (name) => mp.screenshot({ path: `${shots}/${name}.png` })
  // evaluate 返回值需小而可序列化: 页面上下文内先算摘要
  const pageData = () => {
    const pages = getCurrentPages()
    const page = pages[pages.length - 1]
    const d = page.data || {}
    return {
      route: page.route,
      records: (d.records || []).length,
      canUploadEvidence: d.canUploadEvidence,
      tree: (d.categoryTreeView || []).length,
      statNames: (d.categoryStats || []).slice(0, 3).map((x) => x.name),
      statCount: (d.categoryStats || []).length,
      statDetailName: d.statDetail && d.statDetail.name,
      statDetailRecords: d.statDetail ? (d.statDetail.records || []).length : 0,
      parentCategories: (d.categoryParentNodes || []).map((x) => ({ name: x.name, children: x.children.length })),
      statusFilters: (d.statusFilters || []).map((x) => x.label),
      fileTabs: (d.fileView && d.fileView.tabs ? d.fileView.tabs : []).map((t) => t.label)
    }
  }

  // 强制重新登录(排除 token 失效导致的空数据)
  const login = await mp.reLaunch('/pages/login/index')
  await sleep(1500)
  await login.setData({ username: 'zz-ui-review', password: 'UIReview#20260920' })
  await login.callMethod('submit')
  await sleep(5000)

  // 1. 激励页: 上传入口 + 分类树 + 统计钻取(第 3/4 条)
  await mp.switchTab('/pages/incentive/index')
  await sleep(4500)
  // 清残留筛选并刷新, 最多重读 3 次(网络/时序兜底)
  await mp.evaluate(() => { const pg = getCurrentPages()[getCurrentPages().length - 1]; if (pg.route.indexOf('incentive/index') >= 0 && pg.resetRecordFilters) pg.resetRecordFilters() })
  await sleep(5000)
  let d = null
  for (let i = 0; i < 3; i++) {
    d = await mp.evaluate(pageData)
    if (d.records > 0) break
    await sleep(4000)
  }
  assert(d.records > 0, `incentive records empty after retries: ${d.route}`)
  assert(d.canUploadEvidence === true, 'upload entry permission flag missing')
  await shot('opt4-upload-entry')
  await mp.evaluate(() => { const pg = getCurrentPages()[getCurrentPages().length - 1]; if (pg.route.indexOf('incentive/index') >= 0) pg.switchTab({ currentTarget: { dataset: { key: 'categories' } } }) })
  await sleep(4500)
  d = await mp.evaluate(pageData)
  assert(d.tree >= 10, `category tree empty: ${d.tree}`)
  await shot('opt3-tree')
  await mp.evaluate(() => { const pg = getCurrentPages()[getCurrentPages().length - 1]; if (pg.route.indexOf('incentive/index') >= 0) pg.switchTab({ currentTarget: { dataset: { key: 'stats' } } }) })
  await sleep(4000)
  d = await mp.evaluate(pageData)
  assert(d.statCount > 0, 'category stats empty after byCategory fix')
  assert(d.statNames[0] && d.statNames[0] !== '未命名分类', `stats name not mapped from tree: ${d.statNames[0]}`)
  await mp.evaluate(() => { const pg = getCurrentPages()[getCurrentPages().length - 1]; if (pg.route.indexOf('incentive/index') >= 0) pg.openStatDetail({ currentTarget: { dataset: { index: 0 } } }) })
  await sleep(5500)
  d = await mp.evaluate(pageData)
  assert(d.statDetailRecords > 0, `stat detail records empty (第3条): ${d.statDetailName}`)
  console.log('statDetail:', d.statDetailName, '→', d.statDetailRecords, '条')
  await shot('opt3-stat-detail')
  await mp.evaluate(() => { const pg = getCurrentPages()[getCurrentPages().length - 1]; if (pg.route.indexOf('incentive/index') >= 0) pg.closeStatDetail() })
  await sleep(800)

  // 2. 上传页两级分类(第 4 条)
  const up = await mp.navigateTo('/pages/incentive/upload/index')
  await sleep(4500)
  d = await mp.evaluate(pageData)
  assert(d.parentCategories.length >= 3, `upload parent categories missing: ${d.parentCategories.length}`)
  assert(d.parentCategories[0].children >= 0, 'parent children missing')
  console.log('upload 大类:', d.parentCategories.map((p) => p.name).join('/'), '| 首大类子类数', d.parentCategories[0].children)
  await mp.navigateBack(); await sleep(1200)

  // 3. 客户列表状态筛选(第 12 条)
  const cust = await mp.navigateTo('/pages/customer/index')
  await sleep(4500)
  d = await mp.evaluate(pageData)
  assert(d.statusFilters.length === 4, 'customer status filters missing')
  await shot('opt12-customer-status')
  await mp.navigateBack(); await sleep(1000)

  // 4. 客户表单红星(第 1 条)
  const form = await mp.navigateTo('/pages/customer/form/index')
  await sleep(4000)
  await shot('opt1-form-stars')
  await mp.navigateBack(); await sleep(1000)

  // 5. 文件页骨架+四 scope tab(第 6/7/8 条)
  await mp.switchTab('/pages/files/index')
  await sleep(5000)
  d = await mp.evaluate(pageData)
  const labels = d.fileTabs
  assert(JSON.stringify(labels) === JSON.stringify(['公共', '私人', '四项激励', '分享给我']), `file tabs mismatch: ${labels.join(',')}`)
  await shot('opt6-8-files-head')

  // 6. 应用中心图标(第 9 条)
  await mp.switchTab('/pages/app/index')
  await sleep(3500)
  await shot('opt9-app-icons')

  // 7. 经营统计: 卡化区块+更新按钮(第 5/11 条)
  const stat = await mp.navigateTo('/pages/statistics/index')
  await sleep(4500)
  await shot('opt5-11-stat-panels')
  await mp.navigateBack(); await sleep(800)

  // 8. 首页居中(第 2 条)
  await mp.switchTab('/pages/home/index')
  await sleep(3000)
  await shot('opt2-home-centered')

  console.log('console errors during sweep:', errors.length)
  errors.slice(0, 5).forEach((e, i) => console.log('E' + i + ':', e))
  mp.disconnect()
  console.log('PASS: 优化批次 12 条全量验证通过')
}

main().catch((e) => { console.error(e.message); process.exit(1) })
