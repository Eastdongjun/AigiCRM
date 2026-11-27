// 认知广场(原型 A2 · 朋友圈式信息流)。
// 【2026-11-25 用户裁决】“小程序法布施界面按原型 v7 重做外观”: 这一页按原型重排 —— 搜索卡 + 筛选(全部 / 三类 / 只看未读)、
//   朋友圈式卡片(头像 + 昵称 + 类型 / 正文 / 语音条 / 图片 / 时间·来源 / 灰底操作行 + ⋯ 菜单 / 谁点了木鱼)、底部“按书浏览”入口。
//   取数与分页沿用原来的做法(每页 10 条, 触底加载, 旧的应答晚到不覆盖新的)。
const { hasPermission, requireLogin } = require('../../../utils/auth')
const { whenPermissionsReady } = require('../../../utils/permission-ready')
const { getFabushiPosts, getFabushiBooks, toggleFabushiLike } = require('../../../api/fabushi')
const { getDownloadToken } = require('../../../api/file')
const { showError } = require('../../../utils/feedback')
const vm = require('../../../utils/fabushi-view-model')

const FILTERS = [
  { value: 'all', label: '全部' },
  { value: 'songdu', label: '每日诵读' },
  { value: 'suishi', label: '每日随施' },
  { value: 'zaidu', label: '再读一本' }
]
const PAGE_SIZE = 10
// “只看未读”是前端过滤(列表接口没有这个参数): 一页里已读的都滤掉; 滤完一条不剩而后面还有, 就接着取下一页,
// 最多连取这么多页, 免得出现“看着是空的、其实后面还有”
const MAX_CHASE_PAGES = 5

