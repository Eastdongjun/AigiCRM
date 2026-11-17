<template>
  <div class="fabushi-detail">
    <div class="back">
      <el-button link @click="router.back()">← 返回分享广场</el-button>
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
          <el-button :type="liked ? 'primary' : 'default'" plain @click="toggleLike">🐟 木鱼 {{ likeCount }}</el-button>
          <span class="detail-count">评论 {{ comments.length }}</span>
        </div>

        <div class="comments-head">评论（{{ comments.length }}）</div>
        <div v-if="!comments.length" class="comment-empty">还没有评论</div>
        <div v-for="comment in comments" :key="comment.id" class="comment">
          <b>{{ comment.userName || '同事' }}</b>
          <p>{{ comment.content }}</p>
          <span>{{ formatTime(comment.createdAt) }}</span>
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
          <div class="side-row"><span>我的阅读</span><b>{{ readState ? '已读' : '未读' }}</b></div>
          <div class="side-row"><span>木鱼</span><b>{{ likeCount }}</b></div>
          <div class="side-row"><span>评论</span><b>{{ comments.length }}</b></div>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { refreshDownloadToken, getDownloadToken } from '@/utils/token'
import {
  getFabushiPostDetail, getFabushiComments, addFabushiComment, toggleFabushiLike, markFabushiRead, getFabushiBooks,
} from '@/api/fabushi'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const canCreate = computed(() => userStore.hasPermission('fabushi:create'))

const loading = ref(false)
const post = ref({ title: '', userName: '', createdAt: '', scopeType: 'heart', content: '', bookId: null })
const comments = ref([])
const likeCount = ref(0)
const liked = ref(false)
const readState = ref(false)
const commentText = ref('')
const submitting = ref(false)
const books = ref([])
const attachments = ref([])
const fileToken = ref('')

const book = computed(() => books.value.find((item) => item.id === post.value.bookId) || null)
const isNew = computed(() => !readState.value && post.value.createdAt && (Date.now() - new Date(post.value.createdAt).getTime() < 30 * 24 * 3600 * 1000))
const typeLabel = (value) => ({ heart: '心得', recitation: '诵读', znl: '正能量' }[value] || '内容')
const formatTime = (value) => (value ? String(value).replace('T', ' ').slice(0, 16) : '')
const openReader = () => router.push(`/fabushi/read/${book.value.id}`)
const goLibrary = () => router.push('/fabushi/library')
const attachmentUrl = (item) => `/api/file/download/${item.fileId}?token=${encodeURIComponent(fileToken.value)}`

const loadDetail = async () => {
  loading.value = true
  try {
    const { data } = await getFabushiPostDetail(route.params.id)
    post.value = (data && data.post) || {}
    attachments.value = (data && data.attachments) || []
    likeCount.value = Number(data && data.likeCount) || 0
    liked.value = Boolean(data && data.likedByMe)
    markFabushiRead(route.params.id).then(() => { readState.value = true }).catch(() => {})
    await Promise.all([loadComments(), loadBooks()])
    fileToken.value = (await refreshDownloadToken()) || getDownloadToken() || ''
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

const toggleLike = async () => {
  if (!canCreate.value) {
    ElMessage.warning('当前账号没有互动权限')
    return
  }
  try {
    const { data } = await toggleFabushiLike(route.params.id)
    liked.value = Boolean(data && data.liked)
    likeCount.value = Number(data && data.likeCount) || 0
  } catch (e) {
    // 全局拦截器已提示
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
</script>

<style scoped>
.fabushi-detail { display: flex; flex-direction: column; gap: 10px; }
.back { display: flex; align-items: center; }
.detail-grid { display: grid; grid-template-columns: minmax(0, 1fr) 270px; gap: 12px; align-items: start; }
.panel { padding: 16px 18px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-bg-color); }
.feed-top { display: flex; align-items: center; gap: 8px; }
.tag { padding: 1px 6px; border-radius: var(--radius-sm); font-size: 10px; font-weight: 700; }
.tag.heart { color: var(--primary-color); background: var(--el-color-primary-light-9); }
.tag.recitation { color: var(--el-color-success); background: var(--el-color-success-light-9); }
.tag.znl { color: var(--el-color-warning-dark-2); background: var(--el-color-warning-light-9); }
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
.detail-count { color: var(--text-muted); font-size: 11px; }
.comments-head { margin-top: 16px; color: var(--text-primary); font-size: 13px; font-weight: 700; }
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
