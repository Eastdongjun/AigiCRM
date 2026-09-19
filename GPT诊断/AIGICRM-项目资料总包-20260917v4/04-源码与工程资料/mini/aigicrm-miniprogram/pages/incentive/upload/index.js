const { getCustomerList } = require('../../../api/customer')
const { getCategoryTree, addRecord, getRecords, updateRecord } = require('../../../api/incentive')
const { getDownloadUrl, uploadFile } = require('../../../api/file')
const { getDictData } = require('../../../api/system')
const { requireLogin, hasPermission, getToken } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')

function flattenCategories(nodes = [], result = []) {
  nodes.forEach((item) => {
    result.push({
      id: item.id,
      parentId: item.parentId,
      categoryCode: item.categoryCode || '',
      name: item.categoryName || item.name || '未命名分类',
      score: item.score || 0
    })
    if (item.children && item.children.length) flattenCategories(item.children, result)
  })
  return result
}

function defaultSlots() {
  return [
    { key: 'fileId', baseLabel: '主要佐证', label: '主要佐证', hint: '交流、方案、参观等非安装类资料', filePath: '', fileName: '', fileKind: '', files: [], hidden: false },
    { key: 'filePreId', label: '安装前', hint: '试点安装前现场、问题或方案', filePath: '', fileName: '', fileKind: '', files: [] },
    { key: 'fileMidId', label: '安装中', hint: '安装过程、调试过程、现场记录', filePath: '', fileName: '', fileKind: '', files: [] },
    { key: 'filePostId', label: '安装后', hint: '运行结果、验收报告、客户反馈', filePath: '', fileName: '', fileKind: '', files: [] },
    { key: 'fileTrackId', label: '跟踪记录', hint: '回访、复盘、持续跟踪资料', filePath: '', fileName: '', fileKind: '', files: [] }
  ]
}

const MAX_FILES_PER_SLOT = 5

function defaultPeriod() {
  return `${new Date().getFullYear()}全年`
}

function buildPeriodOptions() {
  const currentYear = new Date().getFullYear()
  const options = []
  for (let year = currentYear + 4; year >= currentYear - 1; year -= 1) {
    options.push(`${year}全年`, `${year}下半年`, `${year}上半年`)
  }
  return options
}

function parseIds(value) {
  if (value === null || value === undefined || value === '') return []
  if (Array.isArray(value)) return value.map(Number).filter(Boolean)
  const text = String(value).trim()
  if (!text) return []
  try {
    const parsed = JSON.parse(text)
    if (Array.isArray(parsed)) return parsed.map(Number).filter(Boolean)
    const single = Number(parsed)
    return single ? [single] : []
  } catch (_) {
    return text.split(',').map((item) => Number(String(item).trim())).filter(Boolean)
  }
}

function splitNames(value) {
  if (!value) return []
  return String(value).split(',').map((item) => item.trim()).filter(Boolean)
}

