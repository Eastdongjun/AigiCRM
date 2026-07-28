<template>
  <div class="incentive-page">
    <h3 class="section-title"><span class="title-bar"></span>激励文件上传</h3>

    <div class="top-bar">
      <span class="top-label">人员</span>
      <el-select v-model="selectedUser" style="width:120px">
        <el-option :label="userStore.realName || '当前用户'" :value="null" />
      </el-select>
      <span class="top-label" style="margin-left:16px">周期</span>
      <el-select v-model="selectedPeriod" style="width:140px">
        <el-option v-for="p in periodOptions" :key="p" :label="p" :value="p" />
      </el-select>
    </div>

    <!-- 四项 Tab -->
    <div class="category-tabs">
      <div
        v-for="cat in parentCategories"
        :key="cat.id"
        class="tab-item"
        :class="{ active: activeTab === String(cat.id) }"
        @click="activeTab = String(cat.id)"
      >
        <el-icon v-if="cat.categoryCode === 'visit'"><Location /></el-icon>
        <el-icon v-else-if="cat.categoryCode === 'exchange'"><ChatDotSquare /></el-icon>
        <el-icon v-else-if="cat.categoryCode === 'plan'"><Document /></el-icon>
        <el-icon v-else><Box /></el-icon>
        <span>{{ cat.categoryName }}</span>
      </div>
    </div>

    <!-- 主体左右布局 -->
    <div class="main-layout" v-if="groupedChildren.length">
      <!-- 左侧：子分类列表 -->
      <div class="left-panel">
        <div class="sub-hint">
          <el-tag type="danger" size="small" effect="plain">{{ activeParent?.categoryName }}</el-tag>
          <span>— 选择子项上传文件</span>
        </div>

        <div v-for="group in groupedChildren" :key="group.id" class="sub-group">
          <!-- 三级结构：group 是分组标题，items 是子项 -->
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
          <!-- 二级结构：直接展示 -->
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
        <div class="right-header">
          <span class="right-title">已上传文件</span>
          <div class="right-filters">
            <el-input v-model="searchText" placeholder="搜索文件名..." clearable size="small" style="width:160px" :prefix-icon="Search" />
            <el-select v-model="filterGroup" placeholder="全部分类" clearable size="small" style="width:120px">
              <el-option v-for="c in filterGroupOptions" :key="c" :label="c" :value="c" />
            </el-select>
            <el-select v-model="filterTier" placeholder="全部子类" clearable size="small" style="width:120px">
              <el-option v-for="c in filterTierOptions" :key="c" :label="c" :value="c" />
            </el-select>
            <span class="file-count">{{ filteredRecords.length }} 个文件</span>
          </div>
        </div>
        <el-table :data="filteredRecords" stripe style="width:100%" empty-text="暂无已上传文件" size="small">
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
              <el-button link type="primary" size="small" @click="startReplace(row)">替换</el-button>
              <el-button link type="danger" size="small" @click="handleDeleteRecord(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 关联客户 + 安装文件 弹窗 -->
    <el-dialog v-model="showCustomerDialog" :title="(pendingReplace ? '替换 · ' : '关联客户 · ') + (pendingChild?.categoryName || '')" width="520px" :close-on-click-modal="false">
      <div class="upload-dialog">
        <div class="dialog-field">
          <div class="field-label">选择行业</div>
          <el-select v-model="selectedIndustry" placeholder="请选择行业" clearable filterable style="width:100%">
            <el-option v-for="i in industryOptions" :key="i" :label="i" :value="i" />
          </el-select>
        </div>

        <div class="dialog-field">
          <div class="field-label">相关产品</div>
          <el-input v-model="relatedProducts" type="textarea" :rows="3" placeholder="请填写相关产品(每行一个或逗号分隔)..." maxlength="1000" show-word-limit />
        </div>

        <div class="dialog-field">
          <div class="field-label">关联客户</div>
          <el-select
            v-model="selectedCustomerId"
            filterable
            remote
            :remote-method="searchCustomers"
            placeholder="搜索客户名称..."
            style="width:100%"
            :loading="customerLoading"
          >
            <el-option v-for="c in customerOptions" :key="c.id" :label="c.customerName" :value="c.id" />
          </el-select>
          <div v-if="selectedCustomerName" class="field-value">{{ selectedCustomerName }}</div>
        </div>

        <div class="dialog-field">
          <div class="field-label">安装前问题</div>
          <el-input v-model="preInstallQuestion" type="textarea" :rows="3" placeholder="请描述安装前存在的问题..." maxlength="1000" show-word-limit />
        </div>

        <div class="dialog-field">
          <div class="field-label">安装文件 <span class="label-hint">(安装前/中/后，至少选 1 个)</span></div>
          <div v-for="slot in fileSlots" :key="slot.key" class="file-row">
            <span class="file-row-label">{{ slot.label }}</span>
            <span class="file-row-name" :class="{ empty: !installFiles[slot.key] }">
              {{ installFiles[slot.key] ? installFiles[slot.key].name : '未选择文件' }}
            </span>
            <el-button v-if="!installFiles[slot.key]" circle size="small" type="primary" :icon="Plus" @click="pickFile(slot.key)" />
            <el-button v-else circle size="small" type="danger" plain :icon="Delete" @click="installFiles[slot.key] = null" />
          </div>
        </div>

        <div class="dialog-field">
          <div class="field-label">备注</div>
          <el-input v-model="remarkText" type="textarea" :rows="2" placeholder="备注(选填)..." maxlength="500" show-word-limit />
        </div>

        <div class="dialog-field share-field">
          <span class="field-label">共享给团队</span>
          <el-switch v-model="shareToTeam" />
          <span v-if="shareToTeam" class="share-tag">● 共享</span>
        </div>

        <p class="upload-hint">支持 PDF、Word、Excel、图片，单文件 ≤ 50MB · 每个文件计 1 份学分</p>
      </div>
      <template #footer>
        <el-button @click="showCustomerDialog = false">取消</el-button>
        <el-button type="primary" :icon="Upload" :loading="uploading" :disabled="!canSubmit" @click="confirmUpload">
          {{ pendingReplace ? '替换并提交' : '上传并提交' }}
        </el-button>
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
import { getCategoryTree, getRecords, addRecord, deleteRecord } from '@/api/incentive'
import { getCustomerList } from '@/api/customer'
import { getDictData } from '@/api/system'
import { useUserStore } from '@/store/user'
import request from '@/utils/request'

