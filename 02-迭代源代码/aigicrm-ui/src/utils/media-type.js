// 文件名媒体类型判定（唯一来源，2026-10-04）
//
// 此前图片/视频扩展名正则与三态判定在 useIncentiveUpload(fileType)、
// useCustomerDetail(detectType) 各内联一份（useIncentiveUpload 的 isImage 又重复了图片正则），
// 扩展名集合一旦调整极易漂移；抽出共享。返回 'image' | 'video' | 'doc'（兜底 doc，含空值），
// 供 FilePreview 预览类型与"可预览"样式判定使用。

const IMAGE_EXT_RE = /\.(jpg|jpeg|png|gif|webp|bmp)$/i
const VIDEO_EXT_RE = /\.(mp4|mov|avi|webm)$/i

/** 是否图片文件名（按扩展名判定，大小写不敏感；空值返回 false） */
export function isImageName(name) {
  return IMAGE_EXT_RE.test(name || '')
}

/** 文件名三态判定：image / video / doc（无法识别或空值兜底 doc） */
export function mediaTypeOf(name) {
  if (!name) return 'doc'
  if (IMAGE_EXT_RE.test(name)) return 'image'
  if (VIDEO_EXT_RE.test(name)) return 'video'
  return 'doc'
}

// ===== 在线预览种类判定（文件阅读器 2026-10-16）=====
// FilePreview / SearchLightbox / 文件管理页此前各持一份扩展名表判定"能不能预览、怎么预览"，
// 口径漂移(文件页认 mov 为视频、灯箱不认; 灯箱认 csv 为文本、文件页归 other)。统一到这里：
// 返回值即渲染器选择，新增格式只改此表。
//   image  → <el-image>（浏览器原生可解码格式，heic 不在内）
//   video  → <video> 直链流式（avi/mkv 浏览器不可播，归 unsupported）
//   audio  → <audio>
//   pdf    → 浏览器内建 PDF 阅读器（blob iframe）
//   docx   → docx-preview 渲染（仅 OOXML；老 .doc 无前端方案）
//   sheet  → SheetJS 解析→表格（xlsx/xls/csv）
//   text   → 纯文本排版
//   unsupported → 提示下载（pptx/ppt/doc/heic/压缩包等）
const PREVIEW_KIND_RES = [
  ['image', /\.(jpg|jpeg|png|gif|webp|bmp)$/i],
  ['video', /\.(mp4|webm|mov)$/i],
  ['audio', /\.(mp3|wav|m4a)$/i],
  ['pdf', /\.pdf$/i],
  ['docx', /\.docx$/i],
  ['sheet', /\.(xlsx|xls|csv)$/i],
  ['text', /\.(txt|md|log|json|xml)$/i],
]

/** 文件名 → 预览种类（见上表）；空值或未知扩展名返回 'unsupported' */
export function previewKindOf(name) {
  const n = name || ''
  for (const [kind, re] of PREVIEW_KIND_RES) if (re.test(n)) return kind
  return 'unsupported'
}

/** 【图片秒开 2026-10-17】给下载 URL 追加后端缩略参数(≤300px, 实测 18MB 原图 14.8s → 缩略 0.1~0.9s)。
 *  列表/弹窗"展示"一律走它; 原图仅在点击放大/下载时加载。非图片 URL 调用方自行判断后再用。 */
export function thumbSrcOf(url) {
  if (!url || String(url).includes('thumbnail=')) return url
  return url + (String(url).includes('?') ? '&' : '?') + 'thumbnail=1'
}

/** 不支持在线预览时的说明文案（按扩展名给出具体原因，避免一句"不支持"让用户以为是故障） */
export function unsupportedHintOf(name) {
  const ext = String(name || '').split('.').pop()?.toLowerCase() || ''
  if (['ppt', 'pptx'].includes(ext)) return '演示文稿暂不支持在线预览，请下载后用 PowerPoint / WPS 打开'
  if (ext === 'doc') return '老版 .doc 格式暂不支持在线预览，请下载查看（.docx 可直接预览）'
  if (ext === 'heic' || ext === 'heif') return 'HEIC 图片浏览器无法解码，请下载后查看'
  if (['avi', 'mkv'].includes(ext)) return '该视频封装格式浏览器无法播放，请下载后用播放器打开'
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return '压缩包请下载后解压查看'
  return '当前格式暂不支持在线预览，可下载查看'
}
