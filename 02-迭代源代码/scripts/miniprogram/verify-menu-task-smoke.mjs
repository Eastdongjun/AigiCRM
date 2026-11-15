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
console.log('connected')
await mini.evaluate(() => {
  try { wx.removeStorageSync('AIGICRM_TOKEN::http://111.229.37.220') } catch (e) {}
  const g = getApp().globalData; g.currentUser = null; g.permissions = []
})
const lp = await mini.reLaunch('/pages/login/index')
await lp.setData({ username: e.HEALTH_CHECK_USER, password: e.HEALTH_CHECK_PASS, agreed: true })
await lp.callMethod('submit')
let state = null
for (let i=0;i<20;i++){ await sleep(1000); state = await mini.evaluate(()=>{const g=getApp().globalData;return {user:g.currentUser&&g.currentUser.username,perms:(g.permissions||[]).length}}); if(state.user && state.perms) break }
console.log('login', state)
const out={login:state,checks:{}}
let page = await mini.switchTab('/pages/fabushi/index')
await sleep(2500)
let d = await page.data()
out.checks.fabushiTab={path:page.path,canCreate:d.canCreate,personal:d.personal}
page = await mini.switchTab('/pages/app/index')
await sleep(2500)
d = await page.data()
const groups=(d.appView&&d.appView.groups)||[]
out.checks.appCenter={groups:groups.map(g=>g.title),workflow:(groups.find(g=>g.title==='流程协同')||{}).entries?.map(x=>x.title)}
page = await mini.navigateTo('/pages/files/index')
await sleep(2500)
d = await page.data()
out.checks.filesPage={path:page.path,hasFileView:!!(d.fileView&&d.fileView.tabs),activeTab:d.activeTab}
await mini.navigateBack(); await sleep(1200)
page = await mini.navigateTo('/pages/task/index')
await sleep(2500)
d = await page.data()
out.checks.taskPage={path:page.path,canManage:d.canManage,stats:d.stats,taskCount:(d.tasks||[]).length,total:d.total}
await mini.screenshot({path:'test-results/menu-dict/mp-task.png'})
console.log(JSON.stringify(out,null,1))
fs.writeFileSync('test-results/menu-dict/mp-smoke.json',JSON.stringify(out,null,1))
await mini.disconnect()
const ok = out.checks.fabushiTab.path==='pages/fabushi/index' && out.checks.appCenter.groups.join(',')==='客户经营,文件管理,四项激励,法布施,学分管理,统计分析,流程协同' && out.checks.appCenter.workflow?.join(',')==='我的任务,提醒管理,审批管理,公海池' && out.checks.filesPage.path==='pages/files/index' && out.checks.taskPage.path==='pages/task/index'
process.exit(ok?0:1)
