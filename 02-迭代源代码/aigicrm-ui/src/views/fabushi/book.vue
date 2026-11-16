<template>
  <div class="fabushi-book">
    <div class="caps">
      <button type="button" class="cap" :class="{ active: !query.categoryCode }" @click="selectCategory('')">
        全部 <span class="n">{{ allBooks.length }}</span>
      </button>
      <button v-for="c in categories" :key="c.dictValue" type="button" class="cap" :class="{ active: query.categoryCode === c.dictValue }" @click="selectCategory(c.dictValue)">
        {{ c.dictLabel }} <span class="n">{{ categoryCounts[c.dictValue] || 0 }}</span>
      </button>
    </div>

    <div class="filter">
      <span class="fl">状态</span>
      <el-select v-model="query.status" style="width: 120px" @change="onFilterChange">
        <el-option label="全部状态" value="all" />
        <el-option label="已通过" value="approved" />
        <el-option label="待审核" value="pending" />
        <el-option label="已驳回" value="rejected" />
        <el-option label="已停用" value="disabled" />
      </el-select>
      <span class="fl">关键词</span>
      <el-input v-model="query.keyword" clearable placeholder="书名 / 作者 / 提交人" style="width: 220px" @keyup.enter="onFilterChange" />
      <el-button type="primary" @click="onFilterChange">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
      <span class="spacer" />
      <el-button :class="{ 'pending-btn': pendingCount }" @click="showPending">待审核 ({{ pendingCount }})</el-button>
      <el-button v-if="userStore.hasPermission('fabushi:create')" type="primary" @click="submitVisible = true">＋ 新增书目</el-button>
    </div>

    <div class="book-layout">
      <aside class="sub-panel">
        <div class="sub-title">书目 · {{ categoryName || '全部' }}</div>
        <el-input v-model="leftKeyword" clearable placeholder="搜索书目" size="small" />
        <button v-for="book in leftBooks" :key="book.id" type="button" class="sub-row" :class="{ active: selectedBookId === book.id }" @click="selectBook(book)">
          <span>{{ book.title }}</span><span class="cnt">{{ book.postCount || 0 }}</span>
        </button>
        <div v-if="!leftBooks.length" class="sub-empty">暂无书目</div>
        <div class="sub-note">左侧计数 = 该书目已发布作品数；右侧表格为当前筛选结果（含待审核/驳回，仅管理端可见）。</div>
      </aside>

      <section class="table-panel" v-loading="loading">
        <div class="table-head">
          <span class="tt">{{ selectedBookTitle || categoryName || '全部' }} · 书目治理</span>
          <span class="tag green">通过 {{ statusCount('approved') }}</span>
          <span class="tag orange">待审核 {{ statusCount('pending') }}</span>
          <span class="tag red">驳回 {{ statusCount('rejected') }}</span>
          <span class="spacer" />
          <span class="head-hint">状态操作需 fabushi:book:manage</span>
        </div>
        <table class="data-table">
          <thead><tr><th>书目</th><th>作者 / 版本 / 页数</th><th>状态</th><th>电子书</th><th>提交人 / 时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="row in books" :key="row.id">
              <td class="title-cell">{{ row.title }}</td>
              <td>{{ row.author || '—' }}<template v-if="row.editionType"> · {{ editionLabel(row.editionType) }}</template><template v-if="row.pages"> · {{ row.pages }} 页</template></td>
              <td><span class="status" :class="row.status">{{ statusText(row.status) }}</span></td>
              <td><span v-if="row.pdfFileId" class="pdf-tag">已挂载</span><span v-else class="muted">未挂载</span></td>
              <td>{{ row.submitterName || '—' }} · {{ formatDate(row.createdAt) }}</td>
              <td class="actions">
                <template v-if="userStore.hasPermission('fabushi:book:manage')">
                  <el-button v-if="row.status === 'pending'" link type="primary" @click="doApprove(row)">通过</el-button>
                  <el-button v-if="row.status === 'pending'" link type="warning" @click="openReject(row)">驳回</el-button>
                  <el-button v-if="row.status === 'approved'" link type="primary" @click="openPdf(row)">{{ row.pdfFileId ? '换PDF' : '挂PDF' }}</el-button>
                  <el-button v-if="row.status === 'approved'" link type="primary" @click="openSections(row)">章节</el-button>
                  <el-button v-if="row.status === 'approved'" link type="danger" @click="doDisable(row)">停用</el-button>
                  <el-button v-if="row.status === 'rejected'" link type="primary" @click="showRejectReason(row)">查看原因</el-button>
                  <el-button link type="danger" @click="doDelete(row)">删除</el-button>
                </template>
                <span v-else class="muted">只读</span>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!loading && !books.length" class="empty">当前筛选暂无书目</div>
        <div class="pager">
          <span>共 {{ total }} 条</span>
          <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" :page-sizes="pageSizes" layout="sizes, prev, pager, next" size="small" @change="loadData" />
        </div>
      </section>
    </div>

    <el-dialog v-model="submitVisible" title="提交书目" width="480px">
      <el-form label-width="70px">
        <el-form-item label="类别" required>
          <el-select v-model="submitForm.categoryCode" placeholder="选择类别" style="width: 100%">
            <el-option v-for="c in categories" :key="c.dictValue" :label="c.dictLabel" :value="c.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="书名" required><el-input v-model="submitForm.title" placeholder="书名" /></el-form-item>
        <el-form-item label="作者"><el-input v-model="submitForm.author" /></el-form-item>
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

    <el-dialog v-model="rejectVisible" title="驳回书目" width="420px">
      <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="驳回原因（必填，留痕）" />
      <template #footer>
        <el-button @click="rejectVisible = null">取消</el-button>
        <el-button type="warning" :loading="actionLoading" @click="doReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="sectionVisible" title="章节结构" width="560px">
      <div class="section-head">
        <span>{{ sectionBook?.title }} · 序 / 部分 / 章 / 节</span>
        <div class="section-add">
          <el-input v-model="sectionTitle" placeholder="章节标题" size="small" style="width: 180px" />
          <el-select v-model="sectionLevel" size="small" style="width: 100px">
            <el-option label="序" value="preface" /><el-option label="部分" value="part" /><el-option label="章" value="chapter" /><el-option label="节" value="section" />
          </el-select>
          <el-button size="small" type="primary" @click="addSection">添加</el-button>
        </div>
      </div>
      <div v-for="item in sections" :key="item.id" class="section-row">
        <span class="section-level">{{ sectionLevelLabel(item.levelCode) }}</span>
        <span class="section-title-cell">{{ item.title }}</span>
        <el-button v-if="userStore.hasPermission('fabushi:book:manage')" link type="danger" @click="removeSection(item)">删除</el-button>
      </div>
      <div v-if="!sections.length" class="empty">暂无章节，先添加序/部分/章/节</div>
    </el-dialog>

    <el-dialog v-model="pdfVisible" title="挂载电子书 PDF" width="460px">
      <div class="upload-dropzone" @click="pickPdf">
        {{ pdfUploading ? '上传中…' : '点击选择 PDF（≤100MB，上传到法布施空间）' }}
        <input ref="pdfInputRef" type="file" accept=".pdf" style="display:none" @change="handlePdfChosen" />
      </div>
      <p class="pdf-hint">当前书目：{{ pdfVisible ? pdfVisible.title : '' }}</p>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { useUserStore } from '@/store/user'
