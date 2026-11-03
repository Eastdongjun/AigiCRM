const { getCustomerStageAmounts, getCustomerInvoices, getCustomerPayments } = require('../../../api/customer')
const { hasPermission, requireLogin } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')

function toNumber(value) {
  const num = Number(value)
  return Number.isFinite(num) ? num : 0
}

function moneyText(value) {
  const n = toNumber(value)
  if (!n) return '0'
  return String(Math.round(n * 100) / 100)
}

function normalizeHistoryInvoice(item) {
  return {
    ...item,
    invoiceNumber: item.invoiceNumber || item.invoiceNo || '开票记录',
    invoiceType: item.invoiceType || '-',
    invoiceCompany: item.invoiceCompany || '-',
    amountText: item.amount != null ? item.amount : '-',
    invoiceDateText: item.invoiceDate || '-',
    // 【体检 P2 2026-10-12】状态中文映射+作废灰显标记——原直显英文码(confirmed/voided),
    // 用户无法辨识已作废; PC 端 FINANCE_LEDGER_STATUS 同口径
    status: financeStatusText(item.status),
    voided: item.status === 'voided'
  }
}

function normalizeHistoryPayment(item) {
  return {
    ...item,
    paymentNumber: item.paymentNumber || '回款记录',
    paymentAmountText: item.paymentAmount != null ? item.paymentAmount : '-',
    paymentDateText: item.paymentDate || '-',
    paymentMethod: item.paymentMethod || '-',
    remark: item.remark || '-',
    status: financeStatusText(item.status),
    voided: item.status === 'voided'
  }
}

/** 台账状态 → 中文(PC FINANCE_LEDGER_STATUS 同口径); pending 为批量导入预留态 */
function financeStatusText(status) {
  const map = { confirmed: '已确认', voided: '已作废', pending: '待核对' }
  return map[status] || status || '已记录'
}

/** 【裁决 2026-10-10】阶段总额视图: 判定三态由后端摘要回传(coreType/baseYear/年度金额), 前端只展示 */
function buildStageView(data) {
  const summary = data.stageSummary || {}
  const rows = Array.isArray(summary.rows) ? summary.rows : []
  const coreType = summary.coreType || ''
  const baseYear = toNumber(summary.baseYear)
  const stageRows = rows.map((row) => {
    const gap = toNumber(row.invoiceAmount) - toNumber(row.paymentAmount)
    return {
      ...row,
      invoiceText: moneyText(row.invoiceAmount),
      paymentText: moneyText(row.paymentAmount),
      // 阶段差额=开票-回款, 负值(回款超开票)自带负号
      gapText: moneyText(gap),
      updText: `${row.updatedByName || '-'} · ${String(row.updatedAt || '').replace('T', ' ').slice(0, 16)}`
    }
  })
  return {
    showLoading: Boolean(data.loading),
    hasVerdict: baseYear > 0,
    verdictText: baseYear ? `按 ${baseYear} 年度` : '',
    invoiceText: moneyText(summary.annualInvoiceAmount),
    paymentText: moneyText(summary.annualPaymentAmount),
    coreTagText: coreType === 'core' ? '真核心' : coreType === 'quasi' ? '争核心' : '非核心',
    coreTagClass: `stage-core-tag ${coreType === 'core' ? 'core' : coreType === 'quasi' ? 'quasi' : 'none'}`,
    // 【2026-11-03 门槛配置化】门槛数字用摘要回传(兜底 50/100), 规则长文不落死值
    stageNoteText: `半年末录「上半年 / 下半年」总额，年末录「全年」总额；全年行与上/下半年行独立录入，核心判定优先取全年行。真核心 = 判定年度开票、回款均 ≥ ${toNumber(summary.coreMajorWan) || 100} 万；争核心 = 均达到争核心门槛（${toNumber(summary.coreMinorWan) || 50} 万）未双达标；否则非核心。录入开票额 > 0 的阶段会刷新保护期起算日（按阶段末日期计，未到期的阶段按当天计，只延长不缩短），保护期 = 起算日 + 保护天数 + 已批准延期。`,
    stageRows,
    emptyText: !data.loading && stageRows.length === 0 ? '暂无阶段数据，点击下方「录入阶段数据」开始记录（半年末 / 年末录入总额）' : '',
    showAddAction: Boolean(data.canEdit),
    historyOpen: Boolean(data.historyOpen),
    historyToggleText: data.historyOpen ? '收起历史明细' : '展开历史明细（只读）',
    invoiceCount: (data.invoices || []).length,
    paymentCount: (data.payments || []).length,
    invoiceRows: data.invoices || [],
    paymentRows: data.payments || []
  }
}

