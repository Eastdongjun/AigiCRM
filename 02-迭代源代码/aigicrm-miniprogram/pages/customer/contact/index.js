const { getCustomerContacts } = require('../../../api/customer')
const { hasPermission, requireLogin } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')
const { readDemoContacts } = require('../../../utils/demo-contacts')

Page({
  data: { id: '', contacts: [], loading: false, canEdit: false },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && (!hasPermission('customer:view') || !hasPermission('contact:view'))) {
      wx.showToast({ title: '没有联系人查看权限', icon: 'none' })
      wx.navigateBack()
      return
    }
    this.setData({ id: options.id || '', canEdit: getApp().globalData.demoMode || hasPermission('customer:edit') })
    this.load()
  },
  load() {
    if (!this.data.id) return
    if (getApp().globalData.demoMode) return this.setData({ contacts: [{ id: 1, name: '李经理', position: '采购负责人', mobile: '138****0001', email: 'li@example.com' }, { id: 2, name: '王工', position: '技术负责人', mobile: '139****0002', email: 'wang@example.com' }, ...readDemoContacts(this.data.id)] })
    this.setData({ loading: true })
    getCustomerContacts(this.data.id).then((contacts) => this.setData({ contacts: contacts || [] })).catch((error) => showError(error, '联系人加载失败')).finally(() => this.setData({ loading: false }))
  },
  call(e) {
    const phone = e.currentTarget.dataset.phone
    if (phone && !phone.includes('*')) wx.makePhoneCall({ phoneNumber: phone })
    else wx.showToast({ title: '手机号已脱敏', icon: 'none' })
  },
  openDetail(e) {
    const item = this.data.contacts[Number(e.currentTarget.dataset.index)]
    if (!item) return
    wx.navigateTo({ url: `/pages/customer/contact/detail/index?name=${encodeURIComponent(item.name || item.contactName || '')}&position=${encodeURIComponent(item.position || item.title || '')}&mobile=${encodeURIComponent(item.mobile || item.phone || '')}&email=${encodeURIComponent(item.email || '')}` })
  },
  goBack() { wx.navigateBack() }
  ,openForm() { wx.navigateTo({ url: `/pages/customer/contact/form/index?id=${this.data.id}` }) }
})
