<template>
  <div class="fabushi-feed">
    <div class="page-head">
      <div>
        <h1>法布施</h1>
        <p>读书 · 心得 · 正能量</p>
      </div>
      <el-button v-if="canCreate" type="primary" @click="openPublish">＋ 发布内容</el-button>
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

    <div class="filter">
      <span class="fl">筛选</span>
      <el-select v-model="bookFilter" clearable placeholder="全部书目" style="width: 150px" @change="resetPage">
        <el-option v-for="book in bookOptions" :key="book.id" :label="book.title" :value="book.id" />
      </el-select>
      <el-input v-model="keyword" clearable placeholder="搜标题 / 正文 / 作者" style="width: 230px" @keyup.enter="resetPage" />
      <span class="spacer" />
      <el-checkbox v-model="unreadOnly">只看未读</el-checkbox>
      <el-button v-if="canView" link type="primary" @click="resetFilters">重置</el-button>
    </div>

    <div class="feed-layout">
      <section class="feed-list" v-loading="loading">
        <article v-for="post in visiblePosts" :key="post.id" class="feed-item" @click="openDetail(post.id)">
          <div class="feed-top">
            <span class="tag" :class="post.scopeType">{{ typeLabel(post.scopeType) }}</span>
            <span class="feed-title">{{ post.title || '无标题' }}</span>
            <span v-if="isNew(post)" class="new-badge">NEW</span>
            <span class="feed-time">{{ formatTime(post.createdAt) }}</span>
          </div>
          <div class="feed-body">{{ post.content }}</div>
          <div v-if="bookName(post)" class="feed-book">书目 · {{ bookName(post) }}</div>
          <div class="feed-meta">
            <span>{{ post.userName || '法布施' }}</span>
            <span>木鱼 {{ post.likeCount || 0 }}</span>
            <span>评论 {{ post.commentCount || 0 }}</span>
            <span class="read" :class="{ unread: !post.readByMe }">{{ post.readByMe ? '已读' : '未读' }}</span>
          </div>
        </article>
        <el-empty v-if="!loading && !visiblePosts.length" description="暂无法布施内容" />
      </section>

      <aside class="feed-side">
        <section class="side-card">
          <div class="side-title">我的法布施</div>
          <div class="side-row"><span>我的内容</span><b>{{ personal.myPosts || 0 }}</b></div>
          <div class="side-row"><span>我的评论</span><b>{{ personal.myComments || 0 }}</b></div>
          <div class="side-row"><span>我的阅读</span><b>{{ personal.myReadCount || 0 }}</b></div>
          <div class="side-row"><span>获木鱼</span><b>{{ personal.myLikesReceived || 0 }}</b></div>
        </section>

        <section v-if="recommendedBook" class="side-card">
          <div class="side-title">今日推荐书目</div>
          <div class="side-book">
            <div class="cover">{{ recommendedBook.title.slice(0, 4) }}</div>
            <div>
              <b>{{ recommendedBook.title }}</b>
              <span>{{ recommendedBook.author || '法布施书目' }}<template v-if="recommendedBook.pages"> · {{ recommendedBook.pages }} 页</template></span>
            </div>
          </div>
          <el-button class="side-action" @click="goLibrary">进入书目浏览</el-button>
        </section>
      </aside>
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

    <el-dialog v-model="publishVisible" title="发布内容" width="620px" :close-on-click-modal="false">
      <div class="form-row">
        <label>类型</label>
        <div class="type-caps">
          <button
            v-for="tab in tabs"
            :key="tab.value"
            type="button"
            class="type-cap"
            :class="{ active: form.scopeType === tab.value }"
            @click="form.scopeType = tab.value"
          >{{ tab.label }}</button>
        </div>
      </div>
      <div class="form-row">
        <label>关联书目</label>
        <el-select v-model="form.bookId" clearable placeholder="选择书目（可选）" style="width: 100%">
          <el-option v-for="book in bookOptions" :key="book.id" :label="book.title" :value="book.id" />
        </el-select>
      </div>
      <div class="form-row">
        <label>标题</label>
        <el-input v-model="form.title" maxlength="120" placeholder="给这段内容起个标题（可选）" />
      </div>
      <div class="form-row">
        <label>正文</label>
        <el-input v-model="form.content" type="textarea" :rows="6" maxlength="2000" show-word-limit placeholder="写下你的读书心得、诵读内容或今天的正能量…" />
      </div>
      <div class="form-row">
        <label>附件</label>
        <div class="attachment-field">
          <div class="attachment-actions">
            <el-button size="small" :loading="uploadingAttachment" @click="pickAttachment">选择图片 / 音视频</el-button>
            <span class="attachment-hint">上传到法布施空间；最多 9 个</span>
            <input ref="attachmentInputRef" type="file" accept="image/*,audio/*,video/*" multiple style="display:none" @change="handleAttachmentChosen" />
          </div>
          <div v-for="(file, index) in uploadedAttachments" :key="file.fileId" class="attachment-chip">
            <span>{{ file.name }}</span>
            <el-button link type="danger" @click="removePendingAttachment(index)">移除</el-button>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitPost">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import {
  getFabushiPosts, createFabushiPost, getFabushiBooks, getFabushiPersonalStats, addFabushiAttachment,
} from '@/api/fabushi'
import request from '@/utils/request'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const canCreate = computed(() => userStore.hasPermission('fabushi:create'))
const canView = computed(() => userStore.hasPermission('fabushi:view'))

