const { deleteRecord, getCustomerRecords, getGrantCheck, getScoreByCustomer, markIncentiveRead } = require('../../../api/incentive')
const { incentiveRecords } = require('../../../data/demo')
const { getDownloadUrl, getDownloadToken } = require('../../../api/file')
const { hasPermission, isAdmin, requireLogin, getToken } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')
const { buildIncentiveFileGroups, buildIncentiveFiles } = require('../../../utils/incentive-files')

function flatten(rows) {
  return (rows || []).flatMap((customer) => (customer.records || []).map((record) => ({ ...record, customerName: customer.customerName, industry: customer.industry || '其他', category: record.category || record.type || '未分类', score: record.score || record.points || 0 })))
}

function currentUserId() {
  const user = getApp().globalData.currentUser || {}
  return user.id || user.userId || ''
}

function incentiveCodeOf(record) {
  const value = [
    record && record.categoryCode,
    record && record.categoryType,
    record && record.type,
    record && record.category,
    record && record.categoryName
  ].filter(Boolean).join(' ').toLowerCase()
  if (/plan|方案|两谈两做/.test(value)) return 'plan'
  if (/visit|参观|外出/.test(value)) return 'visit'
  if (/exchange|交流|拜访/.test(value)) return 'exchange'
  if (/trial|试点|安装|涂层|防滑|机封|密封|润滑/.test(value)) return 'trial'
  return ''
}

function detailLabelOf(record) {
  const code = incentiveCodeOf(record)
  const sub = (record && (record.categoryName || record.category || '')) || ''
  if (code === 'plan') return `${sub}文件详情描述`
  if (code === 'visit' || code === 'exchange') return `${sub}详情描述`
  return `${sub || '试点'}安装详情`
}

function remarkLabelOf(record) {
  const sub = (record && (record.categoryName || record.category || '')) || ''
  return `${sub || '试点'}安装备注`
}

function textOrDash(value) {
  return value === undefined || value === null || value === '' ? '-' : String(value)
}

function buildFileGroupsView(fileGroups) {
  return (fileGroups || []).map((group, groupIndex) => ({
    ...group,
    groupIndex,
    countText: `${(group.files || []).length} 个`,
    files: (group.files || []).map((file, fileIndex) => ({
      ...file,
      fileIndex
    }))
  }))
}

function buildDetailView(record, customerScore, detailLabel, isTrialRecord, fileGroups = [], loading = false, canDeleteRecord = false, previewing = false) {
  const hasPreInstallQuestion = Boolean(record && record.preInstallQuestion)
  const hasTrialRemark = Boolean(record && record.remark && isTrialRecord)
  const fileGroupsView = buildFileGroupsView(fileGroups)
  const infoRows = [
    ['客户', record && (record.customerName || record.name)],
    ['行业', record && record.industry],
    ['分类', record && (record.category || record.categoryName)],
    ['相关产品', record && record.relatedProducts],
    ['具体产品', record && record.specificProduct],
    ['周期', record && record.period],
    ['客户累计积分', customerScore === null ? '加载中' : `${customerScore} 分`],
    ['上传人', record && (record.userName || record.updatedByName)],
    ['上传时间', record && (record.createdAt || record.createTime)]
  ].filter((row) => row[1] !== undefined && row[1] !== null && row[1] !== '')
  return {
    hasRecord: Boolean(record),
    showLoading: Boolean(loading),
    loadingText: '加载中...',
    previewLoading: Boolean(previewing),
    showEmpty: !loading && !record,
    title: record && (record.name || record.title || record.customerName) || '四项激励记录',
    scoreText: record && (record.score || record.points || 0) || 0,
    infoRows: infoRows.map(([label, value]) => ({ label, valueText: textOrDash(value) })),
    showPreInstallQuestion: hasPreInstallQuestion,
    detailLabel,
    preInstallQuestionText: record && record.preInstallQuestion || '',
    showTrialRemark: hasTrialRemark,
    remarkLabel: remarkLabelOf(record),
    remarkText: record && record.remark || '',
    showFallbackDescription: !hasPreInstallQuestion && !hasTrialRemark,
    fallbackDescription: record && (record.description || record.summary || record.product) || '暂无详情描述',
    showDeleteAction: Boolean(canDeleteRecord),
    showFileGroups: fileGroupsView.length > 0,
    fileGroups: fileGroupsView
  }
}

