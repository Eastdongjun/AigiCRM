#!/usr/bin/env node
// 【2026-11-15】小程序权限刷新/缓存清理/TabBar 权限回归（Node 桩，不依赖 DevTools）
const assert = require('node:assert/strict')
const path = require('node:path')

let unlinked = []
global.wx = {
  env: { USER_DATA_PATH: '/tmp/aigicrm-mp-cache-test' },
  getFileSystemManager() {
    return {
      readdirSync() { return ['cached.bin', 'index.json'] },
      unlinkSync(name) { unlinked.push(name) },
      writeFileSync() {},
      readFileSync() { throw new Error('empty') },
      accessSync() {},
      mkdirSync() {}
    }
  }
}
const page = { onPermissionChanged() { page.called = true } }
global.getCurrentPages = () => [page]
const app = { globalData: { demoMode: false, currentUser: { id: 1, username: 'u' }, permissions: ['customer:view'], apiBaseUrl: 'http://test' } }
global.getApp = () => app

const pr = require(path.resolve(__dirname, '../aigicrm-miniprogram/utils/permission-refresh.js'))
const before = pr.getVersion()
pr.markChanged('test')
assert.equal(pr.getVersion(), before + 1, 'markChanged must bump version')
assert.equal(page.called, true, 'markChanged must notify current page')
assert.ok(unlinked.some((n) => n.includes('cached.bin')), 'markChanged must clear file cache')

// TabBar（【F-MP1 2026-11-18】菜单收敛 9e6a9e1d 后 tab 集=home/四项服务(incentive:view)/
// 法布施(fabushi:view)/应用/我的——files tab 已退役。断言改门控 tab 的 disabled 语义:
// 无码 disabled / 授码 enabled / 无 permission 恒 enabled。
let componentConfig = null
global.Component = (cfg) => { componentConfig = cfg }
require(path.resolve(__dirname, '../aigicrm-miniprogram/custom-tab-bar/index.js'))
assert.ok(componentConfig, 'tabbar component captured')
const comp = { data: { tabs: [] }, setData(patch) { Object.assign(this.data, patch) } }
componentConfig.methods.refreshTabs.call(comp)
let gated = comp.data.tabs.filter((t) => t.permission)
assert.equal(gated.length, 2, '当前导航恰两个门控 tab(service/fabushi)')
let serviceTab = comp.data.tabs.find((t) => t.key === 'service')
let fabushiTab = comp.data.tabs.find((t) => t.key === 'fabushi')
assert.ok(serviceTab && fabushiTab, 'service/fabushi tab 在位')
assert.equal(serviceTab.disabled, true, 'service tab 无 incentive:view 时 disabled')
assert.equal(fabushiTab.disabled, true, 'fabushi tab 无 fabushi:view 时 disabled')
assert.ok(comp.data.tabs.filter((t) => !t.permission).every((t) => !t.disabled),
    '无 permission tab(home/apps/profile) 恒 enabled')
app.globalData.permissions = ['incentive:view', 'fabushi:view']
componentConfig.methods.refreshTabs.call(comp)
serviceTab = comp.data.tabs.find((t) => t.key === 'service')
fabushiTab = comp.data.tabs.find((t) => t.key === 'fabushi')
assert.equal(serviceTab.disabled, false, 'service tab 持 incentive:view 后 enabled')
assert.equal(fabushiTab.disabled, false, 'fabushi tab 持 fabushi:view 后 enabled')
// 授权变更后 markChanged 应刷新 tab 状态(权限刷新总线的 TabBar 联动)
app.globalData.permissions = []
componentConfig.methods.refreshTabs.call(comp)
assert.equal(comp.data.tabs.find((t) => t.key === 'service').disabled, true,
    '撤码后回 disabled(权限回收即时生效语义)')

// admin 前端兜底：无任何 permissions 时 hasPermission 也应为 true；普通用户仍按码
const auth = require(path.resolve(__dirname, '../aigicrm-miniprogram/utils/auth.js'))
app.globalData.currentUser = { id: 1, username: 'admin', roles: ['admin'] }
app.globalData.permissions = []
assert.equal(auth.hasPermission('customer:view'), true, 'admin must bypass permission codes in UI')
app.globalData.currentUser = { id: 2, username: 'u', roles: ['06'] }
assert.equal(auth.hasPermission('customer:view'), false, 'non-admin must require code')

console.log('PASS: 权限刷新总线（版本递增/页面通知/文件缓存清理）、admin 兜底与 TabBar 权限门禁')
