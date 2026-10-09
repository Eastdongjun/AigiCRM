const { clearToken, getToken } = require('./auth')
// 【E7】getGlobalData/buildUrl 收敛至 app-context 单一出处(原两份复制移除, 行为不变)
const { getGlobalData, buildUrl } = require('./app-context')

let redirectingToLogin = false

function requestError(message, details) {
  const error = new Error(message || '请求失败')
  Object.assign(error, details)
  return error
}

// 【P8】判断当前栈顶页面是否已是登录页。登录接口对"密码错误/限流"同样回 HTTP 401
// (后端 AuthService 抛 BusinessException(401)), 若此时仍 redirectTo 登录页,
// 页面会被重载、用户已输入的账号密码被清空——登录页上的 401 只需清令牌, 不应再跳转。
function isOnLoginPage() {
  try {
    const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
    const current = pages && pages.length ? pages[pages.length - 1] : null
    return Boolean(current && current.route === 'pages/login/index')
  } catch (_) {
    return false
  }
}

function redirectToLoginOnce() {
  if (redirectingToLogin) return
  redirectingToLogin = true
  clearToken()
  // 已在登录页(如登录失败 401)时跳过跳转, 保住已输入内容; 其余 401 语义不变
  if (isOnLoginPage()) {
    redirectingToLogin = false
    return
  }
  wx.redirectTo({
    url: '/pages/login/index',
    complete() { redirectingToLogin = false }
  })
}

// 【M22 契约钉明】仅剔除 null/undefined 与空串三类"未填写"值(空串剔除是既有行为:
// 筛选/表单页大量传空串表示"该项不参与", 发给后端会改变查询/更新语义, 故保留);
// 不再剥字面 'null'/'undefined' 字符串——传输层静默改写请求体会吞掉合法的用户输入
// (如备注里真实写着 "null"), 脏值应在调用方源头修正, 而非此处统一篡改。
function sanitizeData(data) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) return data || {}
  return Object.keys(data).reduce((result, key) => {
    const value = data[key]
    if (value !== undefined && value !== null && value !== '') result[key] = value
    return result
  }, {})
}

function request(options) {
  const globalData = getGlobalData()
  const baseUrl = globalData.apiBaseUrl || ''
  const token = getToken()

  return new Promise((resolve, reject) => {
    wx.request({
      url: buildUrl(baseUrl, options.url),
      method: options.method || 'GET',
      // 【工作流复核确认修复】显式 30s 超时(原无配置, 弱网按微信默认 60s 挂起, 按钮 loading 转满全程); 与 PC 同口径
      timeout: options.timeout || 30000,
      data: sanitizeData(options.data),
      header: {
        'content-type': 'application/json',
        ...(token ? { Authorization: 'Bearer ' + token } : {}),
        // 调用方自定义头透传(如 PR-05 幂等键),同名时以调用方为准
        ...(options.header || {})
      },
      success(res) {
        const body = res.data || {}
        if (res.statusCode >= 200 && res.statusCode < 300 && (body.code === undefined || body.code === 200)) {
          resolve(body.data !== undefined ? body.data : body)
          return
        }
        // 【2026-09-22 语义修正】只有 401(未认证/失效)才踢登录; 403=已认证但无权,
        // 此前 403 也触发 redirectToLoginOnce, 会把"无权限动作"放大成"被登出"(reminder 页实测)
        if (res.statusCode === 401 || body.code === 401) {
          redirectToLoginOnce()
        }
        reject(requestError(body.message || '请求失败', {
          statusCode: res.statusCode,
          code: body.code,
          response: res
        }))
      },
      fail(error) {
        // 【工作流复核确认修复】 errMsg 中文化: 原样 toast 'request:fail timeout' 类英文技术串给用户
        const rawMsg = error.errMsg || '网络请求失败'
        let friendly = '网络请求失败，请检查网络后重试'
        if (rawMsg.indexOf('timeout') >= 0) friendly = '网络超时，请稍后重试'
        else if (rawMsg.indexOf('fail') >= 0 && rawMsg.indexOf('SSL') >= 0) friendly = '安全连接失败，请稍后重试'
        reject(requestError(friendly, {
          statusCode: 0,
          code: 'NETWORK_ERROR',
          cause: error
        }))
      }
    })
  })
}

/** 【M21】wx.uploadFile 响应统一判定: 与 request() 完全同口径的成功判定、
 *  数据解包与 401 踢登录处理。上传走 wx.uploadFile 无法直接复用 request 封装,
 *  此前 api/file.js 两处复制该判定(且 401 不踢登录), 现提取为共享实现防止口径漂移。
 *  注意: 响应体非 JSON(如网关错误页)按失败拒绝——与原 file.js 的 parse 抛错即拒绝
 *  语义一致, 只是错误消息改用 fallbackMessage 而非裸抛解析异常。 */
function settleUploadResponse(res, fallbackMessage) {
  let body = null
  try {
    body = JSON.parse(res.data || '{}')
  } catch (_) {
    body = null
  }
  if (body !== null && res.statusCode >= 200 && res.statusCode < 300 && (body.code === undefined || body.code === 200)) {
    return Promise.resolve(body.data !== undefined ? body.data : body)
  }
  if (res.statusCode === 401 || (body && body.code === 401)) {
    redirectToLoginOnce()
  }
  return Promise.reject(requestError((body && body.message) || fallbackMessage || '请求失败', {
    statusCode: res.statusCode,
    code: body ? body.code : undefined,
    response: res
  }))
}

module.exports = request
// request 以函数形态导出(既有调用方不变), 附加共享工具供上传类特殊调用方复用
module.exports.settleUploadResponse = settleUploadResponse
