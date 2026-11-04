const { hasPermission, requireLogin } = require('../../utils/auth')

const APP_ICONS = {
  customer: '人',
  customerCreate: '增',
  files: '文',
  incentiveUpload: '传',
  incentiveQuery: '查',
  reminder: '铃',
  approval: '审',
  sea: '海',
  indicator: '核',
  statistics: '统',
  ranking: '榜'
}

// 半宽卡片里的副标题要能一行放下，完整副标题仍保留在 apps 数据里供无障碍/契约使用。
const TILE_SUBTITLES = {
  customer: '客户列表与新增',
  customerCreate: '录入客户档案',
  files: '文件、文件夹与分享',
  incentiveUpload: '记录、文件与学分',
  incentiveQuery: '分类、统计、排行与阅读',
  reminder: '到期与待办提醒',
  approval: '处理审批事项',
  sea: '可见公海客户',
  indicator: '核心指标总览',
  statistics: '客户、审批与公海',
  ranking: '激励与客情排行'
}

// 应用中心按业务模块组织，而不是把 9 个入口平铺成菜单。
// apps 里放应用 key，构建时按当前账号权限过滤，空模块整块隐藏。
const APP_MODULES = [
  // 【2026-11-04 用户令"统一命名 统一数据来源"】模块分组与 PC 菜单分组逐一对齐:
  //   客户经营=客户管理(PC 业务核心)/文件管理=文件管理/四项服务=四项服务/考核与分析=考核与分析(PC 组,
  //   收纳 学分排行+统计分析+核心客户六项指标)/流程协同=公海·审批·提醒 协作面; 原「经营分析」组撤销。
  { key: 'customer', title: '客户经营', subtitle: '客户列表、详情与新增', apps: ['customer', 'customerCreate'] },
  // 【2026-11-03 用户令"应用与 PC 对齐"】补文件管理(PC 正式菜单 file:view; tabBar 页 switchTab 直达)
  { key: 'files', title: '文件管理', subtitle: '文件库、文件夹与分享', apps: ['files'] },
  { key: 'incentive', title: '四项服务', subtitle: '上传、查询与统计', apps: ['incentiveUpload', 'incentiveQuery', 'incentiveStats'] },
  { key: 'assessment', title: '考核与分析', subtitle: '学分排行 · 统计分析 · 核心指标', apps: ['ranking', 'statistics', 'indicator'] },
  { key: 'workflow', title: '流程协同', subtitle: '审批、提醒与公海协作', apps: ['approval', 'reminder', 'sea'] }
]

function padIndex(index) {
  return index < 9 ? `0${index + 1}` : String(index + 1)
}

/** 奇数额模块的最后一项通栏，避免 2 列网格留下半格空白；单应用模块整行展示 */
function decorateEntries(entries) {
  const last = entries.length - 1
  return entries.map((entry, index) => ({
    ...entry,
    wide: entries.length === 1 || (entries.length % 2 === 1 && index === last)
  }))
}

function buildAppView(apps) {
  const entries = (apps || []).map((item) => ({
    ...item,
    iconText: APP_ICONS[item.key] || '•',
    tileSubtitle: TILE_SUBTITLES[item.key] || item.subtitle
  }))
  const byKey = {}
  entries.forEach((item) => { byKey[item.key] = item })
  const groups = APP_MODULES
    .map((module) => ({
      key: module.key,
      title: module.title,
      subtitle: module.subtitle,
      entries: decorateEntries(module.apps.map((key) => byKey[key]).filter(Boolean))
    }))
    .filter((module) => module.entries.length > 0)
    .map((module, index) => ({
      ...module,
      indexText: padIndex(index),
      countText: `${module.entries.length} 个应用`
    }))
  return {
    entries,
    groups,
    showEmpty: entries.length === 0,
    totalText: String(entries.length),
    groupText: `${groups.length} 个模块`
  }
}

