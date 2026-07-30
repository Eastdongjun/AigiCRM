<template>
  <div class="incentive-page">
    <!-- 框1: 分类Tab -->
    <div class="section-box">
      <div class="category-tabs">
        <div v-for="cat in parentCategories" :key="cat.id" class="tab-item" :class="{ active: activeTab === String(cat.id) }" @click="activeTab = String(cat.id)">
          <el-icon v-if="cat.categoryCode === 'visit'"><Location /></el-icon>
          <el-icon v-else-if="cat.categoryCode === 'exchange'"><ChatDotSquare /></el-icon>
          <el-icon v-else-if="cat.categoryCode === 'plan'"><Document /></el-icon>
          <el-icon v-else><Box /></el-icon>
          <span>{{ cat.categoryName }}</span>
        </div>
      </div>
    </div>

    <!-- 框2: 统一筛选栏 -->
    <div class="section-box filter-bar">
      <div class="filter-row">
        <span class="flabel">人员</span>
        <el-select v-model="selectedUser" size="small" style="width:140px" clearable placeholder="全部人员" @change="selectedUser=$event||null">
          <el-option v-for="u in userList" :key="u.id" :label="u.realName" :value="u.id" />
        </el-select>
        <span class="flabel">周期</span>
        <el-select v-model="selectedPeriod" size="small" style="width:130px">
          <el-option v-for="p in periodOptions" :key="p" :label="p" :value="p" />
        </el-select>
        <span class="fsep"></span>
        <el-input v-model="searchText" placeholder="搜索文件名..." clearable size="small" style="width:150px" :prefix-icon="Search" />
        <el-input v-model="filterCustomer" placeholder="客户名称" clearable size="small" style="width:140px" />
        <el-select v-model="filterGroup" placeholder="全部分类" clearable size="small" style="width:110px">
          <el-option v-for="c in filterGroupOptions" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="filterTier" placeholder="全部子类" clearable size="small" style="width:110px">
          <el-option v-for="c in filterTierOptions" :key="c" :label="c" :value="c" />
        </el-select>
        <span class="file-count">{{ filteredRecords.length }} 个文件</span>
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
    <el-dialog v-model="showCustomerDialog" width="700px" top="2vh" :close-on-click-modal="false" class="upload-dlg">
      <template #header>
        <span style="font-size:17px;font-weight:700">{{ pendingReplace ? '追加文件' : '上传激励文件' }}</span>
        <el-tag v-if="pendingChild" size="small" type="warning" style="margin-left:8px">{{ pendingChild.categoryName }}</el-tag>
      </template>
      <div class="up-body">
        <!-- 基本信息卡 -->
        <div class="up-card-box">
          <div class="up-box-title">📋 基本信息</div>
          <div class="up-row">
            <div class="up-field flex-2"><label>关联客户 <i>*</i></label>
              <el-select v-model="selectedCustomerId" filterable remote :remote-method="searchCustomers" placeholder="搜索客户名称..." :loading="customerLoading">
                <el-option v-for="c in customerOptions" :key="c.id" :label="c.customerName" :value="c.id" />
              </el-select>
              <div v-if="selectedCustomerName" class="up-hint">✅ {{ selectedCustomerName }}</div>
            </div>
            <div class="up-field flex-1"><label>行业</label>
              <el-select v-model="selectedIndustry" placeholder="选择" clearable><el-option v-for="i in industryOptions" :key="i" :label="i" :value="i" /></el-select>
            </div>
          </div>
          <div class="up-field flex-1"><label>相关产品</label><el-input v-model="relatedProducts" placeholder="填写产品名称" /></div>
        </div>

        <!-- 安装详情 -->
        <div class="up-card-box">
          <div class="up-box-title">📝 安装详情</div>
          <div class="up-field" style="margin-bottom:10px"><label>安装前问题</label><el-input v-model="preInstallQuestion" type="textarea" :rows="3" placeholder="描述安装前存在的问题..." /></div>
          <div class="up-field"><label>安装备注</label><el-input v-model="remarkText" type="textarea" :rows="2" placeholder="安装备注..." /></div>
        </div>

        <!-- 安装文件卡 -->
        <div class="up-card-box highlight">
          <div class="up-box-title">📸 安装文件 <span>前 / 中 / 后 全部上传后计分</span></div>
          <div class="up-cards">
            <div v-for="slot in fileSlots" :key="slot.key" class="up-card" :class="{has:installFiles[slot.key]}" @click="!installFiles[slot.key]&&pickFile(slot.key)">
              <template v-if="installFiles[slot.key]">
                <div class="up-card-preview"><img v-if="isImage(installFiles[slot.key].name)" :src="getObjectURL(installFiles[slot.key])" /><span v-else class="up-card-icon">📄</span></div>
                <div class="up-card-name">{{ installFiles[slot.key].name }}</div>
                <span class="up-card-del" @click.stop="installFiles[slot.key]=null">✕</span>
              </template>
              <template v-else><span class="up-card-plus">+</span><span class="up-card-lbl">{{ slot.label }}</span></template>
            </div>
          </div>
        </div>

        <!-- 跟踪记录卡 -->
        <div class="up-card-box">
          <div class="up-box-title">📝 跟踪记录 <span>不加分</span></div>
          <div class="up-card" :class="{has:trackFile}" style="width:200px" @click="!trackFile&&pickTrackFile()">
            <template v-if="trackFile">
              <div class="up-card-preview"><img v-if="isImage(trackFile.name)" :src="getObjectURL(trackFile)" /><span v-else class="up-card-icon">📄</span></div>
              <div class="up-card-name">{{ trackFile.name }}</div>
              <span class="up-card-del" @click.stop="trackFile=null">✕</span>
            </template>
            <template v-else><span class="up-card-plus">+</span><span class="up-card-lbl">选择文件</span></template>
          </div>
        </div>

        <!-- 共享 -->
        <div class="up-card-box">
          <div class="up-row">
            <div class="up-field" style="display:flex;align-items:center;gap:10px">
              <label style="margin-bottom:0">共享给团队</label><el-switch v-model="shareToTeam" />
              <span v-if="shareToTeam" style="color:#67c23a;font-size:12px">● 已共享</span>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <span style="font-size:12px;color:#999;margin-right:auto">任意文件即可提交 · 三文件齐全后自动计分</span>
        <el-button @click="showCustomerDialog=false">取消</el-button>
        <el-button type="primary" :loading="uploading" :disabled="!canSubmit" @click="confirmUpload">{{ pendingReplace?'追加提交':'上传并提交' }}</el-button>
      </template>
    </el-dialog>

    <!-- 隐藏的文件上传 -->
    <input
      ref="fileInputRef"
      type="file"
      style="display:none"
      @change="handleFileSelected"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Search, Refresh, Delete, Plus } from '@element-plus/icons-vue'
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
const pendingChild = ref(null)
const pendingReplace = ref(null)
const fileInputRef = ref(null)
const shareToTeam = ref(false)
const uploading = ref(false)