Page({
  data: {
    id: '',
    loading: false,
    canEdit: false,
    stageSummary: null,
    historyOpen: false,
    historyLoaded: false,
    // 【2026-10-26 布局合理化】录入与保护期规则长文改折叠展示(默认收起)
    noteOpen: false,
    invoices: [],
    payments: [],
    stageView: buildStageView({ loading: false })
  },

  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('finance:view')) {
      wx.showToast({ title: '没有财务查看权限', icon: 'none' })
      wx.navigateBack()
      return
    }
    this.setData({ id: options.id || '', canEdit: getApp().globalData.demoMode || hasPermission('finance:manage') || (hasPermission('customer:edit') && hasPermission('finance:view')) }, () => this.refreshStageView())
    this.load()
  },

  /** 【M14 对齐】从阶段表单返回时刷新(保存返回后列表不刷新) */
  onShow() {
    if (!this.didInitialShow) {
      this.didInitialShow = true
      return
    }
    if (!this.data.loading) this.load()
  },

  refreshStageView() {
    this.setData({ stageView: buildStageView(this.data) })
  },

  applyState(patch, callback) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, stageView: buildStageView(next) }, callback)
  },

  load() {
    if (!this.data.id) return
    if (getApp().globalData.demoMode) {
      const demoSummary = {
        rows: [
          { periodLabel: '2026上半年', invoiceAmount: 60, paymentAmount: 50, updatedByName: '张三', updatedAt: '2026-07-02 10:30' },
          { periodLabel: '2025全年', invoiceAmount: 180, paymentAmount: 150, updatedByName: '张三', updatedAt: '2026-01-08 09:12' }
        ],
        baseYear: 2026,
        annualInvoiceAmount: 60,
        annualPaymentAmount: 50,
        coreType: 'quasi'
      }
      return this.applyState({ stageSummary: demoSummary, invoices: [], payments: [] })
    }
    this.applyState({ loading: true })
    getCustomerStageAmounts(this.data.id).then((summary) => {
      this.applyState({ stageSummary: summary || {} })
    }).catch((error) => showError(error, '阶段开票回款加载失败')).finally(() => this.applyState({ loading: false }))
  },

  /** 【2026-10-26 布局合理化】录入与保护期规则说明折叠开关(默认收起, 与历史明细同款折叠) */
  toggleNote() {
    this.setData({ noteOpen: !this.data.noteOpen })
  },

  /** 历史逐笔明细(2026-10-10 逐笔录入制时期的台账)只读折叠; 首次展开懒加载一次 */
  toggleHistory() {
    const next = !this.data.historyOpen
    if (next && !this.data.historyLoaded && !getApp().globalData.demoMode) {
      Promise.all([getCustomerInvoices(this.data.id), getCustomerPayments(this.data.id)])
        .then(([invoices, payments]) => {
          const invList = Array.isArray(invoices) ? invoices : []
          const payList = Array.isArray(payments) ? payments : []
          this.applyState({
            historyOpen: true, historyLoaded: true,
            invoices: invList.map(normalizeHistoryInvoice),
            payments: payList.map(normalizeHistoryPayment)
          })
        })
        .catch((error) => showError(error, '历史明细加载失败'))
      return
    }
    this.applyState({ historyOpen: next })
  },

  openForm(e) {
    if (!this.data.canEdit) return wx.showToast({ title: '没有财务编辑权限', icon: 'none' })
    const dataset = (e && e.currentTarget && e.currentTarget.dataset) || {}
    const periodQuery = dataset.year ? `&periodYear=${dataset.year}&periodType=${dataset.type}` : ''
    wx.navigateTo({ url: `/pages/customer/finance/form/index?id=${this.data.id}${periodQuery}` })
  },

  goBack() { wx.navigateBack() }
})
