const { hasPermission, requireLogin } = require('../../utils/auth')
const { getFabushiPersonalStats, getFabushiBooks, getFabushiPosts } = require('../../api/fabushi')
const { showError } = require('../../utils/feedback')

const TABS = [
  { value: 'lib', label: '书库' },
  { value: 'znl', label: '正能量' },
  { value: 'shelf', label: '书架', phase: '二期' },
  { value: 'rec', label: '记录', phase: '二期' },
  { value: 'stats', label: '统计' }
]

Page({
  data: {
    canCreate: false,
    personal: {},
    loading: true,
    tabs: TABS,
    activeTab: 'lib',
    books: [],
    posts: [],
    recommended: null,
    searchKeyword: '',
    hasView: false
  },

  onPermissionChanged() {
    if (getApp().globalData.demoMode) return
    if (!hasPermission('fabushi:view')) {
      wx.showToast({ title: '没有法布施查看权限', icon: 'none' })
      setTimeout(() => wx.switchTab({ url: '/pages/home/index' }), 300)
      return
    }
    this.loadAll()
  },

  onLoad() {
    if (!requireLogin()) return
    if (!hasPermission('fabushi:view')) {
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    this.setData({ canCreate: hasPermission('fabushi:create'), hasView: true })
    this.loadAll()
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ selected: 2 })
    if (getApp().globalData.demoMode) { this.setData({ canCreate: true, hasView: true }); return }
    if (!hasPermission('fabushi:view')) {
      wx.showToast({ title: '没有法布施查看权限', icon: 'none' })
      setTimeout(() => wx.switchTab({ url: '/pages/home/index' }), 300)
      return
    }
    this.setData({ canCreate: hasPermission('fabushi:create'), hasView: true })
    this.loadPersonal()
  },

  loadAll() {
    this.setData({ loading: true })
    Promise.all([
      getFabushiPersonalStats().catch(() => ({})),
      getFabushiBooks({ status: 'approved', current: 1, size: 20 }).catch(() => ({})),
      getFabushiPosts({ scopeType: 'heart', current: 1, size: 10 }).catch(() => ({}))
    ]).then(([personal, bookRes, postRes]) => {
      const books = (bookRes && bookRes.records) || []
      const posts = (postRes && postRes.records) || []
      this.setData({
        personal: personal || {},
        books,
        posts,
        recommended: books.find((book) => book.pdfFileId) || books[0] || null,
        loading: false
      })
    }).catch((err) => {
      this.setData({ loading: false })
      showError(err, '法布施加载失败')
    })
  },

  loadPersonal() {
    getFabushiPersonalStats().then((res) => {
      this.setData({ personal: res || {} })
    }).catch((err) => {
      showError(err, '个人统计加载失败')
    })
  },

  onTab(e) {
    const value = e.currentTarget.dataset.value
    this.setData({ activeTab: value })
    if (value === 'znl') {
      getFabushiPosts({ scopeType: 'znl', current: 1, size: 10 }).then((res) => {
        this.setData({ posts: (res && res.records) || [] })
      }).catch(() => {})
    } else if (value === 'lib') {
      getFabushiPosts({ scopeType: 'heart', current: 1, size: 10 }).then((res) => {
        this.setData({ posts: (res && res.records) || [] })
      }).catch(() => {})
    }
  },

  onSearchInput(e) {
    this.setData({ searchKeyword: e.detail.value })
  },

  onSearch() {
    const keyword = (this.data.searchKeyword || '').trim()
    if (!keyword) return
    getFabushiPosts({ scopeType: this.data.activeTab === 'znl' ? 'znl' : 'heart', current: 1, size: 20 })
      .then((res) => {
        const records = (res && res.records) || []
        this.setData({ posts: records.filter((item) => `${item.title || ''}${item.content || ''}`.indexOf(keyword) >= 0) })
      }).catch(() => {})
  },

  refresh() { this.loadAll() },
  goFeed() { wx.navigateTo({ url: '/pages/fabushi/feed/index' }) },
  goBook() { wx.navigateTo({ url: '/pages/fabushi/book/index' }) },
  goUpload() { wx.navigateTo({ url: '/pages/fabushi/upload/index' }) },
  openPost(e) { wx.navigateTo({ url: `/pages/fabushi/detail/index?id=${e.currentTarget.dataset.id}` }) }
})
