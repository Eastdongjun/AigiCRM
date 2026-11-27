// 【MP-PARITY 2026-11-23】A3 按书浏览（再读一本 · 按书下钻）——对齐 MP 原型 v7 A3 与 PC P2：
// 书头卡（版本元信息 + 参与 N 人 · 内容 N 条 · 另有 N 版本 + 在线阅读/我也传）
// + 三种排列（按时间 / 按章节 / 按成员）+ 分组内容列表（章节名 + N 条 + 成员行）。
// 分组全在前端做（一次拉全量本区内容），不额外加接口，也不做「先按章节查再按成员查」的多趟往返。
// 【2026-11-25 用户裁决】“小程序法布施界面按原型 v7 重做外观”: 版式按原型 A3 重排, 取数与分组逻辑不变。
const { requireLogin, hasPermission } = require('../../../utils/auth')
const { whenPermissionsReady } = require('../../../utils/permission-ready')
const { getFabushiBook, getFabushiBooks, getFabushiPosts } = require('../../../api/fabushi')
const { showError } = require('../../../utils/feedback')
const vm = require('../../../utils/fabushi-view-model')

const SORTS = [
  { value: 'time', label: '按时间' },
  { value: 'section', label: '按章节' },
  { value: 'member', label: '按成员' }
]

const EDITION_LABEL = { original: '原版', annotated: '注释本', translation: '译本', plain: '白话本' }

// 【S4.2 2026-11-23】分页拉取。此前一次 size=100 全量拉，超过 100 条静默截断且无提示。
const PAGE_SIZE = 20

// 摘要：标题优先，否则正文前 40 字（原型行内只给一句）
const summaryOf = (post) => {
  const title = String((post && post.title) || '').trim()
  if (title) return title
  const body = String((post && post.content) || '').replace(/\s+/g, ' ').trim()
  return body.length > 40 ? body.slice(0, 40) + '…' : (body || '（无正文）')
}

// 形式：按附件类型拼「书页 3 张 + 语音」，与 PC formLabel 同口径
const formOf = (post) => {
  const list = (post && post.attachments) || []
  const images = list.filter((a) => a.fileType === 'image').length
  const audios = list.filter((a) => a.fileType === 'audio').length
  const videos = list.filter((a) => a.fileType === 'video').length
  const files = list.filter((a) => !['image', 'audio', 'video'].includes(a.fileType)).length
  const parts = []
  if (images) parts.push('书页 ' + images + ' 张')
  if (audios) parts.push('语音')
  if (videos) parts.push('视频')
  if (files) parts.push('文件 ' + files)
  return parts.join(' + ')
}

