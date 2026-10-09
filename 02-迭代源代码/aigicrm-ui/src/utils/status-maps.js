// 全站状态语义表唯一来源（2026-10-08 UI 整改 B7/C9 收口）
//
// 此前"客户状态 / 公海捞取状态 / 审批状态 / 财务台账状态"四套 text+type 映射散落各页本地定义
// （sea/index.vue、sea/records.vue、FinanceTab.vue、approval 页各自为政），同一状态码
// 不同页面可能渲染不同文案或颜色。现统一收敛到本文件：
//   · 客户状态(status 码)        → utils/customer-status.js（已有唯一来源，此处不重复）
//   · 公海捞取状态(sea.status)   → SEA_CLAIM_STATUS
//   · 审批单状态(approval.status) → APPROVAL_STATUS
//   · 开票/回款行状态(ledger)     → FINANCE_LEDGER_STATUS
// 新页面一律 import 本表，禁止再写第四套本地映射。
// type 值即 el-tag 的 type（success/warning/danger/info/primary）；文案与配色如需调整只改这里。

/** 公海捞取状态：sea/index.vue 列表与公海相关页共用 */
export const SEA_CLAIM_STATUS = {
  available: { text: '待捞取', type: 'primary' },
  pending_approval: { text: '审批中', type: 'warning' },
  claimed: { text: '已捞取', type: 'success' },
}

/** 审批单状态：审批列表/捞取记录共用（pending=待审批、reviewing=审批中，与审批页页签同名，不再叫「待提交」） */
export const APPROVAL_STATUS = {
  pending: { text: '待审批', type: 'warning' },
  reviewing: { text: '审批中', type: 'warning' },
  approved: { text: '已通过', type: 'success' },
  rejected: { text: '已驳回', type: 'danger' },
}

/** 开票/回款台账行状态：客户详情 FinanceTab 共用 */
export const FINANCE_LEDGER_STATUS = {
  voided: { text: '已作废', type: 'info' },
  pending: { text: '待核对', type: 'warning' },
  confirmed: { text: '已确认', type: 'success' },
}

/** 通用取值：未知状态码回退 info / 原样文案 */
export function statusOf(map, code) {
  return map[code] || { text: code || '—', type: 'info' }
}
