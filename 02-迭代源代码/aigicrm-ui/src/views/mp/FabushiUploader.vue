<template>
  <div class="mfbu">
    <!-- 仅微信小程序 web-view 内可用: URL 只带短时上传票(5min/scope=upload), 主 JWT 绝不进 URL -->
    <div v-if="!isWechat" class="mfbu-block">
      <ElIcon class="mfbu-icon"><Lock /></ElIcon>
      <div class="mfbu-title">请从微信小程序进入</div>
      <div class="mfbu-desc">本页面在小程序「法布施 → 发布 → 手机文件」中使用</div>
    </div>

    <div v-else-if="!ticket" class="mfbu-block">
      <ElIcon class="mfbu-icon"><Warning /></ElIcon>
      <div class="mfbu-title">上传票缺失或已过期</div>
      <div class="mfbu-desc">请返回小程序重新进入（票有效期 5 分钟）</div>
    </div>

    <template v-else>
      <div class="mfbu-head">
        <div class="mfbu-title">上传手机文件到法布施</div>
        <div class="mfbu-desc">支持图片(≤10MB) / 语音(≤20MB) / 视频(≤100MB) / PDF(≤100MB)，可多选</div>
      </div>

      <div class="mfbu-body">
        <div v-if="!items.length" class="mfbu-pick" @click="pickFiles">
          <ElIcon class="mfbu-pick-icon"><UploadFilled /></ElIcon>
          <div class="mfbu-pick-text">点击选择手机文件</div>
          <div class="mfbu-pick-sub">iOS「文件」App / 安卓文件管理器</div>
        </div>

        <template v-else>
          <div v-for="(it, i) in items" :key="i" class="mfbu-item" :class="'s-' + it.state">
            <div class="mfbu-item-name">{{ it.name }}</div>
            <div class="mfbu-item-sub">
              <span v-if="it.state === 'waiting'">等待中</span>
              <span v-else-if="it.state === 'uploading'">{{ it.progress }}%</span>
              <span v-else-if="it.state === 'done'" class="mfbu-ok">✓ 已上传</span>
              <span v-else class="mfbu-err">{{ it.error }}</span>
            </div>
            <div v-if="it.state === 'uploading'" class="mfbu-bar"><div class="mfbu-bar-in" :style="{ width: it.progress + '%' }"></div></div>
            <button v-if="it.state === 'error'" class="mfbu-retry" @click="retry(i)">重试</button>
          </div>
          <div v-if="!allDone" class="mfbu-pick small" @click="pickFiles">＋ 继续添加</div>
          <button class="mfbu-done" :disabled="!anyOk || uploading" @click="finish">
            {{ uploading ? '上传中…' : (anyOk ? '完成并返回' : '全部失败，点文件重试') }}
          </button>
        </template>
      </div>

      <div class="mfbu-foot">完成后自动返回小程序发布页并添加为附件</div>
    </template>
  </div>
</template>

<script setup>
// 【F3a 2026-11-19 spec v2 GAP-B】法布施手机文件 H5 上传页:
// 与 /mp-file-picker 先例的差异(有意为之): ①多文件队列 ②accept 白名单+分型大小预检
// ③鉴权从「URL 带主 JWT」改为短时上传票(先例模式=MP-TOKEN-001 待修弱点, 不复制)。
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElIcon } from 'element-plus'
import { Lock, UploadFilled, Warning } from '@element-plus/icons-vue'

const route = useRoute()
const ticket = ref('')
const items = ref([]) // { name, file, cat, state: waiting|uploading|done|error, progress, fileId, error }
const isWechat = /MicroMessenger/i.test(navigator.userAgent)
const uploading = ref(false)

