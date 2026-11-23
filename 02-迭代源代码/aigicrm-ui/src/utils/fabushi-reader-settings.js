// 【D19/D24】阅读器版式设置的纯函数单源（PC 使用；MP 阅读器沿用原样渲染）。
// 口径：背景/缩放/对比度/裁边/单连续页/百分比 + 【D24 2026-11-23】重排（文本层）/字号/行距。
// 重排与书内搜索只读 PDF 自带文本层，不做 OCR；影印件无文本层时由壳层如实提示不可用。
export const FABUSHI_LAYOUT_TYPES = [
  { value: 'text', label: '文本版', hint: '默认纸白 · 可重排（改字号行距）' },
  { value: 'scan', label: '影印版', hint: '默认米黄增强 · 原样渲染' }
]

export const READER_BACKDROPS = [
  { value: 'paper', label: '纸白', bg: '#fffdf8', stage: '#4a4238', text: '#2e2a24' },
  { value: 'sepia', label: '护眼米黄', bg: '#f4e7c8', stage: '#6b5b43', text: '#3d3527' },
  { value: 'night', label: '夜间', bg: '#202525', stage: '#141818', text: '#e6e2d8' }
]

export const READER_LIMITS = {
  zoom: [70, 160],
  contrast: [90, 160],
  crop: [0, 14],
  // 【D24】重排字号（px）与行距（倍）
  fontSize: [14, 30],
  lineHeight: [14, 26] // 存的是 1.4~2.6 的十分位整数，避免浮点记忆误差
}

export const DEFAULT_READER_SETTINGS = {
  backdrop: 'paper',
  zoom: 100,
  contrast: 100,
  crop: 0,
  continuous: false,
  // 【D24 2026-11-23】重排默认关（影印件本来就不能重排，默认开会让多数书一进来就是"不可用"）
  reflow: false,
  fontSize: 18,
  lineHeight: 19
}

export const layoutLabel = (value) =>
  (FABUSHI_LAYOUT_TYPES.find((item) => item.value === value) || FABUSHI_LAYOUT_TYPES[1]).label

export const backdropOf = (value) =>
  READER_BACKDROPS.find((item) => item.value === value) || READER_BACKDROPS[0]

const clamp = (value, min, max, fallback) => {
  const n = Number(value)
  if (!Number.isFinite(n)) return fallback
  return Math.min(max, Math.max(min, Math.round(n)))
}

/**
 * 按书目版式标记给保守默认值：文本版纸白原色，影印版米黄 + 轻度对比增强。
 * 【S4 2026-11-23】返回类型必须列全 D24 新增的 reflow/fontSize/lineHeight：
 * 此前 JSDoc 只写了 5 个字段，TS 会以 JSDoc 为准把返回值收窄成 5 键，
 * 导致 read.vue / FabushiReader.vue 里 settings.reflow/fontSize/lineHeight 全部报
 * 「Property does not exist」（实测 35 处连带错误）。运行时对象本来就是全的。
 * @returns {{backdrop: string, zoom: number, contrast: number, crop: number, continuous: boolean,
 *   reflow: boolean, fontSize: number, lineHeight: number}}
 */
export function resolveReaderDefaults(layoutType) {
  const text = layoutType === 'text'
  return {
    ...DEFAULT_READER_SETTINGS,
    backdrop: text ? 'paper' : 'sepia',
    contrast: text ? 100 : 112
  }
}

/**
 * 合并本地记忆与默认值；越界/脏值一律回落，不允许把 CSS filter 弄成非法值。
 * 【S4 2026-11-23】返回类型同 resolveReaderDefaults，必须含 D24 的 reflow/fontSize/lineHeight。
 * @returns {{backdrop: string, zoom: number, contrast: number, crop: number, continuous: boolean,
 *   reflow: boolean, fontSize: number, lineHeight: number}}
 */
export function normalizeReaderSettings(raw, layoutType) {
  const base = resolveReaderDefaults(layoutType)
  if (!raw || typeof raw !== 'object') return base
  const backdrop = READER_BACKDROPS.some((item) => item.value === raw.backdrop) ? raw.backdrop : base.backdrop
  return {
    backdrop,
    zoom: clamp(raw.zoom, ...READER_LIMITS.zoom, base.zoom),
    contrast: clamp(raw.contrast, ...READER_LIMITS.contrast, base.contrast),
    crop: clamp(raw.crop, ...READER_LIMITS.crop, base.crop),
    continuous: raw.continuous === true,
    reflow: raw.reflow === true,
    fontSize: clamp(raw.fontSize, ...READER_LIMITS.fontSize, base.fontSize),
    lineHeight: clamp(raw.lineHeight, ...READER_LIMITS.lineHeight, base.lineHeight)
  }
}

/** 行距十分位整数 → CSS 倍数（19 → 1.9）。 */
export const lineHeightRatio = (stored) => {
  const n = Number(stored)
  if (!Number.isFinite(n)) return 1.9
  return Math.min(2.6, Math.max(1.4, Math.round(n) / 10))
}

/** 页码 + 百分比同显（D24）；总页数缺失回 0，不显示误导性的 100%。 */
export function readingPercent(page, totalPages) {
  const total = Number(totalPages)
  const current = Number(page)
  if (!Number.isFinite(total) || total <= 0) return 0
  if (!Number.isFinite(current) || current <= 0) return 0
  return Math.min(100, Math.round((current / total) * 100))
}

/** 生成 CSS 变量：背景换肤 / 缩放 / 对比度 / 裁白边 / 重排字号行距，全部即时生效且不改 PDF 源文件。 */
export function readerCssVars(settings) {
  const s = normalizeReaderSettings(settings, 'scan')
  const theme = backdropOf(s.backdrop)
  return {
    '--reader-page-bg': theme.bg,
    '--reader-stage-bg': theme.stage,
    '--reader-ink': theme.text,
    '--reader-zoom': String(s.zoom / 100),
    '--reader-contrast': String(s.contrast / 100),
    '--reader-crop': s.crop + '%',
    '--reader-font-size': s.fontSize + 'px',
    '--reader-line-height': String(lineHeightRatio(s.lineHeight))
  }
}

export const storageKey = (bookId) => `fabushi:reader-settings:${bookId || 'none'}`
