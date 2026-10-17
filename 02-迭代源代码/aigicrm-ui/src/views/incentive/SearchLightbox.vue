<template>
  <!-- 图片/视频灯箱预览：深色底 + 顶部信息条 + 左右切换 + 缩略图条 + 键盘方向键（D9 2026-10-04 自 search.vue 拆出，行为不变） -->
  <el-dialog v-model="visible" width="1000px" top="3vh" :close-on-click-modal="true" destroy-on-close append-to-body class="preview-dlg">
    <template #header></template>
    <div class="pv-wrap" v-if="current">
      <div class="pv-bar">
        <span class="pv-name">{{ name || '文件预览' }}</span>
        <span v-if="current.tag" class="pv-tag">{{ current.tag }}</span>
        <span v-if="count > 1" class="pv-count">{{ index + 1 }} / {{ count }}</span>
        <span class="pv-spacer"></span>
        <el-button size="small" @click="downloadCurrent"><el-icon><Download /></el-icon> 下载</el-button>
        <el-button size="small" circle @click="visible=false"><el-icon><Close /></el-icon></el-button>
      </div>
      <div class="pv-stage">
        <button v-if="count > 1" class="pv-nav prev" @click="step(-1)"><el-icon><ArrowLeft /></el-icon></button>
        <video v-if="type==='video'" :src="url" controls autoplay class="pv-media" />
        <!-- 【图片秒开 2026-10-17】先秒开缩略图, 原图后台预载到货自动换上(displaySrc) -->
        <img v-else-if="type==='image' && !error" :src="displaySrc" class="pv-media" @error="onImageError" />
        <!-- 文档类统一走 components/preview 渲染器（与 FilePreview 同源，2026-10-16 文件阅读器） -->
        <div v-else-if="type==='pdf'" class="pv-doc-panel"><PdfFrame :url="url" :file-name="name" /></div>
        <div v-else-if="type==='docx'" class="pv-doc-panel"><DocxView :url="url" :file-name="name" /></div>
        <div v-else-if="type==='sheet'" class="pv-doc-panel"><SheetView :url="url" :file-name="name" /></div>
        <div v-else-if="type==='text'" class="pv-doc-panel"><TextView :url="url" :file-name="name" /></div>
        <div v-else-if="type==='audio'" class="pv-missing">
          <el-icon :size="34"><Headset /></el-icon>
          <div class="pv-missing-title">{{ name || '音频' }}</div>
          <audio :src="url" controls class="pv-audio" />
        </div>
        <div v-else-if="type==='unsupported'" class="pv-missing">
          <el-icon :size="34"><Document /></el-icon>
          <div class="pv-missing-title">{{ name || '该格式暂不支持在线预览' }}</div>
          <div class="pv-missing-hint">{{ unsupportedHintOf(name) }}</div>
          <el-button size="small" type="primary" plain @click="downloadCurrent"><el-icon><Download /></el-icon> 下载文件</el-button>
        </div>
        <div v-else class="pv-missing">
          <el-icon :size="34"><WarningFilled /></el-icon>
          <div class="pv-missing-title">该文件当前不可用</div>
          <div class="pv-missing-hint">文件可能已被清理，或本环境的存储中还没有这个文件</div>
          <el-button size="small" type="primary" plain @click="downloadCurrent">仍要下载试试</el-button>
        </div>
        <button v-if="count > 1" class="pv-nav next" @click="step(1)"><el-icon><ArrowRight /></el-icon></button>
      </div>
      <div v-if="count > 1" class="pv-thumbs">
        <div v-for="(f,i) in list" :key="f.id" class="pv-thumb" :class="{active:i===index}" @click="index=i; error=false">
          <img v-if="f.type==='image' && !thumbFailed.has(f.id)" :src="thumbUrl(f.id)" @error="onThumbError(f.id)" />
          <span v-else-if="f.type==='image'" class="pv-thumb-na">不可用</span>
          <div v-else-if="f.type==='video'" class="pv-thumb-vid"><el-icon :size="18"><VideoPlay /></el-icon></div>
          <div v-else class="pv-thumb-doc"><el-icon :size="16"><Document /></el-icon></div>
          <span v-if="f.tag" class="pv-thumb-tag">{{ f.tag }}</span>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
