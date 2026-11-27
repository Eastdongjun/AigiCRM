const { hasPermission, requireLogin } = require('../../../utils/auth')
const { whenPermissionsReady } = require('../../../utils/permission-ready')
const { getFabushiBooks, getFabushiBook, getFabushiPosts, getFabushiSections, addFabushiShelf, submitFabushiBook } = require('../../../api/fabushi')
const { getDictData } = require('../../../api/system')
const { showError } = require('../../../utils/feedback')
const vm = require('../../../utils/fabushi-view-model')

// 【法布施一期 2026-11-15】MP 书目列表: 分类条(字典)+搜索+分页; 提交入口走 create 门
// 【2026-11-25 用户裁决】“小程序法布施界面按原型 v7 重做外观”: 本页 = 原型 A5 书目浏览(类别 → 书目 → 版本)。
//   点一行在行内展开(原型: “点开展开选择”): 这个书名的几个版本可切换, 选定版本下面是在线阅读 / 去发布 / 按书浏览 / 加书架、章节结构、本书目内容。
//   页脚的“＋ 提交书目”按原型做成了一个表单(人人可提交, 管理员审核后展示); 此前小程序里没有提交书目的入口。

const EDITIONS = [
  { value: 'original', label: '原版' },
  { value: 'annotated', label: '注释本' },
  { value: 'translation', label: '译本' },
  { value: 'plain', label: '白话本' }
]
const EDITION_LABEL = EDITIONS.reduce((map, item) => Object.assign(map, { [item.value]: item.label }), {})
const EMPTY_FORM = { categoryIndex: -1, title: '', author: '', publisher: '', editionIndex: 0, pages: '' }

// 书名去掉书名号: 同一本书的不同版本, 录入时有的带《》有的不带
const plainTitle = (value) => String(value == null ? '' : value).replace(/[《》]/g, '').trim()
// 版本在切换按钮上的名字: “陈鼓应 · 注释本”; 都没有时退回书名
const versionLabel = (book) => [book.author, EDITION_LABEL[book.editionType] || book.editionType].filter(Boolean).join(' · ') || plainTitle(book.title)

