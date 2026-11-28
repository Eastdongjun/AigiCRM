<template>
  <div class="fx-page fabushi-detail">
    <!-- 【2026-11-26 按 PC 原型 v8】P3 内容详情：返回条（没有底色）+ 正文卡 + 右栏。
         通用件（标签、NEW、右栏卡片、书脊）在 assets/fabushi-v8.css；P3 自己的版式在本文件底下的 <style scoped>。 -->
    <button type="button" class="back-bar" aria-label="返回认知广场" @click="goBack">
      <ElIcon class="back-ico"><Back /></ElIcon>返回认知广场<span v-if="crumb" class="crumb2">· {{ crumb }}</span>
    </button>

    <div class="dtl-layout">
      <section class="art-card" v-loading="loading">
        <div class="art-title">
          <span class="tag" :class="post.scopeType">{{ typeLabel(post.scopeType) }}</span>
          <span class="art-heading">{{ heading }}</span>
          <span v-if="isNew" class="badge-new">NEW</span>
        </div>
        <div class="art-meta">
          <span class="who">
            <span class="mini-ava">{{ authorInitial }}</span>{{ post.userName || '法布施' }}<template v-if="isOwner">（作者 = 当前登录人）</template>
          </span>
          <span>{{ formatTime(post.createdAt) }}</span>
          <span v-if="bookLine">{{ bookLine }}</span>
          <span class="art-read">阅读状态：{{ readState ? '已读' : '未读' }}</span>
        </div>

        <!-- 本人看得到的修改 / 删除：照原型放在作者行下面这一行（原先混在下面的操作行里）。
             原型里给评审看的标注（“仅作者可见（fabushi:create + 本人）”）不是给使用者看的，不做文案。 -->
        <div v-if="isOwner" class="art-acts owner-acts">
          <el-button size="small" plain :icon="Edit" @click="openEdit">修改</el-button>
          <el-button size="small" plain type="danger" :icon="Delete" @click="removePost">删除</el-button>
        </div>

        <!-- 照原型：正文是带首行缩进的段落，按换行分段 -->
        <div class="art-body">
          <p v-for="(line, index) in bodyLines" :key="index">{{ line }}</p>
        </div>

        <!-- 附件：图片是一排缩略图（原型 78px 的方图），点开可放大、可在同组里翻看。
             附件数据里没有标注信息（只有 fileId / fileType / fileName / fileSize / durationSeconds），所以不放“已标注”的章 -->
        <div v-if="imageAttachments.length" class="art-imgs">
          <div v-for="item in imageAttachments" :key="item.id" class="art-img" :title="item.fileName || '附件'">
            <el-image
              class="art-img-pic"
              :src="attachmentUrl(item)"
              :preview-src-list="imageUrls"
              preview-teleported
              fit="cover"
              :alt="item.fileName || '附件'"
            />
            <span class="art-img-zoom"><ElIcon><ZoomIn /></ElIcon></span>
          </div>
          <span class="art-img-note">图片 {{ imageAttachments.length }} 张 · 点击放大查看</span>
        </div>
        <div v-if="mediaAttachments.length" class="art-media">
          <div v-for="item in mediaAttachments" :key="item.id" class="art-media-item">
            <audio v-if="item.fileType === 'audio'" :src="attachmentUrl(item)" controls class="attachment-audio" />
            <video v-else-if="item.fileType === 'video'" :src="attachmentUrl(item)" controls class="attachment-video" />
            <span class="attachment-name">{{ item.fileName || item.fileType }}</span>
          </div>
        </div>

        <!-- 操作行：木鱼 · 评论数 · 我要复诵（原型的顺序） -->
        <div class="art-acts main-acts">
          <!-- 【D25 切片 7】PC 木鱼：敲击动画 + 波纹 + +1 飞起。
               【2026-11-25 用户裁决】“PC 端的木鱼需要做成和小程序的木鱼一样，注意尺寸就行”。
               木鱼图是 FabushiMuyu：与小程序发布成功页那只逐项同源（部件、尺寸、配色、动画都有对账测试），
               这里只定尺寸。小程序原尺寸下整只木鱼约 144×108 像素（手机发布成功页的主视觉）；
               放进 PC 详情页的操作行取一半（MUYU_SCALE）：鱼身约 42×30 像素，连坐垫和木槌约 72×54 像素。
               原型这一格是一只简化的木鱼（.muyu-disc）：图不换，只照它旁边的摆法——数字在上、“木鱼”小字在下。 -->
          <button type="button" class="muyu" :class="{ hit: muyuHit, on: liked }" :aria-label="liked ? '取消木鱼' : '敲木鱼'" :aria-pressed="liked" @click="toggleLike">
            <FabushiMuyu :scale="MUYU_SCALE" :hit="muyuHit" :plus-key="muyuPlus" />
            <span class="muyu-meta"><b>{{ likeCount }}</b><span>木鱼 · 敲一下</span></span>
          </button>
          <span class="art-count"><ElIcon class="ico"><ChatLineSquare /></ElIcon>评论 {{ comments.length }}</span>
          <el-button
            ref="reciteBtn"
            class="recite-btn"
            :class="{ 'is-reciting': reciting, flash: reciteFlash }"
            :icon="reciting ? VideoPause : Microphone"
            :loading="savingRecite"
            @click="onReciteClick"
          >{{ reciting ? '停止并上传' : '我要复诵' }} · 已被复诵 {{ recitationCount }} 次</el-button>
        </div>

        <div v-if="recitations.length" class="recite-sec">
          <div class="recite-title">复诵（他人对本段也进行朗读/背诵，不必自建诵读）</div>
          <!-- 左侧那一小段波形是固定的装饰（与认知广场卡片里的同一段），不代表真实波形 -->
          <div v-for="item in pagedRecitations" :key="item.id" class="recite-row">
            <span class="recite-wave"><i v-for="h in WAVE" :key="h" :style="{ height: h + 'px' }" /></span>
            <b class="recite-name">{{ item.userName || '同事' }}</b>
            <span class="recite-sub"><template v-if="item.durationSeconds">复诵 {{ item.durationSeconds }} 秒 · </template>{{ formatTime(item.createdAt) }}</span>
            <audio v-if="recitationAudioUrl(item)" :src="recitationAudioUrl(item)" controls class="recite-audio" />
            <!-- 【FABUSHI-DELTA-20261128 R5】仅发起人本人的行显示移除（服务端仍强校验） -->
            <button v-if="Number(item.userId) === Number(userStore.userId)" type="button" class="recite-remove" @click="removeRecitation(item)">移除</button>
          </div>
        </div>

        <div v-if="recitationTotalPages > 1" class="inline-pager">
          <el-pagination v-model:current-page="recPage" :page-size="recSize" :total="recitationTotal" layout="prev, pager, next" small />
        </div>

        <div ref="commentList" class="cmt-list">
          <div v-if="!comments.length" class="comment-empty">还没有评论</div>
          <div v-for="comment in pagedComments" :key="comment.id" class="cmt">
            <div class="cmt-ava">{{ (comment.userName || '同').slice(0, 1) }}</div>
            <div class="cmt-main">
              <div class="cmt-name">{{ comment.userName || '同事' }}<span class="cmt-time">{{ formatTime(comment.createdAt) }}</span></div>
              <div class="cmt-text">{{ comment.content }}</div>
            </div>
          </div>
        </div>

        <div v-if="commentTotalPages > 1" class="inline-pager">
          <el-pagination v-model:current-page="commentPage" :page-size="commentSize" :total="comments.length" layout="prev, pager, next" small />
        </div>

        <div v-if="canCreate" ref="commentForm" class="cmt-input">
          <el-input
            ref="commentInput"
            v-model="commentText"
            class="cmt-field"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 4 }"
            maxlength="500"
            show-word-limit
            placeholder="写评论…"
          />
          <el-button type="primary" :loading="submitting" @click="submitComment">发布评论</el-button>
        </div>
        <div v-else class="comment-empty">当前账号没有评论权限</div>
      </section>

      <aside class="side-col">
        <section class="side-card">
          <div class="side-title">作者</div>
          <div class="author">
            <div class="avatar">{{ authorInitial }}</div>
            <div class="author-main">
              <div class="author-name">{{ post.userName || '法布施' }}</div>
              <div class="author-sub">{{ authorRole }}</div>
            </div>
          </div>
          <!-- 下面几行是作者本人的统计。个人统计接口和“我的书摘”只给当前登录人的数字，
               所以只有“作者 = 当前登录人”时才取、才显示；别人发的内容不拿当前登录人的数字去顶。 -->
          <template v-if="isOwner && authorStats">
            <div class="side-row"><span>本月诵读 / 随施 / 再读</span><b>{{ authorStats.monthSongdu || 0 }} / {{ authorStats.monthSuishi || 0 }} / {{ authorStats.monthZaidu || 0 }}</b></div>
            <div class="side-row"><span>获木鱼</span><b>{{ authorStats.myLikesReceived || 0 }}</b></div>
          </template>
          <div v-if="isOwner && myBookmarkCount !== null" class="side-row hl-row">
            <el-button link type="primary" class="hl-link" @click="goHighlights">我的书摘 {{ myBookmarkCount }} 条 ›</el-button>
          </div>
        </section>

        <section v-if="book" class="side-card">
          <div class="side-title">关联书目</div>
          <div class="rec-book">
            <div class="book-spine">{{ bookSpineText }}</div>
            <div class="rec-main">
              <div class="rec-title">《{{ book.title }}》</div>
              <div v-if="bookMeta" class="rec-meta">{{ bookMeta }}</div>
              <div v-if="hasEbook || otherVersionCount > 0" class="rec-meta rec-tags">
                <span v-if="hasEbook" class="tag blue">电子书</span><span v-if="otherVersionCount > 0">另有 {{ otherVersionCount }} 个版本</span>
              </div>
            </div>
          </div>
          <div class="rec-acts">
            <el-button v-if="hasEbook" size="small" type="primary" @click="openReader">在线阅读</el-button>
            <el-button v-else size="small" plain @click="goLibrary">去书目浏览</el-button>
            <el-button size="small" plain @click="goBookFeed">本书动态</el-button>
          </div>
        </section>

        <section class="side-card">
          <div class="side-title">阅读状态</div>
          <div class="side-row"><span>阅读状态</span><b :class="readState ? 'on' : 'unread'">{{ readState ? '已读' : '未读' }}</b></div>
          <div class="side-row"><span>木鱼 / 评论 / 复诵</span><b>{{ likeCount }} / {{ comments.length }} / {{ recitationCount }}</b></div>
          <p class="side-note">复诵 = 他人挂语音诵读到本内容下；木鱼即点赞。</p>
        </section>
      </aside>
    </div>

    <el-dialog v-model="editVisible" title="修改内容" width="520px" class="fx-dialog">
      <el-input v-model="editTitle" maxlength="120" placeholder="标题（每日诵读/再读一本可选，每日随施留空）" />
      <el-input v-model="editContent" type="textarea" :rows="6" maxlength="2000" show-word-limit style="margin-top: 10px" placeholder="正文" />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="editVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