Page({
  data: {
    id: '',
    record: null,
    files: [],
    fileGroups: [],
    customerScore: null,
    detailLabel: '安装详情',
    isTrialRecord: false,
    detailView: buildDetailView(null, null, '安装详情', false, [], false, false, false),
    loading: false,
    previewing: false,
    previewVideoPath: '',
    canDeleteRecords: false,
    canDeleteRecord: false
  },
  setDetailData(patch) {
    const next = { ...this.data, ...patch }
    this.setData({
      ...patch,
      detailView: buildDetailView(next.record, next.customerScore, next.detailLabel, next.isTrialRecord, next.fileGroups, next.loading, next.canDeleteRecord, next.previewing)
    })
  },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('incentive:view')) {
      wx.showToast({ title: '没有四项激励查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
      return
    }
    this.setDetailData({ id: options.id || '', canDeleteRecords: !getApp().globalData.demoMode && isAdmin() })
    this.loadGrantCheck()
    this.loadDetail()
  },
  loadGrantCheck() {
    if (getApp().globalData.demoMode) return
    getGrantCheck().then((grant) => {
      this.setDetailData({ canDeleteRecords: Boolean(grant && grant.canDelete) || isAdmin() })
      this.updateDeleteState()
    }).catch(() => this.updateDeleteState())
  },
  loadDetail() {
    this.setDetailData({ loading: true })
    if (getApp().globalData.demoMode) {
      const item = incentiveRecords.find((row) => String(row.id) === String(this.data.id)) || incentiveRecords[0]
      this.setRecord(item)
      this.setDetailData({ loading: false })
      return Promise.resolve()
    }
    return getCustomerRecords().then((rows) => {
      const record = flatten(rows).find((item) => String(item.id) === String(this.data.id))
      if (!record) throw new Error('找不到四项激励记录')
      this.setRecord(record)
      if (record.unread) return markIncentiveRead(record.id)
    }).catch((error) => showError(error, '四项激励详情加载失败')).finally(() => this.setDetailData({ loading: false }))
  },
  setRecord(record) {
    const customerScore = getApp().globalData.demoMode ? (record && (record.totalScore || record.score || record.points || 0)) : null
    const detailLabel = detailLabelOf(record)
    const isTrialRecord = incentiveCodeOf(record).startsWith('trial')
    const files = buildIncentiveFiles(record)
    const fileGroups = buildIncentiveFileGroups(record)
    this.setDetailData({
      record,
      files,
      fileGroups,
      customerScore,
      detailLabel,
      isTrialRecord,
      previewVideoPath: ''
    })
    this.updateDeleteState()
    this.loadCustomerScore(record)
    this.loadThumbnails()
  },
  loadThumbnails() {
    if (getApp().globalData.demoMode) return
    const groups = this.data.fileGroups || []
    const hasImage = groups.some((group) => (group.files || []).some((file) => file && file.type === 'image' && file.id != null))
    if (!hasImage) return
    getDownloadToken().then((result) => {
      const token = result && (result.token || (result.data && result.data.token)) || ''
      if (!token) throw new Error('缩略图凭证为空')
      const next = groups.map((group) => ({
        ...group,
        files: (group.files || []).map((file) => {
          if (!file || file.type !== 'image' || file.id == null || file.thumbUrl) return file
          const base = getDownloadUrl(file, true)
          if (!base) return file
          const sep = base.indexOf('?') >= 0 ? '&' : '?'
          return { ...file, thumbUrl: base + sep + 'token=' + encodeURIComponent(token) }
        })
      }))
      this.setDetailData({ fileGroups: next })
    }).catch((error) => showError(error, '缩略图加载失败'))
  },
  updateDeleteState() {
    const record = this.data.record
    const userId = currentUserId()
    const ownerId = record && (record.userId || record.salespersonId)
    this.setDetailData({
      canDeleteRecord: Boolean(record && !getApp().globalData.demoMode && (this.data.canDeleteRecords || (userId && ownerId && String(userId) === String(ownerId))))
    })
  },
  loadCustomerScore(record) {
    if (!record || !record.customerId || getApp().globalData.demoMode) return
    getScoreByCustomer(record.customerId).then((score) => {
      const customerScore = Number(score || 0)
      this.setDetailData({ customerScore })
    }).catch((error) => showError(error, '客户累计积分加载失败'))
  },
  previewAttachment(event) {
    const file = this.findAttachment(event)
    if (!file) return
    if (getApp().globalData.demoMode) return wx.showToast({ title: '演示附件不可打开', icon: 'none' })
    return this.fetchAttachment(file, 'preview')
  },
  downloadAttachment(event) {
    const file = this.findAttachment(event)
    if (!file) return
    if (getApp().globalData.demoMode) return wx.showToast({ title: '演示附件不可下载', icon: 'none' })
    return this.fetchAttachment(file, 'download')
  },
  findAttachment(event) {
    const groupIndex = event.currentTarget.dataset.groupIndex
    const fileIndex = event.currentTarget.dataset.fileIndex
    if (groupIndex !== undefined && fileIndex !== undefined) {
      const group = this.data.fileGroups[Number(groupIndex)]
      return group && group.files ? group.files[Number(fileIndex)] : null
    }
    return this.data.files[event.currentTarget.dataset.index]
  },
  fetchAttachment(file, mode) {
    if (this.data.previewing) return
    const url = getDownloadUrl(file)
    if (!url) return wx.showToast({ title: '附件地址缺失', icon: 'none' })
    this.setDetailData({ previewing: true })
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
        complete: () => { wx.hideLoading(); this.setDetailData({ previewing: false }); resolve() }
      })
    })
  },
  openDownloadedFile(file, tempFilePath) {
    if (file.type === 'image') {
      wx.previewImage({ urls: [tempFilePath], fail: (error) => showError(error, '图片预览失败') })
      return
    }
    if (file.type === 'video') {
      this.setData({ previewVideoPath: tempFilePath })
      return
    }
    wx.openDocument({
      filePath: tempFilePath,
      showMenu: true,
      fail: (error) => showError(error, '无法直接预览，可下载后查看')
    })
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
  removeRecord() {
    if (!this.data.record || !this.data.record.id || !this.data.canDeleteRecord) return wx.showToast({ title: '没有四项激励删除权限', icon: 'none' })
    wx.showModal({
      title: '删除四项激励记录',
      content: `确认删除“${this.data.record.customerName || this.data.record.name || '该记录'}”？`,
      success: (result) => {
        if (!result.confirm) return
        deleteRecord(this.data.record.id).then(() => {
          wx.showToast({ title: '已删除', icon: 'success' })
          setTimeout(() => wx.navigateBack(), 300)
        }).catch((error) => showError(error, '删除四项激励记录失败'))
      }
    })
  },
  goBack() { wx.navigateBack() }
})
