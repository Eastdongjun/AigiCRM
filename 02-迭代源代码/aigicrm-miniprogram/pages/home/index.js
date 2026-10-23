const { loadCurrentUser } = require('../../api/auth')
const { getExpiringList, getPendingCount } = require('../../api/reminder')
const { getDashboardData } = require('../../api/statistics')
const { homeCards, reminderRecords } = require('../../data/demo')
const { hasPermission, logout, requireLogin } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')
const { getTaskStatistics } = require('../../api/task')

function formatToday(date = new Date()) {
  const weekdays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  return {
    date: `${date.getMonth() + 1}月${date.getDate()}日`,
    weekday: weekdays[date.getDay()],
    greeting: date.getHours() < 12 ? '早上好' : date.getHours() < 18 ? '下午好' : '晚上好'
  }
}

function buildExpiringRow(item, index) {
  return {
    ...item,
    viewId: item.id || item.customerId || `expiring-${index}`,
    ownerExpireText: `${item.ownerName || '负责人'} · 到期 ${item.expireDate || '-'}`,
    remainingText: `剩 ${item.remainingDays || '--'} 天`
  }
}

function buildHomeView(state = {}) {
  const expiringRows = (state.expiring || []).map(buildExpiringRow)
  return {
    expiringRows,
    expiringCountText: String(expiringRows.length),
    showExpiringEmpty: expiringRows.length === 0
  }
}

/** 客户结构图：我的客户=总数，保护/即将到期/超期三段互斥，百分比相对总数 */
function buildOverview(cards) {
  const list = Array.isArray(cards) ? cards : []
  const total = Number(list[0] && list[0][0]) || 0
  const tones = ['primary', 'warn', 'danger']
  const rows = list.slice(1, 4).map((pair, index) => {
    const count = Number(pair && pair[0]) || 0
    const percent = total > 0 ? Math.round((count / total) * 1000) / 10 : 0
    return {
      label: (pair && pair[1]) || ['在保护期', '即将到期', '已超期'][index],
      valueText: String(count),
      percentText: `${percent}%`,
      barWidth: `${Math.min(100, percent)}%`,
      tone: tones[index],
      showBar: count > 0
    }
  })
  const protection = Number(list[1] && list[1][0]) || 0
  return {
    total,
    totalText: String(total),
    rows,
    protectionRate: total > 0 ? Math.round((protection / total) * 100) : 0,
    warnText: String(Number(list[2] && list[2][0]) || 0),
    expiredText: String(Number(list[3] && list[3][0]) || 0),
    todayExpiringText: String(Number(list[4] && list[4][0]) || 0)
  }
}

