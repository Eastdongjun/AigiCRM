#!/usr/bin/env node
// 【F9 2026-10-13】客情字典分值口径钉: MP 档位下标必须来自 dictValue(对齐 PC customer-fields.js)。
// 钉住三个会让旧实现(按 sortOrder 排序+数组下标当分值)静默存错档的场景:
// 1) 管理员把 sort_order 改乱 → 输出仍按 dictValue 0-3;
// 2) 停用中间档(status=0) → 缺档用兜底文案补位, 后续档下标不前移;
// 3) 行缺失(dictValue 2 无行) → 同 2 补位; 脏值(负数/非整数/>3)丢弃; 空/全停用返回 null。

const assert = require('node:assert/strict')

// shared.js 是纯常量/工具模块(仅 navigateBackWithFallback 触 wx, 不在本测试路径), node 直 require
const { relationshipOptionsFromDict, RELATIONSHIP_OPTIONS } = require('../aigicrm-miniprogram/pages/customer/shared.js')

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

check('正常四行(sortOrder 乱序) → 按 dictValue 0-3 输出', () => {
  const rows = [
    { dictValue: '2', dictLabel: '感情商务', sortOrder: 3, status: 1 },
    { dictValue: '0', dictLabel: '商务少', sortOrder: 1, status: 1 },
    { dictValue: '3', dictLabel: '多样性', sortOrder: 4, status: 1 },
    { dictValue: '1', dictLabel: '纯商务', sortOrder: 2, status: 1 },
  ]
  assert.deepEqual(relationshipOptionsFromDict(rows), ['商务少', '纯商务', '感情商务', '多样性'])
})

check('管理员调乱 sort_order(3/1/2/0) → 输出仍按 dictValue, 不随排序漂移', () => {
  const rows = [
    { dictValue: '0', dictLabel: '商务少', sortOrder: 3, status: 1 },
    { dictValue: '1', dictLabel: '纯商务', sortOrder: 1, status: 1 },
    { dictValue: '2', dictLabel: '感情商务', sortOrder: 2, status: 1 },
    { dictValue: '3', dictLabel: '多样性', sortOrder: 0, status: 1 },
  ]
  assert.deepEqual(relationshipOptionsFromDict(rows), ['商务少', '纯商务', '感情商务', '多样性'])
})

check('停用中间档(value=1) → 下标1 用兜底文案补位, 后续档下标不前移', () => {
  const rows = [
    { dictValue: '0', dictLabel: '商务少', sortOrder: 1, status: 1 },
    { dictValue: '1', dictLabel: '纯商务', sortOrder: 2, status: 0 },
    { dictValue: '2', dictLabel: '感情商务', sortOrder: 3, status: 1 },
    { dictValue: '3', dictLabel: '多样性', sortOrder: 4, status: 1 },
  ]
  // 旧实现: 过滤 status=0 后剩 3 行, '感情商务' 落到下标 1 → 用户选"感情商务"存 1 分(错档)
  assert.deepEqual(relationshipOptionsFromDict(rows), ['商务少', RELATIONSHIP_OPTIONS[1], '感情商务', '多样性'])
})

check('行缺失(value=2 无行) → 缺档补兜底文案', () => {
  const rows = [
    { dictValue: '0', dictLabel: '商务少', sortOrder: 1, status: 1 },
    { dictValue: '1', dictLabel: '纯商务', sortOrder: 2, status: 1 },
    { dictValue: '3', dictLabel: '多样性', sortOrder: 4, status: 1 },
  ]
  assert.deepEqual(relationshipOptionsFromDict(rows), ['商务少', '纯商务', RELATIONSHIP_OPTIONS[2], '多样性'])
})

check('脏值(负数/非整数/>3)丢弃, 正常行不受影响', () => {
  const rows = [
    { dictValue: '0', dictLabel: '商务少', sortOrder: 1, status: 1 },
    { dictValue: '-1', dictLabel: '脏行A', sortOrder: 2, status: 1 },
    { dictValue: '2.5', dictLabel: '脏行B', sortOrder: 3, status: 1 },
    { dictValue: '9', dictLabel: '脏行C', sortOrder: 4, status: 1 },
    { dictValue: '1', dictLabel: '纯商务', sortOrder: 5, status: 1 },
    { dictValue: '2', dictLabel: '感情商务', sortOrder: 6, status: 1 },
    { dictValue: '3', dictLabel: '多样性', sortOrder: 7, status: 1 },
  ]
  assert.deepEqual(relationshipOptionsFromDict(rows), ['商务少', '纯商务', '感情商务', '多样性'])
})

check('空/全停用/全脏 → null(调用方保持兜底)', () => {
  assert.equal(relationshipOptionsFromDict(null), null)
  assert.equal(relationshipOptionsFromDict([]), null)
  assert.equal(relationshipOptionsFromDict([{ dictValue: '0', dictLabel: 'x', status: 0 }]), null)
  assert.equal(relationshipOptionsFromDict([{ dictValue: '9', dictLabel: 'x', status: 1 }]), null)
})

if (failed) {
  console.error(`\n${failed} 项断言失败`)
  process.exit(1)
}
console.log('全部通过: 客情字典分值口径(dictValue 驱动)与 PC 对齐')
