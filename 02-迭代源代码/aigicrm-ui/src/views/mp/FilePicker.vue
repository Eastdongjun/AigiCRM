<template>
  <div class="mpfp">
    <!-- 仅微信小程序 web-view 内可用: token 由小程序 URL 带入, 不依赖 PC 登录态 -->
    <div v-if="!isWechat" class="mpfp-block">
      <div class="mpfp-icon">🔒</div>
      <div class="mpfp-title">请从微信小程序进入</div>
      <div class="mpfp-desc">本页面仅在小程序「文件页 → 上传 → 手机文件」中使用</div>
    </div>

    <div v-else-if="!token" class="mpfp-block">
      <div class="mpfp-icon">⚠️</div>
      <div class="mpfp-title">登录状态缺失</div>
      <div class="mpfp-desc">请返回小程序重新进入</div>
    </div>

    <template v-else>
      <div class="mpfp-head">
        <div class="mpfp-title">上传手机文件</div>
        <div class="mpfp-desc">选择手机本地文件上传到文件中心{{ folderLabel }}</div>
      </div>

      <div class="mpfp-body">
        <div v-if="state === 'idle'" class="mpfp-pick" @click="pickFile">
          <div class="mpfp-pick-icon">📄</div>
          <div class="mpfp-pick-text">点击选择手机文件</div>
          <div class="mpfp-pick-sub">支持 PDF / Word / Excel / 图片 / 压缩包等</div>
        </div>

        <div v-else-if="state === 'uploading'" class="mpfp-card">
          <div class="mpfp-file">{{ fileName }}</div>
          <div class="mpfp-progress"><div class="mpfp-progress-bar" :style="{ width: progress + '%' }"></div></div>
          <div class="mpfp-pct">{{ progress }}%</div>
        </div>

        <div v-else-if="state === 'done'" class="mpfp-card mpfp-ok">
          <div class="mpfp-file">✓ {{ fileName }}</div>
          <div class="mpfp-pct">上传成功，正在返回…</div>
        </div>

        <div v-else-if="state === 'error'" class="mpfp-card mpfp-err">
          <div class="mpfp-file">上传失败</div>
          <div class="mpfp-pct">{{ errorMsg }}</div>
          <button class="mpfp-btn" @click="pickFile">重新选择文件</button>
        </div>
      </div>

      <div class="mpfp-foot">上传后自动返回小程序文件页</div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const token = ref('')
const folderId = ref('')
const scope = ref('private')
const state = ref('idle') // idle | uploading | done | error
const progress = ref(0)
const fileName = ref('')
const errorMsg = ref('')
const isWechat = /MicroMessenger/i.test(navigator.userAgent)

const folderLabel = computed(() => (folderId.value ? '（当前文件夹）' : ''))

// web-view 内跳回小程序需微信 JSSDK; 动态加载仅本页使用
const wxReady = ref(false)
onMounted(() => {
  token.value = String(route.query.token || '')
  folderId.value = String(route.query.folderId || '')
  scope.value = String(route.query.scope || 'private')
  const s = document.createElement('script')
  s.src = 'https://res.wx.qq.com/open/js/jweixin-1.3.2.js'
  s.onload = () => { wxReady.value = true }
  s.onerror = () => { wxReady.value = false }
  document.head.appendChild(s)
})

function goBack() {
  setTimeout(() => {
    if (wxReady.value && window.wx && window.wx.miniProgram) {
      window.wx.miniProgram.navigateBack()
    } else {
      state.value = 'idle' // JSSDK 未就绪兜底: 手动再传或左滑返回
    }
  }, 1200)
}

function pickFile() {
  const input = document.createElement('input')
  input.type = 'file'
  input.onchange = () => {
    const file = input.files && input.files[0]
    if (file) upload(file)
  }
  input.click()
}

function upload(file) {
  state.value = 'uploading'
  progress.value = 0
  fileName.value = file.name
  const form = new FormData()
  form.append('file', file)
  form.append('scope', scope.value || 'private')
  if (folderId.value) form.append('folderId', folderId.value)
  const xhr = new XMLHttpRequest()
  xhr.open('POST', '/api/file/upload')
  xhr.setRequestHeader('Authorization', 'Bearer ' + token.value)
  xhr.upload.onprogress = (e) => {
    if (e.lengthComputable) progress.value = Math.min(99, Math.round((e.loaded / e.total) * 100))
  }
  xhr.onload = () => {
    let ok = xhr.status === 200
    if (ok) {
      try { ok = (JSON.parse(xhr.responseText).code || 0) === 200 } catch (_) { ok = false }
    }
    if (ok) { progress.value = 100; state.value = 'done'; goBack() }
    else {
      let msg = '上传失败(' + xhr.status + ')'
      try { const j = JSON.parse(xhr.responseText); if (j.message) msg = j.message } catch (_) {}
      errorMsg.value = msg
      state.value = 'error'
    }
  }
  xhr.onerror = () => { errorMsg.value = '网络异常，请重试'; state.value = 'error' }
  xhr.send(form)
}
</script>

<style scoped>
.mpfp { min-height: 100vh; background: #f5f7fa; font-family: -apple-system, 'PingFang SC', sans-serif; }
.mpfp-block, .mpfp-head { padding: 28px 20px 12px; text-align: center; }
.mpfp-block { padding-top: 40vh; }
.mpfp-icon { font-size: 44px; margin-bottom: 12px; }
.mpfp-title { font-size: 17px; font-weight: 600; color: #1f2d3d; }
.mpfp-desc { font-size: 13px; color: #8a97a6; margin-top: 6px; line-height: 1.5; }
.mpfp-body { padding: 16px; }
.mpfp-pick { background: #fff; border: 1px dashed #b8c4d0; border-radius: 14px; padding: 44px 16px; text-align: center; }
.mpfp-pick-icon { font-size: 40px; }
.mpfp-pick-text { font-size: 16px; color: #1f2d3d; font-weight: 500; margin-top: 10px; }
.mpfp-pick-sub { font-size: 12px; color: #8a97a6; margin-top: 6px; }
.mpfp-card { background: #fff; border-radius: 12px; padding: 18px 16px; text-align: center; }
.mpfp-file { font-size: 15px; color: #1f2d3d; word-break: break-all; }
.mpfp-progress { height: 8px; background: #e8edf3; border-radius: 4px; margin: 14px 0 8px; overflow: hidden; }
.mpfp-progress-bar { height: 100%; background: #3b6ff2; border-radius: 4px; transition: width .2s; }
.mpfp-pct { font-size: 13px; color: #8a97a6; }
.mpfp-ok .mpfp-file { color: #2da641; }
.mpfp-err .mpfp-file { color: #d9453d; }
.mpfp-btn { margin-top: 14px; width: 100%; height: 42px; border: none; border-radius: 10px; background: #3b6ff2; color: #fff; font-size: 15px; }
.mpfp-foot { text-align: center; font-size: 12px; color: #aab4c0; padding: 10px 0 24px; }
</style>
