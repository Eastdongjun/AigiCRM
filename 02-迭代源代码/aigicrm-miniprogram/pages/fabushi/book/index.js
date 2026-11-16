const { hasPermission, requireLogin } = require('../../../utils/auth')
const { getFabushiBooks, getFabushiBook, getFabushiPosts, getFabushiSections, addFabushiShelf } = require('../../../api/fabushi')
const { getDictData } = require('../../../api/system')
const { showError } = require('../../../utils/feedback')

// 【法布施一期 2026-11-15】MP 书目列表: 分类条(字典)+搜索+分页; 提交入口走 create 门
Page({
  data: {
    categories: [],
    activeCategory: '',
    keyword: '',
    books: [],
    total: 0,
    page: 1,
    pageSize: 10,
    loading: false,
    canCreate: false,
    detailVisible: false,
    detailBook: {},
    detailPosts: [],
    sections: []
  },

  onPermissionChanged() {
    if (getApp().globalData.demoMode) return
    if (!hasPermission('fabushi:view')) {
      wx.showToast({ title: '没有法布施查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 300)
      return
    }
    this.loadBooks()
  },

  onLoad(query) {
    if (!requireLogin()) return
    if (!hasPermission('fabushi:view')) {
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    this.setData({ canCreate: hasPermission('fabushi:create') })
    this._presetBookId = query && query.id ? Number(query.id) : null
    this.loadCategories()
    this.loadBooks()
  },

  loadCategories() {
    getDictData('fabushi_category').then((res) => {
      this.setData({ categories: res || [] })
    })
  },

  loadBooks() {
    const seq = (this._seq = (this._seq || 0) + 1)
    this.setData({ loading: true })
    getFabushiBooks({
      categoryCode: this.data.activeCategory || '',
      keyword: this.data.keyword || '',
      status: 'approved',
      current: this.data.page,
      size: this.data.pageSize
    }).then((res) => {
      if (seq !== this._seq) return // 慢请求守卫: 旧响应丢弃
      const data = res || {}
      const records = data.records || []
      this.setData({
        books: this.data.page > 1 ? this.data.books.concat(records) : records,
        total: Number(data.total) || 0,
        loading: false
      })
      if (this._presetBookId && this.data.page === 1) {
        const preset = records.find((book) => Number(book.id) === Number(this._presetBookId))
        if (preset) this.openBook({ currentTarget: { dataset: { id: preset.id } } })
      }
    }).catch((err) => {
      this.setData({ loading: false })
      showError(err, '书目加载失败')
    })
  },

  onCategoryTap(e) {
    this.setData({ activeCategory: e.currentTarget.dataset.code || '', page: 1 })
    this.loadBooks()
  },

  onKeywordInput(e) {
    this.setData({ keyword: e.detail.value })
  },

  onSearch() {
    this.setData({ page: 1 })
    this.loadBooks()
  },

  onReachBottom() {
    if (this.data.loading) return
    if (this.data.books.length >= this.data.total) return
    this.setData({ page: this.data.page + 1 })
    this.loadBooks()
  },

  goUpload() {
    wx.navigateTo({ url: '/pages/fabushi/upload/index' })
  },

  openBook(e) {
    const id = e.currentTarget.dataset.id
    this.setData({ detailVisible: true, detailBook: { id }, detailPosts: [] })
    Promise.all([
      getFabushiBook(id).catch(() => null),
      getFabushiPosts({ bookId: id, status: 'published', current: 1, size: 20 }).catch(() => ({})),
      getFabushiSections(id).catch(() => ({}))
    ]).then(([bookRes, postRes, secRes]) => {
      const book = (bookRes && bookRes.book) || bookRes || { id }
      const flat = []
      const walk = (rows, depth) => (rows || []).forEach((row) => { flat.push({ ...row, depth }); walk(row.children, depth + 1) })
      walk((secRes && secRes.sections) || [], 0)
      this.setData({ detailBook: book, detailPosts: (postRes && postRes.records) || [], sections: flat })
    })
  },

  closeDetail() { this.setData({ detailVisible: false }) },
  noop() {},

  goUploadForBook() {
    if (!this.data.canCreate) {
      wx.showToast({ title: '没有发布权限', icon: 'none' })
      return
    }
    wx.navigateTo({ url: `/pages/fabushi/upload/index?bookId=${this.data.detailBook.id}` })
  },

  openPost(e) {
    wx.navigateTo({ url: `/pages/fabushi/detail/index?id=${e.currentTarget.dataset.id}` })
  },

  addShelf() {
    if (!this.data.detailBook || !this.data.detailBook.id) return
    addFabushiShelf(this.data.detailBook.id).then(() => wx.showToast({ title: '已加入书架', icon: 'success' })).catch(() => {})
  },

  openReader() {
    // 【2026-11-16 P2 方案A】改走 web-view H5 PDF.js 阅读器(pages/fabushi/reader):
    // openDocument 无页码回传能力且原实现写死 lastPage=1 会覆盖真实进度——两条路径一并退役。
    // 页码进度由 H5 内翻页自动回传 /api/fabushi/progress(与 PC read.vue 同接口同口径)。
    const book = this.data.detailBook
    if (!book || !book.pdfFileId) return
    wx.navigateTo({
      url: `/pages/fabushi/reader/index?bookId=${book.id}&pdfFileId=${book.pdfFileId}` +
        `&title=${encodeURIComponent(book.title || '法布施书目')}`
    })
  }
})