Page({
  data: {
    sorts: SORTS,
    sort: 'time',
    book: null,
    versionText: '',
    // 书头卡的封面文字与底色、参与人数与内容条数(原型 A3: “参与 8 人 · 内容 36 条 · 另有 2 版本”)
    coverText: '',
    coverTone: '',
    memberCount: 0,
    otherVersions: 0,
    groups: [],
    total: 0,
    loadedCount: 0,
    loading: true,
    loadingMore: false,
    hasMore: true,
    canCreate: false
  },

  onLoad(query) {
    if (!requireLogin()) return
    return whenPermissionsReady(() => {
      if (!hasPermission('fabushi:view')) {
        wx.showToast({ title: '没有法布施查看权限', icon: 'none' })
        return setTimeout(() => wx.navigateBack(), 600)
      }
      this._bookId = query && query.bookId ? Number(query.bookId) : null
      if (!this._bookId) {
        wx.showToast({ title: '参数缺失', icon: 'none' })
        return setTimeout(() => wx.navigateBack(), 600)
      }
      this.setData({ canCreate: hasPermission('fabushi:create') })
      wx.setNavigationBarTitle({ title: '按书浏览' })
      this.loadBook()
      this.loadPosts(true)
    })
  },

  onPullDownRefresh() {
    Promise.all([this.loadBook(), this.loadPosts(true)]).then(() => wx.stopPullDownRefresh())
  },

  /** 【S4.2】触底加载下一页：不打断已有分组，追加后统一 rebuild。 */
  onReachBottom() {
    if (this.data.loading || this.data.loadingMore || !this.data.hasMore) return
    this.loadPosts(false)
  },

  loadBook() {
    return getFabushiBook(this._bookId)
      .then((res) => {
        // 后端 detailData 返回 {book}；request 层若已解包则 res 就是 book，两种都兼容
        const book = (res && res.book) || res || { id: this._bookId }
        const versionText = [book.author, book.publisher,
          EDITION_LABEL[book.editionType] || book.editionType || '',
          book.pages ? book.pages + ' 页' : ''].filter(Boolean).join(' · ')
        const title = String(book.title == null ? '' : book.title).replace(/[《》]/g, '')
        // 标题带上书名(原型 A3 的导航标题: “按书浏览 · 道德经”)
        wx.setNavigationBarTitle({ title: title ? '按书浏览 · ' + title : '按书浏览' })
        // 书头卡的“参与 N 人”是这本书全部内容的作者数(书目接口给的); “再读内容 N 条”用下面列表自己的总数,
        // 两个数口径不同(书目接口的内容条数含诵读、随施), 不混在一起显示
        this.setData({
          book,
          versionText,
          coverText: vm.coverText(book.title),
          coverTone: vm.coverTone(book.id),
          memberCount: Number(book.memberCount) || 0
        })
        // 同名不同版本 = 同一本书的其他版本（D2：书名+作者+版本唯一确定一本书）。
        // 书名比的时候去掉书名号: 同一本书的不同版本, 录入时有的带《》有的不带
        const sameTitle = (other) => String(other.title == null ? '' : other.title).replace(/[《》]/g, '').trim() === title
        return getFabushiBooks({ status: 'approved', current: 1, size: 200 }).then((r) => {
          const all = (r && r.records) || []
          const others = all.filter((b) => b.id !== book.id && sameTitle(b)).length
          this.setData({
            book: Object.assign({}, this.data.book, { otherVersions: others }),
            otherVersions: others
          })
        })
      })
      .catch((err) => showError(err, '书目信息加载失败'))
  },

  /**
   * 【S4.2 2026-11-23】分页加载。
   * reset=true 拉第 1 页并覆盖（首次进入 / 下拉刷新）；reset=false 追加下一页（触底）。
   * hasMore 用「已加载条数 < 后端 total」判断，不用「本页是否满页」——
   * 后端 total 是真实总数，满页判断在末页恰好整除时会多打一次空请求。
   */
  loadPosts(reset) {
    const page = reset ? 1 : (this._page || 1) + 1
    this.setData(reset ? { loading: true } : { loadingMore: true })
    return getFabushiPosts({ scopeType: 'zaidu', bookId: this._bookId, current: page, size: PAGE_SIZE })
      .then((res) => {
        const data = res || {}
        // avatarText / avatarTone / timeText 是 WXML 按 item.xxx 读的派生字段, 逐个在这里写出(视图绑定契约要的就是这个“生产处”)
        const records = (data.records || []).map((item) => Object.assign({}, item, {
          isNew: Boolean(item.isNew),
          summary: summaryOf(item),
          formText: formOf(item),
          timeText: vm.timeText(item.createdAt),
          avatarText: String(item.userName || '法').slice(0, 1),
          avatarTone: vm.coverTone(item.userId)
        }))
        this._page = page
        this._records = reset ? records : (this._records || []).concat(records)
        const total = Number(data.total) || this._records.length
        this.setData({
          total,
          loadedCount: this._records.length,
          loading: false,
          loadingMore: false,
          // 本页返回空 或 已够 total 都算到底，避免无限触底重试
          hasMore: records.length > 0 && this._records.length < total
        })
        this.rebuild()
      })
      .catch((err) => {
        this.setData({ loading: false, loadingMore: false })
        showError(err, '内容加载失败')
      })
  },

  onSortTap(e) {
    const sort = e.currentTarget.dataset.value
    if (!sort || sort === this.data.sort) return
    this.setData({ sort })
    this.rebuild()
  },

  // 三种排列都产出同一结构 groups[{ key, title, count, items }]，wxml 只有一套渲染
  rebuild() {
    const list = this._records || []
    const sort = this.data.sort
    const bucket = new Map()
    const push = (key, title, item) => {
      if (!bucket.has(key)) bucket.set(key, { key, title, count: 0, items: [] })
      const g = bucket.get(key)
      g.count += 1
      g.items.push(item)
    }
    if (sort === 'section') {
      list.forEach((p) => push(p.sectionTitle || '未分章', p.sectionTitle || '未分章', p))
    } else if (sort === 'member') {
      list.forEach((p) => push('u' + (p.userId || 0), p.userName || '法布施', p))
    } else {
      // 按时间：单组平铺，组内已按后端时间倒序
      list.forEach((p) => push('all', '', p))
    }
    const groups = [...bucket.values()]
    if (sort === 'time') groups.forEach((g) => { g.title = '' })
    this.setData({ groups })
  },

  goDetail(e) {
    const id = e.currentTarget.dataset.id
    if (!id) return
    wx.navigateTo({ url: '/pages/fabushi/detail/index?id=' + id })
  },

  openReader() {
    const book = this.data.book || {}
    if (!book.pdfFileId) {
      wx.showToast({ title: '该书未挂电子书', icon: 'none' })
      return
    }
    wx.navigateTo({
      url: '/pages/fabushi/reader/index?bookId=' + book.id
        + '&pdfFileId=' + book.pdfFileId
        + '&title=' + encodeURIComponent(book.title || '在线阅读')
        + (book.layoutType ? '&layoutType=' + book.layoutType : '')
    })
  },

  goUpload() {
    wx.navigateTo({ url: '/pages/fabushi/upload/index?scope=zaidu&bookId=' + this._bookId })
  }
})
