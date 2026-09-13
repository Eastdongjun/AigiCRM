const { getCustomer, getCustomerContacts, getCustomerLogs, getInvoiceSummary, getPaymentSummary, getCustomerIndicator, releaseCustomer } = require('../../../api/customer')
const { submitApproval } = require('../../../api/approval')
const { hasPermission, requireLogin } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')
const { readDemoCustomers } = require('../../../utils/demo-customers')

function normalizeDetail(item) {
  return {
    id: item.id,
    customerCode: item.customerCode || '',
    customerName: item.customerName || item.name || '未命名客户',
    industry: item.industry || '其他',
    devType: item.devType || '未分类',
    ownerName: item.ownerName || item.salesperson || '未分配',
    officeName: item.officeName || item.deptName || '未设置办事处',
    regionName: item.regionName || '',
    address: item.address || '',
    status: item.status || '正常',
    expireDate: item.expireDate || '',
    invoiceName: item.invoiceName || '',
    groupName: item.groupName || ''
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
    indicator: null,
    canEdit: false,
    canViewFinance: false,
    canViewContacts: false
  },

  onLoad(options) {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('customer:view')) {
      wx.showToast({ title: '没有客户查看权限', icon: 'none' })
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    const demoMode = getApp().globalData.demoMode
    this.setData({
      id: options.id || '',
      from: options.from || '',
      canEdit: demoMode || hasPermission('customer:edit'),
      canViewFinance: demoMode || hasPermission('finance:view'),
      canViewContacts: demoMode || hasPermission('contact:view')
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
      this.setData({
        detail: normalizeDetail(saved || {
          id: this.data.id,
          customerCode: 'DEMO-' + this.data.id,
          customerName: this.data.from === 'sea' ? '公海客户样例' : '山西晋能集团中阳煤业',
          industry: '煤矿',
          ownerName: this.data.from === 'sea' ? '原负责人' : '张三',
          officeName: '晋蒙业务部',
          devType: '成熟创新',
          status: this.data.from === 'sea' ? '公海只读' : '保护期',
          expireDate: '2026-12-31',
          invoiceName: '山西晋能集团有限公司',
          groupName: '晋能集团'
        }), indicator: { relationshipScore: 0, indicator02: 0, indicator03: 0, indicator04: 0, indicator05: 0 }
      })
      return
    }

    this.setData({ loading: true })
    getCustomer(this.data.id).then((detail) => {
      this.setData({ detail: normalizeDetail(detail || {}) })
      const contactsRequest = this.data.canViewContacts ? getCustomerContacts(this.data.id) : Promise.resolve([])
      const financeRequest = this.data.canViewFinance
        ? Promise.all([getInvoiceSummary(this.data.id), getPaymentSummary(this.data.id)])
        : Promise.resolve([{}, {}])
      return Promise.all([contactsRequest, getCustomerLogs(this.data.id), financeRequest, getCustomerIndicator(this.data.id)])
    }).then(([contacts, logs, [invoiceSummary, paymentSummary], indicator]) => {
      this.setData({ contacts: contacts || [], logs: logs || [], invoiceSummary: invoiceSummary || {}, paymentSummary: paymentSummary || {}, indicator: indicator || {} })
    }).catch((error) => showError(error, '无权查看或客户不存在')).finally(() => {
      this.setData({ loading: false })
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
  }
})
