import automator from 'miniprogram-automator'
import fs from 'node:fs'
const admin=JSON.parse(fs.readFileSync('/tmp/mp_admin_fresh.json','utf8'))
const KEY='AIGICRM_TOKEN::http://111.229.37.220'; const sleep=ms=>new Promise(r=>setTimeout(r,ms))
const mini=await automator.launch({cliPath:'/Applications/wechatwebdevtools.app/Contents/MacOS/cli',projectPath:'/Users/dongjun/东来/Agent/05-领域/01-工程/10-项目/aigicrm/source/02-迭代源代码/aigicrm-miniprogram',port:9436,timeout:180000,trustProject:true})
try{
 await mini.evaluate(({key,token})=>{wx.setStorageSync(key,token);const a=getApp();a.globalData.currentUser=null;a.globalData.permissions=[];a.globalData.permissionRefreshPromise=null;a.onShow()},{key:KEY,token:admin.token}); await sleep(2500)
 await mini.reLaunch('/pages/files/index'); await sleep(3000)
 const p=await mini.currentPage(); const d=await p.data(); const files=d.files||d.fileList||[]; const folders=d.folders||[]
 console.log('route',p.path,'tabs',JSON.stringify(d.tabs||d.tabList||[]).slice(0,100),'files',files.length,'folders',folders.length)
 console.log('hasUploaded',files.some(f=>String(f.fileName||f.name||'').includes('r3-mp-upload')))
 console.log('sample',JSON.stringify(files.slice(0,2)).slice(0,300))
 await mini.screenshot({path:'/tmp/r3-mp-files-20261122.png'}).catch(()=>{})
}finally{try{await mini.evaluate(({key})=>{try{wx.removeStorageSync(key)}catch(e){}},{key:KEY})}catch(e){};try{await mini.disconnect()}catch(e){}}
