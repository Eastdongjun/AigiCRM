<template>
  <div class="fabushi-feed">
    <div class="page-head">
      <div>
        <h1>认知广场</h1>
        <p>每日诵读 · 每日随施 · 再读一本</p>
      </div>
      <div v-if="canCreate" style="display:flex; gap:8px;">
        <el-button type="primary" @click="openPublish('songdu')">＋每日诵读</el-button>
        <el-button type="warning" plain @click="openPublish('suishi')">＋每日随施</el-button>
        <el-button type="success" plain @click="openPublish('zaidu')">＋再读一本</el-button>
      </div>
    </div>

    <div class="caps" role="tablist" aria-label="内容类型">
      <button
        v-for="tab in tabs"
        :key="tab.value"
        class="cap"
        :class="{ active: active === tab.value }"
        type="button"
        @click="switchTab(tab.value)"
      >
        {{ tab.label }} <span class="n">{{ counts[tab.value] || 0 }}</span>
      </button>
    </div>

    <!-- 【P2 2026-11-23】再读一本分区 = 按书浏览（D15）：左侧按「具体版本的书」罗列，
         右侧=该书全部成员按时间序上传的内容表格。与四项激励「按客户罗列→点进客户看记录」同构。 -->
    <template v-if="active === 'zaidu'">
      <div class="filter bb-filter">
        <span class="fl">书籍筛选</span>
        <el-input v-model="bookKeyword" clearable placeholder="书名 / 作者 / 版本" style="width: 200px" />
        <span class="fl">排序</span>
        <el-select v-model="bookSort" style="width: 130px">
          <el-option label="参与人数" value="members" />
          <el-option label="内容条数" value="posts" />
          <el-option label="书名" value="title" />
        </el-select>
        <el-button type="primary" @click="resetPage">查询</el-button>
        <el-button plain @click="resetByBook">重置</el-button>
        <span class="spacer" />
        <span class="bb-hint">按书罗列 → 点进每本书看每个人上传的内容（D15）</span>
      </div>

      <div class="bb-layout">
        <aside class="bb-left">
          <div class="bb-left-head">
            <b>书目总览 · {{ filteredBooks.length }} 本</b>
            <el-input v-model="bookListKeyword" size="small" clearable placeholder="搜书名" />
          </div>
          <div class="bb-left-list">
            <button
              v-for="book in sortedBooks" :key="book.id" type="button"
              class="bb-book" :class="{ active: bookFilter === book.id }"
              @click="selectBook(book)"
            >
              <span class="bb-name">
                {{ book.title }}
                <em>{{ versionLabel(book) }}</em>
              </span>
              <span class="bb-cnt">{{ book.memberCount || 0 }} 人</span>
            </button>
            <p v-if="!sortedBooks.length" class="bb-empty">暂无已审核书目</p>
          </div>
          <p class="bb-left-note">书名+作者+版本三级确定后才是一本「在读的书」（D2）；不同版本各自成行。</p>
        </aside>

        <section class="bb-right">
          <div class="bb-toolbar">
            <template v-if="selectedBook">
              <b class="bb-tt">{{ selectedBook.title }}</b>
              <span class="bb-tt-sub">{{ versionLabel(selectedBook) }}</span>
              <span v-if="selectedBook.pdfFileId" class="tag blue">电子书</span>
              <!-- 条数用当前查询的 total（=本页签口径），不用书级 postCount，避免与表格行数打架 -->
              <span class="tag green">参与 {{ selectedBook.memberCount || 0 }} 人 · 本区 {{ total }} 条</span>
            </template>
            <b v-else class="bb-tt">全部书目</b>
            <span class="spacer" />
            <el-button v-if="selectedBook" size="small" plain @click="goBookDetail">书目详情</el-button>
            <el-button v-if="selectedBook" size="small" type="primary" @click="quickPublish('zaidu', selectedBook.id)">＋我在本书上传</el-button>
          </div>

          <el-table :data="posts" v-loading="loading" size="small" class="bb-table" empty-text="该书还没有人上传内容">
            <el-table-column label="成员" width="84">
              <template #default="{ row }"><b>{{ row.userName || '—' }}</b></template>
            </el-table-column>
            <el-table-column label="章节" width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ row.sectionTitle || '—' }}</template>
            </el-table-column>
            <el-table-column label="内容摘要" min-width="200">
              <template #default="{ row }">
                <span class="bb-summary">{{ summaryOf(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="形式" width="104">
              <template #default="{ row }"><span class="bb-form">{{ formLabel(row) }}</span></template>
            </el-table-column>
            <el-table-column label="互动" width="132">
              <template #default="{ row }">
                <span class="bb-inter">木鱼 {{ row.likeCount || 0 }} · 评论 {{ row.commentCount || 0 }}<template v-if="row.recitationCount"> · 复诵 {{ row.recitationCount }}</template></span>
              </template>
            </el-table-column>
            <el-table-column label="时间" width="112">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="62" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openDetail(row.id)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="bb-pager">
            <el-pagination
              v-model:current-page="current"
              :page-size="size"
              :total="total"
              layout="total, prev, pager, next"
              @current-change="loadPosts"
            />
          </div>
        </section>
      </div>
    </template>

    <div v-else class="filter">
      <span class="fl">筛选</span>
      <el-select v-model="bookFilter" clearable placeholder="全部书目" style="width: 150px" @change="resetPage">
        <el-option v-for="book in bookOptions" :key="book.id" :label="book.title" :value="book.id" />
      </el-select>
      <el-input v-model="keyword" clearable placeholder="人名 / 书名 / 具体文字（服务端检索）" style="width: 280px" @keyup.enter="resetPage" />
      <span class="spacer" />
      <el-checkbox v-model="unreadOnly">只看未读</el-checkbox>
      <el-button v-if="canView" link type="primary" @click="resetFilters">重置</el-button>
    </div>

    <div v-if="active !== 'zaidu'" class="feed-layout">
      <section class="feed-list" v-loading="loading">
        <article v-for="post in visiblePosts" :key="post.id" class="moments-card" @click="openDetail(post.id)">
          <div class="mc-head">
            <div class="mc-avatar">{{ (post.userName || '法').slice(0, 1) }}</div>
            <div class="mc-main">
              <div class="mc-name">{{ post.userName || '法布施' }}<span class="tag" :class="post.scopeType">{{ typeLabel(post.scopeType) }}</span></div>
              <div class="mc-text">{{ post.title || '' }}</div>
              <div class="mc-text body">{{ post.content }}</div>
              <div v-if="(post.attachments || []).some((a) => a.fileType === 'image')" class="mc-imgs">
                <el-image
                  v-for="img in (post.attachments || []).filter((a) => a.fileType === 'image')"
                  :key="img.id"
                  class="mc-img"
                  :src="`/api/file/download/${img.fileId}?token=${encodeURIComponent(fileToken)}`"
                  :preview-src-list="(post.attachments || []).filter((a) => a.fileType === 'image').map((a) => `/api/file/download/${a.fileId}?token=${encodeURIComponent(fileToken)}`)"
                  preview-teleported
                  fit="cover"
                  @click.stop
                />
              </div>
              <div v-if="(post.attachments || []).some((a) => a.fileType === 'audio')" class="mc-audio">
                <audio v-for="a in (post.attachments || []).filter((x) => x.fileType === 'audio')" :key="a.id" :src="`/api/file/download/${a.fileId}?token=${encodeURIComponent(fileToken)}`" controls style="width:100%; height:32px;" @click.stop />
              </div>
              <div class="mc-meta">
                <span>{{ formatTime(post.createdAt) }}</span>
                <span v-if="bookName(post)" class="src">{{ bookName(post) }}</span>
                <span v-if="isNew(post)" class="new-badge">NEW</span>
                <span class="read" :class="{ unread: !post.readByMe }" style="margin-left:auto;">{{ post.readByMe ? '已读' : '未读' }}</span>
              </div>
            </div>
          </div>
          <div class="mc-acts">
            <span>🐟 木鱼 {{ post.likeCount || 0 }} · 评论 {{ post.commentCount || 0 }} · 🎤 复诵 {{ post.recitationCount || 0 }}</span>
            <span class="more" @click.stop>⋯</span>
          </div>
          <div v-if="post.likeNames" class="mc-inter"><span class="likers">木鱼：</span>{{ post.likeNames }}</div>
        </article>
        <el-empty v-if="!loading && !visiblePosts.length" description="暂无法布施内容" />
      </section>

      <aside class="feed-side">
        <!-- 【P1 2026-11-23】原型右栏第一位＝「我的觉醒 · 今日 x/3」三板块状态卡（D6）。
             数据源 GET /fabushi/awaken/status（早已存在，此前前端没用）。
             口径（如实标注，不臆造"今日"含义）：诵读=今日已发布诵读内容 / 随施=今日已发布随施 /
             再读=书架有「在读」书目（原型即如此计数：已诵 + 精读中 = 2/3）。 -->
        <section class="side-card awaken-card">
          <div class="side-title">
            我的觉醒<template v-if="userStore.realName"> · {{ userStore.realName }}</template>
            <span class="aw-score" :title="awakenRule">今日 {{ awakenDone }}/3</span>
          </div>

          <div class="aw-row">
            <span class="aw-label">每日诵读<em>晨 08:00</em></span>
            <span v-if="awaken.todayRecitations > 0" class="aw-chip done">已诵 · 今日 {{ awaken.todayRecitations }} 条</span>
            <button v-else type="button" class="aw-chip todo" @click="quickPublish('songdu')">未诵 · 去诵读</button>
          </div>

          <div class="aw-row">
            <span class="aw-label">每日随施</span>
            <span v-if="awaken.todayGifts > 0" class="aw-chip done">已施 · 今日 {{ awaken.todayGifts }} 条</span>
            <button v-else type="button" class="aw-chip todo" @click="quickPublish('suishi')">未施 · 去随施</button>
          </div>

          <div class="aw-row">
            <span class="aw-label">再读一本</span>
            <span v-if="currentBook" class="aw-link">
              <!-- 总页数未知时只报当前位置，不显示「1/? 页」这种像 bug 的写法 -->
              《{{ currentBook.title }}》第 {{ currentBook.lastPage || 1 }}<template v-if="currentBook.totalPages || currentBook.pages">/{{ currentBook.totalPages || currentBook.pages }}</template> 页
              <em>精读中 {{ currentBook.progressPct || 0 }}%</em>
            </span>
            <button v-else type="button" class="aw-chip todo" @click="goLibrary">未开始 · 去选书</button>
          </div>

          <el-button class="side-action" type="primary" @click="goAwaken">进入我的觉醒</el-button>
        </section>

        <!-- 【P1 2026-11-23】原型右栏第二块：今日推荐书目。
             文案与原型对齐：「《书名》」+ 作者·出版·版本·页数 + 「诵读/随施/再读 N 人/复诵」一行。
             口径：诵读/随施=内容条数，再读=去重人数（原型即按人计），复诵=他人复诵次数。
             数值来自后端 fillDisplay 分类型统计（songduCount/suishiCount/zaiduMemberCount/recitationTotal）。 -->
        <section v-if="recommendedBook" class="side-card">
          <div class="side-title">今日推荐书目</div>
          <div class="side-book">
            <div class="cover">{{ recommendedBook.title.slice(0, 4) }}</div>
            <div>
              <b>《{{ recommendedBook.title }}》</b>
              <span v-if="metaLine(recommendedBook)">{{ metaLine(recommendedBook) }}</span>
              <span v-else-if="recommendedBook.categoryName">{{ recommendedBook.categoryName }}</span>
              <span class="rec-stats" title="诵读/随施=内容条数；再读=参与人数；复诵=他人复诵次数">
                诵读 {{ recommendedBook.songduCount || 0 }} · 随施 {{ recommendedBook.suishiCount || 0 }}
                · 再读 {{ recommendedBook.zaiduMemberCount || 0 }} 人 · 复诵 {{ recommendedBook.recitationTotal || 0 }}
              </span>
            </div>
          </div>
          <div class="side-actions">
            <!-- 没挂电子书就不给「在线阅读」按钮，避免点了进空阅读器 -->
            <el-button v-if="recommendedBook.pdfFileId" size="small" type="primary" @click="openRead(recommendedBook)">在线阅读</el-button>
            <el-button size="small" plain @click="quickPublish('songdu', recommendedBook.id)">去诵读</el-button>
          </div>
        </section>

        <!-- 【P1 2026-11-23】每日提醒 · 闹钟卡（原型右栏第三块）。数据源 GET /fabushi/settings。 -->
        <section class="side-card">
          <div class="side-title">每日提醒 · 闹钟</div>
          <div class="side-row">
            <span>状态</span>
            <b :class="remind.reminderEnabled ? 'on' : 'off'">{{ remind.reminderEnabled ? '已开启' : '未开启' }}</b>
          </div>
          <div class="side-row"><span>时间</span><b>{{ remindTimeText }}</b></div>
          <p class="side-note">当日已上传不打扰；可改时间/可关（个人设置，非强制）。</p>
          <el-button link type="primary" class="side-link" @click="goAwaken">去设置</el-button>
        </section>

        <!-- 【P1 2026-11-23】原「我的法布施」四格卡（我的内容/评论/阅读天数/获木鱼）已移除：
             v8 原型 P1 右栏只有 我的觉醒 / 今日推荐书目 / 每日提醒 三块，
             个人累计数据按新原型归到 P9 我的觉醒与 P3 详情页作者卡，P1 不再重复展示。 -->
      </aside>
    </div>

    <div v-if="active !== 'zaidu'" class="feed-pager">
      <el-pagination
        v-model:current-page="current"
        :page-size="size"
        :total="total"
        layout="prev, pager, next"
        @current-change="loadPosts"
      />
    </div>

    <!-- 【PROTO-AUDIT-20261123】发布器按原型 P6/P7/P8 重做，逻辑收进独立组件 -->
    <FabushiPublishDialog
      v-model="publishVisible"
      :default-scope="publishScope"
      :preset-book-id="presetBookId"
      @published="onPublished"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import {
  getFabushiPosts, getFabushiBooks,
  getFabushiAwakenStatus, getFabushiSettings,
} from '@/api/fabushi'
import FabushiPublishDialog from '@/components/fabushi/FabushiPublishDialog.vue'
import { getDictData } from '@/api/system'
import { refreshDownloadToken, getDownloadToken } from '@/utils/token'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const canCreate = computed(() => userStore.hasPermission('fabushi:create'))
const fileToken = ref('')
const refreshFileToken = async () => { fileToken.value = (await refreshDownloadToken()) || getDownloadToken() || '' }
const canView = computed(() => userStore.hasPermission('fabushi:view'))

// 【PROTO-AUDIT-20261123 P1】原型四胶囊：全部 / 每日诵读 / 每日随施 / 再读一本。
// 「全部」= 不传 scopeType（后端 page() 对 null 不加类型条件），不是前端拼三个列表。
const tabs = [
  { value: 'all', label: '全部' },
  { value: 'songdu', label: '每日诵读' },
  { value: 'suishi', label: '每日随施' },
  { value: 'zaidu', label: '再读一本' },
]
// 发布器里只允许选三种内容类型，「全部」不是可发布类型
const contentTabs = tabs.filter((tab) => tab.value !== 'all')
const active = ref('all')
const counts = reactive({ all: 0, songdu: 0, suishi: 0, zaidu: 0 })
const posts = ref([])
const total = ref(0)
const current = ref(1)
const size = 10
const loading = ref(false)
// 【P1 2026-11-23】右栏「我的觉醒」三板块状态 + 每日提醒设置（后端早已提供，此前未接）
// 【S4 2026-11-23】初值必须带 currentBook，否则 TS 按字面量收窄后 awaken.currentBook 报不存在
const awaken = ref({ todayRecitations: 0, todayGifts: 0, todayPosts: 0, rereadingBooks: 0, currentBook: null })
const currentBook = computed(() => (awaken.value.currentBook && awaken.value.currentBook.bookId ? awaken.value.currentBook : null))
const awakenDone = computed(() =>
  (awaken.value.todayRecitations > 0 ? 1 : 0)
  + (awaken.value.todayGifts > 0 ? 1 : 0)
  + (currentBook.value ? 1 : 0))
const awakenRule = '今日完成口径：每日诵读今日已发布 1 分 · 每日随施今日已发布 1 分 · 书架有「在读」书目（精读中）1 分'
const remind = ref({ reminderEnabled: 0, freqCode: 'daily', remindTime: '20:00' })
const FREQ_LABEL = { daily: '每天', weekday: '工作日', weekend: '周末' }
const remindTimeText = computed(() =>
  remind.value.reminderEnabled ? `${FREQ_LABEL[remind.value.freqCode] || '每天'} ${remind.value.remindTime || '20:00'}` : '—')
const bookOptions = ref([])
const keyword = ref('')
const bookFilter = ref(null)
const unreadOnly = ref(false)

// 【P2 2026-11-23】按书浏览左栏：书籍筛选（书名/作者/版本）、排序、搜书名、版本标签字典
const bookKeyword = ref('')
const bookListKeyword = ref('')
const bookSort = ref('members')
const editionLabels = ref({})
const editionLabel = (value) => editionLabels.value[value] || value || ''
/** 版本元信息（作者 · 出版社 · 版本类型 · 页数）；全缺时返回空串，由调用方决定怎么兜底。 */
const metaLine = (book) => {
  if (!book) return ''
  return [book.author, book.publisher, book.editionType ? editionLabel(book.editionType) : '', book.pages ? `${book.pages} 页` : '']
    .filter(Boolean).join(' · ')
}
/** 版本标签：作者 · 出版社 · 版本类型 · 页数（与 library.vue / P4 版本链同口径）。 */
const versionLabel = (book) => {
  if (!book) return ''
  return metaLine(book) || '未标注版本'
}
const filteredBooks = computed(() => {
  const kw = bookKeyword.value.trim().toLowerCase()
  if (!kw) return bookOptions.value
  return bookOptions.value.filter((book) => versionLabel(book).toLowerCase().includes(kw)
    || String(book.title || '').toLowerCase().includes(kw))
})
const sortedBooks = computed(() => {
  const kw = bookListKeyword.value.trim().toLowerCase()
  const list = filteredBooks.value.filter((book) => !kw || String(book.title || '').toLowerCase().includes(kw))
  const byTitle = (a, b) => String(a.title || '').localeCompare(String(b.title || ''), 'zh-CN')
  if (bookSort.value === 'title') return [...list].sort(byTitle)
  if (bookSort.value === 'posts') return [...list].sort((a, b) => Number(b.postCount || 0) - Number(a.postCount || 0) || byTitle(a, b))
  return [...list].sort((a, b) => Number(b.memberCount || 0) - Number(a.memberCount || 0) || byTitle(a, b))
})
const selectedBook = computed(() => bookOptions.value.find((book) => book.id === bookFilter.value) || null)

/** 内容摘要：标题优先，无标题取正文前 80 字（原型「内容摘要」列）。 */
const summaryOf = (post) => {
  const title = String(post.title || '').trim()
  if (title) return title
  const body = String(post.content || '').replace(/\s+/g, ' ').trim()
  return body.length > 80 ? `${body.slice(0, 80)}…` : (body || '（无正文）')
}

/** 形式：按附件类型汇总成「书页 N 张 + 语音」这类人类可读串（原型「形式」列）。 */
const formLabel = (post) => {
  const list = post.attachments || []
  const count = (type) => list.filter((a) => a.fileType === type).length
  const parts = []
  if (count('image')) parts.push(`${post.scopeType === 'zaidu' ? '书页' : '图片'} ${count('image')} 张`)
  if (count('audio')) parts.push('语音')
  if (count('video')) parts.push('视频')
  if (count('file')) parts.push('文件')
  return parts.length ? parts.join(' + ') : '文字'
}

const publishVisible = ref(false)
const submitting = ref(false)
const publishScope = ref('songdu')
const presetBookId = ref(null)

/**
 * 【P1 2026-11-23】今日推荐书目：优先「有电子书且有内容」的书（点进去既能读又能看到别人分享），
 * 其次有电子书的、再有内容的，最后退回第一本。之前只看 pdfFileId，常推出一本 0 条内容的书，
 * 右栏「诵读/随施/再读/复诵」全是 0，看起来像功能没做。
 */
const recommendedBook = computed(() => {
  const list = bookOptions.value
  return list.find((b) => b.pdfFileId && (b.postCount || 0) > 0)
    || list.find((b) => b.pdfFileId)
    || list.find((b) => (b.postCount || 0) > 0)
    || list[0] || null
})
const visiblePosts = computed(() => posts.value.filter((post) => {
  // 【D16 2026-11-23】关键词已改服务端搜索（人名/书籍/文字）；本地仅保留 书目筛选/只看未读
  if (bookFilter.value && post.bookId !== bookFilter.value) return false
  if (unreadOnly.value && post.readByMe) return false
  return true
}))

const typeLabel = (value) => tabs.find((tab) => tab.value === value)?.label || '内容'
const formatTime = (value) => (value ? String(value).replace('T', ' ').slice(0, 16) : '')
const isNew = (post) => !post.readByMe && post.createdAt && (Date.now() - new Date(post.createdAt).getTime() < 30 * 24 * 3600 * 1000)
const bookName = (post) => post.bookTitle || bookOptions.value.find((book) => book.id === post.bookId)?.title || ''
const goLibrary = () => router.push('/fabushi/library')
const openDetail = (id) => router.push(`/fabushi/detail/${id}`)
const goBookDetail = () => { if (bookFilter.value) router.push(`/fabushi/library?bookId=${bookFilter.value}`) }

/** 【P2】点左栏某本书：只换右栏内容，不重置左栏筛选。 */
const selectBook = (book) => {
  bookFilter.value = book.id
  current.value = 1
  loadPosts()
}

/** 【P2】重置：清筛选/排序/搜书名并回到「全部书目」。 */
const resetByBook = () => {
  bookKeyword.value = ''
  bookListKeyword.value = ''
  bookSort.value = 'members'
  bookFilter.value = null
  current.value = 1
  loadPosts()
}

/** 【P2】版本类型字典（fabushi_edition_type），用于版本标签中文名。 */
const loadEditions = async () => {
  try {
    const { data } = await getDictData('fabushi_edition_type')
    editionLabels.value = Object.fromEntries((data || []).map((item) => [item.dictValue, item.dictLabel]))
  } catch (e) {
    editionLabels.value = {}
  }
}

const loadPosts = async () => {
  loading.value = true
  try {
    const { data } = await getFabushiPosts({
      // 「全部」不传 scopeType，后端 page() 走 null 分支=不过滤类型
      scopeType: active.value === 'all' ? undefined : active.value,
      bookId: bookFilter.value || undefined,
      keyword: keyword.value || undefined,
      current: current.value,
      size,
    })
    posts.value = (data && data.records) || []
    total.value = Number(data && data.total) || 0
  } finally {
    loading.value = false
  }
}

const loadCounts = async () => {
  const types = contentTabs
  const results = await Promise.all(types.map((tab) => getFabushiPosts({ scopeType: tab.value, current: 1, size: 1 })))
  let sum = 0
  types.forEach((tab, index) => {
    const n = Number(results[index]?.data?.total) || 0
    counts[tab.value] = n
    sum += n
  })
  // 每条内容只有一个类型，三类型之和即全部（省一次请求）
  counts.all = sum
}

const loadAwaken = async () => {
  try {
    awaken.value = (await getFabushiAwakenStatus()).data || {}
  } catch (e) {
    // 右栏状态卡属辅助信息，拉取失败不阻断信息流；回落零值而不是空对象，
    // 避免模板里 undefined 参与运算（也是 TS 报 {} 不可赋值的根因）
    awaken.value = { todayRecitations: 0, todayGifts: 0, todayPosts: 0, rereadingBooks: 0, currentBook: null }
  }
}

const loadRemind = async () => {
  try {
    remind.value = (await getFabushiSettings()).data || { reminderEnabled: 0, freqCode: 'daily', remindTime: '20:00' }
  } catch (e) {
    remind.value = { reminderEnabled: 0, freqCode: 'daily', remindTime: '20:00' }
  }
}

const loadBooks = async () => {
  try {
    const { data } = await getFabushiBooks({ status: 'approved', current: 1, size: 200 })
    bookOptions.value = (data && data.records) || []
  } catch (e) {
    bookOptions.value = []
  }
}

const switchTab = (value) => {
  active.value = value
  current.value = 1
  // 【P2】进入「再读一本 · 按书浏览」时默认选中参与人数最多的书，右栏不空着
  if (value === 'zaidu' && !bookFilter.value && sortedBooks.value.length) {
    bookFilter.value = sortedBooks.value[0].id
  }
  loadPosts()
}

const resetPage = () => {
  current.value = 1
  loadPosts()
}

const resetFilters = () => {
  keyword.value = ''
  bookFilter.value = null
  unreadOnly.value = false
  resetPage()
}

const openPublish = (scope, bookId = null) => {
  publishScope.value = scope || 'songdu'
  presetBookId.value = bookId
  publishVisible.value = true
}

// 【P1 2026-11-23】右栏三板块快捷动作：未完成项一键直达对应发布器，不跳页打断阅读
const quickPublish = (scope, bookId = null) => {
  if (!canCreate.value) {
    goAwaken()
    return
  }
  active.value = scope
  current.value = 1
  openPublish(scope, bookId)
}
const goAwaken = () => router.push('/fabushi/shelf')
const openRead = (book) => {
  if (!book || !book.id) return
  router.push(`/fabushi/read/${book.id}`)
}

const onPublished = async () => {
  current.value = 1
  await Promise.all([loadPosts(), loadCounts(), loadAwaken()])
}

onMounted(async () => {
  // 【BUGFIX 2026-11-23】下载 token 必须早于内容渲染：原来与 loadPosts 并列在 Promise.all 里，
  // 谁先完成不确定，图片/语音会用 token= 空串发请求拿到 401（首屏偶发图片不显示）。
  await refreshFileToken()
  await Promise.all([
    loadPosts(), loadCounts(), loadBooks(),
    loadAwaken(), loadRemind(), loadEditions(),
  ])
  // 【D7 切片 6】觉醒三板块直达对应发布器：?scope=songdu|suishi|zaidu
  const presetScope = String(route.query.scope || '')
  const queryBookId = route.query.bookId ? Number(route.query.bookId) : null
  if (contentTabs.some((tab) => tab.value === presetScope)) {
    active.value = presetScope
    if (queryBookId) bookFilter.value = queryBookId
    if (presetScope === 'zaidu' && !bookFilter.value && sortedBooks.value.length) {
      bookFilter.value = sortedBooks.value[0].id
    }
    if (canCreate.value) openPublish(presetScope, queryBookId)
    if (presetScope === 'zaidu') loadPosts()
  } else if (queryBookId && canCreate.value) {
    openPublish('zaidu', queryBookId)
  }
})
</script>

<style scoped>
.fabushi-feed { display: flex; flex-direction: column; gap: 10px; }
.page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.page-head h1 { margin: 0; color: var(--text-primary); font-size: 20px; font-weight: 800; }
.page-head p { margin: 4px 0 0; color: var(--text-muted); font-size: 12px; }
.caps { display: flex; gap: 4px; padding: 4px; border-radius: var(--radius-md); background: var(--el-fill-color-light); }
.cap { flex: 1; display: inline-flex; align-items: center; justify-content: center; gap: 6px; padding: 8px 6px; border: 0; border-radius: var(--radius-sm); background: transparent; color: var(--text-secondary); font-size: 13px; font-weight: 600; cursor: pointer; }
.cap.active { color: var(--el-color-white); background: var(--hero-gradient); box-shadow: var(--shadow-card-hover); }
.cap .n { padding: 1px 6px; border-radius: var(--radius-pill); background: var(--el-fill-color-darker); font-size: 11px; }
.cap.active .n { background: rgba(255, 255, 255, .25); }
.book-strip { margin-bottom: 10px; padding: 10px 12px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-bg-color); }
/* 【P2 2026-11-23】按书浏览：左书目列表 + 右成员内容表 */
.bb-filter { margin-bottom: 10px; }
.bb-hint { color: var(--text-placeholder); font-size: 11.5px; }
.bb-layout { display: grid; grid-template-columns: 250px minmax(0, 1fr); gap: 12px; align-items: start; }
.bb-left { display: flex; flex-direction: column; max-height: 620px; padding: 10px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-bg-color); }
.bb-left-head { display: flex; flex-direction: column; gap: 7px; padding-bottom: 8px; border-bottom: 1px solid var(--border-light); }
.bb-left-head b { color: var(--text-primary); font-size: 12.5px; }
.bb-left-list { flex: 1; overflow-y: auto; margin-top: 4px; }
.bb-book { display: flex; align-items: center; gap: 6px; width: 100%; padding: 7px 8px; border: 0; border-radius: var(--radius-sm); background: transparent; cursor: pointer; text-align: left; }
.bb-book:hover { background: var(--el-fill-color-light); }
.bb-book.active { background: var(--el-color-primary-light-9); }
.bb-name { display: flex; flex-direction: column; flex: 1; min-width: 0; color: var(--text-primary); font-size: 12px; font-weight: 600; }
.bb-name em { overflow: hidden; color: var(--text-placeholder); font-size: 10px; font-style: normal; font-weight: 400; text-overflow: ellipsis; white-space: nowrap; }
.bb-book.active .bb-name { color: var(--primary-color); }
.bb-cnt { flex-shrink: 0; color: var(--text-muted); font-size: 10.5px; font-variant-numeric: tabular-nums; }
.bb-empty { padding: 14px 4px; color: var(--text-placeholder); font-size: 11.5px; }
.bb-left-note { margin: 8px 0 0; padding-top: 8px; border-top: 1px dashed var(--border-light); color: var(--text-placeholder); font-size: 10.5px; line-height: 1.6; }
.bb-right { display: flex; flex-direction: column; gap: 8px; min-width: 0; padding: 10px 12px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-bg-color); }
.bb-toolbar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding-bottom: 8px; border-bottom: 1px solid var(--border-light); }
.bb-tt { color: var(--text-primary); font-size: 13px; font-weight: 800; }
.bb-tt-sub { color: var(--text-muted); font-size: 11px; }
.bb-toolbar .tag { padding: 1px 7px; border-radius: var(--radius-sm); font-size: 10px; font-weight: 700; }
.bb-toolbar .tag.blue { color: var(--primary-color); background: var(--el-color-primary-light-9); }
.bb-toolbar .tag.green { color: var(--el-color-success); background: var(--el-color-success-light-9); }
.bb-table { width: 100%; }
/* 列宽合计可能超过右栏宽度：交给 el-table 自己横向滚，别把「时间/操作」裁掉 */
.bb-table :deep(.el-table__body-wrapper),
.bb-table :deep(.el-table__header-wrapper) { overflow-x: auto; }
.bb-summary { display: -webkit-box; overflow: hidden; color: var(--text-secondary); font-size: 12px; line-height: 1.6; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.bb-form { color: var(--text-muted); font-size: 11px; }
.bb-inter { color: var(--text-muted); font-size: 11px; font-variant-numeric: tabular-nums; }
.bb-pager { display: flex; justify-content: flex-end; padding-top: 6px; border-top: 1px solid var(--border-light); }
@media (max-width: 1100px) { .bb-layout { grid-template-columns: 1fr; } .bb-left { max-height: 260px; } }
.bs-head { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; color: var(--text-primary); font-size: 13px; }
.bs-hint { color: var(--text-placeholder); font-size: 11px; }
.bs-head .el-button { margin-left: auto; }
.bs-list { display: flex; gap: 7px; overflow-x: auto; padding-bottom: 3px; }
.bs-book { display: flex; flex: 0 0 auto; flex-direction: column; gap: 3px; padding: 7px 11px; border: 1px solid var(--border-light); border-radius: var(--radius-sm); background: var(--el-fill-color-lighter); cursor: pointer; text-align: left; }
.bs-book.active { border-color: var(--primary-color); background: var(--el-color-primary-light-9); }
.bs-title { color: var(--text-primary); font-size: 12px; font-weight: 700; }
.bs-meta { color: var(--text-muted); font-size: 10.5px; }
.bs-empty { color: var(--text-placeholder); font-size: 11px; }
.filter { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; padding: 9px 12px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-bg-color); }
.fl { color: var(--text-secondary); font-size: 12px; }
.spacer { flex: 1; }
.feed-layout { display: grid; grid-template-columns: minmax(0, 1fr) 268px; gap: 12px; align-items: start; }
.feed-list { display: flex; flex-direction: column; gap: 10px; min-height: 220px; }
.moments-card { padding: 14px 16px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-bg-color); box-shadow: var(--shadow-card); cursor: pointer; }
.mc-head { display: flex; gap: 10px; }
.mc-avatar { display: flex; align-items: center; justify-content: center; flex-shrink: 0; width: 40px; height: 40px; border-radius: 9px; color: var(--el-color-white); background: var(--hero-gradient); font-size: 16px; font-weight: 700; }
.mc-main { flex: 1; min-width: 0; }
.mc-name { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; color: var(--text-primary); font-size: 13px; font-weight: 800; }
.mc-name .tag { padding: 1px 6px; border-radius: var(--radius-sm); font-size: 10px; font-weight: 700; }
.mc-name .tag.songdu { color: var(--primary-color); background: var(--el-color-primary-light-9); }
.mc-name .tag.suishi { color: var(--el-color-warning-dark-2); background: var(--el-color-warning-light-9); }
.mc-name .tag.zaidu { color: var(--el-color-success); background: var(--el-color-success-light-9); }
.mc-text { margin-top: 3px; color: var(--text-primary); font-size: 13px; line-height: 1.7; white-space: pre-wrap; word-break: break-word; }
.mc-text.body { color: var(--text-secondary); }
.mc-imgs { display: grid; grid-template-columns: repeat(3, 1fr); gap: 4px; margin-top: 8px; max-width: 330px; }
.mc-img { width: 100%; aspect-ratio: 1; border-radius: 6px; overflow: hidden; }
.mc-audio { margin-top: 8px; }
.mc-meta { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-top: 7px; color: var(--text-placeholder); font-size: 11px; }
.mc-meta .src { color: var(--primary-color); }
.new-badge { padding: 1px 5px; border-radius: var(--radius-sm); color: var(--el-color-danger); background: var(--el-color-danger-light-9); font-size: 9px; font-weight: 700; }
.mc-acts { display: flex; align-items: center; justify-content: space-between; margin-top: 9px; padding: 6px 12px; border-radius: 6px; background: var(--el-fill-color-light); color: var(--text-secondary); font-size: 12px; }
.mc-acts .more { letter-spacing: 2px; color: var(--text-muted); font-weight: 800; }
.mc-inter { margin-top: 6px; padding: 7px 12px; border-radius: 6px; background: var(--el-fill-color-light); color: var(--text-secondary); font-size: 12px; line-height: 1.7; }
.mc-inter .likers { color: var(--text-primary); font-weight: 700; }
.read { color: var(--text-muted); }
.read.unread { color: var(--el-color-danger); font-weight: 700; }
.req { color: var(--el-color-danger); }
.hint { color: var(--text-muted); font-weight: 400; font-size: 11px; }
.suishi-tip { padding: 9px 12px; border-radius: var(--radius-sm); background: var(--el-color-primary-light-9); color: var(--text-secondary); font-size: 12px; line-height: 1.7; }
.moments-card { padding: 13px 15px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-fill-color-lighter); cursor: pointer; transition: background .18s, box-shadow .18s; }
.feed-item:hover { background: var(--el-bg-color); box-shadow: var(--shadow-card-hover); }
.feed-top { display: flex; align-items: center; gap: 8px; margin-bottom: 5px; }
.tag { padding: 1px 6px; border-radius: var(--radius-sm); font-size: 10px; font-weight: 700; }
.tag.songdu { color: var(--primary-color); background: var(--el-color-primary-light-9); }
.tag.suishi { color: var(--el-color-success); background: var(--el-color-success-light-9); }
.tag.zaidu { color: var(--el-color-warning-dark-2); background: var(--el-color-warning-light-9); }
.feed-title { overflow: hidden; color: var(--text-primary); font-size: 14px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.new-badge { padding: 1px 5px; border-radius: var(--radius-sm); color: var(--el-color-danger); background: var(--el-color-danger-light-9); font-size: 9px; font-weight: 700; }
.feed-time { flex-shrink: 0; margin-left: auto; color: var(--text-placeholder); font-size: 11px; }
.feed-body { display: -webkit-box; overflow: hidden; color: var(--text-secondary); font-size: 12.5px; line-height: 1.7; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.feed-book { display: inline-flex; margin-top: 7px; padding: 2px 7px; border-radius: var(--radius-sm); color: var(--primary-color); background: var(--el-color-primary-light-9); font-size: 11px; }
.feed-meta { display: flex; align-items: center; gap: 14px; margin-top: 8px; color: var(--text-muted); font-size: 11px; }
.feed-meta .read { margin-left: auto; color: var(--primary-color); }
.feed-meta .read.unread { color: var(--text-placeholder); }
.feed-side { display: flex; flex-direction: column; gap: 10px; }
.side-card { padding: 13px 14px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-fill-color-lighter); }
.side-title { margin-bottom: 8px; color: var(--text-primary); font-size: 13px; font-weight: 700; }
.side-row { display: flex; justify-content: space-between; padding: 6px 0; border-bottom: 1px solid var(--border-light); color: var(--text-secondary); font-size: 12px; }
.side-row:last-child { border-bottom: 0; }
.side-row b { color: var(--text-primary); font-size: 15px; font-variant-numeric: tabular-nums; }
.side-row b.on { color: var(--el-color-success); }
.side-row b.off { color: var(--text-placeholder); }
/* 【P1 2026-11-23】我的觉醒三板块状态卡（原型右栏第一位） */
.awaken-card .side-title { display: flex; align-items: center; gap: 6px; }
.aw-score { margin-left: auto; padding: 1px 7px; border-radius: var(--radius-pill); color: var(--primary-color); background: var(--el-color-primary-light-9); font-size: 11px; font-weight: 700; cursor: help; }
.aw-row { display: flex; align-items: center; gap: 8px; padding: 6px 0; border-bottom: 1px solid var(--border-light); }
.aw-row:last-of-type { border-bottom: 0; }
.aw-label { display: flex; flex-direction: column; flex: 1; min-width: 0; color: var(--text-secondary); font-size: 12px; }
.aw-label em { color: var(--text-placeholder); font-size: 10px; font-style: normal; }
.aw-chip { flex-shrink: 0; max-width: 62%; padding: 2px 8px; border: 1px solid transparent; border-radius: var(--radius-pill); font-size: 10.5px; line-height: 1.5; text-align: right; }
.aw-chip.done { color: var(--el-color-success); background: var(--el-color-success-light-9); }
.aw-chip.todo { color: var(--el-color-warning-dark-2); background: var(--el-color-warning-light-9); border-color: var(--el-color-warning-light-5); cursor: pointer; }
.aw-chip.todo:hover { border-color: var(--el-color-warning); }
.aw-link { flex-shrink: 0; max-width: 62%; color: var(--primary-color); font-size: 10.5px; line-height: 1.5; text-align: right; }
.aw-link em { display: block; color: var(--text-placeholder); font-size: 10px; font-style: normal; }
.side-actions { display: flex; gap: 8px; margin-top: 9px; }
.side-actions .el-button { flex: 1; margin-left: 0; }
.side-note { margin: 6px 0 0; color: var(--text-placeholder); font-size: 10.5px; line-height: 1.6; }
.side-link { padding: 0; margin-top: 2px; font-size: 11px; }
.side-book { display: flex; align-items: flex-start; gap: 9px; margin-top: 7px; }
.cover { display: flex; align-items: center; justify-content: center; flex-shrink: 0; width: 38px; height: 52px; padding: 4px; border-radius: var(--radius-sm); color: var(--el-color-white); background: var(--hero-gradient); font-size: 10px; line-height: 1.2; text-align: center; }
.side-book b { display: block; color: var(--text-primary); font-size: 12px; }
/* 【P1 2026-11-23】元信息与统计行各自独占一行（原先 inline 会挤成一行看不清） */
.side-book span { display: block; color: var(--text-muted); font-size: 10.5px; line-height: 1.6; }
.side-book .rec-stats { color: var(--text-secondary); }
.side-action { width: 100%; margin-top: 9px; }
.feed-pager { display: flex; justify-content: flex-end; padding-top: 10px; border-top: 1px solid var(--border-light); }
.form-row { display: grid; grid-template-columns: 70px minmax(0, 1fr); gap: 10px; align-items: start; margin-bottom: 12px; }
.form-row label { padding-top: 7px; color: var(--text-secondary); font-size: 12px; }
.type-caps { display: flex; gap: 4px; padding: 4px; border-radius: var(--radius-md); background: var(--el-fill-color-light); }
.type-cap { flex: 1; padding: 6px 10px; border: 0; border-radius: var(--radius-sm); color: var(--text-secondary); background: transparent; font-size: 12px; cursor: pointer; }
.type-cap.active { color: var(--el-color-white); background: var(--hero-gradient); }
.attachment-field { display: flex; flex-direction: column; gap: 6px; }
.attachment-actions { display: flex; align-items: center; gap: 8px; }
.attachment-hint { color: var(--text-placeholder); font-size: 11px; }
.attachment-chip { display: flex; align-items: center; justify-content: space-between; padding: 5px 8px; border-radius: var(--radius-sm); background: var(--el-fill-color-light); color: var(--text-secondary); font-size: 11px; }
.form-placeholder { display: flex; align-items: center; min-height: 32px; padding: 0 10px; border: 1px solid var(--el-border-color); border-radius: var(--radius-sm); color: var(--text-placeholder); font-size: 12px; }
@media (max-width: 1100px) { .feed-layout { grid-template-columns: 1fr; } }
</style>
