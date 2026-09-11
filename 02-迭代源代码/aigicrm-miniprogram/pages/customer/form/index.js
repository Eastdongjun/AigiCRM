const { getCustomer, addCustomer, updateCustomer } = require('../../../api/customer')
const { hasPermission, requireLogin } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')
const { readDemoCustomers, saveDemoCustomer } = require('../../../utils/demo-customers')

Page({
  data: { id: '', isEdit: false, loading: false, form: { customerName: '', invoiceName: '', industry: '', devType: '', address: '', remark: '' } },
  onLoad(options) {
    if (!requireLogin()) return
    const id = options.id || ''
    if (!getApp().globalData.demoMode && !hasPermission(id ? 'customer:edit' : 'customer:add')) {
      wx.showToast({ title: '没有操作权限', icon: 'none' }); return wx.navigateBack()
    }
    this.setData({ id, isEdit: !!id })
    if (id) this.loadDetail()
  },
  loadDetail() {
    if (getApp().globalData.demoMode) {
      const item = readDemoCustomers().find((customer) => String(customer.id) === String(this.data.id))
      if (item) {
        const form = { ...this.data.form }
        Object.keys(form).forEach((key) => { form[key] = item[key] || '' })
        return this.setData({ form })
      }
    }
    this.setData({ loading: true })
    getCustomer(this.data.id).then((item) => {
      const form = { ...this.data.form }
      Object.keys(form).forEach((key) => { form[key] = item[key] || '' })
      this.setData({ form })
    }).catch((error) => showError(error, '客户加载失败')).finally(() => this.setData({ loading: false }))
  },
  onInput(e) { this.setData({ [`form.${e.currentTarget.dataset.field}`]: e.detail.value }) },
  submit() {
    const form = this.data.form
    if (!form.customerName.trim()) return wx.showToast({ title: '请输入客户名称', icon: 'none' })
    if (this.data.loading) return
    this.setData({ loading: true })
    const action = getApp().globalData.demoMode
      ? Promise.resolve(saveDemoCustomer(form, this.data.isEdit ? this.data.id : ''))
      : (this.data.isEdit ? updateCustomer(this.data.id, form) : addCustomer(form))
    action.then(() => { wx.showToast({ title: this.data.isEdit ? '保存成功' : '新增成功', icon: 'success' }); setTimeout(() => wx.navigateBack(), 500) }).catch((error) => showError(error, '保存失败，请检查权限或字段')).finally(() => this.setData({ loading: false }))
  },
  cancel() { wx.navigateBack() }
})
