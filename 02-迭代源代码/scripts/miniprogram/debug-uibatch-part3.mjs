import { mkdirSync } from 'node:fs'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const automator = require('miniprogram-automator')

mkdirSync('/tmp/mp-debug-shots', { recursive: true })
const mini = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:9424' })
try {
  await mini.reLaunch('/pages/home/index')
  await new Promise((r) => setTimeout(r, 1500))
  await mini.navigateTo('/pages/approval/index')
  await new Promise((r) => setTimeout(r, 2500))
  const page = await mini.currentPage()
  console.log('approval page:', page && page.path)
  await mini.screenshot({ path: '/tmp/mp-debug-shots/4-approval-queue.png' })
  console.log('shot: 4-approval-queue')
} catch (e) { console.log('err:', String(e).slice(0, 140)) }
await mini.disconnect()
console.log('DONE-PART3')