// ElIcon 显式引入：paged-list-mount.spec.js 没有给 <el-icon> 这种自动引入的标签准备桩，写 <el-icon> 会报“解析不了组件”的警告
import { ElIcon, ElMessage, ElMessageBox } from 'element-plus'
// 图标用图标组件，不用表情符号（原来“我要复诵”按钮上的话筒、停止两个是表情符号）。木鱼图不在这里：它是下面的 FabushiMuyu
import { Back, ChatLineSquare, Delete, Edit, Microphone, VideoPause, ZoomIn } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { refreshDownloadToken, getDownloadToken } from '@/utils/token'
import {
  getFabushiPostDetail, getFabushiComments, addFabushiComment, toggleFabushiLike, markFabushiRead, getFabushiBooks,
  updateFabushiPost, deleteFabushiPost, addFabushiRecitation, removeFabushiRecitation, getFabushiSettings,
  getFabushiPersonalStats, getMyFabushiBookmarks,
} from '@/api/fabushi'
import { usePagedList } from '@/composables/usePagedList'
import FabushiMuyu from '@/components/fabushi/FabushiMuyu.vue'
// 【2026-11-26 按 PC 原型 v8】原型的通用件（标签、NEW、右栏卡片、书脊 …）
import '@/assets/fabushi-v8.css'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const canCreate = computed(() => userStore.hasPermission('fabushi:create'))
const isOwner = computed(() => !!post.value.userId && !!userStore.userId && Number(post.value.userId) === Number(userStore.userId))

