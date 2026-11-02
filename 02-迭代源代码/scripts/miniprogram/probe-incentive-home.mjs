// 【2026-11-03 修复实证·补】轻量页(tabBar 四项服务)空数据排查: connect 观察 flatRecordCount
import automator from 'miniprogram-automator'

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function main() {
  const mini = await automator.connect({ wsEndpoint: 'ws://localhost:9423' })
  console.log('[connect] ok')
  const page = await mini.reLaunch('/pages/incentive/home/index')
  await sleep(6000)
  const data = await page.data()
  console.log('[page]', page.path)
  console.log('  listMode =', data.listMode)
  console.log('  flatRecordCount =', data.flatRecordCount)
  console.log('  flatPagerTotal =', data.flatPagerTotal)
  console.log('  pagedFlatRecords =', (data.pagedFlatRecords || []).length, '行(当页)')
  console.log('  pagedRecords(封面卡) =', (data.pagedRecords || []).length, '卡(当页)')
  if ((data.pagedFlatRecords || []).length > 0 || (data.pagedRecords || []).length > 0) console.log('[RESULT] PASS — 轻量页有数据')
  else console.log('[RESULT] FAIL — 轻量页为空')
  await mini.disconnect()
}

main().catch((e) => { console.error('[fail]', e && (e.stack || e.message || e)); process.exit(1) })
