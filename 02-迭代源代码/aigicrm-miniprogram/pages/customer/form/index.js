const { getCustomer, saveCustomerWithIndicator, updateCustomerWithIndicator, checkCustomerName, getCustomerSimpleUsers, getCustomerGroupNames, getCustomerIndicator } = require('../../../api/customer')
const { getDepts, getDictData } = require('../../../api/system')
const { hasPermission, requireLogin, isAdmin } = require('../../../utils/auth')
const { showError, showErrorWithRetry } = require('../../../utils/feedback')
// 【E10】表单草稿接口自 api/system 迁至 api/draft(D38 域归位收尾), 引用随迁
const { getFormDraft, saveFormDraft, deleteFormDraft } = require('../../../api/draft')
const { readDemoCustomers, saveDemoCustomer } = require('../../../utils/demo-customers')
// 【D24/D25 2026-10-04】五项指标配置/求和/视图、核心阈值提示、返回兜底统一引用组内共享模块
const { INDICATOR_FIELDS, sumIndicatorScore, buildIndicatorItems, CORE_THRESHOLD_TIP, navigateBackWithFallback } = require('../shared')

// 【PR-05 残余 20260921】提交幂等键:同一次提交(含重试/重复发包)共用一个键,成功后换新;
// 小程序无 crypto.randomUUID,用时间戳+随机串(键只需唯一,服务端按 用户+键 作用域隔离)
let createSubmitKey = null
function newSubmitKey() {
  return 'mp-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 12)
}

/** 【L7 2026-10-04】本地时区当天。此前用 toISOString()（UTC），东八区 0:00-8:00 间
 *  会得到"昨天"，新建客户默认注册日偏一天 → 保护期起算错位。
 *  契约不变：仅影响新建默认值；编辑回填仍必须用库内值（见 emptyForm 注释）。 */