const loading = ref(false)
const post = ref({ title: '', userName: '', createdAt: '', scopeType: 'songdu', content: '', bookId: null, userId: null })
const comments = ref([])
const likeCount = ref(0)
const liked = ref(false)
const readState = ref(false)
const commentText = ref('')
const submitting = ref(false)
const books = ref([])
const attachments = ref([])
const fileToken = ref('')
const recitations = ref([])
const recitationCount = ref(0)
const { current: commentPage, size: commentSize, totalPages: commentTotalPages, paged: pagedComments } = usePagedList(comments, { pageSize: 10 })
const { current: recPage, size: recSize, total: recitationTotal, totalPages: recitationTotalPages, paged: pagedRecitations } = usePagedList(recitations, { pageSize: 5 })
// 【D25 计划切片 3】PC 木鱼音效（复用 MP muyu.mp3；受用户设置 soundEnabled 控制，默认开）
const muyuSfx = typeof Audio !== 'undefined' ? new Audio('/audio/muyu.mp3') : null
const soundOn = ref(true)
// PC 上这只木鱼取小程序原尺寸的一半：鱼身约 42×30 像素，和操作行里别的控件比不失衡，木槌和坐垫也看得清
const MUYU_SCALE = 0.5
const muyuHit = ref(false)
const muyuPlus = ref(0)
let muyuTimer = null

const book = computed(() => books.value.find((item) => item.id === post.value.bookId) || null)
const isNew = computed(() => !readState.value && post.value.createdAt && (Date.now() - new Date(post.value.createdAt).getTime() < 30 * 24 * 3600 * 1000))
const typeLabel = (value) => ({ songdu: '每日诵读', suishi: '每日随施', zaidu: '再读一本' }[value] || '内容')
const formatTime = (value) => (value ? String(value).replace('T', ' ').slice(0, 16) : '')
/** 返回认知广场：从别的页面进来的就原路退回（页签、筛选都还在）；收藏、分享链接直接打开的没有上一页
 *  （退回去只是空白页），就直接去认知广场。history.state.back 是路由在历史里记下的“上一页”。 */
const goBack = () => {
  const state = window.history.state
  if (state && state.back) router.back()
  else router.push('/fabushi/feed')
}
const openReader = () => router.push(`/fabushi/read/${book.value.id}`)
const goLibrary = () => router.push('/fabushi/library')
const attachmentUrl = (item) => `/api/file/download/${item.fileId}?token=${encodeURIComponent(fileToken.value)}`

