const request = require('../utils/request')

// 【法布施一期 2026-11-15】法布施模块 MP API(与 PC api/fabushi.js 同端点同码, 双端同源)
function getFabushiBooks(params) {
  return request({ url: '/api/fabushi/books', data: params || {} })
}

function submitFabushiBook(data) {
  return request({ url: '/api/fabushi/books', method: 'POST', data })
}

function getFabushiBook(id) {
  return request({ url: `/api/fabushi/books/${id}` })
}

function getFabushiPosts(params) {
  return request({ url: '/api/fabushi/posts', data: params || {} })
}

function getFabushiPostDetail(id) {
  return request({ url: `/api/fabushi/posts/${id}` })
}

function createFabushiPost(data) {
  return request({ url: '/api/fabushi/posts', method: 'POST', data })
}

function addFabushiComment(postId, payload) {
  // 【F2 2026-11-19】兼容旧 string(纯文字)与新对象 {content, type:'text'|'highlight'|'audio', audioFileId}
  const data = typeof payload === 'string' ? { content: payload } : (payload || {})
  return request({ url: `/api/fabushi/posts/${postId}/comments`, method: 'POST', data })
}

function getFabushiComments(postId) {
  return request({ url: `/api/fabushi/posts/${postId}/comments` })
}

function toggleFabushiLike(postId) {
  return request({ url: `/api/fabushi/posts/${postId}/like`, method: 'POST' })
}

function markFabushiRead(postId) {
  return request({ url: '/api/fabushi/reads/mark', method: 'POST', data: { postId } })
}

function getFabushiPersonalStats() {
  return request({ url: '/api/fabushi/stats/personal' })
}

module.exports = {
  getFabushiBooks, getFabushiBook, submitFabushiBook, getFabushiPosts, getFabushiPostDetail,
  createFabushiPost, addFabushiComment, getFabushiComments, toggleFabushiLike, markFabushiRead, getFabushiPersonalStats,
}

// 二期阅读链（D7/D20 切片 6）
function getFabushiAwakenStatus() { return request({ url: '/api/fabushi/awaken/status' }) }
function getFabushiShelf() { return request({ url: '/api/fabushi/shelf' }) }
function addFabushiShelf(bookId, data) { return request({ url: '/api/fabushi/shelf', method: 'POST', data: { bookId, ...(data || {}) } }) }
function updateFabushiShelf(id, data) { return request({ url: `/api/fabushi/shelf/${id}`, method: 'PUT', data }) }
function removeFabushiShelf(id) { return request({ url: `/api/fabushi/shelf/${id}`, method: 'DELETE' }) }
function batchFabushiShelf(data) { return request({ url: '/api/fabushi/shelf/batch', method: 'POST', data }) }
function getFabushiShelfGroups() { return request({ url: '/api/fabushi/shelf/groups' }) }
function createFabushiShelfGroup(name) { return request({ url: '/api/fabushi/shelf/groups', method: 'POST', data: { name } }) }
function renameFabushiShelfGroup(id, name) { return request({ url: `/api/fabushi/shelf/groups/${id}`, method: 'PUT', data: { name } }) }
function deleteFabushiShelfGroup(id) { return request({ url: `/api/fabushi/shelf/groups/${id}`, method: 'DELETE' }) }
function getFabushiProgress(bookId) { return request({ url: `/api/fabushi/progress/${bookId}` }) }
function saveFabushiProgress(bookId, data) { return request({ url: `/api/fabushi/progress/${bookId}`, method: 'PUT', data }) }
function getFabushiRecords(month) { return request({ url: '/api/fabushi/records', data: { month } }) }
function getFabushiSettings() { return request({ url: '/api/fabushi/settings' }) }
function saveFabushiSettings(data) { return request({ url: '/api/fabushi/settings', method: 'PUT', data }) }
function getFabushiReadToday() { return request({ url: '/api/fabushi/reads/today' }) }
function getFabushiUploadTicket() { return request({ url: '/api/fabushi/upload-ticket' }) }
function getFabushiReaderTicket() { return request({ url: '/api/fabushi/reader-ticket' }) }
module.exports = Object.assign(module.exports, {
  getFabushiAwakenStatus, getFabushiShelf, addFabushiShelf, updateFabushiShelf, removeFabushiShelf,
  batchFabushiShelf, getFabushiShelfGroups, createFabushiShelfGroup, renameFabushiShelfGroup, deleteFabushiShelfGroup,
  getFabushiProgress, saveFabushiProgress, getFabushiRecords, getFabushiSettings, saveFabushiSettings,
  getFabushiReadToday, getFabushiUploadTicket, getFabushiReaderTicket,
})

