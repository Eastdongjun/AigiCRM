const { hasPermission, requireLogin } = require('../../utils/auth')
const { getFabushiPersonalStats } = require('../../api/fabushi')
const { showError } = require('../../utils/feedback')

// 【法布施一期 2026-11-15】MP 工作台: 书目/发布入口 + 个人统计卡
Page({
  data: {
    canCreate: false,
    personal: {},
    loading: true
  },

  onPermissionChanged() {
    if (getApp().globalData.demoMode) return
    if (!hasPermission('fabushi:view')) {
      wx.showToast({ title: '没有法布施查看权限', icon: 'none' })
      setTimeout(() => wx.switchTab({ url: '/pages/home/index' }), 300)
      return
    }
    this.loadPersonal(); this.setData({ canCreate: hasPermission('fabushi:create') })
  },

  onLoad() {
    if (!requireLogin()) return
    this.setData({
      canCreate: hasPermission('fabushi:create'),
      loading: false
    })
  },

  onShow() {
    // 【2026-11-16 tabBar 调整】法布施成为 tab 页：同步选中态；权限可能已变，回前台重判
    if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ selected: 2 })
    if (getApp().globalData.demoMode) { this.setData({ canCreate: true }); return }
    if (!hasPermission('fabushi:view')) {
      wx.showToast({ title: '没有法布施查看权限', icon: 'none' })
      setTimeout(() => wx.switchTab({ url: '/pages/home/index' }), 300)
      return
    }
    this.setData({ canCreate: hasPermission('fabushi:create') })
    this.loadPersonal()
  },

  loadPersonal() {
    getFabushiPersonalStats().then((res) => {
      this.setData({ personal: res || {} })
    }).catch((err) => {
      showError(err, '个人统计加载失败')
    })
  },

  goFeed() { wx.navigateTo({ url: '/pages/fabushi/feed/index' }) },

  goBook() {
    wx.navigateTo({ url: '/pages/fabushi/book/index' })
  },

  goUpload() {
    wx.navigateTo({ url: '/pages/fabushi/upload/index' })
  }
})
