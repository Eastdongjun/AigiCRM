<template>
  <div class="page-container">
    <div class="filter-bar">
      <el-select v-model="query.status" placeholder="状态" style="width: 130px" @change="onFilterChange">
        <el-option label="已通过" value="approved" />
        <el-option label="待审核" value="pending" />
        <el-option label="已驳回" value="rejected" />
        <el-option label="已停用" value="disabled" />
        <el-option label="全部状态" value="all" />
      </el-select>
      <el-select v-model="query.categoryCode" placeholder="类别" clearable style="width: 150px" @change="loadData">
        <el-option v-for="c in categories" :key="c.dictValue" :label="c.dictLabel" :value="c.dictValue" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="搜书名" clearable style="width: 200px" @keyup.enter="loadData" />
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button v-if="userStore.hasPermission('fabushi:create')" @click="submitVisible = true">提交书目</el-button>
    </div>

    <el-table :data="books" stripe size="small" v-loading="loading">
      <el-table-column prop="title" label="书名" min-width="180" show-overflow-tooltip />
      <el-table-column prop="author" label="作者" width="120" show-overflow-tooltip />
      <el-table-column prop="categoryName" label="类别" width="110" />
      <el-table-column prop="pages" label="页数" width="70" />
      <el-table-column label="电子书" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.pdfFileId" type="success" size="small">已挂载</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="submitterName" label="提交人" width="90" />
      <el-table-column prop="createdAt" label="提交时间" width="160" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <template v-if="userStore.hasPermission('fabushi:book:manage')">
            <el-button v-if="row.status === 'pending'" link type="primary" @click="doApprove(row)">通过</el-button>
            <el-button v-if="row.status === 'pending'" link type="warning" @click="openReject(row)">驳回</el-button>
            <el-button v-if="row.status === 'approved'" link type="primary" @click="openPdf(row)">挂PDF</el-button>
            <el-button v-if="row.status === 'approved'" link type="danger" @click="doDisable(row)">停用</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <div class="list-pager">
      <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total"
        :page-sizes="pageSizes" layout="sizes, prev, pager, next" size="small" @change="loadData" />
    </div>

    <!-- 提交书目(人人可提交, 待审核) -->
    <el-dialog v-model="submitVisible" title="提交书目" width="480px">
      <el-form label-width="70px">
        <el-form-item label="类别" required>
          <el-select v-model="submitForm.categoryCode" placeholder="选择类别" style="width: 100%">
            <el-option v-for="c in categories" :key="c.dictValue" :label="c.dictLabel" :value="c.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="书名" required>
          <el-input v-model="submitForm.title" placeholder="书名" />
        </el-form-item>
        <el-form-item label="作者">
          <el-input v-model="submitForm.author" />
        </el-form-item>
        <el-form-item label="版本">
          <el-select v-model="submitForm.editionType" placeholder="原版/译本/注释本/讲义" clearable style="width: 100%">
            <el-option v-for="e in editions" :key="e.dictValue" :label="e.dictLabel" :value="e.dictValue" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="submitVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="doSubmit">提交</el-button>
      </template>
    </el-dialog>

    <!-- 驳回(原因必填留痕) -->
    <el-dialog v-model="rejectVisible" title="驳回书目" width="420px">
      <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="驳回原因(必填, 留痕)" />
      <template #footer>
        <el-button @click="rejectVisible = null">取消</el-button>
        <el-button type="warning" :loading="actionLoading" @click="doReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <!-- PDF 挂载(上传到 scope=fabushi 空间) -->
    <el-dialog v-model="pdfVisible" title="挂载电子书 PDF" width="460px">
      <div class="upload-dropzone" @click="pickPdf">
        {{ pdfUploading ? '上传中…' : '点击选择 PDF(≤100MB, 上传到法布施空间)' }}
        <input ref="pdfInputRef" type="file" accept=".pdf" style="display:none" @change="handlePdfChosen" />
      </div>
      <p class="pdf-hint">当前书目: {{ pdfVisible ? pdfVisible.title : '' }}</p>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useUserStore } from '@/store/user'