const MB = 1024 * 1024
const CATS = [
  { cat: 'image', exts: ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'], max: 10 * MB, label: '图片' },
  { cat: 'audio', exts: ['mp3', 'm4a', 'wav', 'aac'], max: 20 * MB, label: '语音' },
  { cat: 'video', exts: ['mp4', 'mov', 'avi'], max: 100 * MB, label: '视频' },
  { cat: 'pdf', exts: ['pdf'], max: 100 * MB, label: 'PDF' }
]

const anyOk = computed(() => items.value.some((it) => it.state === 'done'))
const allDone = computed(() => items.value.length > 0 && items.value.every((it) => it.state === 'done' || it.state === 'error'))

const wxReady = ref(false)
onMounted(() => {
  ticket.value = String(route.query.ticket || '')
  // web-view 内跳回小程序需微信 JSSDK(动态加载 + nginx CSP 定向放行 res.wx.qq.com, 同先例)
  const s = document.createElement('script')
  s.src = 'https://res.wx.qq.com/open/js/jweixin-1.3.2.js'
  s.onload = () => { wxReady.value = true }
  s.onerror = () => { wxReady.value = false; console.warn('JSSDK 加载失败(CSP/网络)——请用左上角返回') }
  document.head.appendChild(s)
})

function catOf(name) {
  const ext = String(name || '').split('.').pop().toLowerCase()
  return CATS.find((c) => c.exts.includes(ext)) || null
}

function pickFiles() {
  const input = document.createElement('input')
  input.type = 'file'
  input.multiple = true
  input.accept = 'image/*,video/*,audio/*,.pdf'
  input.onchange = () => {
    const files = Array.from(input.files || [])
    files.forEach((file) => {
      const c = catOf(file.name)
      if (!c) {
        items.value.push({ name: file.name, file, cat: '', state: 'error', progress: 0, error: '不支持的类型' })
        return
      }
      if (file.size > c.max) {
        items.value.push({ name: file.name, file, cat: c.cat, state: 'error', progress: 0, error: c.label + '不能超过' + (c.max / MB) + 'MB' })
        return
      }
      items.value.push({ name: file.name, file, cat: c.cat, state: 'waiting', progress: 0, fileId: null, error: '' })
    })
    drain()
  }
  input.click()
}

// 队列串行上传(避免大文件并发挤占移动端带宽)
function drain() {
  const next = items.value.find((it) => it.state === 'waiting')
  if (!next) { uploading.value = false; return }
  uploading.value = true
  uploadOne(next, () => drain())
}

function uploadOne(it, done) {
  it.state = 'uploading'
  it.progress = 0
  const form = new FormData()
  form.append('file', it.file, it.name)
  const xhr = new XMLHttpRequest()
  xhr.open('POST', '/api/fabushi/upload/h5?ticket=' + encodeURIComponent(ticket.value))
  xhr.upload.onprogress = (e) => {
    if (e.lengthComputable) it.progress = Math.min(99, Math.round((e.loaded / e.total) * 100))
  }
  xhr.onload = () => {
    try {
      const j = JSON.parse(xhr.responseText)
      if (xhr.status === 200 && j.code === 200 && j.data && j.data.id) {
        it.progress = 100
        it.state = 'done'
        it.fileId = j.data.id
      } else {
        it.state = 'error'
        it.error = (j && j.message) || ('上传失败(' + xhr.status + ')')
      }
    } catch (_) {
      it.state = 'error'
      it.error = '上传失败(' + xhr.status + ')'
    }
    done()
  }
  xhr.onerror = () => { it.state = 'error'; it.error = '网络异常'; done() }
  xhr.send(form)
}

function retry(i) {
  const it = items.value[i]
  if (!it || it.state !== 'error' || !it.file) return
  if (!catOf(it.name)) return
  it.state = 'waiting'
  drain()
}

function finish() {
  const files = items.value
    .filter((it) => it.state === 'done')
    .map((it) => ({ fileId: it.fileId, fileName: it.name, fileType: it.cat }))
  const wxApi = /** @type {any} */ (window).wx
  if (wxReady.value && wxApi && wxApi.miniProgram) {
    if (files.length) wxApi.miniProgram.postMessage({ data: { files } })
    wxApi.miniProgram.navigateBack()
  }
  // JSSDK 未就绪兜底: 提示用左上角返回(消息已 post, 返回时仍会送达)
}
</script>

<style scoped>
/* 【2026-11-26 按小程序原型 v7】配色取自原型：浅灰蓝画布、白卡片、蓝色主色；图标用组件，不用表情符号 */
.mfbu {
  --mu-ink: #172033; --mu-ink2: #52627a; --mu-ink3: #7c8ba1; --mu-ink4: #94a3b8; --mu-line: #dbe4f0; --mu-line-soft: #eef2f7;
  --mu-accent: #2563eb; --mu-primary: #1d4ed8; --mu-ok: #179050; --mu-ok-bg: #e8f7ef; --mu-danger: #e11d48; --mu-danger-bg: #ffe9ee;
  min-height: 100vh; background: #f5f7fb; font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', sans-serif; color: var(--mu-ink);
}
.mfbu-block { padding-top: 34vh; text-align: center; }
.mfbu-icon { margin-bottom: 12px; color: var(--mu-ink4); font-size: 40px; }
.mfbu-head { padding: 22px 20px 12px; text-align: center; }
.mfbu-title { color: var(--mu-ink); font-size: 17px; font-weight: 600; }
.mfbu-desc { margin-top: 6px; color: var(--mu-ink3); font-size: 13px; line-height: 1.5; }
.mfbu-body { padding: 16px; }
.mfbu-pick { padding: 44px 16px; border: 1px dashed var(--mu-line); border-radius: 14px; background: #fff; text-align: center; }
.mfbu-pick.small { padding: 14px; color: var(--mu-primary); font-size: 14px; }
.mfbu-pick-icon { color: var(--mu-accent); font-size: 40px; }
.mfbu-pick-text { margin-top: 10px; color: var(--mu-ink); font-size: 16px; font-weight: 500; }
.mfbu-pick-sub { margin-top: 6px; color: var(--mu-ink3); font-size: 12px; }
.mfbu-item { margin-bottom: 10px; padding: 14px 16px; border: 1px solid var(--mu-line-soft); border-radius: 12px; background: #fff; box-shadow: 0 6px 16px rgba(35, 57, 96, .07); }
.mfbu-item-name { color: var(--mu-ink); font-size: 14px; word-break: break-all; }
.mfbu-item-sub { margin-top: 4px; color: var(--mu-ink3); font-size: 12px; }
.mfbu-ok { color: var(--mu-ok); }
.mfbu-err { color: var(--mu-danger); }
.mfbu-bar { height: 6px; margin-top: 8px; overflow: hidden; border-radius: 3px; background: var(--mu-line-soft); }
.mfbu-bar-in { height: 100%; border-radius: 3px; background: var(--mu-accent); transition: width .2s; }
.mfbu-retry { width: 100%; height: 36px; margin-top: 8px; border: 0; border-radius: 8px; background: var(--mu-danger-bg); color: var(--mu-danger); font-size: 13px; font-family: inherit; }
.mfbu-done { width: 100%; height: 46px; margin-top: 6px; border: 0; border-radius: 12px; background: var(--mu-primary); color: #fff; font-size: 15px; font-weight: 600; font-family: inherit; }
.mfbu-done:disabled { opacity: .5; }
.mfbu-foot { padding: 10px 0 24px; color: var(--mu-ink3); font-size: 12px; text-align: center; }
</style>