Page({
  data: {
    categories: [],
    activeCategory: '',
    activeCategoryLabel: '',
    keyword: '',
    books: [],
    total: 0,
    page: 1,
    pageSize: 10,
    loading: false,
    canCreate: false,
    // 行内展开的那一本
    expandedId: null,
    selectedId: null,
    versions: [],
    detailBook: {},
    detailVersionText: '',
    detailPosts: [],
    sections: [],
    // 提交书目
    submitOpen: false,
    submitting: false,
    editions: EDITIONS,
    form: Object.assign({}, EMPTY_FORM)
  },

  onPermissionChanged() {
    if (getApp().globalData.demoMode) return
    // 【BUG-MP-TABBAR-PERM-RACE-20261127 第二批】冷启动回填也会触发本回调: 页面未初始化时跳过, 避免误踢/双拉。
    if (!this._loadedOnce) return
    if (!hasPermission('fabushi:view')) {
      wx.showToast({ title: '没有法布施查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 300)
      return
    }
    this.loadBooks()
  },

  onLoad(query) {
    if (!requireLogin()) return
    return whenPermissionsReady(() => {
      if (!hasPermission('fabushi:view')) {
        wx.switchTab({ url: '/pages/home/index' })
        return
      }
      this.setData({ canCreate: hasPermission('fabushi:create') })
      this._presetBookId = query && query.id ? Number(query.id) : null
      this.loadCategories()
      this.loadBooks()
      this._loadedOnce = true
    })
  },

  loadCategories() {
    getDictData('fabushi_category').then((res) => {
      this.setData({ categories: res || [] })
    }).catch(() => {})
  },

  reload() {
    this._seq = (this._seq || 0) + 1
    this.setData({ books: [], total: 0, page: 1, expandedId: null, selectedId: null })
    this.loadBooks()
  },

  loadBooks() {
    const seq = (this._seq = (this._seq || 0) + 1)
    this.setData({ loading: true })
    getFabushiBooks({
      categoryCode: this.data.activeCategory || '',
      keyword: String(this.data.keyword || '').trim(),
      status: 'approved',
      current: this.data.page,
      size: this.data.pageSize
    }).then((res) => {
      if (seq !== this._seq) return // 慢请求守卫: 旧响应丢弃
      const data = res || {}
      // coverText / coverTone / titleText 是 WXML 按 item.xxx 读的派生字段, 逐个在这里写出(视图绑定契约要的就是这个“生产处”)
      const records = (data.records || []).map((book) => Object.assign({}, book, {
        coverText: vm.coverText(book.title, 2),
        coverTone: vm.coverTone(book.id),
        titleText: `《${plainTitle(book.title)}》`
      }))
      this.setData({
        books: this.data.page > 1 ? this.data.books.concat(records) : records,
        total: Number(data.total) || 0,
        loading: false
      })
      if (this._presetBookId && this.data.page === 1) {
        const preset = records.find((book) => Number(book.id) === Number(this._presetBookId))
        this._presetBookId = null
        if (preset) this.openBook({ currentTarget: { dataset: { id: preset.id } } })
      }
    }).catch((err) => {
      if (seq !== this._seq) return
      this.setData({ loading: false })
      showError(err, '书目加载失败')
    })
  },

  onCategoryTap(e) {
    const code = e.currentTarget.dataset.code || ''
    if (code === this.data.activeCategory) return
    const category = (this.data.categories || []).find((item) => item.dictValue === code)
    this.setData({ activeCategory: code, activeCategoryLabel: category ? category.dictLabel : '', expandedId: null, selectedId: null, page: 1 })
    // 导航标题跟着类别走(原型 A5: “书目 · 国学建议”)
    wx.setNavigationBarTitle({ title: category ? '书目 · ' + category.dictLabel : '书目浏览' })
    this.loadBooks()
  },

  onKeywordInput(e) {
    const keyword = e.detail.value
    this.setData({ keyword })
    // 把搜索词删空了 = 不再搜索, 回到不筛词的列表
    if (this._searched && !String(keyword || '').trim()) { this._searched = false; this.setData({ page: 1 }); this.loadBooks() }
  },

  onSearch() {
    this._searched = Boolean(String(this.data.keyword || '').trim())
    this.setData({ page: 1 })
    this.loadBooks()
  },

  onReachBottom() {
    if (this.data.loading) return
    if (this.data.books.length >= this.data.total) return
    this.setData({ page: this.data.page + 1 })
    this.loadBooks()
  },

  onPullDownRefresh() {
    this.setData({ page: 1 })
    this.loadBooks()
    wx.stopPullDownRefresh()
  },

  // ===== 行内展开: 点一行展开这一本, 再点一次收起 =====
  openBook(e) {
    const id = Number(e.currentTarget.dataset.id)
    if (!id) return
    if (Number(this.data.expandedId) === id) { this.closeDetail(); return }
    this.setData({ expandedId: id, selectedId: id, detailBook: { id }, detailVersionText: '', detailPosts: [], sections: [], versions: [] })
    this.loadVersion(id)
  },

  closeDetail() { this.setData({ expandedId: null, selectedId: null }) },
  noop() {},

  // 选定版本的详情: 书目信息、这本书的内容(前 20 条)、章节结构; 再按书名找一遍, 得到同名的其他版本
  loadVersion(id) {
    const seq = (this._detailSeq = (this._detailSeq || 0) + 1)
    Promise.all([
      getFabushiBook(id).catch(() => null),
      getFabushiPosts({ bookId: id, status: 'published', current: 1, size: 20 }).catch(() => ({})),
      getFabushiSections(id).catch(() => ({}))
    ]).then(([bookRes, postRes, secRes]) => {
      if (seq !== this._detailSeq) return
      const book = (bookRes && bookRes.book) || bookRes || { id }
      const flat = []
      const walk = (rows, depth) => (rows || []).forEach((row) => { flat.push({ ...row, depth }); walk(row.children, depth + 1) })
      walk((secRes && secRes.sections) || [], 0)
      // timeText 是 WXML 按 item.xxx 读的派生字段, 逐个写出(同上)
      const detailPosts = ((postRes && postRes.records) || []).map((item) => ({ ...item, isNew: Boolean(item.isNew), timeText: vm.timeText(item.createdAt) }))
      this.setData({ detailBook: book, detailVersionText: versionLabel(book), detailPosts, sections: flat, versions: [{ id: book.id, label: versionLabel(book) }] })
      return getFabushiBooks({ keyword: plainTitle(book.title), status: 'approved', current: 1, size: 50 }).then((res) => {
        if (seq !== this._detailSeq) return
        const others = ((res && res.records) || [])
          .filter((other) => other.id !== book.id && plainTitle(other.title) === plainTitle(book.title))
          .map((other) => ({ id: other.id, label: versionLabel(other) }))
        this.setData({ versions: [{ id: book.id, label: versionLabel(book) }].concat(others) })
      })
    }).catch(() => {})
  },

  pickVersion(e) {
    const id = Number(e.currentTarget.dataset.id)
    if (!id || id === Number(this.data.selectedId)) return
    this.setData({ selectedId: id, detailBook: { id }, detailVersionText: '', detailPosts: [], sections: [] })
    this.loadVersion(id)
  },

  goUploadForBook() {
    if (!this.data.canCreate) {
      wx.showToast({ title: '没有发布权限', icon: 'none' })
      return
    }
    wx.navigateTo({ url: `/pages/fabushi/upload/index?bookId=${this.data.detailBook.id}` })
  },

  // 按书浏览(原型 A3): 看这本书里每个人上传的内容
  goByBook() {
    const id = this.data.detailBook && this.data.detailBook.id
    if (!id) return
    wx.navigateTo({ url: '/pages/fabushi/bybook/index?bookId=' + id })
  },

  openPost(e) {
    wx.navigateTo({ url: `/pages/fabushi/detail/index?id=${e.currentTarget.dataset.id}` })
  },

  addShelf() {
    if (!this.data.detailBook || !this.data.detailBook.id) return
    addFabushiShelf(this.data.detailBook.id).then(() => wx.showToast({ title: '已加入书架', icon: 'success' })).catch((err) => showError(err, '加入书架失败'))
  },

  openReader() {
    // 【2026-11-16 P2 方案A】改走 web-view H5 PDF.js 阅读器(pages/fabushi/reader):
    // openDocument 无页码回传能力且原实现写死 lastPage=1 会覆盖真实进度——两条路径一并退役。
    // 页码进度由 H5 内翻页自动回传 /api/fabushi/progress(与 PC read.vue 同接口同口径)。
    const book = this.data.detailBook
    if (!book || !book.pdfFileId) return
    wx.navigateTo({
      url: `/pages/fabushi/reader/index?bookId=${book.id}&pdfFileId=${book.pdfFileId}` +
        `&title=${encodeURIComponent(book.title || '法布施书目')}` +
        // 【MP-PARITY 2026-11-23】版式标记带给 H5（文本版/影印版默认底色与重排可用性不同）
        (book.layoutType ? `&layoutType=${book.layoutType}` : '')
    })
  },

  // ===== 提交书目(原型 A5 页脚): 填类别、书名、作者、出版社、版本类型、页数; 提交后待管理员审核 =====
  openSubmit() {
    if (!hasPermission('fabushi:create')) {
      wx.showToast({ title: '没有发布权限', icon: 'none' })
      return
    }
    this.setData({ submitOpen: true, form: Object.assign({}, EMPTY_FORM) })
  },

  closeSubmit() { this.setData({ submitOpen: false }) },

  onFormInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ form: Object.assign({}, this.data.form, { [field]: e.detail.value }) })
  },

  onCategoryPick(e) {
    this.setData({ form: Object.assign({}, this.data.form, { categoryIndex: Number(e.detail.value) }) })
  },

  onEditionPick(e) {
    this.setData({ form: Object.assign({}, this.data.form, { editionIndex: Number(e.detail.value) }) })
  },

  submitBook() {
    if (this.data.submitting) return
    const form = this.data.form
    const category = (this.data.categories || [])[form.categoryIndex]
    const title = String(form.title || '').trim()
    if (!category) { wx.showToast({ title: '请选择类别', icon: 'none' }); return }
    if (!title) { wx.showToast({ title: '请填写书名', icon: 'none' }); return }
    const pages = String(form.pages || '').trim()
    if (pages && !/^\d+$/.test(pages)) { wx.showToast({ title: '页数要填正整数', icon: 'none' }); return }
    const edition = EDITIONS[form.editionIndex] || EDITIONS[0]
    this.setData({ submitting: true })
    submitFabushiBook({
      categoryCode: category.dictValue,
      title,
      author: String(form.author || '').trim(),
      publisher: String(form.publisher || '').trim(),
      editionType: edition.value,
      pages: pages ? Number(pages) : null
    }).then(() => {
      this.setData({ submitting: false, submitOpen: false })
      wx.showToast({ title: '已提交，待管理员审核', icon: 'none' })
      this.reload()
    }).catch((err) => {
      this.setData({ submitting: false })
      showError(err, '提交失败')
    })
  }
})
