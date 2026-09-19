const request = require('../utils/request')

function getIndicatorStats() {
  return request({ url: '/api/statistics/indicator-stats' })
}

function getDashboardData() {
  return request({ url: '/api/statistics/dashboard' })
}

function getAnalysis(params) {
  return request({ url: '/api/statistics/analysis', data: params || {} })
}

function getTeamActivities() {
  return request({ url: '/api/statistics/team-activities' })
}

function getDailyStats() {
  return request({ url: '/api/statistics/daily-stats' })
}

function getWeeklyStats() {
  return request({ url: '/api/statistics/weekly-stats' })
}

function getCustomerRankings(params) {
  return request({ url: '/api/statistics/customer-rankings', data: params || {} })
}

module.exports = { getIndicatorStats, getDashboardData, getAnalysis, getTeamActivities, getDailyStats, getWeeklyStats, getCustomerRankings }
