const { hasPermission, requireLogin } = require('../../../../utils/auth')
const { getFabushiBooks } = require('../../../../api/fabushi')
const request = require('../../../../utils/request')

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
    canCreate: false
  },

  onLoad() {
    if (!requireLogin()) return
    if (!hasPermission('fabushi:view')) {
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    this.setData({ canCreate: hasPermission('fabushi:create') })
    this.loadCategories()
    this.loadBooks()
  },

  loadCategories() {
    request({ url: '/api/system/dict/data/fabushi_category' }).then((res) => {
      this.setData({ categories: (res && res.data) || [] })
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
      const data = (res && res.data) || {}
      const records = data.records || []
      this.setData({
        books: this.data.page > 1 ? this.data.books.concat(records) : records,
        total: Number(data.total) || 0,
        loading: false
      })
    }).catch(() => this.setData({ loading: false }))
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
  }
})
