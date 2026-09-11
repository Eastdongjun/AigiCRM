const { requireLogin, hasPermission } = require('../../../../utils/auth')

Page({
  data: { name: '', position: '', mobile: '', email: '' },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && (!hasPermission('customer:view') || !hasPermission('contact:view'))) {
      wx.showToast({ title: '没有联系人查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
      return
    }
    this.setData({ name: decodeURIComponent(options.name || ''), position: decodeURIComponent(options.position || ''), mobile: decodeURIComponent(options.mobile || ''), email: decodeURIComponent(options.email || '') })
  },
  call() {
    if (this.data.mobile && !this.data.mobile.includes('*')) wx.makePhoneCall({ phoneNumber: this.data.mobile })
    else wx.showToast({ title: '手机号已脱敏', icon: 'none' })
  },
  goBack() { wx.navigateBack() }
})
