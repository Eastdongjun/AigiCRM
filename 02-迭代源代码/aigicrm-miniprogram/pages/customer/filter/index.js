const { requireLogin, hasPermission } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')
const { getDictData, getDepts } = require('../../../api/system')
const { getCustomerSimpleUsers } = require('../../../api/customer')
// 【D27 2026-10-04】筛选草稿 storage key/字段清单统一由组内 shared 提供(与客户列表页读取同源)
const { saveFilterDraft } = require('../shared')

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
    protectText: optionText(data.protectOptions, data.selectedProtectIndex, 'label', '全部保护状态'),
    deptText: optionText(data.deptOptions, data.selectedDeptIndex, 'name', '全部部门'),
    ownerText: optionText(data.ownerOptions, data.selectedOwnerIndex, 'name', '全部负责人'),
    provinceValue: data.province || '',
    cityValue: data.city || ''
  }
}

Page({
  data: {
    keyword: '',
    devType: '全部',
    // 【A4 2026-10-08 二轮】补齐第 4 分类「新建改建扩建大修」，与 PC 客户列表/高级筛选同口径
    devTypes: ['全部', '成熟创新', '突破开拓', '铺垫宣传', '新建改建扩建大修'],
    selectedDevTypeIndex: 0,
    industryOptions: [{ value: '', label: '全部行业' }],
    // 【对齐 PC 2026-09-21】客户状态域 = status 字段(normal/warning); '超期'属保护状态域, 移入保护选择器
    statusOptions: [{ value: '', label: '全部状态' }, { value: 'normal', label: '正常' }, { value: 'warning', label: '预警' }],
    protectOptions: [{ value: '', label: '全部保护状态' }, { value: 'active', label: '保护中' }, { value: 'expiring', label: '即将到期' }, { value: 'expired', label: '已到期' }],
    deptOptions: [{ id: '', name: '全部部门' }],
    ownerOptions: [{ id: '', name: '全部负责人' }],
    province: '',
    city: '',
    selectedIndustryIndex: 0,
    selectedStatusIndex: 0,
    selectedProtectIndex: 0,
    selectedDeptIndex: 0,
    selectedOwnerIndex: 0,
    filterView: buildFilterView({
      keyword: '',
      devType: '全部',
      industryOptions: [{ value: '', label: '全部行业' }],
      statusOptions: [{ value: '', label: '全部状态' }, { value: 'normal', label: '正常' }, { value: 'warning', label: '预警' }],
      protectOptions: [{ value: '', label: '全部保护状态' }, { value: 'active', label: '保护中' }, { value: 'expiring', label: '即将到期' }, { value: 'expired', label: '已到期' }],
      deptOptions: [{ id: '', name: '全部部门' }],
      ownerOptions: [{ id: '', name: '全部负责人' }],
      province: '',
      city: '',
      selectedIndustryIndex: 0,
      selectedStatusIndex: 0,
      selectedProtectIndex: 0,
      selectedDeptIndex: 0,
      selectedOwnerIndex: 0
    })
  },
  onLoad() {
    if (!requireLogin()) return
    if (!getApp().globalData.demoMode && !hasPermission('customer:view')) {
      // 【D28 2026-10-04】补 return：无权限分支此前缺 return，已提示无权限并返回仍继续拉筛选下拉数据
      wx.showToast({ title: '没有客户查看权限', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 500)
      return
    }
    this.loadFilterOptions()
  },
  applyState(patch, callback) {
    const next = { ...this.data, ...patch }
    this.setData({ ...patch, filterView: buildFilterView(next) }, callback)
  },
  onKeywordInput(e) { this.applyState({ keyword: e.detail.value }) },
  onProvinceInput(e) { this.applyState({ province: e.detail.value }) },
  onCityInput(e) { this.applyState({ city: e.detail.value }) },
  chooseDevType(e) {
    const selectedDevTypeIndex = Number(e.detail.value) || 0
    this.applyState({ selectedDevTypeIndex, devType: this.data.devTypes[selectedDevTypeIndex] || '全部' })
  },
  chooseIndustry(e) { this.applyState({ selectedIndustryIndex: Number(e.detail.value || 0) }) },
  chooseStatus(e) { this.applyState({ selectedStatusIndex: Number(e.detail.value || 0) }) },
  chooseProtect(e) { this.applyState({ selectedProtectIndex: Number(e.detail.value || 0) }) },
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
    const protect = this.data.protectOptions[this.data.selectedProtectIndex] || {}
    const dept = this.data.deptOptions[this.data.selectedDeptIndex] || {}
    const owner = this.data.ownerOptions[this.data.selectedOwnerIndex] || {}
    // 【D27 2026-10-04】key 与字段清单由 shared 统一(saveFilterDraft 按清单挑值落盘)，
    // 与客户列表页读取同源，防止三处字段清单漂移导致静默丢条件
    saveFilterDraft({
      keyword: this.data.keyword.trim(),
      devType: this.data.devType,
      industry: industry.value || '',
      status: status.value || '',
      protect: protect.value || '',
      province: this.data.province.trim(),
      city: this.data.city.trim(),
      deptId: dept.id || '',
      ownerId: owner.id || ''
    })
    wx.navigateBack()
  },
  reset() { this.applyState({ keyword: '', devType: '全部', selectedDevTypeIndex: 0, selectedIndustryIndex: 0, selectedStatusIndex: 0, selectedProtectIndex: 0, selectedDeptIndex: 0, selectedOwnerIndex: 0, province: '', city: '' }) },
  goBack() { wx.navigateBack() }
})
