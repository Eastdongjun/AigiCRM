// 【2026-10-23 四项服务拆分·用户令】轻量客户记录页: tabBar「四项服务」点客户进入。
// 形态 = 客户信息条 + 纵向记录列表(触底增量 10 条), 无一客一册档案/翻页/左右滑动;
// 记录可点进既有详情页(pages/incentive/detail, 附件查看/下载/删除能力保留)。
// 数据经 eventChannel 从轻量列表页传入(与完整版 openCustomerDetail 同机制, 免二次请求);
// 子记录视图字段(titleText/metaText/...)在本页构建——满足视图绑定契约(派生字段须同目录生产)。
const { hasPermission, requireLogin } = require('../../../utils/auth')

const PAGE_SIZE = 10
const TAG_COLORS = { 安装前: '#b45309', 安装中: '#1d4fd7', 安装后: '#047857', 跟踪: '#7c3aed', 佐证: '#64748b' }

function buildFileGroupView(fileGroups) {
  return (fileGroups || []).map((group) => ({
    ...group,
    countText: `${(group.files || []).length} 个`,
    files: (group.files || []).map((file) => ({
      ...file,
      tagText: file.tag || '',
      tagColor: TAG_COLORS[file.tag] || '#64748b',
      showTag: Boolean(file.tag)
    }))
  }))
}

function buildChildRecordView(record) {
  const fileGroupsView = buildFileGroupView(record.fileGroups)
  return {
    ...record,
    titleText: record.categoryName || record.category || record.type || '未分类',
    metaText: `${record.period || '未设周期'} · ${record.userName || record.updatedByName || '未命名'} · ${record.createdAt || record.createTime || '-'}`,
    scoreText: `${record.score || record.points || 0} 分`,
    summaryText: record.preInstallQuestion || record.remark || '',
    showSummary: Boolean(record.preInstallQuestion || record.remark),
    fileCountText: `${fileGroupsView.reduce((sum, group) => sum + (group.files || []).length, 0)}个附件`,
    showFiles: fileGroupsView.length > 0,
    fileGroupsView
  }
}

Page({
  data: {
    customer: { name: '', industry: '', category: '', scoreText: '0 分', countText: '0条', fileCountText: '0文件' },
    children: [],
    pagedChildren: [],
    hasMore: false,
    loading: true
  },

  onLoad() {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('incentive:view')) {
      wx.showToast({ title: '没有四项服务查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
      return
    }
    const channel = this.getOpenerEventChannel && this.getOpenerEventChannel()
    if (channel && channel.on) {
      channel.on('payload', (payload) => this.applyPayload(payload))
    } else {
      // 无载荷直达(如分享链接): 本页无独立取数入口, 回退到轻量列表
      this.setData({ loading: false })
    }
  },

  applyPayload(payload) {
    const record = (payload && payload.record) || {}
    const children = ((payload && payload.children) || []).map(buildChildRecordView)
    // 与完整版一致的计数字段兜底(buildRecordView 的产物)
    this.setData({
      customer: {
        name: record.name || '客户',
        industry: record.industry || '',
        category: record.category || '',
        scoreText: record.scoreText || `${record.score || 0} 分`,
        countText: record.countText || `${record.count || children.length}条`,
        fileCountText: record.fileCountText || `${record.fileCountTotal || 0}文件`
      },
      children,
      pagedChildren: children.slice(0, PAGE_SIZE),
      hasMore: children.length > PAGE_SIZE,
      loading: false
    })
  },

  onReachBottom() {
    if (!this.data.hasMore) return
    const next = this.data.pagedChildren.length + PAGE_SIZE
    this.setData({
      pagedChildren: this.data.children.slice(0, next),
      hasMore: next < this.data.children.length
    })
  },

  // 记录操作保留: 点记录 → 既有详情页(附件查看/下载、删除/追加都在那里)
  openRecord(e) {
    const record = this.data.pagedChildren[Number(e.currentTarget.dataset.index)]
    if (!record || record.id === undefined || record.id === null) return
    wx.navigateTo({ url: `/pages/incentive/detail/index?id=${record.id}` })
  }
})
