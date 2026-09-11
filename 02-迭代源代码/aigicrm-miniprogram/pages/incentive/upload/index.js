const { getCustomerList } = require('../../../api/customer')
const { getCategoryTree, addRecord } = require('../../../api/incentive')
const { uploadFile } = require('../../../api/file')
const { requireLogin, hasPermission } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')

function flattenCategories(nodes = [], result = []) {
  nodes.forEach((item) => {
    result.push({ id: item.id, name: item.categoryName || item.name || '未命名分类' })
    if (item.children && item.children.length) flattenCategories(item.children, result)
  })
  return result
}

Page({
  data: {
    customers: [],
    customerNames: [],
    categories: [],
    categoryNames: [],
    selectedCustomerIndex: -1,
    selectedCategoryIndex: -1,
    description: '',
    filePath: '',
    fileName: '',
    loading: false
  },

  onLoad() {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('incentive:add')) {
      wx.showToast({ title: '没有激励上传权限', icon: 'none' })
      wx.navigateBack()
      return
    }
    this.loadOptions()
  },

  loadOptions() {
    if (getApp().globalData.demoMode) {
      const customers = [{ id: 1, customerName: '华东智能制造集团', industry: '制造业' }]
      const categories = [{ id: 1, name: '试点安装' }, { id: 2, name: '交流拜访' }]
      this.setData({ customers, customerNames: customers.map((item) => item.customerName), categories, categoryNames: categories.map((item) => item.name) })
      return
    }
    Promise.all([getCustomerList({ current: 1, size: 100 }), getCategoryTree()]).then(([customerRes, categoryRes]) => {
      const page = customerRes && (customerRes.records || customerRes.data || customerRes)
      const customers = Array.isArray(page) ? page : (page.records || [])
      const categories = flattenCategories(categoryRes || [])
      this.setData({ customers, customerNames: customers.map((item) => item.customerName || item.name || '未命名客户'), categories, categoryNames: categories.map((item) => item.name) })
    }).catch((error) => showError(error, '上传选项加载失败'))
  },

  chooseCustomer(e) { this.setData({ selectedCustomerIndex: Number(e.detail.value) }) },
  chooseCategory(e) { this.setData({ selectedCategoryIndex: Number(e.detail.value) }) },
  inputDescription(e) { this.setData({ description: e.detail.value }) },

  chooseFile() {
    wx.chooseMessageFile({ count: 1, type: 'file', success: (res) => {
      const file = res.tempFiles && res.tempFiles[0]
      if (file && file.path) this.setData({ filePath: file.path, fileName: file.name || '已选择文件' })
    }, fail: (error) => { if (error && error.errMsg && !error.errMsg.includes('cancel')) showError(error, '选择文件失败') } })
  },

  submit() {
    const customer = this.data.customers[this.data.selectedCustomerIndex]
    const category = this.data.categories[this.data.selectedCategoryIndex]
    if (!customer || !category || !this.data.description.trim() || !this.data.filePath) {
      wx.showToast({ title: '请补齐客户、分类、说明和文件', icon: 'none' })
      return
    }
    if (this.data.loading) return
    this.setData({ loading: true })
    wx.showLoading({ title: '上传中' })
    uploadFile(this.data.filePath, null, 'incentive').then((uploaded) => {
      const fileId = uploaded && uploaded.id
      if (!fileId) throw new Error('上传结果缺少文件 ID')
      return addRecord({ customerId: customer.id, categoryId: category.id, remark: this.data.description.trim(), fileId: JSON.stringify([fileId]) })
    }).then(() => {
      wx.showToast({ title: '上传成功', icon: 'success' })
      wx.navigateTo({ url: '/pages/incentive/index' })
    }).catch((error) => showError(error, '激励上传失败')).finally(() => {
      this.setData({ loading: false })
      wx.hideLoading()
    })
  }
})
