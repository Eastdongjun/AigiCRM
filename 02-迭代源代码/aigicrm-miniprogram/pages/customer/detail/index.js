const { deleteCustomer, getCustomer, getCustomerContacts, getCustomerLogs, getInvoiceSummary, getPaymentSummary, getCustomerIndicator, saveCustomerRelationship, releaseCustomer } = require('../../../api/customer')
const { submitApproval } = require('../../../api/approval')
const { hasPermission, requireLogin } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')
const { readDemoCustomers } = require('../../../utils/demo-customers')

function normalizeStatusKey(status) {
  return status === undefined || status === null ? '' : String(status).trim().toLowerCase()
}

function customerStatusLabel(status) {
  const labels = {
    normal: '正常',
    warning: '预警',
    protected: '保护期',
    expired: '超期',
    sea: '公海',
    active: '正常',
    available: '公海'
  }
  return labels[normalizeStatusKey(status)] || status || '正常'
}

function normalizeDetail(item) {
  const terminalValue = item.hasTerminal
  const status = item.status || '正常'
  return {
    id: item.id,
    customerCode: item.customerCode || '',
    customerName: item.customerName || item.name || '未命名客户',
    invoiceName: item.invoiceName || '',
    industry: item.industry || '其他',
    devType: item.devType || '未分类',
    capacity: item.capacity || '',
    hasTerminal: terminalValue,
    terminalLabel: terminalValue === 1 || terminalValue === true || terminalValue === '1' ? '终端客户' : (terminalValue === 0 || terminalValue === false || terminalValue === '0' ? '非终端客户' : '-'),
    ownerName: item.ownerName || item.salesperson || '未分配',
    managerName: item.managerName || '',
    deptName: item.deptName || '',
    officeName: item.officeName || item.deptName || '未设置办事处',
    regionName: item.regionName || '',
    groupName: item.groupName || '',
    branchCompany: item.branchCompany || '',
    unitName: item.unitName || '',
    province: item.province || '',
    city: item.city || '',
    district: item.district || '',
    areaText: `${item.province || ''}${item.city || ''}${item.district || ''}` || '',
    address: item.address || '',
    status,
    statusLabel: customerStatusLabel(status),
    registerDate: item.registerDate || '',
    lastInvoiceDate: item.lastInvoiceDate || '',
    expireDate: item.expireDate || '',
    remark: item.remark || ''
  }
}

function toNumber(value) {
  const num = Number(value)
  return Number.isFinite(num) ? num : 0
}

function amountOf(summary, ...keys) {
  const source = summary || {}
  for (const key of keys) {
    const value = toNumber(source[key])
    if (value) return value
  }
  return 0
}

function statusView(met, okText, warnText) {
  return {
    statusClass: met ? 'ok' : 'warn',
    statusText: met ? okText : warnText
  }
}

function buildIndicatorSummary(invoiceSummary, paymentSummary, indicator) {
  const invoiceAmount = amountOf(invoiceSummary, 'totalAmount', 'invoiceAmount')
  const paymentAmount = amountOf(paymentSummary, 'totalAmount', 'paidAmount')
  const scoreTotal = ['relationshipScore', 'indicator02', 'indicator03', 'indicator04', 'indicator05']
    .reduce((sum, key) => sum + toNumber((indicator || {})[key]), 0)
  const invoiceMet = invoiceAmount >= 100
  const paymentMet = paymentAmount >= 100
  const scoreMet = scoreTotal >= 12
  const invoiceView = statusView(invoiceMet, '达标', '未达标')
  const paymentView = statusView(paymentMet, '达标', '未达标')
  const scoreView = statusView(scoreMet, '达标', '未达标')
  const coreView = statusView(invoiceMet && paymentMet && scoreMet, '已满足', '未满足')
  return {
    invoiceAmount,
    paymentAmount,
    invoiceMet,
    invoiceStatusClass: invoiceView.statusClass,
    invoiceStatusText: invoiceView.statusText,
    paymentMet,
    paymentStatusClass: paymentView.statusClass,
    paymentStatusText: paymentView.statusText,
    bothMet: invoiceMet && paymentMet,
    scoreTotal,
    scoreMet,
    scoreStatusClass: scoreView.statusClass,
    scoreStatusText: scoreView.statusText,
    coreMet: invoiceMet && paymentMet && scoreMet,
    coreStatusClass: coreView.statusClass,
    coreStatusText: coreView.statusText,
    coreSummaryText: invoiceMet && paymentMet && scoreMet ? '核心客户' : '待提升'
  }
}

