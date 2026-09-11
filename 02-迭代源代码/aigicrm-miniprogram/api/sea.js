const request = require('../utils/request')

function getSeaList(params) {
  return request({ url: '/api/sea/list', data: params || {} })
}

function getSeaStatistics() {
  return request({ url: '/api/sea/statistics' })
}

function getVisibleSeaConfigs() {
  return request({ url: '/api/sea/configs/visible' })
}
function getClaimRecords(params) { return request({ url: '/api/sea/claim-records', data: params || {} }) }

module.exports = {
  getSeaList,
  getSeaStatistics,
  getVisibleSeaConfigs,
  getClaimRecords
}