const userStore = useUserStore()
const activeTab = ref('')
const selectedUser = ref(null)
const periodOptions = (() => { const l = []; for (let y = 2030; y >= 2025; y--) { l.push(y + '下半年', y + '上半年') } return l })()
const selectedPeriod = ref('2026上半年')
const categories = ref([])
const records = ref([])

// 右侧筛选
const searchText = ref('')
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

// 记录增强：计算大类·子类、档位
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
          // 三级：grandparent(tab) · parent(组) — cat(档位)
          majorMinor = grandparent.categoryName + ' · ' + parent.categoryName
          tier = cat.categoryName
          groupName = parent.categoryName
        } else if (parent.parentId === 0) {
          // 二级：parent(tab) · cat(子类)
          majorMinor = parent.categoryName + ' · ' + cat.categoryName
          groupName = cat.categoryName
        }
      }
    }
    return { ...r, majorMinor, tier, groupName }
  })
})

// 筛选选项
const filterGroupOptions = computed(() => {
  return [...new Set(enrichedRecords.value.map(r => r.groupName).filter(Boolean))]
})
const filterTierOptions = computed(() => {
  let list = enrichedRecords.value
  if (filterGroup.value) list = list.filter(r => r.groupName === filterGroup.value)
  return [...new Set(list.map(r => r.tier).filter(Boolean))]
})

