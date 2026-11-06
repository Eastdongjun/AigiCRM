import { mkdirSync } from 'node:fs'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const automator = require('miniprogram-automator')

mkdirSync('/tmp/mp-debug-shots', { recursive: true })
const mini = await automator.launch({
  cliPath: '/Applications/wechatwebdevtools.app/Contents/MacOS/cli',
  projectPath: '/Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码/aigicrm-miniprogram',
  port: 9424,
  timeout: 120000
})
console.log('connected')
const page = await mini.currentPage()
console.log('current:', page && page.path)
await mini.screenshot({ path: '/tmp/mp-debug-shots/0-probe.png' })
console.log('PROBE_OK')
await mini.disconnect()