// ===== 【2026-11-26 按 PC 原型 v8】展示用的小函数：只整理已有数据，不改接口调用与数据流 =====
// 列表初值是空数组 / 初始 post 里没有的字段：按 any 读，免得类型检查把它们当成“不存在的属性”
const postAny = () => /** @type {any} */ (post.value)
const bookAny = () => /** @type {any} */ (book.value)
const authorName = computed(() => post.value.userName || '法布施')
const authorInitial = computed(() => authorName.value.slice(0, 1))
// 身份说明：内容上只带得出姓名；作者是本人时才拿得到当前登录人的部门 / 岗位（两样相同就只写一遍）。
// “（本人）”前面放一个不换行符（U+2060）：部门名很长、这一行要折行时，不让“（本人）”被单独挤到最后一行。
const authorRole = computed(() => {
  const parts = isOwner.value ? [...new Set([userStore.deptName, userStore.officeName].filter(Boolean))] : []
  const base = parts.join(' · ') || '法布施内容发布人'
  return isOwner.value ? `${base}\u2060（本人）` : base
})
// “《书名》章节”（返回条后半段用）：书名优先用内容自己带的，其次书目表里的
const bookTitle = computed(() => postAny().bookTitle || (bookAny() && bookAny().title) || '')
const bookPart = computed(() => (bookTitle.value ? `《${bookTitle.value}》${postAny().sectionTitle || ''}` : ''))
// 大标题（原型的写法是“《书名》第 N 章 · 标题”）：内容自己的标题；没有标题、但有书和章节时用“《书名》章节”；都没有才是“无标题”
const heading = computed(() => post.value.title || bookPart.value || '无标题')
// 返回条后半段（原型：“· 《道德经》第 8 章 · 上善若水”；开头的“· ”在模板里）；没有书只到标题，都没有就不显示。
// 大标题已经就是“《书名》章节”（内容没有自己的标题）时，这里不再写一遍
const crumb = computed(() => {
  const title = post.value.title || ''
  if (!bookPart.value) return title
  if (!title || title === bookPart.value) return bookPart.value
  return `${bookPart.value} · ${title}`
})
// 作者行里的书目一项（原型：“《道德经》陈鼓应注 · 中华书局 2009 › 第八章”）
const bookLine = computed(() => {
  if (!bookTitle.value) return ''
  const extra = bookAny() ? [bookAny().author, bookAny().publisher].filter(Boolean).join(' · ') : ''
  const section = postAny().sectionTitle || ''
  return `《${bookTitle.value}》${extra}${section ? ` › ${section}` : ''}`
})
// 照原型分段：按换行切成段落；两头的空行去掉，中间的空行留着当段间距
const bodyLines = computed(() => {
  // 两头换行去掉、中间的留着当段间距。【Sonar javascript:S5850】原来 ^\n+ 与 \n+$ 写在同一条正则的交替里,
  // 被判“优先级不明确”; 拆成两次 replace, 语义更直白。
  const text = String(post.value.content || '').replace(/\r\n?/g, '\n').replace(/^\n+/, '').replace(/\n+$/, '')
  return text ? text.split('\n') : []
})
const imageAttachments = computed(() => /** @type {any[]} */ (attachments.value).filter((item) => item.fileType === 'image'))
const mediaAttachments = computed(() => /** @type {any[]} */ (attachments.value).filter((item) => item.fileType !== 'image'))
const imageUrls = computed(() => imageAttachments.value.map((item) => attachmentUrl(item)))
// 关联书目：“作者 · 出版社 · 页数”；全缺时退回分类名
const bookSpineText = computed(() => String((bookAny() && bookAny().title) || '').slice(0, 4))
const bookMeta = computed(() => {
  const item = bookAny()
  if (!item) return ''
  return [item.author, item.publisher, item.pages ? `${item.pages} 页` : ''].filter(Boolean).join(' · ') || item.categoryName || ''
})
const hasEbook = computed(() => Boolean(bookAny() && bookAny().pdfFileId))
// “另有 N 个版本”＝书目表里同名的其它条目（书名 + 作者 + 版本确定后才是一本「在读的书」，不同版本各自一条）。
// 书目列表一次只取 200 条：没取全时这个数不准，就不写。
const booksTruncated = ref(false)
const otherVersionCount = computed(() => {
  const item = bookAny()
  if (!item || booksTruncated.value) return 0
  const title = String(item.title || '').trim()
  return /** @type {any[]} */ (books.value).filter((other) => other.id !== item.id && String(other.title || '').trim() === title).length
})
// “本书动态”= 认知广场里这本书的按书浏览。带 publish=0：只看这本书的内容，不直达发布器
//（认知广场对带 scope / bookId 的地址默认“直达发布器”，不加这个参数，有发布权限的人一点就弹出“发布 · 再读一本”）
const goBookFeed = () => router.push({ path: '/fabushi/feed', query: { scope: 'zaidu', bookId: bookAny().id, publish: '0' } })
const goHighlights = () => router.push('/fabushi/highlights')

// 右栏“作者”卡里的几行是作者本人的统计。个人统计接口 /fabushi/stats/personal 只给“当前登录人”的数字，
// “我的书摘”同理：所以只有“作者 = 当前登录人”时才取、才显示。取不到就不放那几行，不拿 0 去顶。
/** @type {import('vue').Ref<any>} */
const authorStats = ref(null)
const myBookmarkCount = ref(null)
const loadAuthorCard = () => {
  getFabushiPersonalStats().then((res) => {
    const data = res && res.data
    // 应答不是对象（例如空数组）当没有数据
    authorStats.value = data && typeof data === 'object' && !Array.isArray(data) ? data : null
  }).catch(() => { authorStats.value = null })
  getMyFabushiBookmarks().then((res) => {
    const list = res && res.data
    myBookmarkCount.value = Array.isArray(list) ? list.length : null
  }).catch(() => { myBookmarkCount.value = null })
}

// 复诵行左侧那一小段波形（固定的装饰，与认知广场卡片里的同一段）
const WAVE = [6, 12, 8, 14, 10, 15, 7]

// ===== 【P3 v8】落点：认知广场卡片“⋯”菜单里的“查看评论 / 我要复诵”链过来时带 ?focus= =====
//   focus=comment → 滚到评论输入框，把光标放进去（没有评论权限时没有输入框，就滚到评论区）
//   focus=recite  → 滚到“我要复诵”，让它短暂高亮一下
// 不带这个参数时什么都不做。
/** @type {import('vue').Ref<any>} */
const commentInput = ref(null)
/** @type {import('vue').Ref<any>} */
const commentForm = ref(null)
/** @type {import('vue').Ref<any>} */
const commentList = ref(null)
/** @type {import('vue').Ref<any>} */
const reciteBtn = ref(null)
const reciteFlash = ref(false)
let flashTimer = null
const scrollTo = (el) => {
  if (el && typeof el.scrollIntoView === 'function') el.scrollIntoView({ block: 'center', behavior: 'smooth' })
}
const commentField = () => {
  const root = commentInput.value && commentInput.value.$el
  if (!root) return null
  return /^(TEXTAREA|INPUT)$/.test(root.tagName) ? root : root.querySelector('textarea, input')
}
const applyRouteFocus = async () => {
  const focus = String((route.query && route.query.focus) || '')
  if (!['comment', 'recite'].includes(focus)) return
  // 等评论 / 复诵 / 输入框按数据渲染出来再滚
  await nextTick()
  if (focus === 'comment') {
    const field = commentField()
    scrollTo(commentForm.value || commentList.value)
    // 先抢光标（不让它自己跳一下），再平滑滚过去
    if (field) field.focus({ preventScroll: true })
  } else {
    scrollTo(reciteBtn.value && reciteBtn.value.$el)
    reciteFlash.value = true
    if (flashTimer) clearTimeout(flashTimer)
    flashTimer = setTimeout(() => { reciteFlash.value = false }, 1600)
  }
}

