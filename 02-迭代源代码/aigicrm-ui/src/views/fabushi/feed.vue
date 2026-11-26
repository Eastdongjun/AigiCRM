<template>
  <div class="fx-page fabushi-feed">
    <!-- 【2026-11-26 按 PC 原型 v8】P1 / P2：页面直接摆在灰画布上（没有页头标题块），四个类型页签在最上面 -->
    <div class="cat-tabs" role="tablist" aria-label="内容类型">
      <button
        v-for="tab in tabs"
        :key="tab.value"
        class="cat-tab"
        :class="{ active: active === tab.value }"
        type="button"
        role="tab"
        :aria-selected="active === tab.value"
        @click="switchTab(tab.value)"
      >
        <!-- 进到“再读一本”这一页签就是按书浏览（原型 P2 的页签文案），不带条数 -->
        <template v-if="tab.value === 'zaidu' && active === 'zaidu'">再读一本 · 按书浏览</template>
        <template v-else>{{ tab.label }}<span class="badge">{{ counts[tab.value] || 0 }}</span></template>
      </button>
    </div>

    <!-- 【P2 2026-11-23】再读一本分区 = 按书浏览（D15）：左侧按「具体版本的书」罗列，
         右侧=该书全部成员按时间序上传的内容表格。与四项激励「按客户罗列→点进客户看记录」同构。 -->
    <template v-if="active === 'zaidu'">
      <div class="filter-card">
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
        <span class="filter-note">按书罗列 → 点进每本书看每个人上传的内容</span>
      </div>

      <div class="main-layout">
        <aside class="sub-panel bybook-left">
          <div class="sub-title">书目总览<span class="sub2">· {{ filteredBooks.length }} 本</span></div>
          <el-input v-model="bookListKeyword" clearable placeholder="搜书名" class="sub-search">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <div class="sub-list">
            <button
              v-for="book in sortedBooks"
              :key="book.id"
              type="button"
              class="sub-row"
              :class="{ active: bookFilter === book.id }"
              @click="selectBook(book)"
            >
              <span class="sub-name">《{{ book.title }}》<em>{{ versionLabel(book) }}</em></span>
              <span class="cnt">{{ book.memberCount || 0 }} 人</span>
            </button>
            <p v-if="!sortedBooks.length" class="bb-empty">暂无已审核书目</p>
          </div>
          <p class="sub-foot">书名+作者+版本三级确定后才是一本「在读的书」；不同版本各自成行。</p>
        </aside>

        <section class="table-card">
          <div class="toolbar">
            <template v-if="selectedBook">
              <b class="tt">《{{ selectedBook.title }}》</b>
              <span class="tt-sub">· {{ versionLabel(selectedBook) }}</span>
              <span v-if="selectedBook.pdfFileId" class="tag blue">电子书</span>
              <!-- 条数用当前查询的 total（=本页签口径），不用书级 postCount，避免与表格行数打架 -->
              <span class="tag green">参与 {{ selectedBook.memberCount || 0 }} 人 · 本区 {{ total }} 条</span>
            </template>
            <b v-else class="tt">全部书目</b>
            <span class="spacer" />
            <el-button v-if="selectedBook" size="small" plain @click="goBookDetail">书目详情</el-button>
            <el-button v-if="selectedBook" size="small" type="primary" @click="quickPublish('zaidu', selectedBook.id)">＋我在本书上传</el-button>
          </div>

          <el-table :data="posts" v-loading="loading" class="bb-table" empty-text="该书还没有人上传内容">
            <el-table-column label="成员" width="96">
              <template #default="{ row }"><b>{{ row.userName || '—' }}</b></template>
            </el-table-column>
            <el-table-column label="章节" width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ row.sectionTitle || '—' }}</template>
            </el-table-column>
            <el-table-column label="内容摘要" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ summaryOf(row) }}</template>
            </el-table-column>
            <el-table-column label="形式" width="150">
              <template #default="{ row }"><span class="tag gray">{{ formLabel(row) }}</span></template>
            </el-table-column>
            <el-table-column label="互动" width="170">
              <template #default="{ row }">
                <span class="bb-inter">木鱼 {{ row.likeCount || 0 }} · 评论 {{ row.commentCount || 0 }}<template v-if="row.recitationCount"> · 复诵 {{ row.recitationCount }}</template></span>
              </template>
            </el-table-column>
            <el-table-column label="时间" width="112">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="70" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
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

    <div v-else class="filter-card">
      <span class="fl">搜索</span>
      <el-input v-model="keyword" clearable placeholder="人名 / 书籍 / 具体文字（服务端检索）" style="width: 250px" @keyup.enter="resetPage">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <!-- 按书筛选：原型 P1 的筛选条里没有这一项（按书看内容在“再读一本 · 按书浏览”里），这里保留，只占一个小下拉 -->
      <el-select v-model="bookFilter" clearable placeholder="全部书目" style="width: 150px" @change="resetPage">
        <el-option v-for="book in bookOptions" :key="book.id" :label="book.title" :value="book.id" />
      </el-select>
      <el-checkbox v-model="unreadOnly">只看未读</el-checkbox>
      <el-button plain @click="resetFilters">重置</el-button>
      <span class="spacer" />
      <template v-if="canCreate">
        <el-button type="primary" @click="openPublish('songdu')">＋每日诵读</el-button>
        <el-button plain @click="openPublish('suishi')">＋每日随施</el-button>
        <el-button type="success" @click="openPublish('zaidu')">＋再读一本</el-button>
      </template>
    </div>

    <div v-if="active !== 'zaidu'" class="feed-layout">
      <section class="feed-col" v-loading="loading">
        <article v-for="post in visiblePosts" :key="post.id" class="moments-card" @click="openDetail(post.id)">
          <div class="mc-head">
            <div class="mc-ava" :class="`ava-${post.scopeType}`">{{ (post.userName || '法').slice(0, 1) }}</div>
            <div class="mc-main">
              <div class="mc-name">{{ post.userName || '法布施' }}<span class="tag" :class="post.scopeType">{{ typeLabel(post.scopeType) }}</span></div>
              <div v-if="post.title" class="mc-text title">{{ post.title }}</div>
              <div class="mc-text">{{ post.content }}</div>
              <div v-if="imagesOf(post).length" class="mc-imgs" :class="imagesOf(post).length === 1 ? 'g1' : 'g3'">
                <el-image
                  v-for="img in imagesOf(post)"
                  :key="img.id"
                  class="mc-img"
                  :class="{ big: imagesOf(post).length === 1 }"
                  :src="fileUrl(img.fileId)"
                  :preview-src-list="imagesOf(post).map((a) => fileUrl(a.fileId))"
                  preview-teleported
                  fit="cover"
                  @click.stop
                />
              </div>
              <div v-if="audiosOf(post).length" class="mc-audio" @click.stop>
                <span class="recite-wave"><i v-for="h in WAVE" :key="h" :style="{ height: h + 'px' }" /></span>
                <span>语音 {{ audiosOf(post).length }} 段</span>
                <audio v-for="a in audiosOf(post)" :key="a.id" :src="fileUrl(a.fileId)" controls preload="none" class="mc-audio-ctl" />
              </div>
              <div class="mc-meta">
                <span>{{ formatTime(post.createdAt) }}</span>
                <span v-if="bookName(post)" class="src">《{{ bookName(post) }}》<template v-if="post.sectionTitle"> › {{ post.sectionTitle }}</template></span>
                <span v-if="sourceNote(post)">{{ sourceNote(post) }}</span>
                <span v-if="isNew(post)" class="badge-new">NEW</span>
                <span class="read-state" :class="{ unread: !post.readByMe }"><i class="read-dot" />{{ post.readByMe ? '已读' : '未读' }}</span>
              </div>
            </div>
          </div>
          <div class="mc-acts">
            <span>木鱼 {{ post.likeCount || 0 }} · 评论 {{ post.commentCount || 0 }} · 复诵 {{ post.recitationCount || 0 }}</span>
            <!-- 原型这一格是“⋯”操作菜单（木鱼 / 评论 / 复诵）：这里做成真的下拉，不做假按钮。
                 拦截冒泡写在触发元素自己身上：el-dropdown 不会把组件上的点击监听接到包住触发元素的那一层，
                 写在组件上拦不住，点“⋯”会连带触发整张卡片的“打开详情” -->
            <el-dropdown
              class="mc-more"
              trigger="click"
              placement="bottom-end"
              popper-class="fx-popper"
              @command="(cmd) => onMore(cmd, post)"
            >
              <span class="more" role="button" :aria-label="`对 ${post.userName || '这条内容'} 的这条内容：更多操作`" @click.stop>⋯</span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="like" :disabled="likingIds.includes(post.id)">{{ post.likedByMe ? '取消木鱼' : '敲木鱼' }}</el-dropdown-item>
                  <el-dropdown-item command="comment">查看评论</el-dropdown-item>
                  <el-dropdown-item command="recite">我要复诵</el-dropdown-item>
                  <el-dropdown-item command="detail">打开详情</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <div v-if="post.likeNames" class="mc-inter"><div><span class="likers">木鱼：</span>{{ post.likeNames }}</div></div>
        </article>
        <el-empty v-if="!loading && !visiblePosts.length" description="暂无法布施内容" />
        <div class="feed-pager">
          <el-pagination
            v-model:current-page="current"
            :page-size="size"
            :total="total"
            layout="prev, pager, next"
            @current-change="loadPosts"
          />
        </div>
      </section>

      <aside class="side-col">
        <!-- 【P1 2026-11-23】原型右栏第一位＝「我的觉醒 · 今日 x/3」三板块状态卡（D6）。
             数据源 GET /fabushi/awaken/status（早已存在，此前前端没用）。
             口径（如实标注，不臆造"今日"含义）：诵读=今日已发布诵读内容 / 随施=今日已发布随施 /
             再读=书架有「在读」书目（原型即如此计数：已诵 + 精读中 = 2/3）。 -->
        <section class="side-card">
          <div class="side-title" :title="awakenRule">
            我的觉醒<template v-if="userStore.realName"> · {{ userStore.realName }}</template>（今日 {{ awakenDone }}/3）
          </div>

          <div class="side-row">
            <span>每日诵读（晨 08:00）</span>
            <span v-if="awaken.todayRecitations > 0" class="done-chip">已诵 · 今日 {{ awaken.todayRecitations }} 条</span>
            <button v-else type="button" class="todo-chip" @click="quickPublish('songdu')">未诵 · 去诵读</button>
          </div>

          <div class="side-row">
            <span>每日随施</span>
            <span v-if="awaken.todayGifts > 0" class="done-chip">已施 · 今日 {{ awaken.todayGifts }} 条</span>
            <button v-else type="button" class="todo-chip" @click="quickPublish('suishi')">未施 · 去随施</button>
          </div>

          <div class="side-row">
            <span>再读一本<template v-if="currentBook"> ·《{{ currentBook.title }}》</template></span>
            <span v-if="currentBook" class="aw-link">
              <!-- 总页数未知时只报当前位置，不显示「1/? 页」这种像 bug 的写法 -->
              第 {{ currentBook.lastPage || 1 }}<template v-if="currentBook.totalPages || currentBook.pages">/{{ currentBook.totalPages || currentBook.pages }}</template> 页 · 精读中 {{ currentBook.progressPct || 0 }}%
            </span>
            <button v-else type="button" class="todo-chip" @click="goLibrary">未开始 · 去选书</button>
          </div>

          <div class="rec-acts"><el-button type="primary" @click="goAwaken">进入我的觉醒</el-button></div>
        </section>

        <!-- 【P1 2026-11-23】原型右栏第二块：今日推荐书目。
             文案与原型对齐：「《书名》」+ 作者·出版·版本·页数 + 「诵读/随施/再读 N 人/复诵」一行。
             口径：诵读/随施=内容条数，再读=去重人数（原型即按人计），复诵=他人复诵次数。
             数值来自后端 fillDisplay 分类型统计（songduCount/suishiCount/zaiduMemberCount/recitationTotal）。 -->
        <section v-if="recommendedBook" class="side-card">
          <div class="side-title">今日推荐书目</div>
          <div class="rec-book">
            <div class="book-spine">{{ recommendedBook.title.slice(0, 4) }}</div>
            <div class="rec-main">
              <div class="rec-title">《{{ recommendedBook.title }}》</div>
              <div class="rec-meta">{{ metaLine(recommendedBook) || recommendedBook.categoryName }}</div>
              <div class="rec-meta" title="诵读/随施=内容条数；再读=参与人数；复诵=他人复诵次数">
                诵读 {{ recommendedBook.songduCount || 0 }} · 随施 {{ recommendedBook.suishiCount || 0 }}
                · 再读 {{ recommendedBook.zaiduMemberCount || 0 }} 人 · 复诵 {{ recommendedBook.recitationTotal || 0 }}
              </div>
            </div>
          </div>
          <div class="rec-acts">
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
        </section>

        <!-- 【P1 2026-11-23】原「我的法布施」四格卡（我的内容/评论/阅读天数/获木鱼）已移除：
             v8 原型 P1 右栏只有 我的觉醒 / 今日推荐书目 / 每日提醒 三块，
             个人累计数据按新原型归到 P9 我的觉醒与 P3 详情页作者卡，P1 不再重复展示。 -->
      </aside>
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
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import {
  getFabushiPosts, getFabushiBooks, toggleFabushiLike,
  getFabushiAwakenStatus, getFabushiSettings,
} from '@/api/fabushi'
import FabushiPublishDialog from '@/components/fabushi/FabushiPublishDialog.vue'
import { getDictData } from '@/api/system'
import { refreshDownloadToken, getDownloadToken } from '@/utils/token'
import '@/assets/fabushi-v8.css'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const canCreate = computed(() => userStore.hasPermission('fabushi:create'))
const fileToken = ref('')
const refreshFileToken = async () => { fileToken.value = (await refreshDownloadToken()) || getDownloadToken() || '' }
const fileUrl = (fileId) => `/api/file/download/${fileId}?token=${encodeURIComponent(fileToken.value)}`
const imagesOf = (post) => (post.attachments || []).filter((a) => a.fileType === 'image')
const audiosOf = (post) => (post.attachments || []).filter((a) => a.fileType === 'audio')
// 朋友圈卡片里语音条左侧的那一小段波形（固定的装饰，不代表真实波形）
const WAVE = [6, 12, 8, 14, 10, 15, 7]

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

