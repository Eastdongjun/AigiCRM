const { getCustomer, getCustomerLogs } = require('../../../api/customer')
const { getRecords } = require('../../../api/incentive')
const { getDownloadUrl } = require('../../../api/file')
const { requireLogin, hasPermission, getToken } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')
const { buildIncentiveFileGroups, buildIncentiveFiles } = require('../../../utils/incentive-files')

function normalizeLog(item) {
  return {
    ...item,
    type: 'log',
    dotClass: 'timeline-dot log',
    typeClass: 'timeline-type log',
    typeText: '跟进',
    content: item.content || item.remark || '跟进记录',
    time: item.createdAt || item.createTime || '-',
    creatorName: item.userName || item.createByName || item.creatorName || item.updatedByName || '',
    files: [],
    fileGroups: []
  }
}

function incentiveCodeOf(item) {
  const value = [
    item && item.categoryCode,
    item && item.categoryType,
    item && item.type,
    item && item.category,
    item && item.categoryName
  ].filter(Boolean).join(' ').toLowerCase()
  if (/plan|方案|两谈两做/.test(value)) return 'plan'
  if (/visit|参观|外出/.test(value)) return 'visit'
  if (/exchange|交流|拜访/.test(value)) return 'exchange'
  if (/trial|试点|安装|涂层|防滑|机封|密封|润滑/.test(value)) return 'trial'
  return ''
}

function detailLabelOf(item) {
  const code = incentiveCodeOf(item)
  const sub = (item && (item.categoryName || item.category || '')) || ''
  if (code === 'plan') return `${sub}文件详情描述`
  if (code === 'visit' || code === 'exchange') return `${sub}详情描述`
  return `${sub || '试点'}安装详情`
}

function remarkLabelOf(item) {
  const sub = (item && (item.categoryName || item.category || '')) || ''
  return `${sub || '试点'}安装备注`
}

function normalizeIncentive(item) {
  const isTrialRecord = incentiveCodeOf(item).startsWith('trial')
  const fileGroups = buildIncentiveFileGroups(item)
  return {
    ...item,
    type: 'incentive',
    dotClass: 'timeline-dot incentive',
    typeClass: 'timeline-type incentive',
    typeText: '四项激励',
    content: item.preInstallQuestion || item.description || item.product || item.categoryName || '四项激励上传记录',
    detailLabel: detailLabelOf(item),
    isTrialRecord,
    time: item.createdAt || item.createTime || '-',
    creatorName: item.userName || item.updatedByName || '',
    files: buildIncentiveFiles(item),
    fileGroups
  }
}

function buildTimelineItemView(item) {
  const isIncentive = item.type === 'incentive'
  const metaTags = []
  if (isIncentive && item.period) metaTags.push(`周期：${item.period}`)
  if (isIncentive && item.specificProduct) metaTags.push(`具体产品：${item.specificProduct}`)
  if (isIncentive && item.relatedProducts) metaTags.push(`相关产品：${item.relatedProducts}`)
  const fileGroups = (item.fileGroups || []).map((group, groupIndex) => ({
    ...group,
    groupIndex,
    countText: `${(group.files || []).length} 个`,
    files: (group.files || []).map((file, fileIndex) => ({
      ...file,
      fileIndex
    }))
  }))
  const showTrialRemark = Boolean(isIncentive && item.remark && item.isTrialRecord)
  return {
    ...item,
    contentText: item.content || item.remark || '跟进记录',
    creatorText: item.creatorName || item.userName ? `记录人：${item.creatorName || item.userName}` : '',
    showIncentiveMeta: metaTags.length > 0,
    metaTags,
    showPreInstallQuestion: Boolean(isIncentive && item.preInstallQuestion),
    preInstallQuestionText: item.preInstallQuestion || '',
    showTrialRemark,
    remarkLabel: remarkLabelOf(item),
    remarkText: item.remark || '',
    showDetailAction: isIncentive,
    showFiles: fileGroups.length > 0,
    fileGroups
  }
}

function sortByTimeDesc(a, b) {
  return String(b.time || '').localeCompare(String(a.time || ''))
}

function monthKeyOf(item) {
  const value = String(item && item.time ? item.time : '')
  const match = value.match(/^(\d{4})-(\d{2})/)
  return match ? `${match[1]}-${match[2]}` : 'unknown'
}

function monthLabelOf(key) {
  if (key === 'unknown') return '未标记月份'
  const [year, month] = key.split('-')
  return `${year}年${Number(month)}月`
}

function groupTimeline(items) {
  const groups = []
  const groupMap = {}
  ;(items || []).forEach((item, originalIndex) => {
    const key = monthKeyOf(item)
    if (!groupMap[key]) {
      groupMap[key] = { key, label: monthLabelOf(key), count: 0, countText: '', items: [] }
      groups.push(groupMap[key])
    }
    groupMap[key].items.push(buildTimelineItemView({ ...item, originalIndex }))
    groupMap[key].count += 1
  })
  groups.forEach((group) => { group.countText = `${group.count} 条` })
  return groups
}

function buildTimelineView(data) {
  const loading = Boolean(data.loading)
  return {
    showLoading: loading,
    loadingText: '加载中...',
    previewLoading: Boolean(data.previewing),
    showEmpty: !loading && Boolean(data.showEmpty)
  }
}

