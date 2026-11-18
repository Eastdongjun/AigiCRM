/**
 * 法布施小程序「撤权 → 入口/按钮消失 → 恢复」验收（DevTools automator）
 *
 * 使用隔离的临时角色/账号（zz_mp_probe / 角色 102），不触碰真实账号与真实角色。
 * 撤权走权限中心真实接口 PUT /api/system/roles/{id}（会 bump X-Perm-Epoch），
 * 再让小程序 onShow → refreshPermissions 拉新 /auth/info，验证入口/发布按钮消失。
 * 结束后恢复角色原始 menuIds（临时角色/账号由调用方清理）。
 *
 * 凭据：~/.aigicrm-deploy.env 的 HEALTH_CHECK_USER/HEALTH_CHECK_PASS（admin）
 *       MP_REVOKE_USER / MP_REVOKE_PASS 为临时账号（默认 zz_mp_probe + admin 密码）
 */
import { createRequire } from 'node:module'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

const require = createRequire(import.meta.url)
const automator = require('miniprogram-automator')

const BASE = 'http://111.229.37.220'
const WS = process.env.WECHAT_AUTOMATOR_WS || 'ws://127.0.0.1:9424'
const ROLE_ID = Number(process.env.MP_REVOKE_ROLE_ID || 102)
const OUT = path.resolve('test-results/fabushi')
const EVIDENCE = path.join(OUT, 'mp-revoke-evidence-20261116.json')
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

