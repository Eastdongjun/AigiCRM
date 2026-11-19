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

// 二期阅读链
function getFabushiShelf() { return request({ url: '/api/fabushi/shelf' }) }
function addFabushiShelf(bookId) { return request({ url: '/api/fabushi/shelf', method: 'POST', data: { bookId } }) }
function updateFabushiShelf(id, data) { return request({ url: `/api/fabushi/shelf/${id}`, method: 'PUT', data }) }
function removeFabushiShelf(id) { return request({ url: `/api/fabushi/shelf/${id}`, method: 'DELETE' }) }
function getFabushiProgress(bookId) { return request({ url: `/api/fabushi/progress/${bookId}` }) }
function saveFabushiProgress(bookId, data) { return request({ url: `/api/fabushi/progress/${bookId}`, method: 'PUT', data }) }
function getFabushiRecords(month) { return request({ url: '/api/fabushi/records', data: { month } }) }
function getFabushiSettings() { return request({ url: '/api/fabushi/settings' }) }
function saveFabushiSettings(data) { return request({ url: '/api/fabushi/settings', method: 'PUT', data }) }
module.exports = Object.assign(module.exports, {
  getFabushiShelf, addFabushiShelf, updateFabushiShelf, removeFabushiShelf,
  getFabushiProgress, saveFabushiProgress, getFabushiRecords, getFabushiSettings, saveFabushiSettings,
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
function deleteFabushiSection(bookId, id) { return request({ url: `/api/fabushi/books/${bookId}/sections/${id}`, method: 'DELETE' }) }
function getFabushiActivities() { return request({ url: '/api/fabushi/activities' }) }
function joinFabushiActivity(id) { return request({ url: `/api/fabushi/activities/${id}/join`, method: 'POST' }) }
function createFabushiActivity(data) { return request({ url: '/api/fabushi/activities', method: 'POST', data }) }
function endFabushiActivity(id) { return request({ url: `/api/fabushi/activities/${id}/end`, method: 'PUT' }) }
module.exports = Object.assign(module.exports, { addFabushiAttachment, removeFabushiAttachment, getFabushiSections, createFabushiSection, deleteFabushiSection, getFabushiActivities, joinFabushiActivity, createFabushiActivity, endFabushiActivity })
