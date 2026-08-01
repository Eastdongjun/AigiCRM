<template>
  <div class="incentive-page">
    <!-- 分类Tab -->
    <div class="cat-tabs-bar">
      <div class="cat-tabs">
        <div v-for="cat in parentCategories" :key="cat.id" class="cat-tab" :class="{ active: activeTab === String(cat.id) }" @click="activeTab = String(cat.id)">
          <span class="cat-tab-icon"><el-icon><component :is="catIcons[cat.categoryCode] || Box" /></el-icon></span>
          <span class="cat-tab-name">{{ cat.categoryName }}</span>
          <span class="cat-tab-badge">{{ cat._recordCount || 0 }}</span>
        </div>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-strip">
      <div class="filter-left">
        <div class="filter-group">
          <span class="filter-label">人员</span>
          <el-select v-model="selectedUser" size="default" style="width:130px" clearable placeholder="全部" @change="selectedUser=$event||null">
            <el-option v-for="u in userList" :key="u.id" :label="u.realName" :value="u.id" />
          </el-select>
        </div>
        <div class="filter-group">
          <span class="filter-label">周期</span>
          <el-select v-model="selectedPeriod" size="default" style="width:120px">
            <el-option v-for="p in periodOptions" :key="p" :label="p" :value="p" />
          </el-select>
        </div>
        <div class="filter-divider"></div>
        <el-input v-model="searchText" placeholder="搜索文件名..." clearable size="default" style="width:160px" :prefix-icon="Search" />
        <el-input v-model="filterCustomer" placeholder="客户名称" clearable size="default" style="width:140px" />
      </div>
      <div class="filter-right">
        <span class="file-count"><el-icon><Folder /></el-icon> {{ filteredRecords.length }} 个文件</span>
      </div>
    </div>

    <!-- 框3: 左右布局 -->
    <div class="main-layout" v-if="groupedChildren.length">
      <!-- 左侧：子分类列表 -->
      <div class="left-panel">
        <div class="sub-hint">
          <el-tag type="danger" size="small" effect="plain">{{ activeParent?.categoryName }}</el-tag>
          <span>— 选择子项上传文件</span>
        </div>

        <div v-for="group in groupedChildren" :key="group.id" class="sub-group">
          <template v-if="group.items">
            <div class="group-header">{{ group.categoryName }}</div>
            <div v-for="item in group.items" :key="item.id" class="sub-item">
              <span class="sub-name">{{ item.categoryName }}</span>
              <div class="sub-actions">
                <span class="score-badge" :class="'score-' + Math.min(item.score, 3)">{{ item.score }}分</span>
                <el-button size="small" plain :icon="Upload" @click="startUpload(item)">上传文件</el-button>
              </div>
            </div>
          </template>
          <template v-else>
            <div class="sub-item flat">
              <span class="sub-name">{{ group.categoryName }}</span>
              <div class="sub-actions">
                <span class="score-badge" :class="'score-' + Math.min(group.score, 3)">{{ group.score }}分</span>
                <el-button size="small" plain :icon="Upload" @click="startUpload(group)">上传文件</el-button>
              </div>
            </div>
          </template>
        </div>
      </div>

      <!-- 右侧：已上传文件表格 -->
      <div class="right-panel">
        <el-table :data="filteredRecords" stripe style="width:100%" empty-text="暂无已上传文件" size="small" @row-click="startReplace" :row-style="{cursor:'pointer'}">
          <el-table-column label="大类·子类" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.majorMinor }}</template>
          </el-table-column>
          <el-table-column prop="industry" label="行业" min-width="70">
            <template #default="{ row }">{{ row.industry || '—' }}</template>
          </el-table-column>
          <el-table-column prop="relatedProducts" label="相关产品" min-width="110" show-overflow-tooltip>
            <template #default="{ row }">{{ row.relatedProducts || '—' }}</template>
          </el-table-column>
          <el-table-column label="分" min-width="45" align="center">
            <template #default="{ row }">
              <span :style="{ color: scoreColor(row.score), fontWeight: 600 }">{{ row.score }}</span>
            </template>
          </el-table-column>
          <el-table-column label="安装文件" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="install-files">
                <span v-if="row.filePreName" class="file-tag"><b>前</b>{{ row.filePreName }}</span>
                <span v-if="row.fileMidName" class="file-tag"><b>中</b>{{ row.fileMidName }}</span>
                <span v-if="row.filePostName" class="file-tag"><b>后</b>{{ row.filePostName }}</span>
                <span v-if="!row.filePreName && !row.fileMidName && !row.filePostName" class="file-cell">
                  <el-icon class="file-icon"><Document /></el-icon>{{ row.fileName || '—' }}
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="客户" min-width="100" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="customer-link">{{ row.customerName || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="共享" min-width="55" align="center">
            <template #default="{ row }">
              <span v-if="row.shared" class="shared-yes">● 共享</span>
              <span v-else class="shared-no">—</span>
            </template>
          </el-table-column>
          <el-table-column label="上传时间" min-width="90">
            <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="userName" label="操作人" min-width="65" />
          <el-table-column label="操作" width="110" fixed="right" align="center">
            <template #default="{ row }">
              <el-button v-if="canEdit" link type="primary" size="small" @click="startReplace(row)">追加</el-button>
              <el-button v-if="canDelete" link type="danger" size="small" @click="handleDeleteRecord(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 关联客户 + 安装文件 弹窗 -->
    <el-dialog v-model="showCustomerDialog" width="780px" top="2vh" :close-on-click-modal="false" class="upload-dlg">
      <template #header>
        <div class="dlg-header">
          <div class="dlg-header-left">
            <span class="dlg-title">{{ pendingReplace ? '追加文件' : '上传激励文件' }}</span>
            <el-tag v-if="pendingChild" size="small" effect="dark" type="warning">{{ pendingChild.categoryName }}</el-tag>
          </div>
        </div>
      </template>

      <div class="dlg-body">
        <!-- 基本信息 -->
        <div class="dlg-section">
          <div class="dlg-sec-head">
            <span class="dlg-sec-icon" style="background:#ecf5ff;color:#409eff"><el-icon><User /></el-icon></span>
            <span class="dlg-sec-title">基本信息</span>
          </div>
          <div class="dlg-row cols-3-2">
            <div class="dlg-field">
              <label>关联客户 <i>*</i></label>
              <el-select v-model="selectedCustomerId" filterable remote :remote-method="searchCustomers" placeholder="搜索客户名称..." :loading="customerLoading" size="large">
                <el-option v-for="c in customerOptions" :key="c.id" :label="c.customerName" :value="c.id">
                  <span>{{ c.customerName }}<el-tag v-if="c._shared" size="small" type="warning" style="margin-left:6px">分享</el-tag></span>
                </el-option>
              </el-select>
              <div v-if="selectedCustomerName" class="dlg-confirm">✅ {{ selectedCustomerName }}</div>
            </div>
            <div class="dlg-field"><label>行业</label>
              <el-select v-model="selectedIndustry" placeholder="选择" clearable size="large"><el-option v-for="i in industryOptions" :key="i" :label="i" :value="i" /></el-select>
            </div>
          </div>
          <div class="dlg-field"><label>相关产品</label><el-input v-model="relatedProducts" placeholder="填写产品名称" size="large" /></div>
        </div>

        <!-- 安装详情 -->
        <div class="dlg-section">
          <div class="dlg-sec-head">
            <span class="dlg-sec-icon" style="background:#fef0f0;color:#f56c6c"><el-icon><Edit /></el-icon></span>
            <span class="dlg-sec-title">安装详情</span>
          </div>
          <div class="dlg-field"><label>安装前问题</label><el-input v-model="preInstallQuestion" type="textarea" :rows="2" placeholder="描述安装前存在的问题..." /></div>
          <div class="dlg-field"><label>安装备注</label><el-input v-model="remarkText" type="textarea" :rows="2" placeholder="安装备注..." /></div>
        </div>

        <!-- 安装文件 -->
        <div class="dlg-section highlight">
          <div class="dlg-sec-head">
            <span class="dlg-sec-icon" style="background:#f0f9eb;color:#67c23a"><el-icon><Picture /></el-icon></span>
            <span class="dlg-sec-title">安装文件</span>
            <span class="dlg-sec-tip">每槽位最多 5 个文件</span>
          </div>
          <div class="file-slots">
            <div v-for="slot in fileSlots" :key="slot.key" class="file-slot">
              <div class="file-slot-head">
                <span class="file-slot-dot" :class="'dot-'+slot.key"></span>
                <span class="file-slot-label">{{ slot.label }}</span>
                <span class="file-slot-count">{{ installFiles[slot.key].length }}/{{ MAX_FILES }}</span>
              </div>
              <div class="file-slot-cards">
                <div v-for="(file, idx) in installFiles[slot.key]" :key="idx" class="file-card has" @click.stop>
                  <img v-if="!file._existing && isImage(file.name)" :src="getObjectURL(file)" class="file-card-img" />
                  <div v-else class="file-card-icon">
                    <el-icon :size="file._existing?20:24"><Document /></el-icon>
                  </div>
                  <span class="file-card-del" @click.stop="installFiles[slot.key].splice(idx,1)"><el-icon><Close /></el-icon></span>
                  <span class="file-card-name">{{ file.name?.substring(0, 8) }}{{ file.name?.length > 8 ? '…' : '' }}</span>
                </div>
                <div v-if="installFiles[slot.key].length < MAX_FILES" class="file-card add" @click="pickFile(slot.key)">
                  <el-icon :size="28"><Plus /></el-icon>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 跟踪记录 -->
        <div class="dlg-section">
          <div class="dlg-sec-head">
            <span class="dlg-sec-icon" style="background:#f5f0ff;color:#9c27b0"><el-icon><Notebook /></el-icon></span>
            <span class="dlg-sec-title">跟踪记录</span>
            <span class="dlg-sec-tip">不加分</span>
          </div>
          <div class="file-slot-cards">
            <div v-for="(file, idx) in trackFile" :key="idx" class="file-card has" @click.stop>
              <img v-if="!file._existing && isImage(file.name)" :src="getObjectURL(file)" class="file-card-img" />
              <div v-else class="file-card-icon"><el-icon :size="20"><Document /></el-icon></div>
              <span class="file-card-del" @click.stop="trackFile.splice(idx,1)"><el-icon><Close /></el-icon></span>
              <span class="file-card-name">{{ file.name?.substring(0, 8) }}{{ file.name?.length > 8 ? '…' : '' }}</span>
            </div>
            <div v-if="trackFile.length < MAX_FILES" class="file-card add" @click="pickTrackFile()">
              <el-icon :size="28"><Plus /></el-icon>
              <span style="font-size:11px;color:#909399;margin-top:4px">选择文件</span>
            </div>
          </div>
        </div>

        <!-- 共享 + 提示 -->
        <div class="dlg-section last">
          <div class="dlg-row">
            <div style="display:flex;align-items:center;gap:10px">
              <label style="font-size:13px;font-weight:600;color:#4e5969">共享给团队</label>
              <el-switch v-model="shareToTeam" />
              <span v-if="shareToTeam" class="shared-on">● 已共享</span>
            </div>
            <div class="dlg-tip">
              <el-icon><InfoFilled /></el-icon> 单文件最大 100MB，支持图片、PDF、Word、Excel、压缩包等格式
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="dlg-footer">
          <div v-if="uploading && totalNewFiles > 0" class="upload-progress">
            <div class="progress-bar"><div class="progress-fill" :style="{width:uploadProgress+'%'}"></div></div>
            <div class="progress-text">{{ uploadProgressText }} — {{ uploadProgress }}%</div>
          </div>
          <div class="dlg-footer-btns">
            <el-button size="large" @click="showCustomerDialog=false">取消</el-button>
            <el-button type="primary" size="large" :loading="uploading" :disabled="!canSubmit" @click="confirmUpload">
              {{ pendingReplace?'追加提交':'上传并提交' }}
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 隐藏的文件上传 (用opacity而非display:none,避免浏览器安全限制) -->
    <input
      ref="fileInputRef"
      type="file"
      multiple
      style="position:absolute;width:1px;height:1px;opacity:0;overflow:hidden"
      @change="handleFileSelected"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Search, Refresh, Delete, Plus, Box, Location, ChatDotSquare, Document, Folder } from '@element-plus/icons-vue'
import { getCategoryTree, getRecords, addRecord, deleteRecord, checkIncentivePermission } from '@/api/incentive'
import { getCustomerList } from '@/api/customer'
import { getDictData } from '@/api/system'
import { useUserStore } from '@/store/user'
import request from '@/utils/request'

const userStore = useUserStore()
const activeTab = ref('')
const selectedUser = ref(null)
const userList = ref([])
const periodOptions = (() => { const l = []; for (let y = 2030; y >= 2025; y--) { l.push(y + '下半年', y + '上半年') } return l })()
const selectedPeriod = ref('2026上半年')
const categories = ref([])
const records = ref([])

// 权限
const canEdit = ref(false)
const canDelete = ref(false)

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
const shareToTeam = ref(false)
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

const canSubmit = computed(() => !!selectedCustomerId.value)

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
          // 三级：grandparent(tab) · parent(组) — cat(子类)
          majorMinor = grandparent.categoryName + ' · ' + parent.categoryName
          groupName = grandparent.categoryName
          tier = cat.categoryName
        } else if (parent.parentId === 0) {
          // 二级：parent(tab) · cat(子类)
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

// 过滤后的记录
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
  return list
})

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

