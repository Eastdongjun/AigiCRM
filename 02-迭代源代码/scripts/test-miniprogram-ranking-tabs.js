#!/usr/bin/env node
// 排行页注册码并集逻辑·单元测试（TDD 2026-11-04: 先写本测试(红)→抽 utils/ranking-tabs.js(绿)→页面接线）
// 被测模块: aigicrm-miniprogram/utils/ranking-tabs.js —— 纯函数, 零 wx 依赖, 权限判断函数由调用方注入。
// 行为规格来源: 提交 2b129496(排行页权限绑定归位注册表) + 权限中心菜单注册表(学分排行=score:view 等)。
//
// 【已确认缝隙(2026-11-04 用户拍板 1-4, tdd 技能要求事先成文)】
//   1. 纯函数模块导出面(本文件)         —— 抓注册码绑定/any-of/默认页签/页签过滤; 抓不到页面接线
//   2. 页面级 automator 双角色实测       —— 抓接线类 bug(如裸引用 ReferenceError); 抓不到极端权限画像
//      (scripts: /tmp/aigicrm-itest/mp-role-ranking-test.js, 断言 canTabs/页签/数据)
//   3. 后端接口守卫矩阵 三账号×四接口   —— 抓守卫码与注册表漂移; 抓不到前端绑定
//   4. 双端绑定契约 check-dual-end-permission-binding-contract —— 抓死码/发明码/双端不一致/命名回潮
//      (含数据来源同源与命名统一 E 段); 抓不到运行时行为
const assert = require('assert')

const tabs = require('../aigicrm-miniprogram/utils/ranking-tabs.js')

// 模拟权限中心下发的账号权限画像（与测试环境实测一致）
const XZ_PROFILE = ['score:view', 'incentive:view', 'score:entry', 'customer:view', 'customer:add'] // 行政: 无 statistics:view
const SALES_PROFILE = ['score:view', 'statistics:view', 'incentive:view', 'customer:view', 'customer:add'] // 销售员: 三码全
const STATS_ONLY_PROFILE = ['statistics:view'] // 仅统计查看
const NO_PROFILE = ['customer:view'] // 与排行三码无关

const checker = (perms) => (code) => perms.includes(code)

// ---- 1. 注册码映射: 三榜各绑权限中心注册码, 不发明不特判 ----
assert.deepStrictEqual(tabs.MAIN_TAB_PERMISSIONS, {
  score: 'score:view',
  customer: 'statistics:view',
  incentive: 'incentive:view'
})
assert.deepStrictEqual(tabs.MAIN_TAB_ORDER, ['score', 'customer', 'incentive'])

// ---- 2. computeCanTabs: 按注入的权限判断函数逐码计算 ----
// 行政(score:view+incentive:view, 无 statistics:view)
assert.deepStrictEqual(tabs.computeCanTabs(checker(XZ_PROFILE), false), {
  score: true, customer: false, incentive: true
})

// 销售员(三码全)
assert.deepStrictEqual(tabs.computeCanTabs(checker(SALES_PROFILE), false), {
  score: true, customer: true, incentive: true
})
// 全无权
assert.deepStrictEqual(tabs.computeCanTabs(checker(NO_PROFILE), false), {
  score: false, customer: false, incentive: false
})
// demo 模式恒放开
assert.deepStrictEqual(tabs.computeCanTabs(checker(NO_PROFILE), true), {
  score: true, customer: true, incentive: true
})

// ---- 3. firstAllowedTab: 默认页签=第一个有权页签; 全无权=null(调用方弹回首页) ----
assert.strictEqual(tabs.firstAllowedTab(tabs.computeCanTabs(checker(XZ_PROFILE), false)), 'score')
assert.strictEqual(tabs.firstAllowedTab(tabs.computeCanTabs(checker(STATS_ONLY_PROFILE), false)), 'customer')
assert.strictEqual(tabs.firstAllowedTab({ score: false, customer: false, incentive: true }), 'incentive')
assert.strictEqual(tabs.firstAllowedTab({ score: false, customer: false, incentive: false }), null)

// ---- 4. visibleMainTabs: 无权页签不渲染, 顺序恒为 学分→客户→激励, 标题与 PC 菜单一致 ----
const xzTabs = tabs.visibleMainTabs(tabs.computeCanTabs(checker(XZ_PROFILE), false))
assert.deepStrictEqual(xzTabs, [
  { key: 'score', title: '学分排行' },
  { key: 'incentive', title: '激励排行' }
])
const salesTabs = tabs.visibleMainTabs(tabs.computeCanTabs(checker(SALES_PROFILE), false))
assert.deepStrictEqual(salesTabs.map((t) => t.title), ['学分排行', '客户排行', '激励排行'])
assert.deepStrictEqual(tabs.visibleMainTabs({ score: false, customer: false, incentive: false }), [])

console.log('PASS: ranking-tabs registry-union unit contract is aligned (registry codes / any-of entry / per-tab visibility).')
