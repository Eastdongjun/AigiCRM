const request = require('../utils/request')
const { setToken } = require('../utils/auth')

function getGlobalData() {
  const app = typeof getApp === 'function' ? getApp() : null
  if (!app) return {}
  if (!app.globalData) app.globalData = {}
  return app.globalData
}

function login(data) {
  return request({ url: '/api/auth/login', method: 'POST', data })
}

function loadCurrentUser() {
  return request({ url: '/api/auth/info' }).then((user) => {
    const globalData = getGlobalData()
    globalData.currentUser = user || null
    globalData.permissions = user && Array.isArray(user.permissions) ? user.permissions : []
    globalData.groupIds = user && Array.isArray(user.groupIds) ? user.groupIds : []
    globalData.currentGroupId = user ? user.currentGroupId : null
    return user
  })
}

function switchGroup(groupId) {
  return request({ url: '/api/auth/switch-group', method: 'POST', data: { groupId } }).then((response) => {
    const globalData = getGlobalData()
    if (response && response.token) setToken(response.token)
    globalData.groupIds = response.groupIds || []
    globalData.currentGroupId = response.currentGroupId || null
    return response
  })
}

function forgotPasswordLookup(data) { return request({ url: '/api/auth/forgot-password/lookup', method: 'POST', data }) }
function forgotPasswordSendCode(data) { return request({ url: '/api/auth/forgot-password/send-code', method: 'POST', data }) }
function forgotPasswordReset(data) { return request({ url: '/api/auth/forgot-password/reset', method: 'POST', data }) }

module.exports = {
  login,
  loadCurrentUser,
  switchGroup,
  forgotPasswordLookup,
  forgotPasswordSendCode,
  forgotPasswordReset
}
