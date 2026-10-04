const { getCustomerContacts, deleteCustomerContact } = require('../../../api/customer')
const { hasPermission, requireLogin } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')
const { readDemoContacts, deleteDemoContact } = require('../../../utils/demo-contacts')

/** 【D28 2026-10-04】手机号取值统一优先级：后端契约字段 phone 优先，mobile 仅兼容历史演示数据。
 *  此前本页两处相反(openDetail 用 mobile||phone、editContact 用 phone||mobile)，取值口径漂移。 */
const phoneOf = (item) => item.phone || item.mobile || ''

function buildContactItem(item, index) {
  const phone = phoneOf(item)
  return {
    ...item,
    viewId: item.id || `contact-${index}`,
    nameText: item.name || item.contactName || '未命名联系人',
    positionText: item.position || item.title || '联系人',
    phoneText: phone || '-',
    phoneValue: phone
  }
}

function buildContactView(state = {}) {
  const contacts = (state.contacts || []).map(buildContactItem)
  return {
    showLoading: Boolean(state.loading),
    contacts,
    showEmpty: !state.loading && contacts.length === 0,
    showAddAction: Boolean(state.canEdit)
  }
}

Page({
  data: {
    id: '',
    contacts: [],
    loading: false,
    canEdit: false,
    contactView: buildContactView({ contacts: [], loading: false, canEdit: false })
  },
  applyState(patch) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, contactView: buildContactView(next) })
  },
  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && (!hasPermission('customer:view') || !hasPermission('contact:view'))) {
      wx.showToast({ title: '没有联系人查看权限', icon: 'none' })
      wx.navigateBack()
      return
    }
    this.applyState({ id: options.id || '', canEdit: getApp().globalData.demoMode || hasPermission('customer:edit') })
    this.load()
  },

  /** 【M14 2026-10-04】从联系人表单/详情返回时刷新列表(此前保存返回后列表不刷新)。
   *  对齐 customer/index.js 既有 onShow 模式：首次 onShow 跳过(onLoad 已加载)，加载中不重复请求。 */
  onShow() {
    if (!this.didInitialShow) {
      this.didInitialShow = true
      return
    }
    if (!this.data.loading) this.load()
  },
  load() {
    if (!this.data.id) return
    if (getApp().globalData.demoMode) return this.applyState({ contacts: [{ id: 1, name: '李经理', position: '采购负责人', mobile: '138****0001' }, { id: 2, name: '王工', position: '技术负责人', mobile: '139****0002' }, ...readDemoContacts(this.data.id)] })
    this.applyState({ loading: true })
    getCustomerContacts(this.data.id).then((contacts) => this.applyState({ contacts: contacts || [] })).catch((error) => showError(error, '联系人加载失败')).finally(() => this.applyState({ loading: false }))
  },
  call(e) {
    const phone = e.currentTarget.dataset.phone
    if (phone && !phone.includes('*')) wx.makePhoneCall({ phoneNumber: phone })
    else wx.showToast({ title: '手机号已脱敏', icon: 'none' })
  },
  openDetail(e) {
    const item = this.data.contacts[Number(e.currentTarget.dataset.index)]
    if (!item) return
    wx.navigateTo({ url: `/pages/customer/contact/detail/index?name=${encodeURIComponent(item.name || item.contactName || '')}&position=${encodeURIComponent(item.position || item.title || '')}&mobile=${encodeURIComponent(phoneOf(item))}&remark=${encodeURIComponent(item.remark || '')}` })
  },
  editContact(e) {
    if (!this.data.canEdit) return wx.showToast({ title: '没有联系人编辑权限', icon: 'none' })
    const item = this.data.contacts[Number(e.currentTarget.dataset.index)]
    if (!item) return
    const query = [
      `id=${encodeURIComponent(this.data.id)}`,
      `contactId=${encodeURIComponent(item.id || '')}`,
      `name=${encodeURIComponent(item.name || item.contactName || '')}`,
      `position=${encodeURIComponent(item.position || item.title || '')}`,
      `phone=${encodeURIComponent(phoneOf(item))}`,
      `remark=${encodeURIComponent(item.remark || '')}`
    ].join('&')
    wx.navigateTo({ url: `/pages/customer/contact/form/index?${query}` })
  },
  deleteContact(e) {
    if (!this.data.canEdit) return wx.showToast({ title: '没有联系人编辑权限', icon: 'none' })
    const item = this.data.contacts[Number(e.currentTarget.dataset.index)]
    if (!item || !item.id) return
    wx.showModal({
      title: '删除联系人',
      content: `确认删除 ${buildContactItem(item).nameText}？`,
      success: (res) => {
        if (!res.confirm) return
        const action = getApp().globalData.demoMode
          ? Promise.resolve(deleteDemoContact(this.data.id, item.id))
          : deleteCustomerContact(this.data.id, item.id)
        action.then(() => {
          wx.showToast({ title: '已删除', icon: 'success' })
          this.load()
        }).catch((error) => showError(error, '删除联系人失败'))
      }
    })
  },
  goBack() { wx.navigateBack() }
  ,openForm() { wx.navigateTo({ url: `/pages/customer/contact/form/index?id=${this.data.id}` }) }
})
