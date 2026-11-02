// 【2026-11-03 三页签实测】排行 tab: 页签渲染/切换/互斥 + 截图
import automator from 'miniprogram-automator'

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function main() {
  let mini
  try {
    mini = await automator.connect({ wsEndpoint: 'ws://localhost:9423' })
    console.log('[connect] ok(复用实例)')
  } catch (e) {
    mini = await automator.launch({
      cliPath: '/Applications/wechatwebdevtools.app/Contents/MacOS/cli',
      projectPath: '/Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码/aigicrm-miniprogram',
      port: 9423,
      timeout: 90000
    })
    console.log('[launch] ok')
    await sleep(12000)
  }
  let page = await mini.reLaunch('/pages/incentive/index')
  console.log('[reLaunch] ->', page.path)
  await sleep(3000)
  page = await mini.currentPage()
  console.log('[currentPage]', page.path)
  await page.callMethod('switchTab', { currentTarget: { dataset: { key: 'ranking' } } })
  await sleep(2500)
  let data = await page.data()
  console.log('[切排行后] activeTab=%s rankTab=%s', data.activeTab, data.rankTab)
  const tabs = await page.$$('.rank-subtabs .tab')
  console.log('[页签数]', tabs.length, '(期望3)')
  for (const t of tabs) console.log('  -', await t.text())
  const v = data.incentiveView || {}
  console.log('[互斥] customer=%s scoreboard=%s dept=%s (期望 true/false/false)', v.showRankCustomer, v.showRankScoreboard, v.showRankDept)
  // 切到个人学分榜
  await tabs[1].tap()
  await sleep(1200)
  data = await page.data()
  console.log('[tap个人] rankTab=%s (期望 scoreboard)', data.rankTab)
  // 切到部门排行
  await tabs[2].tap()
  await sleep(1200)
  data = await page.data()
  console.log('[tap部门] rankTab=%s (期望 dept)', data.rankTab)
  // 切回客户排行并截图
  await tabs[0].tap()
  await sleep(1200)
  await mini.screenshot({ path: '/Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码/scripts/miniprogram/rank-tabs.png' })
  console.log('[screenshot] saved')
  await mini.disconnect()
}

main().catch((e) => { console.error('[fail]', e && (e.stack || e.message || e)); process.exit(1) })
