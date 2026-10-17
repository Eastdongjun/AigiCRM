<template>
  <!-- Excel/CSV：SheetJS 在客户端解析工作簿 → 每个 sheet 一个页签 → 生成 HTML 表格 →
       放进 sandbox="" 的 iframe（无脚本、无同源权限）展示。表格 HTML 全部由单元格文本转义生成，
       sandbox 是纵深防御，不是唯一防线。超大表只渲染前 MAX_ROWS 行并提示下载看全量。 -->
  <div class="pv-sheet">
    <PreviewStatus :loading="loading || parsing" :error="error || parseError" :too-large="tooLarge" loading-text="正在解析表格…" />
    <template v-if="sheets.length">
      <div class="pv-sheet-tabs">
        <button
          v-for="(s, i) in sheets"
          :key="s"
          type="button"
          class="pv-sheet-tab"
          :class="{ active: i === active }"
          @click="active = i"
        >{{ s }}</button>
        <span v-if="truncated" class="pv-sheet-note">当前表超过 {{ maxRows }} 行，仅显示前 {{ maxRows }} 行；完整内容请下载查看</span>
      </div>
      <iframe v-if="html" :srcdoc="html" sandbox="" class="pv-sheet-frame" title="表格预览"></iframe>
    </template>
  </div>
</template>

<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { useFileBlob } from '@/composables/useFileBlob'
import PreviewStatus from './PreviewStatus.vue'

const props = defineProps({
  url: { type: String, default: '' },
  fileName: { type: String, default: '' },
  sizeHint: { type: Number, default: 0 },
})

const MAX_ROWS = 3000
const MAX_COLS = 200
const maxRows = MAX_ROWS

const { loading, error, tooLarge, load, abort } = useFileBlob()
const sheets = ref([])
const active = ref(0)
const html = ref('')
const truncated = ref(false)
const parsing = ref(false)
const parseError = ref('')
let workbook = null
let xlsxLib = null
let renderSeq = 0

const FRAME_CSS = [
  'body{margin:0;padding:10px;background:#fff}',
  'table{border-collapse:collapse;font:12px/1.5 -apple-system,"PingFang SC","Microsoft YaHei",system-ui,sans-serif;color:#1f2937}',
  'td,th{border:1px solid #d9dee5;padding:3px 8px;white-space:nowrap;max-width:420px;overflow:hidden;text-overflow:ellipsis;vertical-align:top}',
  'tr:first-child td,tr:first-child th{background:#f3f5f8;font-weight:600;position:sticky;top:0;z-index:1}',
].join('')

function escapeHtml(v) {
  return String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c])
}

function wrapDoc(table) {
  return `<!doctype html><html><head><meta charset="utf-8"><style>${FRAME_CSS}</style></head><body>${table}</body></html>`
}

// CSV 常见 GBK 编码（Excel 另存为 CSV 默认）：先按 UTF-8 严格解码，失败再按 GBK
function decodeCsv(buf) {
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(buf)
  } catch {
    try { return new TextDecoder('gbk').decode(buf) } catch { return new TextDecoder('utf-8').decode(buf) }
  }
}

function renderSheet(index) {
  if (!workbook || !xlsxLib) return
  const name = workbook.SheetNames[index]
  const ws = workbook.Sheets[name]
  truncated.value = false
  if (!ws || !ws['!ref']) {
    html.value = wrapDoc('<p style="color:#909399">（空表）</p>')
    return
  }
  const range = xlsxLib.utils.decode_range(ws['!ref'])
  const rowCount = range.e.r - range.s.r + 1
  const colCount = range.e.c - range.s.c + 1
  if (rowCount <= MAX_ROWS && colCount <= MAX_COLS) {
    // 小表走库自带导出：保留合并单元格 colspan/rowspan，单元格文本由库转义
    html.value = wrapDoc(xlsxLib.utils.sheet_to_html(ws, { editable: false, header: '', footer: '' }))
    return
  }
  truncated.value = true
  const clipped = {
    s: { r: range.s.r, c: range.s.c },
    e: { r: Math.min(range.e.r, range.s.r + MAX_ROWS - 1), c: Math.min(range.e.c, range.s.c + MAX_COLS - 1) },
  }
  const rows = xlsxLib.utils.sheet_to_json(ws, { header: 1, range: clipped, defval: '', raw: false, blankrows: true })
  const body = rows.map((r) => '<tr>' + r.map((c) => `<td>${escapeHtml(c)}</td>`).join('') + '</tr>').join('')
  html.value = wrapDoc(`<table>${body}</table>`)
}

function clear() {
  sheets.value = []
  active.value = 0
  html.value = ''
  truncated.value = false
  parseError.value = ''
  workbook = null
}

async function render() {
  const seq = ++renderSeq
  clear()
  const buf = await load(props.url, { expect: 'arrayBuffer', sizeHint: props.sizeHint })
  if (!buf || seq !== renderSeq) return
  parsing.value = true
  try {
    xlsxLib = await import('xlsx')
    if (seq !== renderSeq) return
    const isCsv = /\.csv$/i.test(props.fileName || '')
    workbook = isCsv
      ? xlsxLib.read(decodeCsv(buf), { type: 'string', raw: true })
      : xlsxLib.read(buf, { type: 'array', cellDates: false })
    if (!workbook.SheetNames?.length) throw new Error('no sheets')
    sheets.value = workbook.SheetNames.slice()
    active.value = 0
    renderSheet(0)
  } catch (e) {
    if (seq === renderSeq) parseError.value = '表格解析失败：文件可能已加密、损坏或不是受支持的表格格式'
  } finally {
    if (seq === renderSeq) parsing.value = false
  }
}

watch(() => props.url, render, { immediate: true })
watch(active, (i) => renderSheet(i))
onBeforeUnmount(() => {
  renderSeq += 1
  abort()
  clear()
})
</script>

<style scoped>
.pv-sheet { width: 100%; height: 100%; min-height: 60vh; display: flex; flex-direction: column; }
.pv-sheet-tabs { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; padding: 0 0 8px; }
.pv-sheet-tab { border: 1px solid var(--el-border-color); background: #fff; color: var(--el-text-color-regular); border-radius: 6px; padding: 4px 12px; font-size: 12px; cursor: pointer; }
.pv-sheet-tab.active { border-color: var(--el-color-primary); color: var(--el-color-primary); background: var(--el-color-primary-light-9); font-weight: 600; }
.pv-sheet-note { margin-left: auto; font-size: 12px; color: var(--el-color-warning); }
.pv-sheet-frame { flex: 1; width: 100%; min-height: 56vh; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; background: #fff; }
</style>
