const { getCustomerIndicator, saveCustomerIndicator } = require('../../../api/customer')
const { hasPermission, requireLogin } = require('../../../utils/auth')
const { showError, showErrorWithRetry } = require('../../../utils/feedback')
const { readDemoCustomers, saveDemoCustomer } = require('../../../utils/demo-customers')
// 【D24/D25 2026-10-04】五项指标配置/视图构建、核心门槛提示统一引用组内 shared(与客户表单/详情共用同一出处)
const { INDICATOR_FIELDS, buildIndicatorItems, CORE_THRESHOLD_TIP } = require('../shared')

function buildIndicatorView(data) {
  const loadFailed = Boolean(data.loadFailed)
  return {
    // 【C4 2026-10-19】加载失败复用加载卡片展示错误态,不展示全 0 可编辑表单(失败时 form 仍是 {})
    showLoading: Boolean(data.loading) || loadFailed,
    loadingText: loadFailed ? '指标加载失败，请点击"重试"重新加载' : '加载中...',
    showForm: !data.loading && !loadFailed,
    submitLoading: Boolean(data.saving),
    submitText: data.saving ? '保存中' : '保存指标'
  }
}

Page({
  data: {
    id: '',
    form: {},
    indicatorItems: buildIndicatorItems({}),
    // 【D25 2026-10-04】门槛提示由 shared 常量拼装，wxml 不再硬编码 100 万/12 分
    coreThresholdTip: CORE_THRESHOLD_TIP,
    loading: false,
    loadFailed: false,
    saving: false,
    indicatorView: buildIndicatorView({ loading: false, saving: false })
  },
  applyState(patch) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, indicatorView: buildIndicatorView(next) })
  },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && (!hasPermission('customer:view') || !hasPermission('customer:edit'))) { wx.showToast({ title: '没有指标编辑权限', icon: 'none' }); wx.navigateBack(); return }
    this.applyState({ id: options.id || '' })
    this.load()
  },
  load() {
    if (!this.data.id) return
    if (getApp().globalData.demoMode) {
      const customer = readDemoCustomers().find((item) => String(item.id) === String(this.data.id))
      const source = (customer && (customer.indicator || customer.customerIndicator)) || {}
      const form = INDICATOR_FIELDS.reduce((result, field) => ({ ...result, [field]: Number(source[field]) || 0 }), {})
      return this.applyState({ form, indicatorItems: buildIndicatorItems(form) })
    }
    this.applyState({ loading: true, loadFailed: false })
    getCustomerIndicator(this.data.id).then((indicator) => {
      const form = INDICATOR_FIELDS.reduce((result, field) => ({ ...result, [field]: Number(indicator && indicator[field]) || 0 }), {})
      this.applyState({ form, indicatorItems: buildIndicatorItems(form) })
    }).catch((error) => {
      // 【C4 2026-10-19】失败置错误态:隐藏表单(避免把空 form 渲染成五项 0),并弹"重试"重新加载
      this.applyState({ loadFailed: true })
      showErrorWithRetry(error, '指标加载失败', () => this.load())
    }).finally(() => this.applyState({ loading: false }))
  },
  selectScore(e) {
    const field = e.currentTarget.dataset.field
    const score = Number(e.currentTarget.dataset.score) || 0
    const form = { ...this.data.form, [field]: score }
    this.applyState({ form, indicatorItems: buildIndicatorItems(form) })
  },
  submit() {
    // 【C4 2026-10-19】未加载成功(加载中/失败)时 form 仍是空载荷,拒绝提交
    if (this.data.saving || this.data.loading || this.data.loadFailed) return
    this.applyState({ saving: true })
    const action = getApp().globalData.demoMode ? this.saveDemoIndicator() : saveCustomerIndicator(this.data.id, this.data.form)
    action.then(() => { wx.showToast({ title: '指标已保存', icon: 'success' }); setTimeout(() => wx.navigateBack(), 500) }).catch((error) => showError(error, '指标保存失败')).finally(() => this.applyState({ saving: false }))
  },
  saveDemoIndicator() {
    const customer = readDemoCustomers().find((item) => String(item.id) === String(this.data.id))
    if (!customer) return Promise.resolve()
    saveDemoCustomer({ ...customer, indicator: { ...this.data.form } }, this.data.id)
    return Promise.resolve()
  },
  goBack() { wx.navigateBack() }
})
