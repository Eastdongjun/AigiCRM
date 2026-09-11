const { addInvoice, addPayment } = require('../../../../api/customer')
const { hasPermission, requireLogin } = require('../../../../utils/auth')
const { showError } = require('../../../../utils/feedback')
const { saveDemoFinance } = require('../../../../utils/demo-finance')

Page({
  data: { id: '', mode: 'invoice', form: {}, saving: false },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && (!hasPermission('customer:view') || !hasPermission('customer:edit'))) { wx.showToast({ title: '没有财务编辑权限', icon: 'none' }); wx.navigateBack(); return }
    const mode = options.mode === 'payment' ? 'payment' : 'invoice'
    this.setData({ id: options.id || '', mode, form: mode === 'invoice' ? { invoiceDate: '', invoiceNumber: '', invoiceCompany: '', amount: '' } : { paymentDate: '', paymentAmount: '', paymentMethod: '', remark: '' } })
  },
  input(e) { this.setData({ [`form.${e.currentTarget.dataset.field}`]: e.detail.value }) },
  submit() {
    const form = this.data.form
    const required = this.data.mode === 'invoice' ? [form.invoiceDate, form.invoiceNumber, form.amount] : [form.paymentDate, form.paymentAmount]
    if (required.some((value) => !String(value || '').trim()) || this.data.saving) return wx.showToast({ title: '请填写必填项', icon: 'none' })
    this.setData({ saving: true })
    const action = getApp().globalData.demoMode ? Promise.resolve(saveDemoFinance(this.data.id, this.data.mode, form)) : (this.data.mode === 'invoice' ? addInvoice(this.data.id, form) : addPayment(this.data.id, form))
    action.then(() => { wx.showToast({ title: '已保存', icon: 'success' }); setTimeout(() => wx.navigateBack(), 500) }).catch((error) => showError(error, '保存失败')).finally(() => this.setData({ saving: false }))
  },
  goBack() { wx.navigateBack() }
})