function getFabushiDashboard(period) {
  return request({ url: '/api/fabushi/stats/dashboard', data: { period: period || 'week' } })
}
module.exports = Object.assign(module.exports, { getFabushiDashboard })

// 【2026-11-16 P3】月度报告简报(month=YYYY-MM, 缺省当月): 汇总+环比+部门进度
function getFabushiMonthly(month) {
  return request({ url: '/api/fabushi/stats/monthly', data: { month: month || '' } })
}
module.exports = Object.assign(module.exports, { getFabushiMonthly })

function addFabushiAttachment(postId, data) { return request({ url: `/api/fabushi/posts/${postId}/attachments`, method: 'POST', data }) }
function removeFabushiAttachment(postId, attachmentId) { return request({ url: `/api/fabushi/posts/${postId}/attachments/${attachmentId}`, method: 'DELETE' }) }
function getFabushiSections(bookId) { return request({ url: `/api/fabushi/books/${bookId}/sections` }) }
function createFabushiSection(bookId, data) { return request({ url: `/api/fabushi/books/${bookId}/sections`, method: 'POST', data }) }
// 【PROTO-AUDIT-20261123 P8】发布时「自我录入章节」：fabushi:create 权限 + 同名幂等
function createFabushiSectionSelf(bookId, title) { return request({ url: `/api/fabushi/books/${bookId}/sections/self`, method: 'POST', data: { title } }) }
function deleteFabushiSection(bookId, id) { return request({ url: `/api/fabushi/books/${bookId}/sections/${id}`, method: 'DELETE' }) }
function getFabushiActivities() { return request({ url: '/api/fabushi/activities' }) }
function joinFabushiActivity(id) { return request({ url: `/api/fabushi/activities/${id}/join`, method: 'POST' }) }
function createFabushiActivity(data) { return request({ url: '/api/fabushi/activities', method: 'POST', data }) }
function endFabushiActivity(id) { return request({ url: `/api/fabushi/activities/${id}/end`, method: 'PUT' }) }
// 【D1 2026-11-23】内容类型单源（与后端 FabushiScopeType/V2026.11.23.1 一致，禁止页面另写字面量）
const FABUSHI_SCOPE_TYPES = [
  { value: 'songdu', label: '每日诵读' },
  { value: 'suishi', label: '每日随施' },
  { value: 'zaidu', label: '再读一本' }
]
function fabushiScopeLabel(value) { const t = FABUSHI_SCOPE_TYPES.find((x) => x.value === value); return t ? t.label : '内容' }
// 【D13 2026-11-23 计划切片 2】本人修改/删除（PUT 仅作者本人；软删不断文件）
function updateFabushiPost(id, data) { return request({ url: `/api/fabushi/posts/${id}`, method: 'PUT', data }) }
function deleteFabushiPost(id) { return request({ url: `/api/fabushi/posts/${id}`, method: 'DELETE' }) }
// 【D14 计划切片 3】复诵（独立实体表；同人同帖可多次）
function getFabushiRecitations(id) { return request({ url: `/api/fabushi/posts/${id}/recitations` }) }
function addFabushiRecitation(id, data) { return request({ url: `/api/fabushi/posts/${id}/recitations`, method: 'POST', data }) }
module.exports = Object.assign(module.exports, { addFabushiAttachment, removeFabushiAttachment, getFabushiSections, createFabushiSection, createFabushiSectionSelf, deleteFabushiSection, getFabushiActivities, joinFabushiActivity, createFabushiActivity, endFabushiActivity, FABUSHI_SCOPE_TYPES, fabushiScopeLabel, updateFabushiPost, deleteFabushiPost, getFabushiRecitations, addFabushiRecitation })
