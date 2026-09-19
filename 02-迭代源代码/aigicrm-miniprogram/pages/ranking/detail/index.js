const { requireLogin, hasPermission } = require('../../../utils/auth')

function buildRankingDetailView(data) {
  return {
    nameInitialText: data.nameInitial || '排',
    nameText: data.name || '未命名用户',
    scoreText: String(data.score || 0),
    periodText: data.period || '当前周期',
    deptText: data.dept || '-'
  }
}

Page({
  data: {
    name: '',
    nameInitial: '',
    score: 0,
    dept: '',
    period: '',
    rankingDetailView: buildRankingDetailView({ name: '', nameInitial: '', score: 0, dept: '', period: '' })
  },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('score:view')) {
      wx.showToast({ title: '没有排行查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
      return
    }
    const name = decodeURIComponent(options.name || '')
    this.applyState({ name, nameInitial: name ? name.slice(0, 1) : '排', score: decodeURIComponent(options.score || '0'), dept: decodeURIComponent(options.dept || ''), period: decodeURIComponent(options.period || '当前周期') })
  },
  applyState(patch, callback) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, rankingDetailView: buildRankingDetailView(next) }, callback)
  },
  goBack() { wx.navigateBack() }
})
