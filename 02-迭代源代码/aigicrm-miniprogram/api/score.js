const request = require('../utils/request')

// 【E8 去重】此前同函数双名导出(getScoreRanking: getRanking, getRanking)。
// 全库调用方(statistics/ranking 两页)均以 getScoreRanking 引用, 别名 getRanking 无
// 调用方(注意与 api/incentive.js 的 getRanking 是不同模块不同接口), 故函数正名为
// getScoreRanking 并删除别名, 行为不变。
function getScoreRanking(params) {
  return request({ url: '/api/score/ranking', data: params || {} })
}

function getRankingStats(params) {
  return request({ url: '/api/score/ranking/stats', data: params || {} })
}

module.exports = { getScoreRanking, getRankingStats }
