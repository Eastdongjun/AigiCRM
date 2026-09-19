const { getCustomerIndicator, saveCustomerIndicator } = require('../../../api/customer')
const { hasPermission, requireLogin } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')
const { readDemoCustomers, saveDemoCustomer } = require('../../../utils/demo-customers')

const fields = ['relationshipScore', 'indicator02', 'indicator03', 'indicator04', 'indicator05']
const indicatorItems = [
  { field: 'relationshipScore', name: '高层', desc: '客情程度', options: ['陌生', '知道', '熟悉', '支持'] },
  { field: 'indicator02', name: '渠道', desc: '编码数量', options: ['一两个码', '有效编码', '多种编码', '全部畅通'] },
  { field: 'indicator03', name: '基层', desc: '分厂车间', options: ['一两个', '1/3分厂', '2/3分厂', '全部部门'] },
  { field: 'indicator04', name: '产品', desc: '密封润滑防腐', options: ['一两个', '一大类', '两大类', '三大类'] },
  { field: 'indicator05', name: '项目', desc: '项目资金', options: ['日常计划', '改造大修', '安全环保', '新建配套'] }
]

function buildIndicatorItems(form) {
  return indicatorItems.map((item) => ({
    ...item,
    value: Number(form[item.field]) || 0,
    optionsView: item.options.map((label, score) => ({
      label,
      score,
      className: `score-option${Number(form[item.field]) === score ? ' active' : ''}`
    }))
  }))
}

function buildIndicatorView(data) {
  return {
    showLoading: Boolean(data.loading),
    loadingText: '加载中...',
    showForm: !data.loading,
    submitLoading: Boolean(data.saving),
    submitText: data.saving ? '保存中' : '保存指标'
  }
}

Page({
  data: {
    id: '',
    form: {},
    indicatorItems: buildIndicatorItems({}),
    loading: false,
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
      const form = fields.reduce((result, field) => ({ ...result, [field]: Number(source[field]) || 0 }), {})
      return this.applyState({ form, indicatorItems: buildIndicatorItems(form) })
    }
    this.applyState({ loading: true })
    getCustomerIndicator(this.data.id).then((indicator) => {
      const form = fields.reduce((result, field) => ({ ...result, [field]: Number(indicator && indicator[field]) || 0 }), {})
      this.applyState({ form, indicatorItems: buildIndicatorItems(form) })
    }).catch((error) => showError(error, '指标加载失败')).finally(() => this.applyState({ loading: false }))
  },
  selectScore(e) {
    const field = e.currentTarget.dataset.field
    const score = Number(e.currentTarget.dataset.score) || 0
    const form = { ...this.data.form, [field]: score }
    this.applyState({ form, indicatorItems: buildIndicatorItems(form) })
  },
  submit() {
    if (this.data.saving) return
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
