const request = require('../utils/request')
const { setToken } = require('../utils/auth')
// 【E7】getGlobalData 收敛至 utils/app-context 单一出处(原复制移除, 行为不变)
const { getGlobalData } = require('../utils/app-context')

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

// 【体检 P1 2026-10-12】服务端注销: 后端按 jti 拉黑当前 token(防被盗 token 复用, 有效期内不再可用);
// PC 已接(store/user.js 先调 apiLogout), MP 此前只清本地未同步——两端行为分裂
function logout() {
  return request({ url: '/api/auth/logout', method: 'POST' })
}

function forgotPasswordLookup(data) { return request({ url: '/api/auth/forgot-password/lookup', method: 'POST', data }) }
function forgotPasswordSendCode(data) { return request({ url: '/api/auth/forgot-password/send-code', method: 'POST', data }) }
function forgotPasswordReset(data) { return request({ url: '/api/auth/forgot-password/reset', method: 'POST', data }) }

module.exports = {
  login,
  loadCurrentUser,
  switchGroup,
  logout,
  forgotPasswordLookup,
  forgotPasswordSendCode,
  forgotPasswordReset
}
