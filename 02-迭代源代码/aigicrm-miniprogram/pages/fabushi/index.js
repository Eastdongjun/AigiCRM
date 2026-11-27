const { hasPermission, requireLogin } = require('../../utils/auth')
const { isAuthReady, ensurePermissionsReady } = require('../../utils/permission-ready')
const {
  getFabushiPersonalStats, getFabushiBooks, getFabushiPosts, getFabushiShelf,
  getFabushiMonthly, getFabushiAwakenStatus, updateFabushiShelf,
  batchFabushiShelf, getFabushiShelfGroups, createFabushiShelfGroup, renameFabushiShelfGroup, deleteFabushiShelfGroup,
  getFabushiSettings, toggleFabushiLike
} = require('../../api/fabushi')
const { getDownloadToken } = require('../../api/file')
const { showError } = require('../../utils/feedback')
// 【2026-11-25 用户裁决】界面按原型 v7 重做外观: 原型上的文字都由这个纯函数模块从接口数据算出来
const vm = require('../../utils/fabushi-view-model')

// 【D6/D7 切片 6】先自己后别人；觉醒为第一屏，书库与书架分列。
const TABS = [
  { value: 'awaken', label: '觉醒' },
  { value: 'square', label: '广场' },
  { value: 'lib', label: '书库' },
  { value: 'shelf', label: '书架' },
  { value: 'stats', label: '统计' }
]
// 【原型 A2】“全部”排第一: 不传 scopeType, 由后端返回三类合计
const SCOPE_OPTIONS = [
  { value: 'all', label: '全部' },
  { value: 'songdu', label: '每日诵读' },
  { value: 'suishi', label: '每日随施' },
  { value: 'zaidu', label: '再读一本' }
]
const STATUS_CHIPS = [
  { value: 'all', label: '全部' },
  { value: 'wish', label: '想读' },
  { value: 'reading', label: '在读' },
  { value: 'finished', label: '已读完' },
  { value: 'favorite', label: '收藏' }
]

