const { getCustomerList } = require('../../../api/customer')
const { getCategoryTree, addRecord, getRecords, updateRecord } = require('../../../api/incentive')
const { getDownloadUrl, uploadFile } = require('../../../api/file')
const { getDictData } = require('../../../api/system')
// 【E10】表单草稿接口自 api/system 迁至 api/draft(D38 域归位收尾), 引用随迁
const { getFormDraft, saveFormDraft, deleteFormDraft } = require('../../../api/draft')
const { requireLogin, hasPermission } = require('../../../utils/auth')
const { fetchRemoteAttachment, saveDownloadedFileToDisk } = require('../../../utils/attachment')
const { showError, showErrorWithRetry } = require('../../../utils/feedback')

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
    { key: 'fileId', baseLabel: '主要佐证', label: '主要佐证', hint: '交流、方案、参观等非安装类资料', filePath: '', fileName: '', fileKind: '', files: [], hidden: true },
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
      // 对齐 PC: PC 上传弹窗任何品类都没有通用佐证槽, fileId 仅承载旧数据提交/回填, 界面一律隐藏
      return {
        ...slot,
        label: slot.baseLabel || '主要佐证',
        hint: '交流、方案、参观等非安装类资料',
        hidden: true
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

function incentiveRemarkLabel(category) {
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

const UPLOAD_STATE_LABELS = { pending: '待上传', uploading: '上传中', done: '已上传' }

function uploadStateText(file) {
  if (file.uploadedId) return '已上传'
  const state = file.uploadState || 'pending'
  if (state === 'failed') return `失败：${file.uploadError || '请重试'}`
  return UPLOAD_STATE_LABELS[state] || '待上传'
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

function deriveCategoryParents(categories = []) {
  const parents = []
  categories.forEach((cat, index) => {
    if (!cat.parentId || Number(cat.parentId) === 0) parents.push({ ...cat, index, children: [] })
  })
  categories.forEach((cat, index) => {
    const parent = parents.find((item) => String(item.id) === String(cat.parentId))
    if (parent) parent.children.push({ ...cat, index })
  })
  return parents
}

function activeParentChildren(data) {
  const parents = data.categoryParentNodes || []
  const selectedCategory = (data.categories || [])[data.selectedCategoryIndex]
  let parent = parents[Number(data.selectedParentCategoryIndex)]
  if (!parent && selectedCategory) {
    parent = parents.find((item) => String(item.id) === String(selectedCategory.id)) ||
      parents.find((item) => String(item.id) === String(selectedCategory.parentId))
  }
  return parent ? parent.children : []
}

function parentIndexOfCategory(categories, parentNodes, categoryIndex) {
  const category = categories[categoryIndex]
  if (!category) return -1
  const parent = parentNodes.find((item) => String(item.id) === String(category.id)) ||
    parentNodes.find((item) => String(item.id) === String(category.parentId))
  return parent ? parentNodes.indexOf(parent) : -1
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
        // 【L11】existing/草稿恢复文件 filePath 恒为空串, 直接拿 filePath 当 wx:key 会整组重复:
        // 依次用本地临时路径 → 服务端文件 id → 附件 URL 兜底, 最后按下标保证唯一
        fileKey: file.filePath || (file.fileId ? `id-${file.fileId}` : (file.fileUrl ? `url-${file.fileUrl}` : `idx-${fileIndex}`)),
        isImage: file.fileKind === 'image' || isImageFile(file.fileName, file.filePath),
        // 【PR-13 首批 2026-09-19】逐文件上传反馈:pending/uploading/done/failed(+失败原因)
        uploadState: file.uploadedId ? 'done' : (file.uploadState || 'pending'),
        uploadStateText: uploadStateText(file),
        uploadStateClass: `upload-state ${file.uploadedId ? 'done' : (file.uploadState || 'pending')}`
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
    // 【对齐 PC 2026-09-20】分类两级选择: 大类(产品试点/两谈两做/外出参观/交流拜访) → 子类(涂层类/楼梯板类…);
    // selectedCategoryIndex 仍指向全量 categories, 兼容草稿恢复与编辑回填
    categoryParentNames: (data.categoryParentNodes || []).map((item) => item.name),
    categoryParentPicker: pickerTextView((data.categoryParentNodes || []).map((item) => item.name), data.selectedParentCategoryIndex, '请选择大类'),
    showCategoryChild: Boolean(activeParentChildren(data).length),
    categoryChildNames: activeParentChildren(data).map((item) => item.name),
    categoryChildPicker: pickerTextView(activeParentChildren(data).map((item) => item.name), activeParentChildren(data).findIndex((item) => item.index === data.selectedCategoryIndex), '请选择子类'),
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
    remarkLabel: incentiveRemarkLabel(selectedCategory),
    remarkPlaceholder: '补充安装过程、效果反馈、跟踪说明或其他备注',
    periodPickerText: selectedText(data.periodOptions, data.selectedPeriodIndex, '请选择周期'),
    periodPickerClass: `picker-card${data.editId ? ' disabled' : ''}`,
    periodDisabled: Boolean(data.editId),
    showPeriodArrow: !data.editId,
    showPeriodPreserveHint: Boolean(data.editId),
    shareLocked: Boolean(data.editId && !data.canToggleShare),
    submitLoading: Boolean(data.loading),
    // 【M18 2026-10-04】submitDisabled 只表达"提交中"禁用: 表单未就绪不再进 disabled(微信原生禁用
    // 按钮会吞掉点击, 用户点不出任何反馈), 改为 submitIncomplete 置灰样式 + submit() 点击时 toast 原因
    submitDisabled: Boolean(data.loading),
    submitIncomplete: !canSubmitUpload(data, selectedCategory, categories),
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
    categoryParentNodes: [],
    selectedParentCategoryIndex: -1,
    draftVersion: 0, // 【PR-13】草稿乐观锁版本
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
    // 【PR-04】真实模式接口失败：报错并置空，不回退硬编码演示行业
    getDictData('industry').then((list) => {
      this.setData({ industryOptions: (list || []).map((item) => item.dictValue || item.dictLabel).filter(Boolean) }, () => this.refreshUploadViewState())
    }).catch((error) => {
      showErrorWithRetry(error, '行业字典加载失败，请重试', () => this.loadIndustries())
      this.setData({ industryOptions: [] }, () => this.refreshUploadViewState())
    })
  },

  loadOptions() {
    if (getApp().globalData.demoMode) {
      const customers = [{ id: 1, customerName: '华东智能制造集团', industry: '制造业' }]
      const categories = [{ id: 1, parentId: 0, categoryCode: 'trial', name: '产品试点', score: 3 }, { id: 2, parentId: 0, categoryCode: 'exchange', name: '交流拜访', score: 1 }]
      this.setData({ customers, customerNames: customers.map((item) => item.customerName), categories, categoryParentNodes: deriveCategoryParents(categories), categoryNames: categories.map((item) => item.name) }, () => this.refreshUploadViewState())
      if (this.data.editId) this.loadEditRecord()
      return
    }
    Promise.all([getCustomerList({ current: 1, size: 100 }), getCategoryTree()]).then(([customerRes, categoryRes]) => {
      const page = customerRes && (customerRes.records || customerRes.data || customerRes)
      const customers = Array.isArray(page) ? page : (page.records || [])
      const categories = flattenCategories(categoryRes || [])
      this.setData({ customers, customerNames: customers.map((item) => item.customerName || item.name || '未命名客户'), categories, categoryParentNodes: deriveCategoryParents(categories), categoryNames: categories.map((item) => item.name) }, () => this.refreshUploadViewState())
      if (this.data.editId) this.loadEditRecord()
      else this.tryRestoreDraft() // 【PR-13】新建模式: 选项就绪后检查草稿恢复(客户/分类索引依赖选项)
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
    // 【2026-09-22 编辑入口补齐】客户选项来自 getCustomerList 首页(size:100),客户总数超页时
    // 记录归属客户不在选项内 → 回填 -1 → 提交被"请补齐客户和分类"拦截。编辑态把记录
    // 自带客户补进选项头部,保证任意客户的记录都可编辑(提交时 customerId 等值回传,归属不变)。
    let customers = this.data.customers
    let customerIndex = customers.findIndex((item) => String(item.id) === String(record.customerId))
    if (customerIndex === -1 && record.customerId) {
      customers = [{ id: record.customerId, customerName: record.customerName || record.name || '未命名客户', industry: record.industry || '' }, ...customers]
      customerIndex = 0
    }
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
      customers,
      customerNames: customers.map((item) => item.customerName || item.name || '未命名客户'),
      selectedCustomerIndex: customerIndex,
      selectedCategoryIndex: categoryIndex,
      selectedParentCategoryIndex: parentIndexOfCategory(this.data.categories, this.data.categoryParentNodes, categoryIndex),
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
  applyCategorySelection(selectedCategoryIndex) {
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
  // 【对齐 PC】先选大类; 无子类的大类直接成为最终分类
  chooseCategoryParent(e) {
    const index = Number(e.detail.value)
    const parent = this.data.categoryParentNodes[index]
    if (!parent) return
    if (!parent.children.length) {
      this.setData({ selectedParentCategoryIndex: index }, () => this.applyCategorySelection(parent.index))
      return
    }
    this.setData({ selectedParentCategoryIndex: index, selectedCategoryIndex: -1 }, () => this.refreshUploadViewState())
  },
  // 【对齐 PC】大类之下选子类(子类才是记录挂载的分类, 与 PC「子分类列表→上传文件」同口径)
  chooseCategoryChild(e) {
    const parent = this.data.categoryParentNodes[this.data.selectedParentCategoryIndex]
    const child = parent && parent.children[Number(e.detail.value)]
    if (!child) return
    this.applyCategorySelection(child.index)
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
    return saveDownloadedFileToDisk(file.filePath, '下载保存失败')
  },

  // 【E5 去重】既有附件的下载/预览共用 utils/attachment.js; 本页与列表/详情页的既定差异:
  // busy=fileActionLoading、无视频分支、图片按 fileKind/扩展名判定、hideLoading 在 complete
  // 统一收、保存失败文案"下载保存失败"
  fetchExistingFile(file, mode) {
    return fetchRemoteAttachment({
      url: getDownloadUrl({ id: file.fileId, fileUrl: file.fileUrl, url: file.fileUrl }),
      mode,
      file,
      isBusy: this.data.fileActionLoading,
      setBusy: (busy) => this.setUploadData({ fileActionLoading: busy }),
      isImage: (item) => item.fileKind === 'image' || isImageFile(item.fileName, ''),
      saveFailTitle: '下载保存失败',
      hideLoadingBeforeResult: false
    })
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
    wx.switchTab({ url: '/pages/incentive/index' }) // 无栈兜底同样必须 switchTab
  },

  submit() {
    // 【M18 2026-10-04】按钮 disabled 属性只保留给"提交中"态, 表单未就绪时按钮可点击;
    // 此处按缺失项逐条校验并 toast, 保证用户知道为何不能提交(原先 submitDisabled 早退
    // 会把"请补齐客户和分类"/"请至少上传 1 个文件"两条提示挡成不可达死代码)
    if (this.data.loading) return // 提交中重复点击静默忽略(已有全屏 loading 反馈)
    const customer = this.data.customers[this.data.selectedCustomerIndex]
    const category = this.data.categories[this.data.selectedCategoryIndex]
    if (!customer || !category) {
      wx.showToast({ title: '请补齐客户和分类', icon: 'none' })
      return
    }
    const selectedFiles = this.data.fileSlots.flatMap((slot, slotIndex) => {
      if (slot.hidden) return []
      const files = slot.files && slot.files.length ? slot.files : (slot.filePath ? [slot] : [])
      return files.map((file, fileIndex) => ({ ...file, key: slot.key, slotIndex, fileIndex }))
    })
    const code = categoryCodeOf(category, this.data.categories)
    const isSingleSlot = ['visit', 'exchange', 'plan'].includes(code)
    if (isSingleSlot && selectedFiles.length === 0) {
      wx.showToast({ title: '请至少上传 1 个文件', icon: 'none' })
      return
    }
    this.setUploadData({ loading: true, uploadProgress: 0, uploadProgressFile: '' })
    wx.showLoading({ title: '上传中' })
    this.uploadPendingFiles(selectedFiles, customer, category, isSingleSlot)
  },

  /**
   * 【PR-13 首批 2026-09-19】逐文件上传 + 失败续办:
   * - 逐文件串行上传,每个文件独立 pending/uploading/done/failed 状态(界面逐文件反馈);
   * - 单个失败不阻断其余;成功项记 uploadedId,重试/再次提交只补传失败项,不全量重来;
   * - 全部就绪后才组装 payload 落库(与原提交口径一致,含单槽分类清空逻辑)。
   */
  uploadPendingFiles(selectedFiles, customer, category, isSingleSlot) {
    const pending = selectedFiles.filter((item) => !item.existing && !item.uploadedId)
    const doneIds = selectedFiles
      .filter((item) => item.existing && item.fileId)
      .map((item) => ({ key: item.key, id: item.fileId }))
      .concat(selectedFiles.filter((item) => item.uploadedId).map((item) => ({ key: item.key, id: item.uploadedId })))
    if (!pending.length) {
      return this.saveRecordWithFiles(doneIds, customer, category, isSingleSlot)
        .then(() => this.finishSubmit())
        .catch((error) => {
          showError(error, '四项激励上传失败')
          this.setUploadData({ loading: false })
          wx.hideLoading()
        })
    }
    let uploadedIndex = doneIds.length
    const total = doneIds.length + pending.length
    const uploadOne = (item) => new Promise((resolve) => {
      const fileName = item.fileName || '附件'
      this.setFileUploadState(item, 'uploading')
      this.setUploadData({ uploadProgressFile: fileName, uploadProgress: Math.min(99, Math.round((uploadedIndex / total) * 100)) })
      uploadFile(item.filePath, null, 'incentive', {
        onProgress: (event) => {
          const fileProgress = Number(event.progress || 0)
          this.setUploadData({ uploadProgressFile: fileName, uploadProgress: Math.min(99, Math.round(((uploadedIndex + fileProgress / 100) / total) * 100)) })
        }
      }).then((uploaded) => {
        const id = uploaded && uploaded.id
        if (!id) throw new Error('上传结果缺少文件 ID')
        item.uploadedId = id
        this.setFileUploadField(item, 'uploadedId', id)
        uploadedIndex += 1
        this.setFileUploadState(item, 'done')
        this.setUploadData({ uploadProgress: Math.round((uploadedIndex / total) * 100), uploadProgressFile: fileName })
        resolve({ key: item.key, id })
      }).catch((error) => {
        // 失败不阻断其余文件:记录状态与原因,结束时统一提示可重试
        this.setFileUploadState(item, 'failed', (error && (error.message || error.errMsg)) || '上传失败')
        resolve(null)
      })
    })
    pending.reduce((chain, item) => chain.then(() => uploadOne(item)), Promise.resolve()).then(() => {
      const failed = pending.filter((item) => !item.uploadedId)
      if (failed.length) {
        wx.hideLoading()
        this.setUploadData({ loading: false })
        const names = failed.slice(0, 3).map((item) => item.fileName).join('、') + (failed.length > 3 ? ` 等 ${failed.length} 个文件` : '')
        wx.showModal({
          title: '部分文件上传失败',
          content: `${names} 上传失败。已成功的文件不会重传,可重试失败项或移除后再提交。`,
          confirmText: '重试失败项',
          cancelText: '稍后处理',
          success: (res) => { if (res.confirm) this.submit() }
        })
        return
      }
      const uploadedIds = pending.map((item) => ({ key: item.key, id: item.uploadedId }))
      this.saveRecordWithFiles(doneIds.concat(uploadedIds), customer, category, isSingleSlot)
        .then(() => this.finishSubmit())
        .catch((error) => {
          showError(error, '四项激励上传失败')
          this.setUploadData({ loading: false })
          wx.hideLoading()
        })
    })
  },

  /** 逐文件字段写回 fileSlots 源(带 slotIndex/fileIndex 定位;uploadedId 落源,重试才跳得过) */
  setFileUploadField(item, field, value) {
    if (item.slotIndex == null || item.fileIndex == null) return
    const source = (this.data.fileSlots[item.slotIndex] || {}).files
    if (!source || !source[item.fileIndex]) return // 单文件槽无 files 数组时仅内存态(uploadedId 仍在 item 上)
    this.setData({ [`fileSlots[${item.slotIndex}].files[${item.fileIndex}].${field}`]: value },
      () => this.refreshUploadViewState())
  },

  setFileUploadState(item, state, error) {
    this.setFileUploadField(item, 'uploadState', state)
    if (error !== undefined) this.setFileUploadField(item, 'uploadError', error)
  },

  /** 组装 payload 并落库(上传全部就绪后调用;口径与原提交一致) */
  saveRecordWithFiles(uploadedIds, customer, category, isSingleSlot) {
    wx.showLoading({ title: '保存中' })
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
    const idsBySlot = uploadedIds.reduce((acc, item) => {
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
  },

  /** 落库后的收尾:成功提示 + 回列表(失败续办语义见 uploadPendingFiles) */
  // 【PR-13 残余 2026-09-20】激励上传表单草稿(复刻客户表单议题四模式):
  // 离开自动保存(新建模式,仅真实环境;客户+分类已选才存) / 进入询问恢复 / 提交成功清除;
  // 乐观锁版本防多端覆盖; 已上传到服务端的附件按 id 恢复(existing), 本地暂存文件不跨会话。
  onHide() { this.autoSaveDraft() },
  onUnload() {
    // 【L12】标记已卸载: 草稿保存请求照常发出完成, 仅回调里不再对已卸载页面 setData
    this._unloaded = true
    this.autoSaveDraft()
  },

  autoSaveDraft() {
    if (this.data.editId || this.data.loading) return
    if (getApp().globalData.demoMode) return
    const customer = this.data.customers[this.data.selectedCustomerIndex]
    const category = this.data.categories[this.data.selectedCategoryIndex]
    if (!customer || !category) return // 关键选项未选齐不存(等价"空表单不存")
    const uploadedFiles = []
    ;(this.data.fileSlots || []).forEach((slot) => {
      if (slot.hidden) return
      const files = slot.files && slot.files.length ? slot.files : (slot.filePath ? [slot] : [])
      files.forEach((file) => {
        const id = file.uploadedId || (file.existing ? file.fileId : '')
        if (id) uploadedFiles.push({ key: slot.key, id, name: file.fileName || file.name || `附件-${id}` })
      })
    })
    saveFormDraft('incentive_upload', {
      customerId: customer.id,
      categoryId: category.id,
      industry: this.data.industryOptions[this.data.selectedIndustryIndex] || customer.industry || '',
      period: this.data.periodOptions[this.data.selectedPeriodIndex] || defaultPeriod(),
      relatedProducts: this.data.relatedProducts || '',
      specificProduct: this.data.specificProduct || '',
      preInstallQuestion: this.data.preInstallQuestion || '',
      remark: this.data.remark || '',
      shared: this.data.shared !== 0,
      uploadedFiles
    }, this.data.draftVersion).then((result) => {
      // 【L12】页面已卸载: 存储本身已完成, 只是跳过界面回写(draftVersion 无处可用)
      if (this._unloaded) return
      const d = result && result.data
      if (d && d.version) this.setData({ draftVersion: d.version })
    }).catch(() => {}) // 自动保存静默失败,不打扰用户
  },

  tryRestoreDraft() {
    if (getApp().globalData.demoMode || this.data.editId) return
    getFormDraft('incentive_upload').then((result) => {
      const draft = result && result.data
      if (!draft || !draft.draftData) return
      let parsed
      try { parsed = JSON.parse(draft.draftData) } catch (e) { return }
      if (!parsed || !parsed.customerId || !parsed.categoryId) return
      const customer = (this.data.customers || []).find((item) => String(item.id) === String(parsed.customerId))
      const category = (this.data.categories || []).find((item) => String(item.id) === String(parsed.categoryId))
      if (!customer || !category) {
        // 选项已失效(客户/分类被删), 草稿无法落地即清
        deleteFormDraft('incentive_upload').catch(() => {})
        return
      }
      wx.showModal({
        title: '发现未完成的草稿',
        content: `客户"${customer.customerName}"的激励上传未完成(保存于 ${String(draft.updatedAt || '').slice(0, 16)}),是否恢复?`,
        confirmText: '恢复',
        cancelText: '放弃',
        success: (res) => {
          if (res.confirm) this.applyDraft(parsed, draft.version || 1)
          else deleteFormDraft('incentive_upload').catch(() => {})
        }
      })
    }).catch(() => {})
  },

  applyDraft(parsed, version) {
    const customerIndex = this.data.customers.findIndex((item) => String(item.id) === String(parsed.customerId))
    const category = this.data.categories.find((item) => String(item.id) === String(parsed.categoryId))
    const categoryIndex = this.data.categories.indexOf(category)
    const industryIndex = parsed.industry ? this.data.industryOptions.indexOf(parsed.industry) : -1
    const periodOptions = this.data.periodOptions.includes(parsed.period) ? this.data.periodOptions : [parsed.period, ...this.data.periodOptions].filter(Boolean)
    // 已上传附件按 id 恢复为 existing 条目(与编辑态 existingFiles 同形状, 提交端 recognized existing&&fileId)
    const fileSlots = applyCategorySlotMode(defaultSlots(), category, this.data.categories).map((slot) => ({
      ...slot,
      files: (parsed.uploadedFiles || []).filter((file) => file.key === slot.key).map((file) => ({
        fileId: file.id,
        fileName: file.name,
        fileUrl: '',
        filePath: '',
        fileKind: isImageFile(file.name, '') ? 'image' : 'file',
        existing: true
      }))
    }))
    this.setData({
      selectedCustomerIndex: customerIndex,
      selectedCategoryIndex: categoryIndex,
      selectedParentCategoryIndex: parentIndexOfCategory(this.data.categories, this.data.categoryParentNodes, categoryIndex),
      selectedIndustryIndex: industryIndex,
      periodOptions,
      selectedPeriodIndex: Math.max(periodOptions.indexOf(parsed.period || defaultPeriod()), 0),
      relatedProducts: parsed.relatedProducts || '',
      specificProduct: parsed.specificProduct || '',
      preInstallQuestion: parsed.preInstallQuestion || '',
      remark: parsed.remark || '',
      shared: parsed.shared !== false,
      fileSlots,
      draftVersion: version,
      selectedSlotIndex: Math.max(fileSlots.findIndex((slot) => !slot.hidden), 0)
    }, () => this.refreshUploadViewState())
  },

  finishSubmit() {
    if (!this.data.editId) deleteFormDraft('incentive_upload').catch(() => {}) // 【PR-13】提交成功清草稿
    wx.showToast({ title: this.data.editId ? '追加成功' : '上传成功', icon: 'success' })
    // 【PR-12 2026-09-19 首批】激励列表是 tabBar 页,navigateTo 跳 tab 会被微信拒绝
    // (此前静默失败,用户停在原页);switchTab 回列表,配合列表 onShow 刷新即见新记录
    wx.switchTab({ url: '/pages/incentive/index' })
    this.setUploadData({ loading: false, uploadProgress: 0, uploadProgressFile: '' })
    wx.hideLoading()
  }
})
