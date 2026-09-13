const { getCustomer, addCustomer, updateCustomer, getCustomerSimpleUsers, getCustomerGroupNames } = require('../../../api/customer')
const { getDepts, getDictData } = require('../../../api/system')
const { hasPermission, requireLogin, isAdmin } = require('../../../utils/auth')
const { showError } = require('../../../utils/feedback')
const { readDemoCustomers, saveDemoCustomer } = require('../../../utils/demo-customers')

function today() { return new Date().toISOString().split('T')[0] }

/** 新建默认值（与 PC 端一致：终端=否、注册日=当天）。
 *  编辑时必须用库内值覆盖（含 null）——否则"今天"会被当成用户输入提交，
 *  后端 protectCustomerDateFields 会判定"注册日被修改"，普通销售直接报错存不了。 */
function emptyForm() {
  return {
    customerName: '', invoiceName: '', industry: '', devType: '',
    hasTerminal: 0,
    regionId: null, officeId: null, ownerId: null, managerId: null,
    groupName: '', branchCompany: '', unitName: '', capacity: '',
    province: '', city: '', district: '', address: '',
    registerDate: today(), lastInvoiceDate: '',
    remark: ''
  }
}

const TERMINAL_OPTIONS = ['否', '是']
const MANUAL_GROUP_LABEL = '其他（手动输入）'

/** 演示模式/接口不可用时的兜底选项：保证下拉仍可操作（与 PC 端字典兜底同思路） */
const FALLBACK = {
  regions: [{ id: 1, deptName: '内蒙古大区' }, { id: 2, deptName: '山西大区' }],
  offices: [{ id: 11, deptName: '内蒙办', parentId: 1 }, { id: 21, deptName: '山西办', parentId: 2 }],
  users: [{ id: 1, realName: '张三' }, { id: 2, realName: '李四' }],
  industry: ['煤矿', '化工', '电力', '洗煤厂', '有色', '其他'],
  devType: ['成熟创新', '突破开拓', '铺垫宣传'],
  groups: ['陕煤集团', '国家能源集团']
}

const indexOfValue = (list, value) => (list || []).findIndex((item) => String(item) === String(value))
const indexOfId = (list, value) => (list || []).findIndex((item) => String(item && item.id) === String(value))

