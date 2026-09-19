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

function pushFiles(files, ids, names, tag, meta) {
  files.push(...buildSlotFiles(ids, names, tag, meta))
}

function buildIncentiveFiles(record) {
  const files = []
  if (!record) return files
  pushFiles(files, record.filePreId || record.filePreIds, record.filePreName, '安装前', pickSlotValue(record, ['filePreUrl', 'filePrePath', 'filePreOssKey', 'filePreType', 'filePreMimeType']))
  pushFiles(files, record.fileMidId || record.fileMidIds, record.fileMidName, '安装中', pickSlotValue(record, ['fileMidUrl', 'fileMidPath', 'fileMidOssKey', 'fileMidType', 'fileMidMimeType']))
  pushFiles(files, record.filePostId || record.filePostIds, record.filePostName, '安装后', pickSlotValue(record, ['filePostUrl', 'filePostPath', 'filePostOssKey', 'filePostType', 'filePostMimeType']))
  pushFiles(files, record.fileTrackId || record.fileTrackIds, record.fileTrackName, '跟踪', pickSlotValue(record, ['fileTrackUrl', 'fileTrackPath', 'fileTrackOssKey', 'fileTrackType', 'fileTrackMimeType']))
  pushFiles(files, record.fileId || record.fileIds, record.fileName, '佐证', pickSlotValue(record, ['fileUrl', 'filePath', 'ossKey', 'fileKey', 'fileType', 'fileMimeType']))
  return files
}

function buildIncentiveFileGroups(record) {
  if (!record) return []
  return [
    {
      key: 'pre',
      label: '安装前',
      hint: '现场问题、安装前状态、方案资料',
      files: buildSlotFiles(record.filePreId || record.filePreIds, record.filePreName, '安装前', pickSlotValue(record, ['filePreUrl', 'filePrePath', 'filePreOssKey', 'filePreType', 'filePreMimeType']))
    },
    {
      key: 'mid',
      label: '安装中',
      hint: '安装过程、调试过程、现场记录',
      files: buildSlotFiles(record.fileMidId || record.fileMidIds, record.fileMidName, '安装中', pickSlotValue(record, ['fileMidUrl', 'fileMidPath', 'fileMidOssKey', 'fileMidType', 'fileMidMimeType']))
    },
    {
      key: 'post',
      label: '安装后',
      hint: '运行结果、验收报告、客户反馈',
      files: buildSlotFiles(record.filePostId || record.filePostIds, record.filePostName, '安装后', pickSlotValue(record, ['filePostUrl', 'filePostPath', 'filePostOssKey', 'filePostType', 'filePostMimeType']))
    },
    {
      key: 'track',
      label: '跟踪',
      hint: '回访、复盘、持续跟踪资料',
      files: buildSlotFiles(record.fileTrackId || record.fileTrackIds, record.fileTrackName, '跟踪', pickSlotValue(record, ['fileTrackUrl', 'fileTrackPath', 'fileTrackOssKey', 'fileTrackType', 'fileTrackMimeType']))
    },
    {
      key: 'evidence',
      label: '佐证',
      hint: '交流、拜访、方案等非安装类资料',
      files: buildSlotFiles(record.fileId || record.fileIds, record.fileName, '佐证', pickSlotValue(record, ['fileUrl', 'filePath', 'ossKey', 'fileKey', 'fileType', 'fileMimeType']))
    }
  ].filter((group) => group.files.length)
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
