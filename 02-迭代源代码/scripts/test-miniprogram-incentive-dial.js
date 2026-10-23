#!/usr/bin/env node
// 四项服务详情「三联拨盘」冒烟测试(2026-09-20 小程序同步轮)
// 在 node 下驱动 pages/incentive/detail/index.js 的视图模型,钉住 PC 定稿语义:
// 年份自增连续/月盘固定12/整月兜底/每月完整天数(闰年)/位置语义过滤/级联复位/回到今天/未读红点/空态文案。

const assert = require('node:assert/strict')

let pageConfig = null
global.Page = (config) => { pageConfig = config }
global.getApp = () => ({ globalData: { demoMode: false, currentUser: { id: 7 }, apiBaseUrl: '' } })
global.wx = {
  request: () => {},
  getStorageSync: () => '',
  setStorageSync: () => {},
  showToast: () => {},
  showModal: () => {},
  navigateBack: () => {}
}

require('../aigicrm-miniprogram/pages/incentive/detail/index.js')
assert.ok(pageConfig, 'page config captured')

function createPage() {
  const page = Object.create(pageConfig)
  page.data = JSON.parse(JSON.stringify(pageConfig.data))
  page.setData = function (patch) {
    Object.keys(patch).forEach((key) => {
      if (key.indexOf('[') >= 0 || key.indexOf('.') >= 0) {
        // 按路径写入(records[0].unread 等):仅测试用到的浅路径
        const [, index, field] = /^records\[(\d+)\]\.(\w+)$/.exec(key) || []
        if (index !== undefined) this.data.records[Number(index)][field] = patch[key]
        return
      }
      this.data[key] = patch[key]
    })
  }
  return page
}

const now = new Date()
const nowYear = now.getFullYear()
const nowMonth = now.getMonth() + 1
const nowDay = now.getDate()

const records = [
  { id: 101, customerId: 9, customerName: '华北钢铁集团', industry: '冶金', category: '产品试点', categoryName: '水泵机封', type: 'trial', score: 3, period: 'Q3', relatedProducts: '机封类', specificProduct: '水泵', userName: '张三', unread: true, createdAt: `${nowYear}-09-18 10:30`, preInstallQuestion: '密封面腐蚀检测完成，安装前确认间隙。', remark: '试运行 48 小时无泄漏', filePreId: '[301]', filePreName: '安装前照片.jpg', filePostId: '[302,303]', filePostName: '验收报告.pdf,运行视频.mp4' },
  { id: 102, customerId: 9, customerName: '华北钢铁集团', industry: '冶金', category: '交流拜访', categoryName: '高层拜访', type: 'exchange', score: 2, userName: '李四', unread: false, createdAt: '2024-02-29 09:00', preInstallQuestion: '拜访纪要与意向确认。' },
  { id: 103, customerId: 9, customerName: '华北钢铁集团', industry: '冶金', category: '两谈两做', categoryName: '方案两谈', type: 'plan', score: 1, userName: '王五', unread: true, createdAt: `${nowYear}-09-05 16:00` },
  { id: 104, customerId: 9, customerName: '华北钢铁集团', industry: '冶金', category: '外出参观', type: 'visit', score: 1, userName: '赵六', unread: false, createdAt: '' }
]

const page = createPage()
page.applyCustomer({ customerId: 9, customerName: '华北钢铁集团', industry: '冶金', totalScore: 7 }, records, records[0])
const view = page.data.detailView

// 客户概览
assert.equal(view.customerName, '华北钢铁集团')
assert.equal(view.heroInitial, '华')
assert.equal(view.scoreText, '7')
assert.equal(view.scoreCaption, '客户累计积分')
assert.equal(view.recordCountText, '4 条记录')
assert.equal(view.showUnread, true)
assert.equal(view.unreadCountText, '2 未读')

// 年份盘:最早数据年(2024)..当前年连续,中间无数据年份置暗、当年有"今天"点
const yearKeys = view.dialYears.map((item) => Number(item.key))
assert.equal(Math.min(...yearKeys), 2024)
assert.equal(Math.max(...yearKeys), nowYear)
assert.equal(yearKeys.length, nowYear - 2024 + 1)
const gapYears = view.dialYears.filter((item) => item.key !== '2024' && item.key !== String(nowYear))
assert.ok(gapYears.every((item) => item.dim), 'gap years stay dim but selectable')
assert.ok(view.dialYears.every((item) => (item.key === String(nowYear)) === item.today), 'today dot only on current year')

