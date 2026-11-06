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

// 【2026-11-06 用户令"学分明细要看真明细"】评分网格(按周期): items=学分项全集,
// scores="itemId_userId"→得分——明细页据此拼单人逐项得分, 复用现有端点零后端改动
function getScoreGrid(params) {
  return request({ url: '/api/score/grid', data: params || {} })
}

module.exports = { getScoreRanking, getRankingStats, getScoreGrid }