function today() {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

/** 新建默认值（与 PC 端一致：终端=否、注册日=当天）。
 *  编辑时必须用库内值覆盖（含 null）——否则"今天"会被当成用户输入提交，
 *  后端 protectCustomerDateFields 会判定"注册日被修改"，普通销售直接报错存不了。 */
function emptyForm() {
  return {
    customerName: '', alias: '', invoiceName: '', industry: '', devType: '',
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
// 【D24 2026-10-04】INDICATOR_FIELDS/INDICATOR_ITEMS 移至组内 shared.js 单一出处(与指标维护页共用)

/** 演示模式专用候选（仅 demoMode 分支使用）。
 * 【PR-04 2026-09-19】真实环境接口失败/空返回一律"报错 + 空选项"，
 * 禁止回退演示身份（张三/李四）与演示组织，防止示例 ID 提交进真实库。 */
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
const emptyIndicatorForm = () => INDICATOR_FIELDS.reduce((result, field) => ({ ...result, [field]: 0 }), {})
// 【D24 2026-10-04】sumIndicatorScore/buildIndicatorItems 移至组内 shared.js(与指标维护页共用同一份)
function pickerView(selected, text, placeholder) {
  return {
    text: selected ? text : placeholder,
    className: selected ? 'picker-value' : 'picker-value placeholder'
  }
}

function buildFormView(data) {
  const form = data.form || {}
  const region = data.regionOptions[data.regionIndex] || {}
  const office = data.officeOptions[data.officeIndex] || {}
  const owner = data.userOptions[data.ownerIndex] || {}
  const manager = data.userOptions[data.managerIndex] || {}
  return {
    title: data.isEdit ? '编辑客户' : '新增客户',
    submitLoading: Boolean(data.saving),
    submitText: data.saving ? '保存中...' : (data.isEdit ? '保存修改' : '新增客户'),
    cancelText: data.isEdit ? '取消编辑' : '取消新增',
    cancelDisabled: Boolean(data.saving),
    duplicateTip: `此客户已在 ${(data.dupInfo && data.dupInfo.createdAt) || '未知时间'} 由 ${(data.dupInfo && data.dupInfo.createdBy) || '他人'} 创建，请确认是否重复`,
    groupPicker: pickerView(Boolean(form.groupName), form.groupName, '选择历史集团名或手动输入'),
    industryPicker: pickerView(Boolean(form.industry), form.industry, '请选择行业'),
    devTypePicker: pickerView(Boolean(form.devType), form.devType, '请选择开发类型'),
    terminalText: data.terminalOptions[data.terminalIndex] || TERMINAL_OPTIONS[0],
    regionPicker: pickerView(Boolean(form.regionId), region.deptName || '', '请选择大区'),
    officePicker: pickerView(Boolean(form.officeId), office.deptName || '', form.regionId ? '请选择办事处' : '请先选择大区'),
    ownerPicker: pickerView(Boolean(form.ownerId), owner.realName || '', '请选择负责人'),
    managerPicker: pickerView(Boolean(form.managerId), manager.realName || '', '选填'),
    deptText: data.deptName || '自动跟随第一负责人所在部门',
    areaPicker: pickerView(Boolean(form.province), `${form.province || ''}${form.city || ''}${form.district || ''}`, '请选择省/市/区'),
    registerDatePicker: pickerView(Boolean(form.registerDate), form.registerDate, '选择日期'),
    lastInvoiceDatePicker: pickerView(Boolean(form.lastInvoiceDate), form.lastInvoiceDate, '尚无开票可留空'),
    dateTip: `保护期按「注册日与最近开票日中较晚者 + 360 天」计算${data.canEditDates ? '' : '；注册日仅总监或行政可改'}`
  }
}

Page({
  data: {
    draftVersion: 0, // 【议题四】草稿乐观锁版本
    id: '', isEdit: false, loading: false, saving: false,
    form: emptyForm(),
    allOffices: [],
    regionOptions: [], officeOptions: [], userOptions: [], groupOptions: [], groupPickerOptions: [],
    industryOptions: [], devTypeOptions: [], terminalOptions: TERMINAL_OPTIONS,
    indicatorForm: emptyIndicatorForm(), indicatorItems: buildIndicatorItems(emptyIndicatorForm()), indicatorLoaded: false, indicatorTotal: 0,
    manualGroupLabel: MANUAL_GROUP_LABEL,
    // 【D25 2026-10-04】核心客户门槛提示由 shared 常量拼装，wxml 不再硬编码 100 万/12 分
    coreThresholdTip: CORE_THRESHOLD_TIP,
    industryIndex: -1, devTypeIndex: 0, terminalIndex: 0,
    regionIndex: -1, officeIndex: -1, ownerIndex: -1, managerIndex: -1, groupIndex: -1,
    region: [], deptName: '', customerStatus: '', isPoolCustomer: false, originalOwnerId: null, canEditDates: true,
    checkingName: false,
    dupInfo: { exists: false, createdBy: '', createdAt: '' },
    formView: buildFormView({
      form: emptyForm(),
      isEdit: false,
      terminalOptions: TERMINAL_OPTIONS,
      terminalIndex: 0,
      regionOptions: [],
      officeOptions: [],
      userOptions: [],
      regionIndex: -1,
      officeIndex: -1,
      ownerIndex: -1,
      managerIndex: -1,
      deptName: '',
      dupInfo: { exists: false, createdBy: '', createdAt: '' },
      canEditDates: true
    })
  },

  refreshFormView() {
    this.setData({ formView: buildFormView(this.data) })
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
    }, () => this.refreshFormView())
    // 【A4 三轮 2026-10-09】导航栏标题与 PC 对齐（新增客户/编辑客户）：原固定「客户表单」，PC 无此概念
    wx.setNavigationBarTitle({ title: id ? '编辑客户' : '新增客户' })
    // 【M13 2026-10-04】记录选项加载 Promise：草稿恢复确认时挂在它后面，
    // 保证 applyCustomer 重算 picker 索引时下拉候选已就绪(选项未加载时索引恒为 -1)
    this.optionsPromise = this.loadOptions().then(() => (id ? this.loadDetail() : this.prefillOwner()))
    if (!id) this.tryRestoreDraft() // 【议题四】新建时检查草稿恢复
  },

  /** 下拉数据：部门 / 人员 / 字典 / 历史集团名（演示模式直接用演示候选，不请求接口） */
  loadOptions() {
    if (getApp().globalData.demoMode) {
      // 演示模式没有后端：直接设置演示候选（FALLBACK 无 deptType，不经 applyDepts 过滤）
      this.setData({
        regionOptions: FALLBACK.regions,
        allOffices: FALLBACK.offices,
        officeOptions: this.filterOffices(FALLBACK.offices, this.data.form.regionId),
        userOptions: FALLBACK.users,
        industryOptions: FALLBACK.industry,
        devTypeOptions: FALLBACK.devType,
        groupOptions: FALLBACK.groups,
        groupPickerOptions: FALLBACK.groups.concat(MANUAL_GROUP_LABEL)
      }, () => this.refreshFormView())
      return Promise.resolve()
    }
    // 【PR-04】真实模式接口失败：报错并置空选项，保留用户已输入内容；不回退演示数据
    return Promise.all([
      getDepts().then((list) => this.applyDepts(list || [])).catch((error) => {
        showErrorWithRetry(error, '部门加载失败，请重试', () => this.loadOptions())
        this.applyDepts([])
      }),
      getCustomerSimpleUsers().then((list) => this.setData({ userOptions: list || [] }, () => this.refreshFormView())).catch((error) => {
        showErrorWithRetry(error, '负责人列表加载失败，请重试', () => this.loadOptions())
        this.setData({ userOptions: [] }, () => this.refreshFormView())
      }),
      getDictData('industry').then((list) => this.setData({ industryOptions: (list || []).map((d) => d.dictValue) }, () => this.refreshFormView())).catch((error) => {
        showErrorWithRetry(error, '行业字典加载失败，请重试', () => this.loadOptions())
        this.setData({ industryOptions: [] }, () => this.refreshFormView())
      }),
      getDictData('dev_type').then((list) => this.setData({ devTypeOptions: (list || []).map((d) => d.dictValue) }, () => this.refreshFormView())).catch((error) => {
        showErrorWithRetry(error, '开发类型字典加载失败，请重试', () => this.loadOptions())
        this.setData({ devTypeOptions: [] }, () => this.refreshFormView())
      }),
      getCustomerGroupNames().then((list) => this.setGroupOptions((list || []).filter(Boolean))).catch(() => this.setGroupOptions([]))
    ])
  },

  setGroupOptions(list) {
    const names = list || []
    this.setData({ groupOptions: names, groupPickerOptions: names.concat(MANUAL_GROUP_LABEL) }, () => this.refreshFormView())
  },

  /** 部门列表 → 大区/办事处候选；按 deptType 过滤，空列表就是空选项（【PR-04】不再回退演示部门） */
  applyDepts(list) {
    const source = list || []
    const regions = source.filter((d) => d.deptType === 2)
    const offices = source.filter((d) => d.deptType === 3)
    this.setData({
      regionOptions: regions,
      allOffices: offices,
      officeOptions: this.filterOffices(offices, this.data.form.regionId)
    }, () => this.refreshFormView())
  },

  /** 办事处只列出所选大区下的（与 PC 端 filteredOffices 同口径） */
  filterOffices(offices, regionId) {
    if (!regionId) return []
    return (offices || []).filter((o) => String(o.parentId) === String(regionId))
  },

  /** 新建时默认第一负责人=当前登录人（与 PC 一致）。
   *  【M13 2026-10-04】草稿恢复已带负责人时不覆盖（草稿里用户选的负责人优先于默认值）。 */
  prefillOwner() {
    if (this.data.form.ownerId) return
    const currentUser = getApp().globalData.currentUser || {}
    const ownerId = currentUser.userId || (getApp().globalData.demoMode ? FALLBACK.users[0].id : null)
    if (!ownerId) return
    this.setData({ 'form.ownerId': ownerId, ownerIndex: indexOfId(this.data.userOptions, ownerId) }, () => this.refreshFormView())
  },

  loadDetail() {
    if (getApp().globalData.demoMode) {
      const item = readDemoCustomers().find((customer) => String(customer.id) === String(this.data.id))
      if (item) {
        this.applyCustomer(item)
        return this.applyIndicator(item.indicator || item.customerIndicator || {})
      }
    }
    this.setData({ loading: true })
    getCustomer(this.data.id)
      .then((item) => this.applyCustomer(item))
      .catch((error) => showError(error, '客户加载失败'))
      .then(() => this.loadIndicator())
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
      customerStatus: item.status || '',
      isPoolCustomer: item.status === 'sea',
      originalOwnerId: form.ownerId || null,
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
    }, () => this.refreshFormView())
  },

  loadIndicator() {
    if (!this.data.id || getApp().globalData.demoMode) return Promise.resolve()
    return getCustomerIndicator(this.data.id)
      .then((indicator) => this.applyIndicator(indicator || {}))
      .catch(() => this.applyIndicator({}))
  },

  applyIndicator(indicator) {
    const form = INDICATOR_FIELDS.reduce((result, field) => ({ ...result, [field]: Number(indicator && indicator[field]) || 0 }), {})
    this.setData({
      indicatorForm: form,
      indicatorItems: buildIndicatorItems(form),
      indicatorLoaded: !!indicator,
      indicatorTotal: sumIndicatorScore(form)
    })
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    const patch = { [`form.${field}`]: e.detail.value }
    if (field === 'customerName') patch.dupInfo = { exists: false, createdBy: '', createdAt: '' }
    this.setData(patch, () => this.refreshFormView())
  },

  checkDuplicateName() {
    const name = String(this.data.form.customerName || '').trim()
    if (this.data.isEdit || getApp().globalData.demoMode || !name || name.length < 2) {
      this.setData({ dupInfo: { exists: false, createdBy: '', createdAt: '' }, checkingName: false }, () => this.refreshFormView())
      return Promise.resolve({ exists: false })
    }
    this.setData({ checkingName: true })
    return checkCustomerName(name)
      .then((info) => {
        const dupInfo = {
          exists: !!(info && info.exists),
          createdBy: (info && info.createdBy) || '',
          createdAt: (info && info.createdAt) || ''
        }
        this.setData({ dupInfo }, () => this.refreshFormView())
        return dupInfo
      })
      .catch(() => {
        this.setData({ dupInfo: { exists: false, createdBy: '', createdAt: '' } }, () => this.refreshFormView())
        return { exists: false }
      })
      .finally(() => this.setData({ checkingName: false }))
  },

  chooseIndustry(e) { const i = Number(e.detail.value); this.setData({ industryIndex: i, 'form.industry': this.data.industryOptions[i] || '' }, () => this.refreshFormView()) },
  chooseDevType(e) { const i = Number(e.detail.value); this.setData({ devTypeIndex: i, 'form.devType': this.data.devTypeOptions[i] || '' }, () => this.refreshFormView()) },
  chooseTerminal(e) { const i = Number(e.detail.value); this.setData({ terminalIndex: i, 'form.hasTerminal': i }, () => this.refreshFormView()) },

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
    }, () => this.refreshFormView())
  },
  chooseOffice(e) { const i = Number(e.detail.value); const office = this.data.officeOptions[i] || {}; this.setData({ officeIndex: i, 'form.officeId': office.id || null }, () => this.refreshFormView()) },
  chooseOwner(e) {
    if (this.data.isPoolCustomer) return wx.showToast({ title: '公海客户请通过捞取流程变更负责人', icon: 'none' })
    const i = Number(e.detail.value); const user = this.data.userOptions[i] || {}; this.setData({ ownerIndex: i, 'form.ownerId': user.id || null }, () => this.refreshFormView())
  },
  chooseManager(e) { const i = Number(e.detail.value); const user = this.data.userOptions[i] || {}; this.setData({ managerIndex: i, 'form.managerId': user.id || null }, () => this.refreshFormView()) },

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
          if (res.confirm && name) this.setData({ groupIndex: -1, 'form.groupName': name }, () => this.refreshFormView())
        }
      })
    }
    this.setData({ groupIndex: i, 'form.groupName': this.data.groupOptions[i] || '' }, () => this.refreshFormView())
  },

  /** 省/市/区：用微信原生 region picker，落库仍是三个字符串（与 PC 级联同口径） */
  chooseArea(e) {
    const value = e.detail.value || []
    this.setData({
      region: value,
      'form.province': value[0] || '',
      'form.city': value[1] || '',
      'form.district': value[2] || ''
    }, () => this.refreshFormView())
  },
  chooseDate(e) { this.setData({ [`form.${e.currentTarget.dataset.field}`]: e.detail.value }, () => this.refreshFormView()) },

  selectIndicatorScore(e) {
    const field = e.currentTarget.dataset.field
    const score = Number(e.currentTarget.dataset.score) || 0
    const form = { ...this.data.indicatorForm, [field]: score }
    this.setData({ indicatorForm: form, indicatorItems: buildIndicatorItems(form), indicatorTotal: sumIndicatorScore(form) })
  },

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
    return this.checkDuplicateName().then((dupInfo) => {
      if (dupInfo.exists) {
        return wx.showModal({
          title: '客户名称可能重复',
          content: `此客户已在 ${dupInfo.createdAt || '未知时间'} 由 ${dupInfo.createdBy || '他人'} 创建，仍要继续保存吗？`,
          confirmText: '继续保存',
          success: (res) => { if (res.confirm) this.saveCustomer() }
        })
      }
      return this.saveCustomer()
    })
  },

  shouldSaveIndicator() {
    return this.data.indicatorLoaded || sumIndicatorScore(this.data.indicatorForm) > 0
  },

  /** 【PR-05】组合提交的指标载荷:未加载/零分时不提交指标(与原 shouldSaveIndicator 口径一致) */
  indicatorPayload() {
    return this.shouldSaveIndicator() ? { ...this.data.indicatorForm } : null
  },

  saveCustomer() {
    if (this.data.loading || this.data.saving) return
    this.setData({ saving: true }, () => this.refreshFormView())

    // 日期空串统一转 null；新建时空串归一 null（库里不写空串），
    // 编辑时保留空串——后端 updateById 跳过 null 字段，空串才是"清空字段"的唯一表达。
    const form = { ...this.data.form }
    if (this.data.isEdit && this.data.isPoolCustomer) form.ownerId = this.data.originalOwnerId
    form.registerDate = form.registerDate || null
    form.lastInvoiceDate = form.lastInvoiceDate || null
    if (!this.data.isEdit) {
      Object.keys(form).forEach((key) => {
        if (typeof form[key] === 'string' && form[key].trim() === '') form[key] = null
      })
    }

    // 【PR-05 2026-09-19】真实模式走客户+指标同事务组合提交:任一步失败整体回滚,
    // 消除"建客户成功、存指标失败→界面报保存失败但客户已入库,重试重复建客"。
    // 演示模式仍本地两步(saveDemoCustomer 自带指标)。
    // 【PR-05 残余 20260921】真实模式新建分支携带幂等键(编辑分支为 PUT,无重复建客风险)
    if (!getApp().globalData.demoMode && !this.data.isEdit && !createSubmitKey) createSubmitKey = newSubmitKey()
    const action = getApp().globalData.demoMode
      ? Promise.resolve(saveDemoCustomer({ ...form, indicator: { ...this.data.indicatorForm } }, this.data.isEdit ? this.data.id : ''))
      : (this.data.isEdit
          ? updateCustomerWithIndicator(this.data.id, { customer: form, indicator: this.indicatorPayload() })
          : saveCustomerWithIndicator({ customer: form, indicator: this.indicatorPayload() }, createSubmitKey))
    action
      .then(() => {
        createSubmitKey = null // 成功后换新键,下一次新建客户是全新提交
        // 【M12 2026-10-04】置已提交标志：500ms 后 navigateBack 触发 onUnload→autoSaveDraft，
        // 此前会把刚 deleteFormDraft 清掉的草稿原样存回(新建成功后草稿"复活")
        this.submitted = true
        deleteFormDraft('customer_form').catch(() => {}) // 【议题四】提交成功清草稿
        wx.showToast({ title: this.data.isEdit ? '保存成功' : '新增成功', icon: 'success' })
        setTimeout(() => wx.navigateBack(), 500)
      })
      .catch((error) => showError(error, '保存失败，请检查权限或字段'))
      .finally(() => this.setData({ saving: false }, () => this.refreshFormView()))
  },

  // 【议题四裁决】表单草稿:离开自动保存(新建模式,仅真实环境;7 天保留/版本冲突)
  onHide() { this.autoSaveDraft() },
  onUnload() { this.autoSaveDraft() },

  autoSaveDraft() {
    // 【M12 2026-10-04】已成功提交的表单不再存草稿(内容已入库,存回即"复活"已清掉的草稿)
    if (this.submitted) return
    if (this.data.isEdit || this.data.saving) return
    if (getApp().globalData.demoMode) return
    const form = this.data.form || {}
    if (!form.customerName || !form.customerName.trim()) return // 空表单不存
    saveFormDraft('customer_form', { form, indicatorForm: this.data.indicatorForm }, this.data.draftVersion)
      .then((result) => {
        const d = result && result.data
        if (d && d.version) this.setData({ draftVersion: d.version })
      })
      .catch(() => {}) // 自动保存静默失败(网络差等),不打扰用户
  },

  /** 【M13 2026-10-04】草稿恢复前判断用户是否已有输入(ownerId 除外——新建会预填当前登录人)：
   *  草稿弹窗异步到达时表单可能已被填过，需提示恢复会覆盖当前内容 */
  hasManualInput() {
    const f = this.data.form || {}
    const textFields = ['customerName', 'alias', 'invoiceName', 'groupName', 'branchCompany', 'unitName', 'capacity', 'province', 'city', 'district', 'address', 'remark', 'industry', 'devType']
    if (textFields.some((key) => String(f[key] || '').trim())) return true
    if (f.regionId || f.officeId || f.managerId) return true
    return sumIndicatorScore(this.data.indicatorForm) > 0
  },

  tryRestoreDraft() {
    if (getApp().globalData.demoMode) return
    getFormDraft('customer_form').then((result) => {
      const draft = result && result.data
      if (!draft || !draft.draftData) return
      let parsed
      try { parsed = JSON.parse(draft.draftData) } catch (e) { return }
      if (!parsed || !parsed.form || !parsed.form.customerName) return
      // 弹窗到达时用户已有输入则明确提示覆盖(wx.showModal 期间页面不可交互,快照即最新)
      const overwriteTip = this.hasManualInput() ? '；恢复将覆盖当前已输入的内容' : ''
      wx.showModal({
        title: '发现未完成的草稿',
        content: `客户"${parsed.form.customerName}"的录入未完成(保存于 ${String(draft.updatedAt || '').slice(0, 16)}),是否恢复?${overwriteTip}`,
        confirmText: '恢复',
        cancelText: '放弃',
        success: (res) => {
          if (res.confirm) {
            // 【M13 2026-10-04】恢复复用 applyCustomer(编辑回填)重算 picker 索引与办事处候选，
            // 此前只回填 form 不重建索引，界面显示值与提交值不一致；
            // 并挂在 optionsPromise 之后执行，保证候选列表已就绪再算索引
            const apply = () => {
              this.applyCustomer(parsed.form)
              if (parsed.indicatorForm) {
                // 重建指标视图(此前 indicatorItems 不更新,恢复的分数界面不显示);
                // indicatorLoaded 保持 false——未从库内加载,维持"零分指标不随组合提交"口径(shouldSaveIndicator)
                const indicatorForm = INDICATOR_FIELDS.reduce((result, field) => ({ ...result, [field]: Number(parsed.indicatorForm[field]) || 0 }), {})
                this.setData({ indicatorForm, indicatorItems: buildIndicatorItems(indicatorForm), indicatorTotal: sumIndicatorScore(indicatorForm) })
              }
              this.setData({ draftVersion: draft.version || 1 }, () => this.refreshFormView())
            }
            if (this.optionsPromise) this.optionsPromise.then(apply)
            else apply()
          } else {
            deleteFormDraft('customer_form').catch(() => {})
          }
        }
      })
    }).catch(() => {})
  },

  cancel() {
    if (this.data.saving) return
    // 【D24 2026-10-04】返回兜底逻辑收敛到 shared.navigateBackWithFallback(组内各表单共用)
    navigateBackWithFallback('/pages/customer/index')
  }
})