function buildRelationshipOptions(options, indicator, canEdit) {
  const score = toNumber((indicator || {}).relationshipScore)
  return options.map((item) => ({
    ...item,
    className: `relationship-option${score === item.value ? ' active' : ''}${canEdit ? '' : ' disabled'}`
  }))
}

function textOrDash(value) {
  return value === undefined || value === null || value === '' ? '-' : String(value)
}

function buildContactView(item) {
  return {
    ...item,
    nameText: item.name || item.contactName || '未命名联系人',
    phoneText: item.mobile || item.phone || '-'
  }
}

function buildLogView(item) {
  return {
    ...item,
    contentText: item.content || item.remark || '跟进记录',
    userText: `记录人：${item.userName || item.creatorName || item.createByName || '-'}`,
    timeText: item.createdAt || item.createTime || '-'
  }
}

function buildDetailView(data) {
  const detail = data.detail || null
  const loading = Boolean(data.loading)
  const indicator = data.indicator || {}
  const contacts = (data.contacts || []).map(buildContactView)
  const logs = (data.logs || []).map(buildLogView)
  const invoiceSummary = data.invoiceSummary || {}
  const paymentSummary = data.paymentSummary || {}
  const coreSummary = buildIndicatorSummary(invoiceSummary, paymentSummary, indicator)
  const hasDetail = Boolean(detail)
  const canEdit = Boolean(data.canEdit)
  const canDelete = Boolean(data.canDelete)
  const canViewContacts = Boolean(data.canViewContacts)
  const canViewFinance = Boolean(data.canViewFinance)
  const fromSea = data.from === 'sea'
  const indicatorRows = [
    { key: 'relationshipScore', label: '高层（客情程度）', valueText: `${toNumber(indicator.relationshipScore)} / 3` },
    { key: 'indicator02', label: '渠道（编码数量）', valueText: `${toNumber(indicator.indicator02)} / 3` },
    { key: 'indicator03', label: '基层（分厂车间）', valueText: `${toNumber(indicator.indicator03)} / 3` },
    { key: 'indicator04', label: '产品（密封润滑防腐）', valueText: `${toNumber(indicator.indicator04)} / 3` },
    { key: 'indicator05', label: '项目（项目资金）', valueText: `${toNumber(indicator.indicator05)} / 3` }
  ]
  return {
    showLoading: loading,
    loadingText: '加载中...',
    hasDetail,
    showEmpty: !loading && !detail,
    showContactsSection: hasDetail && canViewContacts,
    showFinanceSection: hasDetail && canViewFinance,
    showCoreVerdictSection: hasDetail,
    showExtendAction: hasDetail,
    showRelationshipSection: hasDetail,
    showIndicatorSection: hasDetail,
    showIndicatorEditAction: hasDetail && canEdit,
    showReleaseSection: hasDetail && !fromSea && canEdit,
    showDeleteSection: hasDetail && canDelete,
    showActionCard: hasDetail,
    showContactsAction: hasDetail && canViewContacts,
    showLogAction: hasDetail && canEdit,
    titleText: detail ? detail.customerName : '',
    statusText: detail ? (detail.statusLabel || customerStatusLabel(detail.status)) : '',
    industryText: detail ? textOrDash(detail.industry) : '-',
    devTypeText: detail ? textOrDash(detail.devType) : '-',
    baseRows: detail ? [
      ['客户编号', detail.customerCode],
      ['开票名称', detail.invoiceName],
      ['产能(万吨)', detail.capacity],
      ['终端', detail.terminalLabel],
      ['集团', detail.groupName],
      ['分公司', detail.branchCompany],
      ['所属单位', detail.unitName],
      ['负责人', detail.ownerName],
      ['管理上级', detail.managerName],
      ['大区', detail.regionName],
      ['办事处', detail.officeName],
      ['所属部门', detail.deptName],
      ['省市县', detail.areaText],
      ['注册日', detail.registerDate],
      ['最近开票', detail.lastInvoiceDate],
      ['保护到期', detail.expireDate],
      ['地址', detail.address]
    ].map(([label, value]) => ({ label, valueText: textOrDash(value) })) : [],
    remarkText: detail ? textOrDash(detail.remark) : '-',
    contacts,
    showNoContacts: contacts.length === 0,
    logs,
    showNoLogs: logs.length === 0,
    financeRows: [
      { key: 'invoice', label: '开票金额', valueText: textOrDash(invoiceSummary.totalAmount || invoiceSummary.invoiceAmount) },
      { key: 'payment', label: '回款金额', valueText: textOrDash(paymentSummary.totalAmount || paymentSummary.paidAmount) },
      { key: 'unpaid', label: '待回款', valueText: textOrDash(paymentSummary.unpaidAmount || paymentSummary.pendingAmount) }
    ],
    coreVerdictText: coreSummary.coreSummaryText,
    coreVerdictStatusText: coreSummary.coreStatusText,
    coreVerdictStatusClass: coreSummary.coreStatusClass,
    coreVerdictRows: [
      { key: 'invoice', label: '开票门槛', valueText: `${coreSummary.invoiceAmount} 万`, statusText: coreSummary.invoiceStatusText, statusClass: coreSummary.invoiceStatusClass },
      { key: 'payment', label: '回款门槛', valueText: `${coreSummary.paymentAmount} 万`, statusText: coreSummary.paymentStatusText, statusClass: coreSummary.paymentStatusClass },
      { key: 'relationship', label: '客情评分', valueText: `${toNumber(indicator.relationshipScore)} / 3`, statusText: `${toNumber(indicator.relationshipScore)}分`, statusClass: toNumber(indicator.relationshipScore) >= 2 ? 'ok' : 'warn' },
      { key: 'score', label: '五项评分', valueText: `${coreSummary.scoreTotal} / 15`, statusText: coreSummary.scoreStatusText, statusClass: coreSummary.scoreStatusClass }
    ],
    relationshipScoreText: String(toNumber(indicator.relationshipScore)),
    indicatorRows
  }
}

