// 激励上传/管理页共享态（C4 拆组件 2026-08-09）
// 页面级 provide/inject（per-route）：incentive/index.vue 实例化一次并 provide，
// 筛选条/子分类面板/文件表格/上传弹窗/文件预览 inject 同一上下文。
// 共享内容：分类树/记录/权限 + 筛选与分页 + 上传弹窗全部状态(客户/文件槽/进度/分享) + 上传/追加/删除动作。
import { ref, reactive, computed, watch, inject, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmAction } from '@/utils/confirm'
import { Box, Location, ChatDotSquare, Document } from '@element-plus/icons-vue'
import { getCategoryTree, getRecords, addRecord, deleteRecord, updateRecord, removeRecordFile, checkIncentivePermission } from '@/api/incentive'
import { getCustomerList } from '@/api/customer'
import { getDictData } from '@/api/system'
import { useUserStore } from '@/store/user'
import request from '@/utils/request'
import { getDownloadToken, refreshDownloadToken } from '@/utils/token'
import { buildPeriodOptions, periodQueryValue, defaultRecordPeriod } from '@/utils/incentive-period'
// 【D10 2026-10-04】五大类产品目录改为共享常量（原先与 incentive/search.vue 各硬编码一份）
import { PRODUCT_CATS, PRODUCT_CAT_KEYS } from '@/views/incentive/product-catalog'
import { INDUSTRY_FALLBACK_OPTIONS } from '@/utils/dict-fallback'
import { mediaTypeOf, isImageName } from '@/utils/media-type'

export const IncentiveUploadKey = Symbol('incentiveUpload')

/** 子组件注入父级 incentive/index.vue 提供的共享上下文 */
export function injectIncentiveUpload() {
  const ctx = inject(IncentiveUploadKey)
  if (!ctx) throw new Error('injectIncentiveUpload 必须在 incentive/index.vue 的组件内使用')
  return ctx
}

