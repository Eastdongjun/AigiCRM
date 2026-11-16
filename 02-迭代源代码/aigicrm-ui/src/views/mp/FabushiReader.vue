<template>
  <div class="mfbr">
    <!-- 仅微信小程序 web-view 内可用: token 由小程序 URL 带入, 不依赖 PC 登录态 -->
    <div v-if="!isWechat" class="mfbr-block">
      <div class="mfbr-icon">🔒</div>
      <div class="mfbr-title">请从微信小程序进入</div>
      <div class="mfbr-desc">本页面在小程序「书目详情 → 在线阅读」中使用</div>
    </div>

    <div v-else-if="!token || !pdfFileId" class="mfbr-block">
      <div class="mfbr-icon">⚠️</div>
      <div class="mfbr-title">参数缺失</div>
      <div class="mfbr-desc">请返回小程序重新进入</div>
    </div>

    <template v-else>
      <div class="mfbr-head">
        <button class="mfbr-back" @click="goBack">‹ 返回</button>
        <div class="mfbr-title-row">
          <b class="mfbr-book">{{ title }}</b>
          <span class="mfbr-page" v-if="totalPages">第 {{ currentPage }} / {{ totalPages }} 页</span>
        </div>
      </div>

      <div class="mfbr-body">
        <div v-if="state === 'loading'" class="mfbr-block">
          <div class="mfbr-loading">正在加载电子书…</div>
          <div class="mfbr-pct">{{ loadPct ? loadPct + '%' : '' }}</div>
        </div>
        <div v-else-if="state === 'error'" class="mfbr-block">
          <div class="mfbr-icon">📖</div>
          <div class="mfbr-title">加载失败</div>
          <div class="mfbr-desc">{{ errorMsg }}</div>
          <button class="mfbr-retry" @click="retry">重试</button>
        </div>
        <template v-else>
          <div class="mfbr-stage" @touchstart="onTouchStart" @touchend="onTouchEnd">
            <canvas ref="canvasEl" class="mfbr-canvas"></canvas>
          </div>
          <div class="mfbr-nav">
            <button class="mfbr-nav-btn" :disabled="currentPage <= 1" @click="goPage(currentPage - 1)">上一页</button>
            <span class="mfbr-nav-page">{{ currentPage }} / {{ totalPages }}</span>
            <button class="mfbr-nav-btn" :disabled="currentPage >= totalPages" @click="goPage(currentPage + 1)">下一页</button>
          </div>
          <div class="mfbr-foot">翻页自动保存进度，返回后可从本次页码继续</div>
        </template>
      </div>
    </template>
  </div>
</template>

<script setup>
// 【2026-11-16 P3】渲染/进度逻辑收敛到 useFabushiPdfReader（PC /fabushi/read 同源），
// 本页只保留微信 web-view 壳：URL 取 token/参数、JSSDK 返回、触摸翻页。
// P2 的页码级行为不变：翻页 debounce 自动回传 /api/fabushi/progress/{bookId}。
// 鉴权与 /mp-file-picker 先例同构: token 由小程序 URL 带入, API 直传 Authorization header。
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useFabushiPdfReader } from '@/composables/useFabushiPdfReader'

const route = useRoute()
const token = ref('')
const bookId = ref('')
const pdfFileId = ref('')
const title = ref('')
const isWechat = /MicroMessenger/i.test(navigator.userAgent)

const canvasEl = ref(null)
const wxReady = ref(false)

const { state, loadPct, errorMsg, currentPage, totalPages, load, goPage, retry } = useFabushiPdfReader({
  bookId,
  pdfFileId,
  token,
  canvasMaxWidth: 800
})

let touchX = 0

// web-view 内跳回小程序需微信 JSSDK(动态加载 + CSP 定向放行, 同 FilePicker.vue 方案)
onMounted(() => {
  token.value = String(route.query.token || '')
  bookId.value = String(route.query.bookId || '')
  pdfFileId.value = String(route.query.pdfFileId || '')
  title.value = decodeURIComponent(String(route.query.title || '在线阅读'))
  const s = document.createElement('script')
  s.src = 'https://res.wx.qq.com/open/js/jweixin-1.3.2.js'
  s.onload = () => { wxReady.value = true }
  s.onerror = () => { console.warn('JSSDK 加载失败(CSP/网络)——请用左上角返回') }
  document.head.appendChild(s)
  if (token.value && pdfFileId.value) load(() => canvasEl.value)
})

