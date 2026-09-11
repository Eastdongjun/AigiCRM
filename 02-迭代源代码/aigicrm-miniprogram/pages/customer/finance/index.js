const { getCustomerInvoices, getCustomerPayments } = require('../../../api/customer')
const { hasPermission, requireLogin } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')
const { readDemoFinance } = require('../../../utils/demo-finance')

function rows(payload) { return Array.isArray(payload) ? payload : (payload && (payload.records || payload.list || payload.rows)) || [] }

Page({
  data: { id: '', tab: 'invoice', invoices: [], payments: [], loading: false, canEdit: false },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('finance:view')) {
      wx.showToast({ title: '没有财务查看权限', icon: 'none' })
      wx.navigateBack()
      return
    }
    this.setData({ id: options.id || '', canEdit: getApp().globalData.demoMode || hasPermission('customer:edit') })
    this.load()
  },
  load() {
    if (!this.data.id) return
    if (getApp().globalData.demoMode) {
      const saved = readDemoFinance(this.data.id)
      return this.setData({ invoices: [{ id: 1, invoiceNo: 'DEMO-INV-001', amount: 180000, invoiceDate: '2026-08-12', status: '已开票' }, ...saved.invoices], payments: [{ id: 1, paymentNo: 'DEMO-PAY-001', amount: 80000, paymentDate: '2026-08-30', status: '已回款' }, ...saved.payments] })
    }
    this.setData({ loading: true })
    Promise.all([getCustomerInvoices(this.data.id), getCustomerPayments(this.data.id)]).then(([invoices, payments]) => {
      this.setData({ invoices: rows(invoices), payments: rows(payments) })
    }).catch((error) => showError(error, '财务明细加载失败')).finally(() => this.setData({ loading: false }))
  },
  switchTab(e) { this.setData({ tab: e.currentTarget.dataset.tab }) },
  openForm() { wx.navigateTo({ url: `/pages/customer/finance/form/index?id=${this.data.id}&mode=${this.data.tab}` }) },
  goBack() { wx.navigateBack() }
})