export function useIncentiveUpload() {
  const userStore = useUserStore()
  const activeTab = ref('')
  const selectedUser = ref(null)
  const userList = ref([])
  const selectedDeptId = ref(null)
  const deptTree = ref([])
  // 【2026-09-12】周期口径统一：选项含「全年」（库内历史/生产数据用的是 2026全年），
  // 默认「全部」——原来默认 '2026上半年' 且每次都带该参数，而后端按等值过滤，
  // 导致打开页面必然 0 条（且下拉选不到"全年"）。见 utils/incentive-period.js 说明。
  const periodOptions = buildPeriodOptions()
  const selectedPeriod = ref('')
  const categories = ref([])
  const records = ref([])

  // 权限
  const canEdit = ref(false)
  const canDelete = ref(false)
  // 右侧文件列表分页（前端切片）
  const pageNum = ref(1)
  // 【2026-09-23 UI】默认每页 30:与文件/客户列表口径一致,10 条浪费整页高度
  const pageSize = ref(30)

  // ===== 安装文件预览 =====
  const previewVisible = ref(false), previewUrl = ref(''), previewType = ref('image'), previewName = ref('')
  // 【修复 2026-08-14】下载 token 未就绪时不拼 token=（避免必然 403 的空 token 请求）
  const fileUrl = (id) => { const t = getDownloadToken(); return t ? '/api/file/download/' + id + '?token=' + t : '/api/file/download/' + id }
  const parseFids = (v) => { if (!v) return []; if (typeof v === 'number') return [v]; try { const a = JSON.parse(v); return Array.isArray(a) ? a.map(x => Number(x)) : [v] } catch { return String(v).split(',').map(s => s.trim()).filter(Boolean) } }
  const splitNames = (n) => { if (!n) return []; return String(n).split(',').map(s => s.trim()).filter(Boolean) }
  // 文件名三态判定(image/video/doc)统一在 utils/media-type.js，与客户详情页 detectType 同源
  const fileType = mediaTypeOf
  // 安装文件预览:统一走 FilePreview(图片/视频预览+下载,文档显示下载入口);多文件取第一个
  const previewFile = async (id, name) => {
    const ids = parseFids(id), names = splitNames(name)
    const fid = ids[0], fname = names[0] || ''
    if (!fid) return
    await refreshDownloadToken()
    previewUrl.value = fileUrl(fid)
    previewType.value = fileType(fname)
    previewName.value = fname
    previewVisible.value = true
  }

  // 统一筛选
  const searchText = ref('')
  const filterCustomer = ref('')
  const filterGroup = ref('')
  const filterTier = ref('')

  // 客户选择相关
  const showCustomerDialog = ref(false)
  const selectedCustomerId = ref(null)
  const customerOptions = ref([])
  const customerLoading = ref(false)
  const sharedCustomerIds = ref(new Set())
  const pendingChild = ref(null)
  const pendingReplace = ref(null)
  const fileInputRef = ref(null)
  const shareToTeam = ref(true) // 【2026-08-08】新上传默认共享给团队(全员可见)，上传人可取消变私有
  // 【2026-08-12】分享开关仅本人可改：新增=本人记录可改；编辑弹窗仅上传人本人可改
  const canToggleShare = computed(() => !pendingReplace.value || pendingReplace.value.userId === userStore.userId)
  const isRecordOwner = (row) => !!row && row.userId === userStore.userId
  const uploading = ref(false)
  const uploadProgress = ref(0)
  const uploadProgressText = ref('')

  // 安装前/中/后 文件槽 + 文本
  const fileSlots = [
    { key: 'pre', label: '安装前' },
    { key: 'mid', label: '安装中' },
    { key: 'post', label: '安装后' },
  ]
  const installFiles = reactive({ pre: [], mid: [], post: [] })
  const trackFile = ref([])
  const MAX_FILES = 5
  const preInstallQuestion = ref('')
  const remarkText = ref('')
  const currentSlot = ref(null)

  // 行业 + 相关产品
  const industryOptions = ref([])
  const selectedIndustry = ref('')
  const relatedProducts = ref('')
  const specificProduct = ref('')
  const productCats = PRODUCT_CATS
  const productCatKeys = PRODUCT_CAT_KEYS
  const relatedCat = ref('')
  const relatedSub = ref('')
  watch([relatedCat, relatedSub], () => { relatedProducts.value = relatedCat.value && relatedSub.value ? relatedCat.value + '-' + relatedSub.value : relatedCat.value || relatedSub.value })

  // 分类 code：子分类 code 为 visit_factory/exchange_tech/plan_tech 等带前缀值，
  // 须上溯到顶层父级（visit/exchange/plan/trial）才能判定品类；无父级（顶层直接上传）用自身 code
  const categoryCodeOf = (cat) => {
    if (!cat) return ''
    let cur = cat
    while (cur.parentId && categoryMap.value[cur.parentId]) cur = categoryMap.value[cur.parentId]
    return cur.categoryCode || cat.categoryCode || ''
  }

  // 品类分支：visit/exchange/plan 单槽模式（现场照片/安装文件 1~5，无跟踪记录）；trial 三槽
  const isSingleSlot = computed(() => ['visit', 'exchange', 'plan'].includes(categoryCodeOf(pendingChild.value)))
  const slotLabel = computed(() => categoryCodeOf(pendingChild.value) === 'plan' ? '方案文件' : '现场照片')
  // 详情字段标签 = 子分类名 + 阶段词
  const detailLabel = computed(() => {
    const code = categoryCodeOf(pendingChild.value)
    const sub = pendingChild.value?.categoryName || ''
    if (code === 'plan') return sub + '文件详情描述'
    if (code === 'visit' || code === 'exchange') return sub + '详情描述'
    return sub + '安装详情'
  })
  // 安装详情区块标题（随阶段变）
  const secTitle = computed(() => {
    const code = categoryCodeOf(pendingChild.value)
    if (code === 'plan') return '方案详情'
    if (code === 'visit' || code === 'exchange') return '参观详情'
    return '安装详情'
  })
  // 备注仅产品试点保留，标签也带子分类名
  const isTrial = computed(() => categoryCodeOf(pendingChild.value).startsWith('trial'))
  const remarkLabel = computed(() => (pendingChild.value?.categoryName || '') + '安装备注')

  const canSubmit = computed(() => {
    if (!selectedCustomerId.value) return false
    if (isSingleSlot.value) return installFiles.pre.length >= 1
    return true
  })

  const selectedCustomerName = computed(() => {
    if (!selectedCustomerId.value) return ''
    const c = customerOptions.value.find(c => c.id === selectedCustomerId.value)
    return c?.customerName || ''
  })

  const parentCategories = computed(() => categories.value.filter(c => c.parentId === 0))
  const catIcons = { visit: Location, exchange: ChatDotSquare, plan: Document, trial: Box }
  const activeParent = computed(() => parentCategories.value.find(c => String(c.id) === activeTab.value))

  // 分类 id → 对象 映射
  const categoryMap = computed(() => {
    const map = {}
    for (const c of categories.value) map[c.id] = c
    return map
  })

  // 支持2级/3级结构的分组
  const groupedChildren = computed(() => {
    const parent = activeParent.value
    if (!parent) return []
    const children = categories.value.filter(c => c.parentId === parent.id)
    return children.map(child => {
      const grandchildren = categories.value.filter(c => c.parentId === child.id)
      return { ...child, items: grandchildren.length > 0 ? grandchildren : null }
    })
  })

  // 记录增强：从分类树计算 大类·子类 和 所属分组
  const enrichedRecords = computed(() => {
    return records.value.map(r => {
      const cat = categoryMap.value[r.categoryId]
      let majorMinor = r.categoryName || '—'
      let tier = ''
      let groupName = ''

      if (cat) {
        const parent = categoryMap.value[cat.parentId]
        if (parent) {
          const grandparent = categoryMap.value[parent.parentId]
          if (grandparent && grandparent.parentId === 0) {
            majorMinor = grandparent.categoryName + ' · ' + parent.categoryName
            groupName = grandparent.categoryName
            tier = cat.categoryName
          } else if (parent.parentId === 0) {
            majorMinor = parent.categoryName + ' · ' + cat.categoryName
            groupName = parent.categoryName
            // 【五轮复检修复】二级结构 majorMinor 尾段已含子类名, tier 置空防 meta 行重复展示
            tier = ''
          }
        }
      }
      return { ...r, majorMinor, tier, groupName }
    })
  })

  // 筛选选项 — 从分类树生成，不是从记录推算
  const filterGroupOptions = computed(() => {
    return parentCategories.value.map(c => c.categoryName)
  })
  const filterTierOptions = computed(() => {
    if (!filterGroup.value) return []
    const group = parentCategories.value.find(c => c.categoryName === filterGroup.value)
    if (!group) return []
    return categories.value.filter(c => c.parentId === group.id).map(c => c.categoryName)
  })

  // 顶层品类 code（用于四区排序）：试点(0)→两谈两做(1)→交流拜访(2)→外出参观(3)，未知(4)
  const orderOfCode = (code) => ({ trial: 0, plan: 1, exchange: 2, visit: 3 }[code] ?? 4)
  const mainOrderOf = (row) => {
    const cat = categoryMap.value[row.categoryId]
    if (!cat) return 4
    let order = orderOfCode(cat.categoryCode)
    if (order === 4 && cat.parentId && categoryMap.value[cat.parentId]) order = orderOfCode(categoryMap.value[cat.parentId].categoryCode)
    return order
  }

  // 过滤后的记录（四区排序，同品类按时间倒序）
  const filteredRecords = computed(() => {
    let list = enrichedRecords.value
    if (searchText.value) {
      const kw = searchText.value.toLowerCase()
      list = list.filter(r => r.fileName?.toLowerCase().includes(kw))
    }
    if (filterCustomer.value) {
      const kw = filterCustomer.value.toLowerCase()
      list = list.filter(r => (r.customerName || '').toLowerCase().includes(kw))
    }
    if (filterGroup.value) list = list.filter(r => r.groupName === filterGroup.value)
    if (filterTier.value) list = list.filter(r => r.tier === filterTier.value)
    return [...list].sort((a, b) => {
      const d = mainOrderOf(a) - mainOrderOf(b)
      if (d !== 0) return d
      return String(b.createdAt || '').localeCompare(String(a.createdAt || ''))
    })
  })

  // 分页切片（filteredRecords 是过滤后的全量，切当前页）
  const pagedRecords = computed(() => {
    const start = (pageNum.value - 1) * pageSize.value
    return filteredRecords.value.slice(start, start + pageSize.value)
  })
  // 筛选条件变化时回到第 1 页
  watch([searchText, filterCustomer, filterGroup, filterTier, activeTab, selectedUser, selectedPeriod], () => { pageNum.value = 1 })

  const scoreColor = (score) => {
    // 【B5 2026-10-08】语义色字面量改主题变量（内联样式支持 CSS 变量），与全站色板同源
    if (score === 0) return '#c0c4cc'
    if (score === 1) return 'var(--el-color-warning)'
    if (score === 2) return 'var(--el-color-success)'
    return 'var(--el-color-danger)'
  }

  const formatDate = (dt) => {
    if (!dt) return '—'
    return String(dt).substring(0, 10)
  }

  // ---- 加载 ----
  // 分类树缓存:不常变数据,避免每次进入页面重复请求(慢网络下可省 400-700ms)
  let categoriesCache = { at: 0, data: null }
  const loadCategories = async () => {
    try {
      let res = null
      if (categoriesCache.data && Date.now() - categoriesCache.at < 600000) {
        res = { data: categoriesCache.data }
      } else {
        const r = await getCategoryTree()
        categoriesCache = { at: Date.now(), data: r.data || [] }
        res = r
      }
      const flat = []
      const flatten = (list) => {
        for (const item of list) {
          flat.push({ ...item, children: undefined })
          if (item.children) flatten(item.children)
        }
      }
      flatten(res.data || [])
      categories.value = flat

      if (parentCategories.value.length > 0 && !activeTab.value) {
        activeTab.value = String(parentCategories.value[0].id)
      }
    } catch (e) {}
  }

  // 用户列表缓存(与分类树同策略,10分钟)
  let usersCache = { at: 0, data: null }
  const loadUsers = async () => {
    try {
      let data = null
      if (usersCache.data && Date.now() - usersCache.at < 600000) {
        data = usersCache.data
      } else {
        const res = await request.get('/incentive/users')
        usersCache = { at: Date.now(), data: res.data || [] }
        data = res.data || []
      }
      userList.value = data
    } catch (e) {}
  }

  // 部门树（范围感知接口：当前用户可见部门树，含子部门）
  const loadDeptTree = async () => {
    try {
      const res = await request.get('/common/scope/depts')
      deptTree.value = res.data || []
    } catch (e) {}
  }

  // 部门变更：人员下拉数据源切换为该部门（含子部门）可见用户；清空部门恢复原数据源
  const onDeptChange = async (deptId) => {
    if (deptId) {
      try {
        const res = await request.get('/common/scope/users', { params: { deptId } })
        userList.value = res.data || []
        // 已选人员不在新范围内时清空，避免列表过滤无结果（watch selectedUser 自动刷新列表）
        if (selectedUser.value && !userList.value.some(u => u.id === selectedUser.value)) {
          selectedUser.value = null
        }
      } catch (e) {}
    } else {
      loadUsers()
    }
  }

  const loadRecords = async () => {
    try {
      const parentId = activeTab.value ? Number(activeTab.value) : null
      // 选「全部」时省略 period 参数（后端仅在周期非空时过滤）
      const res = await getRecords({ userId: selectedUser.value, period: periodQueryValue(selectedPeriod.value), parentCategoryId: parentId })
      records.value = res.data || []
    } catch (e) {}
  }

  const loadIndustries = async () => {
    try {
      const res = await getDictData('industry')
      industryOptions.value = (res.data || []).map(d => d.dictValue)
    } catch (e) {
      industryOptions.value = INDUSTRY_FALLBACK_OPTIONS
    }
  }

  // 激励权限检查短缓存(60秒)
  let permCache = { at: 0, data: null }
  const loadPermission = async () => {
    try {
      let res = null
      if (permCache.data && Date.now() - permCache.at < 60000) {
        res = { data: permCache.data }
      } else {
        const r = await checkIncentivePermission()
        permCache = { at: Date.now(), data: r.data || {} }
        res = r
      }
      if (res.data) {
        canEdit.value = res.data.canEdit || false
        canDelete.value = res.data.canDelete || false
      }
    } catch (e) {}
  }

  // ---- 弹窗重置 ----
  const resetDialogFields = () => {
    // 关闭/重置弹窗时释放未提交文件的预览 blob,避免内存泄漏
    const release = (list) => { for (const f of list) { if (f && f._url) { URL.revokeObjectURL(f._url); f._url = null } } }
    release(installFiles.pre); release(installFiles.mid); release(installFiles.post); release(trackFile.value)
    installFiles.pre = []
    installFiles.mid = []
    installFiles.post = []
    trackFile.value = []
    preInstallQuestion.value = ''
    remarkText.value = ''
    shareToTeam.value = true // 【2026-08-08】新上传默认全员可见，上传人可取消变私有
    selectedIndustry.value = ''
    relatedProducts.value = ''
    specificProduct.value = ''
    relatedCat.value = ''
    relatedSub.value = ''
    previewVisible.value = false // 【2026-08-12】重置时关预览,防 blob revoke 后预览框黑屏
  }
  // 【工作流复检修复 2026-10-10】取消/ESC/右上角关闭只置 false 不走 resetDialogFields，
  // 新选文件的预览 blob 释放被推迟到下次打开——与注释"关闭/重置时释放"不符；关闭即释放
  // （只 revoke blob，不清列表：清列表会连带弹窗动画期间闪空，下次打开 startUpload/startReplace 会重置）
  const releasePreviewBlobs = () => {
    const release = (list) => { for (const f of list) { if (f && f._url) { URL.revokeObjectURL(f._url); f._url = null } } }
    release(installFiles.pre); release(installFiles.mid); release(installFiles.post); release(trackFile.value)
  }
  // 【三轮复检修复】只 revoke 不清列表会被弹窗关闭同帧重渲染的 getObjectURL 重建 _url 抵消
  // (复检员 jsdom 运行级证实: 存活blob数=1)。关闭即清列表——弹窗已隐藏无视觉影响,
  // 重开由 startUpload/startReplace 的 resetDialogFields 重新填充
  watch(showCustomerDialog, (open) => {
    if (!open) {
      releasePreviewBlobs()
      installFiles.pre = []; installFiles.mid = []; installFiles.post = []; trackFile.value = []
      // 【五轮复检修复】预览框渲染在弹窗之外, blob 已 revoke 不关会黑屏(对齐 resetDialogFields 注释意图)
      previewVisible.value = false
    }
  })
  // 【四轮复检修复】路由离开(组件卸载)兜底释放已选文件 blob——原先弹窗开着切路由无人清理
  onUnmounted(() => releasePreviewBlobs())

  // ---- 上传流程 ----
  const startUpload = (child) => {
    pendingChild.value = child
    pendingReplace.value = null
    selectedCustomerId.value = null
    customerOptions.value = []
    resetDialogFields()
    showCustomerDialog.value = true
    loadSharedCustomerIds()
    searchCustomers('')
  }

  // 删除记录
  const handleDeleteRecord = async (row) => {
    try {
      await confirmAction(`确定删除「${row.fileName || '该记录'}」？删除后不可恢复，积分将随之移除。`, '删除激励记录', { confirmText: '确认删除' })
      await deleteRecord(row.id)
      ElMessage.success('删除成功')
      loadRecords()
    } catch (e) {}
  }

  // 【2026-08-08 分享制】开关记录分享：开→全员可见，关→仅本人+管理员+专项授权可见
  const toggleShare = async (row, val) => {
    // 【2026-08-12】分享开关仅本人可改：非本人(含admin/can_edit)直接拒绝
    if (!isRecordOwner(row)) { ElMessage.warning('仅记录本人可修改分享'); return }
    const target = val ? 1 : 0
    try {
      await updateRecord(row.id, { shared: target })
      // 【复检修复】row 是 enrichedRecords computed 展开出的非响应式副本, 直改不触发重渲染
      // (开关回弹旧值); 刷新数据源让 computed 重算——与其他写路径(删除/移除/提交)成功后一致
      loadRecords()
      ElMessage.success(val ? '已分享（全员可见）' : '已取消分享')
    } catch (e) {
      ElMessage.error('操作失败：' + (e.message || '请检查权限'))
    }
  }

  // 追加流程：打开弹窗，保留已有文件，只补传缺失的文件槽
  const startReplace = (row) => {
    pendingChild.value = categoryMap.value[row.categoryId] || null
    pendingReplace.value = row
    selectedCustomerId.value = row.customerId
    resetDialogFields()
    // 已有图片缩略图需要有效 download token：预热拉取；渲染由 thumbsReady(getDownloadToken 为
    // 响应式 ref)自动门控，token 就绪/换新时 watch 清 _thumbFailed 防闩锁(工作流复检修复)
    refreshDownloadToken().catch(() => {})
    // 保留已有文件，标记为已上传（name + id + _existing）
    const parseExisting = (namesStr, idsJson) => {
      if (!namesStr) return []
      // 【复检修复】与 splitNames 的 ','+trim 口径对齐(原 split(', ') 在名字含","时错位);
      // id 解析失败/缺失不再静默——提交时 getKeptIds 会过滤无 id 文件造成静默丢失
      const names = namesStr.split(',').map((s) => s.trim()).filter(Boolean)
      let ids = []
      let parseFailed = false
      try { ids = idsJson ? JSON.parse(idsJson) : [] } catch { ids = []; parseFailed = true }
      if (parseFailed || (names.length && ids.length < names.length)) {
        ElMessage.warning('部分已有文件信息异常（缺少文件ID），请刷新页面后重试')
      }
      return names.map((name, i) => ({ name, id: ids[i] || null, _existing: true }))
    }
    installFiles.pre = parseExisting(row.filePreName, row.filePreId)
    installFiles.mid = parseExisting(row.fileMidName, row.fileMidId)
    installFiles.post = parseExisting(row.filePostName, row.filePostId)
    trackFile.value = parseExisting(row.fileTrackName, row.fileTrackId)
    preInstallQuestion.value = row.preInstallQuestion || ''
    remarkText.value = row.remark || ''
    shareToTeam.value = !!row.shared
    selectedIndustry.value = row.industry || ''
    relatedProducts.value = row.relatedProducts || ''
    specificProduct.value = row.specificProduct || ''
    const _rp = row.relatedProducts || ''
    const _i = _rp.indexOf('-')
    relatedCat.value = _i > 0 ? _rp.slice(0, _i) : ''
    relatedSub.value = _i > 0 ? _rp.slice(_i + 1) : _rp
    if (row.customerId) {
      customerOptions.value = [{ id: row.customerId, customerName: row.customerName, industry: row.industry, _shared: sharedCustomerIds.value.has(row.customerId) }]
    }
    loadSharedCustomerIds()  // 异步加载分享标记，下次可见
    showCustomerDialog.value = true
  }

  const searchCustomers = async (query) => {
    customerLoading.value = true
    try {
      const res = await getCustomerList({ current: 1, size: 200, keyword: query || '' })
      customerOptions.value = (res.data?.records || []).map(c => ({
        ...c,
        _shared: sharedCustomerIds.value.has(c.id)
      }))
    } catch (e) {}
    customerLoading.value = false
  }

  const loadSharedCustomerIds = async () => {
    try {
      const res = await getCustomerList({ scope: 'shared_to_me', size: 500 })
      sharedCustomerIds.value = new Set((res.data?.records || []).map(c => c.id))
    } catch (e) {
      sharedCustomerIds.value = new Set()
    }
  }

  // ---- 文件槽管理 ----
  // 图片扩展名判定统一在 utils/media-type.js
  const isImage = isImageName
  // 已有图片缩略图（工作流复检修复 2026-10-10）：
  // ①thumbsReady 门控——getDownloadToken 内部读的是响应式 ref，token 未就绪时不渲染 <img>，
  //   避免裸请求 401；token 就绪/换新自动重渲染（比 thumbNonce 机制更直接）
  // ②watch 清 _thumbFailed——token 过期中途 401 会置失败标记，token 换新后必须复位，
  //   否则 v-if 恒 false 把 <img> 永久闩死在图标分支（复检证实的闩锁）
  const thumbsReady = computed(() => !!getDownloadToken())
  const clearThumbFailures = () => {
    for (const key of ['pre', 'mid', 'post']) installFiles[key].forEach((f) => { if (f) f._thumbFailed = false })
    trackFile.value.forEach((f) => { if (f) f._thumbFailed = false })
  }
  watch(() => getDownloadToken(), (t) => { if (t) clearThumbFailures() })
  const existingThumb = (file) => {
    if (!file || !file._existing || !file.id || !isImage(file.name)) return ''
    return fileUrl(file.id)
  }
  // 【需求】文件预览 blob URL 缓存:模板渲染时若每次 createObjectURL 会反复创建大 blob 且不释放,
  // 导致上传后 UI 重渲染卡顿/内存泄漏(FEAT 0804 性能修复)。改为首次创建缓存,移除时 revoke。
  const getObjectURL = (file) => {
    if (!file || file._existing) return ''
    if (!file._url) file._url = URL.createObjectURL(file)
    return file._url
  }
  // 上传弹窗卡片预览:新文件(未提交)用本地 objectURL;已提交(_existing)用远端 fileUrl(id);
  // 统一走 FilePreview(图片放大/视频播放/文档下载入口);复用 getObjectURL 的缓存 blob,不新增泄漏
  const previewCardFile = async (file) => {
    if (!file) return
    if (file._existing) {            // 追加/编辑模式下已存在的文件,无本地 File 对象
      if (!file.id) return
      await previewFile(file.id, file.name)
      return
    }
    const url = getObjectURL(file)   // 未提交文件:本地 blob 预览(视频同样适用)
    if (!url) return
    previewUrl.value = url
    previewType.value = fileType(file.name)
    previewName.value = file.name
    previewVisible.value = true
  }
  // 移除槽位文件:先释放预览 blob 再删除
  const removeSlotFile = (slotKey, idx) => {
    const list = slotKey === 'track' ? trackFile.value : installFiles[slotKey]
    const f = list[idx]
    if (f && f._url) { URL.revokeObjectURL(f._url); f._url = null }
    list.splice(idx, 1)
  }

  // 【用户裁决 2026-10-10】移除已有文件（上传错了需要修改）：立即真实生效，
  // 专用接口原子完成 槽位剔除→重算积分→盖修改人章；物理文件保留只解除引用。
  // 与 removeSlotFile（新文件本地剔除）分流，避免"攒到提交才生效"与删空槽假成功。
  const SLOT_LABELS = { pre: '安装前', mid: '安装中', post: '安装后', track: '跟踪记录' }
  // 【四轮复检修复】移除请求在途计数: 提交快照读的是本地列表, 在途移除未落地前提交会把
  // 已移除文件的 id 一起 PUT 回去(后写覆盖让移除复活)
  const removingInFlight = ref(0)
  const removeExistingSlotFile = async (slotKey, idx) => {
    const list = slotKey === 'track' ? trackFile.value : installFiles[slotKey]
    const f = list[idx]
    if (!f || !f._existing || !f.id || !pendingReplace.value) return
    const label = SLOT_LABELS[slotKey] || '槽位'
    const shortName = (f.name || '').substring(0, 24)
    // 【五轮复检修复】自增提到确认弹窗之前: 确认框开着期间提交不再穿透(原计数在 confirm 之后,
    // 弹窗期提交快照仍带在途移除的文件 id, 网络乱序时可复活)
    removingInFlight.value++
    try {
      await confirmAction(
        `从「${label}」移除文件「${shortName}」？移除后本条记录积分将按最新规则重算，原文件仍保留在文件库中可追溯。`,
        '移除文件',
        { confirmText: '移除' }
      )
    } catch { removingInFlight.value--; return }
    try {
      await removeRecordFile(pendingReplace.value.id, { slot: slotKey, fileId: f.id })
      // 【工作流复检修复 2026-10-10】确认弹窗期间用户可再删别的文件，旧下标会剔错元素——
      // await 后按对象身份重新定位再 splice（删除按 fileId 在后端原子完成，此处只对齐本地视图）
      const i = list.indexOf(f)
      if (i >= 0) list.splice(i, 1)
      ElMessage.success('已移除，积分已重算')
      loadRecords()
    } catch (e) {
      ElMessage.error(e.message || '移除失败')
    } finally {
      removingInFlight.value--
    }
  }
  // 统一入口：新文件本地剔除；已有文件走后端原子移除
  const handleRemoveSlotFile = (slotKey, idx) => {
    // 【工作流复检修复 2026-10-10】上传进行中禁止移除：提交流程已捕获待传文件集合，
    // 中途剔除只影响界面不影响快照，落库后"删掉的"文件会复活
    if (uploading.value) { ElMessage.warning('文件上传处理中，请稍后再移除'); return }
    const list = slotKey === 'track' ? trackFile.value : installFiles[slotKey]
    const f = list[idx]
    if (f && f._existing) return removeExistingSlotFile(slotKey, idx)
    removeSlotFile(slotKey, idx)
  }

  // 【五轮复检修复】上传中禁选新文件: 提交快照在提交时读取, 中途新加不进本次上传且关闭弹窗时被静默丢弃
  const pickFile = (slotKey) => {
    if (uploading.value) { ElMessage.warning('文件上传处理中，请等待完成'); return }
    currentSlot.value = slotKey
    fileInputRef.value?.click()
  }

  const pickTrackFile = () => {
    if (uploading.value) { ElMessage.warning('文件上传处理中，请等待完成'); return }
    currentSlot.value = 'track'
    fileInputRef.value?.click()
  }

  const handleFileSelected = (event) => {
    const files = Array.from(event.target.files)
    // 【五轮复检修复】multiple 多选超限不再静默截断——用户会误以为全部选中
    const capNotice = (list, key) => {
      const merged = [...list, ...files]
      if (merged.length > MAX_FILES) {
        ElMessage.warning(`每槽位最多 ${MAX_FILES} 个文件，已保留前 ${MAX_FILES} 个`)
      }
      return merged.slice(0, MAX_FILES)
    }
    if (currentSlot.value === 'track') {
      trackFile.value = capNotice(trackFile.value)
    } else if (currentSlot.value) {
      // 追加到槽位：保留槽内已有文件（含此前已选未上传的新文件），再拼接本次选择，最多 MAX_FILES
      installFiles[currentSlot.value] = capNotice(installFiles[currentSlot.value])
    }
    currentSlot.value = null
    event.target.value = ''
  }

  // ---- 提交 ----
  const totalNewFiles = computed(() => {
    const count = (arr) => arr.filter(f => !f._existing).length
    return count(installFiles.pre) + count(installFiles.mid) + count(installFiles.post) + count(trackFile.value)
  })
  const uploadOne = async (file, idx, total) => {
    uploadProgressText.value = file.name
    const formData = new FormData()
    formData.append('file', file)
    formData.append('scope', 'incentive')
    // 上传进度节流:axios progress 事件高频触发,直接更新组件级 ref 会导致整个大组件
    // (分类/筛选/表格/弹窗)反复重渲染,上传时主线程卡顿(FEAT 0804 性能修复)
    let lastAt = 0
    let lastPct = -1
    // 【工作流复核确认修复】上传放宽超时到 10 分钟: 全局 axios 30s 超时会让大文件(弹窗承诺 500MB)中途必断
    const res = await request.post('/file/upload', formData, {
      timeout: 600000,
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (e) => {
        const filePct = e.total ? Math.round((e.loaded / e.total) * 100) : 0
        const pct = Math.round(((idx + filePct / 100) / total) * 100)
        const now = Date.now()
        // 间隔 >=150ms 或进度变化 >=2% 才更新 UI
        if (pct !== lastPct && (now - lastAt >= 150 || pct - lastPct >= 2)) {
          lastAt = now
          lastPct = pct
          uploadProgress.value = pct
        }
      }
    })
    uploadProgress.value = Math.round(((idx + 1) / total) * 100)
    return res.code === 200 ? (res.data?.id ?? null) : null
  }

  // 提交：每个槽位支持多文件上传（最多5个），ID序列化为JSON数组
  const confirmUpload = async () => {
    // 【四轮复检修复】移除在途禁止提交: 快照会带上正在被移除的文件 id
    if (removingInFlight.value > 0) { ElMessage.warning('文件移除处理中，请稍候再提交'); return }
    if (!canSubmit.value) return
    uploading.value = true
    uploadProgress.value = 0
    try {
      // 批量上传所有新文件，收集每个槽位的文件ID列表
      const allNew = totalNewFiles.value
      let uploaded = 0
      const uploadSlot = async (files) => {
        const newFiles = files.filter(f => !f._existing)
        if (!newFiles.length) return []
        const ids = []
        for (const f of newFiles) {
          const id = await uploadOne(f, uploaded, allNew)
          if (id === null) throw new Error('文件上传失败')
          ids.push(id)
          uploaded++
        }
        return ids
      }
      const preIds = await uploadSlot(installFiles.pre)
      const midIds = await uploadSlot(installFiles.mid)
      const postIds = await uploadSlot(installFiles.post)
      const trackIds = await uploadSlot(trackFile.value)

      // 序列化为JSON数组
      const toJson = (ids) => ids.length ? `[${ids.join(',')}]` : null

      let finalPreId, finalMidId, finalPostId, finalTrackId

      if (pendingReplace.value) {
        // 追加模式：只保留 UI 中未被删除的已有文件 + 新上传文件
        const old = pendingReplace.value
        const getKeptIds = (slotFiles) => slotFiles.filter(f => f._existing && f.id).map(f => f.id)

        const preOldIds = getKeptIds(installFiles.pre)
        finalPreId = [...preOldIds, ...preIds].length ? `[${[...preOldIds, ...preIds].join(',')}]` : null

        const midOldIds = getKeptIds(installFiles.mid)
        finalMidId = [...midOldIds, ...midIds].length ? `[${[...midOldIds, ...midIds].join(',')}]` : null

        const postOldIds = getKeptIds(installFiles.post)
        finalPostId = [...postOldIds, ...postIds].length ? `[${[...postOldIds, ...postIds].join(',')}]` : null

        const trackOldIds = getKeptIds(trackFile.value)
        finalTrackId = [...trackOldIds, ...trackIds].length ? `[${[...trackOldIds, ...trackIds].join(',')}]` : null

        // 单槽品类：保留旧记录的中/后/跟踪槽文件（不提供编辑入口，避免历史数据丢失）
        if (isSingleSlot.value) {
          const oldRow = pendingReplace.value
          finalMidId = oldRow.fileMidId || null
          finalPostId = oldRow.filePostId || null
          finalTrackId = oldRow.fileTrackId || null
        }

        // 【2026-08-12 审计】编辑改走 updateRecord(PUT)：保留原记录 id/userId/createdAt，
        // 初始上传人不变、上传时间不被重置；categoryId/period 保留原记录值(不随编辑覆盖)。
        const editPayload = {
          customerId: selectedCustomerId.value,
          industry: selectedIndustry.value || null,
          relatedProducts: relatedProducts.value || null,
          specificProduct: specificProduct.value || null,
          preInstallQuestion: preInstallQuestion.value || null,
          filePreId: finalPreId,
          fileMidId: finalMidId,
          filePostId: finalPostId,
          fileTrackId: finalTrackId,
          remark: remarkText.value || null,
        }
        // 【2026-08-12】分享仅本人可改：编辑他人记录时不携带 shared（后端也会忽略），保持原值
        if (canToggleShare.value) editPayload.shared = shareToTeam.value ? 1 : 0
        await updateRecord(pendingReplace.value.id, editPayload)
      } else {
        await addRecord({
          categoryId: pendingChild.value.id,
          customerId: selectedCustomerId.value,
          // 筛选选「全部」时按当年「全年」落库，避免写入空周期（历史数据同为"全年"口径）
          period: selectedPeriod.value || defaultRecordPeriod(),
          industry: selectedIndustry.value || null,
          relatedProducts: relatedProducts.value || null,
          specificProduct: specificProduct.value || null,
          preInstallQuestion: preInstallQuestion.value || null,
          filePreId: toJson(preIds),
          // 单槽品类（visit/exchange/plan）：不提交中/后/跟踪槽
          fileMidId: isSingleSlot.value ? null : toJson(midIds),
          filePostId: isSingleSlot.value ? null : toJson(postIds),
          fileTrackId: isSingleSlot.value ? null : toJson(trackIds),
          remark: remarkText.value || null,
          shared: shareToTeam.value ? 1 : 0
        })
      }
      ElMessage.success(pendingReplace.value ? '追加成功' : '提交成功')
      showCustomerDialog.value = false
      pendingReplace.value = null
      loadRecords()
    } catch (e) {
      ElMessage.error(e.message || '上传失败')
    } finally {
      uploading.value = false
    }
  }

  watch(activeTab, () => { filterGroup.value = ''; filterTier.value = ''; loadRecords() })
  watch(selectedPeriod, loadRecords)
  watch(selectedUser, loadRecords)
  watch(selectedCustomerId, (newId) => {
    if (!newId) return
    const c = customerOptions.value.find(c => c.id === newId)
    if (c?.industry) selectedIndustry.value = c.industry
  })

  return {
    userStore, activeTab, selectedUser, userList, selectedDeptId, deptTree,
    periodOptions, selectedPeriod, categories, records, canEdit, canDelete,
    pageNum, pageSize, pagedRecords, filteredRecords,
    previewVisible, previewUrl, previewType, previewName, fileUrl,
    parseFids, splitNames, fileType, previewFile, previewCardFile,
    searchText, filterCustomer, filterGroup, filterTier, filterGroupOptions, filterTierOptions,
    showCustomerDialog, selectedCustomerId, customerOptions, customerLoading, sharedCustomerIds,
    pendingChild, pendingReplace, fileInputRef, shareToTeam,
    uploading, uploadProgress, uploadProgressText, totalNewFiles,
    fileSlots, installFiles, trackFile, MAX_FILES, preInstallQuestion, remarkText,
    industryOptions, selectedIndustry, relatedProducts, specificProduct,
    productCats, productCatKeys, relatedCat, relatedSub,
    categoryCodeOf, isSingleSlot, slotLabel, detailLabel, secTitle, isTrial, remarkLabel,
    canSubmit, selectedCustomerName, parentCategories, catIcons, activeParent,
    categoryMap, groupedChildren, enrichedRecords, scoreColor, formatDate,
    loadCategories, loadUsers, loadDeptTree, onDeptChange, loadRecords,
    loadIndustries, loadPermission,
    resetDialogFields, startUpload, handleDeleteRecord, toggleShare, startReplace,
    canToggleShare, isRecordOwner,
    searchCustomers, loadSharedCustomerIds,
    isImage, existingThumb, thumbsReady, getObjectURL, removeSlotFile, handleRemoveSlotFile, pickFile, pickTrackFile, handleFileSelected,
    uploadOne, confirmUpload,
  }
}