// 过滤后的记录
const filteredRecords = computed(() => {
  let list = enrichedRecords.value
  if (searchText.value) {
    const kw = searchText.value.toLowerCase()
    list = list.filter(r => r.fileName?.toLowerCase().includes(kw))
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

const loadRecords = async () => {
  try {
    const parentId = activeTab.value ? Number(activeTab.value) : null
    const res = await getRecords({ period: selectedPeriod.value, parentCategoryId: parentId })
    records.value = res.data || []
  } catch (e) {}
}

const resetDialogFields = () => {
  installFiles.pre = null
  installFiles.mid = null
  installFiles.post = null
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

// 替换流程：打开同一弹窗，预填客户，提交时先删旧记录再新增
const startReplace = (row) => {
  pendingChild.value = categoryMap.value[row.categoryId] || null
  pendingReplace.value = row
  selectedCustomerId.value = row.customerId
  resetDialogFields()
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
    const res = await getCustomerList({ current: 1, size: 50, keyword: query || '' })
    customerOptions.value = res.data?.records || []
  } catch (e) {}
  customerLoading.value = false
}

// 选择某个安装文件槽的文件
const pickFile = (slotKey) => {
  currentSlot.value = slotKey
  fileInputRef.value?.click()
}

const handleFileSelected = (event) => {
  const file = event.target.files[0]
  if (file && currentSlot.value) {
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
    const [filePreId, fileMidId, filePostId] = await Promise.all([
      installFiles.pre ? uploadOne(installFiles.pre) : Promise.resolve(null),
      installFiles.mid ? uploadOne(installFiles.mid) : Promise.resolve(null),
      installFiles.post ? uploadOne(installFiles.post) : Promise.resolve(null),
    ])
    if ((installFiles.pre && !filePreId) || (installFiles.mid && !fileMidId) || (installFiles.post && !filePostId)) {
      ElMessage.error('部分文件上传失败，请重试')
      return
    }
    // 替换：先删旧记录，再新增一条
    if (pendingReplace.value) {
      await deleteRecord(pendingReplace.value.id)
    }
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
      remark: remarkText.value || null,
      shared: shareToTeam.value ? 1 : 0
    })
    ElMessage.success(pendingReplace.value ? '替换成功' : '上传成功，已关联客户')
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

const loadIndustries = async () => {
  try {
    const res = await getDictData('industry')
    industryOptions.value = (res.data || []).map(d => d.dictValue)
  } catch (e) {
    industryOptions.value = ['煤矿', '化工', '电力', '洗煤厂', '有色', '其他']
  }
}

onMounted(() => {
  loadCategories()
  loadIndustries()
})
</script>

<style scoped>
.incentive-page { padding: 0; }
.section-title { font-size: 16px; font-weight: 600; margin: 0 0 20px; display: flex; align-items: center; gap: 8px; }
.title-bar { display: inline-block; width: 4px; height: 18px; background: #409eff; border-radius: 2px; }
.top-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 20px; font-size: 14px; }
.top-label { color: #333; }

/* Tab 区 */
.category-tabs { display: flex; background: #f5f7fa; border-radius: 8px; overflow: hidden; }
.tab-item { flex: 1; display: flex; align-items: center; justify-content: center; gap: 6px; padding: 14px 0; font-size: 14px; color: #666; cursor: pointer; border-bottom: 2px solid transparent; transition: all 0.2s; background: transparent; }
.tab-item:hover { color: #409eff; }
.tab-item.active { color: #409eff; font-weight: 600; background: #fff; border-bottom-color: #409eff; }

/* 主体左右布局 */
.main-layout { display: flex; margin-top: 16px; border: 1px solid #ebeef5; border-radius: 8px; overflow: hidden; min-height: 500px; }

/* 左侧面板 */
.left-panel { flex: 0 0 42%; background: #fafbfc; padding: 20px; border-right: 1px solid #ebeef5; overflow-y: auto; max-height: 600px; }
.sub-hint { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; color: #999; font-size: 13px; }

/* 分组标题 */
.sub-group { margin-bottom: 4px; }
.group-header { font-size: 15px; font-weight: 600; color: #303133; padding: 12px 0 8px 12px; border-left: 3px solid #409eff; margin-top: 8px; }

/* 子项行 */
.sub-item { display: flex; justify-content: space-between; align-items: center; padding: 12px 0 12px 16px; border-bottom: 1px solid #f0f0f0; }
.sub-item.flat { padding-left: 0; }
.sub-name { font-size: 14px; color: #333; }
.sub-actions { display: flex; align-items: center; gap: 16px; }

/* 分数徽标（左侧） */
.score-badge { display: inline-block; min-width: 32px; text-align: center; padding: 2px 8px; border-radius: 10px; font-size: 12px; font-weight: 600; }
.score-0 { color: #c0c4cc; background: #f4f4f5; }
.score-1 { color: #e6a23c; background: #fdf6ec; }
.score-2 { color: #67c23a; background: #f0f9eb; }
.score-3 { color: #f56c6c; background: #fef0f0; }

/* 右侧面板 */
.right-panel { flex: 1; background: #fff; padding: 20px; overflow-y: auto; max-height: 600px; }
.right-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; flex-wrap: wrap; gap: 8px; }
.right-title { font-size: 16px; font-weight: 600; color: #303133; }
.right-filters { display: flex; align-items: center; gap: 8px; }
.file-count { color: #909399; font-size: 13px; white-space: nowrap; }

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
</style>
