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

function actionCallSummary(calls) {
  return JSON.stringify(calls)
}

function assertAttachmentTags(files, expectedTags, pageName) {
  for (const tag of expectedTags) {
    assert(files.some((file) => file.tag === tag), `${pageName} missing ${tag} attachment`)
  }
}

async function textList(elements) {
  const texts = []
  for (const element of elements) {
    texts.push(await element.text())
  }
  return texts
}

async function screenshot(miniProgram, name) {
  if (!shouldScreenshot) return
  const path = resolve(screenshotDir, name)
  mkdirSync(dirname(path), { recursive: true })
  await miniProgram.screenshot({ path })
}

async function resetAttachmentActionCalls(miniProgram) {
  await miniProgram.evaluate(() => {
    globalThis.__attachmentActionCalls = {
      downloadFile: [],
      previewImage: [],
      openDocument: [],
      saveFile: []
    }
    if (!globalThis.__aigicrmAttachmentActionsPatched && typeof wx !== 'undefined') {
      globalThis.__aigicrmOriginalAttachmentActions = {
        downloadFile: wx.downloadFile,
        previewImage: wx.previewImage,
        openDocument: wx.openDocument,
        saveFile: wx.saveFile,
        getFileSystemManager: wx.getFileSystemManager
      }
      globalThis.__aigicrmAttachmentActionsPatched = true
    }
    if (typeof wx !== 'undefined') {
      wx.downloadFile = function (options) {
        const calls = globalThis.__attachmentActionCalls || (globalThis.__attachmentActionCalls = { downloadFile: [], previewImage: [], openDocument: [], saveFile: [] })
        calls.downloadFile.push({ url: options && options.url, header: options && options.header })
        if (options && typeof options.success === 'function') options.success({ statusCode: 200, tempFilePath: '/tmp/aigicrm-attachment-download.mock' })
        if (options && typeof options.complete === 'function') options.complete({})
      }
      wx.previewImage = function (options) {
        const calls = globalThis.__attachmentActionCalls || (globalThis.__attachmentActionCalls = { downloadFile: [], previewImage: [], openDocument: [], saveFile: [] })
        calls.previewImage.push({ urls: options && options.urls })
        if (options && typeof options.success === 'function') options.success({})
        if (options && typeof options.complete === 'function') options.complete({})
      }
      wx.openDocument = function (options) {
        const calls = globalThis.__attachmentActionCalls || (globalThis.__attachmentActionCalls = { downloadFile: [], previewImage: [], openDocument: [], saveFile: [] })
        calls.openDocument.push({ filePath: options && options.filePath, showMenu: options && options.showMenu })
        if (options && typeof options.success === 'function') options.success({})
        if (options && typeof options.complete === 'function') options.complete({})
      }
      wx.saveFile = function (options) {
        const calls = globalThis.__attachmentActionCalls || (globalThis.__attachmentActionCalls = { downloadFile: [], previewImage: [], openDocument: [], saveFile: [] })
        calls.saveFile.push({ tempFilePath: options && options.tempFilePath })
        if (options && typeof options.success === 'function') options.success({ savedFilePath: '/tmp/aigicrm-attachment-download.saved' })
        if (options && typeof options.complete === 'function') options.complete({})
      }
      wx.getFileSystemManager = function () {
        const original = globalThis.__aigicrmOriginalAttachmentActions && globalThis.__aigicrmOriginalAttachmentActions.getFileSystemManager
        const manager = typeof original === 'function' ? original.call(wx) : {}
        return {
          ...manager,
          saveFile(options) {
            const calls = globalThis.__attachmentActionCalls || (globalThis.__attachmentActionCalls = { downloadFile: [], previewImage: [], openDocument: [], saveFile: [] })
            calls.saveFile.push({ tempFilePath: options && options.tempFilePath })
            if (options && typeof options.success === 'function') options.success({ savedFilePath: '/tmp/aigicrm-attachment-download.saved' })
            if (options && typeof options.complete === 'function') options.complete({})
          }
        }
      }
    }
  })
}

async function attachmentActionCalls(miniProgram) {
  return await miniProgram.evaluate(() => globalThis.__attachmentActionCalls)
}

async function installAttachmentActionMocks(miniProgram) {
  await resetAttachmentActionCalls(miniProgram)
}

