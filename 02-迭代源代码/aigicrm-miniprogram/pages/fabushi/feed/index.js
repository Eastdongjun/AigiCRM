const { hasPermission, requireLogin } = require('../../../utils/auth')
const { getFabushiPosts } = require('../../../api/fabushi')
const { showError } = require('../../../utils/feedback')

const TABS = [
  { value: 'heart', label: '心得' },
  { value: 'recitation', label: '诵读' },
  { value: 'znl', label: '正能量' }
]

Page({
  data: {
    tabs: TABS,
    active: 'heart',
    posts: [],
    total: 0,
    page: 1,
    pageSize: 10,
    loading: false,
    canCreate: false
  },

  onPermissionChanged() {
    if (getApp().globalData.demoMode) return
    if (!hasPermission('fabushi:view')) {
      wx.showToast({ title: '没有法布施查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 300)
      return
    }
    this.setData({ canCreate: hasPermission('fabushi:create') })
    this.loadPosts()
  },

  onLoad() {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('fabushi:view')) {
      wx.showToast({ title: '没有法布施查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 300)
      return
    }
    this.setData({ canCreate: hasPermission('fabushi:create') })
    this.loadPosts()
  },

  onShow() {
    this.setData({ canCreate: hasPermission('fabushi:create') })
  },

  onTabTap(e) {
    const active = e.currentTarget.dataset.value
    if (!active || active === this.data.active) return
    this.setData({ active, posts: [], page: 1, total: 0 })
    this.loadPosts()
  },

  loadPosts() {
    const seq = (this._seq = (this._seq || 0) + 1)
    this.setData({ loading: true })
    getFabushiPosts({
      scopeType: this.data.active,
      current: this.data.page,
      size: this.data.pageSize
    }).then((res) => {
      if (seq !== this._seq) return
      const data = res || {}
      const records = data.records || []
      this.setData({
        posts: this.data.page > 1 ? this.data.posts.concat(records) : records,
        total: Number(data.total) || 0,
        loading: false
      })
    }).catch((err) => {
      if (seq !== this._seq) return
      this.setData({ loading: false })
      showError(err, '内容加载失败')
    })
  },

  onReachBottom() {
    if (this.data.loading) return
    if (this.data.posts.length >= this.data.total) return
    this.setData({ page: this.data.page + 1 })
    this.loadPosts()
  },

  goDetail(e) {
    const id = e.currentTarget.dataset.id
    if (!id) return
    wx.navigateTo({ url: '/pages/fabushi/detail/index?id=' + id })
  },

  goUpload() {
    wx.navigateTo({ url: '/pages/fabushi/upload/index' })
  }
})
