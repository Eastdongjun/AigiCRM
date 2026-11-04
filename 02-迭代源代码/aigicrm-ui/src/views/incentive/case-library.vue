<template>
  <div class="case-page">
    <!-- 【2026-11-05 用户令"四项服务的定位是给客户展示案例库的"】PC 四项服务=案例库:
         照片墙案例展示面(封面图+客户+服务类别+日期), 点卡片灯箱左右翻案例全部照片/视频。
         数据= /incentive/customer-records 打散到记录级(同 MP 轻量页打散模式), 可见性=服务端分享制,
         本页零新码(路由 meta/菜单节点/数据面三方同码 incentive:view)。展示面不显示激励分值(内部口径)。 -->
    <div class="case-toolbar">
      <div class="case-head">
        <h3 class="case-title">案例库</h3>
        <p class="case-sub">四项服务案例展示 · 范围=我可看的服务记录 · 点卡片翻看案例照片</p>
      </div>
      <div class="case-filters">
        <el-input v-model="fCustomer" placeholder="客户名 / 关键字(相关产品)" clearable :prefix-icon="Search"
          class="case-kw" @keyup.enter="doSearch" @clear="doSearch" />
        <el-select v-model="fMainCat" placeholder="服务大类" clearable style="width:130px" @change="onMainCatChange">
          <el-option v-for="c in mainCats" :key="c.id" :label="c.categoryName" :value="c.id" />
        </el-select>
        <el-select v-model="fSubCat" placeholder="服务小类" clearable style="width:130px" @change="doSearch">
          <el-option v-for="n in subCats" :key="n" :label="n" :value="n" />
        </el-select>
        <el-date-picker v-model="fDateRange" type="daterange" range-separator="—" start-placeholder="开始"
          end-placeholder="结束" value-format="YYYY-MM-DD" style="width:240px" @change="doSearch" />
        <el-button type="primary" :icon="Search" @click="doSearch">查询</el-button>
      </div>
    </div>

    <div v-loading="loading" class="case-body">
      <template v-if="pagedCases.length">
        <div class="case-grid">
          <div v-for="c in pagedCases" :key="c.key" class="case-card" @click="openCase(c)">
            <div class="case-cover">
              <img v-if="c.cover && c.cover.type === 'image' && !thumbFailed.has(c.cover.id)"
                :src="thumbUrl(c.cover.id)" loading="lazy" @error="markThumbFailed(c.cover.id)" />
              <div v-else-if="c.cover && c.cover.type === 'video'" class="cover-face cover-vid">
                <el-icon :size="30"><VideoPlay /></el-icon>
              </div>
              <div v-else-if="c.cover" class="cover-face">
                <el-icon :size="26"><Document /></el-icon>
              </div>
              <div v-else class="cover-face cover-empty">{{ (c.categoryName || '案').charAt(0) }}</div>
              <span v-if="c.files.length > 1" class="cover-count">{{ c.files.length }} 个文件</span>
            </div>
            <div class="case-bd">
              <div class="case-cust" :title="c.customerName">{{ c.customerName }}</div>
              <div class="case-meta">
                <span class="case-cat">{{ c.categoryName }}</span>
                <span class="case-date">{{ c.date }}</span>
              </div>
              <div class="case-by">{{ c.period }} · {{ c.userName }}</div>
            </div>
          </div>
        </div>
        <div v-if="cases.length > pageSize" class="case-pager">
          <span class="case-pager-count">共 <b>{{ cases.length }}</b> 条案例</span>
          <el-pagination v-model:current-page="pageNum" :page-size="pageSize" :total="cases.length"
            :page-sizes="pageSizes(pageSize)" layout="sizes, prev, pager, next, jumper" size="small" background
            @size-change="onSizeChange" />
        </div>
      </template>
      <el-empty v-else-if="!loading" description="暂无案例" :image-size="90" />
    </div>

    <!-- 灯箱预览（图片/视频/文档, 与激励查询同源组件）: 点卡片从封面打开, 左右翻该案例全部文件 -->
    <SearchLightbox ref="lightbox" :thumb-failed="thumbFailed" @thumb-failed="markThumbFailed" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getCategoryTree } from '@/api/incentive'
import request from '@/utils/request'
import { cachedGet } from '@/utils/layout-cache'
import { paginate, pageSizes } from '@/utils/pagination'
import { Search, VideoPlay, Document } from '@element-plus/icons-vue'
import { getFiles, thumbUrl } from './search-helpers'
import SearchLightbox from './SearchLightbox.vue'

// ===== 筛选 =====
const fCustomer = ref(''), fMainCat = ref(null), fSubCat = ref(''), fDateRange = ref(null)
// 一级分类选项=分类树顶层(同激励查询口径); 二级=选中大类下的 children 名
const mainCats = ref([])
const subCats = computed(() => {
  const m = mainCats.value.find(c => c.id === fMainCat.value)
  return ((m && m.children) || []).map(c => c.categoryName)
})
function onMainCatChange() { fSubCat.value = ''; doSearch() }

// ===== 数据: 记录级打散(一次上传=一条案例) =====
const cases = ref([])
const loading = ref(false)
const thumbFailed = ref(new Set())
function markThumbFailed(id) { thumbFailed.value = new Set([...thumbFailed.value, Number(id)]) }

