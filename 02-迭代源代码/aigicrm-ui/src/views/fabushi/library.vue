<template>
  <div class="fx-page fabushi-library">
    <!-- 【2026-11-26 按 PC 原型 v8】P4 书目浏览：页面直接摆在灰画布上（没有页头标题块），
         上面是筛选条，下面左栏类别 + 右侧两列书卡。 -->
    <div class="filter-card">
      <span class="fl">搜索</span>
      <!-- 接口只按书名搜（参数没动）；作者 / 出版社 / 版本类型命中的书由前端从已加载的书目里补上，见 extraMatches -->
      <el-input v-model="keyword" clearable placeholder="书名 / 作者 / 版本" style="width: 200px" @keyup.enter="onSearch">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <span class="fl">排序</span>
      <el-select v-model="sort" style="width: 100px" @change="onFrontFilter">
        <el-option label="默认排序" value="latest" />
        <el-option label="书名排序" value="title" />
      </el-select>
      <span class="fl">电子书</span>
      <el-select v-model="ebook" style="width: 100px" @change="onFrontFilter">
        <el-option label="全部" value="all" />
        <el-option label="有电子书" value="yes" />
        <el-option label="暂无电子书" value="no" />
      </el-select>
      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button plain @click="resetFilters">重置</el-button>
      <span class="spacer" />
      <!-- 原型这一格只有“＋ 提交书目”。「我的觉醒」是原页头上的入口，改版后留在这里，做成不抢眼的文字按钮 -->
      <el-button link @click="router.push('/fabushi/shelf')">我的觉醒</el-button>
      <el-button v-if="canSubmit" plain @click="openSubmit">＋ 提交书目</el-button>
    </div>

    <div class="lib-layout">
      <aside class="cat-list" aria-label="书目类别">
        <button type="button" class="cat-row" :class="{ active: !categoryCode }" @click="selectCategory('')">
          <span>全部书目</span><span class="cnt">{{ allBooks.length }}</span>
        </button>
        <button
          v-for="category in categories"
          :key="category.dictValue"
          type="button"
          class="cat-row"
          :class="{ active: categoryCode === category.dictValue }"
          @click="selectCategory(category.dictValue)"
        >
          <span>{{ category.dictLabel }}</span>
          <span class="cnt">{{ categoryCounts[category.dictValue] || 0 }}</span>
        </button>
      </aside>

      <section class="book-main" v-loading="loading">
        <div class="book-grid">
          <!-- 一张卡 = 一个书名；同名的不 同版本在版本链里，点一个切过去 -->
          <article v-for="card in bookCards" :key="card.key" class="bcard" :class="{ hl: isFocused(card) }">
            <div class="book-spine">{{ card.title.slice(0, 4) }}</div>
            <div class="bcard-main">
              <div class="bcard-title">《{{ card.title }}》<span v-if="currentOf(card).pdfFileId" class="tag blue">电子书</span></div>
              <!-- 元信息：作者 · 出版社 · 版本类型 · 页数，有哪项显示哪项；都没填时显示类别名 -->
              <div v-if="metaLine(currentOf(card))" class="bcard-meta">{{ metaLine(currentOf(card)) }}</div>
              <div class="bcard-tags">
                <button
                  v-for="(item, index) in card.versions"
                  :key="item.id"
                  type="button"
                  class="ver-chip"
                  :class="{ active: item.id === currentOf(card).id }"
                  :aria-pressed="item.id === currentOf(card).id"
                  @click="switchVersion(card, item)"
                >{{ card.chips[index] }}</button>
                <span class="tag gray">共 {{ card.versions.length }} 版本</span>
              </div>
              <div class="bcard-acts">
                <!-- 原型的两颗主操作在前；原有的“写心得 / 加入书架”留在操作行最右，做成文字按钮 -->
                <el-button v-if="currentOf(card).pdfFileId" size="small" type="primary" @click="openReader(currentOf(card))">在线阅读</el-button>
                <span v-else class="no-pdf">暂无电子书</span>
                <el-button size="small" plain @click="quickPublish(currentOf(card))">去诵读</el-button>
                <span class="spacer" />
                <el-button link size="small" @click="writePost(currentOf(card))">写心得</el-button>
                <el-button link size="small" @click="addShelf(currentOf(card))">加入书架</el-button>
              </div>
            </div>
          </article>
          <el-empty v-if="!loading && !bookCards.length" :description="hasFilter ? '没有符合条件的书目' : '暂无书目'" />
        </div>
        <div class="library-pager">
          <el-pagination
            v-model:current-page="current"
            :page-size="size"
            :total="pagerTotal"
            layout="prev, pager, next"
            @current-change="onPageChange"
          />
        </div>
      </section>
    </div>

    <!-- ＋ 提交书目（原型筛选条右侧的入口，小程序端早有；PC 此前只有治理页能新增）。
         字段与治理页「新增书目」一致；提交后是待审核态，管理员通过后才会出现在上面的书卡里 -->
    <el-dialog v-model="submitVisible" title="提交书目" width="480px" class="fx-dialog" :close-on-click-modal="false">
      <!-- 原型点这个入口时的提示是“提交书目后进入待审核队列”，去掉权限码后放在弹窗里：不说明的话，提交完在书卡里找不到会以为没成功 -->
      <p class="submit-hint">提交后进入待审核队列，管理员审核通过后才会出现在书目浏览里。</p>
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
        <div class="dialog-footer">
          <el-button @click="submitVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="doSubmit">提交</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 去诵读：现成的发布器，预选书卡当前那一版 -->
    <FabushiPublishDialog
      v-model="publishVisible"
      :default-scope="publishScope"
      :preset-book-id="presetBookId"
      @published="onPublished"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getFabushiBooks, addFabushiShelf, submitFabushiBook } from '@/api/fabushi'
