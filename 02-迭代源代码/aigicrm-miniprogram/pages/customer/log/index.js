const { addCustomerLog } = require('../../../api/customer')
const { hasPermission, requireLogin } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')
// 【D24 2026-10-04】返回兜底逻辑收敛到组内 shared(各表单共用同一份)
const { navigateBackWithFallback } = require('../shared')

const MAX_CONTENT_LENGTH = 1000

function buildLogFormView(data) {
  const content = data.content || ''
  return {
    contentValue: content,
    contentMaxLength: MAX_CONTENT_LENGTH,
    placeholder: '请输入跟进内容，例如：已完成方案沟通，下一步确认商务条款',
    counterText: `${content.length} / ${MAX_CONTENT_LENGTH}`,
    submitText: data.saving ? '保存中...' : '保存记录',
    submitLoading: Boolean(data.saving),
    cancelText: '取消记录',
    cancelDisabled: Boolean(data.saving)
  }
}

Page({
  data: {
    id: '',
    content: '',
    saving: false,
    logFormView: buildLogFormView({ content: '', saving: false })
  },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && (!hasPermission('customer:view') || !hasPermission('customer:edit'))) {
      wx.showToast({ title: '没有跟进记录权限', icon: 'none' })
      wx.navigateBack()
      return
    }
    this.setData({ id: options.id || '' })
  },
  applyState(patch, callback) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, logFormView: buildLogFormView(next) }, callback)
  },
  onContentInput(e) { this.applyState({ content: e.detail.value }) },
  submit() {
    const content = this.data.content.trim()
    if (!content || this.data.saving) return wx.showToast({ title: '请填写跟进内容', icon: 'none' })
    this.applyState({ saving: true })
    const action = getApp().globalData.demoMode ? Promise.resolve() : addCustomerLog(this.data.id, { content })
    action.then(() => {
      wx.showToast({ title: '已记录', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 500)
    }).catch((error) => showError(error, '记录失败')).finally(() => this.applyState({ saving: false }))
  },
  goBack() {
    if (this.data.saving) return
    navigateBackWithFallback(`/pages/customer/detail/index?id=${this.data.id}`)
  }
})