Page({
  data: {
    id: '', isEdit: false, loading: false, saving: false,
    form: emptyForm(),
    allOffices: [],
    regionOptions: [], officeOptions: [], userOptions: [], groupOptions: [], groupPickerOptions: [],
    industryOptions: [], devTypeOptions: [], terminalOptions: TERMINAL_OPTIONS,
    manualGroupLabel: MANUAL_GROUP_LABEL,
    industryIndex: -1, devTypeIndex: 0, terminalIndex: 0,
    regionIndex: -1, officeIndex: -1, ownerIndex: -1, managerIndex: -1, groupIndex: -1,
    region: [], deptName: '', canEditDates: true
  },

  onLoad(options) {
    if (!requireLogin()) return
    const id = options.id || ''
    if (!getApp().globalData.demoMode && !hasPermission(id ? 'customer:edit' : 'customer:add')) {
      wx.showToast({ title: '没有操作权限', icon: 'none' }); return wx.navigateBack()
    }
    this.setData({
      id,
      isEdit: !!id,
      // 编辑态注册日/最近开票日仅 admin 或 customer:date:edit 可改（与 PC canEditProtectedDates 一致）
      canEditDates: !id || getApp().globalData.demoMode || isAdmin() || hasPermission('customer:date:edit')
    })
    this.loadOptions().then(() => (id ? this.loadDetail() : this.prefillOwner()))
  },

  /** 下拉数据：部门 / 人员 / 字典 / 历史集团名（演示模式直接走兜底数据，不请求接口） */
  loadOptions() {
    if (getApp().globalData.demoMode) {
      // 演示模式没有后端：部门直接走兜底选项（applyDepts 内部回退 FALLBACK.regions/offices）
      this.applyDepts([])
      this.setData({
        userOptions: FALLBACK.users,
        industryOptions: FALLBACK.industry,
        devTypeOptions: FALLBACK.devType,
        groupOptions: FALLBACK.groups,
        groupPickerOptions: FALLBACK.groups.concat(MANUAL_GROUP_LABEL)
      })
      return Promise.resolve()
    }
    return Promise.all([
      getDepts().then((list) => this.applyDepts(list || [])).catch(() => this.applyDepts([], FALLBACK)),
      getCustomerSimpleUsers().then((list) => this.setData({ userOptions: list || [] })).catch(() => this.setData({ userOptions: FALLBACK.users })),
      getDictData('industry').then((list) => this.setData({ industryOptions: (list || []).map((d) => d.dictValue) })).catch(() => this.setData({ industryOptions: FALLBACK.industry })),
      getDictData('dev_type').then((list) => this.setData({ devTypeOptions: (list || []).map((d) => d.dictValue) })).catch(() => this.setData({ devTypeOptions: FALLBACK.devType })),
      getCustomerGroupNames().then((list) => this.setGroupOptions((list || []).filter(Boolean))).catch(() => this.setGroupOptions([]))
    ])
  },

  setGroupOptions(list) {
    const names = list || []
    this.setData({ groupOptions: names, groupPickerOptions: names.concat(MANUAL_GROUP_LABEL) })
  },

  /** 部门列表 → 大区/办事处候选；接口为空或失败时回退兜底数据（演示模式同样走这里） */
  applyDepts(list) {
    const source = (list && list.length) ? list : FALLBACK.regions.concat(FALLBACK.offices)
    const regions = source.filter((d) => d.deptType === 2)
    const offices = source.filter((d) => d.deptType === 3)
    const allOffices = offices.length ? offices : FALLBACK.offices
    this.setData({
      regionOptions: regions.length ? regions : FALLBACK.regions,
      allOffices,
      officeOptions: this.filterOffices(allOffices, this.data.form.regionId)
    })
  },

  /** 办事处只列出所选大区下的（与 PC 端 filteredOffices 同口径） */
  filterOffices(offices, regionId) {
    if (!regionId) return []
    return (offices || []).filter((o) => String(o.parentId) === String(regionId))
  },

  /** 新建时默认第一负责人=当前登录人（与 PC 一致） */
  prefillOwner() {
    const currentUser = getApp().globalData.currentUser || {}
    const ownerId = currentUser.userId || (getApp().globalData.demoMode ? FALLBACK.users[0].id : null)
    if (!ownerId) return
    this.setData({ 'form.ownerId': ownerId, ownerIndex: indexOfId(this.data.userOptions, ownerId) })
  },

  loadDetail() {
    if (getApp().globalData.demoMode) {
      const item = readDemoCustomers().find((customer) => String(customer.id) === String(this.data.id))
      if (item) return this.applyCustomer(item)
    }
    this.setData({ loading: true })
    getCustomer(this.data.id)
      .then((item) => this.applyCustomer(item))
      .catch((error) => showError(error, '客户加载失败'))
      .finally(() => this.setData({ loading: false }))
  },

  /** 编辑回填：一律以库内值为准，null 也照实落到表单（不回退"今天"等新建默认值） */
  applyCustomer(item) {
    const form = { ...this.data.form }
    Object.keys(form).forEach((key) => { if (item[key] !== undefined) form[key] = item[key] })
    const officeOptions = this.filterOffices(this.data.allOffices, form.regionId)
    this.setData({
      form,
      deptName: item.deptName || '',
      region: [form.province, form.city, form.district].filter(Boolean),
      industryIndex: indexOfValue(this.data.industryOptions, form.industry),
      devTypeIndex: indexOfValue(this.data.devTypeOptions, form.devType),
      terminalIndex: Number(form.hasTerminal) === 1 ? 1 : 0,
      regionIndex: indexOfId(this.data.regionOptions, form.regionId),
      officeOptions,
      officeIndex: indexOfId(officeOptions, form.officeId),
      ownerIndex: indexOfId(this.data.userOptions, form.ownerId),
      managerIndex: indexOfId(this.data.userOptions, form.managerId),
      groupIndex: indexOfValue(this.data.groupOptions, form.groupName)
    })
  },

  onInput(e) { this.setData({ [`form.${e.currentTarget.dataset.field}`]: e.detail.value }) },

  chooseIndustry(e) { const i = Number(e.detail.value); this.setData({ industryIndex: i, 'form.industry': this.data.industryOptions[i] || '' }) },
  chooseDevType(e) { const i = Number(e.detail.value); this.setData({ devTypeIndex: i, 'form.devType': this.data.devTypeOptions[i] || '' }) },
  chooseTerminal(e) { const i = Number(e.detail.value); this.setData({ terminalIndex: i, 'form.hasTerminal': i }) },

  /** 换大区后办事处必须重选（与 PC onRegionChange 一致，避免跨大区错配） */
  chooseRegion(e) {
    const index = Number(e.detail.value)
    const region = this.data.regionOptions[index] || {}
    this.setData({
      regionIndex: index,
      'form.regionId': region.id || null,
      'form.officeId': null,
      officeIndex: -1,
      officeOptions: this.filterOffices(this.data.allOffices, region.id)
    })
  },
  chooseOffice(e) { const i = Number(e.detail.value); const office = this.data.officeOptions[i] || {}; this.setData({ officeIndex: i, 'form.officeId': office.id || null }) },
  chooseOwner(e) { const i = Number(e.detail.value); const user = this.data.userOptions[i] || {}; this.setData({ ownerIndex: i, 'form.ownerId': user.id || null }) },
  chooseManager(e) { const i = Number(e.detail.value); const user = this.data.userOptions[i] || {}; this.setData({ managerIndex: i, 'form.managerId': user.id || null }) },

  /** 集团：从历史集团名里选，找不到可手动输入新名称（与 PC allow-create 同能力） */
  chooseGroup(e) {
    const i = Number(e.detail.value)
    if (i === this.data.groupOptions.length) {
      return wx.showModal({
        title: '输入集团名称',
        editable: true,
        placeholderText: '例如：陕煤集团',
        success: (res) => {
          const name = String(res.content || '').trim()
          if (res.confirm && name) this.setData({ groupIndex: -1, 'form.groupName': name })
        }
      })
    }
    this.setData({ groupIndex: i, 'form.groupName': this.data.groupOptions[i] || '' })
  },

  /** 省/市/区：用微信原生 region picker，落库仍是三个字符串（与 PC 级联同口径） */
  chooseArea(e) {
    const value = e.detail.value || []
    this.setData({
      region: value,
      'form.province': value[0] || '',
      'form.city': value[1] || '',
      'form.district': value[2] || ''
    })
  },
  chooseDate(e) { this.setData({ [`form.${e.currentTarget.dataset.field}`]: e.detail.value }) },

  openContacts() {
    if (!this.data.id) return wx.showToast({ title: '请先保存客户', icon: 'none' })
    wx.navigateTo({ url: `/pages/customer/contact/index?id=${this.data.id}` })
  },
  openIndicator() {
    if (!this.data.id) return wx.showToast({ title: '请先保存客户', icon: 'none' })
    wx.navigateTo({ url: `/pages/customer/indicator/index?id=${this.data.id}` })
  },

  missingRequired() {
    const f = this.data.form
    if (!f.industry) return '请选择行业'
    if (!f.devType) return '请选择开发类型'
    if (!f.regionId) return '请选择所属大区'
    if (!f.officeId) return '请选择所属办事处'
    if (!f.ownerId) return '请选择第一负责人'
    return ''
  },

  submit() {
    if (!this.data.form.customerName.trim()) return wx.showToast({ title: '请输入客户名称', icon: 'none' })
    const missing = this.missingRequired()
    if (missing) return wx.showToast({ title: missing, icon: 'none' })
    if (this.data.loading || this.data.saving) return
    this.setData({ saving: true })

    // 日期空串统一转 null；新建时空串归一 null（库里不写空串），
    // 编辑时保留空串——后端 updateById 跳过 null 字段，空串才是"清空字段"的唯一表达。
    const form = { ...this.data.form }
    form.registerDate = form.registerDate || null
    form.lastInvoiceDate = form.lastInvoiceDate || null
    if (!this.data.isEdit) {
      Object.keys(form).forEach((key) => {
        if (typeof form[key] === 'string' && form[key].trim() === '') form[key] = null
      })
    }

    const action = getApp().globalData.demoMode
      ? Promise.resolve(saveDemoCustomer(form, this.data.isEdit ? this.data.id : ''))
      : (this.data.isEdit ? updateCustomer(this.data.id, form) : addCustomer(form))
    action
      .then(() => {
        wx.showToast({ title: this.data.isEdit ? '保存成功' : '新增成功', icon: 'success' })
        setTimeout(() => wx.navigateBack(), 500)
      })
      .catch((error) => showError(error, '保存失败，请检查权限或字段'))
      .finally(() => this.setData({ saving: false }))
  },

  cancel() { wx.navigateBack() }
})