Page({
  data: {
    id: '',
    from: '',
    detail: null,
    loading: false,
    contacts: [],
    logs: [],
    invoiceSummary: null,
    paymentSummary: null,
    indicator: {},
    indicatorSummary: buildIndicatorSummary({}, {}, {}),
    relationshipOptions: [
      { label: '陌生', value: 0 },
      { label: '知道', value: 1 },
      { label: '熟悉', value: 2 },
      { label: '支持', value: 3 }
    ],
    relationshipOptionsView: buildRelationshipOptions([
      { label: '陌生', value: 0 },
      { label: '知道', value: 1 },
      { label: '熟悉', value: 2 },
      { label: '支持', value: 3 }
    ], {}, false),
    canEdit: false,
    canDelete: false,
    canViewFinance: false,
    canViewContacts: false,
    detailView: buildDetailView({ detail: null, contacts: [], logs: [], invoiceSummary: {}, paymentSummary: {}, indicator: {} })
  },

  setDetailData(patch) {
    const nextData = { ...this.data, ...patch }
    this.setData({ ...patch, detailView: buildDetailView(nextData) })
  },

  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('customer:view')) {
      wx.showToast({ title: '没有客户查看权限', icon: 'none' })
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    const demoMode = getApp().globalData.demoMode
    this.setDetailData({
      id: options.id || '',
      from: options.from || '',
      canEdit: demoMode || hasPermission('customer:edit'),
      canDelete: !demoMode && hasPermission('customer:delete'),
      canViewFinance: demoMode || hasPermission('finance:view'),
      canViewContacts: demoMode || hasPermission('contact:view'),
      relationshipOptionsView: buildRelationshipOptions(this.data.relationshipOptions, this.data.indicator, demoMode || hasPermission('customer:edit'))
    })
    this.loadDetail()
  },

  loadDetail() {
    if (!this.data.id) {
      wx.showToast({ title: '缺少客户ID', icon: 'none' })
      return
    }
    if (getApp().globalData.demoMode) {
      const saved = readDemoCustomers().find((item) => String(item.id) === String(this.data.id))
      const indicator = { relationshipScore: 0, indicator02: 0, indicator03: 0, indicator04: 0, indicator05: 0 }
      this.setDetailData({
        detail: normalizeDetail(saved || {
          id: this.data.id,
          customerCode: 'DEMO-' + this.data.id,
          customerName: this.data.from === 'sea' ? '公海客户样例' : '山西晋能集团中阳煤业',
          industry: '煤矿',
          capacity: '600',
          hasTerminal: 1,
          ownerName: this.data.from === 'sea' ? '原负责人' : '张三',
          managerName: '王总',
          deptName: '销售一部',
          officeName: '晋蒙业务部',
          devType: '成熟创新',
          status: this.data.from === 'sea' ? '公海只读' : '保护期',
          registerDate: '2026-01-01',
          lastInvoiceDate: '2026-08-12',
          expireDate: '2026-12-31',
          invoiceName: '山西晋能集团有限公司',
          groupName: '晋能集团',
          branchCompany: '晋能控股装备制造集团',
          unitName: '中阳煤业',
          province: '山西省',
          city: '吕梁市',
          district: '中阳县',
          address: '中阳县煤化工园区',
          remark: '移动端展示 PC 基础信息备注'
        }),
        invoiceSummary: { totalAmount: 120 },
        paymentSummary: { totalAmount: 105, unpaidAmount: 15 },
        indicator,
        relationshipOptionsView: buildRelationshipOptions(this.data.relationshipOptions, indicator, this.data.canEdit),
        indicatorSummary: buildIndicatorSummary(
          { totalAmount: 120 },
          { totalAmount: 105, unpaidAmount: 15 },
          indicator
        )
      })
      return
    }

    this.setDetailData({ loading: true })
    getCustomer(this.data.id).then((detail) => {
      this.setDetailData({ detail: normalizeDetail(detail || {}) })
      const contactsRequest = this.data.canViewContacts ? getCustomerContacts(this.data.id) : Promise.resolve([])
      const financeRequest = this.data.canViewFinance
        ? Promise.all([getInvoiceSummary(this.data.id), getPaymentSummary(this.data.id)])
        : Promise.resolve([{}, {}])
      return Promise.all([contactsRequest, getCustomerLogs(this.data.id), financeRequest, getCustomerIndicator(this.data.id)])
    }).then(([contacts, logs, [invoiceSummary, paymentSummary], indicator]) => {
      const normalizedIndicator = indicator || {}
      this.setDetailData({
        contacts: contacts || [],
        logs: logs || [],
        invoiceSummary: invoiceSummary || {},
        paymentSummary: paymentSummary || {},
        indicator: normalizedIndicator,
        relationshipOptionsView: buildRelationshipOptions(this.data.relationshipOptions, normalizedIndicator, this.data.canEdit),
        indicatorSummary: buildIndicatorSummary(invoiceSummary || {}, paymentSummary || {}, normalizedIndicator)
      })
    }).catch((error) => showError(error, '无权查看或客户不存在')).finally(() => {
      this.setDetailData({ loading: false })
    })
  },

  editCustomer() {
    if (!hasPermission('customer:edit') && !getApp().globalData.demoMode) return wx.showToast({ title: '没有编辑权限', icon: 'none' })
    wx.navigateTo({ url: `/pages/customer/form/index?id=${this.data.id}` })
  },


  submitExtendApproval() {
    if (!this.data.id) return
    if (!getApp().globalData.demoMode && !hasPermission('approval:view')) return wx.showToast({ title: '没有审批提交权限', icon: 'none' })
    if (getApp().globalData.demoMode) return wx.showToast({ title: '延期申请已提交', icon: 'success' })
    submitApproval({ type: 'extend', customerId: String(this.data.id), actionAfterApprove: '保护期+360天' }).then(() => {
      wx.showToast({ title: '延期申请已提交', icon: 'success' })
    }).catch((error) => showError(error, '提交失败'))
  },

  releaseToSea() {
    if (!this.data.id || !hasPermission('customer:edit')) return wx.showToast({ title: '没有释放权限', icon: 'none' })
    wx.showModal({
      title: '释放到公海',
      content: '确认将该客户释放到公海池？',
      success: (res) => {
        if (!res.confirm) return
        if (getApp().globalData.demoMode) return wx.showToast({ title: '释放申请已提交', icon: 'success' })
        releaseCustomer(this.data.id, '移动端释放').then(() => {
          wx.showToast({ title: '已释放到公海', icon: 'success' })
          this.goBack()
        }).catch((error) => showError(error, '释放失败'))
      }
    })
  },

  deleteCurrentCustomer() {
    if (!this.data.id || !this.data.canDelete) return wx.showToast({ title: '没有客户删除权限', icon: 'none' })
    wx.showModal({
      title: '删除客户',
      content: `确认删除“${(this.data.detail && this.data.detail.customerName) || '该客户'}”？删除后不可恢复。`,
      success: (res) => {
        if (!res.confirm) return
        deleteCustomer(this.data.id).then(() => {
          wx.showToast({ title: '客户已删除', icon: 'success' })
          setTimeout(() => wx.redirectTo({ url: '/pages/customer/index' }), 300)
        }).catch((error) => showError(error, '删除客户失败'))
      }
    })
  },

  goBack() {
    if (getCurrentPages().length > 1) {
      wx.navigateBack()
    } else if (this.data.from === 'sea') {
      wx.navigateTo({ url: '/pages/sea/index' })
    } else {
      wx.navigateTo({ url: '/pages/customer/index' })
    }
  }
  ,openFinance() {
    if (!this.data.canViewFinance) return wx.showToast({ title: '没有财务查看权限', icon: 'none' })
    wx.navigateTo({ url: `/pages/customer/finance/index?id=${this.data.id}` })
  },
  openContacts() {
    if (!this.data.canViewContacts) return wx.showToast({ title: '没有联系人查看权限', icon: 'none' })
    wx.navigateTo({ url: `/pages/customer/contact/index?id=${this.data.id}` })
  },
  openShares() {
    if (!this.data.detail) return
    wx.navigateTo({ url: `/pages/customer/share/index?id=${this.data.id}` })
  },
  openLogForm() {
    if (!this.data.canEdit) return wx.showToast({ title: '没有编辑权限', icon: 'none' })
    wx.navigateTo({ url: `/pages/customer/log/index?id=${this.data.id}` })
  },
  openTimeline() {
    wx.navigateTo({ url: `/pages/customer/timeline/index?id=${this.data.id}` })
  },
  openIndicatorForm() {
    if (!this.data.canEdit) return wx.showToast({ title: '没有编辑权限', icon: 'none' })
    wx.navigateTo({ url: `/pages/customer/indicator/index?id=${this.data.id}` })
  },
  selectRelationship(event) {
    if (!this.data.canEdit) return wx.showToast({ title: '没有编辑权限', icon: 'none' })
    const score = Number(event.currentTarget.dataset.score) || 0
    if (getApp().globalData.demoMode) {
      const indicator = { ...(this.data.indicator || {}), relationshipScore: score }
      this.setDetailData({
        indicator,
        relationshipOptionsView: buildRelationshipOptions(this.data.relationshipOptions, indicator, this.data.canEdit),
        indicatorSummary: buildIndicatorSummary(this.data.invoiceSummary || {}, this.data.paymentSummary || {}, indicator)
      })
      return wx.showToast({ title: '客情已保存', icon: 'success' })
    }
    saveCustomerRelationship(this.data.id, score)
      .then(() => getCustomerIndicator(this.data.id))
      .then((indicator) => {
        const normalizedIndicator = indicator || {}
        this.setDetailData({
          indicator: normalizedIndicator,
          relationshipOptionsView: buildRelationshipOptions(this.data.relationshipOptions, normalizedIndicator, this.data.canEdit),
          indicatorSummary: buildIndicatorSummary(this.data.invoiceSummary || {}, this.data.paymentSummary || {}, normalizedIndicator)
        })
        wx.showToast({ title: '客情已保存', icon: 'success' })
      })
      .catch((error) => showError(error, '客情关系保存失败'))
  }
})
