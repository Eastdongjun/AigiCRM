const { requireLogin, hasPermission } = require('../../../../utils/auth')
const { whenPermissionsReady } = require('../../../../utils/permission-ready')
// 【mp-0 2026-10-17】页面参数统一安全解码一次（导航侧 encodeURIComponent 一次），
// 避免真机 %XX 原样展示，也避免对已解码文本二次解码。
const { safeDecodeParam } = require('../../../../utils/query')

function textOrDash(value) {
  return value === undefined || value === null || value === '' ? '-' : String(value)
}

function buildContactDetailView(data = {}) {
  const name = data.name || ''
  return {
    nameText: name || '未命名联系人',
    nameInitial: name ? name.slice(0, 1) : '联',
    positionText: textOrDash(data.position),
    mobileText: textOrDash(data.mobile),
    remarkText: textOrDash(data.remark),
    mobileValue: data.mobile || ''
  }
}

Page({
  data: {
    name: '',
    position: '',
    mobile: '',
    remark: '',
    contactDetailView: buildContactDetailView({})
  },
  applyState(patch) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, contactDetailView: buildContactDetailView(next) })
  },
  onPermissionChanged() {
    if (getApp().globalData.demoMode) return
    // 【BUG-MP-TABBAR-PERM-RACE-20261127 第二批】冷启动回填也会触发本回调: 页面未初始化时跳过, 避免误踢/双拉。
    if (!this._loadedOnce) return
    if (!hasPermission('contact:view')) {
      wx.showToast({ title: '没有操作权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 300)
    }
  },

  onLoad(options) {
    if (!requireLogin()) return
    return whenPermissionsReady(() => {
      if (!getApp().globalData.demoMode && (!hasPermission('customer:view') || !hasPermission('contact:view'))) {
        wx.showToast({ title: '没有联系人查看权限', icon: 'none' })
        setTimeout(() => wx.navigateBack(), 500)
        return
      }
      const name = safeDecodeParam(options.name || '')
      // 备注是自由文本，最可能包含 % 等字符：原生解码一次 + 失败回退原值。
      // customer-form 契约锚点钉在这条原生调用上，故此处不改写为通用 helper。
      const remark = (() => {
        try { return decodeURIComponent(options.remark || '') } catch (_) { return options.remark || '' }
      })()
      this.applyState({ name, position: safeDecodeParam(options.position || ''), mobile: safeDecodeParam(options.mobile || ''), remark })
      this._loadedOnce = true
    })
  },
  call() {
    const phone = this.data.contactDetailView.mobileValue
    if (phone && !phone.includes('*')) wx.makePhoneCall({ phoneNumber: phone })
    else wx.showToast({ title: '手机号已脱敏', icon: 'none' })
  },
  goBack() { wx.navigateBack() }
})
