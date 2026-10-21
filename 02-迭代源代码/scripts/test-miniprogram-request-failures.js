const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

// 【Mimosa HIGH 2026-10-17】本脚本不调用任何 shell/子进程，不存在命令拼接面；
// 唯一的动态加载点（require(路径)）改为 realpath 白名单校验：只允许加载
// aigicrm-miniprogram 根目录内的模块，拒绝路径穿越/越仓 require。
const MINI_ROOT = fs.realpathSync(path.join(__dirname, '..', 'aigicrm-miniprogram'))
function requireWithinMiniRoot(specifier) {
  const resolved = fs.realpathSync(require.resolve(specifier))
  if (resolved !== MINI_ROOT && !resolved.startsWith(MINI_ROOT + path.sep)) {
    throw new Error(`拒绝加载小程序根目录之外的模块: ${resolved}`)
  }
  return require(resolved)
}

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
  const resolved = fs.realpathSync(require.resolve('../aigicrm-miniprogram/utils/request'))
  delete require.cache[resolved]
  return requireWithinMiniRoot('../aigicrm-miniprogram/utils/request')
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

  // 【C4 2026-10-21】空串语义: 默认(GET 查询参数与 body)仍剥空串——筛选/表单页大量传空串
  // 表示"该项不参与", 全局保留会改变查询/更新语义; keepEmpty 选项(opt-in)下只剥
  // undefined/null、保留空串——"空串=清空字段"的编辑调用处(联系人更新/激励编辑 PUT)
  // 依赖它把 '' 送到后端(后端非 null 即合并/写空串, 库内旧值才能真正清掉)。
  let captured = null
  nextResponse = (options) => { captured = options; options.success({ statusCode: 200, data: { code: 200, data: { ok: true } } }) }

  await request({ url: '/api/default-body', method: 'PUT', data: { name: '张三', position: '', phone: '', ext: null, count: 0, flag: false } })
  assert.deepEqual(captured.data, { name: '张三', count: 0, flag: false }, '默认 body: 空串与 null 一并剥除, 0/false 不受影响')

  await request({ url: '/api/keep-empty', method: 'PUT', data: { name: '张三', position: '', phone: '', ext: null, count: 0 }, keepEmpty: true })
  assert.deepEqual(captured.data, { name: '张三', position: '', phone: '', count: 0 }, 'keepEmpty body: 空串保留(清空字段唯一表达), null 仍剥')

  await request({ url: '/api/query', data: { keyword: '', page: 2 } })
  assert.deepEqual(captured.data, { page: 2 }, 'GET 查询参数默认仍剥空串(既有筛选语义不变)')

  // 调用方接线: 联系人更新必带 keepEmpty(空串清空职位/电话/备注), 联系人新建不带;
  // 激励编辑 keepEmpty 显式开启、缺省不发(默认剥空串)。
  const customerApi = requireWithinMiniRoot('../aigicrm-miniprogram/api/customer')
  const incentiveApi = requireWithinMiniRoot('../aigicrm-miniprogram/api/incentive')

  await customerApi.updateCustomerContact(1, 2, { name: '张三', position: '', phone: '', remark: '' })
  assert.equal(captured.method, 'PUT')
  assert.deepEqual(captured.data, { name: '张三', position: '', phone: '', remark: '' }, '联系人更新: 清空字段以空串落请求体')

  await customerApi.addCustomerContact(1, { name: '张三', position: '', remark: '' })
  assert.deepEqual(captured.data, { name: '张三' }, '联系人新建: 维持默认剥空串语义')

  await incentiveApi.updateRecord(5, { customerId: 1, remark: '', relatedProducts: '' }, { keepEmpty: true })
  assert.equal(captured.method, 'PUT')
  assert.deepEqual(captured.data, { customerId: 1, remark: '', relatedProducts: '' }, '激励编辑 keepEmpty: 清空字段保留空串')

  await incentiveApi.updateRecord(5, { customerId: 1, remark: '' })
  assert.deepEqual(captured.data, { customerId: 1 }, '激励编辑缺省选项: 仍剥空串(兼容旧调用)')

  console.log('PASS: miniprogram request failure, token expiry, logout cleanup, env-isolated token keys, and C4 empty-string clear semantics are recoverable.')
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
