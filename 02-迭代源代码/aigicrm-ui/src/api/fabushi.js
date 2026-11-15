import request from '@/utils/request'

// 【法布施一期 2026-11-15】法布施模块 API(端点=docs/api/api-list.json 契约源)
export function getFabushiBooks(params) {
  return request.get('/fabushi/books', { params })
}

export function getFabushiBook(id) {
  return request.get(`/fabushi/books/${id}`)
}

export function getPendingFabushiBooks() {
  return request.get('/fabushi/books/pending')
}

export function submitFabushiBook(data) {
  return request.post('/fabushi/books', data)
}

export function approveFabushiBook(id) {
  return request.put(`/fabushi/books/${id}/approve`)
}

export function rejectFabushiBook(id, reason) {
  return request.put(`/fabushi/books/${id}/reject`, { reason })
}

export function disableFabushiBook(id) {
  return request.put(`/fabushi/books/${id}/disable`)
}

export function attachBookPdf(id, fileId) {
  return request.post(`/fabushi/books/${id}/pdf`, { fileId })
}

export function getFabushiPosts(params) {
  return request.get('/fabushi/posts', { params })
}

export function getFabushiPostDetail(id) {
  return request.get(`/fabushi/posts/${id}`)
}

export function createFabushiPost(data) {
  return request.post('/fabushi/posts', data)
}

export function restoreFabushiPost(id) {
  return request.put(`/fabushi/posts/${id}/restore`)
}

export function offlineFabushiPost(id, reason) {
  return request.put(`/fabushi/posts/${id}/offline`, { reason })
}

export function addFabushiComment(postId, content) {
  return request.post(`/fabushi/posts/${postId}/comments`, { content })
}

export function getFabushiComments(postId) {
  return request.get(`/fabushi/posts/${postId}/comments`)
}

export function toggleFabushiLike(postId) {
  return request.post(`/fabushi/posts/${postId}/like`)
}

export function markFabushiRead(postId) {
  return request.post('/fabushi/reads/mark', { postId })
}

export function getFabushiDashboard(period = 'week') {
  return request.get('/fabushi/stats/dashboard', { params: { period } })
}

export function getFabushiTeamStats() {
  return request.get('/fabushi/stats/team')
}

export function getFabushiPersonalStats() {
  return request.get('/fabushi/stats/personal')
}
