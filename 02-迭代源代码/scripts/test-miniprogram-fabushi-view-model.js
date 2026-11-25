#!/usr/bin/env node
// 法布施小程序 · 视图字段整理(utils/fabushi-view-model.js)·单元测试(2026-11-25, 先红后绿)
// 缘起: 用户裁决“小程序法布施界面按原型 v7 重做外观”。原型上的文字(“今日 2/3”“今天 08:02 已诵…”
//   “第 8/81 章”“今天 09:30”)都要从接口数据算出来。这些换算集中放在一个纯函数模块里, 这里逐条钉住。
// 抓不到: WXML 有没有把这些字段画到正确的位置(由模拟器逐屏截图与原型并排来核对)。
const assert = require('node:assert/strict')

const vm = require('../aigicrm-miniprogram/utils/fabushi-view-model')

// 固定的“现在”: 2026-11-25 10:00(上海时间)。接口给的是不带时区的本地时间字符串。
const NOW = new Date(2026, 10, 25, 10, 0, 0)
let passed = 0
function check(name, fn) {
  fn()
  passed++
  console.log('  ok  ' + name)
}

console.log('封面文字')
check('去掉书名号, 最多 4 个字', () => {
  assert.equal(vm.coverText('《了凡四训》'), '了凡四训')
  assert.equal(vm.coverText('道德经陈鼓应注'), '道德经陈')
  assert.equal(vm.coverText('论'), '论')
})
check('没有书名时给“书”, 不是空白', () => {
  assert.equal(vm.coverText(null), '书')
  assert.equal(vm.coverText(''), '书')
  assert.equal(vm.coverText('《》'), '书')
})
check('封面颜色按编号稳定, 且都在规定的几种里', () => {
  const tones = ['navy', 'cyan', 'amber', 'teal', 'violet', 'gray']
  for (let id = 0; id < 50; id++) assert.ok(tones.includes(vm.coverTone(id)), `coverTone(${id})`)
  assert.equal(vm.coverTone(12), vm.coverTone(12))
  assert.equal(vm.coverTone(undefined), vm.coverTone(0))
  assert.ok(new Set([0, 1, 2, 3, 4, 5].map(vm.coverTone)).size >= 4, '前 6 个编号应当用到 4 种以上颜色')
})

console.log('时间')
check('今天 / 昨天 / 今年更早 / 往年', () => {
  assert.equal(vm.timeText('2026-11-25T09:30:00', NOW), '今天 09:30')
  assert.equal(vm.timeText('2026-11-24T18:40:00', NOW), '昨天 18:40')
  assert.equal(vm.timeText('2026-11-20 07:50:00', NOW), '11-20 07:50')
  assert.equal(vm.timeText('2025-12-31T23:00:00', NOW), '2025-12-31')
})
check('空值给空串; 认不出的格式原样返回', () => {
  assert.equal(vm.timeText('', NOW), '')
  assert.equal(vm.timeText(null, NOW), '')
  assert.equal(vm.timeText('前天', NOW), '前天')
})

console.log('阅读进度')
check('有总页数: 第 x/y 页 · 百分比', () => {
  assert.equal(vm.progressText({ lastPage: 15, totalPages: 300 }), '第 15/300 页 · 5%')
})
check('没有总页数: 读到第 x 页; 只有百分比: 已读 x%', () => {
  assert.equal(vm.progressText({ lastPage: 15 }), '读到第 15 页')
  assert.equal(vm.progressText({ progressPct: 52 }), '已读 52%')
  assert.equal(vm.progressText({}), '暂无进度')
})

