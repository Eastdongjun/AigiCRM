// 【2026-09-29 裁决】四项服务·客户排行销售视图:
// 06 销售只看到自己名下客户的行,但排名序号保留公司级名次(rank 在过滤前已按全量定序)。
// 纯函数便于单测钉行为;admin/总监/行政(salesOnly=false)原样透传。

/**
 * 按负责人过滤客户排行行。
 * @param rows 后端 customer-records 行(已按总学分降序,rank 已赋值)
 * @param userId 当前登录人 id
 * @param salesOnly 是否启用销售过滤(角色含 06 且非 admin 时为 true)
 */
export function filterCustomerRankRows(rows, userId, salesOnly) {
  if (!salesOnly) return rows || []
  const mine = String(userId ?? '')
  return (rows || []).filter((r) => String(r.ownerId ?? '') === mine)
}