async function restoreAttachmentActionMocks(miniProgram) {
  await miniProgram.evaluate(() => {
    if (globalThis.__aigicrmAttachmentActionsPatched && globalThis.__aigicrmOriginalAttachmentActions && typeof wx !== 'undefined') {
      wx.downloadFile = globalThis.__aigicrmOriginalAttachmentActions.downloadFile
      wx.previewImage = globalThis.__aigicrmOriginalAttachmentActions.previewImage
      wx.openDocument = globalThis.__aigicrmOriginalAttachmentActions.openDocument
      wx.saveFile = globalThis.__aigicrmOriginalAttachmentActions.saveFile
      wx.getFileSystemManager = globalThis.__aigicrmOriginalAttachmentActions.getFileSystemManager
    }
    globalThis.__aigicrmAttachmentActionsPatched = false
    globalThis.__aigicrmOriginalAttachmentActions = null
  })
}

async function tapAttachmentAction(row, text) {
  const buttons = await row.$$('button')
  for (const button of buttons) {
    if ((await button.text()) === text) {
      await button.tap()
      return
    }
  }
  throw new Error(`attachment action button not found: ${text}`)
}

async function enableRealAttachmentMode(miniProgram) {
  await miniProgram.evaluate(() => {
    const app = getApp()
    app.globalData.demoMode = false
    app.globalData.apiBaseUrl = app.globalData.apiBaseUrl || 'https://aigicrm-automator.local'
  })
}

async function enableDemoMode(miniProgram) {
  await miniProgram.evaluate(() => {
    getApp().globalData.demoMode = true
  })
}

function eventWithDataset(dataset) {
  return { currentTarget: { dataset } }
}

async function verifyUploadPage(miniProgram) {
  await enableDemoMode(miniProgram)
  const page = await miniProgram.reLaunch('/pages/incentive/upload/index')
  await page.waitFor(1000)

  const labels = await textList(await page.$$('.label'))
  for (const label of ['客户 *', '激励分类 *', '团队共享', '文件槽位 *']) {
    assert(labels.includes(label), `upload page missing label: ${label}`)
  }

  await page.callMethod('setSelectedSlotFile', '/tmp/aigicrm-preview-test.jpg', '现场照片.jpg', 'image')
  await page.callMethod('setSelectedSlotFile', '/tmp/aigicrm-preview-test-2.jpg', '现场照片-补充.jpg', 'image')
  await page.waitFor(500)
  await miniProgram.pageScrollTo(1200)
  await page.waitFor(500)

  const fileSlots = await page.data('fileSlots')
  assert(fileSlots[0].fileName === '现场照片.jpg', 'upload page selected file name was not set')
  assert(fileSlots[0].fileKind === 'image', 'upload page selected file kind was not image')
  assert(fileSlots[0].files.length === 2, 'upload page selected slot must keep multiple files like PC')
  assert((await page.$$('.selected-file-row')).length === 2, 'upload page selected-file rows are missing')
  assert((await page.$$('.selected-file-thumb')).length === 2, 'upload page selected image thumbnails are missing')

  const buttons = await textList(await page.$$('button'))
  for (const button of ['预览', '下载', '移除', '提交激励']) {
    assert(buttons.includes(button), `upload page missing button: ${button}`)
  }

  await resetAttachmentActionCalls(miniProgram)
  await page.callMethod('previewSelectedFile', eventWithDataset({ index: 0, fileIndex: 1 }))
  await page.waitFor(100)
  await page.callMethod('downloadSelectedFile', eventWithDataset({ index: 0, fileIndex: 1 }))
  await page.waitFor(100)
  let calls = await attachmentActionCalls(miniProgram)
  assert(calls.previewImage.length >= 1, `upload page selected image must call previewImage: ${actionCallSummary(calls)}`)
  assert(calls.saveFile.length >= 1, `upload page selected image download must call saveFile: ${actionCallSummary(calls)}`)

  await page.setData({ selectedSlotIndex: 1 })
  await page.callMethod('setSelectedSlotFile', '/tmp/aigicrm-preview-test.pdf', '安装方案.pdf', 'file')
  await page.waitFor(100)
  await resetAttachmentActionCalls(miniProgram)
  await page.callMethod('previewSelectedFile', eventWithDataset({ index: 1 }))
  await page.waitFor(100)
  await page.callMethod('downloadSelectedFile', eventWithDataset({ index: 1 }))
  await page.waitFor(100)
  calls = await attachmentActionCalls(miniProgram)
  assert(calls.openDocument.length >= 1, `upload page selected document must call openDocument: ${actionCallSummary(calls)}`)
  assert(calls.saveFile.length >= 1, `upload page selected document download must call saveFile: ${actionCallSummary(calls)}`)

  await enableRealAttachmentMode(miniProgram)
  await page.setData({
    fileSlots: [
      { key: 'filePreId', label: '安装前', fileName: '历史照片.jpg', filePath: '', fileKind: 'image', files: [{ fileId: 701, fileName: '历史照片.jpg', filePath: '', fileKind: 'image', existing: true }] }
    ]
  })
  await resetAttachmentActionCalls(miniProgram)
  await page.callMethod('previewSelectedFile', eventWithDataset({ index: 0, fileIndex: 0 }))
  await page.waitFor(100)
  await page.callMethod('downloadSelectedFile', eventWithDataset({ index: 0, fileIndex: 0 }))
  await page.waitFor(100)
  calls = await attachmentActionCalls(miniProgram)
  assert(calls.downloadFile.length >= 2, `upload edit mode existing file must download before preview/save: ${actionCallSummary(calls)}`)
  assert(calls.previewImage.length >= 1, `upload edit mode existing image must call previewImage: ${actionCallSummary(calls)}`)
  assert(calls.saveFile.length >= 1, `upload edit mode existing file download must call saveFile: ${actionCallSummary(calls)}`)
  await screenshot(miniProgram, 'upload-selected-file.png')
}

