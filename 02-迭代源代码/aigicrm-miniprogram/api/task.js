const request = require('../utils/request')

// 【PR-10 2026-09-19】任务模块 API
function getTaskStatistics() { return request({ url: '/api/task/statistics' }) }
function getTaskList(params) { return request({ url: '/api/task/list', data: params || {} }) }

module.exports = { getTaskStatistics, getTaskList }
