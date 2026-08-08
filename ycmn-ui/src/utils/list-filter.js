// 客户列表本地过滤纯函数
// 从 customer/list.vue 的 filteredAllData / currentTabData 两条过滤链抽离。
// 关键约定（保持与原页面行为完全一致）：
//   - search 与 filters、advFilter 是三层并列过滤，逐个叠加
//   - advFilter.orgIds 的 uids 集合取自【原始数据源】而非过滤中间结果，
//     且通过 opts.orgKeys 参数化匹配字段（原页面两个 computed 用不同字段）
// 筛选/tab 变化时调用方必须先重置页码（见 pagination.js 注释）。

/**
 * 对客户列表做本地多条件过滤
 * @param {Array} data 源数据（应为服务端分页页的完整记录）
 * @param {string} search 主搜索词（匹配 customerName / customerCode）
 * @param {object} filters 快速筛选 { industry, status }
 * @param {object} advFilter 高级筛选 { industry, province, city, status, protect, owner,
 *   regDate, expDate, orgIds, userIds, deptIds }
 * @param {object} [opts]
 * @param {Array} [opts.uidsSource] orgIds 的 uids 集合来源（默认 data，原页面传 tableData.value）
 * @param {Array} [opts.orgKeys] orgIds 匹配的字段（默认 ['officeId','orgId']，
 *   currentTabData 用 ['officeId','deptId']）
 * @returns {Array} 过滤后的完整列表（未分页）
 */
export function filterCustomers(data, search, filters, advFilter, opts = {}) {
  const uidsSource = opts.uidsSource || data
  const orgKeys = opts.orgKeys || ['officeId', 'orgId']
  let rows = Array.isArray(data) ? data.slice() : []

  // 主搜索：名称 / 编号
  const s = (search || '').toLowerCase()
  if (s) {
    rows = rows.filter(c =>
      (c.customerName || '').toLowerCase().includes(s) ||
      (c.customerCode || '').toLowerCase().includes(s))
  }

  // 快速筛选
  const fs = filters || {}
  if (fs.industry) rows = rows.filter(c => c.industry === fs.industry)
  if (fs.status) rows = rows.filter(c => c.status === fs.status)

  // 高级筛选
  const af = advFilter || {}
  if (af.industry) rows = rows.filter(c => c.industry === af.industry)
  if (af.province) rows = rows.filter(c => (c.province || '').includes(af.province))
  if (af.city) rows = rows.filter(c => (c.city || '').includes(af.city))
  if (af.status) rows = rows.filter(c => c.status === af.status)
  if (af.protect) rows = rows.filter(c => c.protectionStatus === af.protect)
  if (af.owner) rows = rows.filter(c => (c.ownerName || '').includes(af.owner))
  if (af.regDate && af.regDate.length === 2) {
    rows = rows.filter(c => c.registerDate >= af.regDate[0] && c.registerDate <= af.regDate[1])
  }
  if (af.expDate && af.expDate.length === 2) {
    rows = rows.filter(c => c.expireDate >= af.expDate[0] && c.expireDate <= af.expDate[1])
  }
  // orgIds：先从原始数据源收集所属用户的 ownerId，再过滤（与原页面一致）
  if (af.orgIds && af.orgIds.length > 0) {
    const uids = (Array.isArray(uidsSource) ? uidsSource : [])
      .filter(r => orgKeys.some(k => af.orgIds.includes(r[k])))
      .map(r => r.ownerId)
    rows = rows.filter(c => uids.includes(c.ownerId))
  }
  if (af.deptIds && af.deptIds.length > 0) {
    rows = rows.filter(c => af.deptIds.includes(c.deptId))
  }
  if (af.userIds && af.userIds.length > 0) {
    rows = rows.filter(c => af.userIds.includes(c.ownerId))
  }
  return rows
}
