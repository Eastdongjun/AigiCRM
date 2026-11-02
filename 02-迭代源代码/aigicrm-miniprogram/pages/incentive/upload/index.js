// 【C2 2026-10-21】getCustomer 供草稿恢复按 id 补拉客户(本地不再持有全量客户列表)
const { getCustomerList, getCustomer } = require('../../../api/customer')
const { getCategoryTree, addRecord, getRecords, updateRecord, removeRecordFile } = require('../../../api/incentive')
const { getDownloadUrl, getDownloadToken, uploadFile } = require('../../../api/file')
const { getDictData } = require('../../../api/system')
// 【E10】表单草稿接口自 api/system 迁至 api/draft(D38 域归位收尾), 引用随迁
const { getFormDraft, saveFormDraft, deleteFormDraft } = require('../../../api/draft')
const { requireLogin, hasPermission } = require('../../../utils/auth')
const { fetchRemoteAttachment, saveDownloadedFileToDisk, isTextFileName, openTextView } = require('../../../utils/attachment')
const { showError, showErrorWithRetry } = require('../../../utils/feedback')

function flattenCategories(nodes = [], result = [], depth = 1) {
  nodes.forEach((item) => {
    result.push({
      id: item.id,
      parentId: item.parentId,
      categoryCode: item.categoryCode || '',
      name: item.categoryName || item.name || '未命名分类',
      score: item.score || 0
    })
    // 【2026-10-29 归树批】三级(产品小类)只用于产品目录派生, 不进分类选项扁平表——
    // 保持分类两级选择行为与 seed 前完全一致
    if (depth < 2 && item.children && item.children.length) flattenCategories(item.children, result, depth + 1)
  })
  return result
}

