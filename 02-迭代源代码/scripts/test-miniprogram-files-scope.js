#!/usr/bin/env node
// 文件库 scope 冒烟测试(2026-09-22 批次乙)
// 钉住: ①activeFileScope 四 tab 映射(公共=public/私人=private/四项服务=incentive, 分享给我走独立接口)
//      ②真实模式 filterFiles 不再按文件夹文字二次筛选(G2 修复); 演示模式保留文字法
//      ③上传按钮门控: 仅公共/私人 tab 显示(canUpload)

const assert = require('node:assert/strict')

let pageConfig = null
let demoMode = false
global.Page = (config) => { pageConfig = config }
global.getApp = () => ({ globalData: { demoMode, apiBaseUrl: '' } })
global.wx = { request: () => {}, getStorageSync: () => '', setStorageSync: () => {}, showToast: () => {}, showModal: () => {} }

const files = require('../aigicrm-miniprogram/pages/files/index.js')
assert.ok(pageConfig, 'page config captured')

function createPage() {
  const page = Object.create(pageConfig)
  page.data = JSON.parse(JSON.stringify(pageConfig.data))
  page.setData = function (patch) { Object.assign(this.data, patch) }
  return page
}

// ① 四 tab scope 映射
{
  const page = createPage()
  assert.equal((() => { page.data.activeTab = '公共'; return page.activeFileScope() })(), 'public')
  assert.equal((() => { page.data.activeTab = '私人'; return page.activeFileScope() })(), 'private')
  assert.equal((() => { page.data.activeTab = '四项服务'; return page.activeFileScope() })(), 'incentive')
  assert.equal((() => { page.data.activeTab = '分享给我'; return page.activeFileScope() })(), 'private', '分享给我不进 scope 参数(走独立接口)')
}

// ② 真实模式: folder='未分类' 的激励文件不被文字法筛空
{
  demoMode = false
  const page = createPage()
  page.data.activeTab = '四项服务'
  page.data.keyword = ''
  const rows = [{ name: 'a.png', folder: '未分类' }, { name: 'b.png', folder: '激励资料' }]
  const filtered = page.filterFiles(rows)
  assert.equal(filtered.length, 2, '真实模式信任服务端 scope, 不得按文件夹文字清空列表')
}

// ③ 演示模式: 文字法保留
{
  demoMode = true
  const page = createPage()
  page.data.activeTab = '四项服务'
  page.data.keyword = ''
  const rows = [{ name: 'a.png', folder: '未分类' }, { name: 'b.png', folder: '激励资料' }]
  const filtered = page.filterFiles(rows)
  assert.equal(filtered.length, 1, '演示模式保留 folder 文字法(无服务端)')
  assert.equal(filtered[0].folder, '激励资料')
}

// ④ 上传按钮门控在视图层(buildFileView 由源码静态断言: showUploadAction 含 tab 条件)
{
  const fs = require('node:fs')
  const src = fs.readFileSync(require.resolve('../aigicrm-miniprogram/pages/files/index.js'), 'utf-8')
  assert.ok(src.includes("data.activeTab === '公共' || data.activeTab === '私人'"), '上传按钮必须限定公共/私人 tab')
}

console.log('PASS: files scope smoke — 四tab映射/真实模式不做文字二次筛/演示模式保留/上传门控')