// 月盘固定 12 个月;初始对位聚焦记录所在月(9月),该月有未读 → 红点
assert.equal(view.dialMonths.length, 12)
assert.equal(view.dialMonths[8].label, '9月')
assert.equal(page.data.selYear, String(nowYear))
assert.equal(page.data.selMonth, 9)
assert.ok(view.dialMonths[8].hasUnread, 'September carries unread dot')

// 日盘:整月兜底首项 + 按拨盘年月生成完整天数;9 月 18 日亮起,其余置暗
assert.equal(view.dialDays[0].label, '整月')
assert.equal(view.dialDays.length, 1 + 30)
assert.equal(view.dialDays[18].dim, false)
assert.equal(view.dialDays[19].dim, true)
assert.equal(view.dialDays[nowDay].today, nowMonth === 9)

// 位置语义:整月显示当月全部记录(仅含有效时间),按日过滤只留当天
assert.equal(view.items.length, 2, 'whole-month shows both September records')
assert.equal(view.paneLabelText, `${nowYear}年9月`)
page.setDetailData({ selDay: 18 })
assert.equal(page.data.detailView.items.length, 1)
assert.equal(page.data.detailView.items[0].id, 101)
assert.equal(page.data.detailView.paneLabelText, `${nowYear}年9月 · 18日`)

// 记录卡 metabar:时间/阶段/分值/灰标签内联 + 相对天 + 未读点
const item = page.data.detailView.items[0]
assert.equal(item.dayText, '09月18日')
assert.equal(item.timeText, '10:30')
assert.deepEqual(item.chips.map((chip) => chip.label), ['上传人', '周期', '行业', '相关产品', '具体产品'])
assert.equal(item.scoreText, '+3 分')
assert.equal(item.stageColor, '#059669')
assert.equal(item.showUnreadDot, true)
assert.equal(item.detailLabel, '水泵机封安装详情')
assert.equal(item.showTrialRemark, true)
assert.equal(item.remarkLabel, '水泵机封安装备注')

// 附件凭证:平铺大瓦片 + 槽位角标
assert.equal(item.showFilesBlock, true)
assert.equal(item.countText, '附件凭证 · 3 项')
assert.equal(item.files[0].tagText, '安装前')
assert.equal(item.files[0].tagColor, '#2563eb')
assert.equal(item.files[0].showThumb, true)
assert.equal(item.files[1].tagText, '安装后')

// 空态:拨到无数据年月 → 「此时间无数据」
page.setDetailData({ selYear: '2024', selMonth: 3, selDay: 0 })
let empty = page.data.detailView
assert.equal(empty.showPaneEmpty, true)
assert.equal(empty.paneEmptyTitle, '2024年3月，此时间无数据')
assert.ok(empty.paneEmptyHint.indexOf('转盘') >= 0)

// 日历完整性:2024 年 2 月闰年 29 天(与有无记录无关),仅 29 日亮
page.setDetailData({ selYear: '2024', selMonth: 2, selDay: 0 })
const feb = page.data.detailView.dialDays
assert.equal(feb.length, 1 + 29)
assert.equal(feb[29].dim, false)
assert.equal(feb[15].dim, true)

// 级联复位:跨年/月拨动后日盘回到「整月」(dialYears[0]=2024 为当前选中,拨到 [1]=2025 触发换年)
page.onDialChange({ detail: { value: [1, 1, 12] } })
assert.equal(page.data.selDay, 0, 'year change cascades day back to whole-month')
assert.equal(page.data.detailView.dialValue[2], 0)

// 回到今天
page.goToday()
assert.equal(page.data.selYear, String(nowYear))
assert.equal(page.data.selMonth, nowMonth)
assert.equal(page.data.selDay, nowDay)
assert.equal(page.data.detailView.paneLabelText, `${nowYear}年${nowMonth}月 · ${nowDay}日`)

// 实时时钟:秒级跳动文案 + 可清理
assert.ok(/^\d{2}:\d{2}:\d{2}$/.test(view.nowTimeText))
assert.ok(view.nowDateText.indexOf('周') >= 0)
page.startClock()
assert.ok(page._nowTimer, 'clock timer armed')
page.stopClock()
assert.equal(page._nowTimer, null, 'clock timer cleared')

// 删除权限:本人上传(owner=userId 优先,缺省回退 salespersonId)或持删除授权才可见
assert.equal(page.canDeleteRecord({ userId: 7 }), true)
assert.equal(page.canDeleteRecord({ userId: 99 }), false)
assert.equal(page.canDeleteRecord({ salespersonId: 7 }), true)

console.log('PASS: incentive detail dial view-model semantics (year auto-increment, full-month days incl. leap Feb, whole-month fallback, position filtering, cascade reset, back-to-today, unread dots, empty-state copy, metabar chips, attachment tiles)')