// 安装前/中/后 文件槽 + 文本
const fileSlots = [
  { key: 'pre', label: '安装前' },
  { key: 'mid', label: '安装中' },
  { key: 'post', label: '安装后' },
]
const installFiles = reactive({ pre: null, mid: null, post: null })
const trackFile = ref(null)
const preInstallQuestion = ref('')
const remarkText = ref('')
const currentSlot = ref(null)

// 行业 + 相关产品
const industryOptions = ref([])
const selectedIndustry = ref('')
const relatedProducts = ref('')

const canSubmit = computed(() =>
  !!selectedCustomerId.value && (installFiles.pre || installFiles.mid || installFiles.post))

const selectedCustomerName = computed(() => {
  if (!selectedCustomerId.value) return ''
  const c = customerOptions.value.find(c => c.id === selectedCustomerId.value)
  return c?.customerName || ''
})

const parentCategories = computed(() => categories.value.filter(c => c.parentId === 0))
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
  installFiles.pre = null
  installFiles.mid = null
  installFiles.post = null
  trackFile.value = null
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
  resetDialogFields()
  showCustomerDialog.value = true
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
  // 保留已有文件，标记为已上传（不可删除但可替换）
  if (row.filePreId) installFiles.pre = { name: row.filePreName || '已上传', _existing: true }
  if (row.fileMidId) installFiles.mid = { name: row.fileMidName || '已上传', _existing: true }
  if (row.filePostId) installFiles.post = { name: row.filePostName || '已上传', _existing: true }
  if (row.fileTrackId) trackFile.value = { name: row.fileTrackName || '已上传', _existing: true }
  preInstallQuestion.value = row.preInstallQuestion || ''
  remarkText.value = row.remark || ''
  shareToTeam.value = !!row.shared
  selectedIndustry.value = row.industry || ''
  relatedProducts.value = row.relatedProducts || ''
  if (row.customerId) {
    customerOptions.value = [{ id: row.customerId, customerName: row.customerName }]
  }
  showCustomerDialog.value = true
}