// 灯箱内部自持：可见态/文件列表/当前序号/文档预览载荷/键盘导航；
// 缩略图失败标记（thumbFailed）是页面级共享状态（网格卡片、详情附件、灯箱同源），由 props 传入、失败经 emit 上抛。
import { ref, computed, watch, onMounted, onBeforeUnmount, defineAsyncComponent } from 'vue'
import { Download, Close, ArrowLeft, ArrowRight, Document, VideoPlay, WarningFilled, Headset } from '@element-plus/icons-vue'
import { refreshDownloadToken } from '@/utils/token'
import { previewKindOf, unsupportedHintOf } from '@/utils/media-type'
import { fileUrl, thumbUrl, downloadFile } from './search-helpers'

// 文档渲染器与 FilePreview 共用，按需懒加载（docx-preview/SheetJS 不进首屏包）
const PdfFrame = defineAsyncComponent(() => import('@/components/preview/PdfFrame.vue'))
const DocxView = defineAsyncComponent(() => import('@/components/preview/DocxView.vue'))
const SheetView = defineAsyncComponent(() => import('@/components/preview/SheetView.vue'))
const TextView = defineAsyncComponent(() => import('@/components/preview/TextView.vue'))

const props = defineProps({
  // 页面级共享的"缩略图加载失败"文件 id 集合
  thumbFailed: { type: Set, default: () => new Set() },
})
const emit = defineEmits(['thumb-failed'])

const visible = ref(false), list = ref([]), index = ref(0), error = ref(false)
const current = computed(() => list.value[index.value] || null)
const count = computed(() => list.value.length)
const url = computed(() => current.value ? fileUrl(current.value.id) : '')
const name = computed(() => current.value?.label || '')
// 文件种类: 图片/视频按记录标注; 其余按文件名经 previewKindOf 统一判定(pdf/docx/sheet/text/audio/unsupported)
// 【2026-10-16 文件阅读器】此前灯箱自持一份 pdf/text/other 扩展名表与 blob 加载逻辑，与 FilePreview 口径分叉；
// 现全部收口到 utils/media-type + components/preview，灯箱只负责画廊导航。
function kindOf(f) {
  if (f?.type === 'video') return 'video'
  if (f?.type === 'image') return 'image'
  return previewKindOf(f?.label || '')
}
const type = computed(() => current.value ? kindOf(current.value) : 'image')
watch(index, () => { error.value = false })

// 【图片秒开 2026-10-17】主图渐进加载: 先显示 ≤300px 缩略(亚秒), 原图后台 Image() 预载,
// 到货即无缝替换成原图(生产数据有 18MB 的 PNG, 直载原图实测 14.8s)。切图时校验 src 未变才生效。
const fullLoadedUrl = ref('')
const displaySrc = computed(() => {
  if (type.value !== 'image') return url.value
  if (fullLoadedUrl.value) return fullLoadedUrl.value
  return current.value ? thumbUrl(current.value.id) : url.value
})
watch(url, (u) => {
  fullLoadedUrl.value = ''
  if (type.value !== 'image' || !u) return
  const im = new Image()
  im.onload = () => { if (im.src === url.value) fullLoadedUrl.value = u }
  im.src = u
}, { immediate: true })

// 打开灯箱：gallery 传入该记录的可预览文件（图片/视频）列表，可左右切换；不传则只看单个文件
async function open(id, fileType, label, gallery) {
  await refreshDownloadToken()
  const norm = f => ({ id: Number(f.id), type: f.type === 'video' ? 'video' : f.type === 'image' ? 'image' : 'file', label: f.label || '', tag: f.tag || '' })
  const clicked = norm({ id, type: fileType, label })
  let l = (Array.isArray(gallery) ? gallery : []).filter(f => f && f.id != null).map(norm)
  let idx = l.findIndex(f => f.id === clicked.id)
  if (idx < 0) { l = [clicked]; idx = 0 }
  list.value = l; index.value = idx; error.value = false; visible.value = true
}

// 灯箱左右切换（循环）
function step(delta) {
  const n = list.value.length
  if (n < 2) return
  index.value = (index.value + delta + n) % n
  error.value = false
}

// 灯箱主图加载失败：不再只是替换成占位图，而是给出可读的失败原因 + 下载兜底
function onImageError() { error.value = true }
// 缩略图失败：上抛给页面标记该文件不可用（用清晰文字块替代 7px 灰字的占位图）
function onThumbError(id) { emit('thumb-failed', id) }

function downloadCurrent() { const f = current.value; if (f) downloadFile(f.id, f.label) }

// 键盘左右方向键切换（仅灯箱可见时生效）
function onKeydown(e) {
  if (!visible.value) return
  if (e.key === 'ArrowRight') { step(1) } else if (e.key === 'ArrowLeft') { step(-1) }
}
onMounted(() => { window.addEventListener('keydown', onKeydown) })
onBeforeUnmount(() => { window.removeEventListener('keydown', onKeydown) })