Page({
  data: {
    canCreate: false,
    personal: {},
    loading: true,
    tabs: TABS,
    activeTab: 'awaken',
    feedTab: 'all',
    scopeOptions: SCOPE_OPTIONS,
    statusChips: STATUS_CHIPS,
    books: [],
    posts: [],
    searchKeyword: '',
    awake: {},
    awakening: vm.awakeningView({}, {}, null),
    reminder: vm.reminderView(null),
    shelf: [],
    visibleShelf: [],
    groups: [],
    customGroups: [],
    groupFilter: 'all',
    statusFilter: 'all',
    manageMode: false,
    selected: [],
    monthly: {},
    // 【原型 C5】统计页签的团队 / 个人两档, 以及算好百分比的榜单
    canViewStats: false,
    stats: { scope: 'team', monthLabel: '', memberCount: 0 },
    teamTotals: vm.teamTotals({}),
    trend: { posts: '', likes: '', comments: '', readUsers: '' },
    monthlyRanking: [],
    bookRows: [],
    categoryBars: [],
    deptRows: [],
    readingBook: null
  },

  onPermissionChanged() {
    if (getApp().globalData.demoMode) return
    // 【BUG-MP-TABBAR-PERM-RACE-20261127】冷启动回填也会触发本回调: 页面尚未初始化时
    // 交给 onLoad 的 init 统一取数, 避免同一份数据被拉两次。
    if (!this._loadedOnce) return
    if (!hasPermission('fabushi:view')) {
      wx.showToast({ title: '没有法布施查看权限', icon: 'none' })
      setTimeout(() => wx.switchTab({ url: '/pages/home/index' }), 300)
      return
    }
    this.loadAll()
  },

  onLoad() {
    if (!requireLogin()) return
    // 【BUG-MP-TABBAR-PERM-RACE-20261127】权限未回填时先等 /auth/info 回来再判定;
    // admin/demo/已回填走同步快路径(现有单测与登录后正常路径行为不变)。
    const init = () => {
      if (!hasPermission('fabushi:view')) {
        wx.switchTab({ url: '/pages/home/index' })
        return
      }
      this.setData({ canCreate: hasPermission('fabushi:create') })
      this.loadAll()
      this.refreshReading()
      this.loadReminder()
      this._loadedOnce = true
    }
    if (isAuthReady()) return init()
    ensurePermissionsReady().then(init)
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ selected: 2 })
    if (getApp().globalData.demoMode) { this.setData({ canCreate: true }); return }
    // 【BUG-MP-TABBAR-PERM-RACE-20261127】同 onLoad: 未回填时等回填, 不把"未知"当"无权限"踢人。
    const enter = () => {
      if (!hasPermission('fabushi:view')) {
        wx.showToast({ title: '没有法布施查看权限', icon: 'none' })
        setTimeout(() => wx.switchTab({ url: '/pages/home/index' }), 300)
        return
      }
      this.setData({ canCreate: hasPermission('fabushi:create') })
      this.loadPersonal()
      this.refreshReading()
      if (this.data.activeTab === 'awaken') this.loadAwaken()
      // 【2026-11-25 修正】路径原来多写了一级(../../../, 从 pages/fabushi/ 出发会指到项目之外), 11-19 起每次 onShow 都在这里抛
      //   “找不到模块”, 提醒检查从来没执行过; 同目录层级下应该是 ../../
      const { checkAndRemind } = require('../../utils/fabushi-reminder')
      checkAndRemind()
    }
    if (isAuthReady()) return enter()
    ensurePermissionsReady().then(enter)
  },

  // 主卡与三板块的文字(今日 x/3、下一步按钮、每块的副标题)由 awake / personal / readingBook 三份数据算出来, 任何一份更新后都要重算
  syncAwakening() {
    const { awake, personal, readingBook } = this.data
    const next = vm.awakeningView(awake, personal, readingBook)
    if (JSON.stringify(next) !== JSON.stringify(this.data.awakening)) this.setData({ awakening: next })
  },

  loadReminder() {
    getFabushiSettings().then((res) => {
      this.setData({ reminder: vm.reminderView(res || {}) })
    }).catch(() => {})
  },

  refreshReading() {
    getFabushiShelf().then((res) => {
      const rows = (res && res.records) || []
      const reading = rows.find((x) => x.status === 'reading') || rows[0] || null
      this.setData({ readingBook: reading ? this.readingView(reading) : null })
      this.syncAwakening()
    }).catch(() => {})
  },

  readingView(row) {
    const totalPages = Number(row.totalPages) || 0
    const progress = totalPages ? Math.min(100, Math.round(Number(row.lastPage || 1) * 100 / totalPages)) : (Number(row.progressPct) || 0)
    const title = String(row.title == null ? '' : row.title).replace(/[《》]/g, '')
    return {
      ...row,
      progress,
      coverText: vm.coverText(title),
      coverTone: vm.coverTone(row.bookId),
      titleText: `《${title}》`,
      progressText: vm.progressText({ ...row, progressPct: progress }),
      progressNote: progress ? `已读 ${progress}%` : '',
      statusText: vm.STATUS_LABEL[row.status] || '在读'
    }
  },

  // “续读”: 有电子书直接进阅读器(从上次读到的页接着读); 没有电子书进书目页
  goReadReading() {
    const b = this.data.readingBook
    if (!b) return
    if (b.pdfFileId) {
      wx.navigateTo({ url: `/pages/fabushi/reader/index?bookId=${b.bookId}&pdfFileId=${b.pdfFileId}` })
      return
    }
    this.goShelfBook({ currentTarget: { dataset: { id: b.bookId } } })
  },

  loadAll() {
    this.setData({ loading: true })
    Promise.all([
      getFabushiPersonalStats().catch(() => ({})),
      getFabushiBooks({ status: 'approved', current: 1, size: 20 }).catch(() => ({})),
      getFabushiAwakenStatus().catch(() => ({}))
    ]).then(([personal, bookRes, awake]) => {
      // 书库列表的小方块里只放得下两个字
      const books = ((bookRes && bookRes.records) || []).map((book) => ({ ...book, coverText: vm.coverText(book.title, 2), coverTone: vm.coverTone(book.id) }))
      this.setData({
        personal: personal || {},
        books,
        awake: awake || {},
        loading: false
      })
      this.syncAwakening()
    }).catch((err) => {
      this.setData({ loading: false })
      showError(err, '法布施加载失败')
    })
    this.loadPosts(this.data.feedTab)
  },

  loadBooks() {
    getFabushiBooks({ status: 'approved', current: 1, size: 20 }).then((res) => {
      const books = ((res && res.records) || []).map((book) => ({ ...book, coverText: vm.coverText(book.title, 2), coverTone: vm.coverTone(book.id) }))
      this.setData({ books })
    }).catch(() => {})
  },

  loadAwaken() {
    getFabushiAwakenStatus().then((res) => {
      this.setData({ awake: res || {} })
      this.syncAwakening()
    }).catch(() => {})
  },

  loadPersonal() {
    getFabushiPersonalStats().then((res) => {
      this.setData({ personal: res || {} })
      this.syncAwakening()
    }).catch((err) => {
      showError(err, '个人统计加载失败')
    })
  },

  // 信息流里的图片要带下载令牌才能显示: 先取一次令牌(同一批共用), 再把条目整理成视图字段。
  // seq: 切换页签或连续搜索时, 旧的应答晚到不能覆盖新的(与认知广场页同一个做法)
  loadPosts(scopeType, keyword) {
    const seq = (this._postSeq = (this._postSeq || 0) + 1)
    const params = { current: 1, size: 20 }
    if (scopeType && scopeType !== 'all') params.scopeType = scopeType
    if (keyword) params.keyword = keyword
    this.setData({ loading: true })
    getFabushiPosts(params).then((res) => {
      if (seq !== this._postSeq) return
      const records = (res && res.records) || []
      const needsToken = records.some((item) => (item.attachments || []).some((att) => att.fileType === 'image'))
      const ready = needsToken ? getDownloadToken().catch(() => null) : Promise.resolve(null)
      return ready.then((tokenRes) => {
        if (seq !== this._postSeq) return
        const token = tokenRes ? ((tokenRes.token != null ? tokenRes.token : tokenRes.data) || '') : ''
        const base = getApp().globalData.apiBaseUrl || ''
        // isNew / typeLabel / timeText 等是 WXML 按 item.xxx 读的派生字段, 逐个在这里写出(视图绑定契约要的就是这个“生产处”)
        const posts = records.map((item) => {
          const view = vm.postView(item, token, null, base)
          return {
            ...item,
            isNew: view.isNew,
            typeLabel: view.typeLabel,
            timeText: view.timeText,
            pics: view.pics,
            picCount: view.picCount,
            hasAudio: view.hasAudio,
            audioCount: view.audioCount,
            likerText: view.likerText,
            likedByMe: Boolean(item.likedByMe),
            likeCount: Number(item.likeCount) || 0
          }
        })
        this.setData({ posts, loading: false })
      })
    }).catch((err) => {
      if (seq !== this._postSeq) return
      this.setData({ loading: false })
      showError(err, '内容加载失败')
    })
  },

  loadShelf() {
    Promise.all([getFabushiShelf().catch(() => ({})), getFabushiShelfGroups().catch(() => ([]))]).then(([res, groups]) => {
      const rows = (res && res.records) || []
      // 书架格子的派生字段(封面文字与底色、小字、状态名、勾选)逐个写出, 同上
      const shelf = rows.map((row, index) => {
        const view = vm.shelfRowView(row, index)
        return { ...row, coverText: view.coverText, coverTone: view.coverTone, subText: view.subText, statusText: view.statusText, checked: view.checked, titleText: vm.coverText(row.title, 12) }
      })
      this.setData({
        shelf,
        visibleShelf: shelf,
        groups: groups || [],
        customGroups: (groups || []).filter((g) => g.groupType === 'custom')
      }, () => this.applyShelfFilter())
    })
  },

  applyShelfFilter() {
    const { shelf, statusFilter, groupFilter } = this.data
    const visibleShelf = shelf.filter((row) => {
      if (statusFilter !== 'all' && row.status !== statusFilter) return false
      if (groupFilter === 'all') return true
      const group = this.data.groups.find((g) => String(g.id) === String(groupFilter))
      if (!group) return true
      if (group.groupType === 'auto') return row.categoryCode === group.categoryCode
      return String(row.groupId || '') === String(groupFilter)
    })
    this.setData({ visibleShelf })
  },

  // 统计页签(原型 C5): 月度报告。四格 = 三类各多少条 + 被复诵多少次(由成员榜逐行加出来), 另留一行互动数与环比;
  // 成员榜、书目榜、类别热度的条宽按各自最大值折成百分比
  loadStats() {
    getFabushiPersonalStats().then((res) => this.setData({ personal: res || {} })).catch(() => {})
    this.setData({ canViewStats: hasPermission('fabushi:stats:view') })
    if (!hasPermission('fabushi:stats:view')) {
      this.setData({ stats: { scope: 'personal', monthLabel: '', memberCount: 0 } })
      return
    }
    getFabushiMonthly().then((res) => {
      const m = res || {}
      const prev = m.prev || {}
      const ranking = (m.ranking || []).slice(0, 8)
      const top = Math.max(1, ...ranking.map((row) => Number(row.postCount) || 0))
      const bookRanking = (m.bookRanking || []).slice(0, 5)
      const topBook = Math.max(1, ...bookRanking.map((row) => Number(row.readCount) || 0))
      const categories = (m.categoryReads || []).slice(0, 6)
      const topRead = Math.max(1, ...categories.map((row) => Number(row.readCount) || 0))
      const month = String(m.month || '')
      this.setData({
        monthly: m,
        teamTotals: vm.teamTotals(m),
        trend: { posts: vm.trendBadge(prev.postsPct), likes: vm.trendBadge(prev.likesPct), comments: vm.trendBadge(prev.commentsPct), readUsers: vm.trendBadge(prev.readUsersPct) },
        monthlyRanking: ranking.map((row) => ({ ...row, barPct: Math.round((Number(row.postCount) || 0) * 100 / top) })),
        bookRows: bookRanking.map((row) => ({ ...row, barPct: Math.round((Number(row.readCount) || 0) * 100 / topBook) })),
        categoryBars: categories.map((row) => ({ ...row, barPct: Math.round((Number(row.readCount) || 0) * 100 / topRead) })),
        deptRows: (m.deptProgress || []).slice(0, 6),
        stats: {
          scope: this.data.stats.scope,
          monthLabel: /^\d{4}-\d{2}$/.test(month) ? `${Number(month.slice(5))}月` : month,
          // 接口在拿不到人数时给的是 -1: 不能当成人数显示
          memberCount: Math.max(0, Number(m.memberCount) || 0)
        }
      })
    }).catch(() => {})
  },

  onStatsScope(e) {
    this.setData({ stats: { ...this.data.stats, scope: e.currentTarget.dataset.scope } })
  },

  onTab(e) {
    const value = e.currentTarget.dataset.value
    this.setData({ activeTab: value, selected: [], manageMode: false })
    if (value === 'awaken') { this.loadAwaken(); this.refreshReading() }
    else if (value === 'square') this.loadPosts(this.data.feedTab)
    else if (value === 'lib') this.loadBooks()
    else if (value === 'shelf') this.loadShelf()
    else if (value === 'stats') this.loadStats()
  },

  onFeedTab(e) {
    const scopeType = e.currentTarget.dataset.value
    this.setData({ feedTab: scopeType, searchKeyword: '' })
    this.loadPosts(scopeType)
  },

  onSearchInput(e) { this.setData({ searchKeyword: e.detail.value }) },

  onSearch() {
    const keyword = (this.data.searchKeyword || '').trim()
    if (!keyword) return
    this.loadPosts(this.data.feedTab, keyword)
  },

  // 广场页签里直接点“木鱼”(原型 A1 的操作条): 与详情页同一个接口, 返回后只改这一条的“我点没点”与木鱼数
  onFeedLike(e) {
    if (!this.data.canCreate) return
    const id = e.currentTarget.dataset.id
    const index = this.data.posts.findIndex((post) => post.id === id)
    if (index < 0) return
    toggleFabushiLike(id).then((res) => {
      const liked = Boolean(res && res.liked)
      const count = Number(res && res.likeCount) || 0
      // 整个数组换掉(与本页其余写法一致); 这一条已经不在列表里(期间重新加载过)就不会被改到
      this.setData({ posts: this.data.posts.map((post) => (post.id === id ? { ...post, likedByMe: liked, likeCount: count } : post)) })
    }).catch((err) => showError(err, '操作失败'))
  },

  goFeed() { wx.navigateTo({ url: '/pages/fabushi/feed/index' }) },
  goSettings() { wx.navigateTo({ url: '/pages/fabushi/settings/index' }) },
  goRecords() { wx.navigateTo({ url: '/pages/fabushi/records/index' }) },
  goUpload(e) {
    const scope = (e && e.currentTarget && e.currentTarget.dataset.scope) || 'songdu'
    wx.navigateTo({ url: `/pages/fabushi/upload/index?scope=${scope}` })
  },

  onShelfStatusFilter(e) { this.setData({ statusFilter: e.currentTarget.dataset.value }, () => this.applyShelfFilter()) },
  onGroupFilter(e) { this.setData({ groupFilter: e.currentTarget.dataset.value }, () => this.applyShelfFilter()) },
  toggleManage() { this.setData({ manageMode: !this.data.manageMode, selected: [] }) },

  // 管理模式下点封面 = 勾选这一条(按书架记录编号); 平时点封面 = 进书目页(按书目编号)
  onShelfTap(e) {
    const { id, bookId } = e.currentTarget.dataset
    if (this.data.manageMode) {
      const key = Number(id)
      const selected = this.data.selected
      const next = selected.indexOf(key) >= 0 ? selected.filter((x) => x !== key) : selected.concat(key)
      const mark = (row) => ({ ...row, checked: next.indexOf(row.id) >= 0 })
      this.setData({
        selected: next,
        shelf: this.data.shelf.map(mark),
        visibleShelf: this.data.visibleShelf.map(mark)
      })
      return
    }
    this.goShelfBook({ currentTarget: { dataset: { id: bookId } } })
  },

  changeShelfStatus(e) {
    const id = Number(e.currentTarget.dataset.id)
    wx.showActionSheet({
      itemList: ['想读', '在读', '已读完', '收藏'],
      success: (res) => {
        const status = ['wish', 'reading', 'finished', 'favorite'][res.tapIndex]
        updateFabushiShelf(id, { status }).then(() => this.loadShelf()).catch((err) => showError(err, '状态更新失败'))
      }
    })
  },

  onBatch(e) {
    const action = e.currentTarget.dataset.action
    if (!this.data.selected.length) { wx.showToast({ title: '请先选择书目', icon: 'none' }); return }
    if (action === 'group') {
      const groups = this.data.customGroups
      if (!groups.length) { wx.showToast({ title: '请先新建分组', icon: 'none' }); return }
      wx.showActionSheet({
        itemList: groups.map((g) => g.name).slice(0, 6),
        success: (res) => {
          const groupId = groups[res.tapIndex] && groups[res.tapIndex].id
          batchFabushiShelf({ ids: this.data.selected, action: 'group', groupId })
            .then(() => { this.setData({ selected: [] }); this.loadShelf() })
            .catch((err) => showError(err, '加入分组失败'))
        }
      })
      return
    }
    batchFabushiShelf({ ids: this.data.selected, action })
      .then(() => { this.setData({ selected: [] }); this.loadShelf(); wx.showToast({ title: '已更新', icon: 'success' }) })
      .catch((err) => showError(err, '批量操作失败'))
  },

  onNewGroup() {
    wx.showModal({
      title: '新建书架分组',
      editable: true,
      placeholderText: '如：案头常读',
      success: (res) => {
        const name = res.content && res.content.trim()
        if (!res.confirm || !name) return
        createFabushiShelfGroup(name).then(() => this.loadShelf()).catch((err) => showError(err, '新建分组失败'))
      }
    })
  },

  onGroupManage(e) {
    const id = Number(e.currentTarget.dataset.id)
    const name = e.currentTarget.dataset.name
    const g = this.data.groups.find((x) => x.id === id)
    wx.showActionSheet({
      itemList: ['改名', '删除分组（书回未分组）'],
      success: (res) => {
        if (res.tapIndex === 0) {
          wx.showModal({
            title: '分组改名', editable: true, content: name,
            success: (modal) => {
              const next = modal.content && modal.content.trim()
              if (!modal.confirm || !next) return
              renameFabushiShelfGroup(id, next).then(() => this.loadShelf()).catch((err) => showError(err, '改名失败'))
            }
          })
        } else if (res.tapIndex === 1) {
          wx.showModal({
            title: '删除分组', content: `删除「${g ? g.name : name}」？书会回到未分组，阅读进度保留。`,
            success: (modal) => {
              if (!modal.confirm) return
              deleteFabushiShelfGroup(id).then(() => this.loadShelf()).catch((err) => showError(err, '删除失败'))
            }
          })
        }
      }
    })
  },

  goShelfBook(e) { wx.navigateTo({ url: `/pages/fabushi/book/index?id=${e.currentTarget.dataset.id}` }) },
  goBook() { wx.navigateTo({ url: '/pages/fabushi/book/index' }) },
  openPost(e) { wx.navigateTo({ url: `/pages/fabushi/detail/index?id=${e.currentTarget.dataset.id}` }) }
})