const searchCustomers = async (query) => {
  customerLoading.value = true
  try {
    const res = await getCustomerList({ current: 1, size: 200, keyword: query || '' })
    customerOptions.value = res.data?.records || []
  } catch (e) {}
  customerLoading.value = false
}

// 选择某个安装文件槽的文件
const isImage = (name) => /\.(jpg|jpeg|png|gif|webp|bmp)$/i.test(name||'')
const getObjectURL = (file) => (file && !file._existing) ? URL.createObjectURL(file) : ''

const pickFile = (slotKey) => {
  currentSlot.value = slotKey
  fileInputRef.value?.click()
}

const pickTrackFile = () => {
  currentSlot.value = 'track'
  fileInputRef.value?.click()
}

const handleFileSelected = (event) => {
  const file = event.target.files[0]
  if (file && currentSlot.value === 'track') {
    trackFile.value = file
  } else if (file && currentSlot.value) {
    installFiles[currentSlot.value] = file
  }
  currentSlot.value = null
  event.target.value = ''
}

// 上传单个文件，返回 fileId
const uploadOne = async (file) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('scope', 'incentive')
  const res = await request.post('/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return res.code === 200 ? (res.data?.id ?? null) : null
}

// 提交：可同时上传安装前/中/后三个文件，仅生成 1 条记录
const confirmUpload = async () => {
  if (!canSubmit.value) return
  uploading.value = true
  try {
    const trackId = (trackFile.value && !trackFile.value._existing) ? await uploadOne(trackFile.value) : null
    if (trackFile.value && !trackFile.value._existing && !trackId) {
      ElMessage.error('跟踪记录文件上传失败，请重试')
      return
    }
    const [filePreId, fileMidId, filePostId] = await Promise.all([
      (installFiles.pre && !installFiles.pre._existing) ? uploadOne(installFiles.pre) : Promise.resolve(null),
      (installFiles.mid && !installFiles.mid._existing) ? uploadOne(installFiles.mid) : Promise.resolve(null),
      (installFiles.post && !installFiles.post._existing) ? uploadOne(installFiles.post) : Promise.resolve(null),
    ])
    const hasNewFile = (installFiles.pre && !installFiles.pre._existing) || (installFiles.mid && !installFiles.mid._existing) || (installFiles.post && !installFiles.post._existing)
    if (hasNewFile && ((installFiles.pre && !installFiles.pre._existing && !filePreId) || (installFiles.mid && !installFiles.mid._existing && !fileMidId) || (installFiles.post && !installFiles.post._existing && !filePostId))) {
      ElMessage.error('文件上传失败，请重试')
      uploading.value = false
      return
    }
    if (pendingReplace.value) {
      // 追加模式：保留未被删除的已有文件，只替换新上传的文件槽
      const old = pendingReplace.value
      const finalPreId = installFiles.pre && !installFiles.pre._existing ? filePreId : (installFiles.pre && installFiles.pre._existing ? old.filePreId : null)
      const finalMidId = installFiles.mid && !installFiles.mid._existing ? fileMidId : (installFiles.mid && installFiles.mid._existing ? old.fileMidId : null)
      const finalPostId = installFiles.post && !installFiles.post._existing ? filePostId : (installFiles.post && installFiles.post._existing ? old.filePostId : null)
      const finalTrackId = trackFile.value && !trackFile.value._existing ? trackId : (trackFile.value && trackFile.value._existing ? old.fileTrackId : null)
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
        filePreId,
        fileMidId,
        filePostId,
        fileTrackId: trackId,
        remark: remarkText.value || null,
        shared: shareToTeam.value ? 1 : 0
      })
    }
    ElMessage.success(pendingReplace.value ? '追加成功' : '上传成功，三文件齐全后自动计分')
    showCustomerDialog.value = false
    pendingReplace.value = null
    loadRecords()
  } catch (e) {
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}

