// 【D20/D23 切片 6】书架筛选/排序/进度纯函数：PC 与单测共用，避免视图层各写一份口径。
export const SHELF_STATUS_OPTIONS = [
  { value: 'all', label: '全部' },
  { value: 'wish', label: '想读' },
  { value: 'reading', label: '在读' },
  { value: 'finished', label: '已读完' },
  { value: 'favorite', label: '收藏' }
]

export const SHELF_SORT_OPTIONS = [
  { value: 'recent', label: '最近阅读' },
  { value: 'added', label: '加入时间' },
  { value: 'manual', label: '手动排序' },
  { value: 'title', label: '书名' }
]

export const shelfStatusLabel = (status) => {
  const hit = SHELF_STATUS_OPTIONS.find((item) => item.value === status)
  return hit ? hit.label : '在读'
}

export const progressPct = (book) => {
  const last = Number(book && book.lastPage != null ? book.lastPage : 1)
  const total = Number(book && book.totalPages != null ? book.totalPages : book && book.pages)
  if (!Number.isFinite(last) || !Number.isFinite(total) || total <= 0) return 0
  return Math.max(0, Math.min(100, Math.round(last * 100 / total)))
}

/** 分组筛选：auto 组按书目 categoryCode，custom 组按 shelf.groupId。 */
export const matchesGroup = (row, group) => {
  if (!group || group.value === 'all') return true
  if (group.type === 'auto') return row.categoryCode === group.categoryCode
  return String(row.groupId || '') === String(group.value)
}

export const filterShelf = (rows, options = {}) => {
  const { status = 'all', keyword = '', group = null } = options
  const word = String(keyword || '').trim().toLowerCase()
  return (rows || []).filter((row) => {
    if (status !== 'all' && row.status !== status) return false
    if (!matchesGroup(row, group)) return false
    if (!word) return true
    return `${row.title || ''}${row.author || ''}${row.categoryCode || ''}`.toLowerCase().includes(word)
  })
}

export const sortShelf = (rows, mode = 'recent') => {
  const list = [...(rows || [])]
  if (mode === 'manual') {
    return list.sort((a, b) => (b.pinned || 0) - (a.pinned || 0)
      || (a.sortNo || 0) - (b.sortNo || 0))
  }
  if (mode === 'added') {
    return list.sort((a, b) => (b.pinned || 0) - (a.pinned || 0)
      || String(b.createdAt || '').localeCompare(String(a.createdAt || '')))
  }
  if (mode === 'title') {
    return list.sort((a, b) => String(a.title || '').localeCompare(String(b.title || ''), 'zh-CN'))
  }
  return list.sort((a, b) => (b.pinned || 0) - (a.pinned || 0)
    || String(b.updatedAt || '').localeCompare(String(a.updatedAt || '')))
}