import { getDictData } from '@/api/system'
import {
  getFabushiBooks, submitFabushiBook, approveFabushiBook, rejectFabushiBook, disableFabushiBook, attachBookPdf, deleteFabushiBook,
  getFabushiSections, createFabushiSection, deleteFabushiSection,
} from '@/api/fabushi'

const userStore = useUserStore()
const loading = ref(false)
const actionLoading = ref(false)
const books = ref([])
const allBooks = ref([])
const total = ref(0)
const categories = ref([])
const editions = ref([])
const pageSizes = [10, 20, 50]
const query = ref({ status: 'all', categoryCode: '', keyword: '', current: 1, size: 10 })
const leftKeyword = ref('')
const selectedBookId = ref(null)
const submitVisible = ref(false)
const submitForm = ref({ categoryCode: '', title: '', author: '', editionType: '' })
const rejectVisible = ref(null)
const rejectReason = ref('')
const pdfVisible = ref(null)
const pdfUploading = ref(false)
const pdfInputRef = ref(null)
const sectionVisible = ref(false)
const sectionBook = ref(null)
const sections = ref([])
const sectionTitle = ref('')
const sectionLevel = ref('chapter')

const categoryCounts = computed(() => allBooks.value.reduce((map, book) => {
  const key = book.categoryCode || ''
  map[key] = (map[key] || 0) + 1
  return map
}, {}))
const pendingCount = computed(() => allBooks.value.filter((book) => book.status === 'pending').length)
const categoryName = computed(() => categories.value.find((c) => c.dictValue === query.value.categoryCode)?.dictLabel || '')
const selectedBookTitle = computed(() => allBooks.value.find((book) => book.id === selectedBookId.value)?.title || '')
const leftBooks = computed(() => allBooks.value
  .filter((book) => (!query.value.categoryCode || book.categoryCode === query.value.categoryCode))
  .filter((book) => !leftKeyword.value || String(book.title).includes(leftKeyword.value)))

