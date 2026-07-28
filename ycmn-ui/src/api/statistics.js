import request from '@/utils/request'

export function getDashboardData() {
  return request.get('/statistics/dashboard')
}

export function getTeamActivities() {
  return request.get('/statistics/team-activities')
}

export function getWeeklyStats() {
  return request.get('/statistics/weekly-stats')
}

export function getAnalysis(params) {
  return request.get('/statistics/analysis', { params })
}

export function getIndicatorStats() {
  return request.get('/statistics/indicator-stats')
}