const loadCategories = async () => {
  try {
    const res = await getCategoryTree()
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

const loadUsers = async () => {
  try { const res = await request.get('/incentive/users'); userList.value = res.data || [] } catch(e) {}
}

const loadRecords = async () => {
  try {
    const parentId = activeTab.value ? Number(activeTab.value) : null
    const res = await getRecords({ userId: selectedUser.value, period: selectedPeriod.value, parentCategoryId: parentId })
    records.value = res.data || []
  } catch (e) {}
}

const resetDialogFields = () => {
  installFiles.pre = []
  installFiles.mid = []
  installFiles.post = []
  trackFile.value = []
  preInstallQuestion.value = ''
  remarkText.value = ''
  shareToTeam.value = false
  selectedIndustry.value = ''
  relatedProducts.value = ''
}

// 上传流程
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
    await ElMessageBox.confirm(`确定删除「${row.fileName || '该记录'}」？`, '删除确认', { type: 'warning' })
    await deleteRecord(row.id)
    ElMessage.success('删除成功')
    loadRecords()
  } catch (e) {}
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
    const res = await getCustomerList({ scope: 'shared_to_me', size: 9999 })
    sharedCustomerIds.value = new Set((res.data?.records || []).map(c => c.id))
  } catch (e) {
    sharedCustomerIds.value = new Set()
  }
}