const loadDetail = async () => {
  loading.value = true
  try {
    // 【BUGFIX 2026-11-23】先取下载 token 再挂附件：原来先赋值 attachments、token 最后才取，
    // 首帧 <el-image>/<audio> 已经用 token= 空串发了请求，浏览器里稳定出现一次
    // GET /api/file/download/xxx?token= → 401（附件/语音预览首屏失败，刷新后才好）。
    fileToken.value = (await refreshDownloadToken()) || getDownloadToken() || ''
    const { data } = await getFabushiPostDetail(route.params.id)
    post.value = (data && data.post) || {}
    attachments.value = (data && data.attachments) || []
    likeCount.value = Number(data && data.likeCount) || 0
    liked.value = Boolean(data && data.likedByMe)
    recitations.value = (data && data.recitations) || []
    recitationCount.value = Number(data && data.recitationCount) || 0
    markFabushiRead(route.params.id).then(() => { readState.value = true }).catch(() => {})
    await Promise.all([loadComments(), loadBooks()])
    // 右栏作者卡的统计：只有作者是当前登录人才取（见 loadAuthorCard）；属辅助信息，不等着它
    if (isOwner.value) loadAuthorCard()
    getFabushiSettings().then((res) => { const d = (res && (res.data || res)) || {}; soundOn.value = Number(d.soundEnabled ?? 1) !== 0 }).catch(() => {})
  } finally {
    loading.value = false
  }
}

const loadComments = async () => {
  try {
    const { data } = await getFabushiComments(route.params.id)
    comments.value = Array.isArray(data) ? data : []
  } catch (e) {
    comments.value = []
  }
}

const loadBooks = async () => {
  try {
    const { data } = await getFabushiBooks({ status: 'approved', current: 1, size: 200 })
    books.value = (data && data.records) || []
    // 书目总数比取回来的条数多：列表没取全（见 otherVersionCount）
    booksTruncated.value = Number(data && data.total) > books.value.length
  } catch (e) {
    books.value = []
    booksTruncated.value = false
  }
}

// 【D13 计划切片 2】本人修改/删除（PUT 仅作者本人；软删不断文件）
const editVisible = ref(false)
const editTitle = ref('')
const editContent = ref('')
const saving = ref(false)

const openEdit = () => {
  editTitle.value = post.value.title || ''
  editContent.value = post.value.content || ''
  editVisible.value = true
}

const saveEdit = async () => {
  const title = editTitle.value.trim()
  const content = editContent.value.trim()
  if (!title && !content) {
    ElMessage.warning('标题与正文至少留一项')
    return
  }
  saving.value = true
  try {
    await updateFabushiPost(route.params.id, { title: title || null, content: content || null })
    editVisible.value = false
    ElMessage.success('已保存')
    await loadDetail()
  } finally {
    saving.value = false
  }
}