/** 朋友圈卡片元信息行里、时间与书目之后的那一句（原型：通道 / 关联书目 / 图片张数）。 */
const sourceNote = (post) => {
  if (!post) return ''
  const list = post.attachments || []
  const has = (type) => list.some((a) => a.fileType === type)
  if (post.scopeType === 'suishi') return has('image') ? `图片 ${list.filter((a) => a.fileType === 'image').length} 张` : ''
  const parts = []
  if (has('image')) parts.push('图片')
  if (has('audio')) parts.push('语音')
  if (parts.length) return `${parts.join('+')}${post.bookId ? '' : ' · 未关联书目'}`
  return post.bookId ? '' : '文字 · 未关联书目'
}

const publishVisible = ref(false)
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
// 从操作菜单进详情时直接落到评论区 / 复诵按钮（detail.vue 按 ?focus= 处理）
const openDetailAt = (id, focus) => router.push({ path: `/fabushi/detail/${id}`, query: { focus } })
const goBookDetail = () => { if (bookFilter.value) router.push(`/fabushi/library?bookId=${bookFilter.value}`) }

// 列表里的“敲木鱼”（与详情页同一个接口；点过的再点一次是取消）。同一条内容在请求返回前不能重复点。
const likingIds = ref([])
const toggleLike = async (post) => {
  if (!canCreate.value) {
    ElMessage.warning('当前账号没有互动权限')
    return
  }
  if (likingIds.value.includes(post.id)) return
  likingIds.value.push(post.id)
  try {
    const { data } = await toggleFabushiLike(post.id)
    post.likedByMe = Boolean(data && data.liked)
    post.likeCount = Number(data && data.likeCount) || 0
  } catch (e) {
    // 全局拦截器已提示
  } finally {
    likingIds.value = likingIds.value.filter((id) => id !== post.id)
  }
}
const onMore = (cmd, post) => {
  if (cmd === 'like') toggleLike(post)
  else if (cmd === 'comment') openDetailAt(post.id, 'comment')
  else if (cmd === 'recite') openDetailAt(post.id, 'recite')
  else openDetail(post.id)
}

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
  // 【2026-11-26 按 PC 原型 v8】带地址参数进来时落到对应的视图：
  //   · 带书目编号（?bookId=）= 这本书的按书浏览：切到对应页签、左栏选中这本书、按这本书取内容；
  //   · 有发布权限的人同时直达发布器，并把书预选好（原有行为）；加 publish=0 表示“只看不发”
  //     （详情页右栏的“本书动态”这样进来——看这本书的内容，不是要发布）；
  //   · 只带 scope：切到那一类并按那一类重新取内容（此前切了页签却不重新取，页签下面摆的还是“全部”的列表）。
  const presetScope = String(route.query.scope || '')
  const scopeKnown = contentTabs.some((tab) => tab.value === presetScope)
  const queryBookId = route.query.bookId ? Number(route.query.bookId) : null
  const directPublish = String(route.query.publish || '') !== '0'
  if (queryBookId && Number.isFinite(queryBookId)) {
    // 只带书目编号、没带类型（或类型认不出）：按“再读一本”处理
    const scope = scopeKnown ? presetScope : 'zaidu'
    active.value = scope
    bookFilter.value = queryBookId
    current.value = 1
    loadPosts()
    if (directPublish && canCreate.value) openPublish(scope, queryBookId)
  } else if (scopeKnown) {
    active.value = presetScope
    if (presetScope === 'zaidu' && !bookFilter.value && sortedBooks.value.length) {
      bookFilter.value = sortedBooks.value[0].id
    }
    current.value = 1
    loadPosts()
    if (directPublish && canCreate.value) openPublish(presetScope)
  }
})
</script>