// 供页面（search.vue）/测试访问的实例 API
defineExpose({ open, step, count, index, name, type })
</script>

<style scoped>
/* ===== 图片/视频灯箱预览（2026-09-13） ===== */
.preview-dlg :deep(.el-dialog){background:transparent;box-shadow:none;border-radius: var(--radius-lg);overflow:hidden}
.preview-dlg :deep(.el-dialog__header){display:none}
.preview-dlg :deep(.el-dialog__body){padding:0}
.pv-wrap{background:#0b1220;border-radius: var(--radius-lg);overflow:hidden;box-shadow:0 24px 60px rgba(0,0,0,.5)}
.pv-bar{display:flex;align-items:center;gap:10px;padding:12px 16px;background:rgba(255,255,255,.06);
  border-bottom:1px solid rgba(255,255,255,.08);color:#e5eaf3}
.pv-name{font-size:14px;font-weight:600;max-width:46%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.pv-tag{font-size:12px;color:#9ec5ff;background:color-mix(in srgb, var(--el-color-primary) 18%, transparent);border:1px solid color-mix(in srgb, var(--el-color-primary) 36%, transparent);
  border-radius: var(--radius-pill);padding:2px 10px;white-space:nowrap}
.pv-count{font-size:12px;color:#8b97a8;font-variant-numeric:tabular-nums}
.pv-spacer{flex:1}
.pv-bar :deep(.el-button){background:rgba(255,255,255,.10);border-color:rgba(255,255,255,.16);color:#e5eaf3}
.pv-bar :deep(.el-button:hover){background:color-mix(in srgb, var(--el-color-primary) 28%, transparent);border-color:color-mix(in srgb, var(--el-color-primary) 45%, transparent);color:#fff}
.pv-stage{position:relative;display:flex;align-items:center;justify-content:center;min-height:420px;padding:16px}
.pv-media{max-width:100%;max-height:74vh;border-radius: var(--radius-md);background:#05070d;display:block}
.pv-nav{position:absolute;top:50%;transform:translateY(-50%);width:44px;height:44px;border-radius:50%;border:none;
  background:rgba(255,255,255,.12);color:#fff;font-size:20px;display:flex;align-items:center;justify-content:center;
  cursor:pointer;transition:all .18s;backdrop-filter:blur(6px)}
.pv-nav:hover{background:color-mix(in srgb, var(--el-color-primary) 85%, transparent);transform:translateY(-50%) scale(1.06)}
.pv-nav.prev{left:18px}
.pv-nav.next{right:18px}
.pv-thumbs{display:flex;gap:8px;padding:10px 16px 14px;overflow-x:auto;border-top:1px solid rgba(255,255,255,.08)}
.pv-thumb{position:relative;width:76px;height:56px;flex-shrink:0;border-radius:8px;overflow:hidden;cursor:pointer;
  border:2px solid transparent;background:rgba(255,255,255,.06);transition:all .15s}
.pv-thumb:hover{border-color:color-mix(in srgb, var(--el-color-primary) 6%, transparent)}
.pv-thumb.active{border-color:var(--el-color-primary);box-shadow:0 0 0 2px color-mix(in srgb, var(--el-color-primary) 28%, transparent)}
.pv-thumb img{width:100%;height:100%;object-fit:cover;display:block}
.pv-thumb-vid{width:100%;height:100%;display:flex;align-items:center;justify-content:center;color:#cfe2ff}
.pv-thumb-na{width:100%;height:100%;display:flex;align-items:center;justify-content:center;font-size:12px;color:#aab6c8;background:rgba(255,255,255,.06)}
.pv-doc-panel{width:92%;height:74vh;border-radius: var(--radius-md);background:#fff;overflow:hidden;text-align:left;display:flex}
.pv-doc-panel > *{flex:1;min-width:0}
.pv-audio{width:min(520px,90%);margin-top:6px}
.pv-thumb-doc{width:100%;height:100%;display:flex;align-items:center;justify-content:center;color:#cfe2ff;background:rgba(255,255,255,.06)}
.pv-missing{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;color:#c9d4e3;padding:60px 20px;text-align:center}
.pv-missing-title{font-size:15px;font-weight:600;color:#e5eaf3}
.pv-missing-hint{font-size:12px;color:#8b97a8;max-width:420px;line-height:1.7}
.pv-thumb-tag{position:absolute;left:2px;bottom:2px;font-size:12px;color:#fff;background:rgba(0,0,0,.5);
  border-radius:4px;padding:0 4px;line-height:14px}
</style>
