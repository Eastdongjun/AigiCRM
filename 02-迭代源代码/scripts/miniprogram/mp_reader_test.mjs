import automator from 'miniprogram-automator'
import fs from 'node:fs'
const admin=JSON.parse(fs.readFileSync('/tmp/mp_admin_fresh.json','utf8'))
const KEY='AIGICRM_TOKEN::http://111.229.37.220'; const sleep=ms=>new Promise(r=>setTimeout(r,ms))
const mini=await automator.launch({cliPath:'/Applications/wechatwebdevtools.app/Contents/MacOS/cli',projectPath:'/Users/dongjun/东来/Agent/05-领域/01-工程/10-项目/aigicrm/source/02-迭代源代码/aigicrm-miniprogram',port:9435,timeout:180000,trustProject:true})
try{
 await mini.evaluate(({key,token})=>{wx.setStorageSync(key,token);const a=getApp();a.globalData.currentUser=null;a.globalData.permissions=[];a.globalData.permissionRefreshPromise=null;a.onShow()},{key:KEY,token:admin.token}); await sleep(2500)
 await mini.reLaunch('/pages/fabushi/reader/index?bookId=78&pdfFileId=2725&title=R2-TEST'); await sleep(4000)
 const p=await mini.currentPage(); const d=await p.data().catch(()=>null)||{}
 console.log(JSON.stringify({route:p.path,src:(d.src||'').slice(0,400),hasTicket:(d.src||'').includes('ticket='),hasReader:(d.src||'').includes('mp-fabushi-reader')},null,1))
 await mini.screenshot({path:'/tmp/r2-mp-reader-20261122.png'}).catch(()=>{})
}finally{try{await mini.evaluate(({key})=>{try{wx.removeStorageSync(key)}catch(e){}},{key:KEY})}catch(e){};try{await mini.disconnect()}catch(e){}}
