const { requireLogin, hasPermission } = require('../../utils/auth')
const { whenPermissionsReady } = require('../../utils/permission-ready')
const { showError } = require('../../utils/feedback')
const { getTaskList, getTaskStatistics, completeTask, cancelTask, transferTask } = require('../../api/task')
const { getCustomerSimpleUsers } = require('../../api/customer')

const STATUS_TABS = [
  { key: '', label: '全部' },
  { key: 'pending', label: '待办' },
  { key: 'completed', label: '已完成' },
  { key: 'cancelled', label: '已取消' }
]

Page({
  data: {
    tabs: STATUS_TABS,
    status: '',
    overdueOnly: false,
    tasks: [],
    total: 0,
    page: 1,
    pageSize: 10,
    loading: false,
    stats: { pending: 0, todayDue: 0, overdue: 0, completedThisMonth: 0 },
    canManage: false,
    actionVisible: false,
    actionType: '',
    actionTask: null,
    actionTitle: '',
    actionPlaceholder: '',
    actionText: '',
    submitting: false,
    transferVisible: false,
    assignees: [],
    assigneeKeyword: '',
    filteredAssignees: []
  },

  onLoad() {
    if (!requireLogin()) return
    return whenPermissionsReady(() => {
      if (!hasPermission('task:view')) {
        wx.showToast({ title: '没有任务查看权限', icon: 'none' })
        setTimeout(() => wx.navigateBack(), 300)
        return
      }
      this.setData({ canManage: hasPermission('task:manage') })
      this.loadStats()
      this.loadList(true)
      this._loadedOnce = true
    })
  },

  onShow() {
    return whenPermissionsReady(() => {
      if (!getApp().globalData.demoMode && !hasPermission('task:view') && this.data.tasks.length) {
        wx.showToast({ title: '没有任务查看权限', icon: 'none' })
        setTimeout(() => wx.navigateBack(), 300)
        return
      }
      this.setData({ canManage: hasPermission('task:manage') })
    })
  },

  onPermissionChanged() {
    // 【BUG-MP-TABBAR-PERM-RACE-20261127 第二批】冷启动回填也会触发本回调: 页面未初始化时交给 onLoad 统一取数, 避免双拉。
    if (!this._loadedOnce) return
    if (!hasPermission('task:view')) {
      wx.showToast({ title: '没有任务查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 300)
      return
    }
    this.setData({ canManage: hasPermission('task:manage') })
    this.loadStats()
    this.loadList(true)
  },

  onPullDownRefresh() {
    Promise.all([this.loadStats(), this.loadList(true)]).finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (this.data.loading || this.data.tasks.length >= this.data.total) return
    this.loadList(false)
  },

  onTabTap(e) {
    const status = e.currentTarget.dataset.key || ''
    if (status === this.data.status) return
    this.setData({ status })
    this.loadList(true)
  },

  toggleOverdue() {
    this.setData({ overdueOnly: !this.data.overdueOnly })
    this.loadList(true)
  },

  loadStats() {
    return getTaskStatistics().then((res) => {
      this.setData({ stats: res || {} })
    }).catch((err) => {
      showError(err, '任务统计加载失败')
    })
  },

  loadList(reset) {
    const page = reset ? 1 : this.data.page
    const params = { current: page, size: this.data.pageSize }
    if (this.data.status) params.status = this.data.status
    if (this.data.overdueOnly) params.overdue = true
    this.setData({ loading: true })
    return getTaskList(params).then((res) => {
      const data = res || {}
      const records = data.records || []
      this.setData({
        tasks: reset ? records : this.data.tasks.concat(records),
        total: Number(data.total) || 0,
        page: page + 1,
        loading: false
      })
    }).catch((err) => {
      this.setData({ loading: false })
      showError(err, '任务列表加载失败')
    })
  },

  openComplete(e) {
    if (!this.data.canManage) return
    const task = this.data.tasks[Number(e.currentTarget.dataset.index)]
    if (!task) return
    this.setData({
      actionVisible: true, actionType: 'complete', actionTask: task,
      actionTitle: '完成任务', actionPlaceholder: '完成结果（必填，不是简单勾掉）', actionText: ''
    })
  },

  openCancel(e) {
    if (!this.data.canManage) return
    const task = this.data.tasks[Number(e.currentTarget.dataset.index)]
    if (!task) return
    this.setData({
      actionVisible: true, actionType: 'cancel', actionTask: task,
      actionTitle: '取消任务', actionPlaceholder: '取消原因（必填）', actionText: ''
    })
  },

  onActionInput(e) { this.setData({ actionText: e.detail.value }) },

  closeAction() {
    if (this.data.submitting) return
    this.setData({ actionVisible: false, actionTask: null, actionText: '' })
  },

  submitAction() {
    const text = (this.data.actionText || '').trim()
    const task = this.data.actionTask
    if (!task || !text) {
      wx.showToast({ title: this.data.actionType === 'complete' ? '请填写完成结果' : '请填写取消原因', icon: 'none' })
      return
    }
    if (this.data.submitting) return
    this.setData({ submitting: true })
    const action = this.data.actionType === 'complete'
      ? completeTask(task.id, text)
      : cancelTask(task.id, text)
    action.then(() => {
      wx.showToast({ title: this.data.actionType === 'complete' ? '已完成' : '已取消', icon: 'success' })
      this.setData({ actionVisible: false, actionTask: null, actionText: '', submitting: false })
      this.loadStats()
      this.loadList(true)
    }).catch((err) => {
      this.setData({ submitting: false })
      showError(err, '操作失败')
    })
  },

  openTransfer(e) {
    if (!this.data.canManage) return
    const task = this.data.tasks[Number(e.currentTarget.dataset.index)]
    if (!task) return
    getCustomerSimpleUsers().then((users) => {
      const list = (Array.isArray(users) ? users : []).filter((u) => u && u.id != null && String(u.id) !== String(task.assigneeId))
      this.setData({
        actionTask: task, transferVisible: true, assignees: list,
        assigneeKeyword: '', filteredAssignees: list
      })
    }).catch((err) => showError(err, '责任人列表加载失败'))
  },

  onAssigneeInput(e) {
    const keyword = (e.detail.value || '').trim()
    const list = keyword
      ? this.data.assignees.filter((u) => String(u.realName || u.username || '').indexOf(keyword) >= 0)
      : this.data.assignees
    this.setData({ assigneeKeyword: keyword, filteredAssignees: list })
  },

  closeTransfer() {
    if (this.data.submitting) return
    this.setData({ transferVisible: false, actionTask: null, assignees: [], filteredAssignees: [] })
  },

  selectAssignee(e) {
    const task = this.data.actionTask
    const assignee = this.data.filteredAssignees[Number(e.currentTarget.dataset.index)]
    if (!task || !assignee || this.data.submitting) return
    this.setData({ submitting: true })
    transferTask(task.id, assignee.id).then(() => {
      wx.showToast({ title: '已转交', icon: 'success' })
      this.setData({ transferVisible: false, actionTask: null, filteredAssignees: [], submitting: false })
      this.loadStats()
      this.loadList(true)
    }).catch((err) => {
      this.setData({ submitting: false })
      showError(err, '转交失败')
    })
  },

  noop() {}
})
