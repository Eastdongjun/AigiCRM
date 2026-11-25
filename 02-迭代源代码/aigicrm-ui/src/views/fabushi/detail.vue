<template>
  <div class="fabushi-detail">
    <div class="back">
      <el-button link @click="router.back()">← 返回认知广场</el-button>
    </div>

    <div class="detail-grid">
      <section class="panel" v-loading="loading">
        <div class="feed-top">
          <span class="tag" :class="post.scopeType">{{ typeLabel(post.scopeType) }}</span>
          <span class="detail-title">{{ post.title || '无标题' }}</span>
          <span v-if="isNew" class="new-badge">NEW</span>
        </div>
        <div class="detail-meta">
          <span>{{ post.userName || '法布施' }}</span>
          <span>{{ formatTime(post.createdAt) }}</span>
          <span class="read">{{ readState ? '已读' : '未读' }}</span>
        </div>
        <div class="detail-body">{{ post.content }}</div>

        <div v-if="attachments.length" class="attachments">
          <div v-for="item in attachments" :key="item.id" class="attachment-item">
            <img v-if="item.fileType === 'image'" :src="attachmentUrl(item)" :alt="item.fileName || '附件'" class="attachment-image" />
            <audio v-else-if="item.fileType === 'audio'" :src="attachmentUrl(item)" controls class="attachment-audio" />
            <video v-else-if="item.fileType === 'video'" :src="attachmentUrl(item)" controls class="attachment-video" />
            <span class="attachment-name">{{ item.fileName || item.fileType }}</span>
          </div>
        </div>

        <div class="detail-actions">
          <!-- 【D25 切片 7】PC 木鱼：敲击动画 + 波纹 + +1 飞起。
               【2026-11-25 用户裁决】“PC 端的木鱼需要做成和小程序的木鱼一样，注意尺寸就行”。
               木鱼图是 FabushiMuyu：与小程序发布成功页那只逐项同源（部件、尺寸、配色、动画都有对账测试），
               这里只定尺寸。小程序原尺寸下整只木鱼约 144×108 像素（手机发布成功页的主视觉）；
               放进 PC 详情页的操作行取一半（MUYU_SCALE）：鱼身约 42×30 像素，连坐垫和木槌约 72×54 像素。 -->
          <button type="button" class="muyu" :class="{ hit: muyuHit, on: liked }" :aria-label="liked ? '取消木鱼' : '敲木鱼'" :aria-pressed="liked" @click="toggleLike">
            <FabushiMuyu :scale="MUYU_SCALE" :hit="muyuHit" :plus-key="muyuPlus" />
            <span class="muyu-text">木鱼 <b>{{ likeCount }}</b></span>
          </button>
          <span class="detail-count">评论 {{ comments.length }}</span>
          <el-button plain type="success" :loading="savingRecite" @click="onReciteClick">
            {{ reciting ? '⏹ 停止并上传' : '🎤 我要复诵' }} · 已被复诵 {{ recitationCount }} 次
          </el-button>
          <template v-if="isOwner">
            <el-button size="small" @click="openEdit">修改</el-button>
            <el-button size="small" type="danger" plain @click="removePost">删除</el-button>
          </template>
        </div>

        <div v-if="recitations.length" class="recite-sec">
          <div class="recite-title">复诵（他人对本段也进行朗读/背诵，不必自建每日诵读）</div>
          <div v-for="item in pagedRecitations" :key="item.id" class="recite-row">
            <b>{{ item.userName || '同事' }}</b>
            <span v-if="item.durationSeconds" class="recite-dur">复诵 {{ item.durationSeconds }} 秒</span>
            <span class="recite-time">{{ formatTime(item.createdAt) }}</span>
            <audio v-if="recitationAudioUrl(item)" :src="recitationAudioUrl(item)" controls class="recite-audio" />
          </div>
        </div>

        <div v-if="recitationTotalPages > 1" class="inline-pager">
          <el-pagination v-model:current-page="recPage" :page-size="recSize" :total="recitationTotal" layout="prev, pager, next" small />
        </div>

        <div class="comments-head">评论（{{ comments.length }}）</div>
        <div v-if="!comments.length" class="comment-empty">还没有评论</div>
        <div v-for="comment in pagedComments" :key="comment.id" class="comment">
          <b>{{ comment.userName || '同事' }}</b>
          <p>{{ comment.content }}</p>
          <span>{{ formatTime(comment.createdAt) }}</span>
        </div>

        <div v-if="commentTotalPages > 1" class="inline-pager">
          <el-pagination v-model:current-page="commentPage" :page-size="commentSize" :total="comments.length" layout="prev, pager, next" small />
        </div>

        <div v-if="canCreate" class="comment-form">
          <el-input v-model="commentText" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="写评论…" />
          <div class="comment-submit">
            <el-button type="primary" :loading="submitting" @click="submitComment">发送</el-button>
          </div>
        </div>
        <div v-else class="comment-empty">当前账号没有评论权限</div>
      </section>

      <aside class="detail-side">
        <section class="side-card">
          <div class="side-title">作者</div>
          <div class="author">
            <div class="avatar">{{ (post.userName || '法').slice(0, 1) }}</div>
            <div>
              <b>{{ post.userName || '法布施' }}</b>
              <span>法布施内容发布人</span>
            </div>
          </div>
        </section>

        <section v-if="book" class="side-card">
          <div class="side-title">关联书目</div>
          <div class="book-row">
            <div class="cover">{{ book.title.slice(0, 4) }}</div>
            <div>
              <b>{{ book.title }}</b>
              <span>{{ book.author || '法布施书目' }}<template v-if="book.pages"> · {{ book.pages }} 页</template></span>
              <el-button v-if="book.pdfFileId" class="book-action" @click="openReader">在线阅读</el-button>
              <el-button v-else class="book-action" @click="goLibrary">去书目浏览</el-button>
            </div>
          </div>
        </section>

        <section class="side-card">
          <div class="side-title">阅读状态</div>
          <div class="side-row"><span>阅读状态</span><b>{{ readState ? '已读' : '未读' }}</b></div>
          <div class="side-row"><span>木鱼</span><b>{{ likeCount }}</b></div>
          <div class="side-row"><span>评论</span><b>{{ comments.length }}</b></div>
        </section>
      </aside>
    </div>

    <el-dialog v-model="editVisible" title="修改内容" width="520px">
      <el-input v-model="editTitle" maxlength="120" placeholder="标题（每日诵读/再读一本可选，每日随施留空）" />
      <el-input v-model="editContent" type="textarea" :rows="6" maxlength="2000" show-word-limit style="margin-top: 10px" placeholder="正文" />
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { refreshDownloadToken, getDownloadToken } from '@/utils/token'
import {
  getFabushiPostDetail, getFabushiComments, addFabushiComment, toggleFabushiLike, markFabushiRead, getFabushiBooks,
  updateFabushiPost, deleteFabushiPost, getFabushiRecitations, addFabushiRecitation, getFabushiSettings,
} from '@/api/fabushi'
import { usePagedList } from '@/composables/usePagedList'
import FabushiMuyu from '@/components/fabushi/FabushiMuyu.vue'

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
const openReader = () => router.push(`/fabushi/read/${book.value.id}`)
const goLibrary = () => router.push('/fabushi/library')
const attachmentUrl = (item) => `/api/file/download/${item.fileId}?token=${encodeURIComponent(fileToken.value)}`

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
  } catch (e) {
    books.value = []
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
    router.back()
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

onMounted(loadDetail)
onBeforeUnmount(() => { if (muyuTimer) clearTimeout(muyuTimer) })
</script>

<style scoped>
.fabushi-detail { display: flex; flex-direction: column; gap: 10px; }
.back { display: flex; align-items: center; }
.detail-grid { display: grid; grid-template-columns: minmax(0, 1fr) 270px; gap: 12px; align-items: start; }
.panel { padding: 16px 18px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-bg-color); }
.feed-top { display: flex; align-items: center; gap: 8px; }
.tag { padding: 1px 6px; border-radius: var(--radius-sm); font-size: 10px; font-weight: 700; }
.tag.songdu { color: var(--primary-color); background: var(--el-color-primary-light-9); }
.tag.suishi { color: var(--el-color-warning-dark-2); background: var(--el-color-warning-light-9); }
.tag.zaidu { color: var(--el-color-success); background: var(--el-color-success-light-9); }
.detail-title { color: var(--text-primary); font-size: 20px; font-weight: 800; }
.new-badge { padding: 1px 5px; border-radius: var(--radius-sm); color: var(--el-color-danger); background: var(--el-color-danger-light-9); font-size: 9px; font-weight: 700; }
.detail-meta { display: flex; gap: 14px; margin-top: 7px; color: var(--text-placeholder); font-size: 11px; }
.detail-meta .read { color: var(--primary-color); }
.attachments { display: flex; flex-direction: column; gap: 8px; margin-top: 14px; }
.attachment-item { display: flex; flex-direction: column; gap: 5px; }
.attachment-image, .attachment-video { max-width: 100%; max-height: 320px; border-radius: var(--radius-sm); border: 1px solid var(--border-light); }
.attachment-audio { width: 100%; }
.attachment-name { color: var(--text-muted); font-size: 11px; }
.detail-body { margin-top: 16px; color: var(--text-secondary); font-size: 14px; line-height: 1.95; white-space: pre-wrap; }
.detail-actions { display: flex; align-items: center; gap: 10px; margin-top: 18px; padding-top: 12px; border-top: 1px solid var(--border-light); }
/* 【D25 · 2026-11-25 用户裁决】木鱼图与小程序同源，画法在 FabushiMuyu（fabushi-muyu.css）里；这里只管它在操作行里的摆法。
   不再套胶囊底：整只木鱼放大之后，按钮的边框和底色会把它框小（小程序和 PC 原型里它都是直接摆在页面上的）。 */
