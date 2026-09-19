const { addCustomerContact, updateCustomerContact } = require('../../../../api/customer')
const { hasPermission, requireLogin } = require('../../../../utils/auth')
const { showError } = require('../../../../utils/feedback')
const { saveDemoContact, updateDemoContact } = require('../../../../utils/demo-contacts')

function buildContactFormView(data) {
  const isEdit = Boolean(data.isEdit)
  const form = data.form || {}
  return {
    titleText: isEdit ? '编辑联系人' : '新增联系人',
    subtitleText: isEdit ? '维护客户关键人信息' : '补充客户关键人信息',
    fields: [
      { field: 'name', label: '姓名 *', value: form.name || '', placeholder: '请输入姓名', type: 'text' },
      { field: 'position', label: '职位', value: form.position || '', placeholder: '请输入职位', type: 'text' },
      { field: 'phone', label: '手机号', value: form.phone || '', placeholder: '请输入手机号', type: 'text' }
    ],
    remarkValue: form.remark || '',
    remarkPlaceholder: '请输入备注',
    submitText: data.saving ? '保存中...' : (isEdit ? '保存修改' : '保存联系人'),
    submitLoading: Boolean(data.saving),
    cancelText: isEdit ? '取消编辑' : '取消新增',
    cancelDisabled: Boolean(data.saving)
  }
}

Page({
  data: {
    id: '',
    contactId: '',
    isEdit: false,
    form: { name: '', position: '', phone: '', remark: '' },
    saving: false,
    contactFormView: buildContactFormView({ isEdit: false, form: { name: '', position: '', phone: '', remark: '' }, saving: false })
  },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && (!hasPermission('customer:view') || !hasPermission('customer:edit'))) { wx.showToast({ title: '没有联系人编辑权限', icon: 'none' }); wx.navigateBack(); return }
    this.applyState({
      id: options.id || '',
      contactId: options.contactId || '',
      isEdit: Boolean(options.contactId),
      form: {
        name: options.name || '',
        position: options.position || '',
        phone: options.phone || '',
        remark: options.remark || ''
      }
    })
  },
  applyState(patch, callback) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, contactFormView: buildContactFormView(next) }, callback)
  },
  input(e) { this.applyState({ [`form.${e.currentTarget.dataset.field}`]: e.detail.value }) },
  submit() {
    const form = {
      name: String(this.data.form.name || '').trim(),
      position: String(this.data.form.position || '').trim(),
      phone: String(this.data.form.phone || '').trim(),
      remark: String(this.data.form.remark || '').trim()
    }
    if (!form.name || this.data.saving) return wx.showToast({ title: '请填写联系人姓名', icon: 'none' })
    this.applyState({ saving: true })
    const action = this.data.isEdit
      ? (getApp().globalData.demoMode
        ? Promise.resolve(updateDemoContact(this.data.id, this.data.contactId, form))
        : updateCustomerContact(this.data.id, this.data.contactId, form))
      : (getApp().globalData.demoMode
        ? Promise.resolve(saveDemoContact(this.data.id, form))
        : addCustomerContact(this.data.id, form))
    action.then(() => { wx.showToast({ title: '已保存', icon: 'success' }); setTimeout(() => wx.navigateBack(), 500) }).catch((error) => showError(error, '保存失败')).finally(() => this.applyState({ saving: false }))
  },
  goBack() {
    if (this.data.saving) return
    const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
    if (pages.length > 1) {
      wx.navigateBack()
      return
    }
    wx.navigateTo({ url: `/pages/customer/contact/index?id=${this.data.id}` })
  }
})
