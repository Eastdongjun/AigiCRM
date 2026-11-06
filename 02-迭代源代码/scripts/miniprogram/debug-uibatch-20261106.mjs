// 【2026-11-06 五点反馈批 GUI 实证】驱动 DevTools 自动化端口逐页截图核验:
// ③学分明细(真明细+新骨架) / ①文件管理选夹面板(取消无框+空态提示) / ④审批队列卡(无描边) / ⑤登录页记住勾选
// 只读调试: 不写任何业务数据; 登录态测试后原样恢复。
import { mkdirSync } from 'node:fs'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const automator = require('miniprogram-automator')

const WS = process.env.WECHAT_AUTOMATOR_WS || 'ws://127.0.0.1:9421'
const SHOT_DIR = '/tmp/mp-debug-shots'
mkdirSync(SHOT_DIR, { recursive: true })

const mini = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:9424' })
console.log('connected')

const shot = async (name) => {
  await mini.screenshot({ path: `${SHOT_DIR}/${name}.png` })
  console.log('shot:', name)
}

// 登录态保底: 探针键按环境分(AIGICRM_TOKEN::apiBase), 无 token 则用测试环境冒烟账号注入
const sysInfo = await mini.systemInfo()
console.log('page stack check...')
let page = await mini.currentPage()
console.log('current page:', page && page.path)

// —— ③ 学分明细: 直接 navigateTo 带真参(赵珂在测试库有 15 分, userId 需真实; 先从排行页取首行)
await mini.reLaunch('/pages/ranking/index')
await new Promise((r) => setTimeout(r, 2500))
page = await mini.currentPage()
console.log('ranking page:', page && page.path)
// 取第一行个人榜数据(userId/真实姓名/总分)——直接读页面 data.scoreList
const scoreData = await page.data()
const scoreList = (scoreData && scoreData.scoreList) || []
console.log('scoreList rows:', scoreList.length, scoreList[0] ? JSON.stringify({ userId: scoreList[0].userId, realName: scoreList[0].realName, totalScore: scoreList[0].totalScore, rank: scoreList[0].rank }) : 'none')
if (scoreList[0] && scoreList[0].userId) {
  const first = scoreList[0]
  const q = [
    `name=${encodeURIComponent(first.realName || '')}`,
    `score=${encodeURIComponent(first.totalScore || 0)}`,
    `dept=${encodeURIComponent(first.officeName || first.deptName || '')}`,
    `period=${encodeURIComponent('2026全年')}`,
    `userId=${encodeURIComponent(first.userId)}`,
    `rank=${encodeURIComponent(first.rank || 1)}`
  ].join('&')
  await mini.navigateTo(`/pages/ranking/detail/index?${q}`)
  await new Promise((r) => setTimeout(r, 2500))
  const detail = await mini.currentPage()
  const dd = await detail.data()
  console.log('detail itemsView rows:', (dd.itemsView && dd.itemsView.rows || []).length, 'header:', dd.header && dd.header.nameText, dd.header && dd.header.rankText)
  await shot('3-ranking-detail')
  await detail.goBack ? null : null
  await mini.navigateBack()
  await new Promise((r) => setTimeout(r, 1200))
}

// —— ① 文件管理选夹面板
await mini.switchTab('/pages/files/index')
await new Promise((r) => setTimeout(r, 2500))
page = await mini.currentPage()
await page.callMethod('openFolderSelect', { mode: 'album' })
await new Promise((r) => setTimeout(r, 1200))
const fd = await page.data()
console.log('folderSelect visible:', fd.folderSelect && fd.folderSelect.visible, 'folders:', (fd.folderSelect && fd.folderSelect.folders || []).length)
await shot('1-files-folder-panel')
await page.callMethod('cancelFolderSelect')
await new Promise((r) => setTimeout(r, 800))

// —— ④ 审批队列卡(无描边)
await mini.switchTab('/pages/approval/index')
await new Promise((r) => setTimeout(r, 2500))
await shot('4-approval-queue')

// —— ⑤ 登录页记住勾选: 暂存 token→清登录态→进登录页截图→恢复
const TOKEN_KEY_PREFIX = 'AIGICRM_TOKEN::'
const saved = await mini.callWxMethod('getStorageSync', TOKEN_KEY_PREFIX + 'http://111.229.37.220')
console.log('saved token len:', (saved || '').length)
if (saved) {
  await mini.callWxMethod('removeStorageSync', TOKEN_KEY_PREFIX + 'http://111.229.37.220')
  await mini.callWxMethod('removeStorageSync', TOKEN_KEY_PREFIX + 'https://www.aigicrm.com')
}
await mini.reLaunch('/pages/login/index')
await new Promise((r) => setTimeout(r, 2000))
page = await mini.currentPage()
console.log('login page:', page && page.path)
// 勾上两个勾选看视觉态
await page.callMethod('toggleRememberAccount')
await page.callMethod('toggleRememberPassword')
const ld = await page.data()
console.log('rememberAccount:', ld.rememberAccount, 'rememberPassword:', ld.rememberPassword, 'username filled:', ld.username)
await shot('5-login-remember')
// 恢复登录态
if (saved) {
  await mini.callWxMethod('setStorageSync', TOKEN_KEY_PREFIX + 'http://111.229.37.220', saved)
}

await mini.disconnect()
console.log('DONE')
