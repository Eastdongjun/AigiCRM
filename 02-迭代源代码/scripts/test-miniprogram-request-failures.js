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
  assert.equal(storage.get('AIGICRM_TOKEN'), undefined)

  nextResponse = (options) => options.success({ statusCode: 500, data: { code: 500, message: '服务暂不可用' } })
  await expectReject(request({ url: '/api/failure' }), { message: '服务暂不可用', code: 500 })

  nextResponse = (options) => options.fail({ errMsg: 'request:fail timeout' })
  await expectReject(request({ url: '/api/network' }), { message: 'request:fail timeout', code: 'NETWORK_ERROR' })

  const auth = require('../aigicrm-miniprogram/utils/auth')
  auth.setToken('session-token')
  auth.logout()
  assert.equal(storage.get('AIGICRM_TOKEN'), undefined)
  assert.equal(app.globalData.currentUser, null)
  assert.deepEqual(app.globalData.permissions, [])
  assert.deepEqual(app.globalData.groupIds, [])
  assert.equal(app.globalData.currentGroupId, null)
  assert.equal(redirects.at(-1), '/pages/login/index')

  assert.equal(auth.requireLogin(), false)
  assert.equal(redirects.at(-1), '/pages/login/index')
  auth.setToken('fresh-session')
  assert.equal(auth.requireLogin(), true)

  console.log('PASS: miniprogram request failure, token expiry, and logout cleanup paths are recoverable.')
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
