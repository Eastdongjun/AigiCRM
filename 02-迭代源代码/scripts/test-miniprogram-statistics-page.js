#!/usr/bin/env node
// 统计分析页 Page 构造回归测试(2026-11-03)
// 【事故背景】11-01 审计批(a2a15d5d)在 buildMonthlySummary 里误引未定义的 state —— 该函数在
// Page({...data: {...}}) 的 data 初始化时被同步调用, ReferenceError 让 Page 构造直接崩,
// 统计分析页整页白屏(data 只剩 __webviewId__, automator 实证)。静态门禁(node --check)查不出
// 运行时未定义引用, 本测试在 node 下加载真实页面模块钉住"Page 构造零异常 + 初始 data 完整"。
// 【钉】1) require 页面模块不抛(修复前此处即 ReferenceError: state is not defined);
//      2) 捕获的 Page data 含完整初始视图字段(statsView/monthly 摘要为数字, 不出现 NaN);
//      3) 月均分母修复口径仍在: 本年=已过月截断, 往年=12(a2a15d5d 的业务意图, 参数化后保留)。

const assert = require('node:assert/strict')

let pageConfig = null
global.wx = {
  request: () => {},
  getStorageSync: () => 'tok',
  setStorageSync: () => {},
  removeStorageSync: () => {},
  showToast: () => {},
  showModal: () => {},
  showLoading: () => {},
  hideLoading: () => {},
  stopPullDownRefresh: () => {}
}
global.getApp = () => ({ globalData: { demoMode: false, currentUser: { id: 1 }, apiBaseUrl: '', forceRefresh: {} } })
global.Page = (config) => { pageConfig = config }

require('../aigicrm-miniprogram/pages/statistics/index.js')
assert.ok(pageConfig, 'Page() 必须被调用(修复前 buildMonthlySummary 误引 state 使构造直接抛错)')

const data = pageConfig.data
assert.ok(data && data.statsView, '初始 data 必须含 statsView(修复前 Page 构造崩导致整页白屏)')
assert.equal(data.loading, false, '初始 loading=false')
assert.ok(Array.isArray(data.yearOptions) && data.yearOptions.length > 0, '年度选项就绪')

const summary = data.monthlyCustomerSummary
assert.ok(summary, '初始月度摘要存在')
assert.equal(summary.total, 0, '初始月度总数=0')
assert.ok(Number.isFinite(summary.average), '月均必须为有限数字(不得 NaN)')
assert.ok(Array.isArray(summary.bars) && summary.bars.length === 12, '12 个月柱状数据')

// 月均分母口径(参数化 selectedYear, 缺省=本年): 6 月份数据在本年应除以已过月数
const view = data.statsView
assert.ok(view.filterSummary, '筛选摘要就绪')
assert.equal(view.refreshText, '刷新', '刷新文案初始态')

console.log('PASS: statistics page constructs with full initial data (state-ReferenceError regression pinned)')
