const { getCustomerRankings } = require('../../api/statistics')
const { requireLogin, hasPermission } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')

Page({
  data: {
    activeTab: 'incentive',
    tabs: [
      { key: 'incentive', title: '四项激励' },
      { key: 'relationship', title: '客情关系' },
      { key: 'core', title: '核心指标' }
    ],
    incentiveParents: [],
    rankings: { incentive: [], relationship: [], core: [] },
    list: [],
    loading: false
  },

  onLoad() {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !(hasPermission('score:view') || hasPermission('ranking:customer:view') || hasPermission('statistics:view'))) {
      wx.showToast({ title: '没有排行查看权限', icon: 'none' })
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    this.loadRanking()
  },

  switchTab(e) {
    const activeTab = e.currentTarget.dataset.key
    this.setData({ activeTab, list: this.data.rankings[activeTab] || [] })
  },

  loadRanking() {
    if (getApp().globalData.demoMode) {
      const incentiveParents = [
        { id: 'pre', categoryName: '安装前' },
        { id: 'mid', categoryName: '安装中' },
        { id: 'post', categoryName: '安装后' },
        { id: 'track', categoryName: '跟踪' }
      ]
      const rankings = {
        incentive: [{
          customerName: '华东智能制造集团',
          ownerName: '张三',
          industry: '制造业',
          totalScore: 36,
          recordCount: 4,
          byParent: {
            pre: { count: 1, score: 8 },
            mid: { count: 1, score: 10 },
            post: { count: 1, score: 12 },
            track: { count: 1, score: 6 }
          }
        }],
        relationship: [{ customerName: '华东智能制造集团', ownerName: '张三', industry: '制造业', relationshipScore: 3 }],
        core: [{ customerName: '华东智能制造集团', ownerName: '张三', industry: '制造业', relationshipScore: 3, indicator02: 3, indicator03: 2, indicator04: 2, indicator05: 2, totalScore: 12, invoiceTotal: 120, paymentTotal: 110, core: true }]
      }
      this.setData({ incentiveParents, rankings, list: rankings[this.data.activeTab] })
      return
    }
    this.setData({ loading: true })
    getCustomerRankings().then((rankings) => {
      const safeRankings = {
        incentive: rankings && rankings.incentiveRanking || [],
        relationship: rankings && rankings.relationshipRanking || [],
        core: rankings && rankings.indicatorRanking || []
      }
      this.setData({
        incentiveParents: rankings && rankings.incentiveParents || [],
        rankings: safeRankings,
        list: safeRankings[this.data.activeTab] || []
      })
    }).catch((error) => showError(error, '客户排行加载失败')).finally(() => this.setData({ loading: false }))
  },

  refresh() { this.loadRanking() },

  openDetail(e) {
    const item = this.data.list[Number(e.currentTarget.dataset.index)]
    if (!item || !item.customerId) return
    wx.navigateTo({ url: `/pages/customer/detail/index?id=${item.customerId}` })
  }
})
