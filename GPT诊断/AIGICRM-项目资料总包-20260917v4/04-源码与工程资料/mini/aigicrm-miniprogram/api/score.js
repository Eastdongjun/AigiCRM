const request = require('../utils/request')

function getRanking(params) {
  return request({ url: '/api/score/ranking', data: params || {} })
}

function getRankingStats(params) {
  return request({ url: '/api/score/ranking/stats', data: params || {} })
}

module.exports = { getScoreRanking: getRanking, getRanking, getRankingStats }