const removePost = async () => {
  try {
    await ElMessageBox.confirm('删除后可在内容管理（已删除）中找回，附件保留在文件中心。确认删除？', '删除内容', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await deleteFabushiPost(route.params.id)
    ElMessage.success('已删除')
    goBack()
  } catch (e) {
    // 全局拦截器已提示
  }
}

const toggleLike = async () => {
  if (!canCreate.value) {
    ElMessage.warning('当前账号没有互动权限')
    return
  }
  const wasLiked = liked.value
  try {
    const { data } = await toggleFabushiLike(route.params.id)
    liked.value = Boolean(data && data.liked)
    likeCount.value = Number(data && data.likeCount) || 0
    // 【D25】敲击反馈：+1 只在新增点赞时飞起，动画与音效都受用户 soundEnabled 约束
    muyuHit.value = true
    if (!wasLiked && liked.value) muyuPlus.value += 1
    if (muyuTimer) clearTimeout(muyuTimer)
    // 动画类要等最长的一段放完再摘: 与小程序一致，木槌 0.55 秒，波纹与光晕各 0.8 秒
    muyuTimer = setTimeout(() => { muyuHit.value = false }, 820)
    if (muyuSfx && soundOn.value) {
      try { muyuSfx.currentTime = 0; muyuSfx.play() } catch (e) { /* 自动播放策略忽略 */ }
    }
  } catch (e) {
    // 全局拦截器已提示
  }
}

// ===== 【D14 计划切片 3】复诵（PC 录音=MediaRecorder；mp4 优先、webm 回退——Safari 不支持 webm 音频回放） =====
const recitationAudioUrl = (item) => `/api/file/download/${item.audioFileId}?token=${encodeURIComponent(fileToken.value)}`

// 【FABUSHI-DELTA-20261128 R5】移除自己的复诵（服务端强校验发起人；音频软删进回收站）
const removeRecitation = async (item) => {
  try {
    await ElMessageBox.confirm('移除后这段复诵录音将进入文件回收站，确定移除？', '移除复诵', {
      type: 'warning', confirmButtonText: '移除', cancelButtonText: '取消',
    })
  } catch (e) { return }
  try {
    await removeFabushiRecitation(route.params.id, item.id)
    ElMessage.success('已移除')
    await loadDetail()
  } catch (e) {
    // 全局拦截器已提示
  }
}

const pickReciteMime = () => {
  if (!window.isSecureContext || !navigator.mediaDevices || typeof MediaRecorder === 'undefined') return null
  const candidates = ['audio/mp4', 'audio/webm;codecs=opus', 'audio/webm']
  return candidates.find((m) => MediaRecorder.isTypeSupported(m)) || null
}

const reciting = ref(false)
const savingRecite = ref(false)
const stopRecite = ref(null)
const recStartAt = ref(0)

const onReciteClick = () => {
  if (reciting.value) {
    const stop = stopRecite.value
    if (stop) stop()
    return
  }
  startRecite()
}

const startRecite = async () => {
  if (!canCreate.value) {
    ElMessage.warning('当前账号没有互动权限')
    return
  }
  const mime = pickReciteMime()
  if (!mime) {
    ElMessage.warning(window.isSecureContext
      ? '当前浏览器不支持录音，请在微信小程序端复诵'
      : '当前是 HTTP 环境，浏览器禁止麦克风；请改用 HTTPS 打开，或在微信小程序端复诵')
    return
  }
  if (reciting.value) return
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    const chunks = []
    const recorder = new MediaRecorder(stream, { mimeType: mime })
    recorder.ondataavailable = (e) => { if (e.data.size) chunks.push(e.data) }
    recorder.start()
    reciting.value = true
    recStartAt.value = Date.now()
    ElMessage.info('复诵录音中…再次点击结束（≤60 秒）')
    stopRecite.value = () => new Promise((resolve) => {
      recorder.onstop = async () => {
        stream.getTracks().forEach((t) => t.stop())
        const seconds = Math.round((Date.now() - recStartAt.value) / 1000)
        reciting.value = false
        savingRecite.value = true
        try {
          if (seconds > 60) throw new Error('复诵不能超过 60 秒')
          const blob = new Blob(chunks, { type: mime })
          const ext = mime.includes('mp4') ? 'm4a' : 'webm'
          const form = new FormData()
          form.append('file', blob, `recite-${Date.now()}.${ext}`)
          form.append('scope', 'fabushi')
          const uploadRes = await fetch('/api/file/upload', {
            method: 'POST',
            headers: { Authorization: `Bearer ${localStorage.getItem('token') || ''}` },
            body: form,
          }).then((r) => r.json())
          const fileId = uploadRes?.data?.fileId || uploadRes?.data?.id
          if (!fileId) throw new Error(uploadRes?.message || '上传失败')
          await addFabushiRecitation(route.params.id, { audioFileId: fileId, durationSeconds: seconds })
          ElMessage.success('复诵已挂到本内容下')
          await loadDetail()
        } catch (err) {
          ElMessage.error(err?.message || '复诵上传失败')
        } finally {
          savingRecite.value = false
          stopRecite.value = null
          resolve()
        }
      }
      recorder.stop()
    })
  } catch (e) {
    reciting.value = false
    ElMessage.warning('未获得麦克风权限')
  }
}

const submitComment = async () => {
  const content = commentText.value.trim()
  if (!content) {
    ElMessage.warning('评论不能为空')
    return
  }
  submitting.value = true
  try {
    await addFabushiComment(route.params.id, content)
    commentText.value = ''
    ElMessage.success('已评论')
    await loadComments()
  } finally {
    submitting.value = false
  }
}

// 进页面：先照常取详情；从认知广场的操作菜单链过来时，再按 ?focus= 落到评论输入框 / “我要复诵”
onMounted(async () => {
  await loadDetail()
  await applyRouteFocus()
})
onBeforeUnmount(() => {
  if (muyuTimer) clearTimeout(muyuTimer)
  if (flashTimer) clearTimeout(flashTimer)
})
</script>

<style scoped>
/* 通用件（标签 .tag、NEW、右栏卡片 .side-col/.side-card/.side-title/.side-row、书脊 .book-spine、书目卡 .rec-*）
   在 assets/fabushi-v8.css；这里只写 P3 自己的版式，数值取自原型样式表“P3 内容详情”一段。
   与原型的一处不同：原型的页面是定高的，正文卡、正文、评论列表都 overflow:hidden 裁掉超出部分；
   真实页面正文和评论可长可短，要跟着内容长、靠页面滚动，所以这里不裁。 */

/* 返回条：原型 .back-bar（一行文字，没有底色） */
.back-bar {
  display: inline-flex; align-self: flex-start; align-items: center; gap: 8px; max-width: 100%; min-height: 24px;
  padding: 0; border: 0; background: none;
  color: var(--primary-color); font-family: inherit; font-size: 13px; line-height: 1.5; text-align: left; white-space: nowrap; cursor: pointer;
}
.back-bar:hover .crumb2 { color: var(--primary-color); }
.back-bar:focus-visible { outline: 2px solid var(--primary-color); outline-offset: 4px; border-radius: 6px; }
.back-ico { flex-shrink: 0; font-size: 14px; }
.crumb2 { min-width: 0; overflow: hidden; color: var(--text-secondary); text-overflow: ellipsis; }

/* 布局：正文卡 + 右栏（原型 .dtl-layout，右栏宽 300） */
.dtl-layout { display: flex; align-items: flex-start; gap: 10px; }
.side-col { width: 300px; }
.art-card {
  display: flex; flex: 1; flex-direction: column; min-width: 0; min-height: 320px; padding: 18px 22px;
  border-radius: var(--fx-r-md); background: #fff; box-shadow: var(--shadow-card);
}

