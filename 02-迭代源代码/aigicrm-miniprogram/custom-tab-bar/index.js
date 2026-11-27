// 【2026-10-24 自定义 tabBar】用户令"底部图标+下面区域做好看": 原生 tabBar 改不了
// 毛玻璃/双态图标, 切 custom 模式(app.json tabBar.custom: true)。
// 路由与选中态约定:
//   · tab 页注册以 app.json tabBar.list 为准(顺序同本文件 TABS/PATHS, 勿单边改);
//   · 每个 tab 页 onShow 里 getTabBar().setData({ selected: N }) 同步选中态
//     (custom-tab-bar 组件每页独立实例, 不同步会出现选中态滞留);
//   · 点击统一走 wx.switchTab(tabBar 页 navigateTo 会被微信静默拒绝)。
const { hasPermission, isAdmin } = require('../utils/auth')
const { isAuthReady, ensurePermissionsReady } = require('../utils/permission-ready')

const TABS = [
  { key: 'home', title: '首页', permission: null },
  { key: 'service', title: '四项服务', permission: 'incentive:view' },
  { key: 'fabushi', title: '法布施', permission: 'fabushi:view' },
  { key: 'apps', title: '应用', permission: null },
  { key: 'profile', title: '我的', permission: null }
]
const PATHS = [
  '/pages/home/index',
  '/pages/incentive/home/index',
  '/pages/fabushi/index',
  '/pages/app/index',
  '/pages/profile/index'
]

Component({
  data: { selected: 0, tabs: TABS },
  pageLifetimes: {
    show() { this.refreshTabs() }
  },
  attached() { this.refreshTabs() },
  lifetimes: {
    detached() { this._detached = true }
  },
  methods: {
    refreshTabs() {
      const globalData = (typeof getApp === 'function' && getApp().globalData) || {}
      const demo = Boolean(globalData.demoMode)
      const admin = isAdmin()
      // 【BUG-MP-TABBAR-PERM-RACE-20261127】权限尚未回填时不算 disabled:
      // permissions=[] 在回填前是"未知"而不是"无权限"; 回填完成后本方法会被
      // markChanged(api/auth.js)或下面的 ensure 回调再次调用, 再按真实权限收敛。
      const ready = demo || admin || isAuthReady()
      const tabs = TABS.map((tab) => ({
        ...tab,
        disabled: ready && Boolean(tab.permission) && !demo && !admin && !hasPermission(tab.permission),
        dot: tab.key === 'fabushi' && Boolean(globalData.fabushiUnread) // 【F4a】今日未读红点
      }))
      const patch = { tabs }
      // 【BUG-MP-TABBAR-PERM-RACE-20261127】selected 按当前路由校正:
      // 各页 onShow 的 getTabBar() 可能在组件就绪前调用, selected 会滞留旧页,
      // 旧逻辑按 selected 去重会把"点当前页"误吞。这里让路由成为单一出处。
      if (typeof getCurrentPages === 'function') {
        const pages = getCurrentPages()
        const last = pages && pages[pages.length - 1]
        const route = last && last.route ? '/' + last.route : ''
        const idx = PATHS.indexOf(route)
        if (idx >= 0) patch.selected = idx
      }
      if (!this._detached) this.setData(patch)
      if (!ready && !this._waitingAuth) {
        this._waitingAuth = true
        ensurePermissionsReady().then((ok) => {
          this._waitingAuth = false
          // 回填失败(网络/无 token)不再递归, 保持"未知不禁用"; 后续权限到位时
          // markChanged 仍会 refreshTabs。只有真的就绪才重算一次。
          if (ok && !this._detached) this.refreshTabs()
        })
      }
    },
    onTap(e) {
      const index = Number(e.currentTarget.dataset.index)
      const path = PATHS[index]
      if (!path) return
      // 【BUG-MP-TABBAR-PERM-RACE-20261127】去重按路由, 不按 selected:
      // selected 可能因 getTabBar() 未就绪而滞留, 旧判定会把"点的就是当前页"吞掉。
      let current = ''
      if (typeof getCurrentPages === 'function') {
        const pages = getCurrentPages()
        const last = pages && pages[pages.length - 1]
        current = last && last.route ? '/' + last.route : ''
      }
      if (current === path) return
      const tab = this.data.tabs[index]
      if (tab && tab.disabled) {
        wx.showToast({ title: '暂无该模块权限', icon: 'none' })
        return
      }
      wx.switchTab({ url: path })
    }
  }
})
