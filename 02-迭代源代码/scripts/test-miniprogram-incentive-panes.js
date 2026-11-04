#!/usr/bin/env node
// 激励统计页 pane 门禁·单元测试（TDD 2026-11-04: 先写本测试(红)→抽 utils/incentive-panes.js(绿)→页面接线）
// 被测模块: aigicrm-miniprogram/utils/incentive-panes.js —— 纯函数, 零 wx 依赖。
// 行为规格来源: 用户令"逐功能建码"——激励统计查看(incentive:stats:view)/激励排行查看(incentive:rank:view)
// 两码分别门禁 激励统计页的 统计/排行 两个 pane; 入口=注册码并集。
// 缝隙(2026-11-04 已确认 1-4 中的纯函数缝): 抓 pane 门禁与默认 pane 选择; 页面接线由 automator 缝覆盖。
const assert = require('assert')

const panes = require('../aigicrm-miniprogram/utils/incentive-panes.js')

const has = (codes) => (code) => codes.includes(code)

// ---- 1. 注册码映射 ----
assert.deepStrictEqual(panes.STATS_PANE_PERMISSIONS, {
  stats: 'incentive:stats:view',
  ranking: 'incentive:rank:view'
})
assert.deepStrictEqual(panes.STATS_PANE_ORDER, ['stats', 'ranking'])

// ---- 2. computeStatsPanes: 逐 pane 计算可见性 ----
// 行政画像（有 stats:view 有 rank:view —— 2026-11-04 回填后 incentive:view 持有者默认两码皆有）
assert.deepStrictEqual(panes.computeStatsPanes(has(['incentive:stats:view', 'incentive:rank:view', 'incentive:view']), false), {
  stats: true, ranking: true
})
// 仅排行（无统计查看）
assert.deepStrictEqual(panes.computeStatsPanes(has(['incentive:rank:view']), false), {
  stats: false, ranking: true
})
// 仅统计（无排行查看）
assert.deepStrictEqual(panes.computeStatsPanes(has(['incentive:stats:view']), false), {
  stats: true, ranking: false
})
// 全无
assert.deepStrictEqual(panes.computeStatsPanes(has([]), false), {
  stats: false, ranking: false
})
// demo 恒放开
assert.deepStrictEqual(panes.computeStatsPanes(has([]), true), {
  stats: true, ranking: true
})

// ---- 3. firstAllowedStatsPane: 默认 pane=第一个有权者; 全无=null(调用方弹回首页) ----
assert.strictEqual(panes.firstAllowedStatsPane(panes.computeStatsPanes(has(['incentive:stats:view']), false)), 'stats')
assert.strictEqual(panes.firstAllowedStatsPane(panes.computeStatsPanes(has(['incentive:rank:view']), false)), 'ranking')
assert.strictEqual(panes.firstAllowedStatsPane({ stats: false, ranking: false }), null)

// ---- 4. visibleStatsTabs: 无权页签不渲染, 顺序恒为 统计→排行, 标签与页面一致 ----
assert.deepStrictEqual(panes.visibleStatsTabs(panes.computeStatsPanes(has(['incentive:stats:view', 'incentive:rank:view']), false)).map((t) => t.key), ['stats', 'ranking'])
assert.deepStrictEqual(panes.visibleStatsTabs(panes.computeStatsPanes(has(['incentive:rank:view']), false)).map((t) => t.key), ['ranking'])
assert.deepStrictEqual(panes.visibleStatsTabs({ stats: true, ranking: true }).map((t) => t.label), ['统计', '排行'])

console.log('PASS: incentive stats/rank pane gating unit contract is aligned (registry codes / any-of entry / per-pane visibility).')
