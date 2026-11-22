import automator from 'miniprogram-automator'
import fs from 'node:fs'
const roles=JSON.parse(fs.readFileSync('/tmp/mp_roles.json','utf8')); const admin=roles[0]
const KEY='AIGICRM_TOKEN::http://111.229.37.220'; const sleep=ms=>new Promise(r=>setTimeout(r,ms))
const mini=await automator.launch({cliPath:'/Applications/wechatwebdevtools.app/Contents/MacOS/cli',projectPath:'/Users/dongjun/东来/Agent/05-领域/01-工程/10-项目/aigicrm/source/02-迭代源代码/aigicrm-miniprogram',port:9431,timeout:180000,trustProject:true})
const out=[]
try{
 await mini.evaluate(({key,token})=>{wx.setStorageSync(key,token);const a=getApp();a.globalData.currentUser=null;a.globalData.permissions=[];a.globalData.permissionRefreshPromise=null;a.onShow()},{key:KEY,token:admin.token}); await sleep(2500)
 for(const path of ['/pages/customer/index','/pages/files/index','/pages/approval/index','/pages/reminder/index','/pages/incentive/home/index']){
  await mini.reLaunch(path); await sleep(2200); const p=await mini.currentPage(); const d=await p.data().catch(()=>null)||{}
  const pick={}; for(const k of ['total','list','files','folders','reminders','approvals','canAdd','canApprove','canReject','canUpload']) if(k in d) pick[k]=Array.isArray(d[k])?`array(${d[k].length})`:d[k]
  out.push({path,route:p.path,pick}); console.log(path,'->',p.path,JSON.stringify(pick).slice(0,300))
 }
}finally{try{await mini.evaluate(({key})=>{try{wx.removeStorageSync(key)}catch(e){}},{key:KEY})}catch(e){};fs.writeFileSync('/tmp/mp-pages-20261121.json',JSON.stringify({at:new Date().toISOString(),out},null,2));try{await mini.disconnect()}catch(e){}}
