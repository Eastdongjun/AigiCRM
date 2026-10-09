const { hasPermission, requireLogin } = require('../../utils/auth')

const APP_ICONS = {
  customer: '人',
  incentiveUpload: '传',
  incentiveQuery: '查',
  reminder: '铃',
  approval: '审',
  sea: '海',
  indicator: '核',
  statistics: '统',
  ranking: '榜'
}

function buildAppView(apps) {
  const entries = (apps || []).map((item) => ({
    ...item,
    iconText: APP_ICONS[item.key] || '•'
  }))
  return {
    entries,
    showEmpty: entries.length === 0
  }
}

Page({
  data: { apps: [], appView: buildAppView([]) },

  onLoad() {
    if (!requireLogin()) return
    const apps = [
      // 【A4 2026-10-08 二轮】宫格命名与 PC 生产菜单名对齐（客户列表/核心客户六项指标），消除跨端页面名漂移
      { key: 'customer', title: '客户列表', subtitle: '客户列表、详情与新增', color: 'blue', permission: 'customer:view' },
      { key: 'incentiveUpload', title: '四项激励上传', subtitle: '记录、槽位文件与客户积分', color: 'green', permission: 'incentive:add' },
      { key: 'incentiveQuery', title: '四项激励', subtitle: '分类树、统计、排行与记录', color: 'teal', permission: 'incentive:view' },
      { key: 'ranking', title: '客户排行', subtitle: '激励、客情、核心指标排行', color: 'purple', permission: 'statistics:view' },
      { key: 'indicator', title: '核心客户六项指标', subtitle: '核心客户指标总览', color: 'indigo', permission: 'statistics:view' },
      { key: 'statistics', title: '统计分析', subtitle: '客户、审批与公海摘要', color: 'cyan', permission: 'statistics:view' },
      { key: 'approval', title: '审批管理', subtitle: '处理和查看审批事项', color: 'green', permission: 'approval:view' },
      { key: 'reminder', title: '提醒管理', subtitle: '到期客户与待办提醒', color: 'red', permission: 'reminder:view' },
      { key: 'sea', title: '公海池', subtitle: '查看可见公海客户', color: 'orange', permission: 'sea:view' }
    ].filter((item) => getApp().globalData.demoMode || hasPermission(item.permission))
    this.setData({ apps, appView: buildAppView(apps) })
  },

  openApp(e) {
    const key = e.currentTarget.dataset.key
    const routes = { customer: '/pages/customer/index', incentiveUpload: '/pages/incentive/upload/index', incentiveQuery: '/pages/incentive/index', reminder: '/pages/reminder/index', approval: '/pages/approval/index', sea: '/pages/sea/index', indicator: '/pages/indicator/index', statistics: '/pages/statistics/index', ranking: '/pages/ranking/index' }
    if (routes[key]) wx.navigateTo({ url: routes[key] })
  }
})
