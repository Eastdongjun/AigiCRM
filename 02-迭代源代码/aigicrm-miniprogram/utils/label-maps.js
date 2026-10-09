// 跨页共用的业务枚举 → 中文文案映射。
// 原先 approval 两页(approvalTypeLabel/approvalStatusText/nodeStatusText/pendingStatus/
// normalizeApprovalNode)与 sea 两页(claimStatusText)各自逐字复制一份, 收敛到此单一数据源;
// 映射键与回退行为保持原样, 勿在"统一"时改动任何键或兜底文案(界面文案契约)。

// 审批类型 → 文案
function approvalTypeLabel(type) {
  const map = {
    claim: '捞取',
    transfer: '转移',
    extend: '延期',
    storage: '空间',
    customer_release: '客户释放',
    customer_transfer: '客户转交',
    return_to_pool: '退回公海'
  }
  return map[type] || (type ? String(type) : '-')
}

// 审批状态 → 文案(pending 统一为「待审批」,与 PC 端及本端页签一致,消灭「待提交」旧叫法)
function approvalStatusText(status) {
  const map = { pending: '待审批', PENDING: '待审批', WAITING: '待审批', '待审批': '待审批', reviewing: '审批中', approved: '已通过', rejected: '已驳回' }
  return map[status] || status || '-'
}

// 审批节点状态 → 文案(action=cancelled 的节点显示"已作废"; 兜底"待审批"而非 '-')
function nodeStatusText(status, action) {
  if (action === 'cancelled') return '已作废'
  const map = { pending: '待审批', PENDING: '待审批', WAITING: '待审批', '待审批': '待审批', approved: '已通过', rejected: '已驳回' }
  return map[status] || status || '待审批'
}

// 是否"待处理"状态(列表页 pendingStatus 与详情页 pending 同一实现)
function pendingStatus(status) {
  return ['pending', 'PENDING', 'WAITING', '待审批'].includes(status)
}

function normalizeApprovalNode(node) {
  return {
    ...node,
    statusLabel: nodeStatusText(node.status, node.action)
  }
}

// 公海捞取申请状态 → 文案(sea 列表页与捞取详情页共用)
function claimStatusText(status) {
  const map = {
    approved: '已通过',
    APPROVED: '已通过',
    rejected: '已驳回',
    REJECTED: '已驳回',
    // 【INV-11 术语修正】reviewing 统一「审批中」——与同文件 approvalStatusText 及 PC 2026-10-08 收口一致
    reviewing: '审批中',
    REVIEWING: '审批中',
    pending: '待审批',
    PENDING: '待审批',
    WAITING: '待审批',
    '待审批': '待审批'
  }
  return map[status] || status || '-'
}

module.exports = {
  approvalStatusText,
  approvalTypeLabel,
  claimStatusText,
  nodeStatusText,
  normalizeApprovalNode,
  pendingStatus
}