/* 标题行 */
.art-title { display: flex; align-items: center; gap: 10px; color: var(--text-primary); font-size: 20px; font-weight: 800; line-height: 1.4; }
.art-title .tag { flex-shrink: 0; }
.art-heading { min-width: 0; overflow-wrap: anywhere; }

/* 作者行 */
.art-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 4px 10px; margin-top: 8px; color: var(--text-muted); font-size: 12px; }
.art-meta .who { display: inline-flex; align-items: center; gap: 6px; color: var(--text-secondary); }
.mini-ava {
  display: inline-flex; flex-shrink: 0; align-items: center; justify-content: center; width: 22px; height: 22px;
  border-radius: 50%; background: var(--primary-color); color: var(--el-color-white); font-size: 11px; font-weight: 700;
}
/* 照原型：阅读状态这一项是红色加粗 */
.art-read { color: var(--fx-red); font-weight: 700; }

/* 行内操作行（原型 .art-acts）：本人的修改 / 删除一行、木鱼那一行共用 */
.art-acts { display: flex; align-items: center; gap: 12px; margin-top: 10px; padding-top: 12px; border-top: 1px solid var(--fx-line-soft); }

/* 正文：带首行缩进的段落 */
.art-body { margin-top: 14px; color: var(--text-primary); font-size: 14.5px; line-height: 1.9; overflow-wrap: anywhere; }
.art-body p { margin-bottom: 10px; text-indent: 2em; white-space: pre-wrap; }
.art-body p:last-child { margin-bottom: 0; }

/* 附件：图片缩略图一排（78px 方图），点开放大；右下角的放大镜只是提示，不挡点击 */
.art-imgs { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-top: 12px; }
.art-img {
  position: relative; flex-shrink: 0; width: 78px; height: 78px; overflow: hidden; border-radius: 6px;
  background: var(--fx-track); cursor: zoom-in;
}
.art-img-pic { display: block; width: 100%; height: 100%; }
.art-img-pic :deep(img) { display: block; width: 100%; height: 100%; object-fit: cover; }
.art-img-zoom {
  position: absolute; right: 4px; bottom: 4px; z-index: 1; display: flex; align-items: center; justify-content: center;
  width: 18px; height: 18px; border-radius: 50%; background: rgba(0, 0, 0, .4); color: var(--el-color-white); font-size: 11px; pointer-events: none;
}
.art-img-note { color: var(--text-muted); font-size: 11.5px; line-height: 1.7; }
.art-media { display: flex; flex-direction: column; gap: 8px; margin-top: 12px; }
.art-media-item { display: flex; flex-direction: column; gap: 5px; min-width: 0; }
.attachment-audio { width: 320px; max-width: 100%; height: 32px; }
.attachment-video { max-width: 100%; max-height: 320px; border-radius: 6px; }
.attachment-name { color: var(--text-muted); font-size: 11px; overflow-wrap: anywhere; }

/* 【D25 · 2026-11-25 用户裁决】木鱼图与小程序同源，画法在 FabushiMuyu（fabushi-muyu.css）里；这里只定尺寸和它在操作行里的摆法。
   摆法照原型 .muyu-lite：图在左，右边数字在上（16px / 800）、“木鱼”小字在下；点过的数字变主色。
   不套胶囊底：整只木鱼放大之后，按钮的边框和底色会把它框小（小程序和 PC 原型里它都是直接摆在页面上的）。 */
.muyu {
  position: relative; display: inline-flex; flex-shrink: 0; align-items: center; gap: 10px; margin: 0; padding: 0 10px 0 0;
  border: 0; border-radius: 10px; background: none; color: var(--text-primary); font: inherit; cursor: pointer;
}
.muyu:hover .fmuyu { transform: translateY(-1px); }
.muyu:active .fmuyu { transform: scale(.96); }
.muyu:focus-visible { outline: 2px solid var(--primary-color); outline-offset: 4px; }
.muyu-meta { display: flex; flex-direction: column; gap: 2px; text-align: left; white-space: nowrap; }
.muyu-meta b { color: var(--text-primary); font-size: 16px; font-weight: 800; font-variant-numeric: tabular-nums; line-height: 1; }
.muyu-meta > span { color: var(--text-muted); font-size: 10.5px; }
.muyu.on .muyu-meta b { color: var(--primary-color); }
/* “+1”在小程序里是 22px，按 0.5 缩放后只有 11px，PC 上偏小：这里放大到缩放后 14px（位置、颜色、飞起的动画不变） */
.muyu :deep(.muyu-plus) { font-size: 28px; }
@media (prefers-reduced-motion: reduce) {
  .fmuyu { transition: none; }
}

/* 评论数（原型 .art-count） */
.art-count {
  display: inline-flex; flex-shrink: 0; align-items: center; gap: 5px;
  color: var(--text-muted); font-size: 12.5px; font-variant-numeric: tabular-nums; white-space: nowrap;
}
.ico { flex-shrink: 0; font-size: 14px; }

/* “我要复诵”（原型 .recite-btn：浅蓝胶囊）；录音中换成浅红，提醒再点一次结束 */
.recite-btn {
  flex-shrink: 0; height: 32px; margin-left: 0; padding: 0 14px; border: 0; border-radius: var(--fx-r-pill);
  background: var(--el-color-primary-light-9); color: var(--primary-color); font-size: 12.5px; font-weight: 700; white-space: nowrap;
}
.recite-btn:hover { background: var(--el-color-primary-light-8); color: var(--primary-color); }
.recite-btn.is-reciting { background: var(--fx-red-bg); color: var(--fx-red); }
.recite-btn.is-reciting:hover { background: var(--fx-red-bg); color: var(--fx-red); }
/* 从认知广场“我要复诵”链过来时（?focus=recite），高亮一下指个位置：底色加深一档 + 外圈光环，
   约 0.6 秒里保持最明显，1.6 秒收完（与 script 里的定时一致） */
