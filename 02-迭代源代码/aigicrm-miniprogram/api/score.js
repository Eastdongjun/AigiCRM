const request = require('../utils/request')

function getRanking(params) {
  return request({ url: '/api/score/ranking', data: params || {} })
}

module.exports = { getRanking }
