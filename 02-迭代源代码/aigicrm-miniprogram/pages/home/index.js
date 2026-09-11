const { loadCurrentUser } = require('../../api/auth')
const { getExpiringList, getPendingCount } = require('../../api/reminder')
const { getDashboardData } = require('../../api/statistics')
const { homeCards, reminderRecords } = require('../../data/demo')
const { hasPermission, logout, requireLogin } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')

function formatToday(date = new Date()) {
  const weekdays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  return {
    date: `${date.getMonth() + 1}月${date.getDate()}日`,
    weekday: weekdays[date.getDay()],
    greeting: date.getHours() < 12 ? '早上好' : date.getHours() < 18 ? '下午好' : '晚上好'
  }
}

Page({
  data: {
    cards: homeCards,
    userName: '未登录',
    orgName: '',
    roleName: '',
    appName: 'AIGICRM',
    pendingCount: 0,
    expiring: [],
    canViewDashboard: false,
    showDashboardCards: false,
    today: formatToday()
  },

  onLoad() {
    if (!requireLogin()) return
    const app = getApp()
    this.setData({ appName: app.globalData.appName || 'AIGICRM' })
    if (app.globalData.demoMode) {
      this.setData({ userName: '张三', orgName: '晋蒙业务部', roleName: '业务员', canViewDashboard: true, showDashboardCards: true })
      this.setData({ pendingCount: reminderRecords.length, expiring: reminderRecords.filter((item) => item.expireDate) })
      return
    }

    const canViewDashboard = hasPermission('dashboard:view') || hasPermission('statistics:view')
    this.setData({ canViewDashboard, showDashboardCards: canViewDashboard, cards: [] })
    if (canViewDashboard) {
      getDashboardData().then((dashboard) => {
        this.setData({ showDashboardCards: true, cards: [
          [dashboard.myCustomers || 0, '我的客户'],
          [dashboard.inProtection || 0, '在保护期'],
          [dashboard.expiring || 0, '即将到期'],
          [dashboard.expired || 0, '已超期'],
          [dashboard.todayExpiring || 0, '今日到期']
        ] })
      }).catch((error) => {
        this.setData({ showDashboardCards: false })
        showError(error, '看板数据加载失败')
      })
    }

    loadCurrentUser().then((user) => {
      this.setData({
        userName: user.realName || user.username || '当前用户',
        orgName: user.deptName || '',
        roleName: user.roleName || ''
      })
    }).catch((error) => {
      this.setData({ userName: '未登录' })
      showError(error, '用户信息加载失败')
    })
    getPendingCount().then((count) => this.setData({ pendingCount: count || 0 })).catch((error) => showError(error, '提醒数量加载失败'))
    getExpiringList({ current: 1, size: 5 }).then((page) => this.setData({ expiring: (page.records || page.list || page.rows || []).slice(0, 5) })).catch((error) => showError(error, '到期提醒加载失败'))
  },

  openIncentive() {
    if (!hasPermission('incentive:view') && !getApp().globalData.demoMode) {
      wx.showToast({ title: '没有激励查询权限', icon: 'none' })
      return
    }
    wx.navigateTo({ url: '/pages/incentive/index' })
  },

  openApproval() {
    if (!hasPermission('approval:view') && !getApp().globalData.demoMode) {
      wx.showToast({ title: '没有审批权限', icon: 'none' })
      return
    }
    wx.navigateTo({ url: '/pages/approval/index' })
  },

  openReminder() {
    if (!hasPermission('reminder:view') && !getApp().globalData.demoMode) {
      wx.showToast({ title: '没有提醒权限', icon: 'none' })
      return
    }
    wx.navigateTo({ url: '/pages/reminder/index' })
  },

  openSea() {
    if (!hasPermission('sea:view') && !getApp().globalData.demoMode) {
      wx.showToast({ title: '没有公海权限', icon: 'none' })
      return
    }
    wx.navigateTo({ url: '/pages/sea/index' })
  },

  openCustomer(e) {
    const item = this.data.expiring[e.currentTarget.dataset.index]
    if (item && item.customerId) wx.navigateTo({ url: '/pages/customer/detail/index?id=' + item.customerId + '&from=home' })
  },

  openNewCustomer() {
    if (!hasPermission('customer:add') && !getApp().globalData.demoMode) {
      wx.showToast({ title: '没有新建客户权限', icon: 'none' })
      return
    }
    wx.navigateTo({ url: '/pages/customer/form/index' })
  },

  openStatistics() {
    if (!hasPermission('statistics:view') && !getApp().globalData.demoMode) {
      wx.showToast({ title: '没有统计查看权限', icon: 'none' })
      return
    }
    wx.navigateTo({ url: '/pages/statistics/index' })
  },

  logout
})