// 【2026-10-29 归树批】产品目录(大类→小类)从分类树"产品试点"分支派生(系统设置→激励类别管理维护);
// 树缺行/接口失败时由 data.relatedCatOptions/productCats 的兜底常量接管(fail-open)
function deriveProductCatalog(nodes = []) {
  const trial = (nodes || []).find((n) => n && ((n.categoryCode || '') === 'trial' || n.categoryName === '产品试点' || n.name === '产品试点'))
  const cats = {}
  const keys = []
  ;(((trial && trial.children) || [])).forEach((child) => {
    const name = (child && (child.categoryName || child.name)) || ''
    if (!name) return
    const subs = ((child && child.children) || []).map((g) => (g && (g.categoryName || g.name)) || '').filter(Boolean)
    if (!subs.length) return
    keys.push(name)
    cats[name] = subs
  })
  return keys.length ? { keys, cats } : null
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

// 【C2 2026-10-21】客户选择半屏服务端分页页大小: 滚到底追加下一页
const CUSTOMER_SHEET_PAGE_SIZE = 20

function defaultPeriod() {
  return `${new Date().getFullYear()}全年`
}

function buildPeriodOptions() {
  const currentYear = new Date().getFullYear()
  const options = []
  for (let year = currentYear + 1; year >= currentYear - 5; year -= 1) {
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
  // 【C2 2026-10-21】选中态改为客户对象快照(selectedCustomer): 服务端分页后数组下标不再稳定,
  // 不可用 customers[selectedCustomerIndex] 判定(跨页/刷新后下标指向错位客户)
  const hasCustomer = Boolean(data.selectedCustomer && data.selectedCustomer.id != null)
  const hasCategory = data.selectedCategoryIndex >= 0 && Boolean(selectedCategory)
  if (!hasCustomer || !hasCategory) return false
  const code = categoryCodeOf(selectedCategory, categories)
  const isSingleSlot = ['visit', 'exchange', 'plan'].includes(code)
  if (!isSingleSlot) {
    // 【P1-4 2026-10-18】trial 等多槽类: 五槽任一有文件才可提交——与后端 hasAnyFile 守卫对齐
    // (此前 0 文件可点提交,点了才被后端报"请至少上传一个文件")
    return data.fileSlots.some((slot) => slotFileCount(slot) > 0)
  }
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

/** 【C2 2026-10-21】客户选择半屏改服务端搜索+分页后, 选中客户以对象快照承载:
 *  快照在选中/回填/草稿恢复时写入(selectedCustomer), 不依赖当前页数组下标,
 *  翻页/搜索刷新后选中态与提交归属(customer.id)不会错位。 */
function selectedCustomerOf(data) {
  return (data && data.selectedCustomer) || null
}

function customerPickerView(customer) {
  const name = customer ? (customer.customerName || customer.name || '') : ''
  return { text: name || '请选择客户', className: name ? '' : 'placeholder' }
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
  const customer = selectedCustomerOf(data)
  // 【I2】未选分类时初始 selectedSlotIndex=0 指向隐藏的通用佐证槽; 视图/上传都回落到首个可见槽,
  // 否则文件写进隐藏槽后既不展示也不进提交快照(被静默丢弃)
  const selectedSlot = data.fileSlots[selectedSlotIndex]
  const currentSlot = (selectedSlot && !selectedSlot.hidden ? selectedSlot : data.fileSlots.find((slot) => !slot.hidden)) || {}
  const hasCurrentFile = Boolean(currentSlot.fileName)
  const slotOptionsView = data.fileSlots.map((slot, index) => ({
    ...slot,
    originalIndex: index,
    visible: !slot.hidden,
    className: `slot-option${selectedSlotIndex === index ? ' active' : ''}`,
    ready: Boolean(slot.fileName),
    countText: slotCountText(slot)
  }))
  // 【2026-10-30 同槽翻页】每槽当前页备忘: swiper bindchange 写入, 视图重建时钳回合法位
  const slotPages = data.slotPages || {}
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
        // 【2026-10-30 同槽翻页】非图片页的类型面文案
        kindText: file.fileKind === 'video' ? '视频' : (file.fileKind === 'image' ? '图片' : '文档'),
        // 【三轮复检修复】已有图片接入远端缩略图(原只绑本地 filePath, existing 恒空串=灰底空白)
        thumbUrl: (file.existing && file.fileId && (file.fileKind === 'image' || isImageFile(file.fileName, '')) && data.dlToken)
          ? (getDownloadUrl({ id: file.fileId }, true) + '&token=' + encodeURIComponent(data.dlToken))
          : '',
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
    // 【2026-10-30 同槽翻页】补翻页视图字段: viewIndex=深层 setData 路径锚, current/pageText=swiper 当前页与页码
    .map((slot, viewIndex) => {
      const pageCount = slot.files.length
      const current = Math.min(slotPages[slot.slotIndex] || 0, Math.max(0, pageCount - 1))
      return { ...slot, viewIndex, current, pageText: pageCount > 1 ? `${current + 1}/${pageCount}` : '' }
    })
  return {
    customerPicker: customerPickerView(customer),
    showCustomerConfirm: Boolean(customer),
    selectedCustomerName: customer ? (customer.customerName || customer.name || '未命名客户') : '',
    selectedCustomerMeta: customerConfirmMeta(customer),
    categoryPicker: pickerTextView(data.categoryNames, data.selectedCategoryIndex, '请选择分类'),
    // 【对齐 PC 2026-09-20】分类两级选择: 大类(产品试点/两谈两做/外出参观/交流拜访) → 子类(涂层类/防滑板类…);
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
    // 【工作流复核确认修复】当前槽满员标记(满员隐藏添加入口, 与 PC 同口径)
    currentSlotFull: (currentSlot.files || []).length >= MAX_FILES_PER_SLOT,
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

/** 【C2 2026-10-21】演示模式客户选择半屏的数据源(演示无后端, 本地过滤不分页) */
const DEMO_SHEET_CUSTOMERS = [
  { id: 1, customerName: '华东智能制造集团', industry: '制造业' }
]

/** 半屏行视图: 服务端分页行/演示行统一形状, 选中按 id 定位(不按下标) */
function toSheetItem(item) {
  return {
    id: item.id,
    name: item.customerName || item.name || '未命名客户',
    meta: [item.industry, item.devType].filter(Boolean).join(' · '),
    industry: item.industry || ''
  }
}

Page({
  data: {
    // 【C2 2026-10-21】客户选中态=对象快照+id(原 customers 前100数组+下标随服务端分页下线)
    selectedCustomer: null,
    selectedCustomerId: '',
    // 【#3 2026-10-12 + C2 2026-10-21】客户选择半屏: open/keyword/filtered[{id,name,meta}]/
    // total=服务端匹配总数(非本页行数)/page/finished(无更多); 真实模式走服务端搜索+分页
    // (getCustomerList keyword/current/size), 演示模式本地过滤
    customerSheet: { open: false, keyword: '', filtered: [], total: 0, page: 1, loading: false, finished: false },
    categories: [],
    categoryNames: [],
    industryOptions: [],
    selectedIndustryIndex: -1,
    periodOptions: buildPeriodOptions(),
    selectedPeriodIndex: Math.max(buildPeriodOptions().indexOf(defaultPeriod()), 0),
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
    // 【2026-10-30 同槽翻页】每槽当前页/上次文件数备忘(重建视图时新增跳最新页, 删减钳回末位)
    slotPages: {},
    slotFileCounts: {},
    uploadView: buildUploadViewState({
      selectedCustomer: null,
      categoryNames: [],
      industryOptions: [],
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
    // 【2026-10-30 同槽翻页】维护每槽当前页: 该槽文件新增→跳最新一页(刚选的立即可见), 删减/清空→钳回末位
    const slotPages = { ...(next.slotPages || {}) }
    const slotFileCounts = { ...(next.slotFileCounts || {}) }
    ;(next.fileSlots || []).forEach((slot, i) => {
      const n = (slot.files || []).length
      if (n > (slotFileCounts[i] ?? 0)) slotPages[i] = Math.max(0, n - 1)
      else slotPages[i] = Math.min(slotPages[i] || 0, Math.max(0, n - 1))
      slotFileCounts[i] = n
    })
    this.setData({ ...patch, slotPages, slotFileCounts, uploadView: buildUploadViewState({ ...next, slotPages }) })
  },

  // 【2026-10-30 同槽翻页】swiper 滑动回调: 只补丁当前槽的 current/pageText 深层路径, 不整树重建
  onSlotPageChange(e) {
    const viewIndex = Number(e.currentTarget.dataset.viewIndex)
    const slotIndex = Number(e.currentTarget.dataset.slotIndex)
    const count = Number(e.currentTarget.dataset.count) || 0
    const current = Math.max(0, Math.min(Number(e.detail.current) || 0, Math.max(0, count - 1)))
    this.setData({
      [`slotPages.${slotIndex}`]: current,
      [`uploadView.selectedFileSlotsView[${viewIndex}].current`]: current,
      [`uploadView.selectedFileSlotsView[${viewIndex}].pageText`]: count > 1 ? `${current + 1}/${count}` : ''
    })
  },

  onLoad(options = {}) {
    if (!requireLogin()) return
    const editId = options.id || ''
    if (!getApp().globalData.demoMode && !editId && !hasPermission('incentive:add')) {
      wx.showToast({ title: '没有四项服务上传权限', icon: 'none' })
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
      if (this._unloaded) return // 【六轮复检修复】同上
      this.setData({ industryOptions: (list || []).map((item) => item.dictValue || item.dictLabel).filter(Boolean) }, () => this.refreshUploadViewState())
    }).catch((error) => {
      if (this._unloaded) return
      showErrorWithRetry(error, '行业字典加载失败，请重试', () => this.loadIndustries())
      this.setData({ industryOptions: [] }, () => this.refreshUploadViewState())
    })
  },

  loadOptions() {
    if (getApp().globalData.demoMode) {
      const categories = [{ id: 1, parentId: 0, categoryCode: 'trial', name: '产品试点', score: 3 }, { id: 2, parentId: 0, categoryCode: 'exchange', name: '交流拜访', score: 1 }]
      this.setData({ categories, categoryParentNodes: deriveCategoryParents(categories), categoryNames: categories.map((item) => item.name) }, () => this.refreshUploadViewState())
      if (this.data.editId) this.loadEditRecord()
      return
    }
    // 【C2 2026-10-21】不再预拉"前 100 个客户"——客户选择半屏改服务端搜索+分页, 打开/输入时按需
    // 拉取(getCustomerList keyword/current/size); 后端按 createdAt DESC 排序, 旧实现里新客户会把
    // 老客户挤出首屏, 第 101 个之后的客户永远选不到。此处只加载分类树。
    getCategoryTree().then((categoryRes) => {
      if (this._unloaded) return // 【六轮复检修复】进页秒退不再对已卸载页 setData
      const categories = flattenCategories(categoryRes || [])
      this.setData({ categories, categoryParentNodes: deriveCategoryParents(categories), categoryNames: categories.map((item) => item.name) }, () => this.refreshUploadViewState())
      // 【2026-10-29 归树批】产品大类/小类随树派生(三级结构就绪即覆盖兜底常量)
      const derived = deriveProductCatalog(categoryRes || [])
      if (derived) this.setData({ relatedCatOptions: derived.keys, productCats: derived.cats })
      if (this.data.editId) this.loadEditRecord()
      else this.tryRestoreDraft() // 【PR-13】新建模式: 选项就绪后检查草稿恢复(分类索引依赖选项)
      // 【五轮复检修复】草稿恢复的已上传图片同样需要 dlToken 才能显示远端缩略图(原只编辑模式取)
      if (!this.data.editId) {
        getDownloadToken().then((res) => {
          if (this._unloaded) return
          this.setData({ dlToken: (res && (res.token || (res.data && res.data.token))) || '' },
            () => this.refreshUploadViewState())
        }).catch(() => {})
      }
    }).catch((error) => showError(error, '上传选项加载失败'))
  },

  loadEditRecord() {
    if (!this.data.editId) return
    if (getApp().globalData.demoMode) return wx.showToast({ title: '演示记录不可追加', icon: 'none' })
    getRecords({}).then((rows) => {
      if (this._unloaded) return // 【五轮复检修复】加载在途卸载不再对已销毁页 setData
      const records = Array.isArray(rows) ? rows : (rows && (rows.records || rows.data || rows.list)) || []
      const record = records.find((item) => String(item.id) === String(this.data.editId))
      if (!record) throw new Error('找不到四项服务记录')
      this.fillEditRecord(record)
      // 【四轮复检修复】编辑模式取下载 token 供已有图片远端缩略图(详情页同模式)。
      // request 层已解包 body.data → then 入参就是 {token}(四轮复检证实原 res.data.token 恒 undefined);
      // token 存 data.dlToken 而非实例属性——buildUploadViewState 是模块级函数, 读不到 this(四轮复检证实)
      getDownloadToken().then((res) => {
        if (this._unloaded) return
        this.setData({ dlToken: (res && (res.token || (res.data && res.data.token))) || '' },
          () => this.refreshUploadViewState())
      }).catch(() => { if (!this._unloaded) this.setData({ dlToken: '' }) })
    }).catch((error) => showError(error, '四项服务记录加载失败'))
  },

  fillEditRecord(record) {
    // 【C2 2026-10-21】客户选择改服务端分页后, 编辑态不再把记录客户 unshift 进本地"前100"选项
    // 兜底——选中态直接以记录自带客户快照(selectedCustomer)承载, 任意客户的记录都可编辑
    // (提交时 customerId 等值回传, 归属不变), 半屏内按 id 高亮。
    const customer = record.customerId
      ? { id: record.customerId, customerName: record.customerName || record.name || '未命名客户', industry: record.industry || '' }
      : null
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
      selectedCustomer: customer,
      selectedCustomerId: customer ? customer.id : '',
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

  // 【#3 2026-10-12】半屏搜索层; 【C2 2026-10-21】真实模式改服务端搜索+分页:
  // 打开/清空重拉第 1 页、输入防抖 300ms 后按 keyword 搜索第 1 页、滚到底追加下一页;
  // 请求带序号守卫, 慢返回的旧页不得覆盖新搜索结果。
  applyCustomerSelection(customer) {
    if (!customer || customer.id === undefined || customer.id === null) return
    const industryIndex = customer.industry ? this.data.industryOptions.indexOf(customer.industry) : -1
    this.setData({ selectedCustomer: customer, selectedCustomerId: customer.id, selectedIndustryIndex: industryIndex }, () => this.refreshUploadViewState())
  },
  openCustomerSheet() {
    this.setData({ 'customerSheet.open': true, 'customerSheet.keyword': '' }, () => this.fetchCustomerSheetPage(1, false))
  },
  closeCustomerSheet() {
    this.setData({ 'customerSheet.open': false })
  },
  clearCustomerKeyword() {
    this.setData({ 'customerSheet.keyword': '' }, () => this.fetchCustomerSheetPage(1, false))
  },
  inputCustomerKeyword(e) {
    this.setData({ 'customerSheet.keyword': (e.detail.value || '').trim() })
    if (this._sheetKeywordTimer) clearTimeout(this._sheetKeywordTimer)
    this._sheetKeywordTimer = setTimeout(() => {
      this._sheetKeywordTimer = null
      if (this._unloaded) return
      this.fetchCustomerSheetPage(1, false)
    }, 300)
  },
  /** 拉取半屏一页客户(真实模式服务端分页/搜索; 演示模式本地过滤不分页) */
  fetchCustomerSheetPage(page, append) {
    if (getApp().globalData.demoMode) return this.filterDemoCustomerSheet()
    // 【B4 2026-10-21】去掉 loading 早退: 旧实现在途时直接丢弃新搜索且不补发(复现实证
    // "输入=华东而列表仍显示旧关键词结果")。改为新请求递增 seq 取代在途请求, 旧响应由下方
    // 序号守卫丢弃; 翻页入口 loadMoreCustomers 自带 loading/finished 守卫, 不会重复触底追加。
    const seq = (this._customerSheetSeq = (this._customerSheetSeq || 0) + 1)
    const keyword = (this.data.customerSheet.keyword || '').trim()
    this.setData({ 'customerSheet.loading': true })
    getCustomerList({ current: page, size: CUSTOMER_SHEET_PAGE_SIZE, keyword }).then((res) => {
      // 【C2】序号守卫: 旧请求慢返回(输入快于响应)不得覆盖新搜索结果
      if (this._unloaded || seq !== this._customerSheetSeq) return
      const pageData = res && res.records ? res : ((res && res.data) || {})
      const rows = pageData.records || []
      const total = Number(pageData.total) || 0
      const items = rows.map(toSheetItem)
      const filtered = append ? (this.data.customerSheet.filtered || []).concat(items) : items
      this.setData({
        'customerSheet.filtered': filtered,
        'customerSheet.total': total, // 【C2】total 用服务端匹配总数, 不再用本页行数冒充
        'customerSheet.page': page,
        'customerSheet.finished': filtered.length >= total || !items.length,
        'customerSheet.loading': false
      })
    }).catch((error) => {
      if (this._unloaded || seq !== this._customerSheetSeq) return
      this.setData({ 'customerSheet.loading': false })
      showError(error, '客户列表加载失败')
    })
  },
  /** 演示模式: 固定候选本地过滤(演示无后端) */
  filterDemoCustomerSheet() {
    const keyword = (this.data.customerSheet.keyword || '').toLowerCase()
    const filtered = DEMO_SHEET_CUSTOMERS.map(toSheetItem)
      .filter((item) => !keyword || item.name.toLowerCase().includes(keyword) || (item.meta && item.meta.toLowerCase().includes(keyword)))
    this.setData({ 'customerSheet.filtered': filtered, 'customerSheet.total': filtered.length, 'customerSheet.finished': true, 'customerSheet.loading': false })
  },
  /** 滚动到底追加下一页(scroll-view scrolltolower) */
  loadMoreCustomers() {
    const sheet = this.data.customerSheet
    if (sheet.loading || sheet.finished) return
    this.fetchCustomerSheetPage(Number(sheet.page) + 1, true)
  },
  // 【C2 2026-10-21】选中按 customerId 定位(原数组下标在翻页/搜索后会指向错位客户)
  pickCustomer(e) {
    const id = e.currentTarget.dataset.id
    const row = (this.data.customerSheet.filtered || []).find((item) => String(item.id) === String(id))
    if (!row) return
    const customer = { id: row.id, customerName: row.name, industry: row.industry || '' }
    this.setData({ 'customerSheet.open': false }, () => this.applyCustomerSelection(customer))
  },
  noop() {},
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
    let index = this.data.selectedSlotIndex
    let slot = this.data.fileSlots[index]
    let redirected = false
    // 【I2】当前指向隐藏槽(未选分类时的初始态/切换分类后的残留)时, 自动落到首个可见槽并提示;
    // 隐藏槽在 selectedFileSlotsView 不展示、submit 也跳过, 静默写入等于丢文件
    if (!slot || slot.hidden) {
      index = this.data.fileSlots.findIndex((item) => !item.hidden)
      slot = this.data.fileSlots[index]
      redirected = true
    }
    if (!slot) {
      wx.showToast({ title: '请先选择分类后再上传文件', icon: 'none' })
      return
    }
    // 【工作流复核确认修复】满员拒绝+明确提示: 原静默 slice 截断, 现场多选的佐证照片被无声吞掉
    if ((slot.files || []).length >= MAX_FILES_PER_SLOT) {
      wx.showToast({ title: `每槽最多 ${MAX_FILES_PER_SLOT} 个文件，当前槽已满`, icon: 'none' })
      return
    }
    const files = (slot.files || []).concat({
      filePath,
      fileName,
      fileKind: fileKind || (isImageFile(fileName, filePath) ? 'image' : 'file')
    }).slice(0, MAX_FILES_PER_SLOT)
    const firstFile = files[0] || {}
    const patch = {
      [`fileSlots[${index}].files`]: files,
      [`fileSlots[${index}].filePath`]: firstFile.filePath || '',
      [`fileSlots[${index}].fileName`]: firstFile.fileName || '',
      [`fileSlots[${index}].fileKind`]: firstFile.fileKind || ''
    }
    if (redirected) patch.selectedSlotIndex = index
    this.setData(patch, () => this.refreshUploadViewState())
    if (redirected) wx.showToast({ title: `未选择分类，文件已放入「${slot.label}」`, icon: 'none' })
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
    // 【2026-10-17】视频走 previewMedia 全屏播放、文本走文本查看页——此前统一塞 openDocument,
    // 视频必然报错"无法直接预览"(openDocument 不认视频), txt/csv 同样打不开
    const name = file.fileName || ''
    if (file.fileKind === 'video' || /\.(mp4|mov|m4v|webm)$/i.test(name)) {
      recordAttachmentAction('previewMedia', { urls: [{ url: file.filePath, type: 'video' }] })
      wx.previewMedia({ sources: [{ url: file.filePath, type: 'video' }], fail: (error) => showError(error, '视频预览失败') })
      return
    }
    if (isTextFileName(name)) return openTextView(file.filePath, name)
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
      // 【三轮复检修复】卸载后不再 setData(与新代码 _unloaded 守卫口径一致)
    setBusy: (busy) => { if (this._unloaded) return; this.setUploadData({ fileActionLoading: busy }) },
      isImage: (item) => item.fileKind === 'image' || isImageFile(item.fileName, ''),
      // 【2026-10-17】既有视频附件: 全屏播放(此前落 openDocument 必报错); 文本类由
      // fetchRemoteAttachment 的文本分支自动接文本查看页
      onVideo: (tempFilePath) => wx.previewMedia({ sources: [{ url: tempFilePath, type: 'video' }], fail: (error) => showError(error, '视频预览失败') }),
      saveFailTitle: '下载保存失败',
      hideLoadingBeforeResult: false
    })
  },

  removeSelectedFile(event) {
    // 【工作流复检修复 2026-10-10】两道锁：提交上传中禁改(提交流程已捕获文件快照, 中途移除
    // 只改界面不改快照, 落库后"删掉的"文件会复活)；移除请求在途禁并发(旧下标会剔错文件)
    if (this.data.loading || this._removingFile) return
    const index = Number(event.currentTarget.dataset.index)
    const fileIndex = Number(event.currentTarget.dataset.fileIndex || 0)
    const slot = this.data.fileSlots[index] || {}
    const file = (slot.files || [])[fileIndex] || {}
    // 【用户裁决 2026-10-10】已有文件移除须立即真实生效：走后端原子接口(剔除→重算学分→审计)。
    // 此前本地剔除是假删——编辑提交 payload 只带本次新上传ID, 该槽没传新文件时后端 null 合并跳过, 刷新后文件复活。
    if (file.existing && file.fileId && this.data.editId) {
      this.removeExistingFileRemotely(slot, file, index)
      return
    }
    // 【三轮复检修复】仅"编辑态且缺 fileId"(URL-fallback 防御分支)才拦截——本地剔除=假删。
    // 新建模式(含草稿恢复的已上传文件)本地剔除即真删: 记录未创建, 提交快照不含即不挂载
    if (file.existing && this.data.editId) {
      wx.showToast({ title: '该文件暂不支持在小程序移除，请在电脑端处理', icon: 'none', duration: 2500 })
      return
    }
    const files = (slot.files || []).filter((_, idx) => idx !== fileIndex)
    const firstFile = files[0] || {}
    this.setData({
      [`fileSlots[${index}].files`]: files,
      [`fileSlots[${index}].filePath`]: firstFile.filePath || '',
      [`fileSlots[${index}].fileName`]: firstFile.fileName || '',
      [`fileSlots[${index}].fileKind`]: firstFile.fileKind || ''
    }, () => this.refreshUploadViewState())
  },

  /** 已有文件真删：确认→调 remove-file→本地剔除。slotKey(filePreId)→后端槽位(pre) 映射。
   *  【工作流复检修复 2026-10-10】mask 阻挡在途再点；回调前 _unloaded 守卫(防已卸载页 setData)；
   *  剔除按 fileId 身份定位(不按下标)；showError 传 error 对象+兜底文案(传字符串会恒显"操作失败") */
  removeExistingFileRemotely(slot, file, index) {
    const SLOT_KEYS = { fileId: 'file', filePreId: 'pre', fileMidId: 'mid', filePostId: 'post', fileTrackId: 'track' }
    const slotName = slot.label || '槽位'
    const fileName = (file.fileName || '').slice(0, 20)
    wx.showModal({
      title: '移除文件',
      content: `从「${slotName}」移除「${fileName}」？移除后学分将按最新规则重算，原文件仍保留在文件库可追溯。`,
      confirmText: '移除',
      confirmColor: '#e54d42',
      success: (res) => {
        if (!res.confirm) return
        this._removingFile = true
        wx.showLoading({ title: '移除中', mask: true })
        removeRecordFile(this.data.editId, SLOT_KEYS[slot.key] || slot.key, file.fileId)
          .then(() => {
            this._removingFile = false
            wx.hideLoading()
            if (this._unloaded) return
            const files = (this.data.fileSlots[index].files || [])
              .filter((x) => !(x.existing && String(x.fileId) === String(file.fileId)))
            const firstFile = files[0] || {}
            this.setData({
              [`fileSlots[${index}].files`]: files,
              [`fileSlots[${index}].filePath`]: firstFile.filePath || '',
              [`fileSlots[${index}].fileName`]: firstFile.fileName || '',
              [`fileSlots[${index}].fileKind`]: firstFile.fileKind || ''
            }, () => this.refreshUploadViewState())
            // 【五轮复检修复】后端已重算学分, 置刷新标记防列表 30s 节流导致返回时数据陈旧
            const gData = getApp().globalData
            if (gData.forceRefresh) gData.forceRefresh.incentive = true
            wx.showToast({ title: '已移除并重算学分', icon: 'none', duration: 2000 })
          })
          .catch((err) => {
            this._removingFile = false
            wx.hideLoading()
            if (this._unloaded) return
            showError(err || '移除失败', '移除失败')
          })
      }
    })
  },

  cancelUpload() {
    if (this.data.loading) return
    const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
    if (pages.length > 1) {
      wx.navigateBack()
      return
    }
    wx.switchTab({ url: '/pages/incentive/home/index' }) // 无栈兜底同样必须 switchTab【2026-10-23 拆分: tabBar=轻量客户记录】
  },

  submit() {
    // 【M18 2026-10-04】按钮 disabled 属性只保留给"提交中"态, 表单未就绪时按钮可点击;
    // 此处按缺失项逐条校验并 toast, 保证用户知道为何不能提交(原先 submitDisabled 早退
    // 会把"请补齐客户和分类"/"请至少上传 1 个文件"两条提示挡成不可达死代码)
    if (this.data.loading || this._removingFile) return // 提交中/移除在途重复点击静默忽略(已有全屏 loading 反馈)【复检修复: 与移除入口防线对称】
    const customer = this.data.selectedCustomer // 【C2】选中客户=对象快照, 不再按下标取
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
    // 【工作流复检修复 2026-10-10】mask:true 阻挡触摸穿透——上传中仍可点"移除"删待传文件,
    // 但提交快照已捕获该文件, 落库后会"复活"(假删); mask 与 removeSelectedFile 的 loading 守卫双保险
    wx.showLoading({ title: '上传中', mask: true })
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
          showError(error, '四项服务上传失败')
          this.setUploadData({ loading: false })
          wx.hideLoading()
        })
    }
    let uploadedIndex = doneIds.length
    const total = doneIds.length + pending.length
    this._progressNotifyTs = 0
    const uploadOne = (item) => new Promise((resolve) => {
      const fileName = item.fileName || '附件'
      this.setFileUploadState(item, 'uploading')
      this.setUploadData({ uploadProgressFile: fileName, uploadProgress: Math.min(99, Math.round((uploadedIndex / total) * 100)) })
      uploadFile(item.filePath, null, 'incentive', {
        onProgress: (event) => {
          // 【性能 2026-10-27】进度通知节流 150ms(收尾 100% 直通): onProgress 高频连发,
          // 每 tick 重建 uploadView 再 setData 是上传期间掉帧来源之一
          const fileProgress = Number(event.progress || 0)
          const now = Date.now()
          if (fileProgress < 100 && now - (this._progressNotifyTs || 0) < 150) return
          this._progressNotifyTs = now
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
        if (this._unloaded) return // 【七轮复检修复】卸载后不跨页弹重试窗
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
          showError(error, '四项服务上传失败')
          this.setUploadData({ loading: false })
          wx.hideLoading()
        })
    })
  },

  /** 逐文件字段写回 fileSlots 源(带 slotIndex/fileIndex 定位;uploadedId 落源,重试才跳得过) */
  setFileUploadField(item, field, value) {
    if (this._unloaded) return // 【七轮复检修复】上传在途回退, 不再对已卸载页 setData
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
    // 【C4 2026-10-21】文本字段清空语义按模式区分:
    // - 编辑态发 ''(后端 updateRecord"非 null 即合并", '' 才能把库内旧值真正清空;
    //   原先 trim() || null 发 null=未提供, 用户清空后旧值复活, 界面却报保存成功);
    // - 新建态维持 || null(库内不写空串, 与既有口径一致)。
    // industry 是 picker 选择值不会清成 '', 维持原语义。
    const clearableText = (value) => this.data.editId ? value : (value || null)
    const payload = {
      customerId: customer.id,
      categoryId: category.id,
      period: selectedPeriod,
      industry: selectedIndustry || null,
      relatedProducts: clearableText(this.data.relatedProducts.trim()),
      specificProduct: clearableText(this.data.specificProduct.trim()),
      preInstallQuestion: clearableText(this.data.preInstallQuestion.trim()),
      remark: clearableText(this.data.remark.trim()),
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
      // 【C4】keepEmpty: PUT body 保留 ''(仅剥 undefined/null), 清空字段才能落库生效
      return updateRecord(this.data.editId, payload, { keepEmpty: true })
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
    // 【C2 2026-10-21】半屏搜索防抖定时器随页面销毁清理
    if (this._sheetKeywordTimer) { clearTimeout(this._sheetKeywordTimer); this._sheetKeywordTimer = null }
    // 【M12 对齐 customer/form 2026-10-07】已成功提交后卸载不再触发草稿自动保存
    // (autoSaveDraft 首行有同款守卫, 此处短路是双保险)
    if (this._submitted) return
    this.autoSaveDraft()
  },

  autoSaveDraft() {
    if (getApp().globalData.demoMode || this.data.editId || this.data.loading || this._submitted) return
    // 演示模式/编辑态/加载中不存草稿; 【M12 对齐 customer/form 2026-10-07】提交成功后草稿已被
    // deleteFormDraft 清除, 再存回即"草稿复活", 恢复再提交=激励重复入库 → _submitted 一律不存(上方守卫)
    const customer = this.data.selectedCustomer // 【C2】选中客户=对象快照
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
      // 【C2 修复附带】同 tryRestoreDraft: 解包后的草稿行直接读 version(旧 result.data 恒 undefined)
      const d = result && (result.version !== undefined ? result : result.data)
      if (d && d.version) this.setData({ draftVersion: d.version })
    }).catch(() => {}) // 自动保存静默失败,不打扰用户
  },

  tryRestoreDraft() {
    if (getApp().globalData.demoMode || this.data.editId) return
    getFormDraft('incentive_upload').then((result) => {
      if (this._unloaded) return
      // 【C2 修复附带】request 层已解包 body.data → then 入参即草稿行 {draftType,draftData,version,updatedAt}
      // (后端 FormDraftService.toRow 无 data 键; 旧代码读 result.data 恒 undefined, 恢复弹窗实际永远不弹)
      const draft = result && (result.draftData !== undefined ? result : result.data)
      if (!draft || !draft.draftData) return
      let parsed
      try { parsed = JSON.parse(draft.draftData) } catch (e) { return }
      if (!parsed || !parsed.customerId || !parsed.categoryId) return
      const category = (this.data.categories || []).find((item) => String(item.id) === String(parsed.categoryId))
      if (!category) {
        // 分类已删除, 草稿无法落地即清(分类树全量拉取, 本地判定可靠)
        deleteFormDraft('incentive_upload').catch(() => {})
        return
      }
      // 【C2 2026-10-21】客户改服务端分页后本地不再持有全量客户列表, 草稿客户一律按 id 单独
      // 补拉(getCustomer)。只有确实 404/无权限(客户已删/不可见)才清草稿; 网络错误等临时失败
      // 保留草稿待下次恢复——不得把用户未完成的录入当"选项失效"误删(旧实现本地列表找不到即删)。
      getCustomer(parsed.customerId).then((customer) => {
        if (this._unloaded) return
        if (!customer || !customer.id) {
          deleteFormDraft('incentive_upload').catch(() => {})
          return
        }
        wx.showModal({
          title: '发现未完成的草稿',
          content: `客户"${customer.customerName || customer.name || '未命名客户'}"的激励上传未完成(保存于 ${String(draft.updatedAt || '').slice(0, 16)}),是否恢复?`,
          confirmText: '恢复',
          cancelText: '放弃',
          success: (res) => {
            if (res.confirm) this.applyDraft(parsed, customer, draft.version || 1)
            else deleteFormDraft('incentive_upload').catch(() => {})
          }
        })
      }).catch((error) => {
        if (this._unloaded) return
        // 404=客户已删除 / 403=不在数据范围: 草稿指向的客户永久不可达, 清掉;
        // 其余(超时/5xx)为临时失败, 保留草稿下次再试, 静默不打扰
        if (error && (error.statusCode === 404 || error.statusCode === 403)) {
          deleteFormDraft('incentive_upload').catch(() => {})
        }
      })
    }).catch(() => {})
  },

  applyDraft(parsed, customer, version) {
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
    // 【C2】选中客户=按 id 补拉到的客户快照(不再依赖本地列表下标)
    this.setData({
      selectedCustomer: { id: customer.id, customerName: customer.customerName || customer.name || '未命名客户', industry: customer.industry || parsed.industry || '' },
      selectedCustomerId: customer.id,
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
    // 【七轮复检修复】已卸载不强制 switchTab 拉回用户(落库已完成, 数据不丢)
    if (this._unloaded) return
    // 【M12 对齐 customer/form 2026-10-07】置已提交标志: switchTab 会卸载本页并触发
    // onUnload→autoSaveDraft, 此前会把刚 deleteFormDraft 清掉的草稿原样存回(草稿"复活"),
    // 恢复再提交=激励重复入库;标志必须先于 switchTab 置位
    this._submitted = true
    // 【PR-12 契约修复 2026-10-07】写成功置强制刷新标记: 列表 onShow 命中即绕过 30s 节流一次,
    // "写完回来立刻见结果";节流只拦纯回流,不拦写后回流
    getApp().globalData.forceRefresh.incentive = true
    if (!this.data.editId) deleteFormDraft('incentive_upload').catch(() => {}) // 【PR-13】提交成功清草稿
    wx.showToast({ title: this.data.editId ? '追加成功' : '上传成功', icon: 'success' })
    // 【PR-12 2026-09-19 首批】激励列表是 tabBar 页,navigateTo 跳 tab 会被微信拒绝
    // (此前静默失败,用户停在原页);switchTab 回列表,配合列表 onShow 刷新即见新记录
    // 【2026-10-23 拆分】tabBar 四项服务=轻量客户记录页(home), 完整工作台在应用中心
    wx.switchTab({ url: '/pages/incentive/home/index' })
    this.setUploadData({ loading: false, uploadProgress: 0, uploadProgressFile: '' })
    wx.hideLoading()
  }
})
