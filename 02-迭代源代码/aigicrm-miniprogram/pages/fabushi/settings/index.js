const { hasPermission, requireLogin } = require('../../../utils/auth')
const { getFabushiSettings, saveFabushiSettings } = require('../../../api/fabushi')
const { showError } = require('../../../utils/feedback')

const FREQS = [
  { value: 'daily', label: '每天' },
  { value: 'weekday', label: '工作日' },
  { value: 'weekend', label: '休息日' }
]

Page({
  data: {
    freqs: FREQS,
    freqIndex: 0,
    reminderEnabled: false,
    remindTime: '20:00',
    soundEnabled: true,
    submitting: false
  },
  onLoad() {
    if (!requireLogin()) return
    if (!hasPermission('fabushi:view')) {
      wx.navigateBack()
      return
    }
    getFabushiSettings().then((res) => {
      const s = res || {}
      const idx = Math.max(0, FREQS.findIndex((f) => f.value === (s.freqCode || 'daily')))
      this.setData({
        reminderEnabled: Number(s.reminderEnabled) === 1,
        soundEnabled: Number(s.soundEnabled) !== 0,
        remindTime: s.remindTime || '20:00',
        freqIndex: idx
      })
    }).catch((err) => showError(err, '设置加载失败'))
  },
  onReminder(e) { this.setData({ reminderEnabled: !!e.detail.value }) },
  onSound(e) { this.setData({ soundEnabled: !!e.detail.value }) },
  onFreq(e) { this.setData({ freqIndex: Number(e.detail.value) }) },
  onTime(e) { this.setData({ remindTime: e.detail.value }) },
  save() {
    if (this.data.submitting) return
    this.setData({ submitting: true })
    saveFabushiSettings({
      reminderEnabled: this.data.reminderEnabled ? 1 : 0,
      freqCode: FREQS[this.data.freqIndex].value,
      remindTime: this.data.remindTime,
      soundEnabled: this.data.soundEnabled ? 1 : 0
    }).then(() => {
      wx.showToast({ title: '已保存', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 500)
    }).catch((err) => {
      this.setData({ submitting: false })
      showError(err, '保存失败')
    })
  }
})