console.log('觉醒三板块(原型 A1)')
check('一件都没做: 0/3, 主卡按钮是“去诵读”', () => {
  const view = vm.awakeningView({}, {}, null)
  assert.equal(view.doneToday, 0)
  assert.equal(view.nextScope, 'songdu')
  assert.equal(view.nextLabel, '去诵读')
  assert.equal(view.songduDone, false)
  assert.equal(view.suishiDone, false)
  assert.equal(view.zaiduDone, false)
  assert.equal(view.songduSub, '每天朗读背诵一小段经典')
  assert.equal(view.suishiSub, '随手传点儿东西 · 一句话/一页书/一张照片')
  assert.equal(view.zaiduSub, '挑一本书，按章节精读')
})
check('三件都做了: 3/3, 没有下一步按钮; 诵读写条数和连续天数', () => {
  const view = vm.awakeningView({ todayRecitations: 2, todayGifts: 1, todayPosts: 4 }, { myStreakDays: 4 }, null)
  assert.equal(view.doneToday, 3)
  assert.equal(view.nextScope, '')
  assert.equal(view.nextLabel, '')
  assert.equal(view.songduSub, '今天已诵 2 条 · 连续 4 天')
  assert.equal(view.suishiSub, '今天已施 1 条')
  assert.equal(view.zaiduDone, true)
})
check('只做了随施: 下一步仍是先诵读(顺序: 诵读 → 随施 → 再读)', () => {
  const view = vm.awakeningView({ todayRecitations: 0, todayGifts: 1, todayPosts: 1 }, {}, null)
  assert.equal(view.doneToday, 1)
  assert.equal(view.nextScope, 'songdu')
  assert.equal(view.nextLabel, '去诵读')
})
check('诵读、随施做了, 再读没做: 下一步是“去精读”', () => {
  const view = vm.awakeningView({ todayRecitations: 1, todayGifts: 1, todayPosts: 2 }, {}, { title: '了凡四训', lastPage: 15 })
  assert.equal(view.nextScope, 'zaidu')
  assert.equal(view.nextLabel, '去精读')
  assert.equal(view.zaiduSub, '《了凡四训》 · 读到第 15 页')
})
check('在读的书带书名号: 不重复套', () => {
  const view = vm.awakeningView({ todayRecitations: 1, todayGifts: 1, todayPosts: 2 }, {}, { title: '《了凡四训》', lastPage: 15, totalPages: 100 })
  assert.equal(view.zaiduSub, '《了凡四训》 · 第 15/100 页 · 15%')
})
check('今日总数小于诵读加随施(数据不一致)时, 再读按 0 算, 不出负数', () => {
  const view = vm.awakeningView({ todayRecitations: 3, todayGifts: 2, todayPosts: 1 }, {}, null)
  assert.equal(view.zaiduDone, false)
  assert.equal(view.doneToday, 2)
})
check('连续天数不足 2 天不写', () => {
  assert.equal(vm.awakeningView({ todayRecitations: 1 }, { myStreakDays: 1 }, null).songduSub, '今天已诵 1 条')
})

console.log('提醒卡')
check('开着: 频率 + 时间', () => {
  assert.deepEqual(vm.reminderView({ reminderEnabled: 1, freqCode: 'weekday', remindTime: '20:30' }), { on: true, text: '工作日 20:30' })
  assert.deepEqual(vm.reminderView({ reminderEnabled: 1, freqCode: 'weekend', remindTime: '09:00' }), { on: true, text: '休息日 09:00' })
})
check('关着, 或没有设置: 显示默认的每天 20:00', () => {
  assert.deepEqual(vm.reminderView({ reminderEnabled: 0 }), { on: false, text: '每天 20:00' })
  assert.deepEqual(vm.reminderView(null), { on: false, text: '每天 20:00' })
})

console.log('统计四格(原型 C5)')
check('三类的条数与被复诵次数 = 成员榜逐行相加; 没有数据时都是 0', () => {
  const monthly = { ranking: [
    { songduCount: 4, suishiCount: 2, zaiduCount: 0, recitationsReceived: 3 },
    { songduCount: 1, suishiCount: 1, zaiduCount: 1, recitationsReceived: 0 },
    { songduCount: '2', suishiCount: null, zaiduCount: undefined }
  ] }
  assert.deepEqual(vm.teamTotals(monthly), { songdu: 7, suishi: 3, zaidu: 1, recitations: 3 })
  assert.deepEqual(vm.teamTotals({}), { songdu: 0, suishi: 0, zaidu: 0, recitations: 0 })
  assert.deepEqual(vm.teamTotals(null), { songdu: 0, suishi: 0, zaidu: 0, recitations: 0 })
})
check('环比角标: 正数 ↑、负数 ↓; 没有数或正好是 0 不显示', () => {
  assert.equal(vm.trendBadge(12), '↑ 12%')
  assert.equal(vm.trendBadge(-5), '↓ 5%')
  assert.equal(vm.trendBadge(8.5), '↑ 8.5%')
  assert.equal(vm.trendBadge(0), '')
  assert.equal(vm.trendBadge(null), '')
  assert.equal(vm.trendBadge(undefined), '')
  assert.equal(vm.trendBadge('abc'), '')
})