Page({
  data: { apps: [], appView: buildAppView([]) },

  onLoad() {
    if (!requireLogin()) return
    const apps = [
      // 【A4 2026-10-08 二轮】宫格命名与 PC 生产菜单名对齐（客户列表/核心客户六项指标），消除跨端页面名漂移
      { key: 'customer', title: '客户列表', subtitle: '客户列表、详情与新增', color: 'blue', permission: 'customer:view' },
      // 【2026-11-03 用户令"应用与 PC 对齐"】补新增客户直达卡(PC 该入口在列表页「+」按钮, 手机上给直达);
      { key: 'customerCreate', title: '新增客户', subtitle: '录入客户档案信息', color: 'blue', permission: 'customer:add' },
      { key: 'files', title: '文件管理', subtitle: '文件库、文件夹与分享', color: 'cyan', permission: 'file:view' },
      { key: 'incentiveUpload', title: '激励上传', subtitle: '四大类 · 子类 · 槽位佐证', color: 'green', permission: 'incentive:add' },
      { key: 'incentiveQuery', title: '激励查询', subtitle: '客户记录 · 分类 · 阅读', color: 'teal', permission: 'incentive:view' },
      // 【2026-11-04 逐功能建码】激励统计页双 pane 各绑注册码(统计=stats:view/排行=rank:view), 入口=并集
      { key: 'incentiveStats', title: '激励统计', subtitle: '统计 · 客户/个人/部门排行', color: 'amber', permissions: ['incentive:stats:view', 'incentive:rank:view'] },
      // 【2026-11-03 用户裁决】入口卡片名与页面标题统一为「学分排行」(原「客户排行」名实不符)
      // 【2026-11-04 注册表对齐】排行页内含三榜(学分=score:view/客户=statistics:view/激励=incentive:view)，
      // 入口按注册码并集(any-of)显隐——与 PC 学分排行(score:view)、客户排行(statistics:view)菜单同源，不发明新码
      { key: 'ranking', title: '学分排行', subtitle: '学分、客情、核心指标排行', color: 'purple', permissions: ['score:view', 'statistics:view', 'incentive:view'] },
      { key: 'indicator', title: '核心客户六项指标', subtitle: '核心客户指标总览', color: 'indigo', permission: 'statistics:view' },
      { key: 'statistics', title: '统计分析', subtitle: '客户、审批与公海摘要', color: 'cyan', permission: 'statistics:view' },
      { key: 'approval', title: '审批管理', subtitle: '处理和查看审批事项', color: 'green', permission: 'approval:view' },
      { key: 'reminder', title: '提醒管理', subtitle: '到期客户与待办提醒', color: 'red', permission: 'reminder:view' },
      { key: 'sea', title: '公海池', subtitle: '查看可见公海客户', color: 'orange', permission: 'sea:view' }
    ].filter((item) => {
      // 【2026-11-04 注册表对齐】通用 any-of 门禁：permission=单码，permissions=注册码并集（多功能页用），
      // 码值全部来自权限中心注册表，不在此处发明/特判
      if (getApp().globalData.demoMode) return true
      if (Array.isArray(item.permissions)) return item.permissions.some((code) => hasPermission(code))
      return hasPermission(item.permission)
    })
    this.setData({ apps, appView: buildAppView(apps) })
  },

  // 【2026-10-24 自定义 tabBar】每页独立实例, onShow 同步选中态(勿删, 否则选中滞留旧页)
  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ selected: 3 })
  },

  openApp(e) {
    const key = e.currentTarget.dataset.key
    // 【2026-10-23 四项服务拆分】完整工作台(pages/incentive/index)不再是 tabBar 页,
    // 应用中心「四项服务应用」navigateTo 直达; tabBar 轻量客户记录在 pages/incentive/home/index
    // 【2026-11-03 用户令"查询和统计分开"】两入口带 mode 各锁页签集(query=记录/分类/阅读, stats=统计/排行)
    const routes = { customer: '/pages/customer/index', customerCreate: '/pages/customer/form/index', files: '/pages/files/index', incentiveUpload: '/pages/incentive/upload/index', incentiveQuery: '/pages/incentive/index?mode=query', incentiveStats: '/pages/incentive/index?mode=stats', reminder: '/pages/reminder/index', approval: '/pages/approval/index', sea: '/pages/sea/index', indicator: '/pages/indicator/index', statistics: '/pages/statistics/index', ranking: '/pages/ranking/index' }
    // 【#11 2026-10-12】tabBar 页必须 switchTab:navigateTo 跳 tabBar 页静默失败
    const tabPages = ['/pages/home/index', '/pages/incentive/home/index', '/pages/files/index', '/pages/app/index', '/pages/profile/index']
    const url = routes[key]
    if (!url) return
    if (tabPages.indexOf(url) >= 0) {
      wx.switchTab({ url })
    } else {
      wx.navigateTo({ url })
    }
  }
})