Page({
  data: {
    filters: FILTERS,
    active: 'all',
    keyword: '',
    unreadOnly: false,
    posts: [],
    total: 0,
    page: 1,
    pageSize: PAGE_SIZE,
    loading: false,
    noMore: false,
    canCreate: false,
    // 按书浏览的选书面板(再读一本 · 按书罗列 → 每本书看每个人上传的内容)
    pickerOpen: false,
    books: []
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
    this.setData({ canCreate: hasPermission('fabushi:create') })
    this.reload()
  },

  onLoad() {
    if (!requireLogin()) return
    return whenPermissionsReady(() => {
      if (!getApp().globalData.demoMode && !hasPermission('fabushi:view')) {
        wx.showToast({ title: '没有法布施查看权限', icon: 'none' })
        setTimeout(() => wx.navigateBack(), 300)
        return
      }
      this.setData({ canCreate: hasPermission('fabushi:create') })
      this.reload()
      this._loadedOnce = true
    })
  },

  onShow() {
    this.setData({ canCreate: hasPermission('fabushi:create') })
    // 从详情页回来: 看过的那几条在“只看未读”下不该再留着, 重取一遍
    if (this._hidden && this.data.unreadOnly) this.reload()
    this._hidden = false
  },

  onHide() { this._hidden = true },

  reload() {
    this._seq = (this._seq || 0) + 1
    this.setData({ posts: [], total: 0, page: 1, noMore: false })
    this.loadPosts()
  },

  onFilterTap(e) {
    const active = e.currentTarget.dataset.value
    if (!active || active === this.data.active) return
    this.setData({ active })
    this.reload()
  },

  onToggleUnread() {
    this.setData({ unreadOnly: !this.data.unreadOnly })
    this.reload()
  },

  onKeywordInput(e) {
    const keyword = e.detail.value
    this.setData({ keyword })
    // 把搜索词删空了 = 不再搜索, 回到不筛词的列表
    if (this._searched && !String(keyword || '').trim()) { this._searched = false; this.reload() }
  },

  onSearch() {
    this._searched = Boolean(String(this.data.keyword || '').trim())
    this.reload()
  },

  // 取第 page 页: 需要显示图片的先取一次下载令牌, 把条目整理成视图字段
  fetchPage(page) {
    const params = { current: page, size: PAGE_SIZE }
    if (this.data.active !== 'all') params.scopeType = this.data.active
    const keyword = String(this.data.keyword || '').trim()
    if (keyword) params.keyword = keyword
    return getFabushiPosts(params).then((res) => {
      const data = res || {}
      const records = data.records || []
      const needsToken = records.some((item) => (item.attachments || []).some((att) => att.fileType === 'image'))
      const ready = needsToken ? getDownloadToken().catch(() => null) : Promise.resolve(null)
      return ready.then((tokenRes) => {
        const token = tokenRes ? ((tokenRes.token != null ? tokenRes.token : (tokenRes.data || {}).token) || '') : ''
        const base = getApp().globalData.apiBaseUrl || ''
        // isNew / typeLabel / timeText / avatarText 等是 WXML 按 item.xxx 读的派生字段, 逐个在这里写出(视图绑定契约要的就是这个“生产处”)
        return {
          records: records.map((item) => {
            const view = vm.postView(item, token, null, base)
            return {
              ...item,
              isNew: view.isNew,
              typeLabel: view.typeLabel,
              timeText: view.timeText,
              pics: view.pics,
              picCount: view.picCount,
              audioCount: view.audioCount,
              likerText: view.likerText,
              avatarText: String(item.userName || '法').slice(0, 1),
              avatarTone: vm.coverTone(item.userId),
              likedByMe: Boolean(item.likedByMe),
              likeCount: Number(item.likeCount) || 0
            }
          }),
          total: Number(data.total) || 0
        }
      })
    })
  },

  // 从 firstPage 起取, 取到“有能显示的条目”或“到底”为止(见 MAX_CHASE_PAGES 的说明)
  fetchFrom(firstPage, seq, depth) {
    const step = depth || 0
    return this.fetchPage(firstPage).then((page) => {
      const reachedEnd = page.records.length === 0 || firstPage * PAGE_SIZE >= page.total
      const hasVisible = this.data.unreadOnly ? page.records.some((item) => !item.readByMe) : page.records.length > 0
      if (hasVisible || reachedEnd || step >= MAX_CHASE_PAGES - 1 || seq !== this._seq) return { records: page.records, pages: 1, total: page.total }
      return this.fetchFrom(firstPage + 1, seq, step + 1).then((next) => ({ records: page.records.concat(next.records), pages: next.pages + 1, total: next.total }))
    })
  },

  loadPosts() {
    const seq = this._seq
    const firstPage = this.data.page
    this.setData({ loading: true })
    this.fetchFrom(firstPage, seq).then((batch) => {
      if (seq !== this._seq) return
      const shown = this.data.unreadOnly ? batch.records.filter((item) => !item.readByMe) : batch.records
      const nextPage = firstPage + batch.pages
      this.setData({
        posts: firstPage > 1 ? this.data.posts.concat(shown) : shown,
        total: batch.total,
        page: nextPage,
        // 取过的页数 × 每页条数 已不少于总数, 或者接口给了空页: 到底了
        noMore: (nextPage - 1) * PAGE_SIZE >= batch.total || batch.records.length === 0,
        loading: false
      })
    }).catch((err) => {
      if (seq !== this._seq) return
      this.setData({ loading: false })
      showError(err, '内容加载失败')
    })
  },

  onReachBottom() {
    if (this.data.loading || this.data.noMore) return
    this.loadPosts()
  },

  onPullDownRefresh() {
    this.reload()
    wx.stopPullDownRefresh()
  },

  // 操作行右边的“⋯”(原型 D21: 朋友圈式菜单): 木鱼就在这里点(与详情页同一个接口); 评论、复诵进详情页对应的位置
  onMore(e) {
    const id = e.currentTarget.dataset.id
    const post = this.data.posts.find((x) => x.id === id)
    if (!post) return
    if (!this.data.canCreate) { this.goDetail(e); return }
    wx.showActionSheet({
      itemList: [post.likedByMe ? '取消木鱼' : '点木鱼', '写评论', '我要复诵'],
      success: (res) => {
        if (res.tapIndex === 0) this.likePost(id)
        else if (res.tapIndex === 1) wx.navigateTo({ url: `/pages/fabushi/detail/index?id=${id}&focus=comment` })
        else if (res.tapIndex === 2) wx.navigateTo({ url: `/pages/fabushi/detail/index?id=${id}&focus=recite` })
      }
    })
  },

  likePost(id) {
    toggleFabushiLike(id).then((res) => {
      const liked = Boolean(res && res.liked)
      const count = Number(res && res.likeCount) || 0
      this.setData({ posts: this.data.posts.map((post) => (post.id === id ? { ...post, likedByMe: liked, likeCount: count } : post)) })
    }).catch((err) => showError(err, '操作失败'))
  },

  goDetail(e) {
    const id = e.currentTarget.dataset.id
    if (!id) return
    wx.navigateTo({ url: '/pages/fabushi/detail/index?id=' + id })
  },

  // ===== 按书浏览: 先选书, 再进“按书浏览”页(原型 A3) =====
  onOpenPicker() {
    this.setData({ pickerOpen: true })
    if (this.data.books.length || this._booksLoading) return
    this._booksLoading = true
    getFabushiBooks({ status: 'approved', current: 1, size: 100 })
      .then((res) => {
        const books = ((res && res.records) || []).map((book) => ({ ...book, coverText: vm.coverText(book.title, 2), coverTone: vm.coverTone(book.id) }))
        this.setData({ books })
      })
      .catch((err) => showError(err, '书库加载失败'))
      .then(() => { this._booksLoading = false })
  },

  onClosePicker() { this.setData({ pickerOpen: false }) },

  noop() {},

  onBookTap(e) {
    const id = Number(e.currentTarget.dataset.id)
    if (!id) return
    this.setData({ pickerOpen: false })
    wx.navigateTo({ url: '/pages/fabushi/bybook/index?bookId=' + id })
  },

  goUpload() {
    wx.navigateTo({ url: '/pages/fabushi/upload/index' })
  }
})
