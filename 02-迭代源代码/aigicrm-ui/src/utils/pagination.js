// 客户端分页切片纯函数
// 从 reminder/index.vue 的 pagedData、customer/list.vue 的 filteredList、
// incentive/search.vue 的 pagedCusts 抽离的统一实现。筛选/tab 变化时必须先重置 page=1。

/**
 * 对列表做分页切片
 * @param {Array} list 完整数据（未切片）
 * @param {number} page 页码（1-based，越界自动收敛）
 * @param {number} pageSize 每页条数（<=0 自动收敛为 1）
 * @returns {Array} 当前页切片
 */
export function paginate(list, page, pageSize) {
  if (!Array.isArray(list)) return []
  const size = Math.max(1, Number(pageSize) || 1)
  const p = Math.max(1, Number(page) || 1)
  const start = (p - 1) * size
  return list.slice(start, start + size)
}

/**
 * 计算客户端分页总数（等价于 tableData.length，供 total 绑定）
 * @param {Array} list
 * @returns {number}
 */
export function totalOf(list) {
  return Array.isArray(list) ? list.length : 0
}

/**
 * 每页条数选项（统一口径，2026-09-13）
 *
 * 背景：客户列表早已提供「每页条数」下拉（30/50/100），其它列表的 el-pagination
 * 要么没把 `sizes` 写进 layout、要么没有 page-sizes，导致"列表底部缺少选择每页多少条"。
 * 这里集中定义选项与 layout，避免各页面各写一份。
 */
export const PAGE_SIZES = [10, 20, 30, 50, 100]

/** el-pagination 统一 layout：带每页条数下拉与跳页，便于长列表翻页 */
export const PAGINATION_LAYOUT = 'sizes, prev, pager, next, jumper'

/**
 * 生成某列表的每页条数选项：在统一选项基础上**保证该列表当前默认值一定在选项内**
 * （例如默认 15/页 的列表 → [10,15,20,30,50,100]，否则下拉会显示成空值）
 * @param {number} defaultSize 该列表当前默认每页条数
 * @returns {number[]} 升序去重后的选项
 */
export function pageSizes(defaultSize) {
  const size = Number(defaultSize)
  const set = new Set(PAGE_SIZES)
  if (Number.isFinite(size) && size > 0) set.add(size)
  return [...set].sort((a, b) => a - b)
}
