import automator from 'miniprogram-automator'
import fs from 'node:fs'
const u=JSON.parse(fs.readFileSync('/tmp/mp_role11.json','utf8'))
const KEY='AIGICRM_TOKEN::http://111.229.37.220'; const sleep=ms=>new Promise(r=>setTimeout(r,ms))
const mini=await automator.launch({cliPath:'/Applications/wechatwebdevtools.app/Contents/MacOS/cli',projectPath:'/Users/dongjun/东来/Agent/05-领域/01-工程/10-项目/aigicrm/source/02-迭代源代码/aigicrm-miniprogram',port:9433,timeout:180000,trustProject:true})
try{
 await mini.evaluate(({key,token})=>{wx.setStorageSync(key,token);const a=getApp();a.globalData.currentUser=null;a.globalData.permissions=[];a.globalData.permissionRefreshPromise=null;a.onShow()},{key:KEY,token:u.token}); await sleep(2500)
 await mini.reLaunch('/pages/approval/index'); await sleep(2500)
 const p=await mini.currentPage(); const d=await p.data(); const list=d.approvals||d.list||[]
 console.log('route',p.path,'total',d.total,'rows',list.length,'canApprove',d.canApprove)
 const target=list.find(x=>String(x.customerName||x.customerNameText||'').includes('R1-APPROVAL-C'))
 console.log('target',JSON.stringify(target?{id:target.id,title:target.title,status:target.status,customer:target.customerName}:null))
 const btns=await p.$$('button.approval-approve-action'); console.log('approve buttons',btns.length)
 if(btns.length){ await btns[0].tap(); await sleep(2500); }
 const body=(await p.data()).approvals||[]; console.log('after rows',body.length)
 await mini.screenshot({path:'/tmp/r1-mp-approval-20261121.png'}).catch(()=>{})
}finally{try{await mini.evaluate(({key})=>{try{wx.removeStorageSync(key)}catch(e){}},{key:KEY})}catch(e){};try{await mini.disconnect()}catch(e){}}
