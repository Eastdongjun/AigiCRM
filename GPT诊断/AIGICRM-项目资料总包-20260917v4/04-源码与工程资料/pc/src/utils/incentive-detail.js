// 激励统计 → 明细下钻的纯逻辑（2026-09-13）
//
// 需求：激励统计表格里的数据要能点进子页面看明细。
// 这里只放与 UI 无关、可单测的逻辑：明细路由地址、按细分过滤、四个安装槽位文件配对、合计。

export const DETAIL_ROUTE_NAME = 'IncentiveStatsDetail'

/** 四个安装槽位（与后端 filePreId/fileMidId/filePostId/fileTrackId 及对应数组字段一一对应）。 */
export const SLOT_FIELDS = [
  { key: 'pre', label: '安装前', idsField: 'filePreIds', idField: 'filePreId', nameField: 'filePreName' },
  { key: 'mid', label: '安装中', idsField: 'fileMidIds', idField: 'fileMidId', nameField: 'fileMidName' },
  { key: 'post', label: '安装后', idsField: 'filePostIds', idField: 'filePostId', nameField: 'filePostName' },
  { key: 'track', label: '跟踪', idsField: 'fileTrackIds', idField: 'fileTrackId', nameField: 'fileTrackName' },
]

/** 明细页路由地址（childId 走 path 参数，其余走 query，便于直接分享/刷新后仍可读）。 */
export function buildDetailLocation({ childId, parentId, parentName, childName, score, period } = {}) {
  return {
    name: DETAIL_ROUTE_NAME,
    params: { childId: String(childId) },
    query: {
      parentId: parentId == null ? undefined : String(parentId),
      parentName: parentName || undefined,
      childName: childName || undefined,
      score: score == null ? undefined : String(score),
      period: period || undefined,
    },
  }
}

/** 明细 = 该细分（子类）自身的记录；后端 records 接口只支持 parentCategoryId，故按 categoryId 精确过滤。 */
export function filterRecordsByCategory(records, childId) {
  const target = Number(childId)
  return (records || []).filter((record) => Number(record?.categoryId) === target)
}

/** 某槽位的附件 id 列表：优先用后端已解析的数组字段，否则兼容 JSON 字符串/单值。 */
export function normalizeSlotIds(record, slot) {
  const list = record?.[slot.idsField]
  if (Array.isArray(list) && list.length) {
    return list.filter((id) => id != null)
  }
  const raw = record?.[slot.idField]
  if (raw == null || raw === '') return []
  if (Array.isArray(raw)) return raw.filter((id) => id != null)
  if (typeof raw === 'string' && raw.trim().startsWith('[')) {
    try {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed)) return parsed.filter((id) => id != null)
    } catch (e) {
      // 不是合法 JSON 就按单值处理
    }
  }
  return [raw]
}

/** 某槽位的附件列表：ids 与 nameField 里的逗号分隔名按序配对（缺名时给占位名）。 */
export function slotFiles(record, slot) {
  const ids = normalizeSlotIds(record, slot)
  const names = String(record?.[slot.nameField] || '')
    .split(/[,，]/)
    .map((name) => name.trim())
    .filter(Boolean)
  return ids.map((id, index) => ({ id, name: names[index] || `附件${index + 1}` }))
}

/** 明细合计积分（表头展示用）。 */
export function sumScore(records) {
  return (records || []).reduce((total, record) => total + (Number(record?.score) || 0), 0)
}

/** 各槽位计数（表头展示用）。 */
export function slotCounts(records) {
  const counts = {}
  for (const slot of SLOT_FIELDS) {
    counts[slot.key] = (records || []).reduce((total, record) => total + slotFiles(record, slot).length, 0)
  }
  return counts
}