import { getDictData } from '@/api/system'
import { useUserStore } from '@/store/user'
import FabushiPublishDialog from '@/components/fabushi/FabushiPublishDialog.vue'
import '@/assets/fabushi-v8.css'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
// 「＋ 提交书目」「去诵读」走的都是 fabushi:create（与治理页的「新增书目」、发布器是同一个门）
const canSubmit = computed(() => userStore.hasPermission('fabushi:create'))

const categories = ref([])
const editions = ref([])
const editionLabels = ref({})
const categoryCode = ref('')
const keyword = ref('')
/** 点过「查询」的关键词：输入框里的字要点查询 / 回车才生效（原有行为），列表只认这一份 */
const appliedKeyword = ref('')
const sort = ref('latest')
/** 电子书：all 全部 / yes 有电子书 / no 暂无电子书。接口不认这一项，只在已加载的书目上筛 */
const ebook = ref('all')
const allBooks = ref([])
const books = ref([])
const total = ref(0)
const current = ref(1)
const size = 12
const loading = ref(false)
/** 版本链上用户点过的那一版（按书名记）；没点过时按 currentOf 的规则自动落一版 */
const activeVersions = ref({})

const editionLabel = (value) => editionLabels.value[value] || value

/** 元信息行：作者 · 出版社 · 版本类型 · 页数（有哪项显示哪项）；都没填时显示类别名 */
const versionText = (book) => [book.author, book.publisher, book.editionType ? editionLabel(book.editionType) : '', book.pages ? `${book.pages} 页` : '']
  .filter(Boolean).join(' · ')
const metaLine = (book) => versionText(book) || book.categoryName || ''
/** 版本链上每个章的字：作者（没有就用出版社）· 版本类型，照原型“陈鼓应·注释本”“中华书局·原版”的写法；都没填写“未标注版本” */
const chipText = (book) => {
  const who = book.author || book.publisher
  const edition = book.editionType ? editionLabel(book.editionType) : ''
  return who && edition ? `${who}·${edition}` : (who || edition || '未标注版本')
}

const categoryCounts = computed(() => allBooks.value.reduce((counts, book) => {
  const key = book.categoryCode || ''
  counts[key] = (counts[key] || 0) + 1
  return counts
}, {}))

/* ---------- 书卡：一张卡 = 一个书名，同名的不 同版本在版本链里 ---------- */

/** 关键词比对不分大小写（接口的 LIKE 也不分） */
const norm = (value) => String(value || '').toLowerCase()
const titleKey = (book) => norm(book.title).trim()
const inCategory = (book) => !categoryCode.value || book.categoryCode === categoryCode.value
/** 一本书能被搜到的字：书名 / 作者 / 出版社 / 版本类型（搜索框写的是“书名 / 作者 / 版本”） */
const searchWords = (book) => [book.title, book.author, book.publisher, book.editionType ? editionLabel(book.editionType) : ''].map(norm)
const matchesKeyword = (book, kw) => searchWords(book).some((word) => word.includes(kw))
const byTitle = (a, b) => String(a.title || '').localeCompare(String(b.title || ''), 'zh-CN')

