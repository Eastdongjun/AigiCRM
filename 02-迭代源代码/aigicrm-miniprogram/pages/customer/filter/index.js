const { requireLogin, hasPermission } = require('../../../utils/auth')

Page({
  data: { keyword: '', devType: '全部', devTypes: ['全部', '成熟创新', '突破开拓', '铺垫宣传'] },
  onLoad() {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('customer:view')) {
      wx.showToast({ title: '没有客户查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
    }
  },
  onKeywordInput(e) { this.setData({ keyword: e.detail.value }) },
  chooseDevType(e) { this.setData({ devType: this.data.devTypes[Number(e.detail.value)] || '全部' }) },
  apply() {
    wx.setStorageSync('customerFilterDraft', { keyword: this.data.keyword.trim(), devType: this.data.devType })
    wx.navigateBack()
  },
  reset() { this.setData({ keyword: '', devType: '全部' }) },
  goBack() { wx.navigateBack() }
})
