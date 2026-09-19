const { requireLogin, hasPermission } = require('../../../../utils/auth')

function textOrDash(value) {
  return value === undefined || value === null || value === '' ? '-' : String(value)
}

function buildContactDetailView(data = {}) {
  const name = data.name || ''
  return {
    nameText: name || '未命名联系人',
    nameInitial: name ? name.slice(0, 1) : '联',
    positionText: textOrDash(data.position),
    mobileText: textOrDash(data.mobile),
    remarkText: textOrDash(data.remark),
    mobileValue: data.mobile || ''
  }
}

Page({
  data: {
    name: '',
    position: '',
    mobile: '',
    remark: '',
    contactDetailView: buildContactDetailView({})
  },
  applyState(patch) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, contactDetailView: buildContactDetailView(next) })
  },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && (!hasPermission('customer:view') || !hasPermission('contact:view'))) {
      wx.showToast({ title: '没有联系人查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
      return
    }
    const name = decodeURIComponent(options.name || '')
    this.applyState({ name, position: decodeURIComponent(options.position || ''), mobile: decodeURIComponent(options.mobile || ''), remark: decodeURIComponent(options.remark || '') })
  },
  call() {
    const phone = this.data.contactDetailView.mobileValue
    if (phone && !phone.includes('*')) wx.makePhoneCall({ phoneNumber: phone })
    else wx.showToast({ title: '手机号已脱敏', icon: 'none' })
  },
  goBack() { wx.navigateBack() }
})