Page({
  data: {
    cards: homeCards,
    userName: '未登录',
    userInitial: '未',
    orgName: '',
    roleName: '',
    appName: 'AIGICRM',
    pendingCount: 0,
    taskSummary: '',
    expiring: [],
    canViewDashboard: false,
    showDashboardCards: false,
    today: formatToday(),
    overview: buildOverview(homeCards),
    homeView: buildHomeView({})
  },

  applyState(patch) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, overview: buildOverview(next.cards), homeView: buildHomeView(next) })
  },

  onLoad() {
    if (!requireLogin()) return
    const app = getApp()
    this.setData({ appName: app.globalData.appName || 'AIGICRM' })
    if (app.globalData.demoMode) {
      this.setData({ userName: '张三', userInitial: '张', orgName: '晋蒙业务部', roleName: '业务员', canViewDashboard: true, showDashboardCards: true })
      this.applyState({ pendingCount: reminderRecords.length, expiring: reminderRecords.filter((item) => item.expireDate) })
      return
    }
    this.loadHomeData()
  },

  // 【2026-09-20 功能体检】tab 页数据仅 onLoad 加载一次, 处理提醒/任务后回首页全为旧数;
  // 对齐 incentive/approval 的 onShow 刷新模式(PR-12 首批同款): 首显后每次显示刷新
  onShow() {
    if (!this._loadedOnce) {
      this._loadedOnce = true
      return
    }
    // 【容量止血 2026-10-05】onShow 30s 节流：微信每次返回页面/切前台都触发 onShow，
    // 无节流时首页 4 个请求被高频放大(容量评估流量模型专项)；30s 内的回流不重拉，
    // 只拦 onShow 重入 —— 下拉刷新(onPullDownRefresh)是显式刷新路径, 不受限。
    const now = Date.now()
    if (now - (this._lastShowTs || 0) < 30000) return
    this._lastShowTs = now
    if (!getApp().globalData.demoMode) this.loadHomeData()
  },

  // 【PM4 2026-10-07】首页节流后补手动刷新手段: 处理提醒/任务回来数据陈旧时下拉自救。
  // _lastShowTs 置 0 = 绕过 onShow 节流一次并重新起算(下拉后紧随的首次回流重拉,
  // 之后恢复节流);显式刷新不受 30s 节流限制(节流只拦 onShow 重入)。
  onPullDownRefresh() {
    this._lastShowTs = 0
    if (getApp().globalData.demoMode) {
      // 演示模式无接口可拉,数据在 onLoad 已固定,直接收起下拉动画
      wx.stopPullDownRefresh()
      return
    }
    this.loadHomeData().then(() => wx.stopPullDownRefresh())
  },

  loadHomeData() {
    const canViewDashboard = hasPermission('dashboard:view') || hasPermission('statistics:view')
    this.setData({ canViewDashboard, showDashboardCards: canViewDashboard, cards: [], overview: buildOverview([]) })
    // 【PM4 2026-10-07】各请求收进 tasks 并聚合返回: 下拉刷新(onPullDownRefresh)用它收尾
    // stopPullDownRefresh;各链路自带 catch(错误已在各自分支处理), 聚合结果恒 resolve
    const tasks = []
    if (canViewDashboard) {
      tasks.push(getDashboardData().then((dashboard) => {
        this.applyState({ showDashboardCards: true, cards: [
          [dashboard.myCustomers || 0, '我的客户'],
          [dashboard.inProtection || 0, '在保护期'],
          [dashboard.expiring || 0, '即将到期'],
          [dashboard.expired || 0, '已超期'],
          [dashboard.todayExpiring || 0, '今日到期']
        ] })
      }).catch((error) => {
        this.setData({ showDashboardCards: false })
        showError(error, '看板数据加载失败')
      }))
    }

    // 【PR-10 前端】我的待办任务数量(首页显示,不加底部菜单)
    if (!getApp().globalData.demoMode) {
      tasks.push(getTaskStatistics().then((stats) => {
        const pending = (stats && stats.pending) || 0
        const overdue = (stats && stats.overdue) || 0
        this.setData({
          taskSummary: pending > 0
            ? (overdue > 0 ? `待办 ${pending} 件(逾期 ${overdue})` : `待办 ${pending} 件`)
            : ''
        })
      }).catch(() => this.setData({ taskSummary: '' })))
    }

    tasks.push(loadCurrentUser().then((user) => {
      const userName = user.realName || user.username || '当前用户'
      this.setData({
        userName,
        userInitial: userName ? userName.slice(0, 1) : '用',
        orgName: user.deptName || '',
        roleName: user.roleName || ''
      })
    }).catch((error) => {
      this.setData({ userName: '未登录', userInitial: '未' })
      showError(error, '用户信息加载失败')
    }))
    tasks.push(getPendingCount().then((count) => this.setData({ pendingCount: count || 0 })).catch((error) => showError(error, '提醒数量加载失败')))
    tasks.push(getExpiringList({ current: 1, size: 5 }).then((page) => this.applyState({ expiring: (page.records || page.list || page.rows || []).slice(0, 5) })).catch((error) => showError(error, '到期提醒加载失败')))
    return Promise.all(tasks)
  },

  openIncentive() {
    if (!hasPermission('incentive:view') && !getApp().globalData.demoMode) {
      wx.showToast({ title: '没有四项服务查看权限', icon: 'none' })
      return
    }
    wx.switchTab({ url: '/pages/incentive/home/index' }) // tabBar 页必须 switchTab, navigateTo 会被微信拒绝(死按钮)【2026-10-23 拆分: tabBar=轻量客户记录】
  },

  openIncentiveUpload() {
    if (!hasPermission('incentive:add') && !getApp().globalData.demoMode) {
      wx.showToast({ title: '没有四项服务上传权限', icon: 'none' })
      return
    }
    wx.navigateTo({ url: '/pages/incentive/upload/index' })
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
    const item = this.data.homeView.expiringRows[e.currentTarget.dataset.index]
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

  openAppCenter() {
    wx.switchTab({ url: '/pages/app/index' })
  },

  logout
})