async function verifyDetailPage(miniProgram) {
  await enableDemoMode(miniProgram)
  const page = await miniProgram.reLaunch('/pages/incentive/detail/index?id=9001')
  await page.waitFor(1000)

  await page.callMethod('setRecord', {
    id: 9001,
    customerId: 501,
    customerName: '自动化测试客户',
    industry: '制造业',
    category: '产品试点',
    categoryName: '产品试点',
    score: 12,
    userName: '测试用户',
    createdAt: '2026-09-15 17:35:00',
    remark: '自动化注入的激励详情附件记录',
    filePreId: '[301]',
    filePreName: '安装前照片.jpg',
    filePreMimeType: 'image/jpeg',
    fileMidId: '[302]',
    fileMidName: '安装中方案.pdf',
    fileMidMimeType: 'application/pdf',
    filePostId: '[303]',
    filePostName: '安装后验收.png',
    filePostMimeType: 'image/png',
    fileTrackId: '[304]',
    fileTrackName: '跟踪记录.txt',
    fileTrackMimeType: 'text/plain',
    fileId: '[305]',
    fileName: '佐证材料.docx',
    fileMimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
  })
  await page.waitFor(500)
  await miniProgram.pageScrollTo(650)
  await page.waitFor(500)

  const files = await page.data('files')
  assert(files.length === 5, `detail page expected 5 files, got ${files.length}`)
  assertAttachmentTags(files, ['安装前', '安装中', '安装后', '跟踪', '佐证'], 'detail page')
  assert(files.some((file) => file.type === 'image'), 'detail page missing image attachment')
  assert(files.some((file) => file.type === 'document'), 'detail page missing document attachment')
  assert((await page.$$('.attachment-row')).length === 5, 'detail page attachment rows are missing')

  const buttons = await textList(await page.$$('button'))
  assert(buttons.filter((text) => text === '打开').length === 5, 'detail page must show open button per attachment')
  assert(buttons.filter((text) => text === '下载').length === 5, 'detail page must show download button per attachment')

  await resetAttachmentActionCalls(miniProgram)
  await enableRealAttachmentMode(miniProgram)
  await page.callMethod('previewAttachment', eventWithDataset({ index: 0 }))
  await page.waitFor(300)
  await page.setData({ previewing: false })
  await page.callMethod('previewAttachment', eventWithDataset({ index: 1 }))
  await page.waitFor(300)
  await page.setData({ previewing: false })
  await page.callMethod('downloadAttachment', eventWithDataset({ index: 1 }))
  await page.waitFor(300)
  const calls = await attachmentActionCalls(miniProgram)
  assert(calls.downloadFile.length === 3, `detail page attachment actions must call downloadFile: ${actionCallSummary(calls)}`)
  assert(calls.previewImage.length === 1, `detail page downloaded image must call previewImage: ${actionCallSummary(calls)}`)
  assert(calls.openDocument.length === 1, `detail page document preview must call openDocument: ${actionCallSummary(calls)}`)
  assert(calls.saveFile.length === 1, `detail page document download must call saveFile: ${actionCallSummary(calls)}`)
  await screenshot(miniProgram, 'detail-attachments.png')
}

