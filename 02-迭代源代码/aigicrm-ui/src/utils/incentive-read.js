export function isCurrentMonth(date, now = new Date()) {
  if (!date) return false
  const d = new Date(date)
  return d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth()
}

export function sortByScore(items, direction = '') {
  const list = [...(items || [])]
  if (direction === 'asc') return list.sort((a, b) => Number(a.totalScore || 0) - Number(b.totalScore || 0))
  if (direction === 'desc') return list.sort((a, b) => Number(b.totalScore || 0) - Number(a.totalScore || 0))
  return list
}
