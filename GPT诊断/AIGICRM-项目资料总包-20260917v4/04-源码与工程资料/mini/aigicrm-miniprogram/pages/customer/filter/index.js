const { requireLogin, hasPermission } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')
const { getDictData, getDepts } = require('../../../api/system')
const { getCustomerSimpleUsers } = require('../../../api/customer')

function flattenDepts(nodes = [], depth = 0) {
  const rows = []
  for (const node of Array.isArray(nodes) ? nodes : []) {
    rows.push({ id: node.id, name: `${'　'.repeat(depth)}${node.deptName || node.name || '未命名部门'}` })
    rows.push(...flattenDepts(node.children || [], depth + 1))
  }
  return rows
}

function optionText(options, index, key, fallback) {
  const item = (Array.isArray(options) ? options : [])[Number(index) || 0] || {}
  return item[key] || fallback
}

function buildFilterView(data) {
  return {
    keywordValue: data.keyword || '',
    devTypeText: data.devType || '全部',
    industryText: optionText(data.industryOptions, data.selectedIndustryIndex, 'label', '全部行业'),
    statusText: optionText(data.statusOptions, data.selectedStatusIndex, 'label', '全部状态'),
    deptText: optionText(data.deptOptions, data.selectedDeptIndex, 'name', '全部部门'),
    ownerText: optionText(data.ownerOptions, data.selectedOwnerIndex, 'name', '全部负责人')
  }
}

Page({
  data: {
    keyword: '',
    devType: '全部',
    devTypes: ['全部', '成熟创新', '突破开拓', '铺垫宣传'],
    selectedDevTypeIndex: 0,
    industryOptions: [{ value: '', label: '全部行业' }],
    statusOptions: [{ value: '', label: '全部状态' }, { value: '正常', label: '正常' }, { value: '保护期', label: '保护期' }, { value: '超期', label: '超期' }],
    deptOptions: [{ id: '', name: '全部部门' }],
    ownerOptions: [{ id: '', name: '全部负责人' }],
    selectedIndustryIndex: 0,
    selectedStatusIndex: 0,
    selectedDeptIndex: 0,
    selectedOwnerIndex: 0,
    filterView: buildFilterView({
      keyword: '',
      devType: '全部',
      industryOptions: [{ value: '', label: '全部行业' }],
      statusOptions: [{ value: '', label: '全部状态' }, { value: '正常', label: '正常' }, { value: '保护期', label: '保护期' }, { value: '超期', label: '超期' }],
      deptOptions: [{ id: '', name: '全部部门' }],
      ownerOptions: [{ id: '', name: '全部负责人' }],
      selectedIndustryIndex: 0,
      selectedStatusIndex: 0,
      selectedDeptIndex: 0,
      selectedOwnerIndex: 0
    })
  },
  onLoad() {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('customer:view')) {
      wx.showToast({ title: '没有客户查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
    }
    this.loadFilterOptions()
  },
  applyState(patch, callback) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, filterView: buildFilterView(next) }, callback)
  },
  onKeywordInput(e) { this.applyState({ keyword: e.detail.value }) },
  chooseDevType(e) {
    const selectedDevTypeIndex = Number(e.detail.value) || 0
    this.applyState({ selectedDevTypeIndex, devType: this.data.devTypes[selectedDevTypeIndex] || '全部' })
  },
  chooseIndustry(e) { this.applyState({ selectedIndustryIndex: Number(e.detail.value || 0) }) },
  chooseStatus(e) { this.applyState({ selectedStatusIndex: Number(e.detail.value || 0) }) },
  chooseDept(e) { this.applyState({ selectedDeptIndex: Number(e.detail.value || 0) }) },
  chooseOwner(e) { this.applyState({ selectedOwnerIndex: Number(e.detail.value || 0) }) },
  loadFilterOptions() {
    if (getApp().globalData.demoMode) return
    Promise.all([
      getDictData('industry').catch((error) => {
        showError(error, '行业筛选加载失败')
        return []
      }),
      getDepts().catch((error) => {
        showError(error, '部门筛选加载失败')
        return []
      }),
      getCustomerSimpleUsers().catch((error) => {
        showError(error, '负责人筛选加载失败')
        return []
      })
    ]).then(([industries, depts, users]) => {
      const industryRows = Array.isArray(industries) ? industries : (industries && (industries.data || industries.records || industries)) || []
      const deptRows = Array.isArray(depts) ? depts : (depts && (depts.data || depts.records || depts)) || []
      const userRows = Array.isArray(users) ? users : []
      this.applyState({
        industryOptions: [{ value: '', label: '全部行业' }, ...industryRows.map((item) => ({ value: item.dictValue || item.value || item.label, label: item.dictLabel || item.label || item.dictValue || item.value }))],
        deptOptions: [{ id: '', name: '全部部门' }, ...flattenDepts(deptRows)],
        ownerOptions: [{ id: '', name: '全部负责人' }, ...userRows.map((user) => ({ id: user.id, name: user.realName || user.username || `用户${user.id}` }))]
      })
    })
  },
  apply() {
    const industry = this.data.industryOptions[this.data.selectedIndustryIndex] || {}
    const status = this.data.statusOptions[this.data.selectedStatusIndex] || {}
    const dept = this.data.deptOptions[this.data.selectedDeptIndex] || {}
    const owner = this.data.ownerOptions[this.data.selectedOwnerIndex] || {}
    wx.setStorageSync('customerFilterDraft', {
      keyword: this.data.keyword.trim(),
      devType: this.data.devType,
      industry: industry.value || '',
      status: status.value || '',
      deptId: dept.id || '',
      ownerId: owner.id || ''
    })
    wx.navigateBack()
  },
  reset() { this.applyState({ keyword: '', devType: '全部', selectedDevTypeIndex: 0, selectedIndustryIndex: 0, selectedStatusIndex: 0, selectedDeptIndex: 0, selectedOwnerIndex: 0 }) },
  goBack() { wx.navigateBack() }
})