function goBack() {
  setTimeout(() => {
    // 微信 JSSDK 动态注入的全局(仅 web-view 环境存在), JSDoc cast 绕开 Window 类型无 wx 声明
    const wxApi = /** @type {any} */ (window).wx
    if (wxReady.value && wxApi && wxApi.miniProgram) {
      wxApi.miniProgram.navigateBack()
    }
    // JSSDK 未就绪兜底: 小程序自带左上角返回键可用
  }, 200)
}

function onTouchStart(e) {
  touchX = (e.touches && e.touches[0] && e.touches[0].clientX) || 0
}
function onTouchEnd(e) {
  const endX = (e.changedTouches && e.changedTouches[0] && e.changedTouches[0].clientX) || 0
  const dx = endX - touchX
  if (Math.abs(dx) < 48) return // 过短滑动视为点击, 不翻页
  if (dx < 0 && currentPage.value < totalPages.value) goPage(currentPage.value + 1)
  if (dx > 0 && currentPage.value > 1) goPage(currentPage.value - 1)
}
</script>

<style scoped>
/* 【书卷改版 2026-11-16】纸色底/墨绿导航/朱砂强调, 与 MP 书卷语言同源 */
.mfbr { min-height: 100vh; background: #f1ebdf; font-family: -apple-system, 'PingFang SC', sans-serif; display: flex; flex-direction: column; }
.mfbr-block { padding-top: 38vh; text-align: center; }
.mfbr-icon { font-size: 44px; margin-bottom: 12px; }
.mfbr-title { font-size: 17px; font-weight: 600; color: #2e2a24; }
.mfbr-desc { font-size: 13px; color: #9c9184; margin-top: 6px; line-height: 1.5; padding: 0 24px; }
.mfbr-loading { font-size: 15px; color: #6b6257; }
.mfbr-pct { font-size: 13px; color: #9c9184; margin-top: 8px; }
.mfbr-retry { margin: 16px auto 0; padding: 0 28px; height: 40px; border: none; border-radius: 999px; background: #b5543a; color: #fff7ee; font-size: 14px; }
.mfbr-head { display: flex; align-items: center; gap: 8px; padding: 10px 12px; background: #fffdf8; border-bottom: 1px solid #e8e0d3; }
.mfbr-back { border: 1px solid #d8cdbb; background: transparent; border-radius: 999px; font-size: 13px; color: #2d5a4a; padding: 5px 12px; }
.mfbr-title-row { display: flex; flex-direction: column; min-width: 0; }
.mfbr-book { font-family: 'Songti SC', 'STSong', serif; font-size: 16px; font-weight: 700; color: #2e2a24; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mfbr-page { font-size: 12px; color: #a67c52; margin-top: 2px; }
.mfbr-body { flex: 1; display: flex; flex-direction: column; min-height: 0; }
.mfbr-stage { flex: 1; display: flex; justify-content: center; align-items: flex-start; overflow: auto; padding: 10px 8px; }
.mfbr-canvas { background: #fffdf8; box-shadow: 0 2px 10px rgba(90, 74, 51, .18); border-radius: 4px; }
.mfbr-nav { display: flex; align-items: center; justify-content: space-between; padding: 8px 16px; background: #fffdf8; border-top: 1px solid #e8e0d3; }
.mfbr-nav-btn { border: 1px solid #c8d8cc; background: #eef3ee; border-radius: 999px; padding: 8px 18px; font-size: 14px; color: #2d5a4a; font-weight: 600; }
.mfbr-nav-btn:disabled { opacity: .4; }
.mfbr-nav-page { font-size: 13px; color: #a67c52; font-family: 'Songti SC', 'STSong', serif; font-weight: 600; }
.mfbr-foot { text-align: center; font-size: 12px; color: #9c9184; padding: 8px 0 12px; background: #fffdf8; }
</style>