Page({
  data: {
    id: '',
    customerName: '客户跟进记录',
    logs: [],
    timelineGroups: [],
    showEmpty: false,
    loading: false,
    previewing: false,
    previewVideoPath: '',
    timelineView: buildTimelineView({ loading: false, previewing: false, showEmpty: false })
  },
  setTimelineData(patch) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, timelineView: buildTimelineView(next) })
  },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('customer:view')) {
      wx.showToast({ title: '没有客户查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
      return
    }
    this.setTimelineData({ id: options.id || '' })
    this.loadTimeline()
  },
  onPullDownRefresh() { this.loadTimeline().finally(() => wx.stopPullDownRefresh()) },
  loadTimeline() {
    if (!this.data.id) return Promise.resolve()
    this.setTimelineData({ loading: true })
    if (getApp().globalData.demoMode) {
      this.setTimelineData({
        customerName: '山西晋能集团中阳煤业',
        logs: [
          normalizeLog({ id: 1, content: '完成现场技术交流，确认下一步合作计划。', createdAt: '2026-09-08 10:30', userName: '李经理' }),
          normalizeIncentive({ id: 2, categoryName: '交流拜访', period: '2026全年', specificProduct: '技术交流纪要', preInstallQuestion: '上传现场交流照片与会议纪要。', remark: '客户反馈后续可安排二次交流。', createdAt: '2026-09-07 16:20', userName: '张三', fileId: '[101,102]', fileName: '现场交流照片.jpg,会议纪要.pdf' }),
          normalizeLog({ id: 3, content: '电话跟进项目进展，客户反馈良好。', createdAt: '2026-09-05 15:20', userName: '王工' })
        ],
        loading: false
      })
      this.rebuildTimelineGroups()
      return Promise.resolve()
    }
    const incentivePromise = hasPermission('incentive:view') ? getRecords({ customerId: this.data.id }).catch(() => []) : Promise.resolve([])
    return Promise.all([getCustomer(this.data.id), getCustomerLogs(this.data.id), incentivePromise]).then(([customer, logs, incentives]) => {
      const timeline = [
        ...(logs || []).map(normalizeLog),
        ...(incentives || []).map(normalizeIncentive)
      ].sort(sortByTimeDesc)
      this.setTimelineData({ customerName: customer.customerName || customer.name || '客户跟进记录', logs: timeline, previewVideoPath: '' })
      this.rebuildTimelineGroups()
    }).catch((error) => showError(error, '跟进记录加载失败')).finally(() => this.setTimelineData({ loading: false }))
  },
  rebuildTimelineGroups() {
    const timelineGroups = groupTimeline(this.data.logs)
    this.setTimelineData({ timelineGroups, showEmpty: timelineGroups.length === 0 })
  },
  goIncentiveDetail(event) {
    const item = this.data.logs[event.currentTarget.dataset.logIndex]
    if (!item || item.type !== 'incentive' || !item.id) return
    wx.navigateTo({ url: `/pages/incentive/detail/index?id=${item.id}` })
  },
  previewAttachment(event) {
    const file = this.findFile(event)
    if (!file) return
    if (getApp().globalData.demoMode) return wx.showToast({ title: '演示附件不可打开', icon: 'none' })
    return this.fetchAttachment(file, 'preview')
  },
  downloadAttachment(event) {
    const file = this.findFile(event)
    if (!file) return
    if (getApp().globalData.demoMode) return wx.showToast({ title: '演示附件不可下载', icon: 'none' })
    return this.fetchAttachment(file, 'download')
  },
  findFile(event) {
    const item = this.data.logs[event.currentTarget.dataset.logIndex]
    const groupIndex = event.currentTarget.dataset.groupIndex
    const fileIndex = event.currentTarget.dataset.fileIndex
    if (item && item.fileGroups && groupIndex !== undefined && fileIndex !== undefined) {
      const group = item.fileGroups[Number(groupIndex)]
      return group && group.files ? group.files[Number(fileIndex)] : null
    }
    return item && item.files ? item.files[event.currentTarget.dataset.fileIndex] : null
  },
  fetchAttachment(file, mode) {
    if (this.data.previewing) return
    const url = getDownloadUrl(file)
    if (!url) return wx.showToast({ title: '附件地址缺失', icon: 'none' })
    this.setTimelineData({ previewing: true })
    wx.showLoading({ title: mode === 'download' ? '下载中' : '准备预览' })
    return new Promise((resolve) => {
      wx.downloadFile({
        url,
        header: getToken() ? { Authorization: 'Bearer ' + getToken() } : {},
        success: (result) => {
          if (result.statusCode !== 200) return showError(new Error('文件下载失败'), '文件下载失败')
          if (mode === 'download') return this.saveDownloadedFile(result.tempFilePath)
          this.openDownloadedFile(file, result.tempFilePath)
        },
        fail: (error) => showError(error, '文件下载失败'),
        complete: () => { wx.hideLoading(); this.setTimelineData({ previewing: false }); resolve() }
      })
    })
  },
  openDownloadedFile(file, tempFilePath) {
    if (file.type === 'image') return wx.previewImage({ urls: [tempFilePath], fail: (error) => showError(error, '图片预览失败') })
    if (file.type === 'video') return this.setData({ previewVideoPath: tempFilePath })
    wx.openDocument({ filePath: tempFilePath, showMenu: true, fail: (error) => showError(error, '无法直接预览，可下载后查看') })
  },
  saveDownloadedFile(tempFilePath) {
    const options = {
      tempFilePath,
      success: () => wx.showToast({ title: '已保存到本机', icon: 'success' }),
      fail: (error) => showError(error, '保存失败')
    }
    const fs = typeof wx.getFileSystemManager === 'function' ? wx.getFileSystemManager() : null
    if (fs && typeof fs.saveFile === 'function') return fs.saveFile(options)
    return wx.saveFile(options)
  },
  goBack() { wx.navigateBack() }
})
