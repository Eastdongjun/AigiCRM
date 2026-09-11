const { hasPermission, requireLogin } = require('../../utils/auth')

Page({
  data: { apps: [] },

  onLoad() {
    if (!requireLogin()) return
    this.setData({ apps: [
      { key: 'customer', title: '客户管理', subtitle: '客户列表、详情与新增', color: 'blue', permission: 'customer:view' },
      { key: 'incentiveUpload', title: '激励上传', subtitle: '价值服务上报与文件上传', color: 'green', permission: 'incentive:add' },
      { key: 'incentiveQuery', title: '激励查询', subtitle: '按客户查看激励记录', color: 'teal', permission: 'incentive:view' },
      { key: 'ranking', title: '客户排行', subtitle: '激励、客情、核心指标排行', color: 'purple', permission: 'statistics:view' },
      { key: 'indicator', title: '核心客户指标', subtitle: '核心客户指标总览', color: 'indigo', permission: 'statistics:view' },
      { key: 'statistics', title: '经营统计', subtitle: '客户、审批与公海摘要', color: 'cyan', permission: 'statistics:view' },
      { key: 'approval', title: '审批中心', subtitle: '处理和查看审批事项', color: 'green', permission: 'approval:view' },
      { key: 'reminder', title: '提醒中心', subtitle: '到期客户与待办提醒', color: 'red', permission: 'reminder:view' },
      { key: 'sea', title: '公海池', subtitle: '查看可见公海客户', color: 'orange', permission: 'sea:view' }
    ].filter((item) => getApp().globalData.demoMode || hasPermission(item.permission)) })
  },

  openApp(e) {
    const key = e.currentTarget.dataset.key
    const routes = { customer: '/pages/customer/index', incentiveUpload: '/pages/incentive/upload/index', incentiveQuery: '/pages/incentive/index', reminder: '/pages/reminder/index', approval: '/pages/approval/index', sea: '/pages/sea/index', indicator: '/pages/indicator/index', statistics: '/pages/statistics/index', ranking: '/pages/ranking/index' }
    if (routes[key]) wx.navigateTo({ url: routes[key] })
  }
})