const statusMap = { approved: '已通过', pending: '待审核', rejected: '已驳回', disabled: '已停用' }
const statusText = (s) => statusMap[s] || s
const formatDate = (value) => (value ? String(value).replace('T', ' ').slice(0, 10) : '—')
const editionLabel = (value) => editions.value.find((e) => e.dictValue === value)?.dictLabel || value
const statusCount = (status) => allBooks.value.filter((book) => book.status === status).length

const loadDicts = async () => {
  const [cats, eds] = await Promise.all([getDictData('fabushi_category'), getDictData('fabushi_edition_type')])
  categories.value = cats.data || []
  editions.value = eds.data || []
}

const loadAll = async () => {
  const { data } = await getFabushiBooks({ status: 'all', current: 1, size: 200 })
  allBooks.value = (data && data.records) || []
}

const loadData = async () => {
  loading.value = true
  try {
    const params = { ...query.value }
    const { data } = await getFabushiBooks(params)
    books.value = (data && data.records) || []
    total.value = Number(data && data.total) || 0
  } finally { loading.value = false }
}

const onFilterChange = () => { query.value.current = 1; selectedBookId.value = null; loadData() }
const resetFilters = () => { query.value = { status: 'all', categoryCode: '', keyword: '', current: 1, size: 10 }; selectedBookId.value = null; loadData() }
const selectCategory = (value) => { query.value.categoryCode = value; query.value.current = 1; selectedBookId.value = null; query.value.keyword = ''; loadData() }
const selectBook = (book) => { selectedBookId.value = book.id; query.value.keyword = book.title; query.value.current = 1; loadData() }
const showPending = () => { query.value.status = 'pending'; query.value.current = 1; loadData() }
const showRejectReason = (row) => ElMessageBox.alert(row.rejectReason || '—', '驳回原因', { confirmButtonText: '知道了' })
const sectionLevelLabel = (value) => ({ preface: '序', part: '部分', chapter: '章', section: '节' }[value] || value)
const openSections = async (row) => {
  sectionBook.value = row; sectionVisible.value = true; sectionTitle.value = ''
  const { data } = await getFabushiSections(row.id); sections.value = (data && data.sections) || []
}
const addSection = async () => {
  if (!sectionTitle.value.trim()) { ElMessage.warning('章节标题不能为空'); return }
  await createFabushiSection(sectionBook.value.id, { title: sectionTitle.value.trim(), levelCode: sectionLevel.value })
  sectionTitle.value = ''
  await openSections(sectionBook.value)
}
const removeSection = async (item) => {
  await deleteFabushiSection(sectionBook.value.id, item.id)
  await openSections(sectionBook.value)
}

