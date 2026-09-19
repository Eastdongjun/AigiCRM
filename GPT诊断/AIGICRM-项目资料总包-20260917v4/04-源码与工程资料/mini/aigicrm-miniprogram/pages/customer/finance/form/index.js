const { addInvoice, addPayment } = require('../../../../api/customer')
const { hasPermission, requireLogin } = require('../../../../utils/auth')
const { showError } = require('../../../../utils/feedback')
const { saveDemoFinance } = require('../../../../utils/demo-finance')

function buildFinanceFormView(data) {
  const isInvoice = data.mode === 'invoice'
  const form = data.form || {}
  const invoicePrimaryFields = [
    { field: 'invoiceDate', label: '开票日期 *', value: form.invoiceDate || '', placeholder: 'YYYY-MM-DD', type: 'text' },
    { field: 'invoiceNumber', label: '发票号码 *', value: form.invoiceNumber || '', placeholder: '请输入发票号码', type: 'text' }
  ]
  const invoiceAmountFields = [
    { field: 'amount', label: '开票金额 *', value: form.amount || '', placeholder: '请输入金额', type: 'digit' },
    { field: 'invoiceCompany', label: '开票单位', value: form.invoiceCompany || '', placeholder: '请输入开票单位', type: 'text' }
  ]
  const paymentFields = [
    { field: 'paymentDate', label: '回款日期 *', value: form.paymentDate || '', placeholder: 'YYYY-MM-DD', type: 'text' },
    { field: 'paymentAmount', label: '回款金额 *', value: form.paymentAmount || '', placeholder: '请输入金额', type: 'digit' },
    { field: 'paymentMethod', label: '回款方式', value: form.paymentMethod || '', placeholder: '请输入回款方式', type: 'text' }
  ]
  return {
    isInvoice,
    title: isInvoice ? '新增开票' : '新增回款',
    sectionTitle: isInvoice ? '开票信息' : '回款信息',
    submitText: data.saving ? '保存中...' : '保存',
    submitLoading: Boolean(data.saving),
    cancelText: isInvoice ? '取消开票' : '取消回款',
    cancelDisabled: Boolean(data.saving),
    invoiceTypeText: form.invoiceType || '请选择发票类型',
    amountLabel: isInvoice ? '开票金额 *' : '回款金额 *',
    datePlaceholder: 'YYYY-MM-DD',
    invoicePrimaryFields,
    invoiceAmountFields,
    paymentFields,
    remarkValue: form.remark || '',
    remarkPlaceholder: '请输入备注'
  }
}

Page({
  data: {
    id: '',
    mode: 'invoice',
    form: {},
    invoiceTypeOptions: ['增值税专票', '增值税普票'],
    invoiceTypeIndex: 0,
    saving: false,
    financeFormView: buildFinanceFormView({
      mode: 'invoice',
      form: { invoiceType: '增值税专票' },
      saving: false
    })
  },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && (!hasPermission('finance:view') || !hasPermission('customer:edit'))) { wx.showToast({ title: '没有财务编辑权限', icon: 'none' }); wx.navigateBack(); return }
    const mode = options.mode === 'payment' ? 'payment' : 'invoice'
    const form = mode === 'invoice'
      ? { invoiceDate: '', invoiceNumber: '', invoiceType: '增值税专票', invoiceCompany: '', amount: '' }
      : { paymentDate: '', paymentAmount: '', paymentMethod: '', remark: '' }
    this.setData({
      id: options.id || '',
      mode,
      form,
      financeFormView: buildFinanceFormView({ ...this.data, mode, form })
    })
  },
  refreshFinanceFormView() {
    this.setData({ financeFormView: buildFinanceFormView(this.data) })
  },
  applyState(patch, callback) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, financeFormView: buildFinanceFormView(next) }, callback)
  },
  input(e) {
    this.applyState({ [`form.${e.currentTarget.dataset.field}`]: e.detail.value })
  },
  chooseInvoiceType(e) {
    const index = Number(e.detail.value || 0)
    const invoiceType = this.data.invoiceTypeOptions[index] || this.data.invoiceTypeOptions[0]
    this.applyState({ invoiceTypeIndex: index, 'form.invoiceType': invoiceType })
  },
  submit() {
    const form = this.data.form
    const required = this.data.mode === 'invoice' ? [form.invoiceDate, form.invoiceNumber, form.amount] : [form.paymentDate, form.paymentAmount]
    if (required.some((value) => !String(value || '').trim()) || this.data.saving) return wx.showToast({ title: '请填写必填项', icon: 'none' })
    this.applyState({ saving: true })
    const action = getApp().globalData.demoMode ? Promise.resolve(saveDemoFinance(this.data.id, this.data.mode, form)) : (this.data.mode === 'invoice' ? addInvoice(this.data.id, form) : addPayment(this.data.id, form))
    action.then(() => { wx.showToast({ title: '已保存', icon: 'success' }); setTimeout(() => wx.navigateBack(), 500) }).catch((error) => showError(error, '保存失败')).finally(() => this.applyState({ saving: false }))
  },
  goBack() {
    if (this.data.saving) return
    const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
    if (pages.length > 1) {
      wx.navigateBack()
      return
    }
    wx.navigateTo({ url: `/pages/customer/finance/index?id=${this.data.id}` })
  }
})