console.log('信息流条目')
check('时间、类型名、NEW 标记; 图片最多 3 张并带上下载令牌; 音频只计数', () => {
  const post = {
    id: 9, scopeType: 'suishi', title: null, content: '下班路上的晚霞', createdAt: '2026-11-25T09:30:00', isNew: true,
    attachments: [
      { id: 1, fileId: 101, fileType: 'image' },
      { id: 2, fileId: 102, fileType: 'image' },
      { id: 3, fileId: 103, fileType: 'image' },
      { id: 4, fileId: 104, fileType: 'image' },
      { id: 5, fileId: 105, fileType: 'audio' },
      { id: 6, fileId: 106, fileType: 'video' }
    ]
  }
  const view = vm.postView(post, 'tok en', NOW)
  assert.equal(view.typeLabel, '每日随施')
  assert.equal(view.timeText, '今天 09:30')
  assert.equal(view.isNew, true)
  assert.deepEqual(view.pics.map((pic) => pic.fileId), [101, 102, 103])
  assert.ok(view.pics[0].url.endsWith('/api/file/download/101?token=tok%20en'), view.pics[0].url)
  assert.equal(view.hasAudio, true)
  assert.equal(view.audioCount, 1)
  assert.equal(view.picCount, 4)
})
check('没有附件、认不出的类型、没有令牌', () => {
  const view = vm.postView({ id: 1, scopeType: 'other', createdAt: '2026-11-24T18:40:00' }, '', NOW)
  assert.equal(view.typeLabel, '内容')
  assert.deepEqual(view.pics, [])
  assert.equal(view.hasAudio, false)
  assert.equal(view.isNew, false)
  assert.equal(view.timeText, '昨天 18:40')
})
check('“谁点了木鱼”只保留前几个名字和总数(接口给的是已截好的文字)', () => {
  assert.equal(vm.postView({ likeNames: '王晓兰、陈慧 等 12 人' }, '', NOW).likerText, '王晓兰、陈慧 等 12 人')
  assert.equal(vm.postView({}, '', NOW).likerText, '')
})

console.log('书架条目')
check('封面文字、颜色、副标题', () => {
  const row = vm.shelfRowView({ id: 5, bookId: 12, title: '《了凡四训》', status: 'reading', lastPage: 15, totalPages: 100, progressPct: 15 }, 2)
  assert.equal(row.coverText, '了凡四训')
  assert.equal(row.coverTone, vm.coverTone(12))
  assert.equal(row.subText, '第 15/100 页 · 15%')
  assert.equal(row.statusText, '在读')
  assert.equal(row.checked, false)
})
check('想读 / 已读完 / 收藏; 未知状态按在读', () => {
  assert.equal(vm.shelfRowView({ status: 'wish' }, 0).statusText, '想读')
  assert.equal(vm.shelfRowView({ status: 'finished' }, 0).statusText, '已读完')
  assert.equal(vm.shelfRowView({ status: 'favorite' }, 0).statusText, '收藏')
  assert.equal(vm.shelfRowView({}, 0).statusText, '在读')
})
check('书架格子下面的小字: 已读完且没有页数写“读完”; 想读 / 收藏没有进度时写状态名; 有进度就写进度', () => {
  assert.equal(vm.shelfRowView({ status: 'finished' }, 0).subText, '读完')
  assert.equal(vm.shelfRowView({ status: 'finished', lastPage: 10 }, 0).subText, '读到第 10 页')
  assert.equal(vm.shelfRowView({ status: 'wish' }, 0).subText, '想读')
  assert.equal(vm.shelfRowView({ status: 'favorite' }, 0).subText, '收藏')
  assert.equal(vm.shelfRowView({ status: 'favorite', progressPct: 30 }, 0).subText, '已读 30%')
  assert.equal(vm.shelfRowView({ status: 'reading' }, 0).subText, '暂无进度')
})

console.log(`PASS: test-miniprogram-fabushi-view-model (${passed} 条)`)
