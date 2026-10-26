import { mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const automator = require('miniprogram-automator')

const wsEndpoint = process.env.WECHAT_AUTOMATOR_WS || 'ws://127.0.0.1:9421'
const screenshotDir = process.env.MINIPROGRAM_AUTOMATOR_SCREENSHOT_DIR || ''
const shouldScreenshot = Boolean(screenshotDir)

function assert(condition, message) {
  if (!condition) throw new Error(message)
}

function eventWithDataset(dataset) {
  return { currentTarget: { dataset } }
}

async function textList(elements) {
  const texts = []
  for (const element of elements) texts.push(await element.text())
  return texts
}

async function screenshot(miniProgram, name) {
  if (!shouldScreenshot) return
  const path = resolve(screenshotDir, name)
  mkdirSync(dirname(path), { recursive: true })
  await miniProgram.screenshot({ path })
}

async function resetFileActionCalls(miniProgram) {
  await miniProgram.evaluate(() => {
    globalThis.__fileActionCalls = {
      downloadFile: [],
      previewImage: [],
      openDocument: [],
      saveFile: []
    }
  })
}

async function fileActionCalls(miniProgram) {
  return await miniProgram.evaluate(() => globalThis.__fileActionCalls)
}

async function installFileActionMocks(miniProgram) {
  await resetFileActionCalls(miniProgram)
  await miniProgram.evaluate(() => { globalThis.__fileNavigationCalls = [] })
  await miniProgram.mockWxMethod('navigateTo', function (options) {
    globalThis.__fileNavigationCalls = globalThis.__fileNavigationCalls || []
    globalThis.__fileNavigationCalls.push({ url: options && options.url })
    if (options && typeof options.success === 'function') options.success({})
  })
  await miniProgram.mockWxMethod('downloadFile', function (options) {
    const calls = globalThis.__fileActionCalls || (globalThis.__fileActionCalls = { downloadFile: [], previewImage: [], openDocument: [], saveFile: [] })
    calls.downloadFile.push({ url: options && options.url, header: options && options.header })
    if (options && typeof options.success === 'function') options.success({ statusCode: 200, tempFilePath: '/tmp/aigicrm-file-center-download.mock' })
    if (options && typeof options.complete === 'function') options.complete({})
  })
  await miniProgram.mockWxMethod('previewImage', function (options) {
    const calls = globalThis.__fileActionCalls || (globalThis.__fileActionCalls = { downloadFile: [], previewImage: [], openDocument: [], saveFile: [] })
    calls.previewImage.push({ urls: options && options.urls })
    if (options && typeof options.success === 'function') options.success({})
    if (options && typeof options.complete === 'function') options.complete({})
  })
  await miniProgram.mockWxMethod('openDocument', function (options) {
    const calls = globalThis.__fileActionCalls || (globalThis.__fileActionCalls = { downloadFile: [], previewImage: [], openDocument: [], saveFile: [] })
    calls.openDocument.push({ filePath: options && options.filePath, showMenu: options && options.showMenu })
    if (options && typeof options.success === 'function') options.success({})
    if (options && typeof options.complete === 'function') options.complete({})
  })
  await miniProgram.mockWxMethod('saveFile', function (options) {
    const calls = globalThis.__fileActionCalls || (globalThis.__fileActionCalls = { downloadFile: [], previewImage: [], openDocument: [], saveFile: [] })
    calls.saveFile.push({ tempFilePath: options && options.tempFilePath })
    if (options && typeof options.success === 'function') options.success({ savedFilePath: '/tmp/aigicrm-file-center-download.saved' })
    if (options && typeof options.complete === 'function') options.complete({})
  })
}

async function restoreFileActionMocks(miniProgram) {
  for (const method of ['downloadFile', 'previewImage', 'openDocument', 'saveFile', 'navigateTo']) {
    await miniProgram.restoreWxMethod(method)
  }
}

async function enableFileCenterMode(miniProgram) {
  await miniProgram.evaluate(() => {
    const app = getApp()
    // 【2026-09-22 校准】UI 预渲染沙箱统一 demo 口径: false+假token 在现行登录/刷新链路下
    // 触发 request 失败旁路(实测 setFileData 未定义异常), 断言全为界面层, demo 即可覆盖
    app.globalData.demoMode = true
    app.globalData.apiBaseUrl = app.globalData.apiBaseUrl || 'https://aigicrm-automator.local'
    app.globalData.currentUser = app.globalData.currentUser || { id: 1001, realName: '自动化用户' }
    app.globalData.permissions = [
      'file:view',
      'file:upload',
      'file:edit',
      'file:delete',
      'file:share',
      'file:folder:manage',
      'file:acl:manage',
      ...(app.globalData.permissions || [])
    ]
    const app0 = getApp()
    // 【PR-04 令牌缓存隔离】与 utils/auth.js 同口径: 键按 apiBaseUrl 分环境
    wx.setStorageSync('AIGICRM_TOKEN::' + (app0.globalData.apiBaseUrl || 'demo'), 'aigicrm-file-center-automator-token')
  })
}

async function verifyFileCenterPage(miniProgram) {
  await enableFileCenterMode(miniProgram)
  const page = await miniProgram.reLaunch('/pages/files/index')
  await page.waitFor(1000)

  const files = [
    { id: 701, name: '客户现场照片.jpg', type: 'JPG', size: '1.2 MB', folder: '客户文档', uploaderName: '张三', updatedAt: '今天 09:30', tone: 'blue', ossKey: 'customer/site/photo.jpg', canDownload: true },
    { id: 702, name: '激励方案.pdf', type: 'PDF', size: '2.8 MB', folder: '激励资料', uploaderName: '李四', updatedAt: '昨天 18:10', tone: 'red', ossKey: 'incentive/plan.pdf', canDownload: true },
    { id: 703, name: '仅预览资料.pdf', type: 'PDF', size: '980 KB', folder: '客户文档', uploaderName: '王五', updatedAt: '今天 11:00', tone: 'red', ossKey: 'customer/site/readonly.pdf', canDownload: false }
  ]
  // 【2026-09-22 校准】files 页 9/21 重构走 fileView 视图模型, 须走 setFileData 重建
  await page.callMethod('setFileData', {
    activeTab: '私人',
    tabCounts: { 公共: 0, 私人: 3, 四项服务: 1, 分享给我: 1 },
    folders: [{ id: 11, name: '客户文档' }, { id: 12, name: '激励资料' }],
    files,
    filteredFiles: files,
    capacity: { used: '2 GB', total: '10 GB', percent: 20, hint: '已使用 20%，剩余 8 GB' },
    canUpload: true,
    canReplace: true,
    canDelete: true,
    canShare: true,
    canManageFolder: true,
    canViewFolderAcl: true,
    loaded: true,
    previewing: false
  })
  await page.waitFor(500)

  assert((await page.$$('.file-row')).length === 3, 'file center list rows are missing')
  assert((await page.$$('.folder-card')).length >= 2, 'file center folders are missing')
  assert((await page.$$('.files-capacity-row')).length === 1, 'file center storage card is missing')
  const buttons = await textList(await page.$$('button'))
  for (const label of ['搜索', '打开', '下载', '替换', '删除', '分享', '新建']) {
    assert(buttons.includes(label), `file center missing action: ${label}`)
  }
  await page.callMethod('setFileData', { keyword: '方案', activeTab: '私人', activeFolderId: 11 })
  const privateParams = await page.callMethod('buildFileListParams')
  assert(privateParams.keyword === '方案', `file center list params must send keyword to backend: ${JSON.stringify(privateParams)}`)
  assert(privateParams.scope === 'private', `file center list params must send private scope for private tab: ${JSON.stringify(privateParams)}`)
  assert(privateParams.folderId === 11, `file center list params must keep selected folder for private tab: ${JSON.stringify(privateParams)}`)
  await page.callMethod('setFileData', { keyword: '激励', activeTab: '四项服务', activeFolderId: 11 })
  const incentiveParams = await page.callMethod('buildFileListParams')
  assert(incentiveParams.keyword === '激励', `file center incentive params must send keyword to backend: ${JSON.stringify(incentiveParams)}`)
  assert(incentiveParams.scope === 'incentive', `file center incentive params must send incentive scope: ${JSON.stringify(incentiveParams)}`)
  assert(incentiveParams.folderId === undefined, `file center incentive params must not leak private folder filter: ${JSON.stringify(incentiveParams)}`)
  await page.callMethod('setFileData', { keyword: '共享', activeTab: '分享给我', activeFolderId: 11 })
  const sharedParams = await page.callMethod('buildFileListParams')
  assert(sharedParams.keyword === '共享', `file center shared params must send keyword to backend: ${JSON.stringify(sharedParams)}`)
  assert(sharedParams.scope === undefined, `file center shared-to-me params must not send file list scope: ${JSON.stringify(sharedParams)}`)
  const rowText = (await textList(await page.$$('.file-row'))).join('\n')
  for (const expected of ['客户现场照片.jpg', '1.2 MB', '张三', '激励方案.pdf', '2.8 MB', '李四', '仅预览资料.pdf']) {
    assert(rowText.includes(expected), `file center list missing PC metadata ${expected}: ${rowText}`)
  }
  const tabText = (await textList(await page.$$('.file-tab'))).join('\n')
  for (const expected of ['公共0', '私人3', '四项服务1', '分享给我1']) {
    assert(tabText.includes(expected), `file center tab count missing ${expected}: ${tabText}`)
  }

  // 【2026-10-26 重锚】用户令"一次点击一步打开": 点行 openFile 直接走预览下载(不再跳详情页);
  // mock 环境缓存必未命中(lookup fail-open 返回''), 断言 downloadFile 携带鉴权头被调用
  await page.callMethod('openFile', eventWithDataset({ index: 0 }))
  await page.waitFor(200)
  await page.setData({ previewing: false })
  const fileActions = await miniProgram.evaluate(() => globalThis.__fileActionCalls || { downloadFile: [] })
  const previewCall = (fileActions.downloadFile || []).find((item) => item.url && item.url.includes('/api/file/download/'))
  assert(previewCall, `file center one-tap preview download missing: ${JSON.stringify(fileActions.downloadFile)}`)
  assert(previewCall.header && String(previewCall.header.Authorization || '').includes('Bearer'), `file center one-tap preview must carry auth header: ${JSON.stringify(previewCall)}`)

  await resetFileActionCalls(miniProgram)
  await page.callMethod('previewFile', eventWithDataset({ index: 0 }))
  await page.waitFor(300)
  await page.setData({ previewing: false })
  await page.callMethod('downloadFile', eventWithDataset({ index: 1 }))
  await page.waitFor(300)
  await page.setData({ previewing: false })
  await page.callMethod('downloadFile', eventWithDataset({ index: 2 }))
  await page.waitFor(100)
  await page.callMethod('openDownloadedFile', files[0], '/tmp/aigicrm-file-center-image.mock')
  await page.waitFor(100)
  await page.callMethod('openDownloadedFile', files[1], '/tmp/aigicrm-file-center-document.mock')
  await page.waitFor(100)

  const calls = await fileActionCalls(miniProgram)
  assert(calls.downloadFile.length === 2, `file center actions must call downloadFile: ${JSON.stringify(calls)}`)
  assert(calls.previewImage.length === 1, `file center image preview must call previewImage: ${JSON.stringify(calls)}`)
  assert(calls.openDocument.length === 1, `file center document preview must call openDocument: ${JSON.stringify(calls)}`)

  // 【2026-09-22 校准】ACL 行渲染走 fileView.aclRows, 须 setFileData 重建
  await page.callMethod('setFileData', {
    aclVisible: true,
    aclLoading: false,
    aclFolderName: '客户文档',
    aclRows: [
      { roleId: 1, roleName: '销售经理', permLabels: ['查看', '下载'] },
      { roleId: 2, roleName: '服务工程师', permLabels: ['查看'] }
    ]
  })
  await page.waitFor(300)
  assert((await page.$$('.modal-card')).length === 1, 'file center ACL modal is missing')
  const modalText = (await textList(await page.$$('.files-manage-row'))).join('\n')
  assert(modalText.includes('销售经理'), 'file center ACL modal missing role row')
  await screenshot(miniProgram, 'file-center-actions.png')
}

async function main() {
  const miniProgram = await automator.connect({ wsEndpoint })
  try {
    await installFileActionMocks(miniProgram)
    await verifyFileCenterPage(miniProgram)
  } finally {
    await restoreFileActionMocks(miniProgram)
    miniProgram.disconnect()
  }
  console.log('PASS: DevTools automator verified file center list, preview/download, storage card, and folder ACL view UI.')
}

main().catch((error) => {
  console.error('FAIL:', error && error.stack ? error.stack : error)
  console.error(`Hint: set WECHAT_AUTOMATOR_WS to a DevTools miniprogram-automator endpoint. Current WeChat DevTools may not support the legacy --auto-port option; verify CLI auto --help before relying on ws://127.0.0.1:9421.`)
  process.exit(2)
})
