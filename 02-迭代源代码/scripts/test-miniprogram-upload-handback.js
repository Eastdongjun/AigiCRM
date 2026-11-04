#!/usr/bin/env node
// 激励上传·手机文件 H5 回挂逻辑·单元测试（TDD 2026-11-04: 先写本测试(红)→抽 utils/upload-handback.js(绿)→页面接线）
// 被测模块: aigicrm-miniprogram/utils/upload-handback.js —— 纯函数, 零 wx 依赖。
// 行为规格来源: 用户令"所有上传都走 H5" + 激励槽位提交链路(uploadFile(scope='incentive') 返回 id,
// existing+fileId 的槽位文件在 uploadPendingFiles 里直接进 doneIds 免二次上传)。
// 缝隙(2026-11-04 已确认 1-4 中的纯函数缝): 抓"取回最新自己上传的文件"的选取规则; 页面接线由 automator 缝覆盖。
const assert = require('assert')

const handback = require('../aigicrm-miniprogram/utils/upload-handback.js')

const ME = 72
const OTHER = 39
const T0 = Date.parse('2026-11-04T21:00:00')

const rec = (id, uploaderId, iso) => ({ id, uploaderId, fileName: `f${id}.pdf`, createdAt: iso })

// ---- 1. 只认自己的上传, 他人文件不入选 ----
const mixed = [rec(1, OTHER, '2026-11-04T21:05:00'), rec(2, ME, '2026-11-04T21:03:00')]
assert.strictEqual(handback.pickLatestOwnUpload(mixed, ME, T0).id, 2)

// ---- 2. 同为本人多条: 取最新(createdAt 降序) ----
const newest = [rec(3, ME, '2026-11-04T21:01:00'), rec(4, ME, '2026-11-04T21:06:00')]
assert.strictEqual(handback.pickLatestOwnUpload(newest, ME, T0).id, 4)

// ---- 3. 导航时间戳门槛: 早于导航时刻(含 60s 容差之外)的旧文件不认, 容差内的认 ----
assert.strictEqual(handback.pickLatestOwnUpload([rec(5, ME, '2026-11-04T20:58:00')], ME, T0), null) // 早于 T0-60s
assert.strictEqual(handback.pickLatestOwnUpload([rec(6, ME, '2026-11-04T20:59:30')], ME, T0).id, 6) // 容差内

// ---- 4. 缺 createdAt/非法时间的记录不入选(防误挂) ----
assert.strictEqual(handback.pickLatestOwnUpload([rec(7, ME, '')], ME, T0), null)
assert.strictEqual(handback.pickLatestOwnUpload([{ id: 8, uploaderId: ME }], ME, T0), null)

// ---- 5. 空列表/全不符 → null(调用方提示重试) ----
assert.strictEqual(handback.pickLatestOwnUpload([], ME, T0), null)
assert.strictEqual(handback.pickLatestOwnUpload([rec(9, OTHER, '2026-11-04T21:07:00')], ME, T0), null)

console.log('PASS: upload-handback latest-own-upload selection contract is aligned (uploader filter / newest / since-window).')
