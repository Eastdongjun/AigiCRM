function parseIds(value) {
  if (value === null || value === undefined || value === '') return []
  if (Array.isArray(value)) return value.map(Number).filter(Boolean)
  if (typeof value === 'number') return value ? [value] : []
  const text = String(value).trim()
  if (!text) return []
  try {
    const parsed = JSON.parse(text)
    if (Array.isArray(parsed)) return parsed.map(Number).filter(Boolean)
    const single = Number(parsed)
    return single ? [single] : []
  } catch (_) {
    return text.split(',').map((item) => Number(String(item).trim())).filter(Boolean)
  }
}

function splitValues(value) {
  if (!value) return []
  return String(value).split(',').map((item) => item.trim()).filter(Boolean)
}

function pickSlotValue(record, keys) {
  return keys.map((key) => record && record[key]).find((value) => value !== undefined && value !== null && value !== '') || ''
}

function isDownloadReference(value) {
  const text = String(value || '').trim()
  if (/^https?:\/\//i.test(text)) return true
  if (text.indexOf('/api/file/download/') >= 0) return true
  return /(^|\/)[^/]+\.(jpg|jpeg|png|gif|webp|bmp|heic|heif|mp4|mov|m4v|webm|avi|mkv|pdf|doc|docx|xls|xlsx|ppt|pptx|csv|txt)(\?|#|$)/i.test(text)
}

function detectType(name, meta) {
  const text = `${name || ''} ${meta || ''}`.trim().toLowerCase()
  if (/(^|\W)image\//.test(text) || /\.(jpg|jpeg|png|gif|webp|bmp|heic|heif)(\?|#|$)/i.test(text)) return 'image'
  if (/(^|\W)video\//.test(text) || /\.(mp4|mov|m4v|webm|avi|mkv)(\?|#|$)/i.test(text)) return 'video'
  if (
    /application\/(pdf|msword|vnd\.ms-|vnd\.openxmlformats-)/.test(text) ||
    /\.(pdf|doc|docx|xls|xlsx|ppt|pptx|csv|txt)(\?|#|$)/i.test(text)
  ) return 'document'
  return 'file'
}

function typeText(type) {
  return type === 'image' ? '图片' : type === 'video' ? '视频' : type === 'document' ? '文档' : '文件'
}

function pickIndexed(values, index) {
  return values[index] || values[0] || ''
}

function buildSlotFiles(ids, names, tag, meta) {
  const files = []
  const idList = parseIds(ids)
  const nameList = splitValues(names)
  const metaList = splitValues(meta)
  const fallbackList = idList.length ? [] : metaList.filter(isDownloadReference)
  idList.forEach((id, index) => {
    const name = nameList[index] || nameList[0] || `附件-${id}`
    const fileUrl = pickIndexed(metaList, index)
    const detectedType = detectType(name, fileUrl)
    files.push({
      id,
      name,
      tag,
      fileUrl,
      type: detectedType,
      typeText: typeText(detectedType)
    })
  })
  fallbackList.forEach((fileUrl, index) => {
    const name = nameList[index] || nameList[0] || fileUrl.split('/').filter(Boolean).pop() || `附件-${index + 1}`
    const detectedType = detectType(name, fileUrl)
    files.push({
      id: '',
      fileUrl,
      url: fileUrl,
      name,
      tag,
      type: detectedType,
      typeText: typeText(detectedType)
    })
  })
  return files
}

// 【D39 单表维护】五个佐证槽位的字段映射(单一数据源)。同一槽位的元数据字段在
// 历史接口/PC 端存在多套键名(filePreUrl/filePrePath/filePreOssKey…), 因此逐键回退
// 取第一个非空值——这是历史字段兼容, 旧键在存量数据清空前不可删。id 侧
// filePreId(单值) 与 filePreIds(逗号串/JSON 数组) 同理回退。此映射原先在
// buildIncentiveFiles 与 buildIncentiveFileGroups 两个函数里完整重复, 已提取为本表。
const EVIDENCE_SLOTS = [
  { key: 'pre', label: '安装前', hint: '现场问题、安装前状态、方案资料', idKeys: ['filePreId', 'filePreIds'], nameKey: 'filePreName', metaKeys: ['filePreUrl', 'filePrePath', 'filePreOssKey', 'filePreType', 'filePreMimeType'] },
  { key: 'mid', label: '安装中', hint: '安装过程、调试过程、现场记录', idKeys: ['fileMidId', 'fileMidIds'], nameKey: 'fileMidName', metaKeys: ['fileMidUrl', 'fileMidPath', 'fileMidOssKey', 'fileMidType', 'fileMidMimeType'] },
  { key: 'post', label: '安装后', hint: '运行结果、验收报告、客户反馈', idKeys: ['filePostId', 'filePostIds'], nameKey: 'filePostName', metaKeys: ['filePostUrl', 'filePostPath', 'filePostOssKey', 'filePostType', 'filePostMimeType'] },
  { key: 'track', label: '跟踪', hint: '回访、复盘、持续跟踪资料', idKeys: ['fileTrackId', 'fileTrackIds'], nameKey: 'fileTrackName', metaKeys: ['fileTrackUrl', 'fileTrackPath', 'fileTrackOssKey', 'fileTrackType', 'fileTrackMimeType'] },
  { key: 'evidence', label: '佐证', hint: '交流、拜访、方案等非安装类资料', idKeys: ['fileId', 'fileIds'], nameKey: 'fileName', metaKeys: ['fileUrl', 'filePath', 'ossKey', 'fileKey', 'fileType', 'fileMimeType'] }
]

// 与原写法 `record.filePreId || record.filePreIds` 语义一致: 取第一个真值(0/''/null 均跳过)
function pickFirstTruthy(record, keys) {
  return keys.map((key) => record[key]).find(Boolean)
}

function slotFiles(record, slot) {
  return buildSlotFiles(pickFirstTruthy(record, slot.idKeys), record[slot.nameKey], slot.label, pickSlotValue(record, slot.metaKeys))
}

function buildIncentiveFiles(record) {
  if (!record) return []
  return EVIDENCE_SLOTS.reduce((files, slot) => files.concat(slotFiles(record, slot)), [])
}

function buildIncentiveFileGroups(record) {
  if (!record) return []
  return EVIDENCE_SLOTS
    .map((slot) => ({ key: slot.key, label: slot.label, hint: slot.hint, files: slotFiles(record, slot) }))
    .filter((group) => group.files.length)
}

module.exports = {
  buildIncentiveFileGroups,
  buildIncentiveFiles,
  detectType,
  isDownloadReference,
  parseIds,
  pickSlotValue,
  typeText
}
