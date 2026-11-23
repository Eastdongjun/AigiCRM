const { hasPermission, requireLogin } = require('../../utils/auth')
const {
  getFabushiPersonalStats, getFabushiBooks, getFabushiPosts, getFabushiShelf, getFabushiRecords,
  getFabushiDashboard, getFabushiMonthly, getFabushiAwakenStatus, updateFabushiShelf,
  batchFabushiShelf, getFabushiShelfGroups, createFabushiShelfGroup, renameFabushiShelfGroup, deleteFabushiShelfGroup,
} = require('../../api/fabushi')
const { showError } = require('../../utils/feedback')

// 【D6/D7 切片 6】先自己后别人；觉醒为第一屏，书库与书架分列。
const TABS = [
  { value: 'awaken', label: '觉醒' },
  { value: 'square', label: '广场' },
  { value: 'lib', label: '书库' },
  { value: 'shelf', label: '书架' },
  { value: 'stats', label: '统计' }
]
const STATUS_LABEL = { wish: '想读', reading: '在读', finished: '已读完', favorite: '收藏' }
const SCOPE_LABEL = { songdu: '每日诵读', suishi: '每日随施', zaidu: '再读一本' }
const SCOPE_OPTIONS = [
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
    trendBadge: '',
    loading: true,
    tabs: TABS,
    activeTab: 'awaken',
    feedTab: 'songdu',
    scopeLabel: SCOPE_LABEL,
    scopeOptions: SCOPE_OPTIONS,
    statusChips: STATUS_CHIPS,
    books: [],
    posts: [],
    recommended: null,
    searchKeyword: '',
    hasView: false,
    awake: {},
    shelf: [],
    visibleShelf: [],
    groups: [],
    customGroups: [],
    groupFilter: 'all',
    statusFilter: 'all',
    manageMode: false,
    selected: [],
    records: { days: [], readDays: 0, readCount: 0 },
    recordEmpty: true,
    team: null,
    teamRanking: [],
    deptProgress: [],
    bookRanking: [],
    monthly: {},
    readingBook: null
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
    this.refreshReading()
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
    this.refreshReading()
    if (this.data.activeTab === 'awaken') this.loadAwaken()
    const { checkAndRemind } = require('../../../utils/fabushi-reminder')
    checkAndRemind()
  },

  refreshReading() {
    getFabushiShelf().then((res) => {
      const rows = (res && res.records) || []
      const reading = rows.find((x) => x.status === 'reading') || rows[0] || null
      this.setData({
        readingBook: reading ? {
          ...reading,
          progress: reading.totalPages ? Math.min(100, Math.round(Number(reading.lastPage || 1) * 100 / Number(reading.totalPages))) : (reading.progressPct || 0)
        } : null
      })
    }).catch(() => {})
  },

  goReadReading() {
    const b = this.data.readingBook
    if (!b) return
    this.goShelfBook({ currentTarget: { dataset: { id: b.bookId } } })
  },

  loadAll() {
    this.setData({ loading: true })
    Promise.all([
      getFabushiPersonalStats().catch(() => ({})),
      getFabushiBooks({ status: 'approved', current: 1, size: 20 }).catch(() => ({})),
      getFabushiPosts({ scopeType: 'songdu', current: 1, size: 10 }).catch(() => ({})),
      getFabushiAwakenStatus().catch(() => ({}))
    ]).then(([personal, bookRes, postRes, awake]) => {
      const books = (bookRes && bookRes.records) || []
      const posts = (postRes && postRes.records) || []
      this.setData({
        personal: personal || {},
        books,
        posts,
        recommended: books.find((book) => book.pdfFileId) || books[0] || null,
        awake: awake || {},
        loading: false
      })
    }).catch((err) => {
      this.setData({ loading: false })
      showError(err, '法布施加载失败')
    })
  },

  loadAwaken() {
    getFabushiAwakenStatus().then((res) => this.setData({ awake: res || {} })).catch(() => {})
  },

  loadPersonal() {
    getFabushiPersonalStats().then((res) => {
      this.setData({ personal: res || {} })
    }).catch((err) => {
      showError(err, '个人统计加载失败')
    })
  },

  loadPosts(scopeType) {
    getFabushiPosts({ scopeType: scopeType || 'songdu', current: 1, size: 10 }).then((res) => {
      this.setData({ posts: (res && res.records) || [] })
    }).catch(() => {})
  },

  loadShelf() {
    Promise.all([getFabushiShelf().catch(() => ({})), getFabushiShelfGroups().catch(() => ([]))]).then(([res, groups]) => {
      const rows = (res && res.records) || []
      const shelf = rows.map((x) => ({ ...x, statusText: STATUS_LABEL[x.status] || '在读', checked: false }))
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

  loadRecords() {
    getFabushiRecords().then((res) => {
      const r = res || { days: [], readDays: 0, readCount: 0 }
      this.setData({ records: r, recordEmpty: !((r.days || []).length) })
    }).catch(() => {})
  },

  onTab(e) {
    const value = e.currentTarget.dataset.value
    this.setData({ activeTab: value, selected: [], manageMode: false })
    if (value === 'awaken') { this.loadAwaken(); this.refreshReading() }
    else if (value === 'square') this.loadPosts(this.data.feedTab)
    else if (value === 'lib') this.loadPosts('songdu')
    else if (value === 'shelf') { this.loadShelf(); this.loadRecords() }
    else if (value === 'stats') {
      getFabushiPersonalStats().then((res) => this.setData({ personal: res || {} })).catch(() => {})
      if (hasPermission('fabushi:stats:view')) {
        getFabushiDashboard('week').then((res) => {
          const t = res || null
          this.setData({
            team: t,
            teamRanking: (t && t.ranking || []).slice(0, 5),
            deptProgress: (t && t.deptProgress || []).slice(0, 5),
            bookRanking: (t && t.bookRanking || []).slice(0, 5)
          })
        }).catch(() => {})
        getFabushiMonthly().then((res) => {
          const m = res || {}
          const pct = m.prev && m.prev.postsPct
          this.setData({
            monthly: m,
            trendBadge: pct == null ? '' : (pct > 0 ? '▲ ' : pct < 0 ? '▼ ' : '') + Math.abs(pct) + '%'
          })
        }).catch(() => {})
      }
    }
  },

  onFeedTab(e) {
    const scopeType = e.currentTarget.dataset.value
    this.setData({ feedTab: scopeType })
    this.loadPosts(scopeType)
  },

  onSearchInput(e) { this.setData({ searchKeyword: e.detail.value }) },

  onSearch() {
    const keyword = (this.data.searchKeyword || '').trim()
    if (!keyword) return
    getFabushiPosts({ scopeType: this.data.activeTab === 'square' ? this.data.feedTab : 'songdu', keyword: keyword, current: 1, size: 20 })
      .then((res) => {
        const records = (res && res.records) || []
        this.setData({ posts: records.filter((item) => `${item.title || ''}${item.content || ''}`.indexOf(keyword) >= 0) })
      }).catch(() => {})
  },

  refresh() { this.loadAll() },
  goFeed() { wx.navigateTo({ url: '/pages/fabushi/feed/index' }) },
  goSettings() { wx.navigateTo({ url: '/pages/fabushi/settings/index' }) },
  goUpload(e) {
    const scope = (e && e.currentTarget && e.currentTarget.dataset.scope) || 'songdu'
    wx.navigateTo({ url: `/pages/fabushi/upload/index?scope=${scope}` })
  },

  onShelfStatusFilter(e) { this.setData({ statusFilter: e.currentTarget.dataset.value }, () => this.applyShelfFilter()) },
  onGroupFilter(e) { this.setData({ groupFilter: e.currentTarget.dataset.value }, () => this.applyShelfFilter()) },
  toggleManage() { this.setData({ manageMode: !this.data.manageMode, selected: [] }) },

  onShelfTap(e) {
    const id = Number(e.currentTarget.dataset.id)
    if (this.data.manageMode) {
      const selected = this.data.selected
      const next = selected.indexOf(id) >= 0 ? selected.filter((x) => x !== id) : selected.concat(id)
      const shelf = this.data.shelf.map((row) => ({ ...row, checked: next.indexOf(row.id) >= 0 }))
      const visibleShelf = this.data.visibleShelf.map((row) => ({ ...row, checked: next.indexOf(row.id) >= 0 }))
      this.setData({ selected: next, shelf, visibleShelf })
      return
    }
    this.goShelfBook(e)
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

  togglePin(e) {
    const id = Number(e.currentTarget.dataset.id)
    const row = this.data.shelf.find((x) => x.id === id)
    if (!row) return
    updateFabushiShelf(id, { pinned: row.pinned ? 0 : 1 }).then(() => this.loadShelf()).catch((err) => showError(err, '置顶失败'))
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