const doSubmit = async () => {
  if (!submitForm.value.categoryCode) { ElMessage.warning('请选择类别'); return }
  if (!submitForm.value.title.trim()) { ElMessage.warning('书名不能为空'); return }
  actionLoading.value = true
  try {
    await submitFabushiBook(submitForm.value)
    ElMessage.success('已提交，待管理员审核')
    submitVisible.value = false
    submitForm.value = { categoryCode: '', title: '', author: '', editionType: '' }
    await Promise.all([loadData(), loadAll()])
  } finally { actionLoading.value = false }
}

const doApprove = async (row) => { await approveFabushiBook(row.id); ElMessage.success('已通过'); await Promise.all([loadData(), loadAll()]) }
const openReject = (row) => { rejectVisible.value = row; rejectReason.value = '' }
const doReject = async () => {
  if (!rejectReason.value.trim()) { ElMessage.warning('驳回原因不能为空'); return }
  actionLoading.value = true
  try { await rejectFabushiBook(rejectVisible.value.id, rejectReason.value.trim()); ElMessage.success('已驳回'); rejectVisible.value = null; await Promise.all([loadData(), loadAll()]) } finally { actionLoading.value = false }
}
const doDisable = async (row) => { await disableFabushiBook(row.id); ElMessage.success('已停用'); await Promise.all([loadData(), loadAll()]) }
// 【2026-11-16 用户令】删除书目: 二次确认→软删(关联心得/书架/进度保留, 列表自动过滤)
const doDelete = async (row) => {
  await ElMessageBox.confirm(`确定删除书目「${row.title}」？删除后不再展示，其下心得与阅读记录保留。`, '删除书目', {
    confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
  })
  await deleteFabushiBook(row.id)
  ElMessage.success('已删除')
  await Promise.all([loadData(), loadAll()])
}
const openPdf = (row) => { pdfVisible.value = row }
const pickPdf = () => pdfInputRef.value?.click()
const handlePdfChosen = async (evt) => {
  const file = evt.target.files && evt.target.files[0]
  if (!file) return
  pdfUploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('scope', 'fabushi')
    const res = await request.post('/file/upload', formData)
    await attachBookPdf(pdfVisible.value.id, res.data && res.data.id)
    ElMessage.success('PDF 已挂载')
    pdfVisible.value = null
    await Promise.all([loadData(), loadAll()])
  } finally { pdfUploading.value = false }
}

onMounted(async () => { await Promise.all([loadDicts(), loadAll()]); await loadData() })
</script>

