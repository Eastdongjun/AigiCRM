// 【FM3 2026-10-07】服务端分页改造后（2026-10-05）列表数据=当前页，原"records 有值即提示筛选"
// 的判定失真：当前页为空 ≠ 全量为空，搜不到时会误导用户去找管理员查数据范围。
// 空态判定改基于服务端返回的筛选后 total：
//   - 当前页空但 total>0（筛选仍有匹配/页码越界）→ 提示调整筛选条件
//   - total===0 且带搜索词 → "未找到与XX匹配"（搜索不匹配 ≠ 数据范围问题，UI 整改 2026-10-08）
//   - total===0 且无搜索词 → 原数据范围文案
// 【UI 整改 2026-10-08】新增第三参数 searchKey：有搜索词时不再误导用户"联系管理员"，
// 改为引导清空搜索/调整筛选（配合 MainTable 空态区的"清空搜索与筛选"按钮）。
export function customerEmptyText(records, total, searchKey = '') {
  if (records.length || (total || 0) > 0) return '暂无符合筛选条件的客户'
  const kw = String(searchKey || '').trim()
  if (kw) return `未找到与“${kw}”匹配的客户，试试清空搜索或调整筛选`
  return '当前数据范围内暂无可见客户，请联系管理员检查数据范围'
}
