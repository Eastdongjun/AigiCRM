import request from '@/utils/request'

export function login(data) {
  return request.post('/auth/login', data)
}

export function getUserInfo() {
  return request.get('/auth/info')
}

export function logout() {
  return request.post('/auth/logout')
}

/** 【批④ 2026-08-10 多集团会话】切换当前集团(重签 token) */
export function switchGroup(groupId) {
  return request.post('/auth/switch-group', { groupId })
}

export function forgotPasswordLookup(data) {
  return request.post('/auth/forgot-password/lookup', data)
}

export function forgotPasswordSendCode(data) {
  return request.post('/auth/forgot-password/send-code', data)
}

export function forgotPasswordReset(data) {
  return request.post('/auth/forgot-password/reset', data)
}
