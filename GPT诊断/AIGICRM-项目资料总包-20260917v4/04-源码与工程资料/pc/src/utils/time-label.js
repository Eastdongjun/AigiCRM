// 时间文案工具（2026-09-13）
//
// 用途：激励查询「查看详情」时间线里，日期旁边显示"今天 / 昨天 / N 天前"，
// 让最近发生的记录一眼可见（纯展示，不影响任何业务判断）。

/** 把 'YYYY-MM-DD HH:mm' / ISO 串解析为本地时间（解析失败返回 null） */
function parseLocal(dateTime) {
  if (!dateTime) return null
  if (dateTime instanceof Date) return Number.isNaN(dateTime.getTime()) ? null : dateTime
  const normalized = String(dateTime).replace('T', ' ').replace(/-/g, '/')
  const parsed = new Date(normalized)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

/**
 * 相对天数文案：
 * - 今天 / 昨天 → 固定文案
 * - 2~30 天前 → 'N 天前'
 * - 超过 30 天或解析失败 → ''（调用方只显示原日期）
 */
export function relativeDay(dateTime, now = new Date()) {
  const target = parseLocal(dateTime)
  if (!target) return ''
  const startOfTarget = new Date(target.getFullYear(), target.getMonth(), target.getDate())
  const startOfNow = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const days = Math.round((startOfNow - startOfTarget) / 86400000)
  if (days <= 0) return '今天'
  if (days === 1) return '昨天'
  if (days <= 30) return `${days} 天前`
  return ''
}

/** 分组标题：'2026-09' → '2026年9月'；无有效时间 → '时间待补' */
export const UNKNOWN_MONTH_KEY = 'unknown'

export function monthLabelOf(monthKey) {
  if (!monthKey || monthKey === UNKNOWN_MONTH_KEY) return '时间待补'
  const matched = /^(\d{4})-(\d{1,2})$/.exec(String(monthKey))
  if (!matched) return '时间待补'
  return `${matched[1]}年${Number(matched[2])}月`
}

/** 取 'YYYY-MM' 月份键（解析失败 → UNKNOWN_MONTH_KEY） */
export function monthKeyOf(dateTime) {
  const parsed = parseLocal(dateTime)
  if (!parsed) return UNKNOWN_MONTH_KEY
  return `${parsed.getFullYear()}-${String(parsed.getMonth() + 1).padStart(2, '0')}`
}

/**
 * 记录按「月」分组用于时间轴展示：
 * - 组内、组间均按时间**倒序**（最新在前；与原接口的品类排序无关，纯展示口径）
 * - 无有效时间的记录归入最后一组「时间待补」
 * @param {Array} records
 * @returns {{key:string,label:string,count:number,items:Array}[]}
 */
export function groupRecordsByMonth(records) {
  if (!Array.isArray(records) || records.length === 0) return []
  const sorted = [...records].sort((a, b) =>
    String(b?.createdAt || '').localeCompare(String(a?.createdAt || '')))
  const groups = []
  const indexOf = new Map()
  for (const item of sorted) {
    const key = monthKeyOf(item?.createdAt)
    if (!indexOf.has(key)) {
      indexOf.set(key, groups.length)
      groups.push({ key, label: monthLabelOf(key), count: 0, items: [] })
    }
    const group = groups[indexOf.get(key)]
    group.items.push(item)
    group.count += 1
  }
  return groups
}
