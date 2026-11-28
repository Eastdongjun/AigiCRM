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

// 【2026-11-16】删除书目(软删, fabushi:book:manage 门; 关联心得/书架/进度保留)
export function deleteFabushiBook(id) {
  return request.delete(`/fabushi/books/${id}`)
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

// 二期阅读链
// 【D7 切片 6】我的觉醒聚合状态（当日/本月/累计）
export function getFabushiAwakenStatus() {
  return request.get('/fabushi/awaken/status')
}

export function getFabushiShelf() {
  return request.get('/fabushi/shelf')
}

export function addFabushiShelf(bookId, data) {
  return request.post('/fabushi/shelf', { bookId, ...(data || {}) })
}

export function batchFabushiShelf(data) {
  return request.post('/fabushi/shelf/batch', data)
}

export function getFabushiShelfGroups() {
  return request.get('/fabushi/shelf/groups')
}

export function createFabushiShelfGroup(name) {
  return request.post('/fabushi/shelf/groups', { name })
}

export function renameFabushiShelfGroup(id, name) {
  return request.put(`/fabushi/shelf/groups/${id}`, { name })
}

export function deleteFabushiShelfGroup(id) {
  return request.delete(`/fabushi/shelf/groups/${id}`)
}

export function updateFabushiShelf(id, data) {
  return request.put(`/fabushi/shelf/${id}`, data)
}

export function removeFabushiShelf(id) {
  return request.delete(`/fabushi/shelf/${id}`)
}

export function getFabushiProgress(bookId) {
  return request.get(`/fabushi/progress/${bookId}`)
}

export function saveFabushiProgress(bookId, data) {
  return request.put(`/fabushi/progress/${bookId}`, data)
}

// 【D19/D24 切片 7】阅读器页码书签（按书记忆，本人维度）
export function getFabushiBookmarks(bookId) {
  return request.get('/fabushi/bookmarks', { params: { bookId } })
}
// 【PROTO-AUDIT-20261123 P9/D23】我的书摘：跨书本人书签（带书名）
export function getMyFabushiBookmarks() { return request.get('/fabushi/bookmarks/mine') }

export function addFabushiBookmark(data) {
  return request.post('/fabushi/bookmarks', data)
}
export function removeFabushiBookmark(id) {
  return request.delete(`/fabushi/bookmarks/${id}`)
}

/** 【D19 切片 7】管理员确认书目版式标记 text=文本版 / scan=影印版 */
export function updateFabushiBookLayout(id, layoutType) {
  return request.put(`/fabushi/books/${id}/layout`, { layoutType })
}

export function getFabushiRecords(month) {
  return request.get('/fabushi/records', { params: { month } })
}

export function getFabushiSettings() {
  return request.get('/fabushi/settings')
}

export function saveFabushiSettings(data) {
  return request.put('/fabushi/settings', data)
}

// 附件/章节/活动
export function addFabushiAttachment(postId, data) {
  return request.post(`/fabushi/posts/${postId}/attachments`, data)
}

export function removeFabushiAttachment(postId, attachmentId) {
  return request.delete(`/fabushi/posts/${postId}/attachments/${attachmentId}`)
}

export function getFabushiSections(bookId) {
  return request.get(`/fabushi/books/${bookId}/sections`)
}

export function createFabushiSection(bookId, data) {
  return request.post(`/fabushi/books/${bookId}/sections`, data)
}

export function deleteFabushiSection(bookId, id) {
  return request.delete(`/fabushi/books/${bookId}/sections/${id}`)
}

export function getFabushiActivities() {
  return request.get('/fabushi/activities')
}

export function joinFabushiActivity(id) {
  return request.post(`/fabushi/activities/${id}/join`)
}

export function createFabushiActivity(data) {
  return request.post('/fabushi/activities', data)
}

export function endFabushiActivity(id) {
  return request.put(`/fabushi/activities/${id}/end`)
}

// 【2026-11-16 P3】月度报告: 汇总+环比+每日趋势+部门进度+活跃榜(month=YYYY-MM, 缺省当月)
export function getFabushiMonthly(month) {
  return request.get('/fabushi/stats/monthly', { params: { month: month || undefined } })
}

// 【D1 2026-11-23】内容类型单源（与后端 FabushiScopeType 枚举/V2026.11.23.1 一致，禁止页面另写字面量）
export const FABUSHI_SCOPE_TYPES = [
  { value: 'songdu', label: '每日诵读' },
  { value: 'suishi', label: '每日随施' },
  { value: 'zaidu', label: '再读一本' }
]
export const fabushiScopeLabel = (value) => (FABUSHI_SCOPE_TYPES.find((t) => t.value === value) || {}).label || '内容'

// 【D13/D16 2026-11-23 计划切片 2】本人修改/删除 + 治理已删除
export function updateFabushiPost(id, data) { return request.put(`/fabushi/posts/${id}`, data) }
export function deleteFabushiPost(id) { return request.delete(`/fabushi/posts/${id}`) }
export function getDeletedFabushiPosts(params) { return request.get('/fabushi/posts/deleted', { params }) }
export function restoreDeletedFabushiPost(id) { return request.put(`/fabushi/posts/${id}/restore-deleted`) }

// 【D14 2026-11-23 计划切片 3】复诵（独立实体表；同人同帖可多次）
export function getFabushiRecitations(id) { return request.get(`/fabushi/posts/${id}/recitations`) }
export function addFabushiRecitation(id, data) { return request.post(`/fabushi/posts/${id}/recitations`, data) }
// 【FABUSHI-DELTA-20261128 R5】复诵移除（仅发起人本人，服务端强校验）
export function removeFabushiRecitation(postId, rid) { return request.delete(`/fabushi/posts/${postId}/recitations/${rid}`) }
