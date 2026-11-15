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

function addFabushiComment(postId, content) {
  return request({ url: `/api/fabushi/posts/${postId}/comments`, method: 'POST', data: { content } })
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
