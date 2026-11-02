// 【2026-11-03 统计分析空白排查】data 全量 + console 错误捕获
import automator from 'miniprogram-automator'

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function main() {
  const mini = await automator.connect({ wsEndpoint: 'ws://localhost:9423' })
  console.log('[connect] ok')
  const logs = []
  mini.on('console', (msg) => logs.push(`[${msg.type}] ${msg.args && msg.args.join(' ')}`))
  mini.on('exception', (err) => logs.push(`[exception] ${err && (err.message || err.errorMessage)}`))
  const page = await mini.reLaunch('/pages/statistics/index')
  console.log('[page]', page.path)
  await sleep(8000)
  const data = await page.data()
  const keys = Object.keys(data).sort()
  console.log('[data 键]', keys.join(', '))
  for (const k of keys) {
    const v = data[k]
    if (v === null || v === undefined || typeof v !== 'object') console.log(`  ${k} =`, JSON.stringify(v))
    else if (Array.isArray(v)) console.log(`  ${k}: Array[${v.length}]`)
    else console.log(`  ${k}:`, JSON.stringify(v).slice(0, 200))
  }
  console.log('[console 日志]', logs.length ? logs.slice(0, 12).join('\n') : '(无输出)')
  await mini.disconnect()
}

main().catch((e) => { console.error('[fail]', e && (e.stack || e.message || e)); process.exit(1) })
