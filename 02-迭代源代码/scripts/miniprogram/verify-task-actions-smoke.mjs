import { createRequire } from 'node:module'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
const require = createRequire(import.meta.url)
const automator = require('miniprogram-automator')
const WS = process.env.WECHAT_AUTOMATOR_WS || 'ws://127.0.0.1:9424'
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
function env(p){const o={};for(const l of fs.readFileSync(p,'utf8').split(/\r?\n/)){const m=l.match(/^\s*([A-Za-z_]\w*)\s*=\s*(.*)\s*$/);if(m)o[m[1]]=m[2].replace(/^["']|["']$/g,'')}return o}
const e=env(path.join(os.homedir(),'.aigicrm-deploy.env'))
fs.mkdirSync('test-results/menu-dict',{recursive:true})
const mini = await automator.connect({ wsEndpoint: WS })
await mini.evaluate(() => {
  try { wx.removeStorageSync('AIGICRM_TOKEN::http://111.229.37.220') } catch (err) {}
  const g = getApp().globalData; g.currentUser = null; g.permissions = []
})
const lp = await mini.reLaunch('/pages/login/index')
await lp.setData({ username: e.HEALTH_CHECK_USER, password: e.HEALTH_CHECK_PASS, agreed: true })
await lp.callMethod('submit')
for (let i=0;i<20;i++){ await sleep(1000); const s=await mini.evaluate(()=>{const g=getApp().globalData;return !!(g.currentUser&&g.currentUser.username&&(g.permissions||[]).length)}); if(s) break }
const page = await mini.navigateTo('/pages/task/index')
await sleep(3000)
let d = await page.data()
const idx = (d.tasks||[]).findIndex(t=>String(t.title).includes('MP验收临时任务'))
const out={taskCount:(d.tasks||[]).length,index:idx,canManage:d.canManage}
if(idx>=0){
  await page.callMethod('openComplete',{currentTarget:{dataset:{index:idx}}})
  await sleep(500)
  d = await page.data(); out.completeModal={visible:d.actionVisible,title:d.actionTitle}
  await page.callMethod('closeAction')
  await sleep(300)
  await page.callMethod('openTransfer',{currentTarget:{dataset:{index:idx}}})
  await sleep(2000)
  d = await page.data(); out.transferModal={visible:d.transferVisible,assignees:(d.filteredAssignees||[]).length}
  if(d.transferVisible && (d.filteredAssignees||[]).length){
    await page.callMethod('selectAssignee',{currentTarget:{dataset:{index:0}}})
    await sleep(2500)
    d = await page.data(); out.transferResult={visible:d.transferVisible,taskCount:(d.tasks||[]).length}
  }
}
await mini.screenshot({path:'test-results/menu-dict/mp-task-actions.png'})
console.log(JSON.stringify(out,null,1))
fs.writeFileSync('test-results/menu-dict/mp-task-actions.json',JSON.stringify(out,null,1))
mini.disconnect()
const ok = out.taskCount>=1 && out.completeModal?.visible && out.transferModal?.visible && out.transferModal?.assignees>0 && out.transferResult?.visible===false
process.exit(ok?0:1)
