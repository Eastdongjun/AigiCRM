// 激励上传/管理页共享态（C4 拆组件 2026-08-09）
// 页面级 provide/inject（per-route）：incentive/index.vue 实例化一次并 provide，
// 筛选条/子分类面板/文件表格/上传弹窗/文件预览 inject 同一上下文。
// 共享内容：分类树/记录/权限 + 筛选与分页 + 上传弹窗全部状态(客户/文件槽/进度/分享) + 上传/追加/删除动作。
import { ref, reactive, computed, watch, inject } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmAction } from '@/utils/confirm'
import { Box, Location, ChatDotSquare, Document } from '@element-plus/icons-vue'
import { getCategoryTree, getRecords, addRecord, deleteRecord, updateRecord, checkIncentivePermission } from '@/api/incentive'
import { getCustomerList } from '@/api/customer'
import { getDictData } from '@/api/system'
import { useUserStore } from '@/store/user'
import request from '@/utils/request'
import { getDownloadToken, refreshDownloadToken } from '@/utils/token'

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
  const periodOptions = (() => { const l = []; for (let y = 2030; y >= 2025; y--) { l.push(y + '下半年', y + '上半年') } return l })()
  const selectedPeriod = ref('2026上半年')
  const categories = ref([])
  const records = ref([])

  // 权限
  const canEdit = ref(false)
  const canDelete = ref(false)
  // 右侧文件列表分页（前端切片）
  const pageNum = ref(1)
  const pageSize = ref(30)

  // ===== 安装文件预览 =====
  const previewVisible = ref(false), previewUrl = ref(''), previewType = ref('image'), previewName = ref('')
  // 【修复 2026-08-14】下载 token 未就绪时不拼 token=（避免必然 403 的空 token 请求）
  const fileUrl = (id) => { const t = getDownloadToken(); return t ? '/api/file/download/' + id + '?token=' + t : '/api/file/download/' + id }
  const parseFids = (v) => { if (!v) return []; if (typeof v === 'number') return [v]; try { const a = JSON.parse(v); return Array.isArray(a) ? a.map(x => Number(x)) : [v] } catch { return String(v).split(',').map(s => s.trim()).filter(Boolean) } }
  const splitNames = (n) => { if (!n) return []; return String(n).split(',').map(s => s.trim()).filter(Boolean) }
  const fileType = (n) => { if (!n) return 'doc'; if (/\.(jpg|jpeg|png|gif|webp|bmp)$/i.test(n)) return 'image'; if (/\.(mp4|mov|avi|webm)$/i.test(n)) return 'video'; return 'doc' }
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
  const productCats = { '涂层类': ['防腐', '加固', '钢结构', '煤气管道', '其他'], '防滑板类': ['公共区', '装置区'], '机封类': ['水泵', '渣泵', '介质泵'], '密封类': ['静密封', '动密封', '橡胶密封'], '润滑类': ['润滑油', '润滑脂', '润滑剂'] }
  const productCatKeys = Object.keys(productCats)
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
            tier = cat.categoryName
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
    if (score === 0) return '#c0c4cc'
    if (score === 1) return '#e6a23c'
    if (score === 2) return '#67c23a'
    return '#f56c6c'
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
      const res = await getRecords({ userId: selectedUser.value, period: selectedPeriod.value, parentCategoryId: parentId })
      records.value = res.data || []
    } catch (e) {}
  }

  const loadIndustries = async () => {
    try {
      const res = await getDictData('industry')
      industryOptions.value = (res.data || []).map(d => d.dictValue)
    } catch (e) {
      industryOptions.value = ['煤矿', '化工', '电力', '洗煤厂', '有色', '其他']
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
      row.shared = target
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
    // 保留已有文件，标记为已上传（name + id + _existing）
    const parseExisting = (namesStr, idsJson) => {
      if (!namesStr) return []
      const names = namesStr.split(', ')
      let ids = []
      try { ids = idsJson ? JSON.parse(idsJson) : [] } catch { ids = [] }
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
  const isImage = (name) => /\.(jpg|jpeg|png|gif|webp|bmp)$/i.test(name || '')
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

  const pickFile = (slotKey) => {
    currentSlot.value = slotKey
    fileInputRef.value?.click()
  }

  const pickTrackFile = () => {
    currentSlot.value = 'track'
    fileInputRef.value?.click()
  }

  const handleFileSelected = (event) => {
    const files = Array.from(event.target.files)
    if (currentSlot.value === 'track') {
      trackFile.value = [...trackFile.value, ...files].slice(0, MAX_FILES)
    } else if (currentSlot.value) {
      // 追加到槽位：保留槽内已有文件（含此前已选未上传的新文件），再拼接本次选择，最多 MAX_FILES
      installFiles[currentSlot.value] = [...installFiles[currentSlot.value], ...files].slice(0, MAX_FILES)
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
    const res = await request.post('/file/upload', formData, {
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
          period: selectedPeriod.value,
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
    isImage, getObjectURL, removeSlotFile, pickFile, pickTrackFile, handleFileSelected,
    uploadOne, confirmUpload,
  }
}
