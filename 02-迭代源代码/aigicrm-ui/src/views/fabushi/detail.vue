<template>
  <div class="page-container fabushi-detail">
    <div class="detail-head">
      <el-button link @click="router.back()">← 返回</el-button>
      <span class="detail-kicker">法布施 · 内容详情</span>
    </div>

    <el-card v-loading="loading" shadow="never" class="detail-card">
      <div class="detail-title">{{ post.title || '无标题' }}</div>
      <div class="detail-meta">
        <span>{{ post.userName || '法布施' }}</span>
        <span>{{ formatTime(post.createdAt) }}</span>
        <span v-if="post.readByMe" class="detail-read">已读</span>
      </div>
      <div class="detail-content">{{ post.content }}</div>

      <div class="detail-actions">
        <el-button :type="liked ? 'primary' : 'default'" plain @click="toggleLike">
          🐟 木鱼 {{ likeCount }}
        </el-button>
        <span class="detail-comment-count">💬 {{ comments.length }} 条评论</span>
      </div>
    </el-card>

    <el-card shadow="never" class="comment-card">
      <template #header><span>评论（{{ comments.length }}）</span></template>
      <div v-if="!comments.length" class="comment-empty">还没有评论</div>
      <div v-for="comment in comments" :key="comment.id" class="comment-item">
        <div class="comment-name">{{ comment.userName || '同事' }}</div>
        <div class="comment-content">{{ comment.content }}</div>
        <div class="comment-time">{{ formatTime(comment.createdAt) }}</div>
      </div>

      <div v-if="canCreate" class="comment-form">
        <el-input v-model="commentText" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="写评论…" />
        <div class="comment-submit">
          <el-button type="primary" :loading="submitting" @click="submitComment">发送</el-button>
        </div>
      </div>
      <div v-else class="comment-empty">当前账号没有评论权限</div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import {
  getFabushiPostDetail, getFabushiComments, addFabushiComment, toggleFabushiLike, markFabushiRead,
} from '@/api/fabushi'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const canCreate = computed(() => userStore.hasPermission('fabushi:create'))

const loading = ref(false)
const post = ref({ title: '', userName: '', createdAt: '', readByMe: false, content: '' })
const comments = ref([])
const likeCount = ref(0)
const liked = ref(false)
const commentText = ref('')
const submitting = ref(false)

const formatTime = (value) => (value ? String(value).replace('T', ' ').slice(0, 16) : '')

const loadDetail = async () => {
  loading.value = true
  try {
    const { data } = await getFabushiPostDetail(route.params.id)
    post.value = (data && data.post) || {}
    likeCount.value = Number(data && data.likeCount) || 0
    liked.value = Boolean(data && data.likedByMe)
    markFabushiRead(route.params.id).catch(() => {})
    await loadComments()
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
.fabushi-detail { max-width: 920px; margin: 0 auto; }
.detail-head { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.detail-kicker { color: #9ca3af; font-size: 12px; letter-spacing: 1px; }
.detail-card { margin-bottom: 16px; }
.detail-title { font-size: 22px; font-weight: 700; color: #1f2d3d; }
.detail-meta { display: flex; gap: 16px; margin-top: 8px; color: #9ca3af; font-size: 12px; }
.detail-read { color: #2563eb; }
.detail-content { margin-top: 20px; color: #374151; font-size: 15px; line-height: 1.85; white-space: pre-wrap; }
.detail-actions { display: flex; align-items: center; gap: 16px; margin-top: 24px; padding-top: 16px; border-top: 1px solid #eef0f3; }
.detail-comment-count { color: #9ca3af; font-size: 13px; }
.comment-item { padding: 14px 0; border-bottom: 1px solid #f1f3f6; }
.comment-item:last-of-type { border-bottom: none; }
.comment-name { font-size: 13px; font-weight: 600; color: #374151; }
.comment-content { margin-top: 4px; color: #4b5563; font-size: 14px; line-height: 1.7; white-space: pre-wrap; }
.comment-time { margin-top: 4px; color: #9ca3af; font-size: 11px; }
.comment-empty { padding: 18px 0; color: #9ca3af; font-size: 13px; }
.comment-form { margin-top: 16px; padding-top: 16px; border-top: 1px solid #eef0f3; }
.comment-submit { display: flex; justify-content: flex-end; margin-top: 10px; }
</style>
