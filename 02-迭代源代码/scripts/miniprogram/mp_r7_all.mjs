import automator from 'miniprogram-automator'
import fs from 'node:fs'
const roles = JSON.parse(fs.readFileSync('/tmp/mp_roles_all.json','utf8'))
const KEY = 'AIGICRM_TOKEN::http://111.229.37.220'
const sleep = ms => new Promise(r => setTimeout(r, ms))
const mini = await automator.launch({cliPath:'/Applications/wechatwebdevtools.app/Contents/MacOS/cli',projectPath:'/Users/dongjun/东来/Agent/05-领域/01-工程/10-项目/aigicrm/source/02-迭代源代码/aigicrm-miniprogram',port:9428,timeout:180000,trustProject:true})
const results=[]
async function pageData(path){await mini.reLaunch(path);await sleep(1800);const p=await mini.currentPage();let data={};try{data=await p.data()}catch(e){}return {path:p&&p.path,data}}
try{
for(const r of roles){
 const perms=new Set(r.perms)
 await mini.evaluate(({key,token})=>{wx.setStorageSync(key,token);const a=getApp();a.globalData.currentUser=null;a.globalData.permissions=[];a.globalData.groupIds=[];a.globalData.currentGroupId=null;a.globalData.permissionRefreshPromise=null;a.onShow()},{key:KEY,token:r.token})
 await sleep(2200)
 const g=await mini.evaluate(()=>{const a=getApp().globalData;return {user:a.currentUser&&a.currentUser.username,roles:a.currentUser&&a.currentUser.roles,perms:(a.permissions||[]).length,group:a.currentGroupId,demo:a.demoMode}})
 const home=await pageData('/pages/home/index')
 const cust=await pageData('/pages/customer/index')
 const files=await pageData('/pages/files/index')
 const fab=await pageData('/pages/fabushi/index')
 const appr=await pageData('/pages/approval/index')
 const isAdmin=(r.roles||[]).includes('admin')
 const exp={canAdd:isAdmin||perms.has('customer:add'),canViewSea:perms.has('sea:view'),canUpload:isAdmin||perms.has('file:upload'),canShare:isAdmin||perms.has('file:share'),canEditFile:isAdmin||perms.has('file:edit'),canManageFolder:isAdmin||perms.has('file:folder:manage'),canDelete:isAdmin||perms.has('file:delete')||perms.has('fabushi:file:delete'),canCreateFabushi:perms.has('fabushi:create'),canApprove:perms.has('approval:approve'),canReject:perms.has('approval:reject')}
 const act={canAdd:cust.data.canAdd,canViewSea:cust.data.canViewSea,canUpload:files.data.canUpload,canShare:files.data.canShare,canEditFile:files.data.canEditFile,canManageFolder:files.data.canManageFolder,canDelete:files.data.canDelete,canCreateFabushi:fab.data.canCreate,canApprove:appr.data.canApprove,canReject:appr.data.canReject}
 const checks=Object.keys(exp).map(k=>({k,expected:!!exp[k],actual:!!act[k],ok:!!exp[k]===!!act[k]}))
 const tabs=await mini.evaluate(()=>{const p=getCurrentPages().slice(-1)[0];const tb=p.getTabBar&&p.getTabBar();return tb?tb.data.tabs.map(t=>({key:t.key,disabled:!!t.disabled})):null}).catch(()=>null)
 results.push({user:r.username,uid:r.uid,group:g,homePath:home.path,custPath:cust.path,filesPath:files.path,fabPath:fab.path,apprPath:appr.path,tabs,checks})
 console.log(r.username,'group',g.group,'perms',g.perms,'mismatch',checks.filter(c=>!c.ok).length,JSON.stringify(checks.filter(c=>!c.ok)))
}
}finally{try{await mini.evaluate(({key})=>{try{wx.removeStorageSync(key)}catch(e){}},{key:KEY})}catch(e){};fs.writeFileSync('/tmp/mp-r7-20261121.json',JSON.stringify({at:new Date().toISOString(),results},null,2));try{await mini.disconnect()}catch(e){}}
console.log(results.every(r=>r.checks.every(c=>c.ok))?'ALL_PASS':'HAS_MISMATCH')
