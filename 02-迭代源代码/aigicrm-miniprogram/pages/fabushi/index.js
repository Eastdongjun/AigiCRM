const { hasPermission, requireLogin } = require('../../../utils/auth')
const { getFabushiPersonalStats } = require('../../../api/fabushi')

// 【法布施一期 2026-11-15】MP 工作台: 书目/发布入口 + 个人统计卡
Page({
  data: {
    canCreate: false,
    personal: {},
    loading: true
  },

  onLoad() {
    if (!requireLogin()) return
    this.setData({
      canCreate: hasPermission('fabushi:create'),
      loading: false
    })
    if (hasPermission('fabushi:view')) {
      this.loadPersonal()
    }
  },

  onShow() {
    // 权限可能已变(权限中心勾选即生效), 回前台重判
    this.setData({ canCreate: hasPermission('fabushi:create') })
  },

  loadPersonal() {
    getFabushiPersonalStats().then((res) => {
      this.setData({ personal: (res && res.data) || {} })
    })
  },

  goBook() {
    wx.navigateTo({ url: '/pages/fabushi/book/index' })
  },

  goUpload() {
    wx.navigateTo({ url: '/pages/fabushi/upload/index' })
  }
})
