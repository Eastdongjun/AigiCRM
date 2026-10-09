// 客户状态码 → 中文标签/标签色（唯一来源，2026-09-13）
//
// 此前该映射内联在 composables/useCustomerList.js，提醒详情页也需要同一口径；
// 抽出共享工具避免出现第二份状态字典（口径不一致会让同一客户在不同页面显示不同状态）。

export const CUSTOMER_STATUS_MAP = {
  normal: { text: '正常', type: 'success' },
  warning: { text: '预警', type: 'warning' },
  expired: { text: '已超期', type: 'danger' },
  sea: { text: '公海', type: 'info' },
  archived: { text: '已归档', type: 'info' }
}

/** 状态中文标签；未知状态返回占位符 '—' */
export function customerStatusText(status) {
  return CUSTOMER_STATUS_MAP[status]?.text || '—'
}

/** 状态标签色（el-tag type）；未知状态回退 info */
export function customerStatusTagType(status) {
  return CUSTOMER_STATUS_MAP[status]?.type || 'info'
}