const tabs = [
  { value: 'heart', label: '心得' },
  { value: 'recitation', label: '诵读' },
  { value: 'znl', label: '正能量' },
]
const active = ref('heart')
const counts = reactive({ heart: 0, recitation: 0, znl: 0 })
const posts = ref([])
const total = ref(0)
const current = ref(1)
const size = 10
const loading = ref(false)
const personal = ref({ myPosts: 0, myComments: 0, myReadCount: 0, myLikesReceived: 0 })
const bookOptions = ref([])
const keyword = ref('')
const bookFilter = ref(null)
const unreadOnly = ref(false)

const publishVisible = ref(false)
const submitting = ref(false)
const form = reactive({ scopeType: 'heart', bookId: null, title: '', content: '' })
const attachmentInputRef = ref(null)
const uploadingAttachment = ref(false)
const uploadedAttachments = ref([])

const recommendedBook = computed(() => bookOptions.value.find((book) => book.pdfFileId) || bookOptions.value[0] || null)
const visiblePosts = computed(() => posts.value.filter((post) => {
  const text = `${post.title || ''} ${post.content || ''} ${post.userName || ''}`.toLowerCase()
  if (keyword.value && !text.includes(keyword.value.toLowerCase())) return false
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

const loadCounts = async () => {
  const results = await Promise.all(tabs.map((tab) => getFabushiPosts({ scopeType: tab.value, current: 1, size: 1 })))
  tabs.forEach((tab, index) => {
    counts[tab.value] = Number(results[index]?.data?.total) || 0
  })
}

const loadPersonal = async () => {
  try {
    personal.value = (await getFabushiPersonalStats()).data || { myPosts: 0, myComments: 0, myReadCount: 0, myLikesReceived: 0 }
  } catch (e) {
    personal.value = { myPosts: 0, myComments: 0, myReadCount: 0, myLikesReceived: 0 }
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

const pickAttachment = () => attachmentInputRef.value?.click()
const attachmentType = (file) => file.type.startsWith('image/') ? 'image' : file.type.startsWith('audio/') ? 'audio' : 'video'
const handleAttachmentChosen = async (event) => {
  const files = Array.from(event.target.files || []).slice(0, 9 - uploadedAttachments.value.length)
  if (!files.length) return
  uploadingAttachment.value = true
  try {
    for (const file of files) {
      const data = new FormData()
      data.append('file', file)
      data.append('scope', 'fabushi')
      const res = await request.post('/file/upload', data)
      const id = res.data && res.data.id
      if (id) uploadedAttachments.value.push({ fileId: id, name: file.name, fileType: attachmentType(file) })
    }
  } finally {
    uploadingAttachment.value = false
    event.target.value = ''
  }
}
const removePendingAttachment = (index) => { uploadedAttachments.value.splice(index, 1) }

const openPublish = () => {
  form.scopeType = active.value
  form.bookId = route.query.bookId ? Number(route.query.bookId) : null
  form.title = ''
  form.content = ''
  uploadedAttachments.value = []
  publishVisible.value = true
}

const submitPost = async () => {
  if (!form.title.trim() && !form.content.trim()) {
    ElMessage.warning('标题和正文至少填一项')
    return
  }
  submitting.value = true
  try {
    const created = await createFabushiPost({
      scopeType: form.scopeType,
      bookId: form.bookId || null,
      title: form.title.trim() || null,
      content: form.content.trim() || null,
    })
    const postId = created.data && created.data.id
    for (const file of uploadedAttachments.value) {
      await addFabushiAttachment(postId, { fileId: file.fileId, fileType: file.fileType })
    }
    ElMessage.success('发布成功')
    publishVisible.value = false
    current.value = 1
    await Promise.all([loadPosts(), loadCounts(), loadPersonal()])
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadPosts(), loadCounts(), loadPersonal(), loadBooks()])
  if (route.query.bookId && canCreate.value) {
    openPublish()
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
.filter { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; padding: 9px 12px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-bg-color); }
.fl { color: var(--text-secondary); font-size: 12px; }
.spacer { flex: 1; }
.feed-layout { display: grid; grid-template-columns: minmax(0, 1fr) 268px; gap: 12px; align-items: start; }
.feed-list { display: flex; flex-direction: column; gap: 10px; min-height: 220px; }
.feed-item { padding: 13px 15px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-fill-color-lighter); cursor: pointer; transition: background .18s, box-shadow .18s; }
.feed-item:hover { background: var(--el-bg-color); box-shadow: var(--shadow-card-hover); }
.feed-top { display: flex; align-items: center; gap: 8px; margin-bottom: 5px; }
.tag { padding: 1px 6px; border-radius: var(--radius-sm); font-size: 10px; font-weight: 700; }
.tag.heart { color: var(--primary-color); background: var(--el-color-primary-light-9); }
.tag.recitation { color: var(--el-color-success); background: var(--el-color-success-light-9); }
.tag.znl { color: var(--el-color-warning-dark-2); background: var(--el-color-warning-light-9); }
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
.side-book { display: flex; align-items: flex-start; gap: 9px; margin-top: 7px; }
.cover { display: flex; align-items: center; justify-content: center; flex-shrink: 0; width: 38px; height: 52px; padding: 4px; border-radius: var(--radius-sm); color: var(--el-color-white); background: var(--hero-gradient); font-size: 10px; line-height: 1.2; text-align: center; }
.side-book b { display: block; color: var(--text-primary); font-size: 12px; }
.side-book span { color: var(--text-muted); font-size: 10.5px; }
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
