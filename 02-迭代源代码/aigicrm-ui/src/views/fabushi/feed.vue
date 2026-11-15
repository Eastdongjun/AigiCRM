<template>
  <div class="fabushi-feed">
    <div class="page-header">
      <div>
        <h2>法布施</h2>
        <p class="page-desc">读书 · 心得 · 正能量</p>
      </div>
      <el-button v-if="canCreate" type="primary" @click="openPublish">发布内容</el-button>
    </div>

    <div class="feed-layout">
      <section class="feed-main">
        <el-tabs v-model="active" class="feed-tabs" @tab-change="onTabChange">
          <el-tab-pane label="心得" name="heart" />
          <el-tab-pane label="诵读" name="recitation" />
          <el-tab-pane label="正能量" name="znl" />
        </el-tabs>

        <div v-loading="loading" class="feed-list">
          <article v-for="post in posts" :key="post.id" class="feed-card" @click="openDetail(post.id)">
            <div class="feed-card-head">
              <span class="feed-title">{{ post.title || '无标题' }}</span>
              <span class="feed-time">{{ formatTime(post.createdAt) }}</span>
            </div>
            <div class="feed-content">{{ post.content }}</div>
            <div class="feed-meta">
              <span>{{ post.userName || '法布施' }}</span>
              <span>🐟 {{ post.likeCount || 0 }}</span>
              <span>💬 {{ post.commentCount || 0 }}</span>
              <span v-if="post.readByMe" class="feed-read">已读</span>
            </div>
          </article>
          <el-empty v-if="!loading && !posts.length" description="暂无法布施内容" />
        </div>

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

      <aside class="feed-side">
        <el-card shadow="never" class="side-card">
          <template #header><span>我的法布施</span></template>
          <div class="side-stat"><span>我的内容</span><strong>{{ personal.myPosts || 0 }}</strong></div>
          <div class="side-stat"><span>我的评论</span><strong>{{ personal.myComments || 0 }}</strong></div>
          <div class="side-stat"><span>我的阅读</span><strong>{{ personal.myReadCount || 0 }}</strong></div>
          <div class="side-stat"><span>获木鱼</span><strong>{{ personal.myLikesReceived || 0 }}</strong></div>
        </el-card>
        <el-card shadow="never" class="side-card">
          <template #header><span>去读书</span></template>
          <p class="side-hint">浏览已通过审核的书目，查看电子书。</p>
          <el-button link type="primary" @click="router.push('/fabushi/library')">进入书目浏览 →</el-button>
        </el-card>
      </aside>
    </div>

    <el-dialog v-model="publishVisible" title="发布内容" width="560px" :close-on-click-modal="false">
      <el-form label-width="76px">
        <el-form-item label="类型">
          <el-select v-model="form.scopeType" style="width: 100%">
            <el-option label="心得" value="heart" />
            <el-option label="诵读" value="recitation" />
            <el-option label="正能量" value="znl" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联书目">
          <el-select v-model="form.bookId" clearable placeholder="可选" style="width: 100%">
            <el-option v-for="book in bookOptions" :key="book.id" :label="book.title" :value="book.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="form.title" maxlength="120" placeholder="标题（可选，正文必填其一）" />
        </el-form-item>
        <el-form-item label="正文">
          <el-input v-model="form.content" type="textarea" :rows="6" maxlength="2000" show-word-limit placeholder="写点什么…" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitPost">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import {
  getFabushiPosts, createFabushiPost, getFabushiBooks, getFabushiPersonalStats,
} from '@/api/fabushi'

const router = useRouter()
const userStore = useUserStore()
const canCreate = computed(() => userStore.hasPermission('fabushi:create'))

const active = ref('heart')
const posts = ref([])
const total = ref(0)
const current = ref(1)
const size = 10
const loading = ref(false)
const personal = ref({ myPosts: 0, myComments: 0, myReadCount: 0, myLikesReceived: 0 })

const publishVisible = ref(false)
const submitting = ref(false)
const bookOptions = ref([])
const form = reactive({ scopeType: 'heart', bookId: null, title: '', content: '' })

const formatTime = (value) => (value ? String(value).replace('T', ' ').slice(0, 16) : '')

const onTabChange = () => {
  current.value = 1
  loadPosts()
}

