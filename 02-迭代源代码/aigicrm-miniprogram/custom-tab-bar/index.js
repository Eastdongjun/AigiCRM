// 【2026-10-24 自定义 tabBar】用户令"底部图标+下面区域做好看": 原生 tabBar 改不了
// 毛玻璃/双态图标, 切 custom 模式(app.json tabBar.custom: true)。
// 路由与选中态约定:
//   · tab 页注册以 app.json tabBar.list 为准(顺序同本文件 TABS/PATHS, 勿单边改);
//   · 每个 tab 页 onShow 里 getTabBar().setData({ selected: N }) 同步选中态
//     (custom-tab-bar 组件每页独立实例, 不同步会出现选中态滞留);
//   · 点击统一走 wx.switchTab(tabBar 页 navigateTo 会被微信静默拒绝)。
const TABS = [
  { key: 'home', title: '首页' },
  { key: 'service', title: '四项服务' },
  { key: 'files', title: '文件' },
  { key: 'apps', title: '应用' },
  { key: 'profile', title: '我的' }
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
  methods: {
    onTap(e) {
      const index = Number(e.currentTarget.dataset.index)
      const path = PATHS[index]
      if (!path || index === this.data.selected) return
      wx.switchTab({ url: path })
    }
  }
})
