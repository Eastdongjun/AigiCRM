// 【2026-10-24 自定义 tabBar】用户令"底部图标+下面区域做好看": 原生 tabBar 改不了
// 毛玻璃/双态图标, 切 custom 模式(app.json tabBar.custom: true)。
// 路由与选中态约定:
//   · tab 页注册以 app.json tabBar.list 为准(顺序同本文件 TABS/PATHS, 勿单边改);
//   · 每个 tab 页 onShow 里 getTabBar().setData({ selected: N }) 同步选中态
//     (custom-tab-bar 组件每页独立实例, 不同步会出现选中态滞留);
//   · 点击统一走 wx.switchTab(tabBar 页 navigateTo 会被微信静默拒绝)。
const { hasPermission, isAdmin } = require('../utils/auth')

const TABS = [
  { key: 'home', title: '首页', permission: null },
  { key: 'service', title: '四项服务', permission: 'incentive:view' },
  { key: 'files', title: '文件', permission: 'file:view' },
  { key: 'apps', title: '应用', permission: null },
  { key: 'profile', title: '我的', permission: null }
]
const PATHS = [
  '/pages/home/index',
  '/pages/incentive/home/index',
  '/pages/files/index',
  '/pages/app/index',
  '/pages/profile/index'
]

Component({
  data: { selected: 0, tabs: TABS },
  pageLifetimes: {
    show() { this.refreshTabs() }
  },
  attached() { this.refreshTabs() },
  methods: {
    refreshTabs() {
      const globalData = (typeof getApp === 'function' && getApp().globalData) || {}
      const demo = Boolean(globalData.demoMode)
      const admin = isAdmin()
      const tabs = TABS.map((tab) => ({
        ...tab,
        disabled: Boolean(tab.permission) && !demo && !admin && !hasPermission(tab.permission)
      }))
      this.setData({ tabs })
    },
    onTap(e) {
      const index = Number(e.currentTarget.dataset.index)
      const path = PATHS[index]
      if (!path || index === this.data.selected) return
      const tab = this.data.tabs[index]
      if (tab && tab.disabled) {
        wx.showToast({ title: '暂无该模块权限', icon: 'none' })
        return
      }
      wx.switchTab({ url: path })
    }
  }
})