async function verifyListPage(miniProgram) {
  await enableDemoMode(miniProgram)
  const page = await miniProgram.reLaunch('/pages/incentive/index')
  await page.waitFor(1000)

  const filterText = (await textList(await page.$$('.incentive-search-card'))).join('\n')
  for (const expected of ['搜索客户 / 产品 / 关键字', '全部行业', '全部类型', '全部产品', '全部组织', '开始日期', '截止日期']) {
    assert(filterText.includes(expected), `list page missing PC-parity filter: ${expected}`)
  }
  await page.setData({
    keyword: '煤气管道',
    users: [{ id: 7, realName: '王工' }],
    selectedUserIndex: 1,
    selectedIndustryIndex: 1,
    selectedDevTypeIndex: 1,
    selectedProductIndex: 4,
    orgOptions: [{ id: '', label: '全部组织' }, { id: 21, label: '山西办' }],
    selectedOrgIndex: 1,
    startDate: '2026-09-01',
    endDate: '2026-09-30'
  })
  const params = await page.callMethod('buildRecordParams')
  assert(params.customerName === '煤气管道' && params.keyword === '煤气管道', `list page keyword params are not PC-aligned: ${JSON.stringify(params)}`)
  assert(params.salesperson === '王工', `list page salesperson param is missing: ${JSON.stringify(params)}`)
  assert(params.industry === '冶金', `list page industry param is missing: ${JSON.stringify(params)}`)
  assert(params.devType === '成熟创新', `list page devType param is missing: ${JSON.stringify(params)}`)
  assert(params.product === '煤气管道', `list page product param is missing: ${JSON.stringify(params)}`)
  assert(params.orgIds === '21', `list page orgIds param is missing: ${JSON.stringify(params)}`)
  assert(params.startDate === '2026-09-01' && params.endDate === '2026-09-30', `list page date params are missing: ${JSON.stringify(params)}`)

  const item = {
    id: 9001,
    customerId: 501,
    name: '自动化测试客户',
    customerName: '自动化测试客户',
    industry: '制造业',
    score: 12,
    count: 1,
    category: '产品试点',
    unread: false,
    summary: '自动化注入的列表附件记录',
    files: [
      { id: 301, name: '安装前照片.jpg', tag: '安装前', type: 'image', typeText: '图片' },
      { id: 302, name: '安装中方案.pdf', tag: '安装中', type: 'document', typeText: '文档' },
      { id: 303, name: '安装后验收.png', tag: '安装后', type: 'image', typeText: '图片' },
      { id: 304, name: '跟踪记录.txt', tag: '跟踪', type: 'document', typeText: '文档' },
      { id: 305, name: '佐证材料.docx', tag: '佐证', type: 'document', typeText: '文档' }
    ],
    records: [{ id: 9002, categoryName: '安装后跟踪', unread: true, canEdit: false, canDelete: false }],
    detail: { id: 9001 }
  }
  await page.setData({
    allRecords: [item],
    records: [item],
    summary: { customers: 1, records: 1, categories: 1, files: 5, unread: 0, score: 12 },
    loading: false
  })
  await page.waitFor(500)

  assert((await page.$$('.record-file-list')).length === 1, 'list page attachment list is missing')
  assert(await page.data('order') === '', 'list page score order should default to PC-like unsorted state')
  await page.setData({
    allRecords: [
      { ...item, id: 'low-score', name: '低分客户', score: 1 },
      { ...item, id: 'high-score', name: '高分客户', score: 9 }
    ],
    category: '全部'
  })
  await page.callMethod('sort')
  assert(await page.data('records[0].name') === '低分客户', 'default score order must preserve source order')
  await page.callMethod('choose', { detail: { value: 1 } })
  assert(await page.data('records[0].name') === '高分客户', 'descending score order must put high score first')
  await page.callMethod('choose', { detail: { value: 2 } })
  assert(await page.data('records[0].name') === '低分客户', 'ascending score order must put low score first')
  await page.setData({ allRecords: [item], records: [item], order: '', selectedOrderIndex: 0 })
  const files = await page.data('records[0].files')
  assertAttachmentTags(files, ['安装前', '安装中', '安装后', '跟踪', '佐证'], 'list page')
  assert((await page.$$('.record-file-row')).length === 5, 'list page attachment rows are missing')
  const listText = (await textList(await page.$$('.incentive-record-card'))).join('\n')
  assert(listText.includes('已读'), 'list page must expose read status for customer records')
  await page.callMethod('toggleCustomerRecords', eventWithDataset({ index: 0 }))
  await page.waitFor(300)
  const childText = (await textList(await page.$$('.customer-record-list'))).join('\n')
  assert(childText.includes('未读'), 'list page expanded child records must expose read status')
  await page.callMethod('markChildRecordRead', 0, 0)
  assert(await page.data('records[0].records[0].unread') === false, 'child record detail open must mark child record read')
  await page.setData({ 'records[0].unread': true, 'records[0].records[0].unread': true })
  await page.callMethod('markRecordRead', 0)
  assert(await page.data('records[0].unread') === false, 'record detail open must mark customer record read')
  assert(await page.data('records[0].records[0].unread') === false, 'record detail open must mark all child records read')

  const buttons = await textList(await page.$$('button'))
  assert(buttons.filter((text) => text === '打开').length === 5, 'list page must show open button per attachment')
  assert(buttons.filter((text) => text === '下载').length === 5, 'list page must show download button per attachment')

  await resetAttachmentActionCalls(miniProgram)
  await enableRealAttachmentMode(miniProgram)
  await page.callMethod('previewAttachment', eventWithDataset({ recordIndex: 0, fileIndex: 0 }))
  await page.waitFor(300)
  await page.setData({ previewing: false })
  await page.callMethod('previewAttachment', eventWithDataset({ recordIndex: 0, fileIndex: 1 }))
  await page.waitFor(300)
  await page.setData({ previewing: false })
  await page.callMethod('downloadAttachment', eventWithDataset({ recordIndex: 0, fileIndex: 1 }))
  await page.waitFor(300)
  const calls = await attachmentActionCalls(miniProgram)
  assert(calls.downloadFile.length === 3, `list page attachment actions must call downloadFile: ${actionCallSummary(calls)}`)
  assert(calls.previewImage.length === 1, `list page downloaded image must call previewImage: ${actionCallSummary(calls)}`)
  assert(calls.openDocument.length === 1, `list page document preview must call openDocument: ${actionCallSummary(calls)}`)
  assert(calls.saveFile.length === 1, `list page document download must call saveFile: ${actionCallSummary(calls)}`)
  await screenshot(miniProgram, 'list-attachments.png')
}