const loadPosts = async () => {
  loading.value = true
  try {
    const { data } = await getFabushiPosts({ scopeType: active.value, current: current.value, size })
    posts.value = (data && data.records) || []
    total.value = Number(data && data.total) || 0
  } finally {
    loading.value = false
  }
}

const loadPersonal = async () => {
  try {
    personal.value = (await getFabushiPersonalStats()).data || { myPosts: 0, myComments: 0, myReadCount: 0, myLikesReceived: 0 }
  } catch (e) {
    personal.value = { myPosts: 0, myComments: 0, myReadCount: 0, myLikesReceived: 0 }
  }
}

const openDetail = (id) => router.push(`/fabushi/detail/${id}`)

const openPublish = async () => {
  form.scopeType = 'heart'
  form.bookId = null
  form.title = ''
  form.content = ''
  publishVisible.value = true
  if (!bookOptions.value.length) {
    try {
      const { data } = await getFabushiBooks({ status: 'approved', current: 1, size: 100 })
      bookOptions.value = (data && data.records) || []
    } catch (e) {
      bookOptions.value = []
    }
  }
}

const submitPost = async () => {
  if (!form.title.trim() && !form.content.trim()) {
    ElMessage.warning('标题和正文至少填一项')
    return
  }
  submitting.value = true
  try {
    await createFabushiPost({
      scopeType: form.scopeType,
      bookId: form.bookId || null,
      title: form.title.trim() || null,
      content: form.content.trim() || null,
    })
    ElMessage.success('发布成功')
    publishVisible.value = false
    current.value = 1
    await Promise.all([loadPosts(), loadPersonal()])
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadPosts()
  loadPersonal()
})
</script>

<style scoped>
.fabushi-feed { padding-bottom: 24px; }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 16px; }
.page-header h2 { margin: 0 0 4px; color: var(--text-primary); font-size: 20px; font-weight: 600; }
.page-desc { margin: 0; color: var(--text-muted); font-size: 13px; }
.feed-layout { display: grid; grid-template-columns: minmax(0, 1fr) 280px; gap: 16px; align-items: start; }
.feed-main { min-width: 0; }
.feed-side { display: flex; flex-direction: column; gap: 16px; }
.fabushi-feed .side-card { border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-fill-color-lighter); }
.side-card :deep(.el-card__header) { padding: 14px 16px; color: var(--text-primary); font-size: 14px; font-weight: 600; }
.side-card :deep(.el-card__body) { padding: 6px 16px 12px; }
.side-stat { display: flex; justify-content: space-between; padding: 8px 0; color: var(--text-secondary); font-size: 13px; border-bottom: 1px solid var(--border-light); }
.side-stat:last-child { border-bottom: none; }
.side-stat strong { color: var(--text-primary); font-size: 16px; font-variant-numeric: tabular-nums; }
.side-hint { margin: 0 0 10px; color: var(--text-muted); font-size: 13px; line-height: 1.6; }
.feed-list { display: flex; flex-direction: column; gap: 14px; min-height: 240px; }
.feed-card { min-height: 132px; background: var(--el-fill-color-lighter); border: 1px solid var(--border-light); border-radius: var(--radius-md); padding: 18px 20px; cursor: pointer; transition: background .18s, box-shadow .18s, transform .18s; }
.feed-card:hover { transform: translateY(-2px); background: var(--el-bg-color); box-shadow: var(--shadow-card-hover); }
.feed-card-head { display: flex; justify-content: space-between; gap: 12px; align-items: baseline; }
.feed-title { color: var(--text-primary); font-size: 15px; font-weight: 600; }
.feed-time { flex-shrink: 0; color: var(--text-placeholder); font-size: 12px; }
.feed-content { margin-top: 8px; color: var(--text-secondary); font-size: 14px; line-height: 1.85; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.feed-meta { display: flex; gap: 18px; margin-top: 12px; color: var(--text-muted); font-size: 12px; }
.feed-read { color: var(--primary-color); }
.feed-pager { display: flex; justify-content: flex-end; margin-top: 14px; padding-top: 14px; border-top: 1px solid var(--border-light); }
@media (max-width: 1100px) { .feed-layout { grid-template-columns: 1fr; } }

</style>