const appliedKw = computed(() => norm(appliedKeyword.value))
const hasFilter = computed(() => Boolean(appliedKw.value) || ebook.value !== 'all' || sort.value !== 'latest')
/** 排序、电子书筛选接口不认：这两项只要有一项不是默认，列表就在已加载的全量书目上算，接口那一页用不上 */
const frontMode = computed(() => ebook.value !== 'all' || sort.value !== 'latest')

/** 版本链要跨页：同一书名下、当前类别里的全部版本，取自已加载的全量书目（接口顺序，最新在前） */
const versionIndex = computed(() => {
  const index = new Map()
  for (const book of allBooks.value) {
    if (!inCategory(book)) continue
    const key = titleKey(book)
    if (!index.has(key)) index.set(key, [])
    index.get(key).push(book)
  }
  return index
})
const versionsOf = (book) => versionIndex.value.get(titleKey(book)) || [book]
/** 电子书筛选按“这本书”算：任一版本挂了电子书就算有 */
const matchesEbook = (book) => ebook.value === 'all' || versionsOf(book).some((item) => item.pdfFileId) === (ebook.value === 'yes')

/** 把一组版本记录并成书卡（按首次出现的顺序）；每张卡带上完整的版本链和链上每个章的字 */
const groupTitles = (list) => {
  const cards = []
  for (const book of list) {
    const key = titleKey(book)
    if (cards.some((card) => card.key === key)) continue
    const versions = versionsOf(book)
    // 同一书名下几版的字都一样（都没填作者 / 版本）时，后面的加个序号，免得几个章分不清
    const seen = new Map()
    const chips = versions.map((item) => {
      const base = chipText(item)
      const nth = (seen.get(base) || 0) + 1
      seen.set(base, nth)
      return nth > 1 ? `${base} ${nth}` : base
    })
    cards.push({ key, title: String(book.title || '').trim(), versions, chips })
  }
  return cards
}

/** 接口只按书名搜。作者 / 出版社 / 版本类型命中的记录接口不返回，前端从已加载的全量书目里补上，接在接口结果后面 */
const extraMatches = computed(() => (appliedKw.value
  ? allBooks.value.filter((book) => inCategory(book) && !norm(book.title).includes(appliedKw.value) && matchesKeyword(book, appliedKw.value))
  : []))

/** 默认视图：接口分页的那一页原样用；补上的记录排在接口结果（共 total 条）之后，本页只取落进这一页范围的那一段 */
const serverCards = computed(() => {
  const from = Math.max(0, (current.value - 1) * size - total.value)
  const to = Math.max(0, current.value * size - total.value)
  return groupTitles(books.value.concat(extraMatches.value.slice(from, to)))
})

/** 前端筛选激活时：全部结果（按书名并成卡），翻页在它上面切 */
const clientCards = computed(() => {
  const list = allBooks.value.filter((book) => inCategory(book) && matchesEbook(book) && (!appliedKw.value || matchesKeyword(book, appliedKw.value)))
  return groupTitles(sort.value === 'title' ? [...list].sort(byTitle) : list)
})

const bookCards = computed(() => (frontMode.value
  ? clientCards.value.slice((current.value - 1) * size, current.value * size)
  : serverCards.value))
/** 分页条的总数：默认视图按版本记录数（接口总数 + 前端补上的）；前端筛选激活时按书卡数 */
const pagerTotal = computed(() => (frontMode.value ? clientCards.value.length : total.value + extraMatches.value.length))

/** 路由带的 ?bookId=（认知广场「书目详情」进来）：要定位的那一版 */
const focusBookId = computed(() => {
  const id = Number(route.query.bookId)
  return Number.isFinite(id) && id > 0 ? id : null
})

/** 书卡当前显示的那一版：用户点过的 › 路由定位的 › 搜索命中的 › 有电子书的（筛“有电子书”时）› 版本链第一个 */
const currentOf = (card) => {
  const find = (predicate) => card.versions.find(predicate)
  return find((item) => item.id === activeVersions.value[card.key])
    || find((item) => item.id === focusBookId.value)
    || (appliedKw.value && find((item) => matchesKeyword(item, appliedKw.value)))
    || (ebook.value === 'yes' && find((item) => item.pdfFileId))
    || card.versions[0]
}
const switchVersion = (card, book) => {
  activeVersions.value = { ...activeVersions.value, [card.key]: book.id }
}
const isFocused = (card) => card.versions.some((item) => item.id === focusBookId.value)