<style scoped>
.fabushi-book { display: flex; flex-direction: column; gap: 10px; }
.caps { display: flex; gap: 4px; padding: 4px; overflow-x: auto; border-radius: var(--radius-md); background: var(--el-fill-color-light); }
.cap { flex: 0 0 auto; padding: 7px 10px; border: 0; border-radius: var(--radius-sm); color: var(--text-secondary); background: transparent; font-size: 12px; font-weight: 600; cursor: pointer; white-space: nowrap; }
.cap.active { color: var(--el-color-white); background: var(--hero-gradient); }
.cap .n { margin-left: 3px; padding: 1px 5px; border-radius: var(--radius-pill); background: var(--el-fill-color-darker); font-size: 10px; }
.cap.active .n { background: rgba(255, 255, 255, .25); }
.filter { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; padding: 9px 12px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-bg-color); }
.fl { color: var(--text-secondary); font-size: 12px; }
.spacer { flex: 1; }
.pending-btn { color: var(--el-color-warning-dark-2); border-color: var(--el-color-warning-light-5); background: var(--el-color-warning-light-9); }
.book-layout { display: grid; grid-template-columns: 256px minmax(0, 1fr); gap: 12px; align-items: start; }
.sub-panel { display: flex; flex-direction: column; gap: 8px; padding: 12px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-bg-color); }
.sub-title { color: var(--text-primary); font-size: 13px; font-weight: 700; }
.sub-row { display: flex; align-items: center; justify-content: space-between; width: 100%; padding: 8px 9px; border: 0; border-radius: var(--radius-sm); color: var(--text-secondary); background: transparent; font-size: 12px; text-align: left; cursor: pointer; }
.sub-row:hover { background: var(--el-fill-color-light); }
.sub-row.active { color: var(--primary-color); background: var(--el-color-primary-light-9); font-weight: 700; }
.sub-row .cnt { color: var(--text-muted); font-size: 10px; }
.sub-empty { padding: 10px 0; color: var(--text-placeholder); font-size: 11px; }
.sub-note { margin-top: 4px; color: var(--text-placeholder); font-size: 10.5px; line-height: 1.6; }
.table-panel { overflow: hidden; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-bg-color); }
.table-head { display: flex; align-items: center; gap: 8px; padding: 10px 12px; border-bottom: 1px solid var(--border-light); }
.tt { color: var(--text-primary); font-size: 13px; font-weight: 700; }
.head-hint { color: var(--text-placeholder); font-size: 10.5px; }
.tag { padding: 1px 6px; border-radius: var(--radius-sm); font-size: 10px; }
.tag.green { color: var(--el-color-success); background: var(--el-color-success-light-9); }
.tag.orange { color: var(--el-color-warning-dark-2); background: var(--el-color-warning-light-9); }
.tag.red { color: var(--el-color-danger); background: var(--el-color-danger-light-9); }
.data-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.data-table th { padding: 8px 12px; color: var(--text-placeholder); background: var(--el-fill-color-lighter); font-size: 11px; font-weight: 500; text-align: left; }
.data-table td { padding: 9px 12px; color: var(--text-secondary); border-top: 1px solid var(--border-light); }
.title-cell { color: var(--text-primary) !important; font-weight: 600; }
.status { padding: 1px 6px; border-radius: var(--radius-sm); font-size: 10px; }
.status.approved { color: var(--el-color-success); background: var(--el-color-success-light-9); }
.status.pending { color: var(--el-color-warning-dark-2); background: var(--el-color-warning-light-9); }
.status.rejected { color: var(--el-color-danger); background: var(--el-color-danger-light-9); }
.status.disabled { color: var(--text-muted); background: var(--el-fill-color-light); }
.pdf-tag { color: var(--el-color-success); font-size: 11px; }
.muted { color: var(--text-placeholder); font-size: 11px; }
.actions { white-space: nowrap; }
.empty { padding: 24px; color: var(--text-placeholder); text-align: center; font-size: 12px; }
.pager { display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; border-top: 1px solid var(--border-light); color: var(--text-muted); font-size: 11px; }
.upload-dropzone { padding: 28px; border: 1px dashed var(--el-border-color); border-radius: var(--radius-sm); color: var(--text-secondary); text-align: center; cursor: pointer; }
.section-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 10px; color: var(--text-secondary); font-size: 12px; }
.section-add { display: flex; gap: 6px; }
.section-row { display: flex; align-items: center; gap: 8px; padding: 8px 0; border-bottom: 1px solid var(--border-light); }
.section-level { width: 38px; color: var(--primary-color); font-size: 11px; }
.section-title-cell { flex: 1; color: var(--text-primary); font-size: 12px; }
.pdf-hint { margin-top: 8px; color: var(--text-muted); font-size: 12px; }
</style>
