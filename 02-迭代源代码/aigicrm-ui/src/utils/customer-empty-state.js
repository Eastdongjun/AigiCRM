// 【FM3 2026-10-07】服务端分页改造后（2026-10-05）列表数据=当前页，原"records 有值即提示筛选"
// 的判定失真：当前页为空 ≠ 全量为空，搜不到时会误导用户去找管理员查数据范围。
// 空态判定改基于服务端返回的筛选后 total：
//   - 当前页空但 total>0（筛选仍有匹配/页码越界）→ 提示调整筛选条件
//   - total===0 且带搜索词 → "未找到与XX匹配"（搜索不匹配 ≠ 数据范围问题，UI 整改 2026-10-08）
//   - total===0 且无搜索词 → 原数据范围文案
// 【UI 整改 2026-10-08】新增第三参数 searchKey：有搜索词时不再误导用户"联系管理员"，
// 改为引导清空搜索/调整筛选（配合 MainTable 空态区的"清空搜索与筛选"按钮）。
// 【⑬B 2026-10-17】页签分支：FM3 只修了搜索词分支，页签(devType)筛到 total=0 仍走数据范围文案——
// 成熟创新=0 的销售员（14/31）打开列表即空表+"联系管理员检查数据范围"，被误导成权限问题
// （NM005/郑磊案）。新增第四参数 opts={ allTotal, tabLabel }：非"全部"页签空表时，
// allTotal（=tabCounts['全部']，counts 接口与列表同口径但不加 devType 条件）>0 即判"该分类为空"；
// 计数未回（防误导闪现）也先按该分类口径，只有计数确证 allTotal===0 才回落数据范围文案。
export function customerEmptyText(records, total, searchKey = '', opts = {}) {
  if (records.length || (total || 0) > 0) return '暂无符合筛选条件的客户'
  const kw = String(searchKey || '').trim()
  if (kw) return `未找到与“${kw}”匹配的客户，试试清空搜索或调整筛选`
  const { allTotal, tabLabel } = opts || {}
  if (tabLabel && !(Number.isFinite(allTotal) && allTotal <= 0)) {
    const suffix = Number.isFinite(allTotal) && allTotal > 0 ? `（共 ${allTotal} 个）` : ''
    return `「${tabLabel}」分类下暂无客户，可切换「全部」页签查看${suffix}`
  }
  return '当前数据范围内暂无可见客户，请联系管理员检查数据范围'
}