/* ---------- 取数（接口与参数都没动） ---------- */

const loadCategories = async () => {
  try {
    const [{ data: categoryData }, { data: editionData }] = await Promise.all([
      getDictData('fabushi_category'),
      getDictData('fabushi_edition_type'),
    ])
    categories.value = Array.isArray(categoryData) ? categoryData : []
    editions.value = Array.isArray(editionData) ? editionData : []
    editionLabels.value = Object.fromEntries((editionData || []).map((item) => [item.dictValue, item.dictLabel]))
  } catch (e) {
    categories.value = []
    editions.value = []
    editionLabels.value = {}
  }
}

const loadAllBooks = async () => {
  try {
    const { data } = await getFabushiBooks({ status: 'approved', current: 1, size: 200 })
    allBooks.value = (data && data.records) || []
  } catch (e) {
    allBooks.value = []
  }
}

const loadBooks = async () => {
  loading.value = true
  try {
    const { data } = await getFabushiBooks({
      categoryCode: categoryCode.value || undefined,
      keyword: appliedKeyword.value || undefined,
      status: 'approved',
      current: current.value,
      size,
    })
    books.value = (data && data.records) || []
    total.value = Number(data && data.total) || 0
  } finally {
    loading.value = false
  }
}

/** 取当前页。前端筛选激活时那一页用不上，就不发请求（回到默认视图的每个入口都会重新取） */
const loadPage = () => (frontMode.value ? Promise.resolve() : loadBooks())

/* ---------- 筛选条 / 类别 / 翻页 ---------- */

const onSearch = () => {
  appliedKeyword.value = keyword.value.trim()
  current.value = 1
  activeVersions.value = {}
  return loadPage()
}

const resetFilters = () => {
  keyword.value = ''
  sort.value = 'latest'
  ebook.value = 'all'
  return onSearch()
}

/** 排序 / 电子书：只在前端已有的书目上做 */
const onFrontFilter = () => {
  current.value = 1
  activeVersions.value = {}
  return loadPage()
}

const selectCategory = (value) => {
  categoryCode.value = value
  current.value = 1
  return loadPage()
}

const onPageChange = () => loadPage()

/* ---------- 书卡上的操作 ---------- */

const openReader = (book) => {
  if (!book.pdfFileId) return
  router.push(`/fabushi/read/${book.id}`)
}

const writePost = (book) => router.push({ path: '/fabushi/feed', query: { bookId: book.id } })

const addShelf = async (book) => {
  await addFabushiShelf(book.id)
  ElMessage.success('已加入书架')
}

/** 去诵读 = 打开发布器，预选这一版（与认知广场“今日推荐书目”卡上的同一个做法） */
const publishVisible = ref(false)
const publishScope = ref('songdu')
const presetBookId = ref(null)
const quickPublish = (book) => {
  if (!canSubmit.value) {
    ElMessage.warning('当前账号没有发布权限')
    return
  }
  publishScope.value = 'songdu'
  presetBookId.value = book.id
  publishVisible.value = true
}
const onPublished = () => Promise.all([loadAllBooks(), loadPage()])

/* ---------- ＋ 提交书目 ---------- */

const emptySubmitForm = () => ({ categoryCode: '', title: '', author: '', editionType: '' })
const submitVisible = ref(false)
const submitting = ref(false)
const submitForm = ref(emptySubmitForm())

const openSubmit = () => {
  if (canSubmit.value) submitVisible.value = true
}

/** 字段与治理页「新增书目」给 submitFabushiBook 的一致：类别、书名、作者、版本类型 */
const doSubmit = async () => {
  if (!canSubmit.value || submitting.value) return
  const form = submitForm.value
  if (!form.categoryCode) { ElMessage.warning('请选择类别'); return }
  const title = String(form.title || '').trim()
  if (!title) { ElMessage.warning('书名不能为空'); return }
  submitting.value = true
  try {
    await submitFabushiBook({
      categoryCode: form.categoryCode,
      title,
      author: String(form.author || '').trim(),
      editionType: form.editionType || '',
    })
    ElMessage.success('已提交，待管理员审核')
    submitVisible.value = false
    submitForm.value = emptySubmitForm()
    await Promise.all([loadAllBooks(), loadPage()])
  } catch (e) {
    // 失败提示由 request 拦截器统一弹出；弹窗留着，填的内容不丢
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadCategories(), loadAllBooks()])
  // 路由带 ?bookId=（认知广场「书目详情」）：定位到这本书——搜索框带上书名（列表只剩这一书名），
  // 这一版在版本链里选中（currentOf），书卡高亮（isFocused）
  const focus = allBooks.value.find((book) => book.id === focusBookId.value)
  if (focus) {
    keyword.value = String(focus.title || '').trim()
    appliedKeyword.value = keyword.value
    current.value = 1
  }
  await loadPage()
})
</script>

