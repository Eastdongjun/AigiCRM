// 【F4a 提醒触达·方案a 在线自检 2026-11-19 拍板②】设置原本是数据孤岛(开关/频率/时间全存
// 永不触达)——改为打开小程序/进入法布施页时自检:
//   reminderEnabled ∧ freqCode 匹配当天(daily 恒真 / workday 周一~五 / weekend 周六日, 设备本地日)
//   ∧ now ≥ remindTime ∧ 未读今日 → showModal + tabBar 法布施红点
// 防重复打扰: storage 记当日已提醒键; 已读则红点清零。离线推送(方案b 订阅消息)待资质另批。
const { getFabushiSettings, getFabushiReadToday } = require('../api/fabushi')

function freqMatchesToday(freqCode, now) {
  const day = now.getDay() // 0=周日
  const code = freqCode || 'daily'
  if (code === 'workday') return day >= 1 && day <= 5
  if (code === 'weekend') return day === 0 || day === 6
  return true // daily/未知值兜底: 天天可提醒
}

function timeReached(remindTime, now) {
  if (!remindTime) return true // 未设时间视为全天可提醒
  const parts = String(remindTime).split(':').map(Number)
  const h = parts[0]
  const m = parts[1]
  if (!Number.isFinite(h) || !Number.isFinite(m)) return true
  return now.getHours() * 60 + now.getMinutes() >= h * 60 + m
}

function localDateKey(now) {
  const p = (n) => (n < 10 ? '0' + n : '' + n)
  return now.getFullYear() + '-' + p(now.getMonth() + 1) + '-' + p(now.getDate())
}

/** 红点写入 globalData 并刷新当前页 tabBar(custom-tab-bar 每页独立实例)。 */
function setUnreadDot(unread) {
  const app = typeof getApp === 'function' ? getApp() : null
  if (!app) return
  app.globalData.fabushiUnread = Boolean(unread)
  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
  const cur = pages[pages.length - 1]
  if (cur && typeof cur.getTabBar === 'function' && cur.getTabBar()) {
    cur.getTabBar().refreshTabs()
  }
}

/**
 * 自检入口(app onShow 与 fabushi/index onShow 调用)。
 * @returns Promise<boolean> 是否弹了提醒(调试/测试用; 链路失败静默 false)
 */
function checkAndRemind(options) {
  const opts = options || {}
  const app = typeof getApp === 'function' ? getApp() : null
  if (!app || app.globalData.demoMode) return Promise.resolve(false)
  return getFabushiSettings().then((s) => {
    const d = s || {}
    if (Number(d.reminderEnabled) !== 1) return false // settings 存 1/0
    const now = new Date()
    if (!freqMatchesToday(d.freqCode, now)) return false
    if (!timeReached(d.remindTime, now)) return false
    return getFabushiReadToday().then((r) => {
      const readToday = Boolean(r && r.readToday)
      setUnreadDot(!readToday)
      if (readToday) return false
      const key = 'fabushi_reminded_at'
      let last = ''
      try { last = wx.getStorageSync(key) || '' } catch (e) { /* storage 不可用不阻断 */ }
      if (last === localDateKey(now)) return false
      try { wx.setStorageSync(key, localDateKey(now)) } catch (e) { /* 同上 */ }
      if (opts.showModal !== false) {
        wx.showModal({
          title: '法布施提醒',
          content: '今日还未阅读法布施内容，去读一读？',
          confirmText: '去阅读',
          cancelText: '暂不',
          success: (res) => {
            if (res.confirm) wx.switchTab({ url: '/pages/fabushi/index' })
          }
        })
      }
      return true
    })
  }).catch(() => false)
}

module.exports = { checkAndRemind, setUnreadDot, freqMatchesToday, timeReached, localDateKey }
