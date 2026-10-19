const { saveCustomerStageAmount } = require('../../../../api/customer')
const { hasPermission, requireLogin } = require('../../../../utils/auth')
const { showError } = require('../../../../utils/feedback')
// 【D24 2026-10-04】返回兜底逻辑收敛到组内 shared(各表单共用同一份)
const { navigateBackWithFallback } = require('../../shared')

const PERIOD_TYPES = [
  { value: 'H1', label: '上半年' },
  { value: 'H2', label: '下半年' },
  { value: 'FY', label: '全年' }
]

function buildYearOptions() {
  const current = new Date().getFullYear()
  const years = []
  for (let y = current + 1; y >= 2015; y--) years.push(String(y))
  return years
}

function buildStageFormView(data) {
  const form = data.form || {}
  const periodType = PERIOD_TYPES.find((item) => item.value === form.periodType) || PERIOD_TYPES[0]
  return {
    title: data.isEdit ? '修改阶段数据' : '录入阶段数据',
    sectionTitle: '周期与金额（单位：万元）',
    yearText: String(form.periodYear || ''),
    periodText: periodType.label,
    invoiceValue: form.invoiceAmount === null || form.invoiceAmount === undefined ? '' : String(form.invoiceAmount),
    paymentValue: form.paymentAmount === null || form.paymentAmount === undefined ? '' : String(form.paymentAmount),
    submitText: data.saving ? '保存中...' : '保存',
    submitLoading: Boolean(data.saving),
    cancelText: '取消',
    cancelDisabled: Boolean(data.saving)
  }
}

Page({
  data: {
    id: '',
    isEdit: false,
    saving: false,
    yearOptions: buildYearOptions(),
    periodTypeOptions: PERIOD_TYPES,
    form: { periodYear: new Date().getFullYear(), periodType: 'H1', invoiceAmount: null, paymentAmount: null },
    stageFormView: null
  },

  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && (!hasPermission('finance:view') || !hasPermission('customer:edit'))) {
      wx.showToast({ title: '没有财务编辑权限', icon: 'none' })
      wx.navigateBack()
      return
    }
    // 编辑模式: 列表页带 periodYear/periodType 预填(同周期重复保存即覆盖修改)
    const periodYear = Number(options.periodYear) || new Date().getFullYear()
    const periodType = PERIOD_TYPES.some((item) => item.value === options.periodType) ? options.periodType : 'H1'
    const isEdit = Boolean(options.periodYear)
    const form = { periodYear, periodType, invoiceAmount: null, paymentAmount: null }
    this.setData({ id: options.id || '', isEdit, form, stageFormView: buildStageFormView({ ...this.data, isEdit, form }) })
  },

  refreshStageFormView() {
    this.setData({ stageFormView: buildStageFormView(this.data) })
  },

  applyState(patch, callback) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, stageFormView: buildStageFormView(next) }, callback)
  },

  // 【C3 2026-10-19】统一整对象更新 form:applyState 用 next 重建视图,
  // 传 'form.xxx' 路径键时 next.form 仍是旧对象,选择年份/周期/输入金额后视图落后一拍
  chooseYear(e) {
    const periodYear = Number(this.data.yearOptions[Number(e.detail.value)]) || new Date().getFullYear()
    this.applyState({ form: { ...this.data.form, periodYear } })
  },

  choosePeriodType(e) {
    const item = PERIOD_TYPES[Number(e.detail.value)] || PERIOD_TYPES[0]
    this.applyState({ form: { ...this.data.form, periodType: item.value } })
  },

  input(e) {
    const field = e.currentTarget.dataset.field
    const raw = String(e.detail.value || '').trim()
    const value = raw === '' ? null : Number(raw)
    this.applyState({ form: { ...this.data.form, [field]: Number.isFinite(value) && value >= 0 ? value : null } })
  },

  submit() {
    const form = this.data.form
    if (this.data.saving) return
    if (!form.periodYear) return wx.showToast({ title: '请选择周期', icon: 'none' })
    const hasInvoice = form.invoiceAmount !== null && form.invoiceAmount !== undefined && form.invoiceAmount > 0
    const hasPayment = form.paymentAmount !== null && form.paymentAmount !== undefined && form.paymentAmount > 0
    if (!hasInvoice && !hasPayment) return wx.showToast({ title: '开票额与回款额至少填一项且大于 0', icon: 'none' })
    this.applyState({ saving: true })
    const payload = {
      periodYear: form.periodYear,
      periodType: form.periodType,
      invoiceAmount: hasInvoice ? form.invoiceAmount : null,
      paymentAmount: hasPayment ? form.paymentAmount : null
    }
    const action = getApp().globalData.demoMode
      ? Promise.resolve()
      : saveCustomerStageAmount(this.data.id, payload)
    action.then(() => {
      wx.showToast({ title: '已保存，判定与保护期已同步', icon: 'none', duration: 1800 })
      setTimeout(() => wx.navigateBack(), 600)
    }).catch((error) => showError(error, '保存失败')).finally(() => this.applyState({ saving: false }))
  },

  goBack() {
    if (this.data.saving) return
    navigateBackWithFallback(`/pages/customer/finance/index?id=${this.data.id}`)
  }
})
