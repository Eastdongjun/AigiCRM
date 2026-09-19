// 激励/得分周期口径的单一来源（2026-09-12 修复）
//
// 背景缺陷：激励页、激励统计页、客户排行、得分排行各自内联生成周期选项，
// 只生成「上半年/下半年」，而库内历史与生产数据（228 条激励记录、得分表）用的是「全年」；
// 且激励页默认 `selectedPeriod = '2026上半年'` 并**每次请求都带上**，后端按等值过滤
// ⇒ 打开页面必然 0 条，且下拉里选不到「全年」，用户完全看不到数据。
//
// 处理：
//   · 选项统一由本模块生成：全部 / <年>全年 / <年>下半年 / <年>上半年；
//   · 「全部」的值为空串，调用方请求时应省略该参数（后端对空值不放行等值过滤的旧行为已同步修复）；
//   · 需要"具体周期"的写入口（如新增激励记录）用 defaultRecordPeriod() 兜底，避免写入空周期。

export const PERIOD_ALL = { label: '全部', value: '' }

/** 周期下拉选项：默认覆盖 2030→2025，每含 全年/下半年/上半年 三项。 */
export function buildPeriodOptions({ fromYear = 2030, toYear = 2025 } = {}) {
  const options = [PERIOD_ALL]
  for (let year = fromYear; year >= toYear; year--) {
    options.push({ label: `${year}全年`, value: `${year}全年` })
    options.push({ label: `${year}下半年`, value: `${year}下半年` })
    options.push({ label: `${year}上半年`, value: `${year}上半年` })
  }
  return options
}

/** 列表/统计筛选用：值为空串（全部）时返回 undefined，axios 会自动省略该查询参数。 */
export function periodQueryValue(value) {
  return value ? value : undefined
}

/** 写入口的周期兜底：界面选的是"全部"时，按当前年份的「全年」落库。 */
export function defaultRecordPeriod(now = new Date()) {
  return `${now.getFullYear()}全年`
}
