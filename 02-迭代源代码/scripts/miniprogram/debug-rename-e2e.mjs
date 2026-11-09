import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const automator = require('miniprogram-automator')
const { execSync } = require('node:child_process')

const CLIPATH = '/Applications/wechatwebdevtools.app/Contents/MacOS/cli'
const PROJECT = '/Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码/aigicrm-miniprogram'
const TS = new Date().toISOString().replace(/[-:T]/g, '').slice(0, 12)
const NEW_NAME = `重命名测试-${TS}.png`

async function connectOrLaunch(port) {
  try {
    return await automator.connect({ wsEndpoint: `ws://127.0.0.1:${port}` })
  } catch (e) {
    console.log(`port ${port} dead, relaunching...`)
    try { execSync(`"${CLIPATH}" close --project "${PROJECT}"`, { stdio: 'pipe' }) } catch (_) {}
    await new Promise((r) => setTimeout(r, 3000))
    return automator.launch({ cliPath: CLIPATH, projectPath: PROJECT, port, timeout: 120000 })
  }
}

let mini = await connectOrLaunch(9424)
console.log('connected')
await mini.reLaunch('/pages/files/index')
await new Promise((r) => setTimeout(r, 3500))
const page = await mini.currentPage()
console.log('page:', page && page.path)
await page.callMethod('onTabTap', { currentTarget: { dataset: { tab: '四项服务' } } })
await new Promise((r) => setTimeout(r, 4000))
const d = await page.data()
const rows = d.filteredFiles || []
const imgIdx = rows.findIndex((r) => /\.(png|jpg|jpeg)$/i.test(String(r.name || '')))
const before = rows[imgIdx] ? rows[imgIdx].name : ''
const fileId = rows[imgIdx] ? rows[imgIdx].id : ''
console.log('target row:', imgIdx, before, 'id:', fileId)
if (!fileId) { console.log('NO_TARGET'); await mini.disconnect(); process.exit(1) }

// 改名
const evt = { currentTarget: { dataset: { index: imgIdx } } }
await page.callMethod('openRenameModal', evt)
await page.callMethod('onRenameInput', { detail: { value: NEW_NAME } })
const m = await page.data()
console.log('modal input:', m.renameModal.input)
await page.callMethod('confirmRename')
await new Promise((r) => setTimeout(r, 3000))
const d2 = await page.data()
const afterName = (d2.filteredFiles[imgIdx] || {}).name
console.log('UI 列表名 after:', afterName, ' 改名成功:', afterName === NEW_NAME)
await mini.screenshot({ path: '/tmp/mp-debug-shots/9-renamed-list.png' })
console.log('FILE_ID=' + fileId)
console.log('NEW_NAME=' + NEW_NAME)
console.log('DONE-PART5')
await mini.disconnect()