function isDownloadReference(value) {
  const text = String(value || '').trim()
  if (/^https?:\/\//i.test(text)) return true
  if (text.indexOf('/api/file/download/') >= 0) return true
  return /(^|\/)[^/]+\.(jpg|jpeg|png|gif|webp|bmp|heic|heif|mp4|mov|m4v|webm|avi|mkv|pdf|doc|docx|xls|xlsx|ppt|pptx|csv|txt)(\?|#|$)/i.test(text)
}

function existingFiles(ids, names, urls) {
  const nameList = splitNames(names)
  const urlList = splitNames(urls)
  const idFiles = parseIds(ids).map((id, index) => {
    const fileName = nameList[index] || nameList[0] || `附件-${id}`
    const fileUrl = urlList[index] || urlList[0] || ''
    return {
      fileId: id,
      fileUrl,
      filePath: '',
      fileName,
      fileKind: isImageFile(fileName, fileUrl) ? 'image' : 'file',
      existing: true
    }
  })
  if (idFiles.length) return idFiles
  return urlList.filter(isDownloadReference).map((fileUrl, index) => {
    const fileName = nameList[index] || nameList[0] || fileUrl.split('/').filter(Boolean).pop() || `附件-${index + 1}`
    return {
      fileId: '',
      fileUrl,
      filePath: '',
      fileName,
      fileKind: isImageFile(fileName, fileUrl) ? 'image' : 'file',
      existing: true
    }
  })
}

function categoryCodeOf(category, categories) {
  if (!category) return ''
  const map = {}
  categories.forEach((item) => { map[item.id] = item })
  let current = category
  while (current.parentId && map[current.parentId]) current = map[current.parentId]
  return current.categoryCode || category.categoryCode || ''
}

function singleSlotLabel(code) {
  return code === 'plan' ? '方案文件' : '现场照片'
}

function applyCategorySlotMode(slots, category, categories) {
  const code = categoryCodeOf(category, categories)
  const isSingleSlot = ['visit', 'exchange', 'plan'].includes(code)
  return slots.map((slot) => {
    if (slot.key === 'fileId') {
      return {
        ...slot,
        label: slot.baseLabel || '主要佐证',
        hint: '交流、方案、参观等非安装类资料',
        hidden: isSingleSlot
      }
    }
    if (slot.key === 'filePreId') {
      return {
        ...slot,
        label: isSingleSlot ? singleSlotLabel(code) : '安装前',
        hint: isSingleSlot ? '对齐 PC 单槽上传：可上传 1 份图片或文件' : '试点安装前现场、问题或方案',
        hidden: false
      }
    }
    return { ...slot, hidden: isSingleSlot }
  })
}

function isImageFile(name, path) {
  return /\.(jpg|jpeg|png|gif|webp|bmp)$/i.test(name || path || '')
}

function recordAttachmentAction(action, payload) {
  if (typeof globalThis === 'undefined' || !globalThis.__attachmentActionCalls) return
  const calls = globalThis.__attachmentActionCalls
  if (!calls[action]) calls[action] = []
  calls[action].push(payload || {})
}

function currentUserId() {
  const user = getApp().globalData.currentUser || {}
  return user.id || user.userId || ''
}

function selectedText(options, index, placeholder) {
  return index >= 0 && options[index] ? options[index] : placeholder
}

function pickerTextView(options, index, placeholder) {
  const selected = index >= 0 && Boolean(options[index])
  return {
    text: selected ? options[index] : placeholder,
    className: selected ? '' : 'placeholder'
  }
}

function textOrDash(value) {
  return value === undefined || value === null || value === '' ? '-' : String(value)
}

function incentiveDetailLabel(category, categories) {
  const code = categoryCodeOf(category, categories)
  const name = category && (category.name || category.categoryName) ? (category.name || category.categoryName) : ''
  if (code === 'plan') return `${name}文件详情描述`
  if (code === 'visit' || code === 'exchange') return `${name}详情描述`
  return `${name || '产品试点'}安装详情`
}

function incentiveRemarkLabel(category, categories) {
  const name = category && (category.name || category.categoryName) ? (category.name || category.categoryName) : ''
  return `${name || '产品试点'}安装备注`
}

function incentiveSectionTitle(category, categories) {
  const code = categoryCodeOf(category, categories)
  if (code === 'plan') return '方案详情'
  if (code === 'visit' || code === 'exchange') return '参观详情'
  return '安装详情'
}

function categoryScoreText(category) {
  if (!category) return ''
  return `+${Number(category.score || 0)} 分`
}

function isTrialCategory(category, categories) {
  return categoryCodeOf(category, categories).startsWith('trial')
}

function slotFileCount(slot) {
  if (!slot || slot.hidden) return 0
  if (slot.files && slot.files.length) return slot.files.length
  return slot.filePath ? 1 : 0
}

function slotCountText(slot) {
  const count = slotFileCount(slot)
  return count > 0 ? `${count}/${MAX_FILES_PER_SLOT}` : ''
}

function canSubmitUpload(data, selectedCategory, categories) {
  const hasCustomer = data.selectedCustomerIndex >= 0 && Boolean(data.customers[data.selectedCustomerIndex])
  const hasCategory = data.selectedCategoryIndex >= 0 && Boolean(selectedCategory)
  if (!hasCustomer || !hasCategory) return false
  const code = categoryCodeOf(selectedCategory, categories)
  const isSingleSlot = ['visit', 'exchange', 'plan'].includes(code)
  if (!isSingleSlot) return true
  const preSlot = data.fileSlots.find((slot) => slot.key === 'filePreId')
  return slotFileCount(preSlot) > 0
}

function uploadProgressText(data) {
  if (!data.loading || !data.uploadProgressFile) return ''
  return `${data.uploadProgressFile} - ${Number(data.uploadProgress || 0)}%`
}

function selectedCustomer(data) {
  return data.selectedCustomerIndex >= 0 ? data.customers[data.selectedCustomerIndex] : null
}

function customerConfirmMeta(customer) {
  if (!customer) return ''
  const industry = customer.industry || customer.industryName || ''
  return industry ? `行业：${industry}` : '已关联客户'
}

function buildUploadViewState(data) {
  const selectedSlotIndex = Number(data.selectedSlotIndex) || 0
  const categories = data.categories || []
  const selectedCategory = categories[data.selectedCategoryIndex]
  const customer = selectedCustomer(data)
  const currentSlot = data.fileSlots[selectedSlotIndex] || data.fileSlots.find((slot) => !slot.hidden) || {}
  const hasCurrentFile = Boolean(currentSlot.fileName)
  const slotOptionsView = data.fileSlots.map((slot, index) => ({
    ...slot,
    originalIndex: index,
    visible: !slot.hidden,
    className: `slot-option${selectedSlotIndex === index ? ' active' : ''}`,
    ready: Boolean(slot.fileName),
    countText: slotCountText(slot)
  }))
  const selectedFileSlotsView = data.fileSlots
    .map((slot, slotIndex) => {
      const files = (slot.files || []).map((file, fileIndex) => ({
        ...file,
        fileIndex,
        displayIndex: fileIndex + 1,
        isImage: file.fileKind === 'image' || isImageFile(file.fileName, file.filePath)
      }))
      return {
        ...slot,
        slotIndex,
        files,
        visible: !slot.hidden && Boolean(slot.fileName)
      }
    })
    .filter((slot) => slot.visible)
  return {
    customerPicker: pickerTextView(data.customerNames, data.selectedCustomerIndex, '请选择客户'),
    showCustomerConfirm: Boolean(customer),
    selectedCustomerName: customer ? (customer.customerName || customer.name || '未命名客户') : '',
    selectedCustomerMeta: customerConfirmMeta(customer),
    categoryPicker: pickerTextView(data.categoryNames, data.selectedCategoryIndex, '请选择分类'),
    industryPicker: pickerTextView(data.industryOptions, data.selectedIndustryIndex, '请选择行业'),
    relatedCatPicker: pickerTextView(data.relatedCatOptions, data.selectedRelatedCatIndex, '产品大类'),
    relatedSubPicker: pickerTextView(data.relatedSubOptions, data.selectedRelatedSubIndex, '细分产品'),
    relatedSubDisabled: data.selectedRelatedCatIndex < 0,
    relatedSubPickerClass: `picker-card compact${data.selectedRelatedCatIndex < 0 ? ' disabled' : ''}`,
    showCategorySummary: Boolean(selectedCategory),
    categoryScoreText: categoryScoreText(selectedCategory),
    categorySectionTitle: incentiveSectionTitle(selectedCategory, categories),
    showSpecificProduct: isTrialCategory(selectedCategory, categories),
    showRemarkField: isTrialCategory(selectedCategory, categories),
    detailLabel: incentiveDetailLabel(selectedCategory, categories),
    detailPlaceholder: '记录客户现场问题、安装前情况、过程效果或试点背景',
    remarkLabel: incentiveRemarkLabel(selectedCategory, categories),
    remarkPlaceholder: '补充安装过程、效果反馈、跟踪说明或其他备注',
    periodPickerText: selectedText(data.periodOptions, data.selectedPeriodIndex, '请选择周期'),
    periodPickerClass: `picker-card${data.editId ? ' disabled' : ''}`,
    periodDisabled: Boolean(data.editId),
    showPeriodArrow: !data.editId,
    showPeriodPreserveHint: Boolean(data.editId),
    shareLocked: Boolean(data.editId && !data.canToggleShare),
    submitLoading: Boolean(data.loading),
    submitDisabled: data.loading || !canSubmitUpload(data, selectedCategory, categories),
    submitText: data.loading ? '提交中' : '提交激励',
    cancelText: data.editId ? '取消追加' : '取消上传',
    cancelDisabled: Boolean(data.loading),
    showUploadProgress: Boolean(data.loading && data.uploadProgressFile),
    uploadProgressText: uploadProgressText(data),
    uploadProgressWidth: `${Number(data.uploadProgress || 0)}%`,
    fileActionLoading: Boolean(data.fileActionLoading),
    currentSlotTitle: hasCurrentFile ? currentSlot.fileName : `选择${currentSlot.label || '槽位'}文件`,
    currentSlotHint: hasCurrentFile ? '已选择文件，可预览图片/打开文档，也可下载保存；每个槽位最多 5 个文件' : (currentSlot.hint || ''),
    slotOptionsView,
    selectedFileSlotsView,
    showAudit: Boolean(data.editId && data.auditRecord),
    auditRows: data.auditRecord ? [
      { label: '上传人', valueText: textOrDash(data.auditRecord.userName || data.auditRecord.createByName || data.auditRecord.createdByName) },
      { label: '上传日期', valueText: textOrDash(data.auditRecord.createdAt || data.auditRecord.createTime) },
      { label: '最后修改人', valueText: textOrDash(data.auditRecord.updatedByName || data.auditRecord.updateByName) },
      { label: '最后修改时间', valueText: textOrDash(data.auditRecord.updatedAt || data.auditRecord.updateTime) }
    ] : []
  }
}

Page({
  data: {
    customers: [],
    customerNames: [],
    categories: [],
    categoryNames: [],
    industryOptions: [],
    selectedIndustryIndex: -1,
    periodOptions: buildPeriodOptions(),
    selectedPeriodIndex: Math.max(buildPeriodOptions().indexOf(defaultPeriod()), 0),
    selectedCustomerIndex: -1,
    selectedCategoryIndex: -1,
    relatedCatOptions: ['涂层类', '防滑板类', '机封类', '密封类', '润滑类'],
    relatedSubOptions: [],
    productCats: {
      '涂层类': ['防腐', '加固', '钢结构', '煤气管道', '其他'],
      '防滑板类': ['公共区', '装置区'],
      '机封类': ['水泵', '渣泵', '介质泵'],
      '密封类': ['静密封', '动密封', '橡胶密封'],
      '润滑类': ['润滑油', '润滑脂', '润滑剂']
    },
    selectedRelatedCatIndex: -1,
    selectedRelatedSubIndex: -1,
    relatedProducts: '',
    specificProduct: '',
    preInstallQuestion: '',
    remark: '',
    shared: true,
    canToggleShare: true,
    editId: '',
    auditRecord: null,
    selectedSlotIndex: 0,
    fileSlots: defaultSlots(),
    uploadView: buildUploadViewState({
      customerNames: [],
      categoryNames: [],
      industryOptions: [],
      selectedCustomerIndex: -1,
      selectedCategoryIndex: -1,
      selectedIndustryIndex: -1,
      relatedCatOptions: ['涂层类', '防滑板类', '机封类', '密封类', '润滑类'],
      relatedSubOptions: [],
      selectedRelatedCatIndex: -1,
      selectedRelatedSubIndex: -1,
      periodOptions: buildPeriodOptions(),
      selectedPeriodIndex: Math.max(buildPeriodOptions().indexOf(defaultPeriod()), 0),
      editId: '',
      canToggleShare: true,
      auditRecord: null,
      selectedSlotIndex: 0,
      fileSlots: defaultSlots(),
      fileActionLoading: false
    }),
    loading: false,
    fileActionLoading: false,
    uploadProgress: 0,
    uploadProgressFile: ''
  },

  refreshUploadViewState() {
    this.setData({ uploadView: buildUploadViewState(this.data) })
  },

  setUploadData(patch) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, uploadView: buildUploadViewState(next) })
  },

  onLoad(options = {}) {
    if (!requireLogin()) return
    const editId = options.id || ''
    if (!getApp().globalData.demoMode && !editId && !hasPermission('incentive:add')) {
      wx.showToast({ title: '没有四项激励上传权限', icon: 'none' })
      wx.navigateBack()
      return
    }
    this.setData({ editId }, () => this.refreshUploadViewState())
    this.loadIndustries()
    this.loadOptions()
  },

  loadIndustries() {
    if (getApp().globalData.demoMode) {
      this.setData({ industryOptions: ['制造业', '煤矿', '化工', '电力', '其他'] }, () => this.refreshUploadViewState())
      return
    }
    getDictData('industry').then((list) => {
      this.setData({ industryOptions: (list || []).map((item) => item.dictValue || item.dictLabel).filter(Boolean) }, () => this.refreshUploadViewState())
    }).catch(() => this.setData({ industryOptions: ['煤矿', '化工', '电力', '洗煤厂', '有色', '其他'] }, () => this.refreshUploadViewState()))
  },

  loadOptions() {
    if (getApp().globalData.demoMode) {
      const customers = [{ id: 1, customerName: '华东智能制造集团', industry: '制造业' }]
      const categories = [{ id: 1, parentId: 0, categoryCode: 'trial', name: '产品试点', score: 3 }, { id: 2, parentId: 0, categoryCode: 'exchange', name: '交流拜访', score: 1 }]
      this.setData({ customers, customerNames: customers.map((item) => item.customerName), categories, categoryNames: categories.map((item) => item.name) }, () => this.refreshUploadViewState())
      if (this.data.editId) this.loadEditRecord()
      return
    }
    Promise.all([getCustomerList({ current: 1, size: 100 }), getCategoryTree()]).then(([customerRes, categoryRes]) => {
      const page = customerRes && (customerRes.records || customerRes.data || customerRes)
      const customers = Array.isArray(page) ? page : (page.records || [])
      const categories = flattenCategories(categoryRes || [])
      this.setData({ customers, customerNames: customers.map((item) => item.customerName || item.name || '未命名客户'), categories, categoryNames: categories.map((item) => item.name) }, () => this.refreshUploadViewState())
      if (this.data.editId) this.loadEditRecord()
    }).catch((error) => showError(error, '上传选项加载失败'))
  },

  loadEditRecord() {
    if (!this.data.editId) return
    if (getApp().globalData.demoMode) return wx.showToast({ title: '演示记录不可追加', icon: 'none' })
    getRecords({}).then((rows) => {
      const records = Array.isArray(rows) ? rows : (rows && (rows.records || rows.data || rows.list)) || []
      const record = records.find((item) => String(item.id) === String(this.data.editId))
      if (!record) throw new Error('找不到四项激励记录')
      this.fillEditRecord(record)
    }).catch((error) => showError(error, '四项激励记录加载失败'))
  },

  fillEditRecord(record) {
    const customerIndex = this.data.customers.findIndex((item) => String(item.id) === String(record.customerId))
    const categoryIndex = this.data.categories.findIndex((item) => String(item.id) === String(record.categoryId))
    const industryIndex = record.industry ? this.data.industryOptions.indexOf(record.industry) : -1
    const ownerId = record.userId || record.salespersonId || record.createBy || record.createdBy
    const userId = currentUserId()
    const periodOptions = this.data.periodOptions.includes(record.period) ? this.data.periodOptions : [record.period, ...this.data.periodOptions].filter(Boolean)
    const selectedPeriodIndex = periodOptions.indexOf(record.period || defaultPeriod())
    const slotFiles = {
      fileId: existingFiles(record.fileId || record.fileIds, record.fileName, record.fileUrl || record.filePath || record.ossKey || record.fileKey),
      filePreId: existingFiles(record.filePreId || record.filePreIds, record.filePreName, record.filePreUrl || record.filePrePath || record.filePreOssKey),
      fileMidId: existingFiles(record.fileMidId || record.fileMidIds, record.fileMidName, record.fileMidUrl || record.fileMidPath || record.fileMidOssKey),
      filePostId: existingFiles(record.filePostId || record.filePostIds, record.filePostName, record.filePostUrl || record.filePostPath || record.filePostOssKey),
      fileTrackId: existingFiles(record.fileTrackId || record.fileTrackIds, record.fileTrackName, record.fileTrackUrl || record.fileTrackPath || record.fileTrackOssKey)
    }
    const fileSlots = applyCategorySlotMode(defaultSlots(), this.data.categories[categoryIndex], this.data.categories).map((slot) => {
      const files = slotFiles[slot.key] || []
      const firstFile = files[0] || {}
      return {
        ...slot,
        files,
        filePath: firstFile.filePath || '',
        fileName: firstFile.fileName || '',
        fileKind: firstFile.fileKind || ''
      }
    })
    this.setData({
      selectedCustomerIndex: customerIndex,
      selectedCategoryIndex: categoryIndex,
      selectedIndustryIndex: industryIndex,
      periodOptions,
      selectedPeriodIndex: Math.max(selectedPeriodIndex, 0),
      relatedProducts: record.relatedProducts || '',
      specificProduct: record.specificProduct || '',
      preInstallQuestion: record.preInstallQuestion || '',
      remark: record.remark || '',
      shared: record.shared !== 0,
      canToggleShare: Boolean(userId && ownerId && String(userId) === String(ownerId)),
      auditRecord: record,
      fileSlots,
      selectedSlotIndex: Math.max(fileSlots.findIndex((slot) => !slot.hidden), 0)
    }, () => this.refreshUploadViewState())
  },

  chooseCustomer(e) {
    const index = Number(e.detail.value)
    const customer = this.data.customers[index]
    const industryIndex = customer && customer.industry ? this.data.industryOptions.indexOf(customer.industry) : -1
    this.setData({ selectedCustomerIndex: index, selectedIndustryIndex: industryIndex }, () => this.refreshUploadViewState())
  },
  chooseCategory(e) {
    const selectedCategoryIndex = Number(e.detail.value)
    const category = this.data.categories[selectedCategoryIndex]
    const fileSlots = applyCategorySlotMode(this.data.fileSlots, category, this.data.categories)
    const selectedSlot = fileSlots[this.data.selectedSlotIndex]
    const firstVisibleSlotIndex = fileSlots.findIndex((slot) => !slot.hidden)
    this.setData({
      selectedCategoryIndex,
      fileSlots,
      selectedSlotIndex: selectedSlot && !selectedSlot.hidden ? this.data.selectedSlotIndex : Math.max(firstVisibleSlotIndex, 0)
    }, () => this.refreshUploadViewState())
  },
  chooseIndustry(e) { this.setData({ selectedIndustryIndex: Number(e.detail.value) }, () => this.refreshUploadViewState()) },
  choosePeriod(e) { this.setData({ selectedPeriodIndex: Number(e.detail.value) }, () => this.refreshUploadViewState()) },
  chooseRelatedCat(e) {
    const index = Number(e.detail.value)
    const name = this.data.relatedCatOptions[index]
    const subOptions = this.data.productCats[name] || []
    this.setData({
      selectedRelatedCatIndex: index,
      selectedRelatedSubIndex: -1,
      relatedSubOptions: subOptions,
      relatedProducts: name || ''
    }, () => this.refreshUploadViewState())
  },
  chooseRelatedSub(e) {
    if (this.data.selectedRelatedCatIndex < 0) {
      wx.showToast({ title: '请先选择产品大类', icon: 'none' })
      return
    }
    const index = Number(e.detail.value)
    const cat = this.data.relatedCatOptions[this.data.selectedRelatedCatIndex] || ''
    const sub = this.data.relatedSubOptions[index] || ''
    this.setData({ selectedRelatedSubIndex: index, relatedProducts: cat && sub ? `${cat}-${sub}` : cat || sub }, () => this.refreshUploadViewState())
  },
  chooseSlot(e) { this.setData({ selectedSlotIndex: Number(e.currentTarget.dataset.index) }, () => this.refreshUploadViewState()) },
  inputRelatedProducts(e) { this.setData({ relatedProducts: e.detail.value }) },
  inputSpecificProduct(e) { this.setData({ specificProduct: e.detail.value }) },
  inputPreInstallQuestion(e) { this.setData({ preInstallQuestion: e.detail.value }) },
  inputRemark(e) { this.setData({ remark: e.detail.value }) },
  toggleShared(e) {
    if (this.data.editId && !this.data.canToggleShare) {
      wx.showToast({ title: '仅上传人可修改共享状态', icon: 'none' })
      return
    }
    this.setData({ shared: e.detail.value }, () => this.refreshUploadViewState())
  },

  setSelectedSlotFile(filePath, fileName, fileKind) {
    const index = this.data.selectedSlotIndex
    const slot = this.data.fileSlots[index] || {}
    const files = (slot.files || []).concat({
      filePath,
      fileName,
      fileKind: fileKind || (isImageFile(fileName, filePath) ? 'image' : 'file')
    }).slice(0, MAX_FILES_PER_SLOT)
    const firstFile = files[0] || {}
    this.setData({
      [`fileSlots[${index}].files`]: files,
      [`fileSlots[${index}].filePath`]: firstFile.filePath || '',
      [`fileSlots[${index}].fileName`]: firstFile.fileName || '',
      [`fileSlots[${index}].fileKind`]: firstFile.fileKind || ''
    }, () => this.refreshUploadViewState())
  },

  chooseImage() {
    wx.chooseMedia({
      count: MAX_FILES_PER_SLOT,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const files = res.tempFiles || []
        files.forEach((file, index) => {
          if (!file || !file.tempFilePath) return
          const suffix = (file.tempFilePath.match(/\.[a-z0-9]+$/i) || ['.jpg'])[0]
          this.setSelectedSlotFile(file.tempFilePath, `现场图片${index + 1}${suffix}`, 'image')
        })
      },
      fail: (error) => { if (error && error.errMsg && !error.errMsg.includes('cancel')) showError(error, '选择图片失败') }
    })
  },

  chooseFile() {
    wx.chooseMessageFile({ count: MAX_FILES_PER_SLOT, type: 'file', success: (res) => {
      const files = res.tempFiles || []
      files.forEach((file) => {
        if (file && file.path) {
          this.setSelectedSlotFile(file.path, file.name || '已选择文件')
        }
      })
    }, fail: (error) => { if (error && error.errMsg && !error.errMsg.includes('cancel')) showError(error, '选择文件失败') } })
  },

  selectedSlot(event) {
    const slotIndex = Number(event.currentTarget.dataset.index)
    const fileIndex = Number(event.currentTarget.dataset.fileIndex || 0)
    const slot = this.data.fileSlots[slotIndex]
    if (!slot) return null
    const file = (slot.files || [])[fileIndex]
    return file || slot
  },

  previewSelectedFile(event) {
    const file = this.selectedSlot(event)
    if (!file) return
    if (file.existing && (file.fileId || file.fileUrl)) return this.fetchExistingFile(file, 'preview')
    if (!file.filePath) return
    if (file.fileKind === 'image' || isImageFile(file.fileName, file.filePath)) {
      recordAttachmentAction('previewImage', { urls: [file.filePath] })
      wx.previewImage({ urls: [file.filePath], fail: (error) => showError(error, '图片预览失败') })
      return
    }
    recordAttachmentAction('openDocument', { filePath: file.filePath, showMenu: true })
    wx.openDocument({
      filePath: file.filePath,
      showMenu: true,
      fail: (error) => showError(error, '无法直接预览，可保存后查看')
    })
  },

  downloadSelectedFile(event) {
    const file = this.selectedSlot(event)
    if (!file) return
    if (file.existing && (file.fileId || file.fileUrl)) return this.fetchExistingFile(file, 'download')
    if (!file.filePath) return
    recordAttachmentAction('saveFile', { tempFilePath: file.filePath })
    const options = {
      tempFilePath: file.filePath,
      success: () => wx.showToast({ title: '已保存到本机', icon: 'success' }),
      fail: (error) => showError(error, '下载保存失败')
    }
    const fs = typeof wx.getFileSystemManager === 'function' ? wx.getFileSystemManager() : null
    if (fs && typeof fs.saveFile === 'function') return fs.saveFile(options)
    return wx.saveFile(options)
  },

  fetchExistingFile(file, mode) {
    if (this.data.fileActionLoading) return
    const downloadUrl = getDownloadUrl({ id: file.fileId, fileUrl: file.fileUrl, url: file.fileUrl })
    if (!downloadUrl) return wx.showToast({ title: '附件地址缺失', icon: 'none' })
    this.setUploadData({ fileActionLoading: true })
    wx.showLoading({ title: mode === 'download' ? '下载中' : '准备预览' })
    return new Promise((resolve) => {
      wx.downloadFile({
        url: downloadUrl,
        header: getToken() ? { Authorization: 'Bearer ' + getToken() } : {},
        success: (result) => {
          if (result.statusCode !== 200) return showError(new Error('文件下载失败'), '文件下载失败')
          if (mode === 'download') return this.saveTempFile(result.tempFilePath)
          if (file.fileKind === 'image' || isImageFile(file.fileName, '')) {
            wx.previewImage({ urls: [result.tempFilePath], fail: (error) => showError(error, '图片预览失败') })
            return
          }
          wx.openDocument({ filePath: result.tempFilePath, showMenu: true, fail: (error) => showError(error, '无法直接预览，可下载后查看') })
        },
        fail: (error) => showError(error, '文件下载失败'),
        complete: () => { wx.hideLoading(); this.setUploadData({ fileActionLoading: false }); resolve() }
      })
    })
  },

  saveTempFile(tempFilePath) {
    const options = {
      tempFilePath,
      success: () => wx.showToast({ title: '已保存到本机', icon: 'success' }),
      fail: (error) => showError(error, '下载保存失败')
    }
    const fs = typeof wx.getFileSystemManager === 'function' ? wx.getFileSystemManager() : null
    if (fs && typeof fs.saveFile === 'function') return fs.saveFile(options)
    return wx.saveFile(options)
  },

  removeSelectedFile(event) {
    const index = Number(event.currentTarget.dataset.index)
    const fileIndex = Number(event.currentTarget.dataset.fileIndex || 0)
    const slot = this.data.fileSlots[index] || {}
    const files = (slot.files || []).filter((_, idx) => idx !== fileIndex)
    const firstFile = files[0] || {}
    this.setData({
      [`fileSlots[${index}].files`]: files,
      [`fileSlots[${index}].filePath`]: firstFile.filePath || '',
      [`fileSlots[${index}].fileName`]: firstFile.fileName || '',
      [`fileSlots[${index}].fileKind`]: firstFile.fileKind || ''
    }, () => this.refreshUploadViewState())
  },

  cancelUpload() {
    if (this.data.loading) return
    const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
    if (pages.length > 1) {
      wx.navigateBack()
      return
    }
    wx.navigateTo({ url: '/pages/incentive/index' })
  },

  submit() {
    const customer = this.data.customers[this.data.selectedCustomerIndex]
    const category = this.data.categories[this.data.selectedCategoryIndex]
    if (this.data.uploadView.submitDisabled) return
    const selectedFiles = this.data.fileSlots.flatMap((slot) => {
      if (slot.hidden) return []
      const files = slot.files && slot.files.length ? slot.files : (slot.filePath ? [slot] : [])
      return files.map((file) => ({ ...file, key: slot.key }))
    })
    if (!customer || !category) {
      wx.showToast({ title: '请补齐客户和分类', icon: 'none' })
      return
    }
    const code = categoryCodeOf(category, this.data.categories)
    const isSingleSlot = ['visit', 'exchange', 'plan'].includes(code)
    if (isSingleSlot && selectedFiles.length === 0) {
      wx.showToast({ title: '请至少上传 1 个文件', icon: 'none' })
      return
    }
    if (this.data.loading) return
    this.setUploadData({ loading: true, uploadProgress: 0, uploadProgressFile: '' })
    wx.showLoading({ title: '上传中' })
    const existingFilesBySlot = selectedFiles.filter((slot) => slot.existing && slot.fileId).map((slot) => ({ key: slot.key, id: slot.fileId }))
    const newFiles = selectedFiles.filter((slot) => !slot.existing)
    let uploadedIndex = 0
    const uploadOne = (slot, index, total) => {
      const fileName = slot.fileName || `附件-${index + 1}`
      this.setUploadData({ uploadProgressFile: fileName })
      return uploadFile(slot.filePath, null, 'incentive', {
        onProgress: (event) => {
          const fileProgress = Number(event.progress || 0)
          const progress = Math.min(99, Math.round(((index + fileProgress / 100) / total) * 100))
          this.setUploadData({ uploadProgress: progress, uploadProgressFile: fileName })
        }
      }).then((uploaded) => {
      const id = uploaded && uploaded.id
      if (!id) throw new Error('上传结果缺少文件 ID')
        uploadedIndex += 1
        this.setUploadData({ uploadProgress: Math.round((uploadedIndex / total) * 100), uploadProgressFile: fileName })
        return { key: slot.key, id }
      })
    }
    Promise.all(newFiles.map((slot, index) => uploadOne(slot, index, newFiles.length || 1))).then((uploadedFiles) => {
      const selectedIndustry = this.data.industryOptions[this.data.selectedIndustryIndex] || customer.industry || ''
      const selectedPeriod = this.data.periodOptions[this.data.selectedPeriodIndex] || defaultPeriod()
      const payload = {
        customerId: customer.id,
        categoryId: category.id,
        period: selectedPeriod,
        industry: selectedIndustry || null,
        relatedProducts: this.data.relatedProducts.trim() || null,
        specificProduct: this.data.specificProduct.trim() || null,
        preInstallQuestion: this.data.preInstallQuestion.trim() || null,
        remark: this.data.remark.trim() || null,
        shared: this.data.shared ? 1 : 0
      }
      const idsBySlot = existingFilesBySlot.concat(uploadedFiles).reduce((acc, item) => {
        if (!acc[item.key]) acc[item.key] = []
        acc[item.key].push(item.id)
        return acc
      }, {})
      Object.keys(idsBySlot).forEach((key) => { payload[key] = JSON.stringify(idsBySlot[key]) })
      if (isSingleSlot) {
        payload.fileMidId = null
        payload.filePostId = null
        payload.fileTrackId = null
      }
      if (this.data.editId) {
        delete payload.categoryId
        delete payload.period
        if (!this.data.canToggleShare) delete payload.shared
        return updateRecord(this.data.editId, payload)
      }
      return addRecord(payload)
    }).then(() => {
      wx.showToast({ title: this.data.editId ? '追加成功' : '上传成功', icon: 'success' })
      wx.navigateTo({ url: '/pages/incentive/index' })
    }).catch((error) => showError(error, '四项激励上传失败')).finally(() => {
      this.setUploadData({ loading: false, uploadProgress: 0, uploadProgressFile: '' })
      wx.hideLoading()
    })
  }
})