<style scoped>
/* 通用件（筛选条 .filter-card、书脊 .book-spine、版本章 .ver-chip、标签 .tag）在 assets/fabushi-v8.css；
   这里只写 P4 自己的版式，数值取自原型的 .lib-layout / .cat-list / .cat-row / .book-grid / .bcard。
   原型里每个书脊各配一种底色是评审稿的即兴配色（写死的色值），这里统一用共享的书脊。 */
.fabushi-library { min-height: calc(100vh - 84px); } /* 与外壳对齐：顶栏 60px + 画布内边距 12px×2，让左栏像原型一样撑到底 */
.lib-layout { display: flex; flex: 1; gap: 10px; min-height: 0; }

.cat-list { display: flex; flex-direction: column; flex-shrink: 0; width: 230px; padding: 12px; overflow: hidden; border-radius: var(--fx-r-md); background: #fff; box-shadow: var(--shadow-card); }
.cat-row { display: flex; align-items: center; width: 100%; padding: 8px 10px; border: 0; border-radius: 6px; background: transparent; color: var(--text-secondary); font-size: 13px; line-height: 18px; text-align: left; cursor: pointer; font-family: inherit; }
.cat-row:hover { background: var(--fx-track); }
.cat-row.active { background: var(--el-color-primary-light-9); color: var(--primary-color); font-weight: 700; }
.cat-row .cnt { margin-left: auto; padding: 1px 7px; border-radius: var(--fx-r-pill); background: var(--fx-pill-gray-bg); color: var(--fx-pill-gray); font-size: 11px; font-variant-numeric: tabular-nums; }
.cat-row.active .cnt { background: rgba(29, 78, 216, .15); color: var(--primary-color); }

.book-main { display: flex; flex: 1; flex-direction: column; gap: 10px; min-width: 0; }
.book-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; align-content: start; }
.book-grid .el-empty { grid-column: 1 / -1; }

.bcard { display: flex; gap: 12px; padding: 13px 15px; border-radius: var(--fx-r-md); background: #fff; box-shadow: var(--shadow-card); }
.bcard.hl { box-shadow: 0 0 0 2px var(--primary-color), var(--shadow-card); }
/* 书脊上写书名前 4 个字，照原型排成一行：共享的书脊左右各有 3px 内边距，4 个字放不下会折成两行 */
.bcard .book-spine { padding: 0 0 5px; white-space: nowrap; }
.bcard-main { flex: 1; min-width: 0; }
.bcard-title { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; color: var(--text-primary); font-size: 14px; font-weight: 800; }
.bcard-meta { margin-top: 5px; color: var(--text-muted); font-size: 11.5px; }
.bcard-tags { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-top: 6px; }
.bcard-acts { display: flex; align-items: center; gap: 8px; margin-top: 9px; }
.bcard-acts .el-button { margin-left: 0; border-radius: 8px; } /* 小按钮：26px 高、圆角 8px（原型 .btn.sm） */
/* “写心得 / 加入书架”原型里没画：留在操作行最右，灰字，鼠标移上去才亮 */
.bcard-acts .el-button.is-link { color: var(--text-muted); }
.bcard-acts .el-button.is-link:hover { color: var(--primary-color); }
/* 没挂电子书：原型是一个灰字的描边小按钮，不可点，所以不用 el-button */
.no-pdf { display: inline-flex; align-items: center; flex-shrink: 0; height: 26px; padding: 0 11px; border: 1px solid var(--border-light); border-radius: 8px; color: var(--text-placeholder); font-size: 11.5px; white-space: nowrap; }

/* 分页条跟认知广场一样：不套卡片，靠右摆在书卡下面（只有一页时就是一个小小的“1”，不占地方） */
.library-pager { display: flex; justify-content: flex-end; }
.submit-hint { margin: 0 0 6px; color: var(--text-muted); font-size: 12px; line-height: 1.6; }
</style>
