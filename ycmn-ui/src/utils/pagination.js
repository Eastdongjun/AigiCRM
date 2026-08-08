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