function loadEnvFile(p) {
  if (!fs.existsSync(p)) return {}
  const out = {}
  for (const line of fs.readFileSync(p, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$/)
    if (m) out[m[1]] = m[2].replace(/^["']|["']$/g, '')
  }
  return out
}
const envFile = loadEnvFile(path.join(os.homedir(), '.aigicrm-deploy.env'))
const ADMIN_USER = process.env.MP_ADMIN_USER || envFile.HEALTH_CHECK_USER
const ADMIN_PASS = process.env.MP_ADMIN_PASS || envFile.HEALTH_CHECK_PASS
const PROBE_USER = process.env.MP_REVOKE_USER || 'zz_mp_probe'
const PROBE_PASS = process.env.MP_REVOKE_PASS || ADMIN_PASS
if (!ADMIN_USER || !ADMIN_PASS) {
  console.error('BLOCKED: 缺少 admin 凭据')
  process.exit(2)
}

const evidence = {
  timestamp: new Date().toISOString(),
  environment: BASE,
  roleId: ROLE_ID,
  account: PROBE_USER,
  steps: {},
  errors: []
}
function record(name, ok, detail) {
  evidence.steps[name] = { ok, ...detail }
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}`, detail && Object.keys(detail).length ? JSON.stringify(detail) : '')
  if (!ok) throw new Error(`step failed: ${name}`)
}

let adminToken = ''
async function api(method, url, body) {
  const res = await fetch(BASE + url, {
    method,
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + adminToken },
    body: body ? JSON.stringify(body) : undefined
  })
  const json = await res.json().catch(() => ({}))
  if (!res.ok || (json.code !== undefined && json.code !== 200)) {
    throw new Error(`${method} ${url} http=${res.status} code=${json.code} msg=${json.message}`)
  }
  return json.data
}
function flattenMenus(nodes, out = []) {
  for (const n of nodes || []) {
    out.push(n)
    flattenMenus(n.children, out)
  }
  return out
}

let mini
try {
  // 1) admin 登录 + 读取角色原始菜单集
  const login = await fetch(BASE + '/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: ADMIN_USER, password: ADMIN_PASS })
  }).then((r) => r.json())
  if (login.code !== 200 || !login.data || !login.data.token) throw new Error('admin 登录失败')
  adminToken = login.data.token
  const role = await api('GET', `/api/system/roles/${ROLE_ID}`)
  const originalMenuIds = [...(role.menuIds || [])]
  const menus = flattenMenus(await api('GET', '/api/system/menus/tree'))
  const revokeMenuIds = menus.filter((m) => m.permission === 'fabushi:view' || m.permission === 'fabushi:create').map((m) => m.id)
  record('role-read', originalMenuIds.length > 0 && revokeMenuIds.length >= 2, {
    roleId: ROLE_ID, menuCount: originalMenuIds.length, revokeMenuIds
  })

  // 2) 小程序：切到隔离账号
  mini = await automator.connect({ wsEndpoint: WS })
  mini.on('exception', (e) => evidence.errors.push(String(e && e.message ? e.message : e).slice(0, 200)))
  await mini.evaluate(() => {
    try { wx.removeStorageSync('AIGICRM_TOKEN::http://111.229.37.220') } catch (e) {}
    const g = getApp().globalData
    g.currentUser = null
    g.permissions = []
  })
  const loginPage = await mini.reLaunch('/pages/login/index')
  await loginPage.setData({ username: PROBE_USER, password: PROBE_PASS, agreed: true })
  await loginPage.callMethod('submit')
  let state = null
  for (let i = 0; i < 20; i++) {
    await sleep(1000)
    state = await mini.evaluate(() => {
      const g = getApp().globalData
      return { user: g.currentUser && g.currentUser.username, permissions: Array.isArray(g.permissions) ? g.permissions : [] }
    })
    if (state.user === PROBE_USER && state.permissions.length) break
  }
  record('probe-login', state && state.user === PROBE_USER && state.permissions.includes('fabushi:view') && state.permissions.includes('fabushi:create'), {
    user: state && state.user, permCount: state ? state.permissions.length : 0,
    fabushi: state ? state.permissions.filter((p) => p.startsWith('fabushi')) : []
  })

  const workbench = async () => {
    const p = await mini.reLaunch('/pages/fabushi/index')
    await sleep(2500)
    const d = await p.data().catch(() => ({}))
    const entries = await p.$$('.fb-entry').catch(() => [])
    return { path: p.path, entries: entries.length, canCreate: d.canCreate }
  }
  let wb = await workbench()
  record('visible-before-revoke', wb.path === 'pages/fabushi/index' && wb.entries >= 3 && wb.canCreate === true, wb)

  const appGroups = async () => {
    const p = await mini.switchTab('/pages/app/index')
    await sleep(2500)
    const d = await p.data().catch(() => ({}))
    return ((d.appView && d.appView.groups) || []).map((g) => g.key)
  }
  let groups = await appGroups()
  record('app-center-before', groups.includes('fabushi'), { groups })

  // 3) 撤权：移除 fabushi:view + fabushi:create
  const reduced = originalMenuIds.filter((id) => !revokeMenuIds.includes(id))
  await api('PUT', `/api/system/roles/${ROLE_ID}`, { menuIds: reduced })
  await sleep(2500)
  record('api-revoke', true, { before: originalMenuIds.length, after: reduced.length, removed: revokeMenuIds.length })

  // 4) 小程序刷新权限（onShow → refreshPermissions）
  await mini.evaluate(() => { try { getApp().onShow() } catch (e) {} })
  await sleep(6000)
  state = await mini.evaluate(() => {
    const g = getApp().globalData
    return { permissions: Array.isArray(g.permissions) ? g.permissions : [] }
  })
  record('perms-refreshed', !state.permissions.includes('fabushi:view') && !state.permissions.includes('fabushi:create'), {
    permCount: state.permissions.length, fabushi: state.permissions.filter((p) => p.startsWith('fabushi'))
  })
  wb = await workbench()
  groups = await appGroups()
  record('hidden-after-revoke', !groups.includes('fabushi') && (wb.path !== 'pages/fabushi/index' || (wb.entries < 3 && wb.canCreate === false)), { ...wb, groups })
  await mini.screenshot({ path: path.join(OUT, 'mp-revoked.png') })

  // 5) 恢复授权
  await api('PUT', `/api/system/roles/${ROLE_ID}`, { menuIds: originalMenuIds })
  await sleep(2500)
  await mini.evaluate(() => { try { getApp().onShow() } catch (e) {} })
  await sleep(6000)
  state = await mini.evaluate(() => {
    const g = getApp().globalData
    return { permissions: Array.isArray(g.permissions) ? g.permissions : [] }
  })
  record('perms-restored', state.permissions.includes('fabushi:view') && state.permissions.includes('fabushi:create'), {
    fabushi: state.permissions.filter((p) => p.startsWith('fabushi'))
  })
  wb = await workbench()
  groups = await appGroups()
  record('visible-after-restore', groups.includes('fabushi') && wb.path === 'pages/fabushi/index' && wb.entries >= 3 && wb.canCreate === true, { ...wb, groups })

  evidence.result = 'PASS'
} catch (e) {
  evidence.result = 'FAIL'
  evidence.failure = String(e && e.message ? e.message : e)
  console.error('FAILED:', evidence.failure)
} finally {
  try { if (mini) mini.disconnect() } catch (_) {}
  fs.mkdirSync(OUT, { recursive: true })
  fs.writeFileSync(EVIDENCE, JSON.stringify(evidence, null, 2))
  console.log('evidence:', EVIDENCE, 'result:', evidence.result)
  process.exit(evidence.result === 'PASS' ? 0 : 1)
}
