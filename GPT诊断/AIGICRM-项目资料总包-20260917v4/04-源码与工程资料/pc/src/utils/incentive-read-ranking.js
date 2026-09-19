// 「本月激励查询阅读排行榜」纯逻辑（2026-09-13）
//
// 需求：系统设置下新增该页，默认只有管理员（勾选授权后）可见。
// 这里只放与 UI 无关、可单测的部分：月份标签、已读率口径、名次奖牌、榜单行归一。

/** 当月（YYYY-MM），与后端缺省口径一致 */
export function currentMonth(today = new Date()) {
  const month = String(today.getMonth() + 1).padStart(2, '0')
  return `${today.getFullYear()}-${month}`
}

/** 月份标签：2026-09 → 2026年9月 */
export function monthLabel(period) {
  const matched = /^(\d{4})-(\d{1,2})$/.exec(String(period || ''))
  if (!matched) return '本月'
  return `${matched[1]}年${Number(matched[2])}月`
}

/** 已读率（%）：分母 <=0 记 0，保留 1 位小数，与后端 readRanking 同口径 */
export function readRate(readCount, publishedCount) {
  const whole = Number(publishedCount) || 0
  if (whole <= 0) return 0
  return Math.round((Number(readCount) || 0) * 1000 / whole) / 10
}

/** 已读率展示文案 */
export function readRateText(readCount, publishedCount) {
  const whole = Number(publishedCount) || 0
  if (whole <= 0) return '—'
  return `${readRate(readCount, publishedCount)}%`
}

/** 前三名奖牌（1/2/3），其余返回空串 */
export function medalOf(rank) {
  return ({ 1: '🥇', 2: '🥈', 3: '🥉' })[Number(rank)] || ''
}

/** 已读率进度条颜色档位 */
export function rateTone(rate) {
  const value = Number(rate) || 0
  if (value >= 80) return 'excellent'
  if (value >= 50) return 'good'
  if (value > 0) return 'fair'
  return 'none'
}

/**
 * 榜单行归一：后端已给 rank/readRate，这里补齐缺省与展示字段，
 * 并把「有阅读记录」与否单独标出来（0 条已读的行前端置灰）。
 */
export function normalizeRanking(rows) {
  if (!Array.isArray(rows)) return []
  return rows.map((row, index) => {
    const readCount = Number(row?.readCount) || 0
    const publishedCount = Number(row?.publishedCount) || 0
    const rate = row?.readRate == null ? readRate(readCount, publishedCount) : Number(row.readRate)
    return {
      ...row,
      rank: Number(row?.rank) || index + 1,
      readCount,
      unreadCount: Number(row?.unreadCount) || 0,
      publishedCount,
      readRate: rate,
      isReader: readCount > 0,
    }
  })
}