import { getDictData } from '@/api/system'
import { getFabushiBooks, submitFabushiBook, approveFabushiBook,
         rejectFabushiBook, disableFabushiBook, attachBookPdf } from '@/api/fabushi'

const userStore = useUserStore()
const loading = ref(false)
const actionLoading = ref(false)
const books = ref([])
const total = ref(0)
const categories = ref([])
const editions = ref([])
const pageSizes = [10, 20, 50]
const query = ref({ status: 'approved', categoryCode: '', keyword: '', current: 1, size: 10 })

const submitVisible = ref(false)
const submitForm = ref({ categoryCode: '', title: '', author: '', editionType: '' })
const rejectVisible = ref(null)
const rejectReason = ref('')
const pdfVisible = ref(null)
const pdfUploading = ref(false)
const pdfInputRef = ref(null)

const statusMap = { approved: '已通过', pending: '待审核', rejected: '已驳回', disabled: '已停用' }
const statusTagMap = { approved: 'success', pending: 'warning', rejected: 'danger', disabled: 'info' }
const statusText = (s) => statusMap[s] || s
const statusTagType = (s) => statusTagMap[s] || 'info'

async function loadDicts() {
  const cats = await getDictData('fabushi_category')
  const eds = await getDictData('fabushi_edition_type')
  categories.value = cats.data || []
  editions.value = eds.data || []
}

async function loadData() {
  loading.value = true
  try {
    const params = { ...query.value }
    if (params.status === 'all') params.status = ''
    const { data } = await getFabushiBooks(params)
    books.value = (data && data.records) || []
    total.value = Number(data && data.total) || 0
  } finally {
    loading.value = false
  }
}

function onFilterChange() {
  query.value.current = 1
  loadData()
}

async function doSubmit() {
  if (!submitForm.value.categoryCode) { ElMessage.warning('请选择类别'); return }
  if (!submitForm.value.title.trim()) { ElMessage.warning('书名不能为空'); return }
  actionLoading.value = true
  try {
    await submitFabushiBook(submitForm.value)
    ElMessage.success('已提交, 待管理员审核')
    submitVisible.value = false
    submitForm.value = { categoryCode: '', title: '', author: '', editionType: '' }
    loadData()
  } finally {
    actionLoading.value = false
  }
}

async function doApprove(row) {
  await approveFabushiBook(row.id)
  ElMessage.success('已通过')
  loadData()
}

function openReject(row) {
  rejectVisible.value = row
  rejectReason.value = ''
}

async function doReject() {
  if (!rejectReason.value.trim()) { ElMessage.warning('驳回原因不能为空'); return }
  actionLoading.value = true
  try {
    await rejectFabushiBook(rejectVisible.value.id, rejectReason.value.trim())
    ElMessage.success('已驳回')
    rejectVisible.value = null
    loadData()
  } finally {
    actionLoading.value = false
  }
}

async function doDisable(row) {
  await disableFabushiBook(row.id)
  ElMessage.success('已停用')
  loadData()
}

function openPdf(row) {
  pdfVisible.value = row
}

function pickPdf() { pdfInputRef.value?.click() }

async function handlePdfChosen(evt) {
  const file = evt.target.files && evt.target.files[0]
  if (!file) return
  pdfUploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('scope', 'fabushi')
    const res = await request.post('/api/file/upload', formData)
    const fileId = res.data && res.data.id
    await attachBookPdf(pdfVisible.value.id, fileId)
    ElMessage.success('PDF 已挂载')
    pdfVisible.value = null
    loadData()
  } finally {
    pdfUploading.value = false
  }
}

onMounted(() => { loadDicts(); loadData() })
</script>

<style scoped>
.filter-bar { display: flex; gap: 10px; margin-bottom: 12px; align-items: center; }
.list-pager { margin-top: 12px; display: flex; justify-content: flex-end; }
.upload-dropzone { border: 1px dashed var(--el-border-color); border-radius: 6px; padding: 28px; text-align: center; cursor: pointer; color: var(--el-text-color-secondary); }
.pdf-hint { color: var(--el-text-color-secondary); font-size: 12px; margin-top: 8px; }
</style>
