#!/usr/bin/env node
// 【C8 2026-10-22】数据范围文案钉: /api/auth/info 只返回 dataScope 代码(UserInfoResponse:18),
// MP 两页却读不存在的 dataScopeName → 个人页"数据范围"与"数据权限说明"页永远显示兜底文案。
// 修复口径: utils/label-maps.js 提供 dataScopeLabel(与 PC layout/index.vue scopeMap 同源),
// 未知/缺失代码回退「按权限范围」(MP 既有界面文案契约, 见 label-maps 头注)。
const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')

const ROOT = join(__dirname, '..')
const maps = require('../aigicrm-miniprogram/utils/label-maps.js')

let failed = 0
function check(name, fn) {
  try {
    fn()
    console.log(`ok - ${name}`)
  } catch (e) {
    failed += 1
    console.error(`FAIL - ${name}\n  ${e.message}`)
  }
}

check('dataScopeLabel 已导出且为函数', () => {
  assert.equal(typeof maps.dataScopeLabel, 'function')
})

check('十个档位映射与 PC layout scopeMap 逐键同源', () => {
  const pc = {
    self: '仅本人', team: '本人及下级', dept: '本部门及以下', office: '本办事处及以下',
    region: '本大区及以下', all: '全部数据', custom: '自定义',
    org: '本部门', org_and_children: '本部门及子级', dept_and_children: '本部门及子级',
  }
  for (const [code, label] of Object.entries(pc)) {
    assert.equal(maps.dataScopeLabel(code), label, `档位 ${code} 应为「${label}」`)
  }
})

check('未知/缺失代码回退「按权限范围」(MP 既有文案契约)', () => {
  assert.equal(maps.dataScopeLabel('weird_scope'), '按权限范围')
  assert.equal(maps.dataScopeLabel(undefined), '按权限范围')
  assert.equal(maps.dataScopeLabel(''), '按权限范围')
  assert.equal(maps.dataScopeLabel(null), '按权限范围')
})

check('两页不再读不存在的 dataScopeName', () => {
  for (const page of ['pages/profile/index.js', 'pages/profile/scope/index.js']) {
    const src = readFileSync(join(ROOT, 'aigicrm-miniprogram', page), 'utf-8')
    assert.ok(!src.includes('dataScopeName'), `${page} 不得再读 dataScopeName(后端无此字段)`)
  }
})

check('两页均以 dataScopeLabel(user.dataScope) 取文案', () => {
  for (const page of ['pages/profile/index.js', 'pages/profile/scope/index.js']) {
    const src = readFileSync(join(ROOT, 'aigicrm-miniprogram', page), 'utf-8')
    assert.ok(src.includes('dataScopeLabel(user.dataScope)'), `${page} 应经 dataScopeLabel(user.dataScope) 取文案`)
  }
})

if (failed > 0) {
  console.error(`\n${failed} 项未过`)
  process.exit(1)
}
console.log('PASS: 数据范围文案映射与两页接入成立')
