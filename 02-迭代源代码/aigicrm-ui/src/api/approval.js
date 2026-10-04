import request from '@/utils/request'

export function getApprovalList(params) {
  return request.get('/approval/list', { params })
}

export function getApprovalDetail(id) {
  return request.get(`/approval/${id}`)
}

export function submitApproval(data) {
  return request.post('/approval/submit', data)
}

export function approve(id, data) {
  return request.post(`/approval/${id}/approve`, data)
}

export function reject(id, data) {
  return request.post(`/approval/${id}/reject`, data)
}

export function getApprovalStatistics() {
  return request.get('/approval/statistics')
}

// 【议题二.1 裁决】管理员手动转交审批节点(服务层二次校验 isAdmin)
export function transferApprovalNode(approvalId, nodeId, newApproverId) {
  return request.put(`/approval/${approvalId}/nodes/${nodeId}/transfer`, { newApproverId })
}

// ---- 审批流程配置 CRUD（对应后端 ApprovalConfigController，D3 2026-10-05 收口：页面不再裸调 URL） ----
export function getApprovalConfigList() {
  return request.get('/approval/config/list')
}
export function createApprovalConfig(data) {
  return request.post('/approval/config', data)
}
export function updateApprovalConfig(id, data) {
  return request.put(`/approval/config/${id}`, data)
}
export function deleteApprovalConfig(id) {
  return request.delete(`/approval/config/${id}`)
}
