const { addCustomerContact } = require('../../../../api/customer')
const { hasPermission, requireLogin } = require('../../../../utils/auth')
const { showError } = require('../../../../utils/feedback')
const { saveDemoContact } = require('../../../../utils/demo-contacts')

Page({
  data: { id: '', form: { name: '', position: '', phone: '', email: '', remark: '' }, saving: false },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && (!hasPermission('customer:view') || !hasPermission('customer:edit'))) { wx.showToast({ title: '没有联系人编辑权限', icon: 'none' }); wx.navigateBack(); return }
    this.setData({ id: options.id || '' })
  },
  input(e) { this.setData({ [`form.${e.currentTarget.dataset.field}`]: e.detail.value }) },
  submit() {
    if (!this.data.form.name.trim() || this.data.saving) return wx.showToast({ title: '请填写联系人姓名', icon: 'none' })
    this.setData({ saving: true })
    const action = getApp().globalData.demoMode ? Promise.resolve(saveDemoContact(this.data.id, this.data.form)) : addCustomerContact(this.data.id, this.data.form)
    action.then(() => { wx.showToast({ title: '已保存', icon: 'success' }); setTimeout(() => wx.navigateBack(), 500) }).catch((error) => showError(error, '保存失败')).finally(() => this.setData({ saving: false }))
  },
  goBack() { wx.navigateBack() }
})
