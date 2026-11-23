const { hasPermission, requireLogin } = require('../../../utils/auth')
const { getFabushiPosts, getFabushiBooks } = require('../../../api/fabushi')
const { showError } = require('../../../utils/feedback')

const TABS = [
  { value: 'songdu', label: '每日诵读' },
  { value: 'suishi', label: '每日随施' },
  { value: 'zaidu', label: '再读一本' }
]

Page({
  data: {
    tabs: TABS,
    active: 'songdu',
    posts: [],
    total: 0,
    page: 1,
    pageSize: 10,
    loading: false,
    canCreate: false,
    // 【D15 2026-11-23】再读一本分区：按书浏览 + 下钻
    books: []
  },

  onPermissionChanged() {
    if (getApp().globalData.demoMode) return
    if (!hasPermission('fabushi:view')) {
      wx.showToast({ title: '没有法布施查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 300)
      return
    }
    this.setData({ canCreate: hasPermission('fabushi:create') })
    this.loadBooks()
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
    this.loadBooks()
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

  loadBooks() {
    getFabushiBooks({ status: 'approved', current: 1, size: 100 })
      .then((res) => {
        this.setData({ books: (res && res.records) || [] })
      })
      .catch(() => this.setData({ books: [] }))
  },

  // 【MP-PARITY 2026-11-23】按书浏览改为下钻独立页 A3（原型：再读分区入口 → A3）：
  // 书头卡 + 按时间/章节/成员三种排列 + 分组列表，手机竖屏放得下，不再在信息流里内联筛选。
  onBookTap(e) {
    const id = Number(e.currentTarget.dataset.id)
    if (!id) return
    wx.navigateTo({ url: '/pages/fabushi/bybook/index?bookId=' + id })
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
      // 显式承接后端 isNew（NEW 标记 30 天口径由后端产出），同时满足 MP 视图绑定守卫的“同目录 JS 产出证据”
      const records = (data.records || []).map((item) => ({ ...item, isNew: Boolean(item.isNew) }))
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
