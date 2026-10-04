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