// 选择某个安装文件槽的文件
const isImage = (name) => /\.(jpg|jpeg|png|gif|webp|bmp)$/i.test(name||'')
const getObjectURL = (file) => (file && !file._existing) ? URL.createObjectURL(file) : (file?._existing ? '' : '')

const pickFile = (slotKey) => {
  console.log('[pickFile] slot:', slotKey, 'fileInputRef:', fileInputRef.value)
  currentSlot.value = slotKey
  fileInputRef.value?.click()
}

const pickTrackFile = () => {
  console.log('[pickTrackFile] fileInputRef:', fileInputRef.value)
  currentSlot.value = 'track'
  fileInputRef.value?.click()
}

const handleFileSelected = (event) => {
  const files = Array.from(event.target.files)
  if (currentSlot.value === 'track') {
    trackFile.value = [...trackFile.value, ...files].slice(0, MAX_FILES)
  } else if (currentSlot.value) {
    const existing = installFiles[currentSlot.value].filter(f => f._existing)
    installFiles[currentSlot.value] = [...existing, ...files].slice(0, MAX_FILES)
  }
  currentSlot.value = null
  event.target.value = ''
}

// 上传单个文件，返回 fileId
const totalNewFiles = computed(() => {
  const count = (arr) => arr.filter(f => !f._existing).length
  return count(installFiles.pre) + count(installFiles.mid) + count(installFiles.post) + count(trackFile.value)
})
const uploadOne = async (file, idx, total) => {
  uploadProgressText.value = file.name
  const formData = new FormData()
  formData.append('file', file)
  formData.append('scope', 'incentive')
  const res = await request.post('/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (e) => {
      const filePct = e.total ? Math.round((e.loaded / e.total) * 100) : 0
      uploadProgress.value = Math.round(((idx + filePct / 100) / total) * 100)
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

      // 用 uploadSlot 统一上传（含进度条）
      const preNewIds = await uploadSlot(installFiles.pre)
      const midNewIds = await uploadSlot(installFiles.mid)
      const postNewIds = await uploadSlot(installFiles.post)
      const trackNewIds = await uploadSlot(trackFile.value)

      const preOldIds = getKeptIds(installFiles.pre)
      finalPreId = [...preOldIds, ...preNewIds].length ? `[${[...preOldIds, ...preNewIds].join(',')}]` : null

      const midOldIds = getKeptIds(installFiles.mid)
      finalMidId = [...midOldIds, ...midNewIds].length ? `[${[...midOldIds, ...midNewIds].join(',')}]` : null

      const postOldIds = getKeptIds(installFiles.post)
      finalPostId = [...postOldIds, ...postNewIds].length ? `[${[...postOldIds, ...postNewIds].join(',')}]` : null

      const trackOldIds = getKeptIds(trackFile.value)
      finalTrackId = [...trackOldIds, ...trackNewIds].length ? `[${[...trackOldIds, ...trackNewIds].join(',')}]` : null

      await deleteRecord(pendingReplace.value.id)
      await addRecord({
        categoryId: pendingChild.value.id,
        customerId: selectedCustomerId.value,
        period: selectedPeriod.value,
        industry: selectedIndustry.value || null,
        relatedProducts: relatedProducts.value || null,
        preInstallQuestion: preInstallQuestion.value || null,
        filePreId: finalPreId,
        fileMidId: finalMidId,
        filePostId: finalPostId,
        fileTrackId: finalTrackId,
        remark: remarkText.value || null,
        shared: shareToTeam.value ? 1 : 0
      })
    } else {
      await addRecord({
        categoryId: pendingChild.value.id,
        customerId: selectedCustomerId.value,
        period: selectedPeriod.value,
        industry: selectedIndustry.value || null,
        relatedProducts: relatedProducts.value || null,
        preInstallQuestion: preInstallQuestion.value || null,
        filePreId: toJson(preIds),
        fileMidId: toJson(midIds),
        filePostId: toJson(postIds),
        fileTrackId: toJson(trackIds),
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

const loadIndustries = async () => {
  try {
    const res = await getDictData('industry')
    industryOptions.value = (res.data || []).map(d => d.dictValue)
  } catch (e) {
    industryOptions.value = ['煤矿', '化工', '电力', '洗煤厂', '有色', '其他']
  }
}

const loadPermission = async () => {
  try {
    const res = await checkIncentivePermission()
    if (res.data) {
      canEdit.value = res.data.canEdit || false
      canDelete.value = res.data.canDelete || false
    }
  } catch (e) {}
}

onMounted(() => {
  loadCategories()
  loadIndustries()
  loadPermission()
  loadUsers()
})
</script>

<style scoped>
.incentive-page { padding: 0; height: calc(100vh - 80px); display: flex; flex-direction: column; gap: 10px }

/* Cat Tabs */
.cat-tabs-bar { flex-shrink:0 }
.cat-tabs { display:flex; gap:0; background:#f2f4f7; border-radius:12px; padding:4px; overflow:hidden }
.cat-tab { flex:1; display:flex; align-items:center; justify-content:center; gap:8px; padding:12px 8px; font-size:14px; color:#606266; cursor:pointer; border-radius:10px; transition:all .25s; font-weight:500; position:relative }
.cat-tab:hover { background:rgba(255,255,255,.6) }
.cat-tab.active { font-weight:700; color:#fff; box-shadow:0 2px 8px rgba(0,0,0,.12) }
.cat-tab:nth-child(1).active { background:#f59e0b }
.cat-tab:nth-child(2).active { background:#3b82f6 }
.cat-tab:nth-child(3).active { background:#8b5cf6 }
.cat-tab:nth-child(4).active { background:#10b981 }
.cat-tab-icon { font-size:16px; display:flex }
.cat-tab-badge { font-size:11px; padding:1px 7px; border-radius:10px; background:rgba(0,0,0,.08); font-weight:600 }
.cat-tab.active .cat-tab-badge { background:rgba(255,255,255,.25) }

/* Filter */
.filter-strip { display:flex; align-items:center; justify-content:space-between; background:#fff; border:1px solid #e8ecf1; border-radius:10px; padding:10px 16px; flex-shrink:0 }
.filter-left { display:flex; align-items:center; gap:8px; flex-wrap:wrap }
.filter-group { display:flex; align-items:center; gap:6px }
.filter-label { font-size:12px; font-weight:600; color:#4e5969; white-space:nowrap }
.filter-divider { width:1px; height:22px; background:#e8ecf1; margin:0 4px }
.filter-right { display:flex; align-items:center }
.file-count { display:flex; align-items:center; gap:4px; font-size:13px; font-weight:600; color:#409eff; white-space:nowrap }

.main-layout { display:flex; border:1px solid #e8ecf1; border-radius:12px; overflow:hidden; flex:1; min-height:0; background:#fff }
.left-panel { flex:0 0 240px; background:#fafbfd; padding:16px; border-right:1px solid #f0f2f5; overflow-y:auto }
.sub-hint { display:flex; align-items:center; gap:8px; margin-bottom:12px; color:#909399; font-size:13px }
.sub-group { margin-bottom:4px }
.group-header { font-size:13px; font-weight:700; color:#1f2329; padding:10px 0 6px 12px; border-left:3px solid #409eff; margin-top:8px }
.sub-item { display:flex; justify-content:space-between; align-items:center; padding:10px 0 10px 12px; border-bottom:1px solid #f5f5f5; transition:background .15s }
.sub-item:hover { background:#f0f4ff }
.sub-item.flat { padding-left:0 }
.sub-name { font-size:13px; color:#333; font-weight:500 }
.sub-actions { display:flex; align-items:center; gap:10px }
.score-badge { display:inline-block; min-width:28px; text-align:center; padding:2px 8px; border-radius:10px; font-size:12px; font-weight:600 }
.score-0 { color:#bbb; background:#f5f5f5 } .score-1 { color:#e6a23c; background:#fff7ed } .score-2 { color:#67c23a; background:#f0f9eb } .score-3 { color:#f56c6c; background:#fef0f0 }

.right-panel { flex:1; background:#fff; padding:14px 18px; overflow-y:auto }

/* 表格内样式 */
.file-cell { display: inline-flex; align-items: center; gap: 4px; }
.file-icon { color: #909399; font-size: 14px; flex-shrink: 0; }
.customer-link { color: #409eff; cursor: pointer; }
.customer-link:hover { text-decoration: underline; }
.shared-yes { color: #67c23a; font-size: 12px; font-weight: 500; }
.shared-no { color: #c0c4cc; }

/* === Dialog Redesign === */
.dlg-header { display:flex; align-items:center; justify-content:space-between }
.dlg-header-left { display:flex; align-items:center; gap:10px }
.dlg-title { font-size:18px; font-weight:700; color:#1f2329 }
.dlg-body { max-height:62vh; overflow-y:auto; padding-right:4px }

.dlg-section { background:#fff; border:1px solid #e8ecf1; border-radius:12px; padding:18px 20px; margin-bottom:12px }
.dlg-section.highlight { background:linear-gradient(135deg,#f6fdf9,#f0f7ff); border-color:#c8e0d8 }
.dlg-section.last { border:none; background:transparent; padding:8px 0 0 }
.dlg-sec-head { display:flex; align-items:center; gap:8px; margin-bottom:14px }
.dlg-sec-icon { width:28px; height:28px; border-radius:7px; display:flex; align-items:center; justify-content:center; font-size:14px; flex-shrink:0 }
.dlg-sec-title { font-size:14px; font-weight:700; color:#1f2329 }
.dlg-sec-tip { font-size:11px; color:#909399; margin-left:auto }

.dlg-row { display:flex; gap:12px }
.dlg-row.cols-3-2 { display:grid; grid-template-columns:2fr 1fr; gap:12px; align-items:start }
.dlg-field { min-width:0 }
.dlg-field+.dlg-field { margin-top:10px }
.dlg-row .dlg-field { margin-top:0 }
.dlg-field label { display:block; font-size:12px; font-weight:600; color:#4e5969; margin-bottom:4px }
.dlg-field label i { color:#f56c6c; font-style:normal }
.dlg-field .el-select,.dlg-field .el-input { width:100% }
.dlg-confirm { color:#67c23a; font-weight:600; font-size:13px; margin-top:4px }
.span-2 { grid-column:span 1 }

.dlg-tip { display:flex; align-items:center; gap:4px; font-size:12px; color:#909399; margin-top:8px }
.shared-on { color:#67c23a; font-size:12px; font-weight:500 }

/* File Slots */
.file-slots { display:flex; gap:12px }
.file-slot { flex:1; min-width:0 }
.file-slot-head { display:flex; align-items:center; gap:6px; margin-bottom:8px }
.file-slot-dot { width:8px; height:8px; border-radius:50%; flex-shrink:0 }
.dot-pre { background:#409eff } .dot-mid { background:#e6a23c } .dot-post { background:#67c23a }
.file-slot-label { font-size:13px; font-weight:600; color:#333 }
.file-slot-count { font-size:11px; color:#909399; margin-left:auto }

.file-slot-cards { display:flex; flex-wrap:wrap; gap:8px }
.file-card { width:88px; height:100px; border:2px dashed #dcdfe6; border-radius:10px; display:flex; flex-direction:column; align-items:center; justify-content:center; cursor:pointer; transition:all .2s; position:relative; overflow:hidden; background:#fff }
.file-card:hover { border-color:#409eff; background:#f0f6ff }
.file-card.add { gap:2px }
.file-card.add .el-icon { color:#c0c4cc } .file-card.add:hover .el-icon { color:#409eff }
.file-card.has { border-style:solid; border-color:#d4e8d4; background:#f6fdf6; cursor:default }
.file-card.has:hover { border-color:#a3d9a3 }
.file-card-img { width:100%; height:100%; object-fit:cover; border-radius:8px }
.file-card-icon { display:flex; align-items:center; justify-content:center; color:#909399 }
.file-card-del { position:absolute; top:2px; right:2px; width:20px; height:20px; border-radius:50%; background:rgba(0,0,0,.5); color:#fff; display:flex; align-items:center; justify-content:center; font-size:12px; opacity:0; transition:opacity .15s }
.file-card:hover .file-card-del { opacity:1 }
.file-card-name { font-size:10px; color:#606266; text-align:center; margin-top:2px; max-width:80px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; padding:0 2px }
.file-card-del:hover { background:#f56c6c }

.dlg-footer { }
.dlg-footer-btns { display:flex; justify-content:flex-end; gap:10px; margin-top:8px }
.up-card-preview{width:100%;height:90px;overflow:hidden;display:flex;align-items:center;justify-content:center;background:#f5f5f5;border-radius:6px;margin-bottom:6px}
.up-card-preview img{width:100%;height:100%;object-fit:cover}
.up-card-icon{font-size:36px}
.up-card-name{font-size:11px;color:#555;text-align:center;word-break:break-all;max-width:100%;padding:0 4px}
.up-card-del{position:absolute;top:6px;right:6px;width:22px;height:22px;border-radius:50%;background:rgba(0,0,0,.5);color:#fff;display:flex;align-items:center;justify-content:center;font-size:12px;cursor:pointer;transition:background .2s}
.up-card-del:hover{background:#f56c6c}
.up-slot-group{margin-bottom:6px}
.up-slot-label{font-size:12px;color:#606266;font-weight:500;margin-bottom:4px}
.up-slot-files{display:flex;flex-wrap:wrap;gap:0}

.upload-dlg :deep(.el-dialog){display:flex;flex-direction:column;max-height:92vh;margin-top:4vh!important;margin-bottom:4vh!important}
.upload-dlg :deep(.el-dialog__header){flex-shrink:0}
.upload-dlg :deep(.el-dialog__body){flex:1;overflow-y:auto;min-height:0}
.upload-dlg :deep(.el-dialog__footer){flex-shrink:0}
@media (max-height:800px){.upload-dlg :deep(.el-dialog){max-height:96vh;margin-top:2vh!important;margin-bottom:2vh!important;width:95vw!important}}
@media (max-width:720px){.upload-dlg :deep(.el-dialog){width:96vw!important}}

.upload-progress { margin: 8px 0 12px }
.progress-bar { height: 6px; background: #e8e8e8; border-radius: 3px; overflow: hidden }
.progress-fill { height: 100%; background: linear-gradient(90deg, #409eff, #66b1ff); border-radius: 3px; transition: width .3s ease }
.progress-text { font-size: 12px; color: #909399; margin-top: 4px; text-align: center }
</style>
