const assert = require('node:assert/strict')

const storage = new Map()
const redirects = []
let nextResponse
const app = { globalData: { apiBaseUrl: 'https://local.test', demoMode: false, currentUser: { id: 7 }, permissions: ['customer:view'], groupIds: [3], currentGroupId: 3 } }

global.getApp = () => app
global.wx = {
  getStorageSync: (key) => storage.get(key) || '',
  setStorageSync: (key, value) => storage.set(key, value),
  removeStorageSync: (key) => storage.delete(key),
  redirectTo: (options) => {
    redirects.push(options.url)
    options.complete && options.complete()
  },
  request: (options) => nextResponse(options)
}

function loadRequest() {
  const path = require.resolve('../aigicrm-miniprogram/utils/request')
  delete require.cache[path]
  return require(path)
}

// 【PR-04 令牌缓存隔离】固定环境档，令牌键派生确定性(避免依赖本机 env.local.js 是否存在)
const envPath = require.resolve('../aigicrm-miniprogram/config/env')
require.cache[envPath] = {
  id: envPath, filename: envPath, loaded: true,
  exports: { appName: 'AIGICRM', apiBaseUrl: 'https://local.test', demoMode: false }
}
const auth = require('../aigicrm-miniprogram/utils/auth')
const TOKEN_KEY = auth.tokenStorageKey()

async function expectReject(request, expected) {
  await assert.rejects(request, (error) => {
    assert.equal(error.message, expected.message)
    assert.equal(error.code, expected.code)
    return true
  })
}

async function main() {
  const request = loadRequest()

  nextResponse = (options) => options.success({ statusCode: 200, data: { code: 200, data: { id: 1 } } })
  assert.deepEqual(await request({ url: '/api/ping' }), { id: 1 })

  nextResponse = (options) => options.success({ statusCode: 401, data: { code: 401, message: '登录已过期' } })
  await expectReject(request({ url: '/api/private' }), { message: '登录已过期', code: 401 })
  assert.deepEqual(redirects, ['/pages/login/index'])
  assert.equal(storage.get(TOKEN_KEY), undefined)

  nextResponse = (options) => options.success({ statusCode: 500, data: { code: 500, message: '服务暂不可用' } })
  await expectReject(request({ url: '/api/failure' }), { message: '服务暂不可用', code: 500 })

  nextResponse = (options) => options.fail({ errMsg: 'request:fail timeout' })
  // 【2026-10-10 钉随代码】63d786fb 有意把网络错误 toast 英文技术串改中文友好文案
  await expectReject(request({ url: '/api/network' }), { message: '网络超时，请稍后重试', code: 'NETWORK_ERROR' })

  auth.setToken('session-token')
  auth.logout()
  assert.equal(storage.get(TOKEN_KEY), undefined)
  assert.equal(storage.get('AIGICRM_TOKEN'), undefined) // 旧单键不得复活
  assert.equal(app.globalData.currentUser, null)
  assert.deepEqual(app.globalData.permissions, [])
  assert.deepEqual(app.globalData.groupIds, [])
  assert.equal(app.globalData.currentGroupId, null)
  assert.equal(redirects.at(-1), '/pages/login/index')

  assert.equal(auth.requireLogin(), false)
  assert.equal(redirects.at(-1), '/pages/login/index')
  auth.setToken('fresh-session')
  assert.equal(auth.requireLogin(), true)

  // 【PR-04 令牌缓存隔离】换环境档(如移除 env.local.js / 切 trial)后：
  // 键随 apiBaseUrl 变化，另一环境的令牌不可见；切回原环境令牌仍在
  auth.setToken('env-a-token')
  require.cache[envPath].exports.apiBaseUrl = 'https://other.env'
  assert.equal(auth.tokenStorageKey(), 'AIGICRM_TOKEN::https://other.env')
  assert.equal(auth.getToken(), '')
  require.cache[envPath].exports.apiBaseUrl = 'https://local.test'
  assert.equal(auth.getToken(), 'env-a-token')
  auth.clearToken()
  assert.equal(auth.getToken(), '')

  console.log('PASS: miniprogram request failure, token expiry, logout cleanup, and env-isolated token keys are recoverable.')
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
