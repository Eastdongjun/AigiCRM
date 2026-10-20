// 客户端分页工具：给定完整数组与页码/页大小，返回当前页切片与分页元信息。
// 服务端分页页面不需要它，只把 total/page/pageSize 交给 <pager> 组件即可。
function paginate(rows, page, pageSize) {
  const list = Array.isArray(rows) ? rows : []
  const size = pageSize > 0 ? pageSize : 10
  const total = list.length
  const totalPages = Math.max(1, Math.ceil(total / size))
  const safePage = Math.min(Math.max(1, page || 1), totalPages)
  const start = (safePage - 1) * size
  return {
    rows: list.slice(start, start + size),
    total,
    totalPages,
    page: safePage,
    pageSize: size
  }
}

module.exports = { paginate }