watch(activeTab, () => { filterGroup.value = ''; filterTier.value = ''; loadRecords() })
watch(selectedPeriod, loadRecords)
watch(selectedUser, loadRecords)

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
.incentive-page { padding: 0; height: calc(100vh - 80px); display: flex; flex-direction: column; gap: 8px }
.section-box { background:#fff; border:1px solid #e4e7ed; border-radius:10px; padding:14px 20px; flex-shrink:0 }

/* 筛选栏 */
.filter-bar { padding: 10px 20px }
.filter-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap }
.flabel { color: #555; font-weight: 500; font-size: 13px; white-space: nowrap }
.fsep { width: 1px; height: 20px; background: #e4e7ed; margin: 0 4px }

.category-tabs { display: flex; gap: 4px; padding: 4px; background: #f2f4f7; border-radius: 10px; }
.tab-item { flex: 1; display: flex; align-items: center; justify-content: center; gap: 6px; padding: 10px 0; font-size: 14px; color: #666; cursor: pointer; border-radius: 8px; transition: all .2s; font-weight: 500; }
.tab-item:hover { opacity: .8 }
.tab-item.active { font-weight: 700; color: #fff; box-shadow: 0 1px 4px rgba(0,0,0,.1) }
.tab-item:nth-child(1).active { background: #f59e0b }
.tab-item:nth-child(2).active { background: #3b82f6 }
.tab-item:nth-child(3).active { background: #8b5cf6 }
.tab-item:nth-child(4).active { background: #10b981 }

.main-layout { display: flex; border: 1px solid #e4e7ed; border-radius: 10px; overflow: hidden; flex: 1; min-height: 0; }
.left-panel { flex: 0 0 20%; background: #fafbfd; padding: 14px; border-right: 1px solid #f0f2f5; overflow-y: auto; }
.sub-hint { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; color: #999; font-size: 13px; }
.sub-group { margin-bottom: 2px; }
.group-header { font-size: 14px; font-weight: 700; color: #1f2329; padding: 10px 0 6px 10px; border-left: 3px solid #409eff; margin-top: 6px; }
.sub-item { display: flex; justify-content: space-between; align-items: center; padding: 10px 0 10px 12px; border-bottom: 1px solid #f5f5f5; }
.sub-item.flat { padding-left: 0; }
.sub-name { font-size: 13px; color: #444; font-weight: 500 }
.sub-actions { display: flex; align-items: center; gap: 12px; }
.score-badge { display: inline-block; min-width: 30px; text-align: center; padding: 2px 8px; border-radius: 10px; font-size: 12px; font-weight: 600; }
.score-0 { color: #bbb; background: #f5f5f5; }
.score-1 { color: #e6a23c; background: #fff7ed; }
.score-2 { color: #67c23a; background: #f0f9eb; }
.score-3 { color: #f56c6c; background: #fef0f0; }

/* 右侧面板 */
.right-panel { flex: 1; background: #fff; padding: 12px 18px; overflow-y: auto; }
.file-count { color: #409eff; font-size: 13px; font-weight: 600; white-space: nowrap; margin-left: auto }

/* 表格内样式 */
.file-cell { display: inline-flex; align-items: center; gap: 4px; }
.file-icon { color: #909399; font-size: 14px; flex-shrink: 0; }
.customer-link { color: #409eff; cursor: pointer; }
.customer-link:hover { text-decoration: underline; }
.shared-yes { color: #67c23a; font-size: 12px; font-weight: 500; }
.shared-no { color: #c0c4cc; }

/* 上传弹窗 */
.upload-dialog { padding: 4px 0; }
.dialog-field { margin-bottom: 16px; }
.field-label { font-size: 13px; color: #86909c; margin-bottom: 6px; }
.field-value { font-size: 15px; color: #409eff; font-weight: 600; margin-top: 6px; }
.share-field { display: flex; align-items: center; gap: 12px; }
.share-tag { color: #67c23a; font-size: 13px; font-weight: 500; }
.upload-hint { text-align: center; color: #c0c4cc; font-size: 12px; margin-top: 8px; }
.share-more { color: #c0c4cc; margin-left: 2px; }
.label-hint { color: #c0c4cc; font-weight: 400; }

/* 弹窗内 安装文件 行 */
.file-row { display: flex; align-items: center; gap: 10px; padding: 6px 0; }
.file-row-label { flex: 0 0 48px; font-size: 13px; color: #606266; }
.file-row-name { flex: 1; font-size: 13px; color: #409eff; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-row-name.empty { color: #c0c4cc; }

/* 列表内 安装文件 标签 */
.install-files { display: flex; flex-direction: column; gap: 2px; }
.file-tag { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; color: #333; }
.file-tag b { display: inline-block; min-width: 16px; height: 16px; line-height: 16px; text-align: center; background: #ecf5ff; color: #409eff; border-radius: 3px; font-weight: 600; font-size: 11px; }

/* 上传弹窗 */
.up-card-box{background:#fafbfd;border:1px solid #eef0f4;border-radius:8px;padding:12px 14px;margin-bottom:8px}
.up-card-box.highlight{background:linear-gradient(135deg,#f8faff,#f0f4ff);border-color:#d4e0f8}
.up-box-title{font-size:13px;font-weight:700;color:#1a1a2e;margin-bottom:10px;display:flex;align-items:center;gap:6px}
.up-box-title span{font-weight:400;font-size:11px;color:#909399}
.up-row{display:flex;gap:12px}
.up-row+.up-row{margin-top:8px}
.up-field{min-width:0}.up-field label{display:block;font-size:11px;font-weight:600;color:#555;margin-bottom:3px}.up-field label i{color:#f56c6c;font-style:normal}
.up-field .el-select{width:100%}.up-field .el-input{width:100%}
.up-hint{color:#409eff;font-weight:600;font-size:13px;margin-top:4px}
.flex-1{flex:1}.flex-2{flex:2}
.up-cards{display:flex;gap:16px;margin-top:4px}
.up-card{width:180px;min-height:140px;border:2px dashed #dcdfe6;border-radius:12px;display:flex;flex-direction:column;align-items:center;justify-content:center;cursor:pointer;transition:all .25s;position:relative;overflow:hidden;background:#fff}
.up-card:hover{border-color:#409eff;background:#f0f6ff;box-shadow:0 2px 8px rgba(64,158,255,.08)}
.up-card.has{border-style:solid;border-color:#d4e8d4;background:#f6fdf6;cursor:default;padding:8px}
.up-card.has:hover{border-color:#a3d9a3}
.up-card-plus{font-size:32px;color:#c0c4cc;line-height:1;transition:color .2s}
.up-card:hover .up-card-plus{color:#409eff}
.up-card-lbl{font-size:12px;color:#909399;margin-top:6px}
.up-card-preview{width:100%;height:90px;overflow:hidden;display:flex;align-items:center;justify-content:center;background:#f5f5f5;border-radius:6px;margin-bottom:6px}
.up-card-preview img{width:100%;height:100%;object-fit:cover}
.up-card-icon{font-size:36px}
.up-card-name{font-size:11px;color:#555;text-align:center;word-break:break-all;max-width:100%;padding:0 4px}
.up-card-del{position:absolute;top:6px;right:6px;width:22px;height:22px;border-radius:50%;background:rgba(0,0,0,.5);color:#fff;display:flex;align-items:center;justify-content:center;font-size:12px;cursor:pointer;transition:background .2s}
.up-card-del:hover{background:#f56c6c}

.upload-dlg :deep(.el-dialog){display:flex;flex-direction:column;max-height:92vh;margin-top:4vh!important;margin-bottom:4vh!important}
.upload-dlg :deep(.el-dialog__header){flex-shrink:0}
.upload-dlg :deep(.el-dialog__body){flex:1;overflow-y:auto;min-height:0}
.upload-dlg :deep(.el-dialog__footer){flex-shrink:0}
@media (max-height:800px){.upload-dlg :deep(.el-dialog){max-height:96vh;margin-top:2vh!important;margin-bottom:2vh!important;width:95vw!important}}
@media (max-width:720px){.upload-dlg :deep(.el-dialog){width:96vw!important}}
</style>
