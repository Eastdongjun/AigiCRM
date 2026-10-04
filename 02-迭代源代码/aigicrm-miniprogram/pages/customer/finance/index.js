const { getCustomerInvoices, getCustomerPayments, deleteInvoice, deletePayment } = require('../../../api/customer')
const { hasPermission, requireLogin } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')
const { readDemoFinance, deleteDemoFinance } = require('../../../utils/demo-finance')

function rows(payload) { return Array.isArray(payload) ? payload : (payload && (payload.records || payload.list || payload.rows)) || [] }

function normalizeInvoice(item) {
  return {
    ...item,
    invoiceNumber: item.invoiceNumber || item.invoiceNo || item.code || '开票记录',
    invoiceType: item.invoiceType || item.type || '-',
    invoiceCompany: item.invoiceCompany || item.companyName || item.company || '-',
    amountText: item.amount || item.totalAmount || '-',
    invoiceDateText: item.invoiceDate || item.date || '-',
    status: item.status || '已记录'
  }
}

function normalizePayment(item) {
  return {
    ...item,
    paymentNumber: item.paymentNumber || item.paymentNo || item.code || '回款记录',
    paymentAmountText: item.paymentAmount || item.amount || item.totalAmount || '-',
    paymentDateText: item.paymentDate || item.date || '-',
    paymentMethod: item.paymentMethod || item.method || '-',
    remark: item.remark || '-',
    status: item.status || '已记录'
  }
}

function buildFinanceListView(data) {
  const invoiceActive = data.tab === 'invoice'
  const invoices = Array.isArray(data.invoices) ? data.invoices : []
  const payments = Array.isArray(data.payments) ? data.payments : []
  return {
    tabs: [
      { key: 'invoice', text: '开票明细', className: `tab ${invoiceActive ? 'active' : ''}` },
      { key: 'payment', text: '回款明细', className: `tab ${invoiceActive ? '' : 'active'}` }
    ],
    showLoading: Boolean(data.loading),
    showInvoices: invoiceActive,
    showPayments: !invoiceActive,
    invoiceRows: invoices,
    paymentRows: payments,
    addText: invoiceActive ? '新增开票' : '新增回款',
    showAddAction: Boolean(data.canEdit),
    showDeleteAction: Boolean(data.canEdit),
    invoiceEmptyText: !data.loading && invoices.length === 0 ? '暂无开票明细' : '',
    paymentEmptyText: !data.loading && payments.length === 0 ? '暂无回款明细' : ''
  }
}

Page({
  data: {
    id: '',
    tab: 'invoice',
    invoices: [],
    payments: [],
    loading: false,
    canEdit: false,
    financeListView: buildFinanceListView({ tab: 'invoice', loading: false, invoices: [], payments: [], canEdit: false })
  },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('finance:view')) {
      wx.showToast({ title: '没有财务查看权限', icon: 'none' })
      wx.navigateBack()
      return
    }
    this.setData({ id: options.id || '', canEdit: getApp().globalData.demoMode || hasPermission('customer:edit') }, () => this.refreshFinanceListView())
    this.load()
  },

  /** 【M14 2026-10-04】从开票/回款表单返回时刷新列表(此前保存返回后列表不刷新)。
   *  对齐 customer/index.js 既有 onShow 模式：首次 onShow 跳过(onLoad 已加载)，加载中不重复请求。 */
  onShow() {
    if (!this.didInitialShow) {
      this.didInitialShow = true
      return
    }
    if (!this.data.loading) this.load()
  },
  refreshFinanceListView() {
    this.setData({ financeListView: buildFinanceListView(this.data) })
  },
  applyState(patch, callback) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, financeListView: buildFinanceListView(next) }, callback)
  },
  load() {
    if (!this.data.id) return
    if (getApp().globalData.demoMode) {
      const saved = readDemoFinance(this.data.id)
      return this.applyState({
        invoices: [{ id: 1, invoiceNo: 'DEMO-INV-001', invoiceType: '增值税专票', invoiceCompany: '东来智能科技有限公司', amount: 180000, invoiceDate: '2026-08-12', status: '已开票' }, ...saved.invoices].map(normalizeInvoice),
        payments: [{ id: 1, paymentNo: 'DEMO-PAY-001', amount: 80000, paymentDate: '2026-08-30', paymentMethod: '银行转账', remark: '首付款到账', status: '已回款' }, ...saved.payments].map(normalizePayment)
      })
    }
    this.applyState({ loading: true })
    Promise.all([getCustomerInvoices(this.data.id), getCustomerPayments(this.data.id)]).then(([invoices, payments]) => {
      this.applyState({ invoices: rows(invoices).map(normalizeInvoice), payments: rows(payments).map(normalizePayment) })
    }).catch((error) => showError(error, '财务明细加载失败')).finally(() => this.applyState({ loading: false }))
  },
  switchTab(e) { this.applyState({ tab: e.currentTarget.dataset.tab }) },
  deleteRecord(e) {
    if (!this.data.canEdit) return wx.showToast({ title: '没有财务编辑权限', icon: 'none' })
    const mode = e.currentTarget.dataset.mode
    const index = Number(e.currentTarget.dataset.index)
    const list = mode === 'payment' ? this.data.payments : this.data.invoices
    const item = list[index]
    if (!item || !item.id) return
    // 裁决 2026-09-24: 可删必留痕——editable 弹窗必填原因, 后端记审计快照+逻辑删
    wx.showModal({
      title: mode === 'payment' ? '删除回款记录' : '删除开票记录',
      content: '删除将留痕并可回查,请填写删除原因',
      editable: true,
      placeholderText: '删除原因(必填)',
      success: (res) => {
        if (!res.confirm) return
        const reason = (res.content || '').trim()
        if (!reason) return wx.showToast({ title: '请填写删除原因', icon: 'none' })
        const action = getApp().globalData.demoMode
          ? Promise.resolve(deleteDemoFinance(this.data.id, mode, item.id))
          : (mode === 'payment' ? deletePayment(this.data.id, item.id, reason) : deleteInvoice(this.data.id, item.id, reason))
        action.then(() => {
          wx.showToast({ title: '已删除(已留痕)', icon: 'success' })
          this.load()
        }).catch((error) => showError(error, '删除财务记录失败'))
      }
    })
  },
  openForm() { wx.navigateTo({ url: `/pages/customer/finance/form/index?id=${this.data.id}&mode=${this.data.tab}` }) },
  goBack() { wx.navigateBack() }
})