.muyu { position: relative; display: inline-flex; align-items: center; gap: 10px; padding: 0 10px 0 0; border: 0; background: none; cursor: pointer; font: inherit; color: #6b4a2f; }
.muyu:hover .fmuyu { transform: translateY(-1px); }
.muyu:active .fmuyu { transform: scale(.96); }
.muyu:focus-visible { outline: 2px solid var(--primary); outline-offset: 4px; border-radius: 12px; }
.muyu-text { font-size: 13px; white-space: nowrap; }
.muyu-text b { margin-left: 2px; color: var(--text-1); font-size: 16px; font-weight: 800; font-variant-numeric: tabular-nums; }
.muyu.on .muyu-text b { color: #b5543a; }
/* “+1”在小程序里是 22px，按 0.5 缩放后只有 11px，PC 上偏小：这里放大到缩放后 14px（位置、颜色、飞起的动画不变） */
.muyu :deep(.muyu-plus) { font-size: 28px; }
@media (prefers-reduced-motion: reduce) {
  .fmuyu { transition: none; }
}
.detail-count { color: var(--text-muted); font-size: 11px; }
.recite-sec { margin-top: 14px; padding-top: 12px; border-top: 1px dashed var(--border-light); }
.recite-title { color: var(--text-primary); font-size: 12.5px; font-weight: 700; margin-bottom: 6px; }
.recite-row { display: flex; align-items: center; gap: 10px; padding: 7px 0; border-top: 1px solid var(--border-light); font-size: 12px; color: var(--text-secondary); }
.recite-row b { color: var(--text-primary); }
.recite-dur, .recite-time { color: var(--text-muted); font-size: 11px; }
.recite-audio { height: 30px; max-width: 260px; }
.comments-head { margin-top: 16px; color: var(--text-primary); font-size: 13px; font-weight: 700; }
.inline-pager { display: flex; justify-content: flex-end; margin-top: 8px; }
.comment { padding: 10px 0; border-bottom: 1px solid var(--border-light); }
.comment:last-of-type { border-bottom: 0; }
.comment b { color: var(--text-secondary); font-size: 12px; }
.comment p { margin: 3px 0 0; color: var(--text-secondary); font-size: 12.5px; line-height: 1.7; white-space: pre-wrap; }
.comment span { color: var(--text-placeholder); font-size: 10px; }
.comment-empty { padding: 12px 0; color: var(--text-muted); font-size: 12px; }
.comment-form { margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--border-light); }
.comment-submit { display: flex; justify-content: flex-end; margin-top: 8px; }
.detail-side { display: flex; flex-direction: column; gap: 10px; }
.side-card { padding: 13px 14px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-fill-color-lighter); }
.side-title { margin-bottom: 8px; color: var(--text-primary); font-size: 13px; font-weight: 700; }
.author { display: flex; align-items: center; gap: 9px; }
.avatar { display: flex; align-items: center; justify-content: center; width: 34px; height: 34px; border-radius: 50%; color: var(--el-color-white); background: var(--hero-gradient); font-size: 14px; font-weight: 700; }
.author b { display: block; color: var(--text-primary); font-size: 12px; }
.author span { color: var(--text-muted); font-size: 10.5px; }
.book-row { display: flex; align-items: flex-start; gap: 9px; }
.cover { display: flex; align-items: center; justify-content: center; flex-shrink: 0; width: 38px; height: 52px; padding: 4px; border-radius: var(--radius-sm); color: var(--el-color-white); background: var(--hero-gradient); font-size: 10px; line-height: 1.2; text-align: center; }
.book-row b { display: block; color: var(--text-primary); font-size: 12px; }
.book-row span { color: var(--text-muted); font-size: 10.5px; }
.book-action { height: 24px; margin-top: 5px; padding: 0 8px; font-size: 10.5px; }
.side-row { display: flex; justify-content: space-between; padding: 6px 0; border-bottom: 1px solid var(--border-light); color: var(--text-secondary); font-size: 12px; }
.side-row:last-child { border-bottom: 0; }
.side-row b { color: var(--text-primary); font-size: 13px; }
</style>