const pageNum = ref(1)
const pageSize = ref(12)
const onSizeChange = (size) => { pageSize.value = size; pageNum.value = 1 }
const pagedCases = computed(() => paginate(cases.value, pageNum.value, pageSize.value))

// 【M6 同款】请求序号守卫: 慢的旧响应后到不再覆盖新结果
let searchSeq = 0
async function doSearch() {
  pageNum.value = 1
  const p = {}
  if (fCustomer.value) { p.customerName = fCustomer.value; p.keyword = fCustomer.value }
  if (fMainCat.value) p.mainCategoryId = fMainCat.value
  if (fDateRange.value?.length === 2) { p.startDate = fDateRange.value[0]; p.endDate = fDateRange.value[1] }
  const seq = ++searchSeq
  loading.value = true
  try {
    const res = await request.get('/incentive/customer-records', { params: p })
    if (seq !== searchSeq) return
    const rows = (res.data || []).filter(c => c.records?.length > 0 && c.customerName)
    const flat = []
    rows.forEach(c => {
      c.records.forEach(r => {
        // 二级筛选: 客户端按记录 categoryName 过滤(同激励查询口径, 后端只收大类 id)
        if (fSubCat.value && r.categoryName !== fSubCat.value) return
        const files = getFiles(r)
        if (!files.length) return // 案例库=照片墙展示面, 无附件记录不进墙
        const cover = files.find(f => f.type === 'image') || files[0]
        flat.push({
          key: `${c.customerId || c.customerName}-${r.id}`,
          customerName: c.customerName,
          categoryName: r.categoryName,
          period: r.period,
          userName: r.userName,
          date: String(r.createdAt || '').slice(0, 10),
          createdAt: r.createdAt,
          files,
          cover
        })
      })
    })
    flat.sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')))
    cases.value = flat
  } catch (e) {
    if (seq === searchSeq) cases.value = []
  } finally {
    if (seq === searchSeq) loading.value = false
  }
}

// 灯箱: 从封面/点卡片打开, gallery=该案例全部文件(左右翻)
const lightbox = ref(null)
async function openCase(c) {
  thumbFailed.value = new Set()
  const start = c.cover || c.files[0]
  await lightbox.value?.open(start.id, start.type, start.label, c.files)
}

onMounted(async () => {
  try {
    const res = await cachedGet('categoryTree', getCategoryTree)
    mainCats.value = res.data || []
  } catch (e) { mainCats.value = [] }
  doSearch()
})
</script>

<style scoped>
.case-page { height: calc(100vh - 80px); display: flex; flex-direction: column }
.case-toolbar { display: flex; align-items: flex-end; justify-content: space-between; gap: 14px; flex-wrap: wrap; margin-bottom: 14px }
.case-title { margin: 0; font-size: 18px; color: var(--text-primary, #1f2d3d) }
.case-sub { margin: 4px 0 0; font-size: 12px; color: var(--text-muted, #909399) }
.case-filters { display: flex; align-items: center; gap: 8px; flex-wrap: wrap }
.case-kw { width: 220px }
.case-body { flex: 1; overflow: auto; min-height: 0 }
.case-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 14px }
.case-card { background: #fff; border: 1px solid #e4e7ed; border-radius: var(--radius-md, 10px); overflow: hidden; cursor: pointer; transition: box-shadow .2s, transform .2s }
.case-card:hover { box-shadow: 0 6px 18px rgba(23, 64, 118, .12); transform: translateY(-2px) }
.case-cover { position: relative; aspect-ratio: 4 / 3; background: #eef0f4; overflow: hidden }
.case-cover img { width: 100%; height: 100%; object-fit: cover; display: block }
.cover-face { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; color: #9aa3ad; background: linear-gradient(135deg, #eef2f7, #e3ecf7) }
.cover-face.cover-vid { background: linear-gradient(135deg, #1a1a2e, #2d2d44); color: #fff }
.cover-face.cover-empty { font-size: 34px; font-weight: 600; color: #fff; background: linear-gradient(135deg, #24486b, #173f76) }
.cover-count { position: absolute; right: 8px; bottom: 8px; font-size: 12px; color: #fff; background: rgba(23, 63, 118, .72); padding: 2px 8px; border-radius: 10px }
.case-bd { padding: 10px 12px 12px }
.case-cust { font-size: 14px; font-weight: 600; color: var(--text-primary, #1f2d3d); white-space: nowrap; overflow: hidden; text-overflow: ellipsis }
.case-meta { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 6px }
.case-cat { font-size: 12px; color: var(--el-color-primary); background: var(--el-color-primary-light-9, #ecf5ff); border-radius: 4px; padding: 1px 8px; max-width: 70%; white-space: nowrap; overflow: hidden; text-overflow: ellipsis }
.case-date { font-size: 12px; color: var(--text-muted, #909399) }
.case-by { margin-top: 4px; font-size: 12px; color: var(--text-muted, #909399) }
.case-pager { display: flex; align-items: center; justify-content: flex-end; gap: 12px; margin-top: 14px }
.case-pager-count { font-size: 12px; color: var(--text-muted, #909399) }
</style>
