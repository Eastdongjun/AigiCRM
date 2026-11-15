const { requireLogin } = require('../../../utils/auth')
const { loadCurrentUser } = require('../../../api/auth')
const permissionRefresh = require('../../../utils/permission-refresh')
const { dataScopeLabel } = require('../../../utils/label-maps')

const MODULE_LABELS = { customer: '客户', incentive: '激励', file: '文件', sea: '公海', approval: '审批', reminder: '提醒', statistics: '统计' }

function buildView() {
  const app = getApp()
  const gd = app.globalData || {}
  const user = gd.currentUser || {}
  const perms = (gd.permissions || []).slice().sort()
  const scopes = user.moduleScopes || {}
  let epoch = ''
  try {
    const info = wx.getStorageInfoSync()
    const key = (info.keys || []).find((k) => String(k).indexOf('permEpoch::') === 0)
    if (key) epoch = String(wx.getStorageSync(key) || '')
  } catch (e) { /* 诊断页忽略存储异常 */ }
  return {
    userName: user.realName || user.username || '当前用户',
    permissionCount: perms.length,
    permissionsText: perms.join('、') || '无',
    baseScopeText: dataScopeLabel(user.dataScope) + '（基础范围）',
    moduleRows: Object.keys(MODULE_LABELS).filter((k) => scopes[k]).map((k) => ({
      key: k, label: MODULE_LABELS[k], scopeText: dataScopeLabel(scopes[k])
    })),
    permVersion: permissionRefresh.getVersion(),
    permEpoch: epoch || '未记录'
  }
}

Page({
  data: { view: {} },
  onLoad() {
    if (!requireLogin()) return
    this.refresh()
  },
  onShow() {
    this.setData({ view: buildView() })
  },
  onPermissionChanged() {
    this.setData({ view: buildView() })
  },
  refresh() {
    if (getApp().globalData.demoMode) {
      this.setData({ view: buildView() })
      return
    }
    loadCurrentUser().then(() => this.setData({ view: buildView() })).catch(() => this.setData({ view: buildView() }))
  },
  copyPermissions() {
    const text = this.data.view.permissionsText || ''
    wx.setClipboardData({ data: text })
  }
})