async function verifyReadStatsPage(miniProgram) {
  await enableDemoMode(miniProgram)
  const page = await miniProgram.reLaunch('/pages/incentive/index')
  await page.waitFor(1000)

  await page.setData({
    activeTab: 'reads',
    canViewReadStats: true,
    canViewReadRanking: true,
    readStats: [
      { userId: 101, realName: '张三', readCount: 6, unreadCount: 2 },
      { userId: 102, realName: '李四', readCount: 4, unreadCount: 0 }
    ],
    readRanking: [
      { userId: 101, realName: '张三', readCount: 12 },
      { userId: 102, realName: '李四', readCount: 8 }
    ]
  })
  await page.waitFor(500)

  const periodText = (await textList(await page.$$('.read-period-row'))).join('\n')
  assert(periodText.includes('统计月份'), 'read page missing period selector')
  const periodOptions = await page.data('readPeriodOptions')
  assert(Array.isArray(periodOptions) && periodOptions.length >= 2, 'read page period options are missing')
  await page.callMethod('chooseReadPeriod', { detail: { value: 1 } })
  await page.waitFor(300)
  assert(await page.data('readPeriod') === periodOptions[1].value, 'read page period picker must update selected period')

  const panels = await page.$$('.read-panel')
  assert(panels.length === 2, `read page expected stats and ranking panels, got ${panels.length}`)
  const text = (await textList(await page.$$('.stat-row'))).join('\n')
  for (const expected of ['张三', '已读 6 / 未读 2', '12 次', '李四']) {
    assert(text.includes(expected), `read page missing ${expected}`)
  }
  await screenshot(miniProgram, 'read-stats-ranking.png')
}