<style scoped>
/* 通用件（页签 .cat-tabs、筛选条 .filter-card、标签 .tag、右栏卡片 .side-*、书脊、左侧子面板 .sub-*、表格卡）在 assets/fabushi-v8.css；
   这里只写 P1 / P2 自己的版式。 */
.main-layout { display: flex; align-items: flex-start; gap: 10px; }
.bybook-left { width: 250px; max-height: calc(100vh - 270px); min-height: 320px; }
.sub-search { margin-bottom: 8px; }
.sub-list { flex: 1; min-height: 0; margin: 0 -12px; overflow-y: auto; }
.sub-name { display: flex; align-items: baseline; gap: 4px; min-width: 0; }
.sub-name > em { overflow: hidden; color: var(--text-placeholder); font-size: 11px; font-style: normal; font-weight: 400; text-overflow: ellipsis; white-space: nowrap; }
.bb-empty { padding: 14px 4px; color: var(--text-placeholder); font-size: 11.5px; }
.table-card { flex: 1; }
/* 列宽合计可能超过右栏宽度：交给 el-table 自己横向滚，别把「时间/操作」裁掉 */
.bb-table { width: 100%; }
.bb-inter { font-variant-numeric: tabular-nums; white-space: nowrap; }

/* P1 朋友圈式信息流 */
.feed-layout { display: flex; align-items: flex-start; gap: 10px; }
.feed-col { display: flex; flex: 1; flex-direction: column; gap: 10px; min-width: 0; min-height: 220px; }
.side-col { position: sticky; top: 0; width: 300px; }
.moments-card { padding: 11px 15px; border-radius: var(--fx-r-md); background: #fff; box-shadow: var(--shadow-card); cursor: pointer; transition: box-shadow .18s; }
.moments-card:hover { box-shadow: var(--shadow-card-hover); }
.mc-head { display: flex; gap: 10px; }
.mc-ava { display: flex; align-items: center; justify-content: center; flex-shrink: 0; width: 38px; height: 38px; border-radius: 8px; background: var(--hero-gradient); color: #fff; font-size: 15px; font-weight: 700; }
.mc-ava.ava-songdu { background: linear-gradient(135deg, #0e7490, #12b8cf); }
.mc-ava.ava-suishi { background: linear-gradient(135deg, #b45309, #f59e0b); }
.mc-main { flex: 1; min-width: 0; }
.mc-name { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; color: var(--fx-navy); font-size: 13px; font-weight: 800; }
.mc-text { margin-top: 3px; color: var(--text-primary); font-size: 13px; line-height: 1.65; white-space: pre-wrap; word-break: break-word; }
.mc-text.title { font-weight: 600; }
.mc-imgs { display: grid; gap: 4px; margin-top: 7px; }
.mc-imgs.g3 { grid-template-columns: repeat(3, 78px); }
.mc-imgs.g1 { grid-template-columns: 170px; }
.mc-img { position: relative; height: 78px; overflow: hidden; border-radius: 6px; cursor: zoom-in; }
.mc-img.big { height: 120px; }
.mc-img :deep(img) { display: block; width: 100%; height: 100%; object-fit: cover; }
.mc-audio { display: flex; align-items: center; gap: 9px; margin-top: 7px; padding: 6px 10px; border-radius: 6px; background: var(--fx-table-head); color: var(--text-secondary); font-size: 11.5px; }
.recite-wave { display: flex; align-items: flex-end; flex-shrink: 0; gap: 2px; width: 34px; height: 16px; }
.recite-wave i { width: 3px; border-radius: 2px; background: var(--primary-color); opacity: .7; }
.mc-audio-ctl { width: 200px; height: 30px; margin-left: auto; }
.mc-audio-ctl + .mc-audio-ctl { margin-left: 0; }
.mc-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-top: 6px; color: var(--text-placeholder); font-size: 11px; }
.mc-meta .src { color: var(--primary-color); }
.mc-meta .read-state { margin-left: auto; }
.mc-acts { display: flex; align-items: center; justify-content: space-between; margin-top: 7px; padding: 4px 10px; border-radius: 6px; background: var(--fx-table-head); color: var(--text-secondary); font-size: 12px; font-variant-numeric: tabular-nums; }
.mc-more { margin-right: -6px; }
.more { padding: 0 6px; color: var(--text-muted); font-size: 14px; font-weight: 800; letter-spacing: 2px; line-height: 22px; cursor: pointer; user-select: none; }
.more:hover { color: var(--primary-color); }
.mc-inter { margin-top: 6px; padding: 6px 10px; border-radius: 6px; background: var(--fx-table-head); color: var(--text-secondary); font-size: 12px; line-height: 1.75; }
.mc-inter .likers { color: var(--fx-navy); font-weight: 600; }
.feed-pager { display: flex; justify-content: flex-end; }

/* 右栏“我的觉醒”三板块状态卡 */
.aw-link { color: var(--primary-color); font-size: 11px; font-weight: 700; white-space: nowrap; }
</style>
