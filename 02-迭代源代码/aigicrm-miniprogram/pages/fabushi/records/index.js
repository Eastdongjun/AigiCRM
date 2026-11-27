// 阅读记录(原型 C3 · 我的觉醒 · 阅读记录): 打卡日历 + 四格 + 近几日时间线。
// 【2026-11-25 用户裁决】“小程序法布施界面按原型 v7 重做外观”: 原型有这一屏, 小程序里此前没有这个页面(旧主页里只有一段阅读天数)。
//   数据来自三个现成的接口: 阅读记录(按月)、个人统计、今日觉醒状态; 显示的内容都在 utils/fabushi-view-model.js 的 recordsView 里算。
const { hasPermission, requireLogin } = require('../../../utils/auth')
const { whenPermissionsReady } = require('../../../utils/permission-ready')
const { getFabushiRecords, getFabushiPersonalStats, getFabushiAwakenStatus } = require('../../../api/fabushi')
const { showError } = require('../../../utils/feedback')
const vm = require('../../../utils/fabushi-view-model')

Page({
  data: Object.assign({ weekDays: ['一', '二', '三', '四', '五', '六', '日'], loading: true, canPrev: true }, vm.recordsView('', {}, {}, {}, new Date())),

  onLoad() {
    if (!requireLogin()) return
    return whenPermissionsReady(() => {
      if (!hasPermission('fabushi:view')) {
        wx.showToast({ title: '没有法布施查看权限', icon: 'none' })
        setTimeout(() => wx.navigateBack(), 600)
        return
      }
      this._month = vm.monthKey(new Date())
      this.load()
    })
  },

  onPullDownRefresh() {
    this.load()
    wx.stopPullDownRefresh()
  },

  load() {
    const seq = (this._seq = (this._seq || 0) + 1)
    this.setData({ loading: true })
    const month = this._month
    Promise.all([
      getFabushiRecords(month),
      getFabushiPersonalStats().catch(() => ({})),
      getFabushiAwakenStatus().catch(() => ({}))
    ]).then(([records, personal, awake]) => {
      if (seq !== this._seq) return                // 连着点“上个月 / 下个月”时, 旧的应答晚到不覆盖新的
      this.setData(Object.assign({ loading: false }, vm.recordsView(month, records || {}, personal, awake)))
    }).catch((err) => {
      if (seq !== this._seq) return
      this.setData({ loading: false })
      showError(err, '阅读记录加载失败')
    })
  },

  onPrevMonth() {
    this._month = vm.shiftMonth(this._month, -1)
    this.load()
  },

  // 只能回到当月为止
  onNextMonth() {
    if (!this.data.canNext) return
    this._month = vm.shiftMonth(this._month, 1)
    this.load()
  }
})
