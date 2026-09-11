const { addCustomerLog } = require('../../../api/customer')
const { hasPermission, requireLogin } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')

Page({
  data: { id: '', content: '', saving: false },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && (!hasPermission('customer:view') || !hasPermission('customer:edit'))) {
      wx.showToast({ title: '没有跟进记录权限', icon: 'none' })
      wx.navigateBack()
      return
    }
    this.setData({ id: options.id || '' })
  },
  onContentInput(e) { this.setData({ content: e.detail.value }) },
  submit() {
    const content = this.data.content.trim()
    if (!content || this.data.saving) return wx.showToast({ title: '请填写跟进内容', icon: 'none' })
    this.setData({ saving: true })
    const action = getApp().globalData.demoMode ? Promise.resolve() : addCustomerLog(this.data.id, { content })
    action.then(() => {
      wx.showToast({ title: '已记录', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 500)
    }).catch((error) => showError(error, '记录失败')).finally(() => this.setData({ saving: false }))
  },
  goBack() { wx.navigateBack() }
})