.recite-btn.flash { animation: recite-flash 1.6s ease-out; }
@keyframes recite-flash {
  0% { box-shadow: 0 0 0 0 rgba(29, 78, 216, .6); background-color: var(--el-color-primary-light-7); }
  22% { box-shadow: 0 0 0 8px rgba(29, 78, 216, .34); background-color: var(--el-color-primary-light-8); }
  60% { box-shadow: 0 0 0 8px rgba(29, 78, 216, .34); background-color: var(--el-color-primary-light-8); }
  100% { box-shadow: 0 0 0 12px rgba(29, 78, 216, 0); background-color: var(--el-color-primary-light-9); }
}
@media (prefers-reduced-motion: reduce) {
  .recite-btn.flash { animation: none; box-shadow: 0 0 0 3px var(--el-color-primary-light-7); background-color: var(--el-color-primary-light-8); }
}

/* 复诵区（原型 .recite-sec / .recite-row / .recite-wave） */
.recite-sec { margin-top: 10px; padding-top: 10px; border-top: 1px dashed var(--fx-line); }
.recite-title { margin-bottom: 2px; color: var(--text-primary); font-size: 12.5px; font-weight: 700; }
.recite-row { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-top: 1px solid var(--fx-line-soft); }
.recite-wave { display: flex; flex-shrink: 0; align-items: flex-end; gap: 2px; width: 34px; height: 16px; }
.recite-wave i { width: 3px; border-radius: 2px; background: var(--primary-color); opacity: .7; }
.recite-name { flex-shrink: 0; color: var(--text-primary); font-size: 12px; font-weight: 700; }
.recite-sub { flex-shrink: 0; color: var(--text-muted); font-size: 11px; }
.recite-audio { flex-shrink: 0; width: 240px; max-width: 46%; height: 32px; margin-left: auto; }
/* 【FABUSHI-DELTA-20261128 R5】仅发起人本人可见的移除入口 */
.recite-remove { flex-shrink: 0; border: none; background: none; padding: 0 2px; font-size: 11px; color: var(--el-color-danger); cursor: pointer; }
.recite-remove:hover { text-decoration: underline; }

/* 评论（原型 .cmt / .cmt-ava / .cmt-name / .cmt-time / .cmt-text） */
.cmt-list { margin-top: 10px; }
.cmt { display: flex; gap: 10px; padding: 9px 0; border-top: 1px solid var(--fx-line-soft); }
.cmt-ava {
  display: flex; flex-shrink: 0; align-items: center; justify-content: center; width: 28px; height: 28px;
  border-radius: 50%; background: var(--hero-gradient); color: var(--el-color-white); font-size: 12px; font-weight: 700;
}
.cmt-main { flex: 1; min-width: 0; }
.cmt-name { color: var(--text-primary); font-size: 12px; font-weight: 700; overflow-wrap: anywhere; }
.cmt-time { margin-left: 6px; color: var(--text-placeholder); font-size: 10.5px; font-weight: 400; }
.cmt-text { margin-top: 3px; color: var(--text-secondary); font-size: 12.5px; line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; }
.comment-empty { padding: 10px 0; color: var(--text-muted); font-size: 12px; }

/* 底部：输入框一行 + 右侧“发布评论”（原型 .cmt-input）。多行文本框留着，起步只有一行，随内容长高（最多 4 行）。
   原型的输入框高 44px：Element Plus 的自适应高度按“行高 + 上下内边距”算一行的高度并写进行内样式（它没有边框，线是内阴影画的），
   所以这里用 12px 内边距 + 20px 行高凑成 44px，而不是写 min-height（会被行内样式盖掉） */
.cmt-input { display: flex; align-items: flex-end; gap: 8px; margin-top: 8px; }
.cmt-field { flex: 1; min-width: 0; }
.cmt-field :deep(.el-textarea__inner) { padding: 12px 10px; border-radius: var(--radius-sm); line-height: 1.6; }
.cmt-input .el-button { flex-shrink: 0; margin-left: 0; }

.inline-pager { display: flex; justify-content: flex-end; margin-top: 8px; }

/* 右栏“作者”卡：头像 + 姓名 + 身份说明；下面几行统计用的是共享的 .side-row */
.author { display: flex; align-items: center; gap: 10px; }
.avatar {
  display: flex; flex-shrink: 0; align-items: center; justify-content: center; width: 40px; height: 40px;
  border-radius: 50%; background: var(--hero-gradient); color: var(--el-color-white); font-size: 16px; font-weight: 700;
}
.author-main { min-width: 0; }
.author-name { color: var(--text-primary); font-size: 14px; font-weight: 700; overflow-wrap: anywhere; }
.author-sub { margin-top: 2px; color: var(--text-muted); font-size: 11.5px; }
.hl-link { margin: 0 0 0 -6px; font-weight: 700; }

/* 右栏“关联书目”卡：书脊、书名、“作者 · 出版社 · 页数”用的是共享的 .rec-* */
.rec-tags { display: flex; align-items: center; }
.rec-tags .tag { margin-right: 4px; }

/* 右栏“阅读状态”卡：已读是共享的 b.on（绿），未读这里补一个红 */
.side-row b.unread { color: var(--fx-red); }
</style>