async function verifyTimelinePage(miniProgram) {
  await enableDemoMode(miniProgram)
  const page = await miniProgram.reLaunch('/pages/customer/timeline/index?id=501')
  await page.waitFor(1000)

  await page.setData({
    customerName: '自动化测试客户',
    logs: [
      {
        id: 'timeline-log-1',
        type: 'log',
        typeText: '跟进',
        content: '自动化注入的普通跟进记录',
        time: '2026-09-15 18:00:00',
        creatorName: '跟进记录人',
        files: []
      },
      {
        id: 'timeline-9001',
        type: 'incentive',
        typeText: '激励',
        content: '自动化注入的客户时间轴激励附件记录',
        time: '2026-09-15 17:45:00',
        creatorName: '测试用户',
        files: [
          { id: 301, name: '时间轴现场照片.jpg', tag: '安装前', type: 'image', typeText: '图片' },
          { id: 302, name: '时间轴安装中方案.pdf', tag: '安装中', type: 'document', typeText: '文档' },
          { id: 303, name: '时间轴验收照片.png', tag: '安装后', type: 'image', typeText: '图片' },
          { id: 304, name: '时间轴跟踪记录.txt', tag: '跟踪', type: 'document', typeText: '文档' },
          { id: 305, name: '时间轴佐证材料.docx', tag: '佐证', type: 'document', typeText: '文档' }
        ]
      }
    ],
    loading: false,
    previewing: false
  })
  await page.callMethod('rebuildTimelineGroups')
  await page.waitFor(500)

  assert((await page.$$('.timeline-month')).length === 1, 'timeline page monthly grouped section is missing')
  assert((await page.$$('.timeline-month-count')).length === 1, 'timeline page monthly grouped count is missing')
  assert((await page.$$('.timeline-files')).length === 1, 'timeline page attachment list is missing')
  const pageText = (await textList(await page.$$('.timeline-card'))).join('\n')
  assert(pageText.includes('记录人：跟进记录人'), `timeline page normal log creator is missing: ${pageText}`)
  const files = await page.data('logs[1].files')
  assertAttachmentTags(files, ['安装前', '安装中', '安装后', '跟踪', '佐证'], 'timeline page')
  assert((await page.$$('.timeline-file-row')).length === 5, 'timeline page attachment rows are missing')

  const buttons = await textList(await page.$$('button'))
  assert(buttons.includes('详情'), 'timeline page must show incentive detail button')
  assert(buttons.filter((text) => text === '打开').length === 5, 'timeline page must show open button per attachment')
  assert(buttons.filter((text) => text === '下载').length === 5, 'timeline page must show download button per attachment')

  await resetAttachmentActionCalls(miniProgram)
  await enableRealAttachmentMode(miniProgram)
  await page.callMethod('previewAttachment', eventWithDataset({ logIndex: 1, fileIndex: 0 }))
  await page.waitFor(300)
  await page.setData({ previewing: false })
  await page.callMethod('previewAttachment', eventWithDataset({ logIndex: 1, fileIndex: 1 }))
  await page.waitFor(300)
  await page.setData({ previewing: false })
  await page.callMethod('downloadAttachment', eventWithDataset({ logIndex: 1, fileIndex: 1 }))
  await page.waitFor(300)
  const calls = await attachmentActionCalls(miniProgram)
  assert(calls.downloadFile.length === 3, `timeline page attachment actions must call downloadFile: ${actionCallSummary(calls)}`)
  assert(calls.previewImage.length === 1, `timeline page downloaded image must call previewImage: ${actionCallSummary(calls)}`)
  assert(calls.openDocument.length === 1, `timeline page document preview must call openDocument: ${actionCallSummary(calls)}`)
  assert(calls.saveFile.length === 1, `timeline page document download must call saveFile: ${actionCallSummary(calls)}`)
  await screenshot(miniProgram, 'timeline-attachments.png')
}

async function main() {
  const miniProgram = await automator.connect({ wsEndpoint })
  try {
    await installAttachmentActionMocks(miniProgram)
    await verifyUploadPage(miniProgram)
    await verifyDetailPage(miniProgram)
    await verifyListPage(miniProgram)
    await verifyReadStatsPage(miniProgram)
    await verifyTimelinePage(miniProgram)
  } finally {
    await restoreAttachmentActionMocks(miniProgram)
    miniProgram.disconnect()
  }
  console.log('PASS: DevTools automator verified incentive upload, list, detail, read stats/ranking, and customer timeline attachment UI.')
}

main().catch((error) => {
  console.error('FAIL:', error && error.stack ? error.stack : error)
  console.error(`Hint: set WECHAT_AUTOMATOR_WS to a DevTools miniprogram-automator endpoint. Current WeChat DevTools may not support the legacy --auto-port option; verify CLI auto --help before relying on ws://127.0.0.1:9421.`)
  process.exit(2)
})
